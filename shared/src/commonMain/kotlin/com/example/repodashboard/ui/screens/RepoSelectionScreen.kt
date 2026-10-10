package com.example.repodashboard.ui.screens

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.repodashboard.github.GitHubClient
import com.example.repodashboard.models.AppConfig
import com.example.repodashboard.models.GitHubRepoItem
import com.example.repodashboard.models.RepositoryConfig
import com.example.repodashboard.ui.theme.*

@Composable
fun RepoSelectionScreen(
    client: GitHubClient,
    existingConfig: AppConfig,
    onSave: (AppConfig) -> Unit,
    onBack: (() -> Unit)? = null
) {
    val c       = LocalAppColors.current
    val body    = LocalBodyFont.current
    val display = LocalDisplayFont.current

    var isLoading      by remember { mutableStateOf(true) }
    var error          by remember { mutableStateOf<String?>(null) }
    var availableRepos by remember { mutableStateOf<List<GitHubRepoItem>>(emptyList()) }
    var searchQuery    by remember { mutableStateOf("") }
    var selected       by remember { mutableStateOf<Set<String>>(emptySet()) }

    LaunchedEffect(Unit) {
        existingConfig.repositories.forEach { selected = selected + "${it.owner}/${it.repo}" }
        isLoading = true
        val result = client.getUserRepos()
        isLoading = false
        if (result.isSuccess) availableRepos = result.getOrNull()!!
        else error = result.exceptionOrNull()?.message ?: "Failed to load repositories"
    }

    val filtered = remember(availableRepos, searchQuery) {
        if (searchQuery.isBlank()) availableRepos
        else availableRepos.filter {
            it.full_name.contains(searchQuery, true) || it.description?.contains(searchQuery, true) == true
        }
    }

    Column(Modifier.fillMaxSize().background(c.Background).dots(c.Pattern, spacing = 22.dp, radius = 1.8.dp)) {
        Column(
            Modifier.fillMaxWidth().background(Grape)
                .dots(Color.White.copy(alpha = 0.16f), spacing = 18.dp, radius = 2.dp)
                .padding(start = 40.dp, end = 40.dp, top = 24.dp, bottom = 28.dp)
        ) {
            if (onBack != null) {
                MaxButton("← Back", onBack, fill = Cream, compact = true)
                Spacer(Modifier.height(18.dp))
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        Text("PICK YOUR REPOS", fontFamily = display, fontWeight = FontWeight.ExtraBold, fontSize = 44.sp, lineHeight = 48.sp, letterSpacing = (-0.5).sp, color = Cream)
                        BurstBadge(selected.size, Sunshine, size = 52.dp, rotation = 10f)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Sticker("${selected.size} selected", Lime, rotation = -3f, fontSize = 11.sp)
                        Sticker("${availableRepos.size} available", Aqua, rotation = 2f, fontSize = 11.sp)
                    }
                }
                MaxButton(
                    text = "Save & continue (${selected.size})",
                    onClick = {
                        val configs = availableRepos.filter { it.full_name in selected }.map {
                            RepositoryConfig(name = it.full_name, owner = it.owner.login, repo = it.name, description = it.description ?: "", defaultBranch = it.default_branch)
                        }
                        onSave(AppConfig(configs))
                    },
                    enabled = selected.isNotEmpty(),
                    fill = HotPink
                )
            }
            Spacer(Modifier.height(22.dp))
            MaxTextField(
                value = searchQuery, onValueChange = { searchQuery = it },
                placeholder = "Search repositories…",
                leading = { Icon(FeatherIcons.Search, null, tint = c.Primary, modifier = Modifier.size(18.dp)) }
            )
        }
        Box(Modifier.fillMaxWidth().height(4.dp).background(c.Ink))

        when {
            isLoading -> MaxLoader("Loading your repositories…")
            error != null -> Box(Modifier.fillMaxSize().padding(40.dp), contentAlignment = Alignment.TopStart) {
                MaxNotice(FeatherIcons.AlertTriangle, error!!, c.Error)
            }
            filtered.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    if (searchQuery.isBlank()) "NO REPOSITORIES FOUND." else "NO REPOS MATCH \"${searchQuery.uppercase()}\".",
                    fontFamily = display, fontWeight = FontWeight.ExtraBold, fontSize = 22.sp, color = c.TextSecondary
                )
            }
            else -> LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(14.dp),
                contentPadding = PaddingValues(start = 40.dp, end = 48.dp, top = 24.dp, bottom = 40.dp)
            ) {
                itemsIndexed(filtered, key = { _, r -> r.full_name }) { i, repo ->
                    val isSel = repo.full_name in selected
                    RepoRow(repo, isSel, c.accent(i), c, body) {
                        selected = if (isSel) selected - repo.full_name else selected + repo.full_name
                    }
                }
            }
        }
    }
}

@Composable
private fun RepoRow(repo: GitHubRepoItem, isSelected: Boolean, accent: Color, c: AppColorScheme, body: FontFamily, onToggle: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val lift = rememberLift(interaction, hoverLift = 3.dp)
    val checkRotation by animateFloatAsState(if (isSelected) -10f else 0f, spring(dampingRatio = 0.4f, stiffness = 500f))
    val bg = when { isSelected -> accent; hovered -> c.SurfaceHover; else -> c.Surface }
    val fg = if (isSelected) InkBlack else c.TextPrimary
    val sub = if (isSelected) InkBlack.copy(alpha = 0.75f) else c.TextSecondary

    Row(
        modifier = Modifier.fillMaxWidth()
            .offset(-lift, -lift)
            .block(bg, c.Ink, c.Shadow, RoundedCornerShape(16.dp), shadowOffset = (if (isSelected) 6.dp else 4.dp) + lift, borderWidth = 3.dp)
            .then(if (isSelected) Modifier.stripes(Color.White.copy(alpha = 0.2f), gap = 22.dp, width = 8.dp) else Modifier)
            .hoverable(interaction).clickable(interactionSource = interaction, indication = null) { onToggle() }
            .padding(horizontal = 18.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Box(
            modifier = Modifier.size(28.dp).rotate(checkRotation)
                .block(if (isSelected) Cream else c.SurfaceVariant, c.Ink, c.Shadow, RoundedCornerShape(8.dp), shadowOffset = 2.dp, borderWidth = 3.dp),
            contentAlignment = Alignment.Center
        ) { if (isSelected) Text("✓", fontSize = 16.sp, color = InkBlack, fontWeight = FontWeight.ExtraBold) }

        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(repo.full_name, fontFamily = LocalDisplayFont.current, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp, color = fg)
                if (repo.isPrivate) Sticker("private", if (isSelected) Cream else Lavender, rotation = -4f, fontSize = 9.sp, icon = FeatherIcons.Lock)
            }
            if (!repo.description.isNullOrBlank()) {
                Text(repo.description, fontFamily = body, fontWeight = FontWeight.SemiBold, fontSize = 12.sp, color = sub, maxLines = 1)
            }
        }
        if (repo.updated_at != null) {
            Box(
                Modifier.background(if (isSelected) Cream else c.SurfaceVariant, RoundedCornerShape(50))
                    .border(2.dp, if (isSelected) InkBlack else c.Ink, RoundedCornerShape(50))
                    .padding(horizontal = 9.dp, vertical = 3.dp)
            ) { Text(repo.updated_at.take(10), fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = 11.sp, color = if (isSelected) InkBlack else c.TextPrimary) }
        }
    }
}
