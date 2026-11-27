package com.example.repodashboard.ui.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.repodashboard.ui.inbox.*
import com.example.repodashboard.ui.theme.*

private val URL_RE = Regex("^https://[a-z0-9.-]+\\.[a-z]{2,}(:\\d+)?$", RegexOption.IGNORE_CASE)

/** "Connect to GitHub": token + optional Enterprise Server URL. */
@Composable
fun TokenInputScreen(
    isLoading: Boolean,
    error: String?,
    onTokenSaved: (token: String, host: String) -> Unit,
) {
    val t = LocalTokens.current
    val uri = LocalUriHandler.current
    var token by remember { mutableStateOf("") }
    var show by remember { mutableStateOf(false) }
    var enterprise by remember { mutableStateOf(false) }
    var url by remember { mutableStateOf("") }
    var showRejected by remember { mutableStateOf(false) }
    LaunchedEffect(error) { showRejected = error != null }

    val tok = token.trim()
    val valid = (tok.startsWith("ghp_") || tok.startsWith("github_pat_")) && tok.length >= 12
    val urlRaw = url.trim().trimEnd('/')
    val urlOk = URL_RE.matches(urlRaw)
    val urlError = enterprise && urlRaw.isNotEmpty() && !urlOk
    val host = if (urlOk) urlRaw.removePrefix("https://") else ""
    val formatError = tok.isNotEmpty() && !valid
    val rejected = showRejected && !isLoading
    val canConnect = valid && !isLoading && (!enterprise || urlOk)

    Box(
        Modifier.fillMaxSize().background(t.bg).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 48.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(Modifier.widthIn(max = 440.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(20.dp)) {
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                AppLogo(52.dp)
                Txt("PR Dashboard", size = 28.sp, weight = FontWeight.Bold, letterSpacing = (-0.5).sp)
                Text(
                    "See what's merged, what's waiting and what ships next across your repositories.",
                    color = t.muted, fontSize = 15.sp, fontFamily = LocalSans.current, textAlign = TextAlign.Center, lineHeight = 22.sp
                )
            }

            val cardShape = RoundedCornerShape(16.dp)
            Column(Modifier.fillMaxWidth().softCard(cardShape).padding(28.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                // Where your repositories live
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(t.bg).padding(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Segment("GitHub.com", !enterprise, Modifier.weight(1f)) { enterprise = false; showRejected = false }
                    Segment("GitHub Enterprise Server", enterprise, Modifier.weight(1f)) { enterprise = true; showRejected = false }
                }

                if (enterprise) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Txt("Server URL", weight = FontWeight.SemiBold)
                        Field(
                            url, { url = it; showRejected = false }, "https://github.yourcompany.com",
                            Modifier.fillMaxWidth(), leading = FeatherIcons.Server, height = 48.dp, bg = Color.Transparent,
                            border = if (urlError) t.fail else if (urlOk) t.accent else t.field, mono = true
                        )
                        if (urlError) Txt("Enter the full https:// address of your server, e.g. https://github.acme.com", size = 13.sp, color = t.fail)
                        if (urlOk) Txt("API: $urlRaw/api/v3", size = 13.sp, color = t.muted, mono = true)
                    }
                }

                Txt("Personal access token", weight = FontWeight.SemiBold)
                Field(
                    token, { token = it; showRejected = false }, "ghp_xxxxxxxxxxxxxxxxxxxx",
                    Modifier.fillMaxWidth(), height = 48.dp, bg = Color.Transparent,
                    border = if (formatError || rejected) t.fail else if (valid) t.accent else t.field,
                    mono = true, password = !show,
                    trailing = {
                        Box(
                            Modifier.height(36.dp).clip(RoundedCornerShape(8.dp)).background(t.bg).click { show = !show }.padding(horizontal = 12.dp),
                            contentAlignment = Alignment.Center
                        ) { Txt(if (show) "Hide" else "Show", weight = FontWeight.Medium, color = t.ink2) }
                    }
                )
                if (formatError) Txt("Use a token that starts with ghp_ or github_pat_ and is at least 12 characters.", size = 13.sp, color = t.fail)

                if (rejected) {
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(t.failSoft).padding(horizontal = 14.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Ico(FeatherIcons.AlertCircle, 18.dp, t.failText, Modifier.padding(top = 2.dp))
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Txt(if (enterprise) "${host.ifEmpty { "Your server" }} didn't accept this token" else "GitHub didn't accept this token", weight = FontWeight.Bold, color = t.failText)
                            Txt(error ?: "It may have expired, been revoked or be missing read access to pull requests.", size = 13.sp, color = t.failText)
                        }
                    }
                }

                when {
                    isLoading -> Row(
                        Modifier.fillMaxWidth().height(48.dp).clip(RoundedCornerShape(10.dp)).background(t.accent.copy(alpha = 0.85f)),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center
                    ) { Txt("Checking your token…", size = 15.sp, weight = FontWeight.SemiBold, color = Color.White) }
                    canConnect -> PrimaryButton(
                        "Connect to GitHub", { onTokenSaved(tok, if (enterprise) urlRaw else "") },
                        Modifier.fillMaxWidth(), height = 48.dp, trailing = FeatherIcons.ArrowRight
                    )
                    else -> DisabledButton("Connect to GitHub", Modifier.fillMaxWidth())
                }

                Column(
                    Modifier.fillMaxWidth().padding(top = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(Modifier.fillMaxWidth().height(1.dp).background(t.line2))
                    Spacer(Modifier.height(6.dp))
                    Note(FeatherIcons.Lock, "Kept only in this browser. It's sent to ${if (enterprise) host.ifEmpty { "your Enterprise server" } else "api.github.com"} and nowhere else.")
                    Note(FeatherIcons.Info, "Use a fine-grained token with read-only access to Pull requests, Contents, Metadata and Commit statuses.")
                }
            }
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                TextLink("Create a fine-grained read-only token", {
                    uri.openUri(if (enterprise && urlOk) "$urlRaw/settings/personal-access-tokens/new" else "https://github.com/settings/personal-access-tokens/new")
                }, color = t.accentText)
            }
        }
    }
}

@Composable
private fun Segment(label: String, on: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val t = LocalTokens.current
    val shape = RoundedCornerShape(9.dp)
    val fg by animateColorAsState(if (on) t.ink else t.ink2, motion(150))
    Box(
        modifier.height(38.dp).then(if (on) Modifier.softCard(shape) else Modifier.clip(shape))
            .click(role = Role.RadioButton, onClick = onClick),
        contentAlignment = Alignment.Center
    ) { Txt(label, size = 13.sp, weight = if (on) FontWeight.Bold else FontWeight.Medium, color = fg, maxLines = 1) }
}

@Composable
private fun Note(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String) {
    val t = LocalTokens.current
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Ico(icon, 16.dp, t.muted, Modifier.padding(top = 2.dp))
        Txt(text, size = 13.sp, color = t.muted)
    }
}
