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
data class GitHubRef(val ref: String = "", val sha: String = "")

@Serializable
data class GitHubMilestone(val title: String = "")

@Serializable
data class GitHubPR(
    val number: Int,
    val title: String,
    val user: GitHubPRUser,
    val state: String = "open",
    val draft: Boolean = false,
    val created_at: String = "",
    val updated_at: String = "",
    val merged_at: String? = null,
    val html_url: String = "",
    val body: String? = null,
    val labels: List<GitHubPRLabel> = emptyList(),
    val head: GitHubRef = GitHubRef(),
    val base: GitHubRef = GitHubRef(),
    val milestone: GitHubMilestone? = null
)

/** Fields only returned by GET /pulls/{n}. */
@Serializable
data class GitHubPRFull(
    val additions: Int = 0,
    val deletions: Int = 0,
    val comments: Int = 0,
    val review_comments: Int = 0
)

@Serializable
data class GitHubReview(val state: String = "", val user: GitHubPRUser? = null)

@Serializable
data class GitHubCheckRun(val status: String = "", val conclusion: String? = null)

@Serializable
data class GitHubCheckRuns(val total_count: Int = 0, val check_runs: List<GitHubCheckRun> = emptyList())

@Serializable
data class GitHubCombinedStatus(val state: String = "", val total_count: Int = 0)

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

data class APIError(
    val code: String,
    override val message: String,
    val statusCode: Int? = null,
    /** Epoch seconds when the rate limit resets (RATE_LIMIT only). */
    val resetAt: Long? = null
) : Throwable(message)
