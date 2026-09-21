package com.example.repodashboard.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class RepositoryConfig(
    val name: String,
    val owner: String,
    val repo: String,
    val description: String = "",
    val defaultBranch: String = "main",
    val releaseBranch: String = "release",
    val devBranch: String = "dev"
)

@Serializable
data class AppConfig(
    val repositories: List<RepositoryConfig>
)

@Serializable
data class GitHubRepoOwner(val login: String)

@Serializable
data class GitHubPRUser(val login: String)

@Serializable
data class GitHubPRLabel(val name: String, val color: String = "")

@Serializable
data class GitHubPR(
    val number: Int,
    val title: String,
    val user: GitHubPRUser,
    val state: String = "open",
    val draft: Boolean = false,
    val created_at: String = "",
    val updated_at: String = "",
    val html_url: String = "",
    val body: String? = null,
    val labels: List<GitHubPRLabel> = emptyList()
)

@Serializable
data class GitHubRepoItem(
    val id: Long,
    val name: String,
    val full_name: String,
    val owner: GitHubRepoOwner,
    @SerialName("private") val isPrivate: Boolean = false,
    val description: String? = null,
    val default_branch: String = "main",
    val updated_at: String? = null
)

@Serializable
data class GitHubRelease(
    val tag_name: String,
    val name: String,
    val published_at: String,
    val html_url: String,
    val body: String? = null
)

@Serializable
data class GitHubCompareResponse(
    val status: String,
    val ahead_by: Int,
    val behind_by: Int,
    val commits: List<GitHubCommit>
)

@Serializable
data class GitHubCommit(
    val sha: String,
    val commit: CommitDetails,
    val html_url: String
)

@Serializable
data class CommitDetails(
    val message: String,
    val author: GitHubAuthor,
    val committer: GitHubAuthor? = null
)

@Serializable
data class GitHubAuthor(
    val name: String,
    val email: String,
    val date: String
)

@Serializable
data class Repository(
    val name: String,
    val owner: String,
    val repo: String,
    val description: String = "",
    val version: String = "",
    val defaultBranch: String = "main",
    val openPRsCount: Int = 0,
    val openPRs: List<GitHubPR> = emptyList()
)

/** Loaded lazily when the Releases tab is opened. */
data class ReleaseInfo(
    val tagName: String,
    val name: String,
    val publishedAt: String,
    val htmlUrl: String,
    val prs: List<MergedPR>
)

data class ReleaseData(
    val unreleased: List<MergedPR>,
    val releases: List<ReleaseInfo>
)

@Serializable
data class MergedPR(
    val id: String,
    val number: Int = 0,
    val title: String,
    val author: String,
    val mergedDate: String,
    val repo: String,
    val prUrl: String,
    val commitHash: String
)

@Serializable
data class ReleasePlan(
    val nextVersion: String = "",
    val nextReleaseDate: String = "",
    val notes: String = ""
)

@Serializable
data class ReleasePlans(
    val lastUpdated: String = "",
    val plans: Map<String, ReleasePlan> = emptyMap()
)

data class AppState(
    val isLoading: Boolean = false,
    val error: String? = null,
    val repositories: List<Repository> = emptyList(),
    val selectedRepoName: String? = null,
    val releasePlans: Map<String, ReleasePlan> = emptyMap(),
    val githubToken: String? = null,
    val lastUpdated: String? = null,
    val showRepoSelection: Boolean = false
)

data class APIError(
    val code: String,
    override val message: String,
    val statusCode: Int? = null
) : Throwable(message)
