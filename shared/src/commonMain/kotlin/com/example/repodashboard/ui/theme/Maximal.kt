package com.example.repodashboard.ui.theme

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

// ── Modifiers ──────────────────────────────────────────────────────

/** Solid, un-blurred offset shadow — the signature maximalist "sticker" depth. */
fun Modifier.hardShadow(color: Color, offset: Dp = 5.dp, shape: Shape = RectangleShape): Modifier = drawBehind {
    val o = offset.toPx()
    if (o <= 0f) return@drawBehind
    val outline = shape.createOutline(size, layoutDirection, this)
    translate(o, o) { drawOutline(outline, color) }
}

/** Shadow + clip + fill + thick ink outline, in the right order. */
fun Modifier.block(
    fill: Color,
    ink: Color,
    shadow: Color,
    shape: Shape = RoundedCornerShape(18.dp),
    shadowOffset: Dp = 6.dp,
    borderWidth: Dp = 3.dp,
): Modifier = this
    .hardShadow(shadow, shadowOffset, shape)
    .clip(shape)
    .background(fill)
    .border(borderWidth, ink, shape)

/** Staggered polka dots. */
fun Modifier.dots(color: Color, spacing: Dp = 18.dp, radius: Dp = 1.8.dp): Modifier = drawBehind {
    val s = spacing.toPx()
    val r = radius.toPx()
    var y = s / 2
    var row = 0
    while (y < size.height + s) {
        var x = if (row % 2 == 0) s / 2 else s
        while (x < size.width + s) {
            drawCircle(color, r, Offset(x, y))
            x += s
        }
        y += s
        row++
    }
}

/** Diagonal candy stripes. */
fun Modifier.stripes(color: Color, gap: Dp = 16.dp, width: Dp = 5.dp): Modifier = drawBehind {
    val g = gap.toPx()
    val w = width.toPx()
    clipRect {
        var x = -size.height
        while (x < size.width + g) {
            drawLine(color, Offset(x, size.height), Offset(x + size.height, 0f), w)
            x += g
        }
    }
}

/** Hairlines on the top and bottom edges only. */
fun Modifier.edgeLines(color: Color, width: Dp = 3.dp): Modifier = drawBehind {
    val w = width.toPx()
    drawRect(color, Offset.Zero, Size(size.width, w))
    drawRect(color, Offset(0f, size.height - w), Size(size.width, w))
}

/** Hover lifts up-left, press pushes down-right; returns the lift to feed into offset/shadow. */
@Composable
fun rememberLift(interaction: MutableInteractionSource, enabled: Boolean = true, hoverLift: Dp = 3.dp): Dp {
    val hovered by interaction.collectIsHoveredAsState()
    val pressed by interaction.collectIsPressedAsState()
    val target = when {
        !enabled -> 0.dp
        pressed  -> (-3).dp
        hovered  -> hoverLift
        else     -> 0.dp
    }
    val lift by animateDpAsState(target, spring(dampingRatio = 0.5f, stiffness = 700f))
    return lift
}

// ── Shapes ─────────────────────────────────────────────────────────

/** Comic-book starburst. */
class BurstShape(private val points: Int = 12, private val depth: Float = 0.22f) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val path = Path()
        val cx = size.width / 2
        val cy = size.height / 2
        val rOut = min(cx, cy)
        val rIn = rOut * (1 - depth)
        for (i in 0 until points * 2) {
            val r = if (i % 2 == 0) rOut else rIn
            val a = PI * i / points - PI / 2
            val x = cx + (r * cos(a)).toFloat()
            val y = cy + (r * sin(a)).toFloat()
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        path.close()
        return Outline.Generic(path)
    }
}

// ── Components ─────────────────────────────────────────────────────

@Composable
fun Squiggle(color: Color, modifier: Modifier = Modifier, amplitude: Dp = 4.dp, wavelength: Dp = 18.dp, stroke: Dp = 3.dp) {
    Canvas(modifier.fillMaxWidth().height(amplitude * 2 + stroke)) {
        val a = amplitude.toPx()
        val wl = wavelength.toPx()
        val mid = size.height / 2
        val path = Path().apply {
            moveTo(0f, mid)
            var x = 0f
            while (x < size.width) {
                quadraticTo(x + wl / 4, mid - a, x + wl / 2, mid)
                quadraticTo(x + 3 * wl / 4, mid + a, x + wl, mid)
                x += wl
            }
        }
        drawPath(path, color, style = Stroke(stroke.toPx(), cap = StrokeCap.Round))
    }
}

@Composable
fun Sticker(
    text: String,
    fill: Color,
    modifier: Modifier = Modifier,
    rotation: Float = -5f,
    fontSize: TextUnit = 11.sp,
    icon: ImageVector? = null,
) {
    val c = LocalAppColors.current
    val shape = RoundedCornerShape(50)
    Row(
        modifier = modifier.rotate(rotation)
            .block(fill, c.Ink, c.Shadow, shape, shadowOffset = 3.dp, borderWidth = 2.dp)
            .padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        if (icon != null) Icon(icon, null, tint = c.OnBright, modifier = Modifier.size(12.dp))
        Text(
            text.uppercase(), fontFamily = LocalDisplayFont.current, fontWeight = FontWeight.ExtraBold,
            fontSize = fontSize, letterSpacing = 0.8.sp, color = c.OnBright, maxLines = 1
        )
    }
}

/** Count inside a spiky burst. */
@Composable
fun BurstBadge(count: Int, fill: Color, size: Dp = 34.dp, rotation: Float = 0f) {
    val c = LocalAppColors.current
    val shape = remember { BurstShape(points = 11, depth = 0.2f) }
    Box(
        modifier = Modifier.size(size).rotate(rotation)
            .hardShadow(c.Shadow, 2.dp, shape).background(fill, shape).border(2.dp, c.Ink, shape),
        contentAlignment = Alignment.Center
    ) {
        Text(
            "$count", fontFamily = LocalDisplayFont.current, fontWeight = FontWeight.ExtraBold,
            fontSize = (size.value * 0.36f).sp, color = c.OnBright, modifier = Modifier.rotate(-rotation)
        )
    }
}

@Composable
fun MaxButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    fill: Color = LocalAppColors.current.Primary,
    enabled: Boolean = true,
    loading: Boolean = false,
    icon: ImageVector? = null,
    compact: Boolean = false,
) {
    val c = LocalAppColors.current
    val interaction = remember { MutableInteractionSource() }
    val lift = rememberLift(interaction, enabled)
    val shape = RoundedCornerShape(if (compact) 12.dp else 16.dp)
    val bg = if (enabled || loading) fill else c.SurfaceVariant
    val fg = if (enabled || loading) c.OnBright else c.TextMuted
    Row(
        modifier = modifier
            .offset(-lift, -lift)
            .block(bg, c.Ink, c.Shadow, shape, shadowOffset = (if (compact) 4.dp else 5.dp) + lift)
            .hoverable(interaction)
            .clickable(interactionSource = interaction, indication = null, enabled = enabled) { onClick() }
            .padding(horizontal = if (compact) 14.dp else 22.dp, vertical = if (compact) 8.dp else 13.dp),
        horizontalArrangement = Arrangement.spacedBy(9.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (loading) MiniBurstSpinner(fg)
        else if (icon != null) Icon(icon, null, tint = fg, modifier = Modifier.size(if (compact) 14.dp else 16.dp))
        Text(
            text.uppercase(), fontFamily = LocalDisplayFont.current, fontWeight = FontWeight.ExtraBold,
            fontSize = if (compact) 12.sp else 14.sp, letterSpacing = 1.sp, color = fg, maxLines = 1
        )
    }
}

/** Square icon-only button with the same lift behaviour. */
@Composable
fun MaxIconButton(
    icon: ImageVector,
    onClick: () -> Unit,
    fill: Color,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    size: Dp = 32.dp,
    iconTint: Color = LocalAppColors.current.OnBright,
) {
    val c = LocalAppColors.current
    val interaction = remember { MutableInteractionSource() }
    val lift = rememberLift(interaction, enabled, hoverLift = 2.dp)
    val shape = RoundedCornerShape(10.dp)
    Box(
        modifier = modifier
            .offset(-lift, -lift)
            .size(size)
            .block(fill, c.Ink, c.Shadow, shape, shadowOffset = 3.dp + lift, borderWidth = 2.dp)
            .hoverable(interaction)
            .clickable(interactionSource = interaction, indication = null, enabled = enabled) { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, null, tint = if (enabled) iconTint else c.TextMuted, modifier = Modifier.size(size * 0.48f))
    }
}

@Composable
fun MiniBurstSpinner(color: Color, size: Dp = 16.dp) {
    val t = rememberInfiniteTransition(label = "spin")
    val angle by t.animateFloat(0f, 360f, infiniteRepeatable(tween(900, easing = LinearEasing)), label = "angle")
    val shape = remember { BurstShape(points = 8, depth = 0.4f) }
    Box(Modifier.size(size).rotate(angle).background(color, shape))
}

/** Bouncing, spinning confetti blocks. */
@Composable
fun MaxLoader(label: String, modifier: Modifier = Modifier) {
    val c = LocalAppColors.current
    val t = rememberInfiniteTransition(label = "loader")
    val phase by t.animateFloat(0f, 1f, infiniteRepeatable(tween(1100, easing = LinearEasing)), label = "phase")
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(26.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.Bottom) {
                listOf(HotPink, Sunshine, Aqua, Grape, Tangerine).forEachIndexed { i, col ->
                    val p = (phase + i * 0.16f) % 1f
                    val bounce = abs(sin(p * PI)).toFloat()
                    val shape = if (i % 2 == 0) RoundedCornerShape(6.dp) else RoundedCornerShape(50)
                    Box(
                        Modifier.height(64.dp).width(26.dp),
                        contentAlignment = Alignment.BottomCenter
                    ) {
                        Box(
                            Modifier.offset(y = (-34 * bounce).dp).rotate(p * 360f).size(24.dp)
                                .block(col, c.Ink, c.Shadow, shape, shadowOffset = 3.dp, borderWidth = 3.dp)
                        )
                    }
                }
            }
            Text(
                label.uppercase(), fontFamily = LocalDisplayFont.current, fontWeight = FontWeight.ExtraBold,
                fontSize = 15.sp, letterSpacing = 2.sp, color = c.TextPrimary
            )
        }
    }
}

@Composable
fun MaxTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    mono: Boolean = false,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    leading: (@Composable () -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    val c = LocalAppColors.current
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val shadow by animateColorAsState(if (focused) (if (c.isDark) Sunshine else HotPink) else c.Shadow, tween(150))
    val offset by animateDpAsState(if (focused) 6.dp else 4.dp, tween(150))
    val shape = RoundedCornerShape(14.dp)
    val font = if (mono) FontFamily.Monospace else LocalBodyFont.current
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.fillMaxWidth().block(c.Surface, c.Ink, shadow, shape, shadowOffset = offset),
        enabled = enabled,
        singleLine = true,
        textStyle = TextStyle(fontFamily = font, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = c.TextPrimary),
        cursorBrush = SolidColor(c.Primary),
        visualTransformation = visualTransformation,
        interactionSource = interaction,
        decorationBox = { inner ->
            Row(
                Modifier.padding(horizontal = 14.dp, vertical = 13.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                leading?.invoke()
                Box(Modifier.weight(1f)) {
                    if (value.isEmpty()) Text(placeholder, fontFamily = font, fontSize = 13.sp, color = c.TextMuted, maxLines = 1)
                    inner()
                }
                trailing?.invoke()
            }
        }
    )
}

/** Endless scrolling ticker band. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MarqueeBand(items: List<String>, fill: Color, modifier: Modifier = Modifier) {
    val c = LocalAppColors.current
    val line = remember(items) { List(6) { items.joinToString("  ✦  ") }.joinToString("  ✦  ") + "  ✦  " }
    Box(modifier.fillMaxWidth().background(fill).edgeLines(c.Ink, 3.dp).padding(vertical = 9.dp)) {
        Text(
            line, maxLines = 1, fontFamily = LocalDisplayFont.current, fontWeight = FontWeight.ExtraBold,
            fontSize = 13.sp, letterSpacing = 1.5.sp, color = c.OnBright,
            modifier = Modifier.basicMarquee(iterations = Int.MAX_VALUE, repeatDelayMillis = 0, velocity = 55.dp)
        )
    }
}

/** Loud callout block for errors and warnings. */
@Composable
fun MaxNotice(icon: ImageVector, text: String, fill: Color, modifier: Modifier = Modifier) {
    val c = LocalAppColors.current
    Row(
        modifier = modifier.fillMaxWidth()
            .block(fill, c.Ink, c.Shadow, RoundedCornerShape(14.dp), shadowOffset = 4.dp)
            .stripes(Color.White.copy(alpha = 0.18f), gap = 18.dp, width = 6.dp)
            .padding(14.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = c.OnBright, modifier = Modifier.size(18.dp))
        Text(text, fontFamily = LocalBodyFont.current, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = c.OnBright)
    }
}

/** Tilted single-letter tile. */
@Composable
fun Monogram(letter: String, fill: Color, size: Dp = 44.dp, rotation: Float = -6f) {
    val c = LocalAppColors.current
    Box(
        Modifier.size(size).rotate(rotation)
            .block(fill, c.Ink, c.Shadow, RoundedCornerShape(size * 0.28f), shadowOffset = 3.dp, borderWidth = 3.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            letter.uppercase(), fontFamily = LocalDisplayFont.current, fontWeight = FontWeight.ExtraBold,
            fontSize = (size.value * 0.48f).sp, color = c.OnBright, modifier = Modifier.rotate(-rotation)
        )
    }
}
