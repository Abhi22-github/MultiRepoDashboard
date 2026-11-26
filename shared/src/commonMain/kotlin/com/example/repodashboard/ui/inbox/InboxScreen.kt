package com.example.repodashboard.ui.inbox

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.repodashboard.data.*
import com.example.repodashboard.github.GitHubClient
import com.example.repodashboard.storage.LocalStorageManager
import com.example.repodashboard.ui.theme.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

enum class Layout { List, Board }

/** UI state of the inbox, plus caches for data loaded on demand. */
class InboxState(private val scope: CoroutineScope, private val client: () -> GitHubClient?, private val webBase: String) {
    var view by mutableStateOf(InboxView.Ship)
    var layout by mutableStateOf(Layout.List)
    /** Repo full name the inbox is scoped to; null = all repositories. */
    var repoScope by mutableStateOf<String?>(null)
    var selKey by mutableStateOf<String?>(null)
    var query by mutableStateOf("")
    var filters by mutableStateOf(Filters())
    var openFilter by mutableStateOf<String?>(null)
    var collapsed by mutableStateOf<Map<String, Boolean>>(emptyMap())
    var listW by mutableStateOf((LocalStorageManager.getListWidth() ?: 480).coerceIn(360, 760).toFloat())
    var repoQuery by mutableStateOf("")
    var quietOpen by mutableStateOf(false)

    val details = mutableStateMapOf<String, PrDetail>()
    private val requested = mutableSetOf<String>()
    val releasePrs = mutableStateMapOf<String, List<ShipItem>>()
    private val requestedReleases = mutableSetOf<String>()

    fun requestDetail(item: Item) {
        val pr = item.pr ?: return
        val key = prKey(item.repo, pr)
        if (!requested.add(key)) return
        val c = client() ?: return
        scope.launch {
            val d = loadPrDetail(c, item.repo.owner, item.repo.name, pr)
            if (d != null) details[key] = d else requested.remove(key)
        }
    }

    fun detailOf(item: Item): PrDetail? = item.pr?.let { details[prKey(item.repo, it)] }

    fun requestReleasePrs(item: Item) {
        val rel = item.release ?: return
        if (rel.prs != null) return
        if (!requestedReleases.add(item.key)) return
        val c = client() ?: return
        scope.launch { releasePrs[item.key] = releasePrs(c, item.repo.config, webBase, rel.previousTag, rel.tag) }
    }

    fun prsOf(item: Item): List<ShipItem>? = item.release?.prs ?: releasePrs[item.key]

    /** Drop caches after a refresh so checks and reviews are re-read. */
    fun clearCaches() {
        details.clear(); requested.clear(); releasePrs.clear(); requestedReleases.clear()
    }

    fun pickView(v: InboxView) { view = v; layout = Layout.List; selKey = null; openFilter = null }
    fun pickScope(name: String?) { repoScope = name; selKey = null }
}

@Composable
fun InboxScreen(
    data: InboxData?,
    loading: Boolean,
    noRepos: Boolean,
    client: GitHubClient?,
    apiBase: String,
    pinned: Set<String>,
    onTogglePin: (String) -> Unit,
    onRefresh: () -> Unit,
    onManageRepos: () -> Unit,
    onSignOut: () -> Unit,
    isDark: Boolean,
    onToggleTheme: () -> Unit,
) {
    val t = LocalTokens.current
    val coroutineScope = rememberCoroutineScope()
    val clientRef = rememberUpdatedState(client)
    val state = remember(apiBase) { InboxState(coroutineScope, { clientRef.value }, webBaseOf(apiBase)) }

    // Re-read checks/reviews after every sync.
    LaunchedEffect(data?.syncedAt) { if (data?.syncedAt != null) state.clearCaches() }

    // Tick so "synced N min ago" and ages stay current.
    var now by remember { mutableStateOf(nowMs()) }
    LaunchedEffect(Unit) { while (true) { delay(30_000); now = nowMs() } }

    val allRepos = data?.repos.orEmpty()
    val order = remember(allRepos, pinned) { allRepos.sortedByDescending { it.config.name in pinned } }
    if (state.repoScope != null && allRepos.none { it.config.name == state.repoScope }) state.repoScope = null
    val scoped = state.repoScope?.let { s -> order.filter { it.config.name == s } } ?: order

    val unreachable = data?.problem == SyncProblem.Unreachable && allRepos.isEmpty()
    val initialLoading = loading && data == null
    val ready = !initialLoading && !unreachable && !noRepos

    val counts = remember(scoped, now) { InboxView.entries.associateWith { buildItems(it, scoped, now).size } }

    val synced = when {
        loading -> "syncing…"
        unreachable -> "sync failed"
        data?.problem == SyncProblem.RateLimited -> minutesUntil(data.rateResetAt, now)?.let { "paused for $it min" } ?: "paused"
        data?.problem == SyncProblem.TokenExpired -> "not synced"
        else -> syncedLabel(data?.syncedAt, now)
    }

    val ctx = InboxContext(
        state = state, repos = order, scoped = scoped, pinned = pinned, now = now, ready = ready,
        initialLoading = initialLoading, unreachable = unreachable, noRepos = noRepos, data = data,
        onTogglePin = onTogglePin, onRefresh = onRefresh, onManageRepos = onManageRepos, onSignOut = onSignOut,
    )

    Column(Modifier.fillMaxSize().background(t.bg)) {
        TopBar(state, counts, ready, synced, isDark, onRefresh, onToggleTheme, onManageRepos, onSignOut)
        BoxWithConstraints(Modifier.fillMaxWidth().weight(1f)) {
            val wide = maxWidth >= 1100.dp
            Row(Modifier.fillMaxSize().padding(8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (wide) RepoPanel(ctx, Modifier.width(270.dp).fillMaxHeight())
                if (state.layout == Layout.Board) {
                    BoardView(ctx, Modifier.weight(1f).fillMaxHeight())
                } else {
                    val listMod = if (wide) Modifier.width(state.listW.dp) else Modifier.weight(1f)
                    ItemListPane(ctx, listMod.fillMaxHeight())
                    if (wide) Resizer(state) else Spacer(Modifier.width(0.dp))
                    DetailPane(ctx, Modifier.weight(if (wide) 1f else 1.2f).fillMaxHeight())
                }
            }
        }
    }
}

/** Everything the panes need, bundled to keep signatures short. */
class InboxContext(
    val state: InboxState,
    val repos: List<RepoData>,
    val scoped: List<RepoData>,
    val pinned: Set<String>,
    val now: Long,
    val ready: Boolean,
    val initialLoading: Boolean,
    val unreachable: Boolean,
    val noRepos: Boolean,
    val data: InboxData?,
    val onTogglePin: (String) -> Unit,
    val onRefresh: () -> Unit,
    val onManageRepos: () -> Unit,
    val onSignOut: () -> Unit,
)

// ── Top bar ──────────────────────────────────────────────────────

@Composable
private fun TopBar(
    state: InboxState, counts: Map<InboxView, Int>, ready: Boolean, synced: String, isDark: Boolean,
    onRefresh: () -> Unit, onToggleTheme: () -> Unit, onManageRepos: () -> Unit, onSignOut: () -> Unit,
) {
    val t = LocalTokens.current
    Column(Modifier.fillMaxWidth().background(t.pane)) {
        FlowRow(
            Modifier.fillMaxWidth().heightIn(min = 64.dp).padding(horizontal = 20.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterVertically),
            itemVerticalAlignment = Alignment.CenterVertically,
        ) {
            Row(Modifier.width(266.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                AppLogo(30.dp)
                Txt("PR Dashboard", size = 16.sp, weight = FontWeight.Bold, maxLines = 1)
            }
            if (state.layout == Layout.List) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    InboxView.entries.forEach { v ->
                        ViewPill(v, on = v == state.view, count = if (ready) counts[v]?.toString() ?: "0" else "–") { state.pickView(v) }
                    }
                }
            }
            Spacer(Modifier.weight(1f))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Row(Modifier.padding(start = 12.dp).clip(RoundedCornerShape(12.dp)).background(t.bg).padding(3.dp), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    LayoutToggle("List", FeatherIcons.ListIcon, state.layout == Layout.List) { state.layout = Layout.List }
                    LayoutToggle("Board", FeatherIcons.Columns, state.layout == Layout.Board) { state.layout = Layout.Board; state.openFilter = null }
                }
                Txt(synced, Modifier.padding(start = 18.dp, end = 10.dp), size = 12.sp, color = t.muted, maxLines = 1)
                IconBtn(FeatherIcons.RefreshCw, "Refresh from GitHub", onRefresh)
                IconBtn(if (isDark) FeatherIcons.Sun else FeatherIcons.Moon, if (isDark) "Switch to light mode" else "Switch to dark mode", onToggleTheme)
                IconBtn(FeatherIcons.Sliders, "Manage repositories", onManageRepos)
                IconBtn(FeatherIcons.LogOut, "Sign out", onSignOut)
            }
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(t.line))
    }
}

@Composable
private fun ViewPill(v: InboxView, on: Boolean, count: String, onClick: () -> Unit) {
    val t = LocalTokens.current
    val shape = RoundedCornerShape(18.dp)
    val icon = when (v) {
        InboxView.Ship -> FeatherIcons.Package; InboxView.Open -> FeatherIcons.GitPullRequest
        InboxView.Drafts -> FeatherIcons.FileDashed; InboxView.Releases -> FeatherIcons.Tag
    }
    Row(
        Modifier.height(36.dp).clip(shape).background(if (on) t.ink else Color.Transparent)
            .border(1.dp, if (on) t.ink else t.line, shape).click(role = Role.Tab, onClick = onClick).padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Ico(icon, 16.dp, if (on) t.pane else t.ink2)
        Txt(v.label, size = 13.sp, weight = if (on) FontWeight.Bold else FontWeight.Medium, color = if (on) t.pane else t.ink2, maxLines = 1)
        Chip(count, if (on) t.pane else t.line, if (on) t.ink else t.ink2, weight = FontWeight.Bold, radius = 8.dp)
    }
}

@Composable
private fun LayoutToggle(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, on: Boolean, onClick: () -> Unit) {
    val t = LocalTokens.current
    val shape = RoundedCornerShape(9.dp)
    Row(
        Modifier.height(32.dp).then(if (on) Modifier.softCard(shape) else Modifier.clip(shape)).click(onClick = onClick).padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Ico(icon, 15.dp, if (on) t.ink else t.muted)
        Txt(label, size = 13.sp, weight = if (on) FontWeight.Bold else FontWeight.Medium, color = if (on) t.ink else t.muted)
    }
}

// ── Left: repositories ───────────────────────────────────────────

@Composable
private fun RepoPanel(ctx: InboxContext, modifier: Modifier) {
    val t = LocalTokens.current
    val s = ctx.state
    val rq = s.repoQuery.trim().lowercase()
    val countIn = { r: RepoData? -> buildItems(s.view, r?.let { listOf(it) } ?: ctx.repos, ctx.now).size }
    val matching = ctx.repos.filter { rq.isEmpty() || it.name.lowercase().contains(rq) }
    val shown = if (rq.isNotEmpty() || s.quietOpen) matching
        else matching.filter { it.config.name in ctx.pinned || it.config.name == s.repoScope || countIn(it) > 0 }
    val hidden = matching.size - shown.size

    Column(modifier.padding(horizontal = 10.dp, vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Txt("Repositories", Modifier.padding(horizontal = 8.dp, vertical = 2.dp), size = 12.sp, weight = FontWeight.SemiBold, color = t.muted)
        Field(
            s.repoQuery, { s.repoQuery = it }, "Search ${ctx.repos.size} repositories", Modifier.fillMaxWidth(),
            leading = FeatherIcons.Search, height = 38.dp, bg = t.pane, border = t.line, fontSize = 13.sp
        )
        LazyColumn(Modifier.fillMaxWidth().weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp), contentPadding = PaddingValues(vertical = 2.dp)) {
            item { RepoRow(ctx, null) }
            items(shown, key = { it.config.name }) { RepoRow(ctx, it) }
            if (hidden > 0 || (s.quietOpen && rq.isEmpty())) item {
                TextLink(if (s.quietOpen) "Only repos with activity" else "+ $hidden repos with nothing here", { s.quietOpen = !s.quietOpen }, size = 12.sp)
            }
            if (ctx.ready && ctx.repos.isEmpty()) item { Txt("No repositories yet.", Modifier.padding(10.dp), size = 13.sp, color = t.muted) }
        }
    }
}

@Composable
private fun RepoRow(ctx: InboxContext, repo: RepoData?) {
    val t = LocalTokens.current
    val s = ctx.state
    val on = s.repoScope == repo?.config?.name
    val group = repo?.let { listOf(it) } ?: ctx.repos
    val open = group.sumOf { it.open.size }
    val unrel = group.sumOf { it.unreleased.size }
    val drafts = group.sumOf { it.drafts.size }
    val hasFail = repo != null && repo.open.any { s.details[prKey(repo, it)]?.checks == Checks.Failing }
    val shape = RoundedCornerShape(10.dp)
    Row(
        Modifier.fillMaxWidth().then(if (on) Modifier.softCard(shape) else Modifier.clip(shape))
            .click { s.pickScope(repo?.config?.name) }
            .padding(start = 10.dp, end = 6.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Txt(repo?.name ?: "All repositories", size = 13.sp, weight = if (on) FontWeight.Bold else FontWeight.Medium, color = if (on) t.ink else t.ink2, maxLines = 1)
                if (hasFail) Box(Modifier.semantics { contentDescription = "Has failing checks" }) { Dot(t.fail, size = 6.dp) }
            }
            if (ctx.ready) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                        Ico(FeatherIcons.Tag, 10.dp, t.muted)
                        Txt(repo?.let { it.latestTag ?: "no release" } ?: "${ctx.repos.size} repos", size = 11.sp, color = t.muted, mono = repo != null, maxLines = 1)
                    }
                    if (open > 0) Chip("$open open", t.bg, t.ink2, weight = FontWeight.Normal, radius = 4.dp)
                    if (unrel > 0) Chip("$unrel unreleased", t.warnSoft, t.warn, weight = FontWeight.Normal, radius = 4.dp)
                    if (drafts > 0) Chip("$drafts draft", t.bg, t.muted, weight = FontWeight.Normal, radius = 4.dp)
                }
            } else {
                Txt(if (ctx.initialLoading) "loading…" else "–", size = 11.sp, color = t.muted)
            }
        }
        if (repo != null) {
            val starred = repo.config.name in ctx.pinned
            Box(
                Modifier.size(30.dp).clip(RoundedCornerShape(6.dp)).click(role = Role.Switch) { ctx.onTogglePin(repo.config.name) }
                    .semantics { contentDescription = (if (starred) "Unstar " else "Star ") + repo.name },
                contentAlignment = Alignment.Center
            ) { Ico(if (starred) FeatherIcons.StarFilled else FeatherIcons.Star, 15.dp, if (starred) StarGold else t.muted) }
        }
    }
}

// ── Resizer ──────────────────────────────────────────────────────

@Composable
private fun Resizer(state: InboxState) {
    val t = LocalTokens.current
    val density = LocalDensity.current
    var dragging by remember { mutableStateOf(false) }
    Box(
        Modifier.width(8.dp).fillMaxHeight().pointerHoverIcon(PointerIcon.Hand)
            .semantics { contentDescription = "Resize the list panel" }
            .pointerInput(Unit) {
                detectHorizontalDragGestures(
                    onDragStart = { dragging = true },
                    onDragEnd = { dragging = false; LocalStorageManager.saveListWidth(state.listW.toInt()) },
                    onDragCancel = { dragging = false },
                ) { _, dx -> state.listW = (state.listW + with(density) { dx.toDp().value }).coerceIn(360f, 760f) }
            },
        contentAlignment = Alignment.Center
    ) { Box(Modifier.width(4.dp).height(44.dp).clip(RoundedCornerShape(2.dp)).background(if (dragging) t.accent else t.line)) }
}
