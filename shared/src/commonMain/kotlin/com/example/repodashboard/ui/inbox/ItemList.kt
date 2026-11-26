package com.example.repodashboard.ui.inbox

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.repodashboard.data.RepoData
import com.example.repodashboard.data.SyncProblem
import com.example.repodashboard.data.minutesUntil
import com.example.repodashboard.ui.theme.*

/** Items of the current view after scope, filters and search. */
fun currentItems(ctx: InboxContext): List<Item> =
    if (!ctx.ready) emptyList()
    else applyFilters(buildItems(ctx.state.view, ctx.scoped, ctx.now), ctx.state.filters, ctx.state.query)

fun selectedItem(ctx: InboxContext, items: List<Item>): Item? =
    items.firstOrNull { it.key == ctx.state.selKey } ?: items.firstOrNull()

@Composable
fun ItemListPane(ctx: InboxContext, modifier: Modifier) {
    val t = LocalTokens.current
    val s = ctx.state
    val items = currentItems(ctx)
    val sel = selectedItem(ctx, items)
    val shape = RoundedCornerShape(14.dp)

    Column(modifier.softCard(shape)) {
        Column(Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            val head = s.repoScope?.let { n -> ctx.repos.firstOrNull { it.config.name == n } }
            if (head != null) RepoHead(head) { s.pickScope(null) }
            else Txt("All repositories · ${s.view.title}", Modifier.padding(top = 4.dp), size = 22.sp, weight = FontWeight.Bold, letterSpacing = (-0.2).sp)
            Txt(s.view.desc, size = 13.sp, color = t.muted)
            if (ctx.ready) {
                Field(
                    s.query, { s.query = it }, "Search, or type label:, milestone: or author:", Modifier.fillMaxWidth().padding(top = 6.dp),
                    leading = FeatherIcons.Search, height = 40.dp, fontSize = 14.sp
                )
                if (s.view != InboxView.Releases) FilterBar(ctx, buildItems(s.view, ctx.scoped, ctx.now), alignEnd = false)
            }
            ProblemBanner(ctx)
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(t.line2))
        ListBody(ctx, items, sel, Modifier.fillMaxWidth().weight(1f))
    }
}

@Composable
private fun RepoHead(repo: RepoData, onAll: () -> Unit) {
    val t = LocalTokens.current
    val uri = LocalUriHandler.current
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Txt(repo.name, size = 20.sp, weight = FontWeight.Bold, maxLines = 1)
                Chip(repo.latestTag ?: "no release", Color.Transparent, t.accentText, size = 12.sp, weight = FontWeight.Normal, radius = 6.dp, border = t.accentLine)
            }
            Txt(repo.owner, size = 13.sp, color = t.muted, maxLines = 1)
        }
        TextLink("All", onAll, color = t.ink2)
        GhostButton("GitHub", { uri.openUri(repo.webUrl) }, FeatherIcons.ExternalLink, height = 36.dp)
    }
    if (repo.config.description.isNotBlank()) Txt(repo.config.description, size = 13.sp, color = t.muted, maxLines = 2)
}

/** Milestone / Author menus + active filter chips. */
@Composable
fun FilterBar(ctx: InboxContext, source: List<Item>, alignEnd: Boolean) {
    val t = LocalTokens.current
    val s = ctx.state
    val f = s.filters
    val defs = listOf(
        Triple("label", "Label", f.label) to { it: Item -> it.labels },
        Triple("milestone", "Milestone", f.milestone) to { it: Item -> listOf(it.milestone ?: "No milestone") },
        Triple("author", "Author", f.author) to { it: Item -> listOfNotNull(it.author) },
    )
    fun update(key: String, v: Set<String>) {
        s.filters = when (key) { "label" -> f.copy(label = v); "milestone" -> f.copy(milestone = v); else -> f.copy(author = v) }
    }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = 2.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            defs.forEach { (def, get) ->
                val (key, label, sel) = def
                val open = s.openFilter == key
                val active = sel.isNotEmpty() || open
                val shape = RoundedCornerShape(10.dp)
                Box {
                    Row(
                        Modifier.height(34.dp).softCard(shape).then(if (active) Modifier.border(1.5.dp, t.accent, shape) else Modifier)
                            .click { s.openFilter = if (open) null else key }.padding(start = 12.dp, end = 10.dp),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Txt(label, size = 13.sp, weight = if (active) FontWeight.SemiBold else FontWeight.Medium, color = if (active) t.accentText else t.ink2)
                        if (sel.isNotEmpty()) Chip(sel.size.toString(), t.accent, Color.White, weight = FontWeight.Bold, radius = 8.dp)
                        Ico(FeatherIcons.ChevronDown, 14.dp, t.muted)
                    }
                    Dropdown(open, { s.openFilter = null }, alignEnd = alignEnd) {
                        val opts = optionsOf(source, get)
                        if (opts.isEmpty()) Txt("Nothing to filter by in this view.", Modifier.padding(10.dp), size = 13.sp, color = t.muted)
                        Column(Modifier.verticalScroll(rememberScrollState())) {
                            opts.forEach { (v, c) ->
                                val on = v in sel
                                Row(
                                    Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(if (on) t.sel else Color.Transparent)
                                        .click(role = Role.Checkbox) { update(key, if (on) sel - v else sel + v) }.padding(horizontal = 10.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    CheckBox(on)
                                    Txt(v, Modifier.weight(1f), size = 13.sp, maxLines = 1)
                                    Txt(c.toString(), size = 12.sp, color = t.muted)
                                }
                            }
                        }
                    }
                }
            }
            if (!f.isEmpty) TextLink("Clear all", { s.filters = Filters(); s.openFilter = null })
        }
        if (!f.isEmpty) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                f.label.forEach { v -> ActiveChip("Label: $v") { s.filters = f.copy(label = f.label - v) } }
                f.milestone.forEach { v -> ActiveChip("Milestone: $v") { s.filters = f.copy(milestone = f.milestone - v) } }
                f.author.forEach { v -> ActiveChip("Author: $v") { s.filters = f.copy(author = f.author - v) } }
            }
        }
    }
}

@Composable
private fun ActiveChip(label: String, onRemove: () -> Unit) {
    val t = LocalTokens.current
    val shape = RoundedCornerShape(8.dp)
    Row(
        Modifier.height(28.dp).clip(shape).background(t.accentSoft).click(onClick = onRemove)
            .semantics { contentDescription = "Remove filter $label" }.padding(start = 10.dp, end = 6.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Txt(label, size = 12.sp, weight = FontWeight.SemiBold, color = t.accentText, maxLines = 1)
        Ico(FeatherIcons.X, 12.dp, t.accentText)
    }
}

@Composable
private fun ProblemBanner(ctx: InboxContext) {
    val t = LocalTokens.current
    val data = ctx.data ?: return
    val (title, body, bg, fg, link) = when (data.problem) {
        SyncProblem.RateLimited -> Banner(
            "GitHub rate limit reached",
            "Showing the last data we loaded. " + (minutesUntil(data.rateResetAt, ctx.now)?.let { "Refreshing works again in $it min." } ?: "Try again later."),
            t.warnSoft, t.warn, false
        )
        SyncProblem.TokenExpired -> Banner("Your token stopped working", "GitHub answered 401. The token may have expired or lost read access.", t.failSoft, t.failText, true)
        else -> return
    }
    Row(
        Modifier.fillMaxWidth().padding(top = 10.dp).clip(RoundedCornerShape(10.dp)).background(bg).padding(horizontal = 14.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Ico(FeatherIcons.AlertTriangle, 18.dp, fg, Modifier.padding(top = 2.dp))
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Txt(title, weight = FontWeight.Bold, color = fg)
            Txt(body, size = 13.sp, color = fg)
            if (link) Box(Modifier.padding(top = 4.dp).click(onClick = ctx.onSignOut)) { Txt("Sign in again", size = 13.sp, weight = FontWeight.Bold, color = fg) }
        }
    }
}

private data class Banner(val title: String, val body: String, val bg: Color, val fg: Color, val link: Boolean)

@Composable
private fun ListBody(ctx: InboxContext, items: List<Item>, sel: Item?, modifier: Modifier) {
    val t = LocalTokens.current
    val s = ctx.state
    LazyColumn(modifier, contentPadding = PaddingValues(8.dp)) {
        when {
            ctx.initialLoading -> items(listOf(0.7f, 0.55f, 0.8f, 0.6f)) { w ->
                Row(Modifier.padding(horizontal = 12.dp, vertical = 14.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Box(Modifier.padding(top = 6.dp)) { Dot(t.skel) }
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        SkelBar(0.4f, 10.dp, radius = 5.dp); SkelBar(w, 12.dp); SkelBar(0.3f, 10.dp, radius = 5.dp)
                    }
                }
            }
            ctx.unreachable -> item {
                EmptyState(FeatherIcons.WifiOff, t.failSoft, t.fail, "Couldn't reach GitHub", "Check your connection and try again. Nothing was changed.") {
                    PrimaryButton("Try again", ctx.onRefresh)
                }
            }
            ctx.noRepos -> item {
                EmptyState(FeatherIcons.Inbox, t.accentSoft, t.accentText, "Pick the repositories to watch", "Their pull requests and releases will show up here.") {
                    PrimaryButton("Choose repositories", ctx.onManageRepos)
                }
            }
            items.isEmpty() -> item {
                val filtered = s.query.isNotBlank() || !s.filters.isEmpty
                if (filtered) EmptyState(FeatherIcons.Search, t.bg, t.muted, "No matches", "Nothing in ${s.view.label} matches these filters.") {
                    GhostButton("Clear filters", { s.query = ""; s.filters = Filters() })
                } else EmptyState(FeatherIcons.CheckCircle, t.okSoft, t.ok, s.view.emptyTitle, s.view.emptyBody)
            }
            else -> {
                val groups = items.groupBy { it.repo.config.name }.entries.toList()
                groups.forEachIndexed { idx, (name, gi) ->
                    item(key = "g$name") {
                        RepoGroup(ctx, gi, sel, collapsed = s.collapsed[name] ?: (idx >= 4)) {
                            s.collapsed = s.collapsed + (name to !(s.collapsed[name] ?: (idx >= 4)))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RepoGroup(ctx: InboxContext, gi: List<Item>, sel: Item?, collapsed: Boolean, onToggle: () -> Unit) {
    val t = LocalTokens.current
    val repo = gi.first().repo
    Column(
        Modifier.fillMaxWidth().padding(bottom = 10.dp).clip(RoundedCornerShape(18.dp)).background(t.bg).padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).click(onClick = onToggle).padding(start = 6.dp, end = 6.dp, top = 6.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Ico(FeatherIcons.ChevronRight, 14.dp, t.muted, Modifier.rotate(if (collapsed) 0f else 90f))
            Txt(repo.name, weight = FontWeight.Bold, maxLines = 1)
            Txt(repo.latestTag ?: "no release", size = 12.sp, color = t.muted, mono = true, maxLines = 1)
            Spacer(Modifier.weight(1f))
            Chip(gi.size.toString(), t.pane, t.muted, size = 12.sp, radius = 10.dp)
        }
        if (collapsed) {
            val tones = gi.map { statusOf(it, ctx.state.detailOf(it)).second }
            val pills = listOf(
                Triple(tones.count { it == Tone.Fail }, "failing", t.failSoft to t.fail),
                Triple(gi.count { it.kind == Kind.Pr && !it.draft && tones[gi.indexOf(it)] == Tone.Ok }, "passing", t.okSoft to t.ok),
                Triple(gi.count { it.kind == Kind.Ship || it.kind == Kind.Next }, "unreleased", t.warnSoft to t.warn),
                Triple(gi.count { it.draft }, "draft", t.pane to t.muted),
                Triple(gi.count { it.kind == Kind.Release }, "releases", t.pane to t.muted),
            ).filter { it.first > 0 }
            FlowRow(Modifier.padding(horizontal = 4.dp, vertical = 0.dp).padding(bottom = 4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                pills.forEach { (n, label, c) -> Chip("$n $label", c.first, c.second, size = 12.sp, radius = 6.dp) }
            }
        } else {
            gi.forEach { ItemTile(ctx, it, on = it.key == sel?.key) }
        }
    }
}

@Composable
private fun ItemTile(ctx: InboxContext, it: Item, on: Boolean) {
    val t = LocalTokens.current
    val (status, tone) = statusOf(it, ctx.state.detailOf(it))
    val shape = RoundedCornerShape(12.dp)
    val fill = when { on -> t.sel; it.kind == Kind.Next -> t.warnSoft; else -> t.pane }
    Row(
        Modifier.fillMaxWidth().softCard(shape, fill)
            .then(if (it.kind == Kind.Next && !on) Modifier.border(1.5.dp, t.warnLine, shape) else Modifier)
            .click { ctx.state.selKey = it.key }.padding(horizontal = 14.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(Modifier.padding(top = 7.dp)) { KindDot(it) }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Txt(it.metaShort, size = 12.sp, color = t.muted, maxLines = 1)
                Txt(it.age, size = 12.sp, color = t.muted, maxLines = 1)
            }
            Txt(it.title, weight = FontWeight.SemiBold, maxLines = 2)
            Txt(status, size = 13.sp, color = toneColor(t, tone), maxLines = 1)
            if (it.hasPeople) PeopleTags(it, Modifier.padding(top = 2.dp))
        }
    }
}

@Composable
fun KindDot(it: Item) {
    val t = LocalTokens.current
    when (it.kind) {
        Kind.Ship -> Dot(t.warnDot)
        Kind.Next -> Dot(t.warnDot, DotStyle.Dashed)
        Kind.Release -> Dot(Released)
        Kind.Pr -> Dot(Neutral, if (it.draft) DotStyle.Dashed else DotStyle.Ring)
    }
}

@Composable
fun PeopleTags(it: Item, modifier: Modifier = Modifier, bordered: Boolean = true) {
    val t = LocalTokens.current
    FlowRow(modifier, horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        it.author?.let { a -> Chip("owner: $a", t.bg, t.ink2, weight = FontWeight.Medium, border = if (bordered) t.line else null) }
        it.milestone?.let { m -> Chip("milestone: $m", t.bg, t.ink2, weight = FontWeight.Medium, border = if (bordered) t.line else null) }
        it.labels.forEach { l -> Chip(l, t.accentSoft, t.accentText, weight = FontWeight.Medium) }
    }
}
