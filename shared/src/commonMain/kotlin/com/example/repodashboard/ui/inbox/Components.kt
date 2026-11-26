package com.example.repodashboard.ui.inbox

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import com.example.repodashboard.ui.theme.*

/** Text in the app's DM Sans / DM Mono type. */
@Composable
fun Txt(
    text: String,
    modifier: Modifier = Modifier,
    size: TextUnit = 14.sp,
    weight: FontWeight = FontWeight.Normal,
    color: Color = LocalTokens.current.ink,
    mono: Boolean = false,
    maxLines: Int = Int.MAX_VALUE,
    lineHeight: TextUnit = TextUnit.Unspecified,
    letterSpacing: TextUnit = TextUnit.Unspecified,
) {
    Text(
        text, modifier,
        color = color, fontSize = size, fontWeight = weight,
        fontFamily = if (mono) LocalMono.current else LocalSans.current,
        maxLines = maxLines, overflow = TextOverflow.Ellipsis,
        lineHeight = if (lineHeight == TextUnit.Unspecified) size * 1.5f else lineHeight,
        letterSpacing = letterSpacing,
    )
}

fun Modifier.click(role: Role = Role.Button, enabled: Boolean = true, onClick: () -> Unit): Modifier =
    this.pointerHoverIcon(if (enabled) PointerIcon.Hand else PointerIcon.Default)
        .clickable(enabled = enabled, role = role, onClick = onClick)

/** The design's soft card: light shadow + hairline ring (dark: ring only). */
@Composable
fun Modifier.softCard(shape: Shape, fill: Color = LocalTokens.current.pane): Modifier {
    val t = LocalTokens.current
    val base = if (t.isDark) this else this.shadow(1.5.dp, shape, ambientColor = Color(0x2218181B), spotColor = Color(0x2218181B))
    return base.clip(shape).background(fill).border(1.dp, t.cardRing, shape)
}

@Composable
fun Ico(icon: ImageVector, size: Dp = 16.dp, tint: Color = LocalTokens.current.ink, modifier: Modifier = Modifier) =
    Icon(icon, contentDescription = null, tint = tint, modifier = modifier.size(size))

@Composable
fun IconBtn(icon: ImageVector, label: String, onClick: () -> Unit, size: Dp = 36.dp, tint: Color = LocalTokens.current.muted) {
    Box(
        Modifier.size(size).clip(RoundedCornerShape(8.dp)).click(onClick = onClick).semantics { contentDescription = label },
        contentAlignment = Alignment.Center
    ) { Ico(icon, 16.dp, tint) }
}

@Composable
fun PrimaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, height: Dp = 40.dp, trailing: ImageVector? = null) {
    val t = LocalTokens.current
    Row(
        modifier.height(height).clip(RoundedCornerShape(10.dp)).background(t.accent).click(onClick = onClick).padding(horizontal = 18.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)
    ) {
        Txt(text, size = 15.sp, weight = FontWeight.SemiBold, color = Color.White, maxLines = 1)
        if (trailing != null) Ico(trailing, 16.dp, Color.White)
    }
}

@Composable
fun DisabledButton(text: String, modifier: Modifier = Modifier, height: Dp = 48.dp) {
    val t = LocalTokens.current
    Box(modifier.height(height).clip(RoundedCornerShape(10.dp)).background(t.disabled).padding(horizontal = 18.dp), contentAlignment = Alignment.Center) {
        Txt(text, size = 15.sp, weight = FontWeight.SemiBold, color = t.muted, maxLines = 1)
    }
}

@Composable
fun GhostButton(text: String, onClick: () -> Unit, icon: ImageVector? = null, height: Dp = 40.dp) {
    val t = LocalTokens.current
    val shape = RoundedCornerShape(10.dp)
    Row(
        Modifier.height(height).softCard(shape).click(onClick = onClick).padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Txt(text, weight = FontWeight.Medium, maxLines = 1)
        if (icon != null) Ico(icon, 14.dp, t.ink)
    }
}

@Composable
fun TextLink(text: String, onClick: () -> Unit, color: Color = LocalTokens.current.accentText, size: TextUnit = 13.sp) {
    Box(Modifier.clip(RoundedCornerShape(6.dp)).click(onClick = onClick).padding(horizontal = 8.dp, vertical = 6.dp)) {
        Txt(text, size = size, weight = FontWeight.SemiBold, color = color, maxLines = 1)
    }
}

/** Small rounded label: "3 open", "team: x", counts. */
@Composable
fun Chip(text: String, bg: Color, fg: Color, size: TextUnit = 11.sp, weight: FontWeight = FontWeight.SemiBold, radius: Dp = 5.dp, border: Color? = null) {
    val shape = RoundedCornerShape(radius)
    Box(
        Modifier.clip(shape).background(bg).then(if (border != null) Modifier.border(1.dp, border, shape) else Modifier)
            .padding(horizontal = 6.dp, vertical = 0.dp)
    ) { Txt(text, size = size, weight = weight, color = fg, maxLines = 1, lineHeight = 18.sp) }
}

enum class DotStyle { Solid, Ring, Dashed }

@Composable
fun Dot(color: Color, style: DotStyle = DotStyle.Solid, size: Dp = 8.dp) {
    Box(
        Modifier.size(size).drawBehind {
            when (style) {
                DotStyle.Solid -> drawCircle(color)
                DotStyle.Ring -> drawCircle(color, radius = this.size.minDimension / 2 - 0.75.dp.toPx(), style = Stroke(1.5.dp.toPx()))
                DotStyle.Dashed -> drawCircle(
                    color, radius = this.size.minDimension / 2 - 0.75.dp.toPx(),
                    style = Stroke(1.5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(2.dp.toPx(), 1.5.dp.toPx())))
                )
            }
        }
    )
}

@Composable
fun SkelBar(width: Float, height: Dp, modifier: Modifier = Modifier, radius: Dp = 6.dp) {
    val t = LocalTokens.current
    Box(modifier.fillMaxWidth(width).height(height).clip(RoundedCornerShape(radius)).background(t.skel))
}

@Composable
fun EmptyState(icon: ImageVector, iconBg: Color, iconFg: Color, title: String, body: String, action: (@Composable () -> Unit)? = null) {
    val t = LocalTokens.current
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(Modifier.size(48.dp).clip(CircleShape).background(iconBg), contentAlignment = Alignment.Center) { Ico(icon, 22.dp, iconFg) }
        Txt(title, size = 15.sp, weight = FontWeight.Bold)
        Box(Modifier.widthIn(max = 300.dp)) {
            Text(body, color = t.muted, fontSize = 14.sp, fontFamily = LocalSans.current, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        }
        if (action != null) { Spacer(Modifier.height(6.dp)); action() }
    }
}

/** Single-line input styled like the design's fields. */
@Composable
fun Field(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    leading: ImageVector? = null,
    height: Dp = 44.dp,
    bg: Color = LocalTokens.current.bg,
    border: Color? = null,
    mono: Boolean = false,
    password: Boolean = false,
    fontSize: TextUnit = 14.sp,
    trailing: (@Composable () -> Unit)? = null,
) {
    val t = LocalTokens.current
    val shape = RoundedCornerShape(10.dp)
    val style = TextStyle(color = t.ink, fontSize = fontSize, fontFamily = if (mono) LocalMono.current else LocalSans.current)
    Row(
        modifier.height(height).clip(shape).background(bg)
            .then(if (border != null) Modifier.border(1.dp, border, shape) else Modifier)
            .padding(start = 14.dp, end = if (trailing != null) 6.dp else 14.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (leading != null) Ico(leading, 16.dp, t.muted)
        Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
            if (value.isEmpty()) Text(placeholder, style = style.copy(color = t.muted), maxLines = 1, overflow = TextOverflow.Ellipsis)
            BasicTextField(
                value, onValueChange, singleLine = true, textStyle = style,
                cursorBrush = SolidColor(t.accent),
                visualTransformation = if (password) PasswordVisualTransformation() else VisualTransformation.None,
                keyboardOptions = if (password) KeyboardOptions(keyboardType = KeyboardType.Password) else KeyboardOptions.Default,
                modifier = Modifier.fillMaxWidth().semantics { contentDescription = placeholder }
            )
        }
        trailing?.invoke()
    }
}

/** A dropdown panel anchored under its trigger. */
@Composable
fun Dropdown(open: Boolean, onDismiss: () -> Unit, alignEnd: Boolean = false, offsetY: Dp = 40.dp, content: @Composable ColumnScope.() -> Unit) {
    if (!open) return
    val t = LocalTokens.current
    val dy = with(androidx.compose.ui.platform.LocalDensity.current) { offsetY.roundToPx() }
    Popup(
        alignment = if (alignEnd) Alignment.TopEnd else Alignment.TopStart,
        offset = androidx.compose.ui.unit.IntOffset(0, dy),
        onDismissRequest = onDismiss,
        properties = PopupProperties(focusable = true)
    ) {
        val shape = RoundedCornerShape(12.dp)
        Column(
            Modifier.widthIn(min = 260.dp, max = 340.dp).heightIn(max = 420.dp)
                .shadow(12.dp, shape).clip(shape).background(t.pane).border(1.dp, t.line, shape).padding(6.dp),
            content = content
        )
    }
}

@Composable
fun CheckBox(on: Boolean) {
    val t = LocalTokens.current
    val shape = RoundedCornerShape(4.dp)
    Box(
        Modifier.size(16.dp).clip(shape).then(if (on) Modifier.background(t.accent) else Modifier.border(1.5.dp, t.field, shape)),
        contentAlignment = Alignment.Center
    ) { if (on) Ico(FeatherIcons.Check, 12.dp, Color.White) }
}

/** Dashed horizontal bar (the "not released yet" journey step). */
@Composable
fun DashedBar(color: Color, modifier: Modifier = Modifier) {
    Box(modifier.height(4.dp).drawBehind {
        val y = size.height / 2
        drawLine(color, androidx.compose.ui.geometry.Offset(0f, y), androidx.compose.ui.geometry.Offset(size.width, y), strokeWidth = size.height,
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(8.dp.toPx(), 4.dp.toPx())))
    })
}
