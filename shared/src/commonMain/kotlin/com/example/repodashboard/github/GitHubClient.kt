package com.example.repodashboard.github

import com.example.repodashboard.models.*
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
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

    suspend fun getLatestRelease(owner: String, repo: String): Result<GitHubRelease> {
        return try {
            val response = client.get("$baseUrl/repos/$owner/$repo/releases/latest") { addHeaders() }
            when {
                response.status == HttpStatusCode.NotFound ->
                    Result.failure(APIError("NOT_FOUND", "No releases found", 404))
                response.status == HttpStatusCode.Unauthorized ->
                    Result.failure(APIError("INVALID_TOKEN", "GitHub token invalid or expired", 401))
                response.status == HttpStatusCode.TooManyRequests ->
                    Result.failure(APIError("RATE_LIMIT", "GitHub rate limit exceeded", 429))
                response.status.isSuccess() ->
                    Result.success(response.body())
                else ->
                    Result.failure(APIError("API_ERROR", "Failed to fetch release", response.status.value))
            }
        } catch (e: Exception) {
            Result.failure(APIError("NETWORK_ERROR", "Network error: ${e.message}", null))
        }
    }

    suspend fun compareBranches(
        owner: String,
        repo: String,
        base: String,
        head: String
    ): Result<GitHubCompareResponse> {
        return try {
            val response = client.get("$baseUrl/repos/$owner/$repo/compare/$base...$head") { addHeaders() }
            when {
                response.status == HttpStatusCode.NotFound ->
                    Result.failure(APIError("NOT_FOUND", "Branches not found", 404))
                response.status == HttpStatusCode.Unauthorized ->
                    Result.failure(APIError("INVALID_TOKEN", "GitHub token invalid", 401))
                response.status == HttpStatusCode.TooManyRequests ->
                    Result.failure(APIError("RATE_LIMIT", "Rate limit exceeded", 429))
                response.status.isSuccess() ->
                    Result.success(response.body())
                else ->
                    Result.failure(APIError("API_ERROR", "Failed to compare branches", response.status.value))
            }
        } catch (e: Exception) {
            Result.failure(APIError("NETWORK_ERROR", "Network error: ${e.message}", null))
        }
    }

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
        return try {
            val all = mutableListOf<GitHubPR>()
            var page = 1
            while (true) {
                val response = client.get("$baseUrl/repos/$owner/$repo/pulls") {
                    addHeaders()
                    parameter("state", "open")
                    parameter("per_page", 100)
                    parameter("page", page)
                    parameter("sort", "updated")
                    parameter("direction", "desc")
                }
                if (!response.status.isSuccess()) break
                val batch: List<GitHubPR> = response.body()
                if (batch.isEmpty()) break
                all.addAll(batch)
                if (batch.size < 100) break
                page++
            }
            Result.success(all)
        } catch (e: Exception) {
            Result.failure(APIError("NETWORK_ERROR", e.message ?: "Failed to load PRs", null))
        }
    }

    suspend fun getReleases(owner: String, repo: String): Result<List<GitHubRelease>> {
        return try {
            val response = client.get("$baseUrl/repos/$owner/$repo/releases") {
                addHeaders()
                parameter("per_page", 100)
            }
            if (response.status.isSuccess()) Result.success(response.body())
            else Result.failure(APIError("API_ERROR", "Failed to fetch releases", response.status.value))
        } catch (e: Exception) {
            Result.failure(APIError("NETWORK_ERROR", e.message ?: "Failed to load releases", null))
        }
    }

    fun close() = client.close()
}
