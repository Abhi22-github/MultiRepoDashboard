package com.example.repodashboard.ui.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.repodashboard.github.GitHubClient
import com.example.repodashboard.models.AppConfig
import com.example.repodashboard.models.GitHubRepoItem
import com.example.repodashboard.models.RepositoryConfig
import com.example.repodashboard.ui.theme.AppColorScheme
import com.example.repodashboard.ui.theme.FeatherIcons
import com.example.repodashboard.ui.theme.LocalAppColors
import com.example.repodashboard.ui.theme.LocalBodyFont
import com.example.repodashboard.ui.theme.LocalDisplayFont

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

    Column(Modifier.fillMaxSize().background(c.Background)) {
        Column(Modifier.fillMaxWidth().background(c.Surface).padding(horizontal = 40.dp, vertical = 24.dp)) {
            if (onBack != null) {
                Row(
                    modifier = Modifier.clip(RoundedCornerShape(6.dp)).clickable { onBack() }.padding(horizontal = 6.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    Text("←", fontSize = 13.sp, color = c.TextSecondary)
                    Text("Back", fontFamily = body, fontSize = 13.sp, color = c.TextSecondary)
                }
                Spacer(Modifier.height(14.dp))
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Select repositories", fontFamily = display, fontWeight = FontWeight.Bold, fontSize = 22.sp, color = c.TextPrimary)
                    Text("${selected.size} selected · ${availableRepos.size} available", fontFamily = body, fontSize = 13.sp, color = c.TextSecondary)
                }
                Button(
                    onClick = {
                        val configs = availableRepos.filter { it.full_name in selected }.map {
                            RepositoryConfig(name = it.full_name, owner = it.owner.login, repo = it.name, description = it.description ?: "", defaultBranch = it.default_branch)
                        }
                        onSave(AppConfig(configs))
                    },
                    enabled = selected.isNotEmpty(), shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = c.Primary), modifier = Modifier.height(42.dp)
                ) {
                    Text("Save & continue (${selected.size})", fontFamily = body, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = Color.White)
                }
            }
            Spacer(Modifier.height(16.dp))
            OutlinedTextField(
                value = searchQuery, onValueChange = { searchQuery = it },
                placeholder = { Text("Search repositories…", fontFamily = body, fontSize = 13.sp, color = c.TextMuted) },
                modifier = Modifier.fillMaxWidth(), singleLine = true, shape = RoundedCornerShape(10.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = c.Primary, unfocusedBorderColor = c.Border,
                    unfocusedContainerColor = c.Background, focusedContainerColor = c.Surface,
                    focusedTextColor = c.TextPrimary, unfocusedTextColor = c.TextPrimary
                ),
                leadingIcon = { Icon(FeatherIcons.Search, null, tint = c.TextMuted, modifier = Modifier.padding(start = 6.dp).size(16.dp)) },
                textStyle = androidx.compose.ui.text.TextStyle(fontFamily = body, fontSize = 13.sp, color = c.TextPrimary)
            )
        }
        HorizontalDivider(color = c.Border)

        when {
            isLoading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    CircularProgressIndicator(color = c.Primary, strokeWidth = 2.5.dp, modifier = Modifier.size(30.dp))
                    Text("Loading your repositories…", fontFamily = body, fontSize = 13.sp, color = c.TextSecondary)
                }
            }
            error != null -> Box(Modifier.fillMaxSize().padding(40.dp), contentAlignment = Alignment.TopStart) {
                Row(
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(c.ErrorLight).padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(FeatherIcons.AlertTriangle, null, tint = c.Error, modifier = Modifier.size(15.dp))
                    Text(error!!, fontFamily = body, fontSize = 13.sp, color = c.Error)
                }
            }
            filtered.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(if (searchQuery.isBlank()) "No repositories found." else "No repos match \"$searchQuery\".",
                    fontFamily = body, fontSize = 13.sp, color = c.TextSecondary)
            }
            else -> LazyColumn(
                modifier = Modifier.fillMaxSize().padding(horizontal = 40.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(vertical = 20.dp)
            ) {
                items(filtered, key = { it.full_name }) { repo ->
                    val isSel = repo.full_name in selected
                    RepoRow(repo, isSel, c, body) {
                        selected = if (isSel) selected - repo.full_name else selected + repo.full_name
                    }
                }
            }
        }
    }
}

@Composable
private fun RepoRow(repo: GitHubRepoItem, isSelected: Boolean, c: AppColorScheme, body: FontFamily, onToggle: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val bg by animateColorAsState(when { isSelected -> c.PrimaryLight; hovered -> c.SurfaceHover; else -> c.Surface }, tween(150))
    val borderColor by animateColorAsState(if (isSelected) c.PrimaryMid else c.Border, tween(150))

    Row(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(bg)
            .border(1.dp, borderColor, RoundedCornerShape(12.dp))
            .hoverable(interaction).clickable(interaction, indication = null) { onToggle() }.padding(16.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            modifier = Modifier.size(20.dp).clip(RoundedCornerShape(6.dp))
                .background(if (isSelected) c.Primary else c.SurfaceVariant)
                .then(if (!isSelected) Modifier.border(1.dp, c.Border, RoundedCornerShape(6.dp)) else Modifier),
            contentAlignment = Alignment.Center
        ) { if (isSelected) Text("✓", fontSize = 11.sp, color = Color.White, fontWeight = FontWeight.Bold) }

        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                Text(repo.full_name, fontFamily = body, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = if (isSelected) c.Primary else c.TextPrimary)
                if (repo.isPrivate) {
                    Box(Modifier.background(c.SurfaceVariant, RoundedCornerShape(4.dp)).border(1.dp, c.Border, RoundedCornerShape(4.dp)).padding(horizontal = 6.dp, vertical = 2.dp)) {
                        Text("private", fontFamily = body, fontWeight = FontWeight.Medium, fontSize = 10.sp, color = c.TextMuted)
                    }
                }
            }
            if (!repo.description.isNullOrBlank()) {
                Text(repo.description, fontFamily = body, fontSize = 12.sp, color = c.TextSecondary, maxLines = 1)
            }
        }
        if (repo.updated_at != null) Text(repo.updated_at.take(10), fontFamily = body, fontSize = 11.sp, color = c.TextMuted)
    }
}
