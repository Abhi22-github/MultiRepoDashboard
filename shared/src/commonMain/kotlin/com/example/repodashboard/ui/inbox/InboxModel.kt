package com.example.repodashboard.ui.inbox

import androidx.compose.ui.graphics.Color
import com.example.repodashboard.data.*
import com.example.repodashboard.models.GitHubPR
import com.example.repodashboard.ui.theme.Tokens

enum class InboxView(val label: String, val title: String, val desc: String, val emptyTitle: String, val emptyBody: String) {
    Ship("Unreleased", "Unreleased", "Merged, not in the latest release.", "Nothing waiting", "Every merged pull request is already in a release."),
    Open("Open PRs", "Open pull requests", "Ready for review, newest first.", "No open pull requests", "There are no open PRs right now."),
    Drafts("Drafts", "Drafts", "Work in progress that is not ready for review.", "No draft pull requests", "Drafts you open will show up here."),
    Releases("Releases", "Releases", "What is waiting for the next release, then every published tag and its pull requests.", "No releases yet", "Tag a release on GitHub and it will appear here."),
}

enum class Kind { Pr, Ship, Next, Release }

enum class Tone { Warn, Ok, Fail, Muted }

data class Item(
    val key: String,
    val kind: Kind,
    val repo: RepoData,
    val title: String,
    val metaShort: String,
    val age: String,
    val pr: GitHubPR? = null,
    val draft: Boolean = false,
    val ship: ShipItem? = null,
    val release: ReleaseEntry? = null,
) {
    val author: String? get() = pr?.user?.login ?: ship?.author
    val milestone: String? get() = pr?.milestone?.title?.takeIf { it.isNotBlank() }
    val labels: List<String> get() = pr?.labels?.map { it.name } ?: ship?.labels.orEmpty()
    val hasPeople: Boolean get() = pr != null || ship != null
}

fun prKey(repo: RepoData, pr: GitHubPR) = "${repo.config.name}#${pr.number}"

fun buildItems(view: InboxView, repos: List<RepoData>, now: Long): List<Item> = buildList {
    repos.forEach { r ->
        when (view) {
            InboxView.Ship -> r.unreleased.forEach { s ->
                add(Item("u${r.config.name}#${s.number}", Kind.Ship, r, s.title, "#${s.number}", ageShort(s.mergedAt, now), ship = s))
            }
            InboxView.Open -> r.open.forEach { p ->
                add(Item("o${prKey(r, p)}", Kind.Pr, r, p.title, "#${p.number}", ageShort(p.updated_at, now), pr = p))
            }
            InboxView.Drafts -> r.drafts.forEach { p ->
                add(Item("d${prKey(r, p)}", Kind.Pr, r, p.title, "#${p.number}", ageShort(p.updated_at, now), pr = p, draft = true))
            }
            InboxView.Releases -> {
                if (r.unreleased.isNotEmpty()) add(Item("n${r.config.name}", Kind.Next, r, "Unreleased changes", "Not released yet", "${r.unreleased.size} waiting"))
                r.releases.forEach { rel ->
                    add(Item("r${r.config.name}@${rel.tag}", Kind.Release, r, rel.name, rel.tag, monthDay(rel.publishedAt), release = rel))
                }
            }
        }
    }
}

/** Status line + tone for a list row. PR rows use the lazily loaded [PrDetail]. */
fun statusOf(it: Item, detail: PrDetail?): Pair<String, Tone> = when (it.kind) {
    Kind.Ship -> "Merged · not in ${it.repo.latestTag ?: "any release"}" to Tone.Warn
    Kind.Next -> it.repo.unreleased.size.let { n -> "$n merged PR${if (n > 1) "s" else ""} not in any release yet" } to Tone.Warn
    Kind.Release -> (it.release?.prs?.let { "${it.size} pull requests" } ?: "Published ${monthDayYear(it.release?.publishedAt)}") to Tone.Muted
    Kind.Pr -> when {
        detail == null -> (if (it.draft) "Draft" else "Open") to Tone.Muted
        it.draft -> "Draft · ${checksWord(detail.checks)}" to Tone.Muted
        detail.checks == Checks.Failing -> "Checks failing · ${detail.reviews}" to Tone.Fail
        detail.checks == Checks.Pending -> "Checks running · ${detail.reviews}" to Tone.Muted
        detail.checks == Checks.None -> "No checks · ${detail.reviews}" to Tone.Ok
        else -> "Checks passing · ${detail.reviews}" to Tone.Ok
    }
}

fun checksWord(c: Checks) = when (c) {
    Checks.Passing -> "checks passing"; Checks.Failing -> "checks failing"; Checks.Pending -> "checks pending"; Checks.None -> "no checks"
}

fun toneColor(t: Tokens, tone: Tone): Color = when (tone) {
    Tone.Warn -> t.warn; Tone.Ok -> t.ok; Tone.Fail -> t.fail; Tone.Muted -> t.muted
}

data class Filters(val label: Set<String> = emptySet(), val milestone: Set<String> = emptySet(), val author: Set<String> = emptySet()) {
    val isEmpty: Boolean get() = label.isEmpty() && milestone.isEmpty() && author.isEmpty()
}

private val TOKEN_RE = Regex("^(label|milestone|author|owner):(.+)$", RegexOption.IGNORE_CASE)

/** Applies the filter menus plus `milestone:` / `author:` tokens and free words from the search box. */
fun applyFilters(items: List<Item>, f: Filters, query: String): List<Item> {
    val words = mutableListOf<String>()
    val tokMs = mutableListOf<String>()
    val tokAuthor = mutableListOf<String>()
    val tokLabel = mutableListOf<String>()
    query.lowercase().split(Regex("\\s+")).filter { it.isNotBlank() }.forEach { w ->
        val m = TOKEN_RE.find(w)
        when {
            m == null -> words += w
            m.groupValues[1] == "milestone" -> tokMs += m.groupValues[2]
            m.groupValues[1] == "label" -> tokLabel += m.groupValues[2]
            else -> tokAuthor += m.groupValues[2]
        }
    }
    val anyPeopleFilter = !f.isEmpty || tokMs.isNotEmpty() || tokAuthor.isNotEmpty() || tokLabel.isNotEmpty()
    return items.filter { it ->
        if (!it.hasPeople && anyPeopleFilter) return@filter false
        val ms = it.milestone ?: "No milestone"
        val author = it.author.orEmpty()
        if (f.milestone.isNotEmpty() && ms !in f.milestone) return@filter false
        if (f.author.isNotEmpty() && author !in f.author) return@filter false
        if (f.label.isNotEmpty() && it.labels.none { l -> l in f.label }) return@filter false
        if (tokLabel.any { v -> it.labels.none { l -> l.lowercase().contains(v) } }) return@filter false
        if (tokMs.any { v -> !ms.lowercase().contains(v) }) return@filter false
        if (tokAuthor.any { v -> !author.lowercase().contains(v) }) return@filter false
        val text = (listOf(it.title, it.repo.name, it.metaShort, author, it.milestone.orEmpty()) + it.labels).joinToString(" ").lowercase()
        words.all { w -> text.contains(w) }
    }
}

/** Options for a filter menu with counts, most common first. */
fun optionsOf(items: List<Item>, get: (Item) -> List<String>): List<Pair<String, Int>> =
    items.filter { it.hasPeople }.flatMap(get).groupingBy { it }.eachCount().entries
        .sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key })
        .map { it.key to it.value }
