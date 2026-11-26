package com.example.repodashboard.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.repodashboard.github.GitHubClient
import com.example.repodashboard.models.AppConfig
import com.example.repodashboard.models.GitHubRepoItem
import com.example.repodashboard.models.RepositoryConfig
import com.example.repodashboard.ui.inbox.*
import com.example.repodashboard.ui.theme.*

/** "Choose repositories": pick which repos the inbox follows. */
@Composable
fun RepoSelectionScreen(
    client: GitHubClient,
    existingConfig: AppConfig,
    onSave: (AppConfig) -> Unit,
    onBack: (() -> Unit)? = null,
) {
    val t = LocalTokens.current
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var repos by remember { mutableStateOf<List<GitHubRepoItem>>(emptyList()) }
    var query by remember { mutableStateOf("") }
    var selected by remember { mutableStateOf(existingConfig.repositories.map { "${it.owner}/${it.repo}" }.toSet()) }

    LaunchedEffect(Unit) {
        val result = client.getUserRepos()
        loading = false
        result.onSuccess { repos = it }.onFailure { error = it.message ?: "Couldn't load your repositories." }
    }

    val filtered = remember(repos, query) {
        val q = query.trim()
        if (q.isEmpty()) repos else repos.filter { it.full_name.contains(q, true) || it.description?.contains(q, true) == true }
    }

    fun save() {
        val found = repos.filter { it.full_name in selected }.map {
            RepositoryConfig(name = it.full_name, owner = it.owner.login, repo = it.name, description = it.description ?: "", defaultBranch = it.default_branch)
        }
        // Keep previously chosen repos that weren't listed this time (e.g. still loading orgs).
        val kept = existingConfig.repositories.filter { "${it.owner}/${it.repo}" in selected && found.none { f -> f.owner == it.owner && f.repo == it.repo } }
        onSave(AppConfig(found + kept))
    }

    Box(Modifier.fillMaxSize().background(t.bg).padding(8.dp), contentAlignment = Alignment.TopCenter) {
        Column(Modifier.widthIn(max = 880.dp).fillMaxWidth().fillMaxHeight().softCard(RoundedCornerShape(14.dp))) {
            Column(Modifier.fillMaxWidth().padding(start = 28.dp, end = 28.dp, top = 24.dp, bottom = 18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                if (onBack != null) {
                    Row(
                        Modifier.clip(RoundedCornerShape(8.dp)).click(onClick = onBack).padding(vertical = 4.dp, horizontal = 2.dp),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Ico(FeatherIcons.ArrowLeft, 16.dp, t.ink2)
                        Txt("Back to inbox", weight = FontWeight.Medium, color = t.ink2)
                    }
                }
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Txt("Choose repositories", size = 24.sp, weight = FontWeight.Bold, letterSpacing = (-0.4).sp)
                        Txt(if (loading) "Loading…" else "${selected.size} selected · ${repos.size} available", color = t.muted)
                    }
                    if (!loading) {
                        if (selected.isNotEmpty()) PrimaryButton("Save and continue (${selected.size})", ::save, height = 44.dp)
                        else DisabledButton("Pick at least one", height = 44.dp)
                    }
                }
                Field(query, { query = it }, "Search repositories", Modifier.fillMaxWidth(), leading = FeatherIcons.Search, fontSize = 15.sp)
            }
            Box(Modifier.fillMaxWidth().height(1.dp).background(t.line2))

            LazyColumn(Modifier.fillMaxWidth().weight(1f), contentPadding = PaddingValues(8.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                when {
                    loading -> {
                        items(listOf(0.35f, 0.45f, 0.30f, 0.40f, 0.28f, 0.38f)) { w ->
                            Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                                Box(Modifier.size(18.dp).clip(RoundedCornerShape(4.dp)).background(t.line2))
                                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    SkelBar(w, 12.dp); SkelBar(0.6f, 10.dp, radius = 5.dp)
                                }
                            }
                        }
                        item { Txt("Loading your repositories from GitHub…", Modifier.padding(start = 16.dp, top = 8.dp, bottom = 16.dp), size = 13.sp, color = t.muted) }
                    }
                    error != null -> item {
                        EmptyState(FeatherIcons.WifiOff, t.failSoft, t.fail, "Couldn't load your repositories", error ?: "")
                    }
                    filtered.isEmpty() -> item {
                        Box(Modifier.fillMaxWidth().padding(vertical = 40.dp), contentAlignment = Alignment.Center) {
                            Txt(if (query.isBlank()) "No repositories found for this token." else "No repositories match \"${query.trim()}\".", color = t.muted)
                        }
                    }
                    else -> items(filtered, key = { it.full_name }) { r ->
                        val on = r.full_name in selected
                        Row(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(if (on) t.sel else t.pane)
                                .click(role = Role.Checkbox) { selected = if (on) selected - r.full_name else selected + r.full_name }
                                .padding(horizontal = 16.dp, vertical = 14.dp),
                            horizontalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            Box(Modifier.padding(top = 3.dp)) { CheckBox(on) }
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Txt(r.full_name, weight = FontWeight.SemiBold, color = if (on) t.accentText else t.ink)
                                Txt(r.description?.takeIf { it.isNotBlank() } ?: "No description", size = 13.sp, color = t.muted, maxLines = 2)
                            }
                            Txt(r.updated_at?.take(10) ?: "", size = 12.sp, color = t.muted, mono = true, maxLines = 1)
                        }
                    }
                }
            }
        }
    }
}
