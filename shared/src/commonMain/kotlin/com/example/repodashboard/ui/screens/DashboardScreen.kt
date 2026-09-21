package com.example.repodashboard.ui.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
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
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
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
import com.example.repodashboard.models.AppState
import com.example.repodashboard.models.Repository
import com.example.repodashboard.ui.theme.AppColorScheme
import com.example.repodashboard.ui.theme.FeatherIcons
import com.example.repodashboard.ui.theme.LocalAppColors
import com.example.repodashboard.ui.theme.LocalBodyFont
import com.example.repodashboard.ui.theme.LocalDisplayFont

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

    Row(modifier = Modifier.fillMaxSize().background(c.Background)) {

        // ── Sidebar ─────────────────────────────────────────────────
        Row(modifier = Modifier.width(252.dp).fillMaxHeight()) {
            Column(modifier = Modifier.weight(1f).fillMaxHeight().background(c.Sidebar)) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 20.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(11.dp)
                ) {
                    Box(Modifier.size(34.dp).background(c.Primary, RoundedCornerShape(9.dp)), contentAlignment = Alignment.Center) {
                        Icon(FeatherIcons.GitBranch, null, tint = Color.White, modifier = Modifier.size(18.dp))
                    }
                    Text("PR Dashboard", fontFamily = display, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = c.TextPrimary)
                }

                Column(Modifier.padding(horizontal = 10.dp)) {
                    NavRow(FeatherIcons.Grid, "Dashboard", state.selectedRepoName == null, c, body, onDashboardSelected)
                }

                Spacer(Modifier.height(14.dp))
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("REPOSITORIES", fontFamily = body, fontWeight = FontWeight.SemiBold, fontSize = 10.sp, color = c.SidebarTextMuted)
                    IconHoverButton(FeatherIcons.RefreshCw, c, enabled = !state.isLoading, onClick = onRefresh)
                }
                HorizontalDivider(color = c.SidebarBorder, modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp))

                LazyColumn(
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                    verticalArrangement = Arrangement.spacedBy(1.dp)
                ) {
                    items(ordered) { repo ->
                        SidebarRepoItem(repo, state.selectedRepoName == repo.name, repo.name in pinned, c, body) { onRepoSelected(repo.name) }
                    }
                    if (state.repositories.isEmpty() && !state.isLoading) {
                        item {
                            Text("No repos yet", fontFamily = body, fontSize = 12.sp, color = c.SidebarTextMuted,
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp))
                        }
                    }
                }

                HorizontalDivider(color = c.SidebarBorder)
                Column(Modifier.padding(horizontal = 10.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                    NavRow(if (isDark) FeatherIcons.Sun else FeatherIcons.Moon, if (isDark) "Light mode" else "Dark mode", false, c, body, onToggleTheme)
                    NavRow(FeatherIcons.Sliders, "Manage repos", false, c, body, onManageRepos)
                    NavRow(FeatherIcons.LogOut, "Sign out", false, c, body, onClearToken)
                }
            }
            Box(Modifier.width(1.dp).fillMaxHeight().background(c.SidebarBorder))
        }

        // ── Body ─────────────────────────────────────────────────────
        Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
            when {
                state.isLoading -> LoadingBody(c, body)
                state.error != null -> ErrorBody(state.error, c, body)
                selectedRepo != null -> RepoDetailScreen(selectedRepo, client, onBack = onDashboardSelected)
                state.repositories.isEmpty() -> EmptyBody(c, body, display, onManageRepos)
                else -> DashboardGrid(state, ordered, pinned, c, body, display, onRepoSelected, onTogglePin)
            }
        }
    }
}

// ── Dashboard grid ─────────────────────────────────────────────────

@Composable
private fun DashboardGrid(
    state: AppState, ordered: List<Repository>, pinned: Set<String>,
    c: AppColorScheme, body: FontFamily, display: FontFamily,
    onRepoSelected: (String) -> Unit, onTogglePin: (String) -> Unit
) {
    val totalOpen  = state.repositories.sumOf { r -> r.openPRs.count { !it.draft } }
    val totalDraft = state.repositories.sumOf { r -> r.openPRs.count { it.draft } }

    Column(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 40.dp, vertical = 28.dp)) {
            Text("Dashboard", fontFamily = display, fontWeight = FontWeight.Bold, fontSize = 26.sp, color = c.TextPrimary)
            Spacer(Modifier.height(4.dp))
            Text("${state.repositories.size} repositories · $totalOpen open pull requests",
                fontFamily = body, fontSize = 13.sp, color = c.TextSecondary)
            Spacer(Modifier.height(20.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                MetricCard(FeatherIcons.GitBranch, "${state.repositories.size}", "Repositories", c.Info, c, body, display)
                MetricCard(FeatherIcons.GitPullRequest, "$totalOpen", "Open PRs", c.Primary, c, body, display)
                MetricCard(FeatherIcons.Edit, "$totalDraft", "Drafts", c.TextSecondary, c, body, display)
            }
        }
        LazyVerticalGrid(
            columns = GridCells.Adaptive(300.dp),
            modifier = Modifier.fillMaxSize().padding(horizontal = 40.dp),
            contentPadding = PaddingValues(bottom = 40.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            items(ordered, key = { it.name }) { repo ->
                RepoCard(repo, repo.name in pinned, c, body, display, onClick = { onRepoSelected(repo.name) }, onTogglePin = { onTogglePin(repo.name) })
            }
        }
    }
}

@Composable
private fun MetricCard(icon: ImageVector, value: String, label: String, accent: Color, c: AppColorScheme, body: FontFamily, display: FontFamily) {
    Row(
        modifier = Modifier.background(c.Surface, RoundedCornerShape(12.dp)).border(1.dp, c.Border, RoundedCornerShape(12.dp)).padding(horizontal = 18.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(Modifier.size(34.dp).background(accent.copy(alpha = 0.12f), RoundedCornerShape(9.dp)), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = accent, modifier = Modifier.size(17.dp))
        }
        Text(value, fontFamily = display, fontWeight = FontWeight.Bold, fontSize = 22.sp, color = c.TextPrimary)
        Text(label, fontFamily = body, fontSize = 13.sp, color = c.TextSecondary)
    }
}

@Composable
private fun RepoCard(
    repo: Repository, isPinned: Boolean, c: AppColorScheme, body: FontFamily, display: FontFamily,
    onClick: () -> Unit, onTogglePin: () -> Unit
) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val elevation by animateDpAsState(if (hovered) 8.dp else 0.dp, tween(200))
    val borderColor by animateColorAsState(if (hovered) c.PrimaryMid else c.Border, tween(200))
    val open  = repo.openPRs.count { !it.draft }
    val draft = repo.openPRs.count { it.draft }

    Column(
        modifier = Modifier.fillMaxWidth().heightIn(min = 172.dp)
            .shadow(elevation, RoundedCornerShape(16.dp), clip = false)
            .clip(RoundedCornerShape(16.dp)).background(c.Surface)
            .border(1.dp, borderColor, RoundedCornerShape(16.dp))
            .hoverable(interaction).clickable(interaction, indication = null) { onClick() }
            .padding(20.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(Modifier.size(40.dp).background(c.PrimaryLight, RoundedCornerShape(11.dp)), contentAlignment = Alignment.Center) {
                Text(repo.repo.take(1).uppercase(), fontFamily = display, fontWeight = FontWeight.Bold, fontSize = 18.sp, color = c.Primary)
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(repo.repo, fontFamily = display, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = c.TextPrimary, maxLines = 1)
                Text(repo.owner, fontFamily = body, fontSize = 12.sp, color = c.TextMuted, maxLines = 1)
            }
            PinButton(isPinned, c, onTogglePin)
        }

        Spacer(Modifier.height(14.dp))
        Text(
            repo.description.ifBlank { "No description provided." },
            fontFamily = body, fontSize = 13.sp, lineHeight = 19.sp,
            color = if (repo.description.isBlank()) c.TextMuted else c.TextSecondary,
            maxLines = 2, modifier = Modifier.weight(1f)
        )

        Spacer(Modifier.height(14.dp))
        HorizontalDivider(color = c.Divider)
        Spacer(Modifier.height(12.dp))

        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            CountStat(c.Success, "$open open", c, body)
            CountStat(c.TextMuted, "$draft draft", c, body)
            Spacer(Modifier.weight(1f))
            if (repo.version.isNotBlank()) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Icon(FeatherIcons.Tag, null, tint = c.TextMuted, modifier = Modifier.size(12.dp))
                    Text("v${repo.version}", fontFamily = FontFamily.Monospace, fontSize = 11.sp, color = c.TextMuted)
                }
            }
        }
    }
}

@Composable
private fun PinButton(isPinned: Boolean, c: AppColorScheme, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val bg by animateColorAsState(if (hovered) c.SurfaceVariant else Color.Transparent, tween(150))
    Box(
        modifier = Modifier.size(30.dp).clip(RoundedCornerShape(8.dp)).background(bg)
            .hoverable(interaction).clickable(interaction, indication = null) { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            if (isPinned) FeatherIcons.StarFilled else FeatherIcons.Star, null,
            tint = if (isPinned) c.Primary else c.TextMuted, modifier = Modifier.size(16.dp)
        )
    }
}

@Composable
private fun CountStat(dot: Color, label: String, c: AppColorScheme, body: FontFamily) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(Modifier.size(7.dp).background(dot, CircleShape))
        Text(label, fontFamily = body, fontSize = 12.sp, color = c.TextSecondary)
    }
}

// ── Sidebar pieces ─────────────────────────────────────────────────

@Composable
private fun NavRow(icon: ImageVector, label: String, active: Boolean, c: AppColorScheme, body: FontFamily, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val bg by animateColorAsState(when { active -> c.SidebarActive; hovered -> c.SidebarHover; else -> Color.Transparent }, tween(150))
    val fg by animateColorAsState(when { active -> c.Primary; hovered -> c.TextPrimary; else -> c.SidebarText }, tween(150))
    Row(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(9.dp)).background(bg)
            .hoverable(interaction).clickable(interaction, indication = null) { onClick() }
            .padding(horizontal = 12.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(11.dp), verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = fg, modifier = Modifier.size(16.dp))
        Text(label, fontFamily = body, fontWeight = if (active) FontWeight.SemiBold else FontWeight.Medium, fontSize = 13.sp, color = fg)
    }
}

@Composable
private fun SidebarRepoItem(repo: Repository, active: Boolean, isPinned: Boolean, c: AppColorScheme, body: FontFamily, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val bg by animateColorAsState(when { active -> c.SidebarActive; hovered -> c.SidebarHover; else -> Color.Transparent }, tween(150))
    val fg by animateColorAsState(when { active -> c.Primary; hovered -> c.TextPrimary; else -> c.SidebarText }, tween(150))
    val open = repo.openPRs.count { !it.draft }

    Row(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(9.dp)).background(bg)
            .hoverable(interaction).clickable(interaction, indication = null) { onClick() }
            .padding(horizontal = 12.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(9.dp)
    ) {
        if (isPinned) Icon(FeatherIcons.StarFilled, null, tint = c.Primary, modifier = Modifier.size(11.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(repo.repo, fontFamily = body, fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal, fontSize = 13.sp, color = fg, maxLines = 1)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                if (repo.version.isNotBlank()) {
                    Text("v${repo.version}", fontFamily = FontFamily.Monospace, fontSize = 10.sp, color = c.SidebarTextMuted)
                    Text("·", fontSize = 10.sp, color = c.SidebarTextMuted)
                }
                Text("$open open", fontFamily = body, fontSize = 11.sp, color = c.SidebarTextMuted)
            }
        }
        if (open > 0) {
            Box(
                modifier = Modifier.defaultMinSize(minWidth = 20.dp, minHeight = 20.dp)
                    .background(if (active) c.Primary.copy(alpha = 0.16f) else c.SidebarHover, CircleShape).padding(horizontal = 6.dp),
                contentAlignment = Alignment.Center
            ) {
                Text("$open", fontFamily = body, fontWeight = FontWeight.SemiBold, fontSize = 11.sp, color = if (active) c.Primary else c.SidebarTextMuted)
            }
        }
    }
}

@Composable
private fun IconHoverButton(icon: ImageVector, c: AppColorScheme, enabled: Boolean, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val bg by animateColorAsState(if (hovered) c.SidebarHover else Color.Transparent, tween(150))
    Box(
        modifier = Modifier.size(26.dp).clip(CircleShape).background(bg)
            .hoverable(interaction).clickable(interaction, indication = null, enabled = enabled) { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, null, tint = if (enabled) c.SidebarText else c.SidebarTextMuted, modifier = Modifier.size(14.dp))
    }
}

// ── Body states ────────────────────────────────────────────────────

@Composable
private fun LoadingBody(c: AppColorScheme, body: FontFamily) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)) {
            CircularProgressIndicator(color = c.Primary, strokeWidth = 2.5.dp, modifier = Modifier.size(32.dp))
            Text("Fetching from GitHub…", fontFamily = body, fontSize = 13.sp, color = c.TextSecondary)
        }
    }
}

@Composable
private fun ErrorBody(error: String, c: AppColorScheme, body: FontFamily) {
    Box(Modifier.fillMaxSize().padding(40.dp), contentAlignment = Alignment.TopStart) {
        Row(
            modifier = Modifier.fillMaxWidth().shadow(2.dp, RoundedCornerShape(12.dp), clip = false).clip(RoundedCornerShape(12.dp)).background(c.ErrorLight).padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(FeatherIcons.AlertTriangle, null, tint = c.Error, modifier = Modifier.size(16.dp))
            Text(error, fontFamily = body, fontSize = 13.sp, color = c.Error)
        }
    }
}

@Composable
private fun EmptyBody(c: AppColorScheme, body: FontFamily, display: FontFamily, onManageRepos: () -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Box(Modifier.size(68.dp).background(c.PrimaryLight, RoundedCornerShape(18.dp)), contentAlignment = Alignment.Center) {
                Icon(FeatherIcons.GitBranch, null, tint = c.Primary, modifier = Modifier.size(30.dp))
            }
            Text("No repositories yet", fontFamily = display, fontWeight = FontWeight.SemiBold, fontSize = 18.sp, color = c.TextPrimary)
            Text("Add repositories to start tracking their pull requests.", fontFamily = body, fontSize = 13.sp, color = c.TextSecondary)
            Spacer(Modifier.height(4.dp))
            Button(onClick = onManageRepos, shape = RoundedCornerShape(10.dp), colors = ButtonDefaults.buttonColors(containerColor = c.Primary)) {
                Text("Add repositories", fontFamily = body, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = Color.White)
            }
        }
    }
}
