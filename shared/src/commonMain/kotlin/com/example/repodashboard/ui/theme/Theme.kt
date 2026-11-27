package com.example.repodashboard.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import org.jetbrains.compose.resources.Font
import repodashboard.shared.generated.resources.Res
import repodashboard.shared.generated.resources.dmmono_medium
import repodashboard.shared.generated.resources.dmmono_regular
import repodashboard.shared.generated.resources.dmsans_bold
import repodashboard.shared.generated.resources.dmsans_medium
import repodashboard.shared.generated.resources.dmsans_regular
import repodashboard.shared.generated.resources.dmsans_semibold

/** Design tokens from the "Release inbox" design (DM Sans / DM Mono, accent #2563EB). */
data class Tokens(
    val bg: Color, val skel: Color, val pane: Color,
    val ink: Color, val ink2: Color, val muted: Color,
    val line: Color, val line2: Color,
    val accent: Color, val accentSoft: Color, val accentText: Color, val accentLine: Color, val sel: Color,
    val warnSoft: Color, val warn: Color, val warnLine: Color, val warnDot: Color,
    val okSoft: Color, val ok: Color,
    val failSoft: Color, val fail: Color, val failText: Color,
    val field: Color, val disabled: Color,
    /** Hairline used around soft cards. */
    val cardRing: Color,
    val isDark: Boolean,
)

val LightTokens = Tokens(
    bg = Color(0xFFF4F4F8), skel = Color(0xFFEDEDF2), pane = Color(0xFFFFFFFF),
    ink = Color(0xFF18181B), ink2 = Color(0xFF3F3F46), muted = Color(0xFF5B5B66),
    line = Color(0xFFE4E4EA), line2 = Color(0xFFEDEDF2),
    accent = Color(0xFF2563EB), accentSoft = Color(0xFFE8F0FE), accentText = Color(0xFF1D4ED8), accentLine = Color(0xFFBFD3FB), sel = Color(0xFFE8F0FE),
    warnSoft = Color(0xFFFEF3E2), warn = Color(0xFF9A4A08), warnLine = Color(0xFFF3C98B), warnDot = Color(0xFFD97706),
    okSoft = Color(0xFFE3F3E8), ok = Color(0xFF15803D),
    failSoft = Color(0xFFFDECEC), fail = Color(0xFFB91C1C), failText = Color(0xFF991B1B),
    field = Color(0xFFC9C9D3), disabled = Color(0xFFE4E4EA),
    cardRing = Color(0x0D18181B),
    isDark = false,
)

val DarkTokens = Tokens(
    bg = Color(0xFF0E1116), skel = Color(0xFF232833), pane = Color(0xFF161A21),
    ink = Color(0xFFECEEF2), ink2 = Color(0xFFC9CDD6), muted = Color(0xFF9BA3B1),
    line = Color(0xFF2A303B), line2 = Color(0xFF232833),
    accent = Color(0xFF2563EB), accentSoft = Color(0xFF1B2A47), accentText = Color(0xFF93C5FD), accentLine = Color(0xFF2F4E85), sel = Color(0xFF1B2A47),
    warnSoft = Color(0xFF33260F), warn = Color(0xFFFBBF24), warnLine = Color(0xFF6B4E1A), warnDot = Color(0xFFF59E0B),
    okSoft = Color(0xFF163322), ok = Color(0xFF86EFAC),
    failSoft = Color(0xFF3A1C1F), fail = Color(0xFFFCA5A5), failText = Color(0xFFFCA5A5),
    field = Color(0xFF3A4150), disabled = Color(0xFF232833),
    cardRing = Color(0xFF2F3541),
    isDark = true,
)

val Released = Color(0xFF16A34A)
val Neutral = Color(0xFFA1A1AA)
val StarGold = Color(0xFFF59E0B)

val LocalTokens = staticCompositionLocalOf { LightTokens }
val LocalSans = staticCompositionLocalOf<FontFamily> { FontFamily.Default }
val LocalMono = staticCompositionLocalOf<FontFamily> { FontFamily.Monospace }

@Composable
fun AppTheme(darkTheme: Boolean = false, content: @Composable () -> Unit) {
    val sans = FontFamily(
        Font(Res.font.dmsans_regular, FontWeight.Normal),
        Font(Res.font.dmsans_medium, FontWeight.Medium),
        Font(Res.font.dmsans_semibold, FontWeight.SemiBold),
        Font(Res.font.dmsans_bold, FontWeight.Bold),
    )
    val mono = FontFamily(
        Font(Res.font.dmmono_regular, FontWeight.Normal),
        Font(Res.font.dmmono_medium, FontWeight.Medium),
    )
    val t = if (darkTheme) DarkTokens else LightTokens
    val material = if (darkTheme)
        darkColorScheme(primary = t.accent, background = t.bg, surface = t.pane, onBackground = t.ink, onSurface = t.ink, error = t.fail)
    else
        lightColorScheme(primary = t.accent, background = t.bg, surface = t.pane, onBackground = t.ink, onSurface = t.ink, error = t.fail)

    val reduced = androidx.compose.runtime.remember { com.example.repodashboard.prefersReducedMotion() }
    CompositionLocalProvider(LocalTokens provides t, LocalSans provides sans, LocalMono provides mono, LocalReducedMotion provides reduced) {
        MaterialTheme(colorScheme = material, content = content)
    }
}
