package com.example.repodashboard.github

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

// Response shapes for the inbox's GraphQL queries. Only the fields we ask for.

@Serializable
data class GqlRequest(val query: String, val variables: Map<String, JsonElement>)

@Serializable
data class GqlError(val type: String? = null, val message: String = "")

@Serializable
data class GqlResponse<T>(val data: T? = null, val errors: List<GqlError>? = null)

@Serializable
data class RepoWrap<T>(val repository: T? = null)

@Serializable
data class PageInfo(val hasNextPage: Boolean = false, val endCursor: String? = null)

@Serializable
data class GqlActor(val login: String = "ghost")

@Serializable
data class GqlName(val name: String = "")

@Serializable
data class GqlTitle(val title: String = "")

@Serializable
data class GqlLabels(val nodes: List<GqlLabel> = emptyList())

@Serializable
data class GqlLabel(val name: String = "", val color: String = "")

@Serializable
data class GqlState(val state: String = "")

@Serializable
data class GqlReviews(val nodes: List<GqlState> = emptyList())

@Serializable
data class GqlRollupCommit(val statusCheckRollup: GqlState? = null)

@Serializable
data class GqlCommitNode(val commit: GqlRollupCommit = GqlRollupCommit())

@Serializable
data class GqlCommits(val nodes: List<GqlCommitNode> = emptyList())

@Serializable
data class GqlPrDetail(
    val number: Int = 0,
    val additions: Int = 0,
    val deletions: Int = 0,
    val totalCommentsCount: Int = 0,
    val reviewDecision: String? = null,
    val latestReviews: GqlReviews? = null,
    val commits: GqlCommits? = null,
)

@Serializable
data class GqlPr(
    val number: Int,
    val title: String,
    val url: String = "",
    val isDraft: Boolean = false,
    val createdAt: String = "",
    val updatedAt: String = "",
    val mergedAt: String? = null,
    val body: String? = null,
    val author: GqlActor? = null,
    val headRefName: String = "",
    val baseRefName: String = "",
    val milestone: GqlTitle? = null,
    val labels: GqlLabels? = null,
    val additions: Int = 0,
    val deletions: Int = 0,
    val totalCommentsCount: Int = 0,
    val reviewDecision: String? = null,
    val latestReviews: GqlReviews? = null,
    val commits: GqlCommits? = null,
)

@Serializable
data class PrConnection(val pageInfo: PageInfo = PageInfo(), val nodes: List<GqlPr> = emptyList())

@Serializable
data class PrsRepo(val defaultBranchRef: GqlName? = null, val pullRequests: PrConnection = PrConnection())

@Serializable
data class GqlRelease(
    val tagName: String = "",
    val name: String? = null,
    val url: String = "",
    val publishedAt: String? = null,
    val isDraft: Boolean = false,
)

@Serializable
data class ReleaseConnection(val pageInfo: PageInfo = PageInfo(), val nodes: List<GqlRelease> = emptyList())

@Serializable
data class ReleasesRepo(val releases: ReleaseConnection = ReleaseConnection())

/** First page of everything for one repo, in one query. */
@Serializable
data class FirstPageRepo(
    val defaultBranchRef: GqlName? = null,
    val open: PrConnection = PrConnection(),
    val merged: PrConnection = PrConnection(),
    val releases: ReleaseConnection = ReleaseConnection(),
)

object Queries {
    private const val PR_FIELDS = """
        number title url isDraft createdAt updatedAt mergedAt body
        author { login } headRefName baseRefName
        milestone { title }
        labels(first: 10) { nodes { name color } }
    """

    /**
     * First page of open PRs, merged PRs and releases for one repo. Sizes are picked so the query costs 1 point:
     * cost ~ (3 connections + 50 + 40 label lookups) / 100.
     */
    const val FIRST_PAGE = """
        query(${'$'}owner: String!, ${'$'}name: String!) {
          repository(owner: ${'$'}owner, name: ${'$'}name) {
            defaultBranchRef { name }
            open: pullRequests(states: OPEN, first: 50, orderBy: { field: UPDATED_AT, direction: DESC }) {
              pageInfo { hasNextPage endCursor }
              nodes { $PR_FIELDS }
            }
            merged: pullRequests(states: MERGED, first: 40, orderBy: { field: UPDATED_AT, direction: DESC }) {
              pageInfo { hasNextPage endCursor }
              nodes { $PR_FIELDS }
            }
            releases(first: 100, orderBy: { field: CREATED_AT, direction: DESC }) {
              pageInfo { hasNextPage endCursor }
              nodes { tagName name url publishedAt isDraft }
            }
          }
        }
    """

    /** Open PRs (drafts included), list fields only. Checks/reviews come from [prDetails]. */
    const val OPEN_PRS = """
        query(${'$'}owner: String!, ${'$'}name: String!, ${'$'}after: String) {
          repository(owner: ${'$'}owner, name: ${'$'}name) {
            defaultBranchRef { name }
            pullRequests(states: OPEN, first: 100, after: ${'$'}after, orderBy: { field: UPDATED_AT, direction: DESC }) {
              pageInfo { hasNextPage endCursor }
              nodes { $PR_FIELDS }
            }
          }
        }
    """

    private const val DETAIL_FIELDS = """
        number additions deletions totalCommentsCount reviewDecision
        latestReviews(first: 10) { nodes { state } }
        commits(last: 1) { nodes { commit { statusCheckRollup { state } } } }
    """

    /**
     * Checks, reviews and sizes for PRs across repos in one query: one aliased repository per repo,
     * one aliased pullRequest per PR. Keep the total small (<= 25 PRs).
     */
    fun prDetails(targets: List<Triple<String, String, Int>>): String = buildString {
        append("query {")
        targets.groupBy { it.first to it.second }.entries.forEachIndexed { i, (repo, prs) ->
            append(" r$i: repository(owner: ${quote(repo.first)}, name: ${quote(repo.second)}) {")
            prs.forEach { append(" p${it.third}: pullRequest(number: ${it.third}) { $DETAIL_FIELDS }") }
            append(" }")
        }
        append(" }")
    }

    private fun quote(s: String) = "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"") + "\""

    /** Merged PRs, most recently updated first; used for Unreleased and recent release contents. */
    const val MERGED_PRS = """
        query(${'$'}owner: String!, ${'$'}name: String!, ${'$'}after: String) {
          repository(owner: ${'$'}owner, name: ${'$'}name) {
            pullRequests(states: MERGED, first: 100, after: ${'$'}after, orderBy: { field: UPDATED_AT, direction: DESC }) {
              pageInfo { hasNextPage endCursor }
              nodes { $PR_FIELDS }
            }
          }
        }
    """

    const val RELEASES = """
        query(${'$'}owner: String!, ${'$'}name: String!, ${'$'}after: String) {
          repository(owner: ${'$'}owner, name: ${'$'}name) {
            releases(first: 100, after: ${'$'}after, orderBy: { field: CREATED_AT, direction: DESC }) {
              pageInfo { hasNextPage endCursor }
              nodes { tagName name url publishedAt isDraft }
            }
          }
        }
    """
}
