package com.example.repodashboard.ui.inbox

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.repodashboard.data.ApiMode
import com.example.repodashboard.data.RepoData
import com.example.repodashboard.data.minutesUntil
import com.example.repodashboard.models.RateBucket
import com.example.repodashboard.models.RateLimitResponse
import com.example.repodashboard.ui.theme.*

private data class ModeInfo(val mode: ApiMode, val label: String, val desc: String, val est: String, val rec: Boolean = false)

/** Rough cost of one refresh in each mode, from the current data. */
private fun estimates(repos: List<RepoData>): Map<ApiMode, String> {
    val n = repos.size
    val ready = repos.sumOf { it.open.size }
    val all = ready + repos.sumOf { it.drafts.size }
    val restLists = n * 3 + (all / 100)
    val gqlPoints = n + (all / 100) + (ready + 24) / 25
    return mapOf(
        ApiMode.Rest to "≈ ${restLists + ready * 4} calls",
        ApiMode.GraphQL to "≈ $gqlPoints points",
        ApiMode.Hybrid to "≈ $restLists calls + ${(ready + 24) / 25} pts",
    )
}

/** Top-bar button + panel to pick how the app fetches from GitHub, with the live rate limits. */
@Composable
fun DevToolsButton(mode: ApiMode, onMode: (ApiMode) -> Unit, rate: RateLimitResponse?, onLoadRate: () -> Unit, repos: List<RepoData>) {
    val t = LocalTokens.current
    var open by remember { mutableStateOf(false) }
    LaunchedEffect(open) { if (open) onLoadRate() }
    val est = estimates(repos)
    val modes = listOf(
        ModeInfo(ApiMode.Rest, "REST", "Lists, then size, reviews and checks per PR: 4 calls each. Uses the large REST budget, but many calls.", est.getValue(ApiMode.Rest)),
        ModeInfo(ApiMode.GraphQL, "GraphQL", "One query per repo, checks and reviews batched. Fewest calls, but spends GraphQL points.", est.getValue(ApiMode.GraphQL)),
        ModeInfo(ApiMode.Hybrid, "Hybrid", "REST for lists (cached with ETags, so unchanged repos are free); one batched GraphQL query for checks and reviews.", est.getValue(ApiMode.Hybrid), rec = true),
    )
    Box {
        Box(
            Modifier.size(36.dp).clip(RoundedCornerShape(8.dp)).background(if (open) t.bg else Color.Transparent)
                .click { open = !open }.semantics { contentDescription = "Developer tools: data source" },
            contentAlignment = Alignment.Center
        ) { Ico(FeatherIcons.Code, 16.dp, if (open) t.ink else t.muted) }
        Dropdown(open, { open = false }, alignEnd = true, offsetY = 44.dp) {
            Column(Modifier.width(360.dp).padding(10.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Txt("Developer tools", size = 15.sp, weight = FontWeight.Bold)
                    Chip("read-only", t.bg, t.muted, radius = 6.dp)
                    Spacer(Modifier.weight(1f))
                    IconBtn(FeatherIcons.X, "Close developer tools", { open = false }, size = 28.dp)
                }
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Txt("How to fetch from GitHub", size = 12.sp, weight = FontWeight.SemiBold, color = t.muted)
                    modes.forEach { m -> ModeRow(m, m.mode == mode) { if (m.mode != mode) onMode(m.mode) } }
                }
                Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(Modifier.fillMaxWidth().height(1.dp).background(t.line2))
                    RateRow("REST", rate?.resources?.core)
                    RateRow("GraphQL", rate?.resources?.graphql)
                    Txt(hint(mode, rate, est), size = 12.sp, color = t.muted)
                }
            }
        }
    }
}

private fun hint(mode: ApiMode, rate: RateLimitResponse?, est: Map<ApiMode, String>): String {
    val g = rate?.resources?.graphql
    return when {
        rate == null -> "Loading rate limits…"
        mode != ApiMode.Rest && g != null && g.limit in 1..999 ->
            "Your server allows ${g.limit} GraphQL points an hour. ${if (mode == ApiMode.GraphQL) "GraphQL mode will run out after a few refreshes; Hybrid is safer." else "Hybrid only spends a few per refresh."}"
        mode == ApiMode.Rest -> "Every refresh re-reads checks and reviews per PR; with many repos this adds up."
        mode == ApiMode.GraphQL -> "GraphQL uses points, not calls: one refresh costs ${est.getValue(ApiMode.GraphQL).removePrefix("≈ ")}."
        else -> "Unchanged lists come back as 304 Not Modified and don't count against the REST limit."
    }
}

@Composable
private fun ModeRow(m: ModeInfo, on: Boolean, onClick: () -> Unit) {
    val t = LocalTokens.current
    val shape = RoundedCornerShape(10.dp)
    Row(
        Modifier.fillMaxWidth().clip(shape).background(if (on) t.accentSoft else t.pane).border(1.5.dp, if (on) t.accent else t.line, shape)
            .click(role = Role.RadioButton, onClick = onClick).padding(horizontal = 12.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Box(
            Modifier.padding(top = 3.dp).size(16.dp).clip(CircleShape)
                .then(if (on) Modifier.border(5.dp, t.accent, CircleShape).background(t.pane) else Modifier.border(1.5.dp, t.field, CircleShape))
        )
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Txt(m.label, weight = FontWeight.Bold)
                if (m.rec) Chip("recommended", t.okSoft, t.ok, radius = 6.dp)
                Spacer(Modifier.weight(1f))
                Txt(m.est, size = 12.sp, color = t.muted, maxLines = 1)
            }
            Txt(m.desc, size = 12.sp, color = t.muted, lineHeight = 17.sp)
        }
    }
}

@Composable
private fun RateRow(label: String, b: RateBucket?) {
    val t = LocalTokens.current
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row {
            Txt("$label rate limit", Modifier.weight(1f), size = 12.sp, weight = FontWeight.SemiBold, color = t.ink2)
            val text = when {
                b == null -> "–"
                b.limit == 0 -> "not limited"
                else -> "${fmt(b.remaining)} of ${fmt(b.limit)} left" + (minutesUntil(b.reset)?.let { " · resets in $it min" } ?: "")
            }
            Txt(text, size = 12.sp, color = t.muted, maxLines = 1)
        }
        val frac = if (b == null || b.limit <= 0) 1f else (b.remaining.toFloat() / b.limit).coerceIn(0f, 1f)
        val color = when { frac < 0.15f -> t.fail; frac < 0.4f -> t.warnDot; else -> t.ok }
        Box(Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)).background(t.line2)) {
            Box(Modifier.fillMaxWidth(frac).fillMaxHeight().clip(RoundedCornerShape(3.dp)).background(color))
        }
    }
}

private fun fmt(n: Int): String = n.toString().reversed().chunked(3).joinToString(",").reversed()
