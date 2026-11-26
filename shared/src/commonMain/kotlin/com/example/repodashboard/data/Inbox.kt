package com.example.repodashboard.data

import com.example.repodashboard.github.GitHubClient
import com.example.repodashboard.models.*
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

/** A merged pull request (unreleased, or part of a release). */
data class ShipItem(
    val number: Int,
    val title: String,
    val author: String,
    val mergedAt: String?,
    val url: String,
)

data class ReleaseEntry(
    val tag: String,
    val name: String,
    val publishedAt: String,
    val url: String,
    val isLatest: Boolean,
    /** null = not loaded yet (older releases load on demand). */
    val prs: List<ShipItem>?,
    val previousTag: String?,
)

data class RepoData(
    val config: RepositoryConfig,
    val webUrl: String,
    val latestTag: String?,
    val latestPublishedAt: String?,
    val open: List<GitHubPR>,
    val drafts: List<GitHubPR>,
    val unreleased: List<ShipItem>,
    val releases: List<ReleaseEntry>,
) {
    val name: String get() = config.repo
    val owner: String get() = config.owner
    val initial: String get() = config.repo.firstOrNull()?.uppercase() ?: "?"
    fun compareUrl(): String =
        if (latestTag != null) "$webUrl/compare/$latestTag...${config.defaultBranch}" else "$webUrl/commits/${config.defaultBranch}"
}

enum class SyncProblem { TokenExpired, RateLimited, Unreachable }

data class InboxData(
    val repos: List<RepoData>,
    val problem: SyncProblem? = null,
    val rateResetAt: Long? = null,
    val syncedAt: Long? = null,
)

enum class Checks { Passing, Failing, Pending, None }

data class PrDetail(
    val checks: Checks,
    val reviews: String,
    val comments: Int,
    val adds: Int,
    val dels: Int,
)

/** github.com web URL for an API base (handles Enterprise Server and ghe.com). */
fun webBaseOf(apiBase: String): String {
    val b = apiBase.trimEnd('/')
    return when {
        b == "https://api.github.com" -> "https://github.com"
        b.endsWith("/api/v3") -> b.removeSuffix("/api/v3")
        b.startsWith("https://api.") -> "https://" + b.removePrefix("https://api.")
        else -> b
    }
}

private const val EAGER_RELEASES = 3

suspend fun loadInbox(client: GitHubClient, config: AppConfig, apiBase: String): InboxData = coroutineScope {
    val web = webBaseOf(apiBase)
    val results = config.repositories.map { rc -> async { loadRepo(client, rc, web) } }.awaitAll()
    val errors = results.mapNotNull { it.exceptionOrNull() as? APIError }
    val repos = results.mapNotNull { it.getOrNull() }
    val problem = when {
        errors.any { it.code == "INVALID_TOKEN" } -> SyncProblem.TokenExpired
        errors.any { it.code == "RATE_LIMIT" } -> SyncProblem.RateLimited
        repos.isEmpty() && errors.isNotEmpty() && errors.all { it.code == "NETWORK_ERROR" } -> SyncProblem.Unreachable
        else -> null
    }
    InboxData(
        repos = repos,
        problem = problem,
        rateResetAt = errors.firstOrNull { it.code == "RATE_LIMIT" }?.resetAt,
        syncedAt = if (problem == null) nowMs() else null,
    )
}

private suspend fun loadRepo(client: GitHubClient, rc: RepositoryConfig, web: String): Result<RepoData> = coroutineScope {
    val openD = async { client.getOpenPRs(rc.owner, rc.repo) }
    val closedD = async { client.getClosedPRs(rc.owner, rc.repo) }
    val relD = async { client.getReleases(rc.owner, rc.repo) }
    val pulls = openD.await().getOrElse { return@coroutineScope Result.failure(it) }
    val closed = closedD.await().getOrElse { emptyList() }
    val rels = relD.await().getOrElse { if ((it as? APIError)?.code == "NOT_FOUND") emptyList() else return@coroutineScope Result.failure(it) }
        .sortedByDescending { it.published_at }

    val latest = rels.firstOrNull()
    val latestMs = isoToMs(latest?.published_at)
    val defaultBranch = rc.defaultBranch
    val unreleased = closed
        .filter { it.merged_at != null && (it.base.ref.isEmpty() || it.base.ref == defaultBranch) }
        .filter { latestMs == null || (isoToMs(it.merged_at) ?: 0) > latestMs }
        .sortedByDescending { it.merged_at }
        .map { ShipItem(it.number, it.title, it.user.login, it.merged_at, it.html_url) }

    val releases = rels.mapIndexed { i, r ->
        val prev = rels.getOrNull(i + 1)?.tag_name
        val prs = if (i < EAGER_RELEASES) releasePrs(client, rc, web, prev, r.tag_name) else null
        ReleaseEntry(r.tag_name, r.name.ifBlank { r.tag_name }, r.published_at, r.html_url, i == 0, prs, prev)
    }

    Result.success(
        RepoData(
            config = rc,
            webUrl = "$web/${rc.owner}/${rc.repo}",
            latestTag = latest?.tag_name,
            latestPublishedAt = latest?.published_at,
            open = pulls.filter { !it.draft },
            drafts = pulls.filter { it.draft },
            unreleased = unreleased,
            releases = releases,
        )
    )
}

/** Pull requests between two tags, read from merge / squash commit messages. */
suspend fun releasePrs(client: GitHubClient, rc: RepositoryConfig, web: String, prevTag: String?, tag: String): List<ShipItem> {
    if (prevTag == null) return emptyList()
    val cmp = client.compareBranches(rc.owner, rc.repo, prevTag, tag).getOrNull() ?: return emptyList()
    return parseCompare(cmp, "$web/${rc.owner}/${rc.repo}")
}

private val MERGE_RE = Regex("^Merge pull request #(\\d+)")
private val SQUASH_RE = Regex("\\(#(\\d+)\\)\\s*$")

fun parseCompare(cmp: GitHubCompareResponse, repoWeb: String): List<ShipItem> =
    cmp.commits.mapNotNull { c ->
        val lines = c.commit.message.lines()
        val first = lines.firstOrNull().orEmpty()
        val merge = MERGE_RE.find(first)
        val (n, title) = when {
            merge != null -> merge.groupValues[1].toInt() to (lines.drop(1).firstOrNull { it.isNotBlank() }?.trim() ?: first)
            else -> {
                val sq = SQUASH_RE.find(first) ?: return@mapNotNull null
                sq.groupValues[1].toInt() to first.removeRange(sq.range).trim()
            }
        }
        ShipItem(n, title, c.commit.author.name, c.commit.author.date, "$repoWeb/pull/$n")
    }.distinctBy { it.number }.reversed()

suspend fun loadPrDetail(client: GitHubClient, owner: String, repo: String, pr: GitHubPR): PrDetail? = coroutineScope {
    val fullD = async { client.getPullFull(owner, repo, pr.number) }
    val reviewsD = async { client.getReviews(owner, repo, pr.number) }
    val runsD = async { if (pr.head.sha.isBlank()) null else client.getCheckRuns(owner, repo, pr.head.sha).getOrNull() }
    val statusD = async { if (pr.head.sha.isBlank()) null else client.getCombinedStatus(owner, repo, pr.head.sha).getOrNull() }
    val full = fullD.await().getOrNull() ?: return@coroutineScope null

    val latestByUser = reviewsD.await().getOrElse { emptyList() }
        .filter { it.state != "COMMENTED" && it.state != "PENDING" }
        .associateBy { it.user?.login }
    val approvals = latestByUser.values.count { it.state == "APPROVED" }
    val reviews = when {
        latestByUser.values.any { it.state == "CHANGES_REQUESTED" } -> "Changes requested"
        approvals == 1 -> "1 approval"
        approvals > 1 -> "$approvals approvals"
        else -> "No reviews"
    }

    val runs = runsD.await()?.check_runs.orEmpty()
    val status = statusD.await()
    val failed = setOf("failure", "timed_out", "cancelled", "action_required", "startup_failure")
    val checks = when {
        runs.any { it.conclusion in failed } || status?.state == "failure" || status?.state == "error" -> Checks.Failing
        runs.any { it.status != "completed" } || (status?.state == "pending" && status.total_count > 0) -> Checks.Pending
        runs.isNotEmpty() || (status?.total_count ?: 0) > 0 -> Checks.Passing
        else -> Checks.None
    }
    PrDetail(checks, reviews, full.comments + full.review_comments, full.additions, full.deletions)
}
