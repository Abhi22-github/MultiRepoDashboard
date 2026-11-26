package com.example.repodashboard.ui.inbox

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.repodashboard.data.*
import com.example.repodashboard.ui.theme.*
import kotlinx.coroutines.delay

@Composable
fun DetailPane(ctx: InboxContext, modifier: Modifier) {
    val t = LocalTokens.current
    val items = currentItems(ctx)
    val it = selectedItem(ctx, items)
    val shape = RoundedCornerShape(14.dp)
    Column(
        modifier.softCard(shape).verticalScroll(rememberScrollState()).padding(horizontal = 32.dp, vertical = 28.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        when {
            ctx.initialLoading -> DetailSkeleton()
            it == null -> {
                val (title, body) = when {
                    ctx.unreachable -> "No data yet" to "Details appear once GitHub responds."
                    ctx.noRepos -> "Nothing to show yet" to "Choose repositories and their pull requests will appear here."
                    else -> "Nothing here" to "There is nothing in this view right now."
                }
                Column(Modifier.fillMaxWidth().padding(top = 120.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Ico(FeatherIcons.Inbox, 28.dp, t.muted)
                    Txt(title, weight = FontWeight.SemiBold)
                    Txt(body, color = t.muted)
                }
            }
            else -> key(it.key) {
                when (it.kind) {
                    Kind.Pr -> PrDetailView(ctx, it)
                    Kind.Ship -> ShipDetailView(it)
                    Kind.Next -> NextDetailView(it)
                    Kind.Release -> ReleaseDetailView(ctx, it)
                }
            }
        }
    }
}

@Composable
private fun DetailSkeleton() {
    val t = LocalTokens.current
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        SkelBar(0.35f, 12.dp); SkelBar(0.7f, 26.dp, radius = 8.dp); SkelBar(0.2f, 20.dp)
        Row(Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            repeat(4) { Box(Modifier.weight(1f).height(64.dp).clip(RoundedCornerShape(12.dp)).background(t.skel)) }
        }
        Box(Modifier.fillMaxWidth().padding(top = 12.dp).height(44.dp).clip(RoundedCornerShape(8.dp)).background(t.skel))
    }
}

@Composable
private fun Header(kicker: String, title: String, badge: String, badgeBg: Color, badgeFg: Color, action: String, url: String) {
    val uri = LocalUriHandler.current
    val t = LocalTokens.current
    FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(Modifier.weight(1f).widthIn(min = 320.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Txt(kicker, size = 13.sp, color = t.muted)
            Txt(title, size = 28.sp, weight = FontWeight.Bold, lineHeight = 34.sp, letterSpacing = (-0.5).sp)
            Box(Modifier.clip(RoundedCornerShape(6.dp)).background(badgeBg).padding(horizontal = 10.dp, vertical = 2.dp)) {
                Txt(badge, size = 13.sp, weight = FontWeight.SemiBold, color = badgeFg)
            }
        }
        if (url.isNotBlank()) GhostButton(action, { uri.openUri(url) }, FeatherIcons.ExternalLink)
    }
}

@Composable
private fun PrDetailView(ctx: InboxContext, it: Item) {
    val t = LocalTokens.current
    val pr = it.pr ?: return
    val d = ctx.state.detailOf(it)
    Header(
        "${it.repo.name} · #${pr.number} · by ${pr.user.login}", pr.title,
        if (it.draft) "Draft" else "Open", if (it.draft) t.bg else t.okSoft, if (it.draft) t.muted else t.ok,
        "Open pull request", pr.html_url
    )
    val loadingVal = "…"
    val checks = d?.checks
    val facts = listOf(
        Triple("Checks", when (checks) { null -> loadingVal; Checks.Passing -> "Passing"; Checks.Failing -> "Failing"; Checks.Pending -> "Pending"; Checks.None -> "None" },
            when (checks) { Checks.Failing -> t.fail; Checks.Passing -> t.ok; else -> t.ink }),
        Triple("Reviews", d?.reviews ?: loadingVal, t.ink),
        Triple("Comments", d?.comments?.toString() ?: loadingVal, t.ink),
        Triple("Changes", d?.let { "+${it.adds} −${it.dels}" } ?: loadingVal, t.ink),
        Triple("Milestone", it.milestone ?: "None", t.ink),
        Triple("Owner", pr.user.login, t.ink),
    )
    FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp), maxItemsInEachRow = 3) {
        facts.forEach { (label, value, color) ->
            Column(
                Modifier.weight(1f).widthIn(min = 150.dp).border(1.dp, t.line, RoundedCornerShape(12.dp)).padding(horizontal = 16.dp, vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Txt(label, size = 12.sp, color = t.muted)
                Txt(value, size = 16.sp, weight = if (label == "Changes") FontWeight.Medium else FontWeight.Bold, color = color, mono = label == "Changes", maxLines = 1)
            }
        }
    }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Txt("Branch", size = 15.sp, weight = FontWeight.Bold)
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(t.bg).padding(start = 12.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Txt(pr.head.ref, size = 13.sp, mono = true, maxLines = 1)
                Ico(FeatherIcons.ArrowRight, 14.dp, t.muted)
                Txt(pr.base.ref, size = 13.sp, mono = true, maxLines = 1)
            }
            CopyButton(pr.head.ref, "Copy branch")
        }
        Txt("Once merged it will show under Unreleased until a release includes it.", size = 13.sp, color = t.muted)
    }
}

@Composable
private fun CopyButton(text: String, label: String) {
    val t = LocalTokens.current
    @Suppress("DEPRECATION") val clipboard = LocalClipboardManager.current
    var copied by remember { mutableStateOf(false) }
    LaunchedEffect(copied) { if (copied) { delay(1600); copied = false } }
    val shape = RoundedCornerShape(8.dp)
    Row(
        Modifier.height(32.dp).softCard(shape).click { clipboard.setText(AnnotatedString(text)); copied = true }.padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Ico(if (copied) FeatherIcons.Check else FeatherIcons.Copy, 14.dp, if (copied) t.ok else t.ink2)
        Txt(if (copied) "Copied" else label, size = 13.sp, weight = FontWeight.Medium, color = if (copied) t.ok else t.ink2, maxLines = 1)
    }
}

@Composable
private fun ShipDetailView(it: Item) {
    val t = LocalTokens.current
    val s = it.ship ?: return
    val repo = it.repo
    Header("${repo.name} · #${s.number} · by ${s.author}", s.title, "Merged · unreleased", t.warnSoft, t.warn, "Open pull request", s.url)
    Row(Modifier.fillMaxWidth()) {
        Column(Modifier.weight(1f).padding(end = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)).background(t.accent))
            Txt("Merged to ${repo.config.defaultBranch}", weight = FontWeight.SemiBold)
            Txt(monthDayYear(s.mergedAt), size = 13.sp, color = t.muted)
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            DashedBar(t.accentLine, Modifier.fillMaxWidth())
            Txt("Not in ${repo.latestTag ?: "any release"}", weight = FontWeight.SemiBold)
            Txt(if (repo.latestTag != null) "Missing from the latest release" else "This repository has no releases yet", size = 13.sp, color = t.muted)
        }
    }
    Column(
        Modifier.fillMaxWidth().border(1.dp, t.line, RoundedCornerShape(12.dp)).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Txt("Everything unreleased in ${repo.name}", size = 16.sp, weight = FontWeight.Bold)
        repo.unreleased.forEach { w -> PrLine(w, bordered = false) }
    }
}

@Composable
private fun NextDetailView(it: Item) {
    val t = LocalTokens.current
    val repo = it.repo
    Header(
        "${repo.name} · merged after ${repo.latestTag ?: "the first commit"}", "Unreleased changes",
        "Merged · not released", t.warnSoft, t.warn, "Compare on GitHub", repo.compareUrl()
    )
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Txt("Merged into ${repo.config.defaultBranch}, waiting for the next release", size = 15.sp, weight = FontWeight.Bold)
        repo.unreleased.forEach { PrLine(it, bordered = true) }
    }
}

@Composable
private fun ReleaseDetailView(ctx: InboxContext, it: Item) {
    val t = LocalTokens.current
    val rel = it.release ?: return
    LaunchedEffect(it.key) { ctx.state.requestReleasePrs(it) }
    val prs = ctx.state.prsOf(it)
    Header(
        "${it.repo.name} · published ${monthDayYear(rel.publishedAt)}", "${rel.tag} · ${rel.name}",
        if (rel.isLatest) "Latest release" else "Release", t.okSoft, t.ok, "View release", rel.url
    )
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Txt("Pull requests in this release", Modifier.weight(1f), size = 15.sp, weight = FontWeight.Bold)
            if (!prs.isNullOrEmpty()) CopyButton(prs.joinToString("\n") { "- ${it.title} (#${it.number})" }, "Copy notes")
        }
        when {
            prs == null -> repeat(3) { SkelBar(1f, 44.dp, radius = 10.dp) }
            prs.isEmpty() -> Txt(
                if (rel.previousTag == null) "This is the first release, so there is nothing to compare it with."
                else "No merged pull requests found between ${rel.previousTag} and ${rel.tag}.",
                color = t.muted
            )
            else -> prs.forEach { PrLine(it, bordered = true) }
        }
    }
}

@Composable
private fun PrLine(p: ShipItem, bordered: Boolean) {
    val t = LocalTokens.current
    val uri = LocalUriHandler.current
    val shape = RoundedCornerShape(10.dp)
    Row(
        Modifier.fillMaxWidth().clip(shape).then(if (bordered) Modifier.border(1.dp, t.line, shape) else Modifier)
            .click { uri.openUri(p.url) }.padding(horizontal = if (bordered) 14.dp else 0.dp, vertical = if (bordered) 12.dp else 4.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Txt("#${p.number}", Modifier.width(44.dp), color = t.muted, mono = true, maxLines = 1)
        Txt(p.title, Modifier.weight(1f), weight = if (bordered) FontWeight.Medium else FontWeight.Normal, maxLines = 2)
        Txt(p.author, size = 13.sp, color = t.muted, maxLines = 1)
    }
}
