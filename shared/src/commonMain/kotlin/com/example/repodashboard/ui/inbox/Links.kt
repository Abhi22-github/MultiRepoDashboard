package com.example.repodashboard.ui.inbox

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.repodashboard.data.RepoData
import com.example.repodashboard.data.isoToMs
import com.example.repodashboard.models.GitHubPR
import com.example.repodashboard.ui.theme.*

// ── Parsing a PR description ─────────────────────────────────────

data class JiraRef(val key: String, val title: String?, val url: String?)

data class DepRef(val owner: String, val repo: String, val number: Int, val url: String) {
    val key: String get() = "${owner.lowercase()}/${repo.lowercase()}#$number"
}

data class OtherLink(val kind: String, val label: String, val url: String)

data class PrLinks(val jira: List<JiraRef>, val deps: List<DepRef>, val other: List<OtherLink>) {
    val isEmpty: Boolean get() = jira.isEmpty() && deps.isEmpty() && other.isEmpty()
    val summary: String get() = listOfNotNull(
        jira.size.takeIf { it > 0 }?.let { "$it Jira" },
        deps.size.takeIf { it > 0 }?.let { "$it ${if (it == 1) "dependency" else "dependencies"}" },
        other.size.takeIf { it > 0 }?.let { "$it other" },
    ).joinToString(" · ")
}

private val MD_LINK = Regex("""(!?)\[([^\]]*)\]\((https?://[^\s)]+)\)""")
private val BARE_URL = Regex("""https?://[^\s)<>\]"'`]+""")
private val JIRA_KEY = Regex("""\b([A-Z][A-Z0-9]{1,9}-\d+)\b""")
private val JIRA_BROWSE = Regex("""^(https?://[^/]+)/browse/([A-Z][A-Z0-9]{1,9}-\d+)""")
private val NOT_JIRA = setOf("UTF", "ISO", "SHA", "RFC", "TLS", "SSL", "HTTP", "API")
private val DEP_HEADING = Regex("""^\s{0,3}#{1,6}\s*Dependencies\b.*$""", RegexOption.IGNORE_CASE)
private val ANY_HEADING = Regex("""^\s{0,3}#{1,6}\s+\S""")
private val PR_URL = Regex("""https?://[^\s/]+/([\w.-]+)/([\w.-]+)/pull/(\d+)""")
private val PR_FULL_REF = Regex("""(?<![\w/])([\w.-]+)/([\w.-]+)#(\d+)\b""")
private val PR_SHORT_REF = Regex("""(?<![\w/#])#(\d+)\b""")
private val IMAGE_EXT = Regex("""\.(png|jpe?g|gif|webp|svg|mp4|mov)(\?.*)?$""", RegexOption.IGNORE_CASE)

/** Jira keys, `## Dependencies` PRs and other links found in a PR body. */
fun parseLinks(body: String?, owner: String, repo: String, webBase: String): PrLinks {
    if (body.isNullOrBlank()) return PrLinks(emptyList(), emptyList(), emptyList())
    val text = body.replace(Regex("```[\\s\\S]*?```"), " ")

    // Dependencies section: from its heading to the next heading.
    val depLines = mutableListOf<String>()
    var inDeps = false
    text.lines().forEach { line ->
        when {
            DEP_HEADING.matches(line) -> inDeps = true
            inDeps && ANY_HEADING.containsMatchIn(line) -> inDeps = false
            inDeps -> depLines += line
        }
    }
    val deps = LinkedHashMap<String, DepRef>()
    var depText = depLines.joinToString("\n")
    PR_URL.findAll(depText).forEach { m ->
        val d = DepRef(m.groupValues[1], m.groupValues[2], m.groupValues[3].toInt(), m.value)
        deps.getOrPut(d.key) { d }
    }
    depText = PR_URL.replace(depText, " ")
    PR_FULL_REF.findAll(depText).forEach { m ->
        val (o, r, n) = m.destructured
        val d = DepRef(o, r, n.toInt(), "$webBase/$o/$r/pull/$n")
        deps.getOrPut(d.key) { d }
    }
    depText = PR_FULL_REF.replace(depText, " ")
    PR_SHORT_REF.findAll(depText).forEach { m ->
        val n = m.groupValues[1]
        val d = DepRef(owner, repo, n.toInt(), "$webBase/$owner/$repo/pull/$n")
        deps.getOrPut(d.key) { d }
    }
    val depUrls = deps.values.map { it.url }.toSet()

    // Every link, with its markdown text when it has one.
    val links = LinkedHashMap<String, String?>()
    MD_LINK.findAll(text).forEach { m -> if (m.groupValues[1].isEmpty()) links.getOrPut(m.groupValues[3]) { m.groupValues[2].trim() } }
    val noMd = MD_LINK.replace(text, " ")
    BARE_URL.findAll(noMd).forEach { m -> links.getOrPut(m.value.trimEnd('.', ',', ';', ':')) { null } }

    val jira = LinkedHashMap<String, JiraRef>()
    var jiraBase: String? = null
    links.forEach { (url, label) ->
        JIRA_BROWSE.find(url)?.let { m ->
            jiraBase = jiraBase ?: m.groupValues[1]
            val key = m.groupValues[2]
            jira[key] = JiraRef(key, label?.let { jiraTitle(it, key) }, url)
        }
    }
    val plain = BARE_URL.replace(noMd, " ") + " " + links.values.filterNotNull().joinToString(" ")
    JIRA_KEY.findAll(plain).forEach { m ->
        val key = m.groupValues[1]
        if (key.substringBefore('-') !in NOT_JIRA && key !in jira) jira[key] = JiraRef(key, null, jiraBase?.let { "$it/browse/$key" })
    }

    val other = links.filter { (url, _) ->
        JIRA_BROWSE.find(url) == null && url !in depUrls && PR_URL.matchEntire(url)?.let { m ->
            DepRef(m.groupValues[1], m.groupValues[2], m.groupValues[3].toInt(), url).key in deps
        } != true && !IMAGE_EXT.containsMatchIn(url) && !url.contains("/user-attachments/")
    }.map { (url, label) -> OtherLink(kindOf(url, webBase), label?.takeIf { it.isNotBlank() } ?: shortUrl(url), url) }

    return PrLinks(jira.values.toList(), deps.values.toList(), other)
}

private fun jiraTitle(label: String, key: String): String? =
    label.replace(key, "").trim().trimStart(':', '-', '–', '—', '|').trim().takeIf { it.isNotEmpty() }

private fun kindOf(url: String, webBase: String): String {
    val host = url.substringAfter("://").substringBefore('/').lowercase().removePrefix("www.")
    val ghHost = webBase.substringAfter("://").substringBefore('/').lowercase()
    return when {
        host.endsWith("figma.com") -> "Figma"
        host == "docs.google.com" || host == "drive.google.com" -> "Docs"
        host.contains("notion.") -> "Notion"
        host.contains("confluence") || (host.endsWith("atlassian.net") && url.contains("/wiki")) -> "Confluence"
        host == ghHost || host == "github.com" -> "GitHub"
        host.endsWith("slack.com") -> "Slack"
        host.endsWith("miro.com") -> "Miro"
        else -> "Link"
    }
}

private fun shortUrl(url: String): String = url.substringAfter("://").removePrefix("www.").let { if (it.length > 60) it.take(57) + "…" else it }

fun linksOf(body: String?, repo: RepoData): PrLinks =
    parseLinks(body, repo.owner, repo.name, repo.webUrl.substringBefore("/${repo.owner}/").ifBlank { "https://github.com" })

// ── Dependency status ────────────────────────────────────────────

enum class DepState(val label: String) {
    Released("Released"), MergedUnreleased("Merged · not released"), Merged("Merged"),
    Open("Open · in review"), Closed("Closed without merging"), Unknown("Status unknown"),
}

data class DepInfo(val title: String?, val state: DepState)

/** Status from the repos already loaded, or null when it has to be fetched. */
fun localDep(d: DepRef, repos: List<RepoData>): DepInfo? {
    val r = trackedRepo(d, repos) ?: return null
    (r.open + r.drafts).firstOrNull { it.number == d.number }?.let { return DepInfo(it.title, DepState.Open) }
    r.unreleased.firstOrNull { it.number == d.number }?.let { return DepInfo(it.title, DepState.MergedUnreleased) }
    r.releases.firstNotNullOfOrNull { rel -> rel.prs?.firstOrNull { it.number == d.number } }?.let { return DepInfo(it.title, DepState.Released) }
    return null
}

fun trackedRepo(d: DepRef, repos: List<RepoData>): RepoData? =
    repos.firstOrNull { it.owner.equals(d.owner, true) && it.name.equals(d.repo, true) }

/** Status of a PR fetched from GitHub. */
fun depInfoOf(p: GitHubPR, tracked: RepoData?): DepInfo {
    val state = when {
        p.state == "open" -> DepState.Open
        p.merged_at == null -> DepState.Closed
        tracked == null -> DepState.Merged
        (isoToMs(tracked.latestPublishedAt) ?: 0L) >= (isoToMs(p.merged_at) ?: Long.MAX_VALUE) -> DepState.Released
        else -> DepState.MergedUnreleased
    }
    return DepInfo(p.title, state)
}

@Composable
private fun depColors(s: DepState?): Pair<Color, Color> {
    val t = LocalTokens.current
    return when (s) {
        DepState.Released -> t.okSoft to t.ok
        DepState.MergedUnreleased -> t.warnSoft to t.warn
        DepState.Open -> t.accentSoft to t.accentText
        DepState.Closed -> t.failSoft to t.fail
        else -> t.pane to t.muted
    }
}

// ── "From the description" ───────────────────────────────────────

@Composable
fun DescriptionSection(ctx: InboxContext, body: String?, repo: RepoData) {
    val t = LocalTokens.current
    val links = remember(body, repo.config.name) { linksOf(body, repo) }
    LaunchedEffect(links) { links.deps.forEach { ctx.state.requestDep(it) } }
    Column(
        Modifier.fillMaxWidth().border(1.dp, t.line, RoundedCornerShape(12.dp)).padding(horizontal = 18.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), itemVerticalAlignment = Alignment.CenterVertically) {
            Txt("From the description", size = 15.sp, weight = FontWeight.Bold)
            if (!links.isEmpty) Txt(links.summary, size = 12.sp, color = t.muted)
        }
        if (links.isEmpty) {
            Txt("No links or Jira keys in this PR's description.", size = 13.sp, color = t.muted)
            return@Column
        }
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (links.jira.isNotEmpty()) LinkRow("Jira") {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    links.jira.forEach { JiraChip(it) }
                }
            }
            if (links.deps.isNotEmpty()) LinkRow("Dependencies") {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) { links.deps.forEach { DepRow(ctx, it) } }
            }
            if (links.other.isNotEmpty()) LinkRow("Other links") {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) { links.other.forEach { OtherRow(it) } }
            }
        }
    }
}

@Composable
private fun LinkRow(label: String, content: @Composable () -> Unit) {
    val t = LocalTokens.current
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Txt(label, Modifier.width(104.dp), size = 12.sp, weight = FontWeight.SemiBold, color = t.muted, lineHeight = 28.sp)
        Box(Modifier.weight(1f)) { content() }
    }
}

@Composable
fun JiraChip(j: JiraRef, small: Boolean = false) {
    val t = LocalTokens.current
    val uri = LocalUriHandler.current
    val shape = RoundedCornerShape(if (small) 6.dp else 8.dp)
    Row(
        Modifier.height(if (small) 22.dp else 28.dp).clip(shape).background(t.accentSoft)
            .then(if (j.url != null) Modifier.click { uri.openUri(j.url) } else Modifier)
            .padding(horizontal = if (small) 6.dp else 10.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Txt(j.key, size = if (small) 12.sp else 13.sp, weight = FontWeight.SemiBold, color = t.accentText, mono = true, maxLines = 1)
        if (!small && j.title != null) Txt(j.title, size = 12.sp, weight = FontWeight.Medium, color = t.muted, maxLines = 1)
        if (!small && j.url != null) Txt("↗", size = 12.sp, color = t.accentText)
    }
}

@Composable
private fun DepRow(ctx: InboxContext, d: DepRef) {
    val t = LocalTokens.current
    val uri = LocalUriHandler.current
    val info = ctx.state.depInfo(d)
    val (bg, fg) = depColors(info?.state)
    Row(
        Modifier.fillMaxWidth().height(32.dp).clip(RoundedCornerShape(8.dp)).background(t.bg).click { uri.openUri(d.url) }.padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Txt(d.repo, size = 13.sp, weight = FontWeight.SemiBold, maxLines = 1)
        Txt("#${d.number}", size = 12.sp, color = t.muted, mono = true, maxLines = 1)
        Txt(info?.title ?: "", Modifier.weight(1f), size = 13.sp, maxLines = 1)
        Chip(info?.state?.label ?: "…", bg, fg, size = 11.sp, radius = 6.dp)
    }
}

@Composable
private fun OtherRow(l: OtherLink) {
    val t = LocalTokens.current
    val uri = LocalUriHandler.current
    Row(
        Modifier.fillMaxWidth().height(28.dp).click { uri.openUri(l.url) },
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Chip(l.kind, t.bg, t.ink2, radius = 4.dp)
        Txt(l.label, Modifier.weight(1f, fill = false), size = 13.sp, color = t.accentText, maxLines = 1)
        Txt("↗", size = 12.sp, color = t.accentText)
    }
}

/** "⛓ N dependencies" chip with a tooltip listing each PR and its status. */
@Composable
fun DepCountChip(ctx: InboxContext, deps: List<DepRef>, eager: Boolean) {
    val t = LocalTokens.current
    // Look statuses up only for the selected row, or when the tooltip opens: avoids a call burst per list.
    LaunchedEffect(deps, eager) { if (eager) deps.forEach { ctx.state.requestDep(it) } }
    if (deps.isEmpty()) { Txt("no dependencies", size = 12.sp, color = t.muted, maxLines = 1); return }
    val tip = deps.joinToString("\n") { d ->
        val info = ctx.state.depInfo(d)
        "${d.repo} #${d.number}${info?.title?.let { " $it" } ?: ""} — ${info?.state?.label ?: "loading…"}"
    }
    Tip(tip, onShow = { deps.forEach { ctx.state.requestDep(it) } }) {
        Box(Modifier.clip(RoundedCornerShape(6.dp)).background(t.warnSoft).padding(horizontal = 8.dp, vertical = 2.dp)) {
            Txt("⛓ ${deps.size} ${if (deps.size == 1) "dependency" else "dependencies"}", size = 12.sp, weight = FontWeight.SemiBold, color = t.warn, maxLines = 1)
        }
    }
}
