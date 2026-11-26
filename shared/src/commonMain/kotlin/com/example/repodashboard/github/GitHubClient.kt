package com.example.repodashboard.github

import com.example.repodashboard.models.*
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive

class GitHubClient(
    private val token: String,
    baseUrl: String = "https://api.github.com"
) {
    private val client = HttpClient {
        install(ContentNegotiation) {
            json(Json { ignoreUnknownKeys = true })
        }
    }

    private val baseUrl = baseUrl.trimEnd('/')

    private fun HttpRequestBuilder.addHeaders() {
        header("Authorization", "Bearer $token")
        header("Accept", "application/vnd.github.v3+json")
        header("X-GitHub-Api-Version", "2022-11-28")
    }

    /** Returns null when the token is valid, otherwise a human-readable reason it failed. */
    suspend fun validateToken(): String? {
        return try {
            val response = client.get("$baseUrl/user") { addHeaders() }
            when {
                response.status.isSuccess() -> null
                response.status == HttpStatusCode.Unauthorized ->
                    "Token rejected (401). It may be mistyped, expired, or revoked. Re-copy the whole token and try again."
                response.status == HttpStatusCode.Forbidden ->
                    "Access forbidden (403). If this is a fine-grained token on organization repos, the org must approve it first: Org Settings → Third-party Access → Personal access tokens."
                else ->
                    "GitHub returned ${response.status.value} ${response.status.description}."
            }
        } catch (e: CancellationException) { throw e } catch (e: Throwable) {
            "Could not reach GitHub (${e.message ?: "network or CORS error"})."
        }
    }

    /** Maps a non-success response to an [APIError] the UI can act on. */
    private fun errorOf(response: HttpResponse, what: String): APIError {
        val remaining = response.headers["x-ratelimit-remaining"]
        val reset = response.headers["x-ratelimit-reset"]?.toLongOrNull()
        return when {
            response.status == HttpStatusCode.Unauthorized ->
                APIError("INVALID_TOKEN", "GitHub token invalid or expired", 401)
            response.status == HttpStatusCode.TooManyRequests ||
                (response.status == HttpStatusCode.Forbidden && remaining == "0") ->
                APIError("RATE_LIMIT", "GitHub rate limit reached", response.status.value, reset)
            response.status == HttpStatusCode.NotFound ->
                APIError("NOT_FOUND", "$what not found", 404)
            else -> APIError("API_ERROR", "Failed to fetch $what", response.status.value)
        }
    }

    private suspend inline fun <reified T> getJson(url: String, what: String, crossinline params: HttpRequestBuilder.() -> Unit = {}): Result<T> =
        try {
            val response = gate.withPermit { client.get(url) { addHeaders(); params() } }
            if (response.status.isSuccess()) Result.success(response.body()) else Result.failure(errorOf(response, what))
        } catch (e: CancellationException) { throw e } catch (e: Throwable) {
            Result.failure(APIError("NETWORK_ERROR", "Network error calling $url: ${e.message}", null))
        }

    private fun repoUrl(owner: String, repo: String) = "$baseUrl/repos/$owner/$repo"

    suspend fun compareBranches(owner: String, repo: String, base: String, head: String): Result<GitHubCompareResponse> =
        getJson("${repoUrl(owner, repo)}/compare/$base...$head", "comparison")

    suspend fun getUserRepos(): Result<List<GitHubRepoItem>> {
        return try {
            // De-duplicate by full_name while preserving discovery order.
            val byName = LinkedHashMap<String, GitHubRepoItem>()

            // 1) Repos the user is directly affiliated with (owner / collaborator / org member).
            var page = 1
            while (true) {
                val response = client.get("$baseUrl/user/repos") {
                    addHeaders()
                    parameter("per_page", 100)
                    parameter("page", page)
                    parameter("sort", "updated")
                    parameter("visibility", "all")
                    parameter("affiliation", "owner,collaborator,organization_member")
                }
                if (!response.status.isSuccess()) break
                val batch: List<GitHubRepoItem> = response.body()
                if (batch.isEmpty()) break
                batch.forEach { byName[it.full_name] = it }
                if (batch.size < 100) break
                page++
            }

            // 2) Per-org listing with type=all — surfaces INTERNAL repos that /user/repos omits.
            val orgs: List<GitHubRepoOwner> = try {
                val resp = client.get("$baseUrl/user/orgs") {
                    addHeaders()
                    parameter("per_page", 100)
                }
                if (resp.status.isSuccess()) resp.body() else emptyList()
            } catch (e: CancellationException) { throw e } catch (e: Throwable) {
                emptyList()
            }

            for (org in orgs) {
                var orgPage = 1
                while (true) {
                    val response = client.get("$baseUrl/orgs/${org.login}/repos") {
                        addHeaders()
                        parameter("per_page", 100)
                        parameter("page", orgPage)
                        parameter("type", "all")
                        parameter("sort", "updated")
                    }
                    if (!response.status.isSuccess()) break
                    val batch: List<GitHubRepoItem> = response.body()
                    if (batch.isEmpty()) break
                    batch.forEach { byName[it.full_name] = it }
                    if (batch.size < 100) break
                    orgPage++
                }
            }

            Result.success(byName.values.toList())
        } catch (e: CancellationException) { throw e } catch (e: Throwable) {
            Result.failure(APIError("NETWORK_ERROR", "Failed to load repos: ${e.message}", null))
        }
    }

    /** GraphQL endpoint for this API base: /graphql on github.com and ghe.com, /api/graphql on Enterprise Server. */
    private val graphqlUrl: String =
        if (this.baseUrl.endsWith("/api/v3")) this.baseUrl.removeSuffix("/api/v3") + "/api/graphql" else "${this.baseUrl}/graphql"

    /** Max requests in flight, to stay under GitHub's secondary (concurrency) limits. */
    private val gate = Semaphore(8)

    /**
     * Runs one GraphQL query. Partial errors (e.g. a field the token can't read) still return the data;
     * only auth, rate-limit and network failures fail the call.
     */
    suspend inline fun <reified T> graphql(query: String, variables: Map<String, String?>): Result<T> =
        graphqlRaw(GqlRequest(query, variables.mapValues { JsonPrimitive(it.value) })) { response ->
            val body: GqlResponse<T> = response.body()
            val errors = body.errors.orEmpty()
            when {
                errors.any { it.type == "RATE_LIMITED" } ->
                    Result.failure(APIError("RATE_LIMIT", "GitHub rate limit reached", 200, response.headers["x-ratelimit-reset"]?.toLongOrNull()))
                body.data != null -> Result.success(body.data)
                else -> Result.failure(APIError("API_ERROR", errors.firstOrNull()?.message ?: "Empty GraphQL response", 200))
            }
        }

    @PublishedApi
    internal suspend fun <T> graphqlRaw(req: GqlRequest, parse: suspend (HttpResponse) -> Result<T>): Result<T> =
        try {
            gate.withPermit {
                val response = client.post(graphqlUrl) {
                    addHeaders()
                    contentType(ContentType.Application.Json)
                    setBody(req)
                }
                if (response.status.isSuccess()) parse(response) else Result.failure(errorOf(response, "data"))
            }
        } catch (e: CancellationException) { throw e } catch (e: Throwable) {
            Result.failure(APIError("NETWORK_ERROR", "Network error calling $graphqlUrl: ${e.message}", null))
        }

    fun close() = client.close()
}
