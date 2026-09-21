package com.example.repodashboard.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
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
import com.example.repodashboard.ui.theme.AppColorScheme
import com.example.repodashboard.ui.theme.FeatherIcons
import com.example.repodashboard.ui.theme.LocalAppColors
import com.example.repodashboard.ui.theme.LocalBodyFont
import com.example.repodashboard.ui.theme.LocalDisplayFont

private enum class DetailTab { Open, Drafts, Releases }

@Composable
fun RepoDetailScreen(repo: Repository, client: GitHubClient?, onBack: () -> Unit) {
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
        Column(Modifier.fillMaxWidth().background(c.Surface).padding(horizontal = 40.dp, vertical = 24.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                val bc = remember { MutableInteractionSource() }
                Text("Dashboard", fontFamily = body, fontSize = 12.sp, color = c.TextMuted,
                    modifier = Modifier.clip(RoundedCornerShape(4.dp)).hoverable(bc).clickable(bc, indication = null) { onBack() })
                Icon(FeatherIcons.ChevronRight, null, tint = c.TextMuted, modifier = Modifier.size(13.dp))
                Text(repo.repo, fontFamily = body, fontWeight = FontWeight.Medium, fontSize = 12.sp, color = c.TextSecondary)
            }

            Spacer(Modifier.height(16.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                Box(Modifier.size(48.dp).background(c.PrimaryLight, RoundedCornerShape(13.dp)), contentAlignment = Alignment.Center) {
                    Text(repo.repo.take(1).uppercase(), fontFamily = display, fontWeight = FontWeight.Bold, fontSize = 22.sp, color = c.Primary)
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(repo.repo, fontFamily = display, fontWeight = FontWeight.Bold, fontSize = 22.sp, color = c.TextPrimary)
                        if (repo.version.isNotBlank()) {
                            Row(
                                modifier = Modifier.border(1.dp, c.Primary, RoundedCornerShape(6.dp)).padding(horizontal = 7.dp, vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(FeatherIcons.Tag, null, tint = c.Primary, modifier = Modifier.size(11.dp))
                                Text("v${repo.version}", fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = 11.sp, color = c.Primary)
                            }
                        }
                    }
                    Text(repo.owner, fontFamily = body, fontSize = 13.sp, color = c.TextMuted)
                }
                GitHubButton(c, body) { openUrl("https://github.com/${repo.owner}/${repo.repo}") }
            }
            if (repo.description.isNotBlank()) {
                Spacer(Modifier.height(14.dp))
                Text(repo.description, fontFamily = body, fontSize = 13.sp, lineHeight = 20.sp, color = c.TextSecondary)
            }
        }

        // ── Tabs ────────────────────────────────────────────────────
        Column(Modifier.fillMaxWidth().background(c.Surface)) {
            Row(modifier = Modifier.padding(horizontal = 40.dp), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                TabItem(FeatherIcons.GitPullRequest, "Open PRs", openPRs.size, tab == DetailTab.Open, c, body) { tab = DetailTab.Open }
                TabItem(FeatherIcons.Edit, "Drafts", draftPRs.size, tab == DetailTab.Drafts, c, body) { tab = DetailTab.Drafts }
                TabItem(FeatherIcons.Tag, "Releases", releaseData?.releases?.size, tab == DetailTab.Releases, c, body) { tab = DetailTab.Releases }
            }
            HorizontalDivider(color = c.Border)
        }

        // ── Content ─────────────────────────────────────────────────
        Box(Modifier.fillMaxSize().background(c.Background)) {
            AnimatedContent(
                targetState = tab,
                transitionSpec = { fadeIn(tween(180)) togetherWith fadeOut(tween(120)) },
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
            if (isDraft) "No drafts" else "No open pull requests",
            if (isDraft) "No draft pull requests here." else "This repository has no open PRs right now.",
            if (isDraft) c.TextMuted else c.Success, c, body
        )
        return
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 40.dp),
        contentPadding = PaddingValues(vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        items(prs, key = { it.number }) { pr -> PRCard(pr, c, body) }
    }
}

@Composable
private fun PRCard(pr: GitHubPR, c: AppColorScheme, body: FontFamily) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val elevation by animateDpAsState(if (hovered) 6.dp else 0.dp, tween(200))
    val borderColor by animateColorAsState(if (hovered) c.PrimaryMid else c.Border, tween(200))

    Row(
        modifier = Modifier.fillMaxWidth()
            .shadow(elevation, RoundedCornerShape(14.dp), clip = false)
            .clip(RoundedCornerShape(14.dp)).background(c.Surface)
            .border(1.dp, borderColor, RoundedCornerShape(14.dp))
            .hoverable(interaction).clickable(interaction, indication = null) { if (pr.html_url.isNotBlank()) openUrl(pr.html_url) }
            .padding(18.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.Top
    ) {
        Box(Modifier.padding(top = 3.dp)) {
            Icon(FeatherIcons.GitPullRequest, null, tint = if (pr.draft) c.TextMuted else c.Success, modifier = Modifier.size(16.dp))
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(pr.title, fontFamily = body, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, lineHeight = 20.sp, color = c.TextPrimary)
            if (pr.labels.isNotEmpty()) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { pr.labels.take(5).forEach { LabelChip(it, body) } }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(7.dp), verticalAlignment = Alignment.CenterVertically) {
                Avatar(pr.user.login, c, body)
                Text(pr.user.login, fontFamily = body, fontSize = 12.sp, color = c.TextSecondary)
                Text("·", fontSize = 12.sp, color = c.TextMuted)
                Text("opened ${pr.created_at.take(10)}", fontFamily = body, fontSize = 12.sp, color = c.TextMuted)
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("#${pr.number}", fontFamily = FontFamily.Monospace, fontSize = 12.sp, color = c.TextMuted)
            Icon(FeatherIcons.ExternalLink, null, tint = c.TextMuted, modifier = Modifier.size(13.dp))
        }
    }
}

// ── Releases tab ─────────────────────────────────────────────────

@Composable
private fun ReleasesTab(data: ReleaseData?, loading: Boolean, c: AppColorScheme, body: FontFamily, display: FontFamily) {
    if (loading || data == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                CircularProgressIndicator(color = c.Primary, strokeWidth = 2.5.dp, modifier = Modifier.size(28.dp))
                Text("Loading releases…", fontFamily = body, fontSize = 13.sp, color = c.TextSecondary)
            }
        }
        return
    }
    if (data.releases.isEmpty() && data.unreleased.isEmpty()) {
        EmptyState(FeatherIcons.Tag, "No releases", "This repository has no published releases.", c.TextMuted, c, body)
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 40.dp),
        contentPadding = PaddingValues(vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        if (data.unreleased.isNotEmpty()) {
            item {
                Column(
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
                        .background(c.WarningLight).border(1.dp, c.Warning.copy(alpha = 0.35f), RoundedCornerShape(14.dp)).padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                        Icon(FeatherIcons.Clock, null, tint = c.Warning, modifier = Modifier.size(16.dp))
                        Text("Unreleased", fontFamily = display, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = c.Warning)
                        CountPill(data.unreleased.size, c.Warning, c.Warning.copy(alpha = 0.18f), body)
                        Spacer(Modifier.weight(1f))
                        Text("merged, not in latest release", fontFamily = body, fontSize = 11.sp, color = c.TextMuted)
                    }
                    data.unreleased.forEach { pr -> MergedPRRow(pr, c, body) }
                }
            }
            item { Spacer(Modifier.height(2.dp)) }
        }
        items(data.releases, key = { it.tagName }) { rel -> ReleaseCard(rel, c, body, display) }
    }
}

@Composable
private fun ReleaseCard(rel: ReleaseInfo, c: AppColorScheme, body: FontFamily, display: FontFamily) {
    var expanded by remember(rel.tagName) { mutableStateOf(false) }

    Column(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(c.Surface).border(1.dp, c.Border, RoundedCornerShape(14.dp))
    ) {
        val interaction = remember { MutableInteractionSource() }
        Row(
            modifier = Modifier.fillMaxWidth().clickable(interaction, indication = null) { expanded = !expanded }.padding(18.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(Modifier.size(34.dp).background(c.PrimaryLight, RoundedCornerShape(9.dp)), contentAlignment = Alignment.Center) {
                Icon(FeatherIcons.Package, null, tint = c.Primary, modifier = Modifier.size(17.dp))
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(rel.tagName, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = c.TextPrimary)
                    if (rel.prs.isNotEmpty()) CountPill(rel.prs.size, c.Primary, c.Primary.copy(alpha = 0.14f), body)
                }
                Text(
                    if (rel.name != rel.tagName && rel.name.isNotBlank()) "${rel.name} · ${rel.publishedAt.take(10)}" else rel.publishedAt.take(10),
                    fontFamily = body, fontSize = 12.sp, color = c.TextMuted, maxLines = 1
                )
            }
            IconButtonSmall(FeatherIcons.ExternalLink, c) { openUrl(rel.htmlUrl) }
            Icon(if (expanded) FeatherIcons.ChevronDown else FeatherIcons.ChevronRight, null, tint = c.TextMuted, modifier = Modifier.size(16.dp))
        }
        if (expanded) {
            HorizontalDivider(color = c.Divider, modifier = Modifier.padding(horizontal = 18.dp))
            if (rel.prs.isEmpty()) {
                Text("No pull requests detected for this release. Open it on GitHub for details.",
                    fontFamily = body, fontSize = 12.sp, color = c.TextMuted, modifier = Modifier.padding(18.dp))
            } else {
                Column(Modifier.padding(start = 18.dp, end = 18.dp, top = 12.dp, bottom = 14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    rel.prs.forEach { pr -> MergedPRRow(pr, c, body) }
                }
            }
        }
    }
}

@Composable
private fun MergedPRRow(pr: MergedPR, c: AppColorScheme, body: FontFamily) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val bg by animateColorAsState(if (hovered) c.SurfaceHover else Color.Transparent, tween(150))
    Row(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(9.dp)).background(bg)
            .hoverable(interaction).clickable(interaction, indication = null) { if (pr.prUrl.isNotBlank()) openUrl(pr.prUrl) }
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Icon(FeatherIcons.GitPullRequest, null, tint = c.Success, modifier = Modifier.size(14.dp))
        Text("#${pr.id}", fontFamily = FontFamily.Monospace, fontSize = 12.sp, color = c.TextMuted)
        Text(pr.title, fontFamily = body, fontSize = 13.sp, color = c.TextPrimary, maxLines = 1, modifier = Modifier.weight(1f))
        Text(pr.author, fontFamily = body, fontSize = 11.sp, color = c.TextMuted)
        Icon(FeatherIcons.ExternalLink, null, tint = c.TextMuted, modifier = Modifier.size(12.dp))
    }
}

// ── Shared pieces ────────────────────────────────────────────────

@Composable
private fun TabItem(icon: ImageVector, label: String, count: Int?, selected: Boolean, c: AppColorScheme, body: FontFamily, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val fg by animateColorAsState(when { selected -> c.Primary; hovered -> c.TextPrimary; else -> c.TextSecondary }, tween(150))
    val underline by animateColorAsState(if (selected) c.Primary else Color.Transparent, tween(150))
    Column(
        modifier = Modifier.width(IntrinsicSize.Max).hoverable(interaction).clickable(interaction, indication = null) { onClick() },
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(7.dp), verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, null, tint = fg, modifier = Modifier.size(15.dp))
            Text(label, fontFamily = body, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium, fontSize = 14.sp, color = fg)
            if (count != null) CountPill(count, if (selected) c.Primary else c.TextMuted, if (selected) c.Primary.copy(alpha = 0.14f) else c.SurfaceVariant, body)
        }
        Box(Modifier.padding(horizontal = 4.dp).height(2.dp).fillMaxWidth().background(underline, RoundedCornerShape(topStart = 2.dp, topEnd = 2.dp)))
    }
}

@Composable
private fun CountPill(count: Int, fg: Color, bg: Color, body: FontFamily) {
    Box(
        modifier = Modifier.defaultMinSize(minWidth = 20.dp, minHeight = 20.dp).background(bg, CircleShape).padding(horizontal = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Text("$count", fontFamily = body, fontWeight = FontWeight.SemiBold, fontSize = 11.sp, color = fg)
    }
}

@Composable
private fun GitHubButton(c: AppColorScheme, body: FontFamily, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val bg by animateColorAsState(if (hovered) c.SurfaceVariant else c.Surface, tween(150))
    Row(
        modifier = Modifier.clip(RoundedCornerShape(9.dp)).background(bg).border(1.dp, c.Border, RoundedCornerShape(9.dp))
            .hoverable(interaction).clickable(interaction, indication = null) { onClick() }.padding(horizontal = 14.dp, vertical = 9.dp),
        horizontalArrangement = Arrangement.spacedBy(7.dp), verticalAlignment = Alignment.CenterVertically
    ) {
        Text("View on GitHub", fontFamily = body, fontWeight = FontWeight.Medium, fontSize = 12.sp, color = c.TextSecondary)
        Icon(FeatherIcons.ExternalLink, null, tint = c.TextMuted, modifier = Modifier.size(13.dp))
    }
}

@Composable
private fun IconButtonSmall(icon: ImageVector, c: AppColorScheme, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val bg by animateColorAsState(if (hovered) c.SurfaceVariant else Color.Transparent, tween(150))
    Box(
        modifier = Modifier.size(30.dp).clip(RoundedCornerShape(8.dp)).background(bg)
            .hoverable(interaction).clickable(interaction, indication = null) { onClick() },
        contentAlignment = Alignment.Center
    ) { Icon(icon, null, tint = c.TextMuted, modifier = Modifier.size(15.dp)) }
}

@Composable
private fun Avatar(name: String, c: AppColorScheme, body: FontFamily) {
    Box(Modifier.size(20.dp).background(c.SurfaceVariant, CircleShape), contentAlignment = Alignment.Center) {
        Text(name.take(1).uppercase(), fontFamily = body, fontWeight = FontWeight.Bold, fontSize = 9.sp, color = c.TextSecondary)
    }
}

@Composable
private fun EmptyState(icon: ImageVector, title: String, subtitle: String, iconColor: Color, c: AppColorScheme, body: FontFamily) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(Modifier.size(58.dp).background(iconColor.copy(alpha = 0.12f), CircleShape), contentAlignment = Alignment.Center) {
                Icon(icon, null, tint = iconColor, modifier = Modifier.size(24.dp))
            }
            Text(title, fontFamily = body, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, color = c.TextPrimary)
            Text(subtitle, fontFamily = body, fontSize = 13.sp, color = c.TextSecondary)
        }
    }
}

@Composable
private fun LabelChip(label: GitHubPRLabel, body: FontFamily) {
    val base = parseHexColor(label.color)
    Box(Modifier.background(base.copy(alpha = 0.16f), RoundedCornerShape(20.dp)).border(1.dp, base.copy(alpha = 0.35f), RoundedCornerShape(20.dp)).padding(horizontal = 9.dp, vertical = 3.dp)) {
        Text(label.name, fontFamily = body, fontWeight = FontWeight.Medium, fontSize = 10.sp, color = base)
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
