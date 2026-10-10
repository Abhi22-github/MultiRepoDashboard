package com.example.repodashboard.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.repodashboard.github.GitHubClient
import com.example.repodashboard.models.GitHubCompareResponse
import com.example.repodashboard.models.GitHubPR
import com.example.repodashboard.models.GitHubPRLabel
import com.example.repodashboard.models.MergedPR
import com.example.repodashboard.models.ReleaseData
import com.example.repodashboard.models.ReleaseInfo
import com.example.repodashboard.models.Repository
import com.example.repodashboard.openUrl
import com.example.repodashboard.ui.theme.*

private enum class DetailTab { Open, Drafts, Releases }

@Composable
fun RepoDetailScreen(repo: Repository, client: GitHubClient?, onBack: () -> Unit, accent: Color = HotPink) {
    val c       = LocalAppColors.current
    val body    = LocalBodyFont.current
    val display = LocalDisplayFont.current

    val openPRs  = remember(repo) { repo.openPRs.filter { !it.draft } }
    val draftPRs = remember(repo) { repo.openPRs.filter { it.draft } }
    var tab by remember(repo.name) { mutableStateOf(DetailTab.Open) }

    var releaseData by remember(repo.name) { mutableStateOf<ReleaseData?>(null) }
    var loadingReleases by remember(repo.name) { mutableStateOf(false) }
    LaunchedEffect(repo.name, tab) {
        if (tab == DetailTab.Releases && releaseData == null && client != null && !loadingReleases) {
            loadingReleases = true
            releaseData = loadReleaseData(client, repo)
            loadingReleases = false
        }
    }

    Column(Modifier.fillMaxSize()) {
        // ── Header ──────────────────────────────────────────────────
        Column(
            Modifier.fillMaxWidth().background(accent)
                .stripes(Color.White.copy(alpha = 0.2f), gap = 28.dp, width = 10.dp)
                .padding(start = 40.dp, end = 40.dp, top = 22.dp, bottom = 26.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                val bc = remember { MutableInteractionSource() }
                val bcHover by bc.collectIsHoveredAsState()
                Box(
                    Modifier.block(if (bcHover) Sunshine else Cream, InkBlack, InkBlack, RoundedCornerShape(50), 2.dp, 2.dp)
                        .hoverable(bc).clickable(interactionSource = bc, indication = null) { onBack() }
                        .padding(horizontal = 10.dp, vertical = 3.dp)
                ) { Text("← DASHBOARD", fontFamily = display, fontWeight = FontWeight.ExtraBold, fontSize = 11.sp, letterSpacing = 0.8.sp, color = InkBlack) }
                Icon(FeatherIcons.ChevronRight, null, tint = InkBlack, modifier = Modifier.size(14.dp))
                Text(repo.repo.uppercase(), fontFamily = display, fontWeight = FontWeight.ExtraBold, fontSize = 11.sp, letterSpacing = 0.8.sp, color = InkBlack)
            }

            Spacer(Modifier.height(20.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                Monogram(repo.repo.take(1), Cream, size = 72.dp, rotation = -7f)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        Text(repo.repo, fontFamily = display, fontWeight = FontWeight.ExtraBold, fontSize = 42.sp, lineHeight = 46.sp, letterSpacing = (-0.5).sp, color = InkBlack, maxLines = 1)
                        if (repo.version.isNotBlank()) Sticker("v${repo.version}", Sunshine, rotation = 6f, fontSize = 13.sp, icon = FeatherIcons.Tag)
                    }
                    Text("@${repo.owner}", fontFamily = body, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = InkBlack.copy(alpha = 0.75f))
                }
                MaxButton("View on GitHub", { openUrl("https://github.com/${repo.owner}/${repo.repo}") }, fill = Cream, icon = FeatherIcons.ExternalLink, compact = true)
            }
            if (repo.description.isNotBlank()) {
                Spacer(Modifier.height(18.dp))
                Box(
                    Modifier.widthIn(max = 760.dp)
                        .block(Cream, InkBlack, InkBlack, RoundedCornerShape(14.dp), 4.dp, 3.dp)
                        .padding(horizontal = 16.dp, vertical = 10.dp)
                ) {
                    Text(repo.description, fontFamily = body, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, lineHeight = 21.sp, color = InkBlack)
                }
            }
        }
        Box(Modifier.fillMaxWidth().height(4.dp).background(c.Ink))

        // ── Tabs ────────────────────────────────────────────────────
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 40.dp, end = 40.dp, top = 24.dp, bottom = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            TabItem(FeatherIcons.GitPullRequest, "Open PRs", openPRs.size, tab == DetailTab.Open, Lime) { tab = DetailTab.Open }
            TabItem(FeatherIcons.Edit, "Drafts", draftPRs.size, tab == DetailTab.Drafts, Aqua) { tab = DetailTab.Drafts }
            TabItem(FeatherIcons.Tag, "Releases", releaseData?.releases?.size, tab == DetailTab.Releases, Sunshine) { tab = DetailTab.Releases }
        }

        // ── Content ─────────────────────────────────────────────────
        Box(Modifier.fillMaxSize()) {
            AnimatedContent(
                targetState = tab,
                transitionSpec = { (fadeIn(tween(200)) + scaleIn(tween(220), initialScale = 0.97f)) togetherWith fadeOut(tween(120)) },
                label = "tab"
            ) { t ->
                when (t) {
                    DetailTab.Open -> PRList(openPRs, isDraft = false, c, body)
                    DetailTab.Drafts -> PRList(draftPRs, isDraft = true, c, body)
                    DetailTab.Releases -> ReleasesTab(releaseData, loadingReleases, c, body, display)
                }
            }
        }
    }
}

// ── Open / Draft PR list ─────────────────────────────────────────

@Composable
private fun PRList(prs: List<GitHubPR>, isDraft: Boolean, c: AppColorScheme, body: FontFamily) {
    if (prs.isEmpty()) {
        EmptyState(
            if (isDraft) FeatherIcons.Edit else FeatherIcons.CheckCircle,
            if (isDraft) "No drafts" else "Inbox zero!",
            if (isDraft) "No draft pull requests here." else "This repository has no open PRs right now.",
            if (isDraft) Aqua else Lime, c, body
        )
        return
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 40.dp, end = 48.dp, top = 22.dp, bottom = 40.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        itemsIndexed(prs, key = { _, pr -> pr.number }) { i, pr -> PRCard(pr, if (isDraft) Aqua else c.accent(i), c, body) }
    }
}

@Composable
private fun PRCard(pr: GitHubPR, spine: Color, c: AppColorScheme, body: FontFamily) {
    val interaction = remember { MutableInteractionSource() }
    val lift = rememberLift(interaction, hoverLift = 4.dp)
    val shape = RoundedCornerShape(18.dp)

    Row(
        modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min)
            .offset(-lift, -lift)
            .block(c.Surface, c.Ink, c.Shadow, shape, shadowOffset = 6.dp + lift, borderWidth = 3.dp)
            .hoverable(interaction).clickable(interactionSource = interaction, indication = null) { if (pr.html_url.isNotBlank()) openUrl(pr.html_url) }
    ) {
        // Colored spine with the PR number
        Box(
            Modifier.width(76.dp).fillMaxHeight().background(spine)
                .dots(InkBlack.copy(alpha = 0.18f), spacing = 10.dp, radius = 1.4.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Icon(FeatherIcons.GitPullRequest, null, tint = InkBlack, modifier = Modifier.size(18.dp))
                Text("#${pr.number}", fontFamily = LocalDisplayFont.current, fontWeight = FontWeight.ExtraBold, fontSize = 15.sp, color = InkBlack)
            }
        }
        Box(Modifier.width(3.dp).fillMaxHeight().background(c.Ink))
        Column(Modifier.weight(1f).padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(pr.title, fontFamily = LocalDisplayFont.current, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp, lineHeight = 22.sp, color = c.TextPrimary)
            if (pr.labels.isNotEmpty()) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { pr.labels.take(5).forEach { LabelChip(it, body) } }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Avatar(pr.user.login, c, body)
                Text(pr.user.login, fontFamily = body, fontWeight = FontWeight.Bold, fontSize = 12.sp, color = c.TextPrimary)
                Text("✦", fontSize = 10.sp, color = c.Primary)
                Text("opened ${pr.created_at.take(10)}", fontFamily = body, fontWeight = FontWeight.SemiBold, fontSize = 12.sp, color = c.TextMuted)
                if (pr.draft) Sticker("draft", Aqua, rotation = -4f, fontSize = 9.sp)
            }
        }
        Box(Modifier.padding(end = 18.dp).fillMaxHeight(), contentAlignment = Alignment.Center) {
            Icon(FeatherIcons.ExternalLink, null, tint = c.TextMuted, modifier = Modifier.size(16.dp))
        }
    }
}

// ── Releases tab ─────────────────────────────────────────────────

@Composable
private fun ReleasesTab(data: ReleaseData?, loading: Boolean, c: AppColorScheme, body: FontFamily, display: FontFamily) {
    if (loading || data == null) {
        MaxLoader("Loading releases…")
        return
    }
    if (data.releases.isEmpty() && data.unreleased.isEmpty()) {
        EmptyState(FeatherIcons.Tag, "No releases", "This repository has no published releases.", Sunshine, c, body)
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 40.dp, end = 48.dp, top = 22.dp, bottom = 40.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        if (data.unreleased.isNotEmpty()) {
            item {
                Box {
                    Column(
                        modifier = Modifier.fillMaxWidth()
                            .block(Sunshine, c.Ink, c.Shadow, RoundedCornerShape(20.dp), shadowOffset = 7.dp, borderWidth = 3.dp)
                            .stripes(Color.White.copy(alpha = 0.3f), gap = 26.dp, width = 9.dp)
                            .padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Icon(FeatherIcons.Clock, null, tint = InkBlack, modifier = Modifier.size(20.dp))
                            Text("UNRELEASED", fontFamily = display, fontWeight = FontWeight.ExtraBold, fontSize = 22.sp, letterSpacing = 0.5.sp, color = InkBlack)
                            BurstBadge(data.unreleased.size, HotPink, size = 36.dp, rotation = 8f)
                            Spacer(Modifier.weight(1f))
                            Text("merged, not in latest release", fontFamily = body, fontWeight = FontWeight.Bold, fontSize = 12.sp, color = InkBlack.copy(alpha = 0.75f))
                        }
                        Column(
                            Modifier.fillMaxWidth().block(Cream, InkBlack, InkBlack, RoundedCornerShape(14.dp), 0.dp, 2.dp).padding(6.dp)
                        ) {
                            data.unreleased.forEach { pr -> MergedPRRow(pr, onLight = true, c, body) }
                        }
                    }
                    Sticker("ship it?", HotPink, rotation = 10f, fontSize = 12.sp, modifier = Modifier.align(Alignment.TopEnd).offset(8.dp, (-14).dp))
                }
            }
        }
        itemsIndexed(data.releases, key = { _, r -> r.tagName }) { i, rel -> ReleaseCard(rel, c.accent(i + 2), c, body, display) }
    }
}

@Composable
private fun ReleaseCard(rel: ReleaseInfo, accent: Color, c: AppColorScheme, body: FontFamily, display: FontFamily) {
    var expanded by remember(rel.tagName) { mutableStateOf(false) }
    val interaction = remember { MutableInteractionSource() }
    val lift = rememberLift(interaction, hoverLift = 3.dp)

    Column(
        modifier = Modifier.fillMaxWidth()
            .offset(-lift, -lift)
            .block(c.Surface, c.Ink, c.Shadow, RoundedCornerShape(18.dp), shadowOffset = 6.dp + lift, borderWidth = 3.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth()
                .hoverable(interaction).clickable(interactionSource = interaction, indication = null) { expanded = !expanded }
                .padding(18.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Box(
                Modifier.size(46.dp).rotate(-6f).block(accent, c.Ink, c.Shadow, RoundedCornerShape(13.dp), 3.dp, 3.dp),
                contentAlignment = Alignment.Center
            ) { Icon(FeatherIcons.Package, null, tint = InkBlack, modifier = Modifier.size(22.dp).rotate(6f)) }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(rel.tagName, fontFamily = display, fontWeight = FontWeight.ExtraBold, fontSize = 20.sp, color = c.TextPrimary)
                    if (rel.prs.isNotEmpty()) BurstBadge(rel.prs.size, accent, size = 30.dp, rotation = -8f)
                }
                Text(
                    if (rel.name != rel.tagName && rel.name.isNotBlank()) "${rel.name} · ${rel.publishedAt.take(10)}" else rel.publishedAt.take(10),
                    fontFamily = body, fontWeight = FontWeight.SemiBold, fontSize = 12.sp, color = c.TextMuted, maxLines = 1
                )
            }
            MaxIconButton(FeatherIcons.ExternalLink, { openUrl(rel.htmlUrl) }, fill = Cream, size = 32.dp)
            MaxIconButton(if (expanded) FeatherIcons.ChevronDown else FeatherIcons.ChevronRight, { expanded = !expanded }, fill = accent, size = 32.dp)
        }
        if (expanded) {
            Box(Modifier.fillMaxWidth().padding(horizontal = 18.dp)) { Squiggle(accent, amplitude = 3.dp, wavelength = 16.dp, stroke = 3.dp) }
            if (rel.prs.isEmpty()) {
                Text("No pull requests detected for this release. Open it on GitHub for details.",
                    fontFamily = body, fontWeight = FontWeight.SemiBold, fontSize = 12.sp, color = c.TextMuted, modifier = Modifier.padding(18.dp))
            } else {
                Column(Modifier.padding(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    rel.prs.forEach { pr -> MergedPRRow(pr, onLight = false, c, body) }
                }
            }
        }
    }
}

@Composable
private fun MergedPRRow(pr: MergedPR, onLight: Boolean, c: AppColorScheme, body: FontFamily) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val bg by animateColorAsState(if (hovered) (if (onLight) Sunshine else c.SurfaceVariant) else Color.Transparent, tween(150))
    val fg = if (onLight) InkBlack else c.TextPrimary
    val muted = if (onLight) InkBlack.copy(alpha = 0.6f) else c.TextMuted
    Row(
        modifier = Modifier.fillMaxWidth().background(bg, RoundedCornerShape(10.dp))
            .hoverable(interaction).clickable(interactionSource = interaction, indication = null) { if (pr.prUrl.isNotBlank()) openUrl(pr.prUrl) }
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Box(
            Modifier.background(Lime, RoundedCornerShape(50)).border(2.dp, InkBlack, RoundedCornerShape(50)).padding(horizontal = 7.dp, vertical = 1.dp)
        ) { Text("#${pr.id}", fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = 11.sp, color = InkBlack) }
        Text(pr.title, fontFamily = body, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = fg, maxLines = 1, modifier = Modifier.weight(1f))
        Text(pr.author, fontFamily = body, fontWeight = FontWeight.Bold, fontSize = 11.sp, color = muted)
        Icon(FeatherIcons.ExternalLink, null, tint = muted, modifier = Modifier.size(12.dp))
    }
}

// ── Shared pieces ────────────────────────────────────────────────

@Composable
private fun TabItem(icon: ImageVector, label: String, count: Int?, selected: Boolean, accent: Color, onClick: () -> Unit) {
    val c = LocalAppColors.current
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val lift = rememberLift(interaction, hoverLift = 2.dp)
    val shape = RoundedCornerShape(14.dp)
    val bg = when { selected -> accent; hovered -> c.SurfaceVariant; else -> c.Surface }
    val fg = if (selected) InkBlack else c.TextPrimary
    Row(
        modifier = Modifier
            .offset(-lift, -lift)
            .block(bg, c.Ink, c.Shadow, shape, shadowOffset = (if (selected) 5.dp else 2.dp) + lift, borderWidth = 3.dp)
            .hoverable(interaction).clickable(interactionSource = interaction, indication = null) { onClick() }
            .padding(horizontal = 16.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(9.dp), verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = fg, modifier = Modifier.size(16.dp))
        Text(label.uppercase(), fontFamily = LocalDisplayFont.current, fontWeight = FontWeight.ExtraBold, fontSize = 13.sp, letterSpacing = 0.8.sp, color = fg)
        if (count != null) {
            Box(
                Modifier.defaultMinSize(minWidth = 22.dp, minHeight = 22.dp)
                    .background(if (selected) Cream else accent, CircleShape).border(2.dp, if (selected) InkBlack else c.Ink, CircleShape)
                    .padding(horizontal = 6.dp),
                contentAlignment = Alignment.Center
            ) { Text("$count", fontFamily = LocalBodyFont.current, fontWeight = FontWeight.ExtraBold, fontSize = 11.sp, color = InkBlack) }
        }
    }
}

@Composable
private fun Avatar(name: String, c: AppColorScheme, body: FontFamily) {
    Box(
        Modifier.size(24.dp).background(c.accent(name.hashCode()), CircleShape).border(2.dp, c.Ink, CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Text(name.take(1).uppercase(), fontFamily = body, fontWeight = FontWeight.ExtraBold, fontSize = 11.sp, color = InkBlack)
    }
}

@Composable
private fun EmptyState(icon: ImageVector, title: String, subtitle: String, fill: Color, c: AppColorScheme, body: FontFamily) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Box(
                Modifier.size(110.dp).rotate(12f).block(fill, c.Ink, c.Shadow, BurstShape(12, 0.22f), 6.dp, 4.dp),
                contentAlignment = Alignment.Center
            ) { Icon(icon, null, tint = InkBlack, modifier = Modifier.size(36.dp).rotate(-12f)) }
            Text(title.uppercase(), fontFamily = LocalDisplayFont.current, fontWeight = FontWeight.ExtraBold, fontSize = 28.sp, letterSpacing = 0.5.sp, color = c.TextPrimary)
            Text(subtitle, fontFamily = body, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = c.TextSecondary)
        }
    }
}

@Composable
private fun LabelChip(label: GitHubPRLabel, body: FontFamily) {
    val c = LocalAppColors.current
    val base = parseHexColor(label.color)
    // Use the label's real GitHub color as a solid fill, with whichever ink reads on it.
    val text = if (base.luminance() > 0.4f) InkBlack else Color.White
    Box(
        Modifier.rotate(if (label.name.length % 2 == 0) -2f else 2f)
            .block(base, c.Ink, c.Shadow, RoundedCornerShape(8.dp), shadowOffset = 2.dp, borderWidth = 2.dp)
            .padding(horizontal = 9.dp, vertical = 3.dp)
    ) {
        Text(label.name, fontFamily = body, fontWeight = FontWeight.ExtraBold, fontSize = 11.sp, color = text)
    }
}

private fun parseHexColor(hex: String): Color = try {
    val v = hex.removePrefix("#").padStart(6, '0').toLong(16)
    Color(0xFF000000L or v)
} catch (_: Exception) { Color(0xFF9AA0A6) }

// ── Data loading ─────────────────────────────────────────────────

private suspend fun loadReleaseData(client: GitHubClient, repo: Repository): ReleaseData {
    val releases = client.getReleases(repo.owner, repo.repo).getOrElse { emptyList() }
    val latestTag = releases.firstOrNull()?.tag_name
    val unreleased = if (latestTag != null) {
        client.compareBranches(repo.owner, repo.repo, latestTag, repo.defaultBranch).getOrNull()?.let { parsePRs(it, repo) } ?: emptyList()
    } else emptyList()

    val infos = releases.take(20).mapIndexed { i, rel ->
        val prevTag = releases.getOrNull(i + 1)?.tag_name
        val prs = if (i < 6 && prevTag != null) {
            client.compareBranches(repo.owner, repo.repo, prevTag, rel.tag_name).getOrNull()?.let { parsePRs(it, repo) } ?: emptyList()
        } else emptyList()
        ReleaseInfo(rel.tag_name, rel.name.ifBlank { rel.tag_name }, rel.published_at, rel.html_url, prs)
    }
    return ReleaseData(unreleased, infos)
}

private fun parsePRs(cmp: GitHubCompareResponse, repo: Repository): List<MergedPR> =
    cmp.commits.mapNotNull { commit ->
        val m = Regex("#(\\d+)").find(commit.commit.message) ?: return@mapNotNull null
        val id = m.groupValues[1]
        MergedPR(
            id = id, title = commit.commit.message.lines().first(), author = commit.commit.author.name,
            mergedDate = commit.commit.author.date, repo = repo.repo,
            prUrl = "https://github.com/${repo.owner}/${repo.repo}/pull/$id", commitHash = commit.sha.take(7)
        )
    }
