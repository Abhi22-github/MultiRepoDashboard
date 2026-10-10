package com.example.repodashboard.ui.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.repodashboard.github.GitHubClient
import com.example.repodashboard.models.AppState
import com.example.repodashboard.models.Repository
import com.example.repodashboard.ui.theme.*

@Composable
fun DashboardScreen(
    state: AppState,
    client: GitHubClient?,
    pinned: Set<String>,
    onTogglePin: (String) -> Unit,
    onRefresh: () -> Unit,
    onRepoSelected: (String) -> Unit,
    onDashboardSelected: () -> Unit,
    onClearToken: () -> Unit,
    onManageRepos: () -> Unit,
    isDark: Boolean,
    onToggleTheme: () -> Unit
) {
    val c       = LocalAppColors.current
    val body    = LocalBodyFont.current
    val display = LocalDisplayFont.current
    val selectedRepo = state.repositories.find { it.name == state.selectedRepoName }
    val ordered = remember(state.repositories, pinned) {
        state.repositories.sortedByDescending { it.name in pinned }
    }
    // Stable accent per repo, independent of pin ordering.
    val accentIndex = remember(state.repositories) {
        state.repositories.mapIndexed { i, r -> r.name to i }.toMap()
    }

    Row(modifier = Modifier.fillMaxSize().background(c.Background)) {

        // ── Sidebar ─────────────────────────────────────────────────
        Row(modifier = Modifier.width(272.dp).fillMaxHeight()) {
            Column(
                modifier = Modifier.weight(1f).fillMaxHeight().background(c.Sidebar)
                    .dots(Color.White.copy(alpha = 0.07f), spacing = 16.dp, radius = 1.6.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 24.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        Modifier.size(42.dp).rotate(-8f).block(HotPink, InkBlack, Sunshine, RoundedCornerShape(12.dp), 4.dp),
                        contentAlignment = Alignment.Center
                    ) { Icon(FeatherIcons.GitBranch, null, tint = InkBlack, modifier = Modifier.size(22.dp).rotate(8f)) }
                    Column {
                        Text("PR//DASH", fontFamily = display, fontWeight = FontWeight.ExtraBold, fontSize = 22.sp, letterSpacing = 1.sp, color = Sunshine)
                        Text("all your repos, loudly", fontFamily = body, fontWeight = FontWeight.Bold, fontSize = 10.sp, color = c.SidebarTextMuted)
                    }
                }

                Column(Modifier.padding(horizontal = 14.dp)) {
                    NavRow(FeatherIcons.Grid, "Dashboard", state.selectedRepoName == null, Sunshine, body, onDashboardSelected)
                }

                Spacer(Modifier.height(18.dp))
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically
                ) {
                    Sticker("Repositories", Aqua, rotation = -3f, fontSize = 10.sp)
                    MaxIconButton(FeatherIcons.RefreshCw, onRefresh, fill = Lime, enabled = !state.isLoading, size = 28.dp)
                }
                Squiggle(c.Primary, Modifier.padding(horizontal = 18.dp, vertical = 10.dp), amplitude = 3.dp, wavelength = 14.dp, stroke = 2.5.dp)

                LazyColumn(
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(start = 14.dp, end = 18.dp, top = 4.dp, bottom = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    itemsIndexed(ordered, key = { _, r -> r.name }) { _, repo ->
                        SidebarRepoItem(
                            repo, state.selectedRepoName == repo.name, repo.name in pinned,
                            c.accent(accentIndex[repo.name] ?: 0), body
                        ) { onRepoSelected(repo.name) }
                    }
                    if (state.repositories.isEmpty() && !state.isLoading) {
                        item {
                            Text("No repos yet", fontFamily = body, fontWeight = FontWeight.Bold, fontSize = 12.sp, color = c.SidebarTextMuted,
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp))
                        }
                    }
                }

                Box(Modifier.fillMaxWidth().height(3.dp).background(c.SidebarBorder))
                Column(Modifier.padding(horizontal = 14.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    NavRow(if (isDark) FeatherIcons.Sun else FeatherIcons.Moon, if (isDark) "Light mode" else "Dark mode", false, Sunshine, body, onToggleTheme)
                    NavRow(FeatherIcons.Sliders, "Manage repos", false, Aqua, body, onManageRepos)
                    NavRow(FeatherIcons.LogOut, "Sign out", false, HotPink, body, onClearToken)
                }
            }
            Box(Modifier.width(4.dp).fillMaxHeight().background(c.SidebarBorder))
        }

        // ── Body ─────────────────────────────────────────────────────
        Box(
            modifier = Modifier.weight(1f).fillMaxHeight()
                .dots(c.Pattern, spacing = 22.dp, radius = 1.8.dp)
        ) {
            when {
                state.isLoading -> MaxLoader("Fetching from GitHub…")
                state.error != null -> Box(Modifier.fillMaxSize().padding(40.dp)) {
                    MaxNotice(FeatherIcons.AlertTriangle, state.error, c.Error)
                }
                selectedRepo != null -> RepoDetailScreen(selectedRepo, client, onBack = onDashboardSelected, accent = c.accent(accentIndex[selectedRepo.name] ?: 0))
                state.repositories.isEmpty() -> EmptyBody(c, body, display, onManageRepos)
                else -> DashboardGrid(state, ordered, pinned, accentIndex, c, body, display, onRepoSelected, onTogglePin)
            }
        }
    }
}

// ── Dashboard grid ─────────────────────────────────────────────────

@Composable
private fun DashboardGrid(
    state: AppState, ordered: List<Repository>, pinned: Set<String>, accentIndex: Map<String, Int>,
    c: AppColorScheme, body: FontFamily, display: FontFamily,
    onRepoSelected: (String) -> Unit, onTogglePin: (String) -> Unit
) {
    val totalOpen  = state.repositories.sumOf { r -> r.openPRs.count { !it.draft } }
    val totalDraft = state.repositories.sumOf { r -> r.openPRs.count { it.draft } }
    val busiest = state.repositories.maxByOrNull { it.openPRsCount }

    val ticker = buildList {
        add("$totalOpen open pull requests")
        add("$totalDraft drafts cooking")
        add("${state.repositories.size} repos on the wall")
        if (busiest != null && busiest.openPRsCount > 0) add("busiest: ${busiest.repo} (${busiest.openPRsCount})")
        state.repositories.filter { it.version.isNotBlank() }.take(4).forEach { add("${it.repo} v${it.version}") }
    }

    Column(Modifier.fillMaxSize()) {
        MarqueeBand(ticker, Sunshine)

        LazyVerticalGrid(
            columns = GridCells.Adaptive(310.dp),
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 40.dp, end = 46.dp, top = 32.dp, bottom = 48.dp),
            horizontalArrangement = Arrangement.spacedBy(26.dp),
            verticalArrangement = Arrangement.spacedBy(28.dp)
        ) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        Text("DASHBOARD", fontFamily = display, fontWeight = FontWeight.ExtraBold, fontSize = 56.sp, lineHeight = 60.sp, letterSpacing = (-1).sp, color = c.TextPrimary)
                        Sticker("live", HotPink, rotation = 8f, fontSize = 13.sp)
                    }
                    Box(Modifier.width(280.dp)) { Squiggle(c.Primary, amplitude = 5.dp, wavelength = 22.dp, stroke = 4.dp) }
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "${state.repositories.size} repositories · $totalOpen open pull requests",
                        fontFamily = body, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = c.TextSecondary
                    )
                    Spacer(Modifier.height(26.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(22.dp)) {
                        MetricCard(FeatherIcons.GitBranch, "${state.repositories.size}", "Repositories", Aqua, -2f, display, body)
                        MetricCard(FeatherIcons.GitPullRequest, "$totalOpen", "Open PRs", HotPink, 1.5f, display, body)
                        MetricCard(FeatherIcons.Edit, "$totalDraft", "Drafts", Sunshine, -1f, display, body)
                    }
                    Spacer(Modifier.height(8.dp))
                }
            }
            itemsIndexed(ordered, key = { _, r -> r.name }) { i, repo ->
                RepoCard(
                    repo, repo.name in pinned, c.accent(accentIndex[repo.name] ?: i),
                    tilt = if (i % 2 == 0) -0.6f else 0.6f,
                    c, body, display,
                    onClick = { onRepoSelected(repo.name) }, onTogglePin = { onTogglePin(repo.name) }
                )
            }
        }
    }
}

@Composable
private fun MetricCard(icon: ImageVector, value: String, label: String, fill: Color, rotation: Float, display: FontFamily, body: FontFamily) {
    val c = LocalAppColors.current
    Row(
        modifier = Modifier.rotate(rotation)
            .block(fill, c.Ink, c.Shadow, RoundedCornerShape(20.dp), shadowOffset = 7.dp, borderWidth = 3.dp)
            .stripes(Color.White.copy(alpha = 0.22f), gap = 26.dp, width = 9.dp)
            .padding(horizontal = 22.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Box(
            Modifier.size(40.dp).block(Cream, InkBlack, InkBlack, RoundedCornerShape(50), 3.dp, 3.dp),
            contentAlignment = Alignment.Center
        ) { Icon(icon, null, tint = InkBlack, modifier = Modifier.size(19.dp)) }
        Column {
            Text(value, fontFamily = display, fontWeight = FontWeight.ExtraBold, fontSize = 38.sp, lineHeight = 40.sp, color = c.OnBright)
            Text(label.uppercase(), fontFamily = body, fontWeight = FontWeight.Bold, fontSize = 11.sp, letterSpacing = 1.2.sp, color = c.OnBright)
        }
    }
}

@Composable
private fun RepoCard(
    repo: Repository, isPinned: Boolean, accent: Color, tilt: Float,
    c: AppColorScheme, body: FontFamily, display: FontFamily,
    onClick: () -> Unit, onTogglePin: () -> Unit
) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val lift = rememberLift(interaction, hoverLift = 5.dp)
    val rotation by animateFloatAsState(if (hovered) tilt * -2.5f else tilt, spring(dampingRatio = 0.45f, stiffness = 400f))
    val open  = repo.openPRs.count { !it.draft }
    val draft = repo.openPRs.count { it.draft }
    val shape = RoundedCornerShape(22.dp)

    Box {
        Column(
            modifier = Modifier.fillMaxWidth().heightIn(min = 220.dp)
                .offset(-lift, -lift)
                .graphicsLayer { rotationZ = rotation }
                .block(c.Surface, c.Ink, c.Shadow, shape, shadowOffset = 8.dp + lift, borderWidth = 3.dp)
                .hoverable(interaction).clickable(interactionSource = interaction, indication = null) { onClick() }
        ) {
            // Accent header
            Row(
                modifier = Modifier.fillMaxWidth().background(accent)
                    .stripes(Color.White.copy(alpha = 0.25f), gap = 20.dp, width = 7.dp)
                    .padding(horizontal = 18.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Monogram(repo.repo.take(1), Cream, size = 46.dp, rotation = -8f)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                    Text(repo.repo, fontFamily = display, fontWeight = FontWeight.ExtraBold, fontSize = 19.sp, color = c.OnBright, maxLines = 1)
                    Text("@${repo.owner}", fontFamily = body, fontWeight = FontWeight.Bold, fontSize = 12.sp, color = c.OnBright.copy(alpha = 0.75f), maxLines = 1)
                }
                MaxIconButton(
                    if (isPinned) FeatherIcons.StarFilled else FeatherIcons.Star, onTogglePin,
                    fill = if (isPinned) Sunshine else Cream, size = 34.dp
                )
            }
            Box(Modifier.fillMaxWidth().height(3.dp).background(c.Ink))

            Column(Modifier.padding(18.dp).weight(1f, fill = false)) {
                Text(
                    repo.description.ifBlank { "No description provided." },
                    fontFamily = body, fontWeight = FontWeight.Medium, fontSize = 13.sp, lineHeight = 20.sp,
                    color = if (repo.description.isBlank()) c.TextMuted else c.TextSecondary,
                    maxLines = 2, modifier = Modifier.heightIn(min = 40.dp)
                )
                Spacer(Modifier.height(16.dp))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    CountChip("$open open", Lime)
                    CountChip("$draft draft", if (c.isDark) c.SurfaceVariant else Cream, textColor = c.TextPrimary)
                    Spacer(Modifier.weight(1f))
                    if (repo.version.isNotBlank()) {
                        Sticker("v${repo.version}", if (accent == Sunshine) Lavender else Sunshine, rotation = 4f, fontSize = 10.sp, icon = FeatherIcons.Tag)
                    }
                }
            }
        }
        if (isPinned) {
            Sticker(
                "pinned", HotPink, rotation = 12f, fontSize = 10.sp,
                modifier = Modifier.align(Alignment.TopEnd).offset(10.dp - lift, (-12).dp - lift)
            )
        }
    }
}

@Composable
private fun CountChip(label: String, fill: Color, textColor: Color = LocalAppColors.current.OnBright) {
    val c = LocalAppColors.current
    Box(
        Modifier.background(fill, RoundedCornerShape(50)).border(2.dp, c.Ink, RoundedCornerShape(50))
            .padding(horizontal = 10.dp, vertical = 4.dp)
    ) {
        Text(label.uppercase(), fontFamily = LocalBodyFont.current, fontWeight = FontWeight.Bold, fontSize = 11.sp, letterSpacing = 0.5.sp, color = textColor)
    }
}

// ── Sidebar pieces ─────────────────────────────────────────────────

@Composable
private fun NavRow(icon: ImageVector, label: String, active: Boolean, accent: Color, body: FontFamily, onClick: () -> Unit) {
    val c = LocalAppColors.current
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val lift = rememberLift(interaction, hoverLift = 2.dp)
    val bg by animateColorAsState(when { active -> accent; hovered -> c.SidebarHover; else -> Color.Transparent }, tween(150))
    val fg = if (active) InkBlack else if (hovered) accent else c.SidebarText
    val shape = RoundedCornerShape(12.dp)
    Row(
        modifier = Modifier.fillMaxWidth()
            .offset(-lift, -lift)
            .then(
                if (active) Modifier.block(bg, InkBlack, HotPink, shape, shadowOffset = 4.dp + lift, borderWidth = 3.dp)
                else Modifier.background(bg, shape)
            )
            .hoverable(interaction).clickable(interactionSource = interaction, indication = null) { onClick() }
            .padding(horizontal = 14.dp, vertical = 11.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = fg, modifier = Modifier.size(17.dp))
        Text(label.uppercase(), fontFamily = LocalDisplayFont.current, fontWeight = FontWeight.ExtraBold, fontSize = 13.sp, letterSpacing = 0.8.sp, color = fg)
    }
}

@Composable
private fun SidebarRepoItem(repo: Repository, active: Boolean, isPinned: Boolean, accent: Color, body: FontFamily, onClick: () -> Unit) {
    val c = LocalAppColors.current
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val lift = rememberLift(interaction, hoverLift = 2.dp)
    val bg by animateColorAsState(when { active -> accent; hovered -> c.SidebarHover; else -> Color.Transparent }, tween(150))
    val fg = if (active) InkBlack else c.SidebarText
    val muted = if (active) InkBlack.copy(alpha = 0.7f) else c.SidebarTextMuted
    val open = repo.openPRs.count { !it.draft }
    val shape = RoundedCornerShape(12.dp)

    Row(
        modifier = Modifier.fillMaxWidth()
            .offset(-lift, -lift)
            .then(
                if (active) Modifier.block(bg, InkBlack, Sunshine, shape, shadowOffset = 4.dp + lift, borderWidth = 3.dp)
                else Modifier.background(bg, shape)
            )
            .hoverable(interaction).clickable(interactionSource = interaction, indication = null) { onClick() }
            .padding(horizontal = 10.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Box(
            Modifier.size(12.dp).rotate(45f).background(accent).border(2.dp, if (active) InkBlack else Cream),
        )
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                if (isPinned) Icon(FeatherIcons.StarFilled, null, tint = if (active) InkBlack else Sunshine, modifier = Modifier.size(11.dp))
                Text(repo.repo, fontFamily = body, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = fg, maxLines = 1)
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                if (repo.version.isNotBlank()) {
                    Text("v${repo.version}", fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = 10.sp, color = muted)
                    Text("·", fontSize = 10.sp, color = muted)
                }
                Text("$open open", fontFamily = body, fontWeight = FontWeight.SemiBold, fontSize = 11.sp, color = muted)
            }
        }
        if (open > 0) BurstBadge(open, if (active) Cream else accent, size = 30.dp, rotation = 10f)
    }
}

// ── Body states ────────────────────────────────────────────────────

@Composable
private fun EmptyBody(c: AppColorScheme, body: FontFamily, display: FontFamily, onManageRepos: () -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(18.dp)) {
            Box(contentAlignment = Alignment.Center) {
                Box(Modifier.size(150.dp).rotate(10f).block(Sunshine, c.Ink, c.Shadow, BurstShape(14, 0.24f), 6.dp, 4.dp))
                Box(
                    Modifier.size(70.dp).rotate(-8f).block(HotPink, InkBlack, InkBlack, RoundedCornerShape(18.dp), 4.dp),
                    contentAlignment = Alignment.Center
                ) { Icon(FeatherIcons.GitBranch, null, tint = InkBlack, modifier = Modifier.size(32.dp)) }
            }
            Text("NOTHING ON THE WALL", fontFamily = display, fontWeight = FontWeight.ExtraBold, fontSize = 30.sp, letterSpacing = 0.5.sp, color = c.TextPrimary)
            Text("Add repositories to start tracking their pull requests.", fontFamily = body, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = c.TextSecondary)
            Spacer(Modifier.height(6.dp))
            MaxButton("Add repositories", onManageRepos, fill = Aqua, icon = FeatherIcons.Sliders)
        }
    }
}
