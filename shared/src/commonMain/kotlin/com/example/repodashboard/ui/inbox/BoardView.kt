package com.example.repodashboard.ui.inbox

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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.repodashboard.ui.theme.*

private data class Col(val view: InboxView, val title: String, val items: List<Item>, val dot: @Composable () -> Unit, val empty: String, val warn: Boolean = false)

@Composable
fun BoardView(ctx: InboxContext, modifier: Modifier) {
    val t = LocalTokens.current
    val s = ctx.state
    val uri = LocalUriHandler.current
    val shape = RoundedCornerShape(14.dp)
    fun build(v: InboxView) = if (ctx.ready) applyFilters(buildItems(v, ctx.scoped, ctx.now), s.filters, s.query) else emptyList()
    val cols = listOf(
        Col(InboxView.Drafts, "Drafts", build(InboxView.Drafts), { Dot(Neutral, DotStyle.Dashed, 9.dp) }, "No drafts"),
        Col(InboxView.Open, "Open PRs", build(InboxView.Open), { Dot(Neutral, size = 9.dp) }, "No open pull requests"),
        Col(InboxView.Ship, "Merged · unreleased", build(InboxView.Ship), { Dot(t.warnDot, size = 9.dp) }, "Everything merged is released", warn = true),
        Col(InboxView.Releases, "Released", build(InboxView.Releases).filter { it.kind == Kind.Release }, { Dot(t.ok, size = 9.dp) }, "No releases yet"),
    )
    val allPeople = if (ctx.ready) InboxView.entries.flatMap { buildItems(it, ctx.scoped, ctx.now) } else emptyList()

    Column(modifier.softCard(shape)) {
        Column(Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), itemVerticalAlignment = Alignment.CenterVertically) {
                Txt("Board", size = 20.sp, weight = FontWeight.Bold)
                val scopeName = s.repoScope?.let { n -> ctx.repos.firstOrNull { it.config.name == n }?.name } ?: "All repositories"
                Txt("$scopeName · drafts, review, merged, released", size = 13.sp, color = t.muted)
            }
            if (ctx.ready) {
                FlowRow(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp), itemVerticalAlignment = Alignment.CenterVertically) {
                    Field(s.query, { s.query = it }, "Search, or type milestone: or author:", Modifier.widthIn(min = 240.dp, max = 380.dp), leading = FeatherIcons.Search, height = 40.dp)
                    FilterBar(ctx, allPeople, alignEnd = false)
                }
            }
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(t.line2))
        when {
            ctx.initialLoading -> Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) { repeat(3) { SkelBar(1f, 64.dp, radius = 12.dp) } }
            ctx.unreachable -> EmptyState(FeatherIcons.WifiOff, t.failSoft, t.fail, "Couldn't reach GitHub", "Check your connection and try again. Nothing was changed.") { PrimaryButton("Try again", ctx.onRefresh) }
            ctx.noRepos -> EmptyState(FeatherIcons.Inbox, t.accentSoft, t.accentText, "Pick the repositories to watch", "Their pull requests and releases will show up here.") { PrimaryButton("Choose repositories", ctx.onManageRepos) }
            else -> BoxWithConstraints(Modifier.fillMaxSize()) {
                val minW = 1000.dp
                val w = if (maxWidth > minW) maxWidth else minW
                Row(
                    Modifier.horizontalScroll(rememberScrollState()).width(w).fillMaxHeight().padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    cols.forEach { c -> BoardColumn(ctx, c, Modifier.weight(1f)) { url -> uri.openUri(url) } }
                }
            }
        }
    }
}

@Composable
private fun BoardColumn(ctx: InboxContext, c: Col, modifier: Modifier, open: (String) -> Unit) {
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
        c.items.forEach { it -> BoardCard(ctx, it) { s.layout = Layout.List; s.view = c.view; s.selKey = it.key } }
        if (c.items.isEmpty()) Box(Modifier.fillMaxWidth().padding(vertical = 18.dp, horizontal = 8.dp), contentAlignment = Alignment.Center) {
            Txt(c.empty, size = 13.sp, color = t.muted)
        }
        val scopedRepo = s.repoScope?.let { n -> ctx.repos.firstOrNull { it.config.name == n } }
        if (c.warn && c.items.isNotEmpty() && scopedRepo != null) {
            val bshape = RoundedCornerShape(10.dp)
            Row(
                Modifier.fillMaxWidth().height(38.dp).clip(bshape).background(t.pane).border(1.dp, t.warnLine, bshape).click { open(scopedRepo.compareUrl()) },
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally)
            ) {
                Txt("Compare on GitHub", size = 13.sp, weight = FontWeight.SemiBold, color = t.warn)
                Ico(FeatherIcons.ExternalLink, 13.dp, t.warn)
            }
        }
    }
}

@Composable
private fun BoardCard(ctx: InboxContext, it: Item, onOpen: () -> Unit) {
    val t = LocalTokens.current
    LaunchedEffect(it.key) { ctx.state.requestDetail(it) }
    val (status, tone) = statusOf(it, ctx.state.detailOf(it))
    Column(
        Modifier.fillMaxWidth().softCard(RoundedCornerShape(12.dp)).click(onClick = onOpen).padding(horizontal = 14.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Txt(it.repo.name, size = 12.sp, weight = FontWeight.SemiBold, color = t.ink2, maxLines = 1)
            Txt(it.pr?.let { "#${it.number}" } ?: it.ship?.let { "#${it.number}" } ?: it.release?.tag ?: "", size = 12.sp, color = t.muted, mono = true, maxLines = 1)
            Spacer(Modifier.weight(1f))
            Txt(it.age, size = 12.sp, color = t.muted, maxLines = 1)
        }
        Txt(it.title, weight = FontWeight.SemiBold, lineHeight = 19.sp, maxLines = 3)
        if (it.hasPeople) PeopleTags(it, bordered = false)
        Txt(status, size = 12.sp, weight = FontWeight.SemiBold, color = toneColor(t, tone), maxLines = 1)
    }
}
