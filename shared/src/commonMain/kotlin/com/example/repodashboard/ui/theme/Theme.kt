package com.example.repodashboard.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import org.jetbrains.compose.resources.Font
import repodashboard.shared.generated.resources.Res
import repodashboard.shared.generated.resources.inter_bold
import repodashboard.shared.generated.resources.inter_medium
import repodashboard.shared.generated.resources.inter_regular
import repodashboard.shared.generated.resources.inter_semibold
import repodashboard.shared.generated.resources.jakarta_bold
import repodashboard.shared.generated.resources.jakarta_extrabold
import repodashboard.shared.generated.resources.jakarta_medium
import repodashboard.shared.generated.resources.jakarta_regular
import repodashboard.shared.generated.resources.jakarta_semibold

/** Plus Jakarta Sans — headings & display. */
val LocalDisplayFont = staticCompositionLocalOf<FontFamily> { FontFamily.Default }
/** Inter — body & dense UI. */
val LocalBodyFont    = staticCompositionLocalOf<FontFamily> { FontFamily.Default }

// ── Maximalist palette ─────────────────────────────────────────────
// Loud, saturated brights. Everything except Grape/Cobalt is light enough to carry Ink text.
val HotPink  = Color(0xFFFF2E88)
val Sunshine = Color(0xFFFFD60A)
val Cobalt   = Color(0xFF2D5BFF)
val Lime     = Color(0xFFB6F500)
val Tangerine = Color(0xFFFF6B1A)
val Grape    = Color(0xFF8B3DFF)
val Aqua     = Color(0xFF00D1B2)
val Lavender = Color(0xFFC9A7FF)
val Sky      = Color(0xFF7FB2FF)
val Cream    = Color(0xFFFFF4D6)
val InkBlack = Color(0xFF14072B)

data class AppColorScheme(
    val Primary: Color,
    val PrimaryDark: Color,
    val PrimaryLight: Color,
    val PrimaryMid: Color,
    val Success: Color,
    val SuccessLight: Color,
    val Warning: Color,
    val WarningLight: Color,
    val Error: Color,
    val ErrorLight: Color,
    val Info: Color,
    val InfoLight: Color,
    val Sidebar: Color,
    val SidebarHover: Color,
    val SidebarActive: Color,
    val SidebarBorder: Color,
    val SidebarText: Color,
    val SidebarTextMuted: Color,
    val Background: Color,
    val Surface: Color,
    val SurfaceVariant: Color,
    val SurfaceHover: Color,
    val TextPrimary: Color,
    val TextSecondary: Color,
    val TextMuted: Color,
    val Border: Color,
    val Divider: Color,
    /** Thick outlines around every block. */
    val Ink: Color,
    /** Solid offset "hard" shadow color. */
    val Shadow: Color,
    /** Text/icon color placed on top of any bright accent fill. */
    val OnBright: Color,
    /** Pattern ink drawn over backgrounds (dots, stripes). */
    val Pattern: Color,
    /** Rotating accent fills for cards, chips, stickers. */
    val Accents: List<Color>,
    val isDark: Boolean
) {
    fun accent(i: Int): Color = Accents[((i % Accents.size) + Accents.size) % Accents.size]
}

private val AccentCycle = listOf(HotPink, Sunshine, Aqua, Tangerine, Lime, Lavender, Sky)

val LightColors = AppColorScheme(
    Primary      = HotPink,
    PrimaryDark  = Color(0xFFD9006B),
    PrimaryLight = Color(0xFFFFD1E6),
    PrimaryMid   = Color(0xFFFF8CC0),
    Success      = Color(0xFF00A86B),
    SuccessLight = Lime,
    Warning      = Tangerine,
    WarningLight = Sunshine,
    Error        = Color(0xFFE5001C),
    ErrorLight   = Color(0xFFFFC2C9),
    Info         = Grape,
    InfoLight    = Color(0xFFE3D1FF),
    Sidebar          = Color(0xFF2A0A5E),
    SidebarHover     = Color(0xFF3F1885),
    SidebarActive    = Sunshine,
    SidebarBorder    = InkBlack,
    SidebarText      = Cream,
    SidebarTextMuted = Color(0xFFC9B6F2),
    Background     = Cream,
    Surface        = Color(0xFFFFFFFF),
    SurfaceVariant = Color(0xFFFFE9A8),
    SurfaceHover   = Color(0xFFFFF9E8),
    TextPrimary   = InkBlack,
    TextSecondary = Color(0xFF3B2A5C),
    TextMuted     = Color(0xFF6E5F8A),
    Border  = InkBlack,
    Divider = InkBlack,
    Ink      = InkBlack,
    Shadow   = InkBlack,
    OnBright = InkBlack,
    Pattern  = Color(0x2914072B),
    Accents  = AccentCycle,
    isDark = false
)

val DarkColors = AppColorScheme(
    Primary      = HotPink,
    PrimaryDark  = Color(0xFFFF6BAA),
    PrimaryLight = Color(0xFF4A0F33),
    PrimaryMid   = Color(0xFF9C1D5C),
    Success      = Lime,
    SuccessLight = Lime,
    Warning      = Sunshine,
    WarningLight = Sunshine,
    Error        = Color(0xFFFF5470),
    ErrorLight   = Color(0xFF4A0F1C),
    Info         = Color(0xFFB98CFF),
    InfoLight    = Color(0xFF2E1660),
    Sidebar          = Color(0xFF0B0219),
    SidebarHover     = Color(0xFF241047),
    SidebarActive    = Sunshine,
    SidebarBorder    = Cream,
    SidebarText      = Cream,
    SidebarTextMuted = Color(0xFFA08CC9),
    Background     = Color(0xFF16072F),
    Surface        = Color(0xFF241047),
    SurfaceVariant = Color(0xFF34195F),
    SurfaceHover   = Color(0xFF2D1455),
    TextPrimary   = Cream,
    TextSecondary = Color(0xFFE2D4FF),
    TextMuted     = Color(0xFFA08CC9),
    Border  = Cream,
    Divider = Color(0xFF5A3D8C),
    Ink      = Cream,
    Shadow   = HotPink,
    OnBright = InkBlack,
    Pattern  = Color(0x26FFF4D6),
    Accents  = AccentCycle,
    isDark = true
)

val LocalAppColors = staticCompositionLocalOf { LightColors }

@Composable
fun AppTheme(darkTheme: Boolean = false, content: @Composable () -> Unit) {
    val body = FontFamily(
        Font(Res.font.inter_regular,  FontWeight.Normal),
        Font(Res.font.inter_medium,   FontWeight.Medium),
        Font(Res.font.inter_semibold, FontWeight.SemiBold),
        Font(Res.font.inter_bold,     FontWeight.Bold),
    )
    val display = FontFamily(
        Font(Res.font.jakarta_regular,   FontWeight.Normal),
        Font(Res.font.jakarta_medium,    FontWeight.Medium),
        Font(Res.font.jakarta_semibold,  FontWeight.SemiBold),
        Font(Res.font.jakarta_bold,      FontWeight.Bold),
        Font(Res.font.jakarta_extrabold, FontWeight.ExtraBold),
    )
    val colors = if (darkTheme) DarkColors else LightColors

    val material = if (darkTheme) darkColorScheme(
        primary = colors.Primary, secondary = colors.Success, tertiary = colors.Warning,
        error = colors.Error, background = colors.Background, surface = colors.Surface,
        onPrimary = InkBlack, onBackground = colors.TextPrimary, onSurface = colors.TextPrimary
    ) else lightColorScheme(
        primary = colors.Primary, secondary = colors.Success, tertiary = colors.Warning,
        error = colors.Error, background = colors.Background, surface = colors.Surface,
        onPrimary = InkBlack, onBackground = colors.TextPrimary, onSurface = colors.TextPrimary
    )

    CompositionLocalProvider(
        LocalBodyFont    provides body,
        LocalDisplayFont provides display,
        LocalAppColors   provides colors
    ) {
        MaterialTheme(
            colorScheme = material,
            typography = Typography(
                headlineLarge  = TextStyle(fontFamily = display, fontWeight = FontWeight.ExtraBold, fontSize = 40.sp, lineHeight = 44.sp),
                headlineMedium = TextStyle(fontFamily = display, fontWeight = FontWeight.ExtraBold, fontSize = 28.sp, lineHeight = 32.sp),
                headlineSmall  = TextStyle(fontFamily = display, fontWeight = FontWeight.ExtraBold, fontSize = 20.sp, lineHeight = 25.sp),
                titleLarge     = TextStyle(fontFamily = display, fontWeight = FontWeight.ExtraBold, fontSize = 17.sp, lineHeight = 22.sp),
                titleMedium    = TextStyle(fontFamily = display, fontWeight = FontWeight.Bold,      fontSize = 15.sp, lineHeight = 20.sp),
                titleSmall     = TextStyle(fontFamily = body,    fontWeight = FontWeight.Bold,      fontSize = 13.sp, lineHeight = 18.sp),
                bodyLarge      = TextStyle(fontFamily = body,    fontWeight = FontWeight.Medium,    fontSize = 14.sp, lineHeight = 22.sp),
                bodyMedium     = TextStyle(fontFamily = body,    fontWeight = FontWeight.Medium,    fontSize = 13.sp, lineHeight = 20.sp),
                bodySmall      = TextStyle(fontFamily = body,    fontWeight = FontWeight.Medium,    fontSize = 12.sp, lineHeight = 18.sp),
                labelLarge     = TextStyle(fontFamily = body,    fontWeight = FontWeight.Bold,      fontSize = 12.sp, lineHeight = 16.sp),
                labelMedium    = TextStyle(fontFamily = body,    fontWeight = FontWeight.Bold,      fontSize = 11.sp, lineHeight = 15.sp),
                labelSmall     = TextStyle(fontFamily = body,    fontWeight = FontWeight.Bold,      fontSize = 10.sp, lineHeight = 14.sp),
            ),
            content = content
        )
    }
}
