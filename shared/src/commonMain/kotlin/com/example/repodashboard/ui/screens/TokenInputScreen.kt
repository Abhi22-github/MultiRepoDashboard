package com.example.repodashboard.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.repodashboard.ui.theme.*

@Composable
fun TokenInputScreen(
    onTokenSaved: (String, String) -> Unit,
    isLoading: Boolean = false,
    error: String? = null
) {
    val c       = LocalAppColors.current
    val body    = LocalBodyFont.current
    val display = LocalDisplayFont.current

    var token     by remember { mutableStateOf("") }
    var host      by remember { mutableStateOf("") }
    var showToken by remember { mutableStateOf(false) }

    BoxWithConstraints(
        modifier = Modifier.fillMaxSize()
            .background(if (c.isDark) Color(0xFF2A0A5E) else HotPink)
            .dots(Color.White.copy(alpha = 0.22f), spacing = 22.dp, radius = 2.4.dp)
    ) {
        val wide = maxWidth > 960.dp

        // ── Floating decoration ─────────────────────────────────────
        Blob(Sunshine, 220.dp, CircleShape, Modifier.align(Alignment.TopStart).offset((-60).dp, (-70).dp))
        Blob(Aqua, 140.dp, RoundedCornerShape(36.dp), Modifier.align(Alignment.BottomStart).offset(60.dp, 40.dp), rotation = 18f, striped = true)
        Blob(Lime, 120.dp, BurstShape(14, 0.26f), Modifier.align(Alignment.TopEnd).offset((-80).dp, 60.dp), rotation = 12f)
        Blob(Grape, 260.dp, CircleShape, Modifier.align(Alignment.BottomEnd).offset(90.dp, 110.dp), striped = true)
        Blob(Tangerine, 70.dp, RoundedCornerShape(18.dp), Modifier.align(Alignment.CenterEnd).offset((-40).dp, 120.dp), rotation = -22f)

        Row(
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(32.dp),
            horizontalArrangement = Arrangement.spacedBy(64.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (wide) Hero(display, body)

            // ── Form card ───────────────────────────────────────────
            Box {
                Column(
                    modifier = Modifier.width(440.dp)
                        .block(c.Surface, c.Ink, c.Shadow, RoundedCornerShape(28.dp), shadowOffset = 12.dp, borderWidth = 4.dp)
                ) {
                    // Striped header band
                    Row(
                        modifier = Modifier.fillMaxWidth().background(Sunshine)
                            .stripes(Color.White.copy(alpha = 0.35f), gap = 22.dp, width = 8.dp)
                            .padding(horizontal = 32.dp, vertical = 22.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Box(
                            Modifier.size(52.dp).rotate(-8f).block(HotPink, InkBlack, InkBlack, RoundedCornerShape(14.dp), 4.dp),
                            contentAlignment = Alignment.Center
                        ) { Icon(FeatherIcons.GitBranch, null, tint = InkBlack, modifier = Modifier.size(26.dp).rotate(8f)) }
                        Column {
                            Text("PR DASHBOARD", fontFamily = display, fontWeight = FontWeight.ExtraBold, fontSize = 24.sp, letterSpacing = 0.5.sp, color = InkBlack)
                            Text("plug in your GitHub & go wild", fontFamily = body, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = InkBlack)
                        }
                    }
                    Box(Modifier.fillMaxWidth().height(4.dp).background(c.Ink))

                    Column(Modifier.padding(32.dp)) {
                        FieldLabel("GitHub host", "optional", Aqua)
                        Spacer(Modifier.height(8.dp))
                        MaxTextField(
                            value = host, onValueChange = { host = it },
                            placeholder = "github.mycompany.com — blank for github.com",
                            enabled = !isLoading, mono = true
                        )

                        Spacer(Modifier.height(22.dp))
                        FieldLabel("Personal access token", "required", HotPink)
                        Spacer(Modifier.height(8.dp))
                        MaxTextField(
                            value = token, onValueChange = { token = it },
                            placeholder = "ghp_xxxxxxxxxxxxxxxxxxxx",
                            enabled = !isLoading, mono = true,
                            visualTransformation = if (showToken) VisualTransformation.None else PasswordVisualTransformation(),
                            trailing = {
                                Box(
                                    Modifier.rotate(3f).block(Lime, c.Ink, c.Shadow, RoundedCornerShape(8.dp), 2.dp, 2.dp)
                                        .clickable { showToken = !showToken }.padding(horizontal = 8.dp, vertical = 3.dp)
                                ) {
                                    Text(if (showToken) "HIDE" else "SHOW", fontFamily = display, fontWeight = FontWeight.ExtraBold, fontSize = 10.sp, color = c.OnBright)
                                }
                            }
                        )

                        if (error != null) {
                            Spacer(Modifier.height(18.dp))
                            MaxNotice(FeatherIcons.AlertTriangle, error, c.Error)
                        }

                        Spacer(Modifier.height(28.dp))
                        MaxButton(
                            text = if (isLoading) "Validating…" else "Connect to GitHub  →",
                            onClick = { if (token.isNotBlank()) onTokenSaved(token.trim(), host.trim()) },
                            enabled = token.isNotBlank() && !isLoading,
                            loading = isLoading,
                            fill = HotPink,
                            modifier = Modifier.fillMaxWidth()
                        )

                        Spacer(Modifier.height(22.dp))
                        Squiggle(c.Primary, amplitude = 3.dp, wavelength = 14.dp, stroke = 2.5.dp)
                        Spacer(Modifier.height(12.dp))
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Icon(FeatherIcons.Lock, null, tint = c.TextSecondary, modifier = Modifier.size(13.dp))
                            Text("Stored only in your browser — never sent to any server", fontFamily = body, fontWeight = FontWeight.SemiBold, fontSize = 11.sp, color = c.TextSecondary)
                        }
                    }
                }
                Sticker(
                    "100% local", Sunshine, rotation = 9f, fontSize = 12.sp, icon = FeatherIcons.Lock,
                    modifier = Modifier.align(Alignment.TopEnd).offset(18.dp, (-16).dp)
                )
            }
        }
    }
}

@Composable
private fun Hero(display: androidx.compose.ui.text.font.FontFamily, body: androidx.compose.ui.text.font.FontFamily) {
    Column(Modifier.widthIn(max = 520.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Sticker("v2 · now louder", Lime, rotation = -4f, fontSize = 13.sp)
        Spacer(Modifier.height(10.dp))
        HeroWord("EVERY", Sunshine, -3f)
        HeroWord("PULL", Aqua, 2f, indent = 48.dp)
        HeroWord("REQUEST.", Cream, -1.5f, indent = 16.dp)
        HeroWord("ONE WALL.", Lime, 3f, indent = 72.dp)
        Spacer(Modifier.height(18.dp))
        Text(
            "Open PRs, drafts, releases and the stuff you forgot to ship — all your repos, shouting at you from one place.",
            fontFamily = body, fontWeight = FontWeight.Bold, fontSize = 17.sp, lineHeight = 26.sp, color = Color.White
        )
    }
}

@Composable
private fun HeroWord(text: String, fill: Color, rotation: Float, indent: Dp = 0.dp) {
    Box(
        Modifier.padding(start = indent).rotate(rotation)
            .block(fill, InkBlack, InkBlack, RoundedCornerShape(10.dp), shadowOffset = 7.dp, borderWidth = 4.dp)
            .padding(horizontal = 18.dp, vertical = 2.dp)
    ) {
        Text(text, fontFamily = LocalDisplayFont.current, fontWeight = FontWeight.ExtraBold, fontSize = 64.sp, lineHeight = 72.sp, color = InkBlack)
    }
}

@Composable
private fun FieldLabel(label: String, tag: String, tagColor: Color) {
    val c = LocalAppColors.current
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(label.uppercase(), fontFamily = LocalDisplayFont.current, fontWeight = FontWeight.ExtraBold, fontSize = 13.sp, letterSpacing = 1.sp, color = c.TextPrimary)
        Box(Modifier.background(tagColor, RoundedCornerShape(50)).border(2.dp, c.Ink, RoundedCornerShape(50)).padding(horizontal = 7.dp, vertical = 1.dp)) {
            Text(tag.uppercase(), fontFamily = LocalBodyFont.current, fontWeight = FontWeight.Bold, fontSize = 9.sp, letterSpacing = 0.5.sp, color = c.OnBright)
        }
    }
}

@Composable
private fun Blob(
    fill: Color, size: Dp, shape: androidx.compose.ui.graphics.Shape, modifier: Modifier,
    rotation: Float = 0f, striped: Boolean = false
) {
    Box(
        modifier.size(size).rotate(rotation)
            .block(fill, InkBlack, InkBlack, shape, shadowOffset = 8.dp, borderWidth = 4.dp)
            .then(if (striped) Modifier.stripes(Color.White.copy(alpha = 0.3f), gap = 20.dp, width = 7.dp) else Modifier)
    )
}
