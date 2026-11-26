package com.example.repodashboard.github

import com.example.repodashboard.models.*
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.json.Json

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
        } catch (e: Exception) {
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
            val response = client.get(url) { addHeaders(); params() }
            if (response.status.isSuccess()) Result.success(response.body()) else Result.failure(errorOf(response, what))
        } catch (e: Exception) {
            Result.failure(APIError("NETWORK_ERROR", "Network error: ${e.message}", null))
        }

    private fun repoUrl(owner: String, repo: String) = "$baseUrl/repos/$owner/$repo"

    suspend fun getLatestRelease(owner: String, repo: String): Result<GitHubRelease> =
        getJson("${repoUrl(owner, repo)}/releases/latest", "release")

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
            } catch (e: Exception) {
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
        } catch (e: Exception) {
            Result.failure(APIError("NETWORK_ERROR", "Failed to load repos: ${e.message}", null))
        }
    }

    suspend fun getOpenPRs(owner: String, repo: String): Result<List<GitHubPR>> {
        val all = mutableListOf<GitHubPR>()
        var page = 1
        while (true) {
            val batch = getJson<List<GitHubPR>>("${repoUrl(owner, repo)}/pulls", "pull requests") {
                parameter("state", "open")
                parameter("per_page", 100)
                parameter("page", page)
                parameter("sort", "updated")
                parameter("direction", "desc")
            }.getOrElse { return Result.failure(it) }
            all.addAll(batch)
            if (batch.size < 100) break
            page++
        }
        return Result.success(all)
    }

    /** Recently closed PRs; callers keep the ones with merged_at set. */
    suspend fun getClosedPRs(owner: String, repo: String, perPage: Int = 50): Result<List<GitHubPR>> =
        getJson("${repoUrl(owner, repo)}/pulls", "pull requests") {
            parameter("state", "closed")
            parameter("per_page", perPage)
            parameter("sort", "updated")
            parameter("direction", "desc")
        }

    suspend fun getReleases(owner: String, repo: String): Result<List<GitHubRelease>> =
        getJson("${repoUrl(owner, repo)}/releases", "releases") { parameter("per_page", 100) }

    suspend fun getPullFull(owner: String, repo: String, number: Int): Result<GitHubPRFull> =
        getJson("${repoUrl(owner, repo)}/pulls/$number", "pull request")

    suspend fun getReviews(owner: String, repo: String, number: Int): Result<List<GitHubReview>> =
        getJson("${repoUrl(owner, repo)}/pulls/$number/reviews", "reviews") { parameter("per_page", 100) }

    suspend fun getCheckRuns(owner: String, repo: String, sha: String): Result<GitHubCheckRuns> =
        getJson("${repoUrl(owner, repo)}/commits/$sha/check-runs", "checks") { parameter("per_page", 100) }

    suspend fun getCombinedStatus(owner: String, repo: String, sha: String): Result<GitHubCombinedStatus> =
        getJson("${repoUrl(owner, repo)}/commits/$sha/status", "status")

    fun close() = client.close()
}
