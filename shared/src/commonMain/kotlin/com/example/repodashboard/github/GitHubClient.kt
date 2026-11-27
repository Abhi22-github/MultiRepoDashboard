package com.example.repodashboard.github

import com.example.repodashboard.models.*
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import com.example.repodashboard.data.nowMs
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive

class GitHubClient(
    private val token: String,
    baseUrl: String = "https://api.github.com"
) {
    @PublishedApi
    internal val json = Json { ignoreUnknownKeys = true }

    private val client = HttpClient {
        install(ContentNegotiation) { json(json) }
    }

    /**
     * ETag cache for REST GETs (this session). A 304 reply is served from here and
     * doesn't count against GitHub's rate limit.
     */
    @PublishedApi
    internal val etags = mutableMapOf<String, Pair<String, String>>()

    @PublishedApi
    internal var restBlockedUntilSec: Long? = null

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
    @PublishedApi
    internal fun errorOf(response: HttpResponse, what: String): APIError {
        val remaining = response.headers["x-ratelimit-remaining"]
        val retryAfter = response.headers["retry-after"]?.toLongOrNull()
        val reset = retryAfter?.let { nowMs() / 1000 + it } ?: response.headers["x-ratelimit-reset"]?.toLongOrNull()
        return when {
            response.status == HttpStatusCode.Unauthorized ->
                APIError("INVALID_TOKEN", "GitHub token invalid or expired", 401)
            response.status == HttpStatusCode.TooManyRequests ||
                (response.status == HttpStatusCode.Forbidden && (remaining == "0" || retryAfter != null)) ->
                APIError("RATE_LIMIT", "GitHub rate limit reached", response.status.value, reset)
            response.status == HttpStatusCode.NotFound ->
                APIError("NOT_FOUND", "$what not found", 404)
            else -> APIError("API_ERROR", "Failed to fetch $what", response.status.value)
        }
    }

    @PublishedApi
    internal suspend inline fun <reified T> getJson(url: String, what: String, params: List<Pair<String, Any>> = emptyList()): Result<T> =
        try {
            restBlockedUntilSec?.let { until ->
                if (nowMs() / 1000 < until) return Result.failure(APIError("RATE_LIMIT", "GitHub rate limit reached", 429, until))
                restBlockedUntilSec = null
            }
            val key = url + params.joinToString("&", prefix = "?") { "${it.first}=${it.second}" }
            val cached = etags[key]
            val response = restGet(url, params, cached?.first)
            when {
                response.status == HttpStatusCode.NotModified && cached != null -> Result.success(json.decodeFromString<T>(cached.second))
                response.status.isSuccess() -> {
                    val text = response.bodyAsText()
                    response.headers["ETag"]?.let { etags[key] = it to text }
                    Result.success(json.decodeFromString<T>(text))
                }
                else -> Result.failure(errorOf(response, what).also { if (it.code == "RATE_LIMIT") restBlockedUntilSec = it.resetAt ?: (nowMs() / 1000 + 60) })
            }
        } catch (e: CancellationException) { throw e } catch (e: Throwable) {
            Result.failure(APIError("NETWORK_ERROR", "Network error calling $url: ${e.message}", null))
        }

    @PublishedApi
    internal suspend fun restGet(url: String, params: List<Pair<String, Any>>, etag: String?): HttpResponse =
        gate.withPermit {
            client.get(url) {
                addHeaders()
                params.forEach { parameter(it.first, it.second) }
                if (etag != null) header("If-None-Match", etag)
            }
        }



    private fun repoUrl(owner: String, repo: String) = "$baseUrl/repos/$owner/$repo"

    /** Current REST and GraphQL budgets. Free: doesn't count against either limit. */
    suspend fun getRateLimit(): Result<RateLimitResponse> =
        try {
            val response = gate.withPermit { client.get("$baseUrl/rate_limit") { addHeaders() } }
            if (response.status.isSuccess()) Result.success(json.decodeFromString(response.bodyAsText()))
            else Result.failure(errorOf(response, "rate limit"))
        } catch (e: CancellationException) { throw e } catch (e: Throwable) {
            Result.failure(APIError("NETWORK_ERROR", "Network error: ${e.message}", null))
        }

    /**
     * One page-walk of /pulls. [stop] sees each page and can end early (e.g. merged PRs older than a cutoff).
     * Returns the PRs and whether the end (or [stop]) was reached.
     */
    suspend fun listPulls(owner: String, repo: String, state: String, maxPages: Int, stop: (List<GitHubPR>) -> Boolean = { false }): Result<Pair<List<GitHubPR>, Boolean>> {
        val all = mutableListOf<GitHubPR>()
        for (page in 1..maxPages) {
            val batch = getJson<List<GitHubPR>>(
                "${repoUrl(owner, repo)}/pulls", "pull requests",
                listOf("state" to state, "per_page" to 100, "page" to page, "sort" to "updated", "direction" to "desc")
            ).getOrElse { return Result.failure(it) }
            all += batch
            if (batch.size < 100 || stop(batch)) return Result.success(all to true)
        }
        return Result.success(all to false)
    }

    suspend fun listReleases(owner: String, repo: String, maxPages: Int): Result<List<GitHubRelease>> {
        val all = mutableListOf<GitHubRelease>()
        for (page in 1..maxPages) {
            val batch = getJson<List<GitHubRelease>>("${repoUrl(owner, repo)}/releases", "releases", listOf("per_page" to 100, "page" to page))
                .getOrElse { return if ((it as? APIError)?.code == "NOT_FOUND") Result.success(all) else Result.failure(it) }
            all += batch
            if (batch.size < 100) break
        }
        return Result.success(all)
    }

    suspend fun getPull(owner: String, repo: String, number: Int): Result<GitHubPR> =
        getJson("${repoUrl(owner, repo)}/pulls/$number", "pull request")

    suspend fun getPullFull(owner: String, repo: String, number: Int): Result<GitHubPRFull> =
        getJson("${repoUrl(owner, repo)}/pulls/$number", "pull request")

    suspend fun getReviews(owner: String, repo: String, number: Int): Result<List<GitHubReview>> =
        getJson("${repoUrl(owner, repo)}/pulls/$number/reviews", "reviews", listOf("per_page" to 100))

    suspend fun getCheckRuns(owner: String, repo: String, sha: String): Result<GitHubCheckRuns> =
        getJson("${repoUrl(owner, repo)}/commits/$sha/check-runs", "checks", listOf("per_page" to 100))

    suspend fun getCombinedStatus(owner: String, repo: String, sha: String): Result<GitHubCombinedStatus> =
        getJson("${repoUrl(owner, repo)}/commits/$sha/status", "status")

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
    /** GraphQL is costlier server-side, so fewer at once. */
    private val gqlGate = Semaphore(3)

    /** Set when GitHub says we're rate limited: later calls fail fast instead of digging deeper. */
    @PublishedApi
    internal var blockedUntilSec: Long? = null

    @PublishedApi
    internal fun blockedError(): APIError? {
        val until = blockedUntilSec ?: return null
        return if (nowMs() / 1000 < until) APIError("RATE_LIMIT", "GitHub rate limit reached", 429, until) else { blockedUntilSec = null; null }
    }

    @PublishedApi
    internal fun noteRateLimit(e: APIError): APIError {
        if (e.code == "RATE_LIMIT") blockedUntilSec = e.resetAt ?: (nowMs() / 1000 + 60)
        return e
    }

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
                    Result.failure(noteRateLimit(APIError("RATE_LIMIT", "GitHub rate limit reached", 200, response.headers["x-ratelimit-reset"]?.toLongOrNull())))
                body.data != null -> Result.success(body.data)
                else -> Result.failure(APIError("API_ERROR", errors.firstOrNull()?.message ?: "Empty GraphQL response", 200))
            }
        }

    @PublishedApi
    internal suspend fun <T> graphqlRaw(req: GqlRequest, parse: suspend (HttpResponse) -> Result<T>): Result<T> =
        try {
            blockedError()?.let { return Result.failure(it) }
            gqlGate.withPermit {
                val response = client.post(graphqlUrl) {
                    addHeaders()
                    contentType(ContentType.Application.Json)
                    setBody(req)
                }
                if (response.status.isSuccess()) parse(response) else Result.failure(noteRateLimit(errorOf(response, "data")))
            }
        } catch (e: CancellationException) { throw e } catch (e: Throwable) {
            Result.failure(APIError("NETWORK_ERROR", "Network error calling $graphqlUrl: ${e.message}", null))
        }

    fun close() = client.close()
}
