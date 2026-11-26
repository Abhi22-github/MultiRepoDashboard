package com.example.repodashboard.data

import com.example.repodashboard.github.*
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
    val labels: List<String> = emptyList(),
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
    /** Checks, reviews and sizes for open PRs and drafts, by PR number. */
    val details: Map<Int, PrDetail> = emptyMap(),
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
private const val MAX_OPEN_PAGES = 20      // 2,000 open PRs per repo
private const val MAX_MERGED_PAGES = 5     // 500 merged PRs per repo
private const val MAX_RELEASE_PAGES = 20   // 2,000 releases per repo

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

/** Follows a connection's cursor until it ends, [maxPages] is reached, or [stop] says enough. */
private suspend fun <R, N> paged(
    maxPages: Int,
    fetch: suspend (after: String?) -> Result<R>,
    page: (R) -> Pair<PageInfo, List<N>>,
    stop: (List<N>) -> Boolean = { false },
): Result<Pair<List<N>, Boolean>> {
    val all = mutableListOf<N>()
    var after: String? = null
    repeat(maxPages) {
        val r = fetch(after).getOrElse { return Result.failure(it) }
        val (info, nodes) = page(r)
        all += nodes
        if (!info.hasNextPage || stop(nodes)) return Result.success(all to true)
        after = info.endCursor
    }
    return Result.success(all to false)
}

private fun GqlPr.toPR(): GitHubPR = GitHubPR(
    number = number, title = title, user = GitHubPRUser(author?.login ?: "ghost"),
    draft = isDraft, created_at = createdAt, updated_at = updatedAt, merged_at = mergedAt, html_url = url,
    labels = labels?.nodes.orEmpty().map { GitHubPRLabel(it.name, it.color) },
    head = GitHubRef(headRefName), base = GitHubRef(baseRefName),
    milestone = milestone?.let { GitHubMilestone(it.title) },
)

private fun GqlPr.toDetail(): PrDetail {
    val approvals = latestReviews?.nodes.orEmpty().count { it.state == "APPROVED" }
    val reviews = when {
        reviewDecision == "CHANGES_REQUESTED" -> "Changes requested"
        approvals == 1 -> "1 approval"
        approvals > 1 -> "$approvals approvals"
        else -> "No reviews"
    }
    val checks = when (commits?.nodes?.firstOrNull()?.commit?.statusCheckRollup?.state) {
        "SUCCESS" -> Checks.Passing
        "FAILURE", "ERROR" -> Checks.Failing
        "PENDING", "EXPECTED" -> Checks.Pending
        else -> Checks.None
    }
    return PrDetail(checks, reviews, totalCommentsCount, additions, deletions)
}

private suspend fun loadRepo(client: GitHubClient, rc: RepositoryConfig, web: String): Result<RepoData> = coroutineScope {
    val vars = { after: String? -> mapOf("owner" to rc.owner, "name" to rc.repo, "after" to after) }

    val releasesD = async {
        paged(MAX_RELEASE_PAGES, { client.graphql<RepoWrap<ReleasesRepo>>(Queries.RELEASES, vars(it)) },
            { r -> r.repository?.releases?.let { it.pageInfo to it.nodes } ?: (PageInfo() to emptyList()) })
    }
    val openD = async {
        var branch: String? = null
        paged(MAX_OPEN_PAGES, { client.graphql<RepoWrap<PrsRepo>>(Queries.OPEN_PRS, vars(it)) },
            { r -> branch = branch ?: r.repository?.defaultBranchRef?.name; r.repository?.pullRequests?.let { it.pageInfo to it.nodes } ?: (PageInfo() to emptyList()) })
            .map { it.first to branch }
    }

    val rels = releasesD.await().getOrElse { return@coroutineScope Result.failure(it) }.first
        .filter { !it.isDraft && it.publishedAt != null }
        .sortedByDescending { it.publishedAt }

    // Merged PRs back to the oldest release we show eagerly. Ordered by updatedAt, which is always >= mergedAt,
    // so once a whole page is older than the cutoff nothing after it can be newer.
    val cutoffMs = rels.getOrNull(minOf(EAGER_RELEASES, rels.size - 1))?.let { isoToMs(it.publishedAt) }
    val mergedD = async {
        paged(if (cutoffMs == null) 1 else MAX_MERGED_PAGES, { client.graphql<RepoWrap<PrsRepo>>(Queries.MERGED_PRS, vars(it)) },
            { r -> r.repository?.pullRequests?.let { it.pageInfo to it.nodes } ?: (PageInfo() to emptyList()) },
            stop = { nodes -> cutoffMs != null && nodes.all { (isoToMs(it.updatedAt) ?: 0) < cutoffMs } })
    }

    val (openNodes, branch) = openD.await().getOrElse { return@coroutineScope Result.failure(it) }
    val (merged, mergedComplete) = mergedD.await().getOrElse { return@coroutineScope Result.failure(it) }
    val defaultBranch = branch ?: rc.defaultBranch
    // How far back the merged list reaches; release windows older than this are loaded on demand instead.
    val coveredFromMs = if (mergedComplete) Long.MIN_VALUE else merged.minOfOrNull { isoToMs(it.updatedAt) ?: Long.MAX_VALUE } ?: Long.MAX_VALUE

    val mergedToDefault = merged
        .filter { it.mergedAt != null && (it.baseRefName.isEmpty() || it.baseRefName == defaultBranch) }
        .map { it to (isoToMs(it.mergedAt) ?: 0L) }
        .sortedByDescending { it.second }
    fun ship(p: GqlPr) = ShipItem(p.number, p.title, p.author?.login ?: "ghost", p.mergedAt, p.url, p.labels?.nodes.orEmpty().map { it.name })

    val latestMs = rels.firstOrNull()?.let { isoToMs(it.publishedAt) }
    val unreleased = mergedToDefault.filter { latestMs == null || it.second > latestMs }.map { ship(it.first) }

    val releases = rels.mapIndexed { i, r ->
        val prev = rels.getOrNull(i + 1)
        val hi = isoToMs(r.publishedAt) ?: 0L
        val lo = prev?.let { isoToMs(it.publishedAt) }
        val prs = when {
            prev == null -> emptyList()                              // first release: nothing to compare with
            i < EAGER_RELEASES && lo != null && lo >= coveredFromMs ->
                mergedToDefault.filter { it.second in (lo + 1)..hi }.map { ship(it.first) }
            else -> null                                             // older: loaded when opened
        }
        ReleaseEntry(r.tagName, r.name?.takeIf { it.isNotBlank() } ?: r.tagName, r.publishedAt!!, r.url, i == 0, prs, prev?.tagName)
    }

    val prs = openNodes.map { it.toPR() }
    Result.success(
        RepoData(
            config = rc.copy(defaultBranch = defaultBranch),
            webUrl = "$web/${rc.owner}/${rc.repo}",
            latestTag = rels.firstOrNull()?.tagName,
            latestPublishedAt = rels.firstOrNull()?.publishedAt,
            open = prs.filter { !it.draft },
            drafts = prs.filter { it.draft },
            unreleased = unreleased,
            releases = releases,
            details = openNodes.associate { it.number to it.toDetail() },
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
