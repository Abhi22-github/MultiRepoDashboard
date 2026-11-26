package com.example.repodashboard.data

import com.example.repodashboard.github.*
import com.example.repodashboard.models.*
import kotlinx.coroutines.async
import kotlinx.serialization.Serializable
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

/** A merged pull request (unreleased, or part of a release). */
@Serializable
data class ShipItem(
    val number: Int,
    val title: String,
    val author: String,
    val mergedAt: String?,
    val url: String,
    val labels: List<String> = emptyList(),
)

@Serializable
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

@Serializable
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

@Serializable
enum class SyncProblem { TokenExpired, RateLimited, Unreachable }

@Serializable
data class InboxData(
    val repos: List<RepoData>,
    val problem: SyncProblem? = null,
    val rateResetAt: Long? = null,
    val syncedAt: Long? = null,
)

@Serializable
enum class Checks { Passing, Failing, Pending, None }

@Serializable
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

/** How the inbox talks to GitHub. Chosen in Developer tools. */
@Serializable
enum class ApiMode { Rest, GraphQL, Hybrid }

private const val EAGER_RELEASES = 3
private const val MAX_OPEN_PAGES = 20      // 2,000 open PRs per repo
private const val MAX_MERGED_PAGES = 5     // ~440-500 merged PRs per repo
private const val MAX_RELEASE_PAGES = 20   // 2,000 releases per repo

suspend fun loadInbox(client: GitHubClient, config: AppConfig, apiBase: String, mode: ApiMode = ApiMode.Rest): InboxData = coroutineScope {
    val web = webBaseOf(apiBase)
    val results = config.repositories.map { rc ->
        async { if (mode == ApiMode.GraphQL) loadRepoGraphQL(client, rc, web) else loadRepoRest(client, rc, web) }
    }.awaitAll()
    val errors = results.mapNotNull { it.exceptionOrNull() as? APIError }.toMutableList()
    var repos = results.mapNotNull { it.getOrNull() }

    // Checks/reviews for every ready-for-review PR. Drafts load theirs when opened.
    val targets = repos.flatMap { r -> r.open.map { Triple(r.owner, r.name, it.number) } }
    val (details, detailError) = when (mode) {
        ApiMode.Rest -> loadPrDetailsRest(client, repos)
        else -> loadPrDetails(client, targets)
    }
    detailError?.let { errors += it }
    repos = repos.map { r -> r.copy(details = details.filterKeys { it.first == r.owner && it.second == r.name }.mapKeys { it.key.third }) }

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

/**
 * Continues a connection from [start] until it ends, [maxPages] more pages are read, or [stop] says enough.
 * Returns all nodes (first page included) and whether the end (or [stop]) was reached.
 */
private suspend fun <R, N> paged(
    first: Pair<PageInfo, List<N>>,
    maxPages: Int,
    fetch: suspend (after: String?) -> Result<R>,
    page: (R) -> Pair<PageInfo, List<N>>,
    stop: (List<N>) -> Boolean = { false },
): Result<Pair<List<N>, Boolean>> {
    val all = first.second.toMutableList()
    var info = first.first
    if (!info.hasNextPage || stop(first.second)) return Result.success(all to true)
    repeat(maxPages) {
        val r = fetch(info.endCursor).getOrElse { return Result.failure(it) }
        val (next, nodes) = page(r)
        all += nodes
        if (!next.hasNextPage || stop(nodes)) return Result.success(all to true)
        info = next
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

private fun GqlPrDetail.toDetail(): PrDetail {
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

/** A published release, whichever API it came from. */
private data class Rel(val tag: String, val name: String, val url: String, val publishedAt: String)

/** Merged PRs reach back to the 4th-latest release so the 3 newest can be filled in without extra calls. */
private fun mergedCutoffMs(rels: List<Rel>): Long? = rels.getOrNull(minOf(EAGER_RELEASES, rels.size - 1))?.let { isoToMs(it.publishedAt) }

/** Builds a repo's inbox data from its lists; shared by the REST and GraphQL loaders. */
private fun assembleRepo(
    rc: RepositoryConfig, web: String, defaultBranch: String,
    prs: List<GitHubPR>, merged: List<GitHubPR>, mergedComplete: Boolean, relsIn: List<Rel>,
): RepoData {
    val rels = relsIn.sortedByDescending { it.publishedAt }
    // How far back the merged list reaches; release windows older than this are loaded on demand instead.
    val coveredFromMs = if (mergedComplete) Long.MIN_VALUE else merged.minOfOrNull { isoToMs(it.updated_at) ?: Long.MAX_VALUE } ?: Long.MAX_VALUE
    val mergedToDefault = merged
        .filter { it.merged_at != null && (it.base.ref.isEmpty() || it.base.ref == defaultBranch) }
        .map { it to (isoToMs(it.merged_at) ?: 0L) }
        .sortedByDescending { it.second }
    fun ship(p: GitHubPR) = ShipItem(p.number, p.title, p.user.login, p.merged_at, p.html_url, p.labels.map { it.name })

    val latestMs = rels.firstOrNull()?.let { isoToMs(it.publishedAt) }
    val unreleased = mergedToDefault.filter { latestMs == null || it.second > latestMs }.map { ship(it.first) }
    val releases = rels.mapIndexed { i, r ->
        val prev = rels.getOrNull(i + 1)
        val hi = isoToMs(r.publishedAt) ?: 0L
        val lo = prev?.let { isoToMs(it.publishedAt) }
        val list = when {
            prev == null -> emptyList()                              // first release: nothing to compare with
            i < EAGER_RELEASES && lo != null && lo >= coveredFromMs ->
                mergedToDefault.filter { it.second in (lo + 1)..hi }.map { ship(it.first) }
            else -> null                                             // older: loaded when opened
        }
        ReleaseEntry(r.tag, r.name, r.publishedAt, r.url, i == 0, list, prev?.tag)
    }
    return RepoData(
        config = rc.copy(defaultBranch = defaultBranch),
        webUrl = "$web/${rc.owner}/${rc.repo}",
        latestTag = rels.firstOrNull()?.tag,
        latestPublishedAt = rels.firstOrNull()?.publishedAt,
        open = prs.filter { !it.draft },
        drafts = prs.filter { it.draft },
        unreleased = unreleased,
        releases = releases,
    )
}

// ── REST loader (REST and Hybrid modes) ──────────────────────────

private suspend fun loadRepoRest(client: GitHubClient, rc: RepositoryConfig, web: String): Result<RepoData> = coroutineScope {
    val openD = async { client.listPulls(rc.owner, rc.repo, "open", MAX_OPEN_PAGES) }
    val rels = client.listReleases(rc.owner, rc.repo, MAX_RELEASE_PAGES).getOrElse { return@coroutineScope Result.failure(it) }
        .filter { !it.draft && it.published_at != null }
        .map { Rel(it.tag_name, it.name?.takeIf { n -> n.isNotBlank() } ?: it.tag_name, it.html_url, it.published_at!!) }
        .sortedByDescending { it.publishedAt }
    val cutoffMs = mergedCutoffMs(rels)
    // Closed PRs sorted by updated: once a whole page is older than the cutoff, nothing later can be newer.
    val (closed, complete) = client.listPulls(rc.owner, rc.repo, "closed", if (cutoffMs == null) 1 else MAX_MERGED_PAGES,
        stop = { page -> cutoffMs != null && page.all { (isoToMs(it.updated_at) ?: 0) < cutoffMs } })
        .getOrElse { return@coroutineScope Result.failure(it) }
    val prs = openD.await().getOrElse { return@coroutineScope Result.failure(it) }.first
    Result.success(assembleRepo(rc, web, rc.defaultBranch, prs, closed.filter { it.merged_at != null }, complete, rels))
}

/** REST mode: 4 calls per ready-for-review PR (size, reviews, check runs, commit status). */
private suspend fun loadPrDetailsRest(client: GitHubClient, repos: List<RepoData>): Pair<Map<Triple<String, String, Int>, PrDetail>, APIError?> = coroutineScope {
    val jobs = repos.flatMap { r -> r.open.map { pr -> async { Triple(r.owner, r.name, pr.number) to loadPrDetailRest(client, r.owner, r.name, pr) } } }
    val results = jobs.awaitAll()
    val out = results.mapNotNull { (k, v) -> v.getOrNull()?.let { k to it } }.toMap()
    out to results.firstNotNullOfOrNull { it.second.exceptionOrNull() as? APIError }
}

suspend fun loadPrDetailRest(client: GitHubClient, owner: String, repo: String, pr: GitHubPR): Result<PrDetail> = coroutineScope {
    val fullD = async { client.getPullFull(owner, repo, pr.number) }
    val reviewsD = async { client.getReviews(owner, repo, pr.number) }
    val runsD = async { if (pr.head.sha.isBlank()) null else client.getCheckRuns(owner, repo, pr.head.sha).getOrNull() }
    val statusD = async { if (pr.head.sha.isBlank()) null else client.getCombinedStatus(owner, repo, pr.head.sha).getOrNull() }
    val full = fullD.await().getOrElse { return@coroutineScope Result.failure(it) }

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
    Result.success(PrDetail(checks, reviews, full.comments + full.review_comments, full.additions, full.deletions))
}

// ── GraphQL loader ───────────────────────────────────────────────

private suspend fun loadRepoGraphQL(client: GitHubClient, rc: RepositoryConfig, web: String): Result<RepoData> = coroutineScope {
    val vars = { after: String? -> mapOf("owner" to rc.owner, "name" to rc.repo, "after" to after) }
    val prPage = { r: RepoWrap<PrsRepo> -> r.repository?.pullRequests?.let { it.pageInfo to it.nodes } ?: (PageInfo() to emptyList()) }

    val firstPage = client.graphql<RepoWrap<FirstPageRepo>>(Queries.FIRST_PAGE, mapOf("owner" to rc.owner, "name" to rc.repo))
        .getOrElse { return@coroutineScope Result.failure(it) }.repository
        ?: return@coroutineScope Result.failure(APIError("NOT_FOUND", "${rc.owner}/${rc.repo} not found", 404))

    // Most repos fit in the first page; only follow cursors where needed.
    val releasesD = async {
        paged(firstPage.releases.pageInfo to firstPage.releases.nodes, MAX_RELEASE_PAGES,
            { client.graphql<RepoWrap<ReleasesRepo>>(Queries.RELEASES, vars(it)) },
            { r -> r.repository?.releases?.let { it.pageInfo to it.nodes } ?: (PageInfo() to emptyList()) })
    }
    val openD = async {
        paged(firstPage.open.pageInfo to firstPage.open.nodes, MAX_OPEN_PAGES,
            { client.graphql<RepoWrap<PrsRepo>>(Queries.OPEN_PRS, vars(it)) }, prPage)
    }
    val rels = releasesD.await().getOrElse { return@coroutineScope Result.failure(it) }.first
        .filter { !it.isDraft && it.publishedAt != null }
        .map { Rel(it.tagName, it.name?.takeIf { n -> n.isNotBlank() } ?: it.tagName, it.url, it.publishedAt!!) }
        .sortedByDescending { it.publishedAt }
    val cutoffMs = mergedCutoffMs(rels)
    val (merged, mergedComplete) = paged(
        firstPage.merged.pageInfo to firstPage.merged.nodes, if (cutoffMs == null) 0 else MAX_MERGED_PAGES,
        { client.graphql<RepoWrap<PrsRepo>>(Queries.MERGED_PRS, vars(it)) }, prPage,
        stop = { nodes -> cutoffMs != null && nodes.all { (isoToMs(it.updatedAt) ?: 0) < cutoffMs } },
    ).getOrElse { return@coroutineScope Result.failure(it) }
    val openNodes = openD.await().getOrElse { return@coroutineScope Result.failure(it) }.first
    val defaultBranch = firstPage.defaultBranchRef?.name ?: rc.defaultBranch
    Result.success(assembleRepo(rc, web, defaultBranch, openNodes.map { it.toPR() }, merged.map { it.toPR() }, mergedComplete, rels))
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

private const val DETAIL_BATCH = 25

/**
 * Checks, reviews and sizes for PRs (owner, name, number), [DETAIL_BATCH] per query across repos.
 * Stops at the first error; PRs it didn't reach simply have no detail.
 */
suspend fun loadPrDetails(client: GitHubClient, targets: List<Triple<String, String, Int>>): Pair<Map<Triple<String, String, Int>, PrDetail>, APIError?> {
    val out = mutableMapOf<Triple<String, String, Int>, PrDetail>()
    targets.chunked(DETAIL_BATCH).forEach { chunk ->
        val repoKeys = chunk.map { it.first to it.second }.distinct()
        val r = client.graphql<Map<String, Map<String, GqlPrDetail?>?>>(Queries.prDetails(chunk), emptyMap())
        val data = r.getOrElse { return out to (it as? APIError) }
        data.forEach { (alias, prs) ->
            val (owner, name) = repoKeys.getOrNull(alias.removePrefix("r").toIntOrNull() ?: -1) ?: return@forEach
            prs?.values?.filterNotNull()?.forEach { out[Triple(owner, name, it.number)] = it.toDetail() }
        }
    }
    return out to null
}

// ── Browser cache ────────────────────────────────────────────────

/** Reuse a cached load for this long before refreshing on open. */
const val CACHE_FRESH_MS = 10 * 60 * 1000L

@Serializable
private data class InboxCache(val apiBase: String, val repos: List<String>, val data: InboxData)

private val cacheJson = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }

private fun repoKeys(config: AppConfig) = config.repositories.map { "${it.owner}/${it.repo}" }.sorted()

fun encodeInboxCache(data: InboxData, config: AppConfig, apiBase: String): String =
    cacheJson.encodeToString(InboxCache.serializer(), InboxCache(apiBase, repoKeys(config), data))

/** The cached load, if it was made for the same server and the same repo selection. */
fun decodeInboxCache(json: String?, config: AppConfig, apiBase: String): InboxData? {
    if (json == null) return null
    val c = try { cacheJson.decodeFromString(InboxCache.serializer(), json) } catch (_: Throwable) { return null }
    return if (c.apiBase == apiBase && c.repos == repoKeys(config)) c.data else null
}
