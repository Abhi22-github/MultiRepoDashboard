package com.example.repodashboard.ui.inbox

import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.max
import androidx.compose.ui.unit.sp
import com.example.repodashboard.data.Checks
import com.example.repodashboard.data.monthDayYear
import com.example.repodashboard.ui.theme.*

const val BOARD_PAGE = 20
private val PANEL_W = 440.dp
private val BOARD_SHIFT = 452.dp

private data class Col(val view: InboxView, val title: String, val items: List<Item>, val dot: @Composable () -> Unit, val empty: String, val warn: Boolean = false)

@Composable
fun BoardView(ctx: InboxContext, modifier: Modifier) {
    val t = LocalTokens.current
    val s = ctx.state
    val shape = RoundedCornerShape(14.dp)
    fun build(v: InboxView) = if (ctx.ready) applyFilters(buildItems(v, ctx.scoped, ctx.now), s.filters, s.query) else emptyList()
    val cols = listOf(
        Col(InboxView.Drafts, "Drafts", build(InboxView.Drafts), { Dot(Neutral, DotStyle.Dashed, 9.dp) }, "No drafts"),
        Col(InboxView.Open, "Open PRs", build(InboxView.Open), { Dot(Neutral, size = 9.dp) }, "No open pull requests"),
        Col(InboxView.Ship, "Merged · unreleased", build(InboxView.Ship), { Dot(t.warnDot, size = 9.dp) }, "Everything merged is released", warn = true),
        Col(InboxView.Releases, "Released", build(InboxView.Releases).filter { it.kind == Kind.Release }, { Dot(t.ok, size = 9.dp) }, "No releases yet"),
    )
    val allPeople = if (ctx.ready) InboxView.entries.flatMap { buildItems(it, ctx.scoped, ctx.now) } else emptyList()
    val selected = s.boardSel?.let { k -> cols.firstNotNullOfOrNull { c -> c.items.firstOrNull { it.key == k }?.let { c to it } } }

    Column(modifier.softCard(shape)) {
        Column(Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), itemVerticalAlignment = Alignment.CenterVertically) {
                Txt("Board", size = 20.sp, weight = FontWeight.Bold)
                val scopeName = s.repoScope?.let { n -> ctx.repos.firstOrNull { it.config.name == n }?.name } ?: "All repositories"
                Txt("$scopeName · drafts, review, merged, released", size = 13.sp, color = t.muted)
            }
            if (ctx.ready) {
                Row(Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Field(
                        s.query, { s.query = it }, "Search, or type label:, milestone: or author:",
                        Modifier.weight(1f, fill = false).widthIn(min = 240.dp, max = 380.dp).fillMaxWidth(), leading = FeatherIcons.Search, height = 40.dp
                    )
                    Spacer(Modifier.weight(1f))
                    FilterBar(ctx, allPeople, alignEnd = true, showChips = false)
                }
                ActiveFilterChips(ctx)
            }
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(t.line2))
        when {
            ctx.initialLoading -> Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) { repeat(3) { SkelBar(1f, 64.dp, radius = 12.dp) } }
            ctx.unreachable -> EmptyState(FeatherIcons.WifiOff, t.failSoft, t.fail, "Couldn't reach GitHub", "Check your connection and try again. Nothing was changed.") { PrimaryButton("Try again", ctx.onRefresh) }
            ctx.noRepos -> EmptyState(FeatherIcons.Inbox, t.accentSoft, t.accentText, "Pick the repositories to watch", "Their pull requests and releases will show up here.") { PrimaryButton("Choose repositories", ctx.onManageRepos) }
            else -> BoxWithConstraints(Modifier.fillMaxSize().clipToBounds()) {
                val w = max(maxWidth, 1000.dp)
                val shiftTarget = if (selected != null && (selected.first.view == InboxView.Ship || selected.first.view == InboxView.Releases)) -BOARD_SHIFT else 0.dp
                val shift by animateDpAsState(shiftTarget, motion(320))
                Box(Modifier.fillMaxSize().entrance(s.repoScope, 250, offsetY = 6.dp, firstScale = 0.985f).graphicsLayer { translationX = shift.toPx() }) {
                    Row(
                        Modifier.horizontalScroll(rememberScrollState()).width(w).fillMaxHeight().padding(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        cols.forEach { c -> BoardColumn(ctx, c, Modifier.weight(1f)) }
                    }
                }
                // Keep the last selection so the panel can slide out with its content.
                var shown by remember { mutableStateOf(selected) }
                if (selected != null) shown = selected
                androidx.compose.animation.AnimatedVisibility(
                    selected != null, Modifier.align(Alignment.CenterEnd).fillMaxHeight(),
                    enter = slideInHorizontally(motion(300)) { it }, exit = slideOutHorizontally(motion(300)) { it },
                ) {
                    shown?.let { (c, item) -> BoardDetailPanel(ctx, c, item) }
                }
            }
        }
    }
}

@Composable
private fun BoardColumn(ctx: InboxContext, c: Col, modifier: Modifier) {
    val t = LocalTokens.current
    val s = ctx.state
    val shape = RoundedCornerShape(16.dp)
    Column(
        modifier.heightIn(min = 200.dp).clip(shape).background(if (c.warn) t.warnSoft else t.bg)
            .then(if (c.warn) Modifier.border(1.dp, t.warnLine, shape) else Modifier)
            .verticalScroll(rememberScrollState()).padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(Modifier.padding(start = 6.dp, end = 6.dp, top = 4.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            c.dot()
            Txt(c.title, Modifier.weight(1f), weight = FontWeight.Bold, color = if (c.warn) t.warn else t.ink, maxLines = 1)
            Chip(c.items.size.toString(), if (c.warn) t.warnDot else t.line, if (c.warn) Color.White else t.ink2, size = 12.sp, weight = FontWeight.Bold, radius = 9.dp)
        }
        val limit = s.boardLimit[c.view] ?: BOARD_PAGE
        c.items.take(limit).forEach { it ->
            BoardCard(ctx, it, on = it.key == s.boardSel) { s.boardSel = if (s.boardSel == it.key) null else it.key }
        }
        val left = c.items.size - limit
        if (left > 0) {
            Row(Modifier.fillMaxWidth().padding(start = 4.dp, end = 4.dp, top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Txt("Showing $limit of ${c.items.size}", Modifier.weight(1f), size = 12.sp, color = t.muted, maxLines = 1)
                val bshape = RoundedCornerShape(10.dp)
                Box(
                    Modifier.height(32.dp).softCard(bshape).click { s.boardLimit = s.boardLimit + (c.view to limit + BOARD_PAGE) }.padding(horizontal = 12.dp),
                    contentAlignment = Alignment.Center
                ) { Txt("Show ${minOf(BOARD_PAGE, left)} more", size = 13.sp, weight = FontWeight.SemiBold, color = t.accentText, maxLines = 1) }
            }
        }
        if (c.items.isEmpty()) Box(Modifier.fillMaxWidth().padding(vertical = 18.dp, horizontal = 8.dp), contentAlignment = Alignment.Center) {
            Txt(c.empty, size = 13.sp, color = t.muted)
        }
    }
}

@Composable
private fun BoardCard(ctx: InboxContext, it: Item, on: Boolean, onClick: () -> Unit) {
    val t = LocalTokens.current
    val (status, tone) = statusOf(it, ctx.state.detailOf(it))
    val shape = RoundedCornerShape(12.dp)
    val url = (it.pr?.html_url ?: it.ship?.url)?.takeIf { u -> u.isNotBlank() }
    Box(Modifier.fillMaxWidth()) {
        Column(
            Modifier.fillMaxWidth().softCard(shape).then(if (on) Modifier.border(2.dp, t.accent, shape) else Modifier)
                .click(onClick = onClick).padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Txt(it.repo.name, size = 12.sp, weight = FontWeight.SemiBold, color = t.ink2, maxLines = 1)
                Txt(refOf(it), size = 12.sp, color = t.muted, mono = true, maxLines = 1)
                Spacer(Modifier.weight(1f))
                Txt(it.age, size = 12.sp, color = t.muted, maxLines = 1)
            }
            Txt(it.title, weight = FontWeight.SemiBold, lineHeight = 19.sp, maxLines = 3)
            if (it.hasPeople) PeopleTags(it, bordered = false)
            Txt(status, Modifier.padding(end = if (url != null) 34.dp else 0.dp), size = 12.sp, weight = FontWeight.SemiBold, color = toneColor(t, tone), maxLines = 1)
        }
        if (url != null) GhIconButton(url, Modifier.align(Alignment.BottomEnd).padding(10.dp))
    }
}

private fun refOf(it: Item): String = it.pr?.let { "#${it.number}" } ?: it.ship?.let { "#${it.number}" } ?: it.release?.tag ?: ""

/** Details for the selected board card, docked to the right edge. */
@Composable
private fun BoardDetailPanel(ctx: InboxContext, c: Col, it: Item) {
    val t = LocalTokens.current
    val s = ctx.state
    val uri = LocalUriHandler.current
    LaunchedEffect(it.key) { s.requestDetail(it); s.requestReleasePrs(it) }
    val (status, tone) = statusOf(it, s.detailOf(it))
    val url = it.pr?.html_url ?: it.ship?.url ?: it.release?.url ?: ""
    val line = t.line
    Column(
        Modifier.width(PANEL_W).fillMaxHeight().shadow(16.dp).background(t.pane)
            .drawBehind { drawLine(line, Offset(0f, 0f), Offset(0f, size.height), strokeWidth = 1.dp.toPx()) }
    ) {
        SelectionContainer(Modifier.weight(1f).entrance(it.key, 160, offsetY = 4.dp)) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Chip(c.title, if (c.warn) t.warnSoft else t.bg, if (c.warn) t.warn else t.ink2, size = 12.sp, radius = 6.dp)
                Spacer(Modifier.weight(1f))
                IconBtn(FeatherIcons.X, "Close details", { s.boardSel = null }, size = 32.dp)
            }
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                val who = it.author?.let { a -> " · $a" } ?: ""
                Txt("${it.repo.name} · ${refOf(it)}$who", size = 13.sp, color = t.muted, maxLines = 1)
                Txt(it.title, size = 20.sp, weight = FontWeight.Bold, lineHeight = 26.sp)
                Txt(status, size = 13.sp, weight = FontWeight.SemiBold, color = toneColor(t, tone))
            }
            FactGrid(factsOf(ctx, it))
            when {
                it.pr != null -> DescriptionSection(ctx, it.pr.body, it.repo)
                it.ship != null -> DescriptionSection(ctx, it.ship.body, it.repo)
            }
        }
        }
        Row(
            Modifier.fillMaxWidth().drawBehind { drawLine(line, Offset(0f, 0f), Offset(size.width, 0f), strokeWidth = 1.dp.toPx()) }.padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically
        ) {
            if (url.isNotBlank()) {
                Row(
                    Modifier.weight(1f).height(40.dp).clip(RoundedCornerShape(10.dp)).background(t.ink).click { uri.openUri(url) },
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)
                ) {
                    Ico(FeatherIcons.GitHub, 15.dp, t.pane)
                    Txt("View on GitHub", weight = FontWeight.SemiBold, color = t.pane, maxLines = 1)
                }
            }
            GhostButton("Open in list", {
                s.layout = Layout.List; s.view = c.view; s.selKey = it.key; s.boardSel = null
            })
        }
    }
}

private fun factsOf(ctx: InboxContext, it: Item): List<Pair<String, String>> {
    it.pr?.let { pr ->
        val d = ctx.state.detailOf(it)
        return listOf(
            "Checks" to when (d?.checks) { null -> "…"; Checks.Passing -> "Passing"; Checks.Failing -> "Failing"; Checks.Pending -> "Pending"; Checks.None -> "None" },
            "Reviews" to (d?.reviews ?: "…"),
            "Labels" to it.labels.joinToString(", ").ifEmpty { "None" },
            "Milestone" to (it.milestone ?: "None"),
            "Owner" to pr.user.login,
            "Updated" to monthDayYear(pr.updated_at),
        )
    }
    it.ship?.let { sh ->
        return listOf(
            "Merged" to monthDayYear(sh.mergedAt),
            "Not in" to (it.repo.latestTag ?: "any release"),
            "Labels" to it.labels.joinToString(", ").ifEmpty { "None" },
            "Owner" to sh.author,
        )
    }
    it.release?.let { rel ->
        return listOf(
            "Tag" to rel.tag,
            "Released" to monthDayYear(rel.publishedAt),
            "Pull requests" to (ctx.state.prsOf(it)?.size?.toString() ?: "…"),
        )
    }
    return emptyList()
}

@Composable
private fun FactGrid(facts: List<Pair<String, String>>) {
    val t = LocalTokens.current
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        facts.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { (label, value) ->
                    Column(
                        Modifier.weight(1f).border(1.dp, t.line, RoundedCornerShape(10.dp)).padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Txt(label, size = 12.sp, color = t.muted)
                        Txt(value, weight = FontWeight.Bold, maxLines = 2)
                    }
                }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}
