package com.example.repodashboard.github

import com.example.repodashboard.models.*
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.json.Json

class GitHubClient(private val token: String) {
    private val client = HttpClient {
        install(ContentNegotiation) {
            json(Json { ignoreUnknownKeys = true })
        }
    }

    private val baseUrl = "https://api.github.com"

    private fun HttpRequestBuilder.addHeaders() {
        header("Authorization", "Bearer $token")
        header("Accept", "application/vnd.github.v3+json")
        header("X-GitHub-Api-Version", "2022-11-28")
    }

    suspend fun validateToken(): Boolean {
        return try {
            val response = client.get("$baseUrl/user") { addHeaders() }
            response.status.isSuccess()
        } catch (e: Exception) {
            false
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
            val all = mutableListOf<GitHubRepoItem>()
            var page = 1
            while (true) {
                val response = client.get("$baseUrl/user/repos") {
                    addHeaders()
                    parameter("per_page", 100)
                    parameter("page", page)
                    parameter("sort", "updated")
                    parameter("affiliation", "owner,organization_member")
                }
                if (!response.status.isSuccess()) break
                val batch: List<GitHubRepoItem> = response.body()
                if (batch.isEmpty()) break
                all.addAll(batch)
                if (batch.size < 100) break
                page++
            }
            Result.success(all)
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
