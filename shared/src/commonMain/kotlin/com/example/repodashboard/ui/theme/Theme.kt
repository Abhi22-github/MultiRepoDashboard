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
    val isDark: Boolean
)

val LightColors = AppColorScheme(
    Primary      = Color(0xFF2563EB),
    PrimaryDark  = Color(0xFF1D4ED8),
    PrimaryLight = Color(0xFFEFF6FF),
    PrimaryMid   = Color(0xFFBFDBFE),
    Success      = Color(0xFF1E8E3E),
    SuccessLight = Color(0xFFE6F4EA),
    Warning      = Color(0xFFE37400),
    WarningLight = Color(0xFFFEF7E0),
    Error        = Color(0xFFD93025),
    ErrorLight   = Color(0xFFFCE8E6),
    Info         = Color(0xFF7C3AED),
    InfoLight    = Color(0xFFF3EEFE),
    Sidebar          = Color(0xFFFFFFFF),
    SidebarHover     = Color(0xFFF1F3F4),
    SidebarActive    = Color(0xFFEFF6FF),
    SidebarBorder    = Color(0xFFE8EAED),
    SidebarText      = Color(0xFF3C4043),
    SidebarTextMuted = Color(0xFF80868B),
    Background     = Color(0xFFF6F8FA),
    Surface        = Color(0xFFFFFFFF),
    SurfaceVariant = Color(0xFFF1F3F4),
    SurfaceHover   = Color(0xFFFBFCFD),
    TextPrimary   = Color(0xFF1F2328),
    TextSecondary = Color(0xFF5F6368),
    TextMuted     = Color(0xFF9AA0A6),
    Border  = Color(0xFFE1E4E8),
    Divider = Color(0xFFEDEFF2),
    isDark = false
)

val DarkColors = AppColorScheme(
    Primary      = Color(0xFF60A5FA),
    PrimaryDark  = Color(0xFF93C5FD),
    PrimaryLight = Color(0xFF17233A),
    PrimaryMid   = Color(0xFF1E3A5F),
    Success      = Color(0xFF81C995),
    SuccessLight = Color(0xFF1E3A28),
    Warning      = Color(0xFFFDD663),
    WarningLight = Color(0xFF3D3016),
    Error        = Color(0xFFF28B82),
    ErrorLight   = Color(0xFF3B2320),
    Info         = Color(0xFFC4B5FD),
    InfoLight    = Color(0xFF2A2140),
    Sidebar          = Color(0xFF1A1A1C),
    SidebarHover     = Color(0xFF27272A),
    SidebarActive    = Color(0xFF17233A),
    SidebarBorder    = Color(0xFF2E2E31),
    SidebarText      = Color(0xFFC4C7C5),
    SidebarTextMuted = Color(0xFF80868B),
    Background     = Color(0xFF0F0F10),
    Surface        = Color(0xFF1C1C1E),
    SurfaceVariant = Color(0xFF29292C),
    SurfaceHover   = Color(0xFF242427),
    TextPrimary   = Color(0xFFE6E6E8),
    TextSecondary = Color(0xFF9AA0A6),
    TextMuted     = Color(0xFF77797D),
    Border  = Color(0xFF34343A),
    Divider = Color(0xFF29292C),
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
        onPrimary = Color(0xFF1F2328), onBackground = colors.TextPrimary, onSurface = colors.TextPrimary
    ) else lightColorScheme(
        primary = colors.Primary, secondary = colors.Success, tertiary = colors.Warning,
        error = colors.Error, background = colors.Background, surface = colors.Surface,
        onPrimary = Color.White, onBackground = colors.TextPrimary, onSurface = colors.TextPrimary
    )

    CompositionLocalProvider(
        LocalBodyFont    provides body,
        LocalDisplayFont provides display,
        LocalAppColors   provides colors
    ) {
        MaterialTheme(
            colorScheme = material,
            typography = Typography(
                headlineLarge  = TextStyle(fontFamily = display, fontWeight = FontWeight.Bold,     fontSize = 24.sp, lineHeight = 30.sp),
                headlineMedium = TextStyle(fontFamily = display, fontWeight = FontWeight.Bold,     fontSize = 20.sp, lineHeight = 26.sp),
                headlineSmall  = TextStyle(fontFamily = display, fontWeight = FontWeight.SemiBold, fontSize = 17.sp, lineHeight = 23.sp),
                titleLarge     = TextStyle(fontFamily = display, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, lineHeight = 22.sp),
                titleMedium    = TextStyle(fontFamily = display, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, lineHeight = 20.sp),
                titleSmall     = TextStyle(fontFamily = body,    fontWeight = FontWeight.SemiBold, fontSize = 13.sp, lineHeight = 18.sp),
                bodyLarge      = TextStyle(fontFamily = body,    fontWeight = FontWeight.Normal,   fontSize = 14.sp, lineHeight = 22.sp),
                bodyMedium     = TextStyle(fontFamily = body,    fontWeight = FontWeight.Normal,   fontSize = 13.sp, lineHeight = 20.sp),
                bodySmall      = TextStyle(fontFamily = body,    fontWeight = FontWeight.Normal,   fontSize = 12.sp, lineHeight = 18.sp),
                labelLarge     = TextStyle(fontFamily = body,    fontWeight = FontWeight.Medium,   fontSize = 12.sp, lineHeight = 16.sp),
                labelMedium    = TextStyle(fontFamily = body,    fontWeight = FontWeight.Medium,   fontSize = 11.sp, lineHeight = 15.sp),
                labelSmall     = TextStyle(fontFamily = body,    fontWeight = FontWeight.Medium,   fontSize = 10.sp, lineHeight = 14.sp),
            ),
            content = content
        )
    }
}
