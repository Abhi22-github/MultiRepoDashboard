package com.example.repodashboard.ui.theme

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** The design's cubic-bezier(.2,.8,.2,1). */
val EaseOut: Easing = CubicBezierEasing(0.2f, 0.8f, 0.2f, 1f)

/** Set from the OS "reduce motion" preference; animations snap when true. */
val LocalReducedMotion = staticCompositionLocalOf { false }

@Composable
fun <T> motion(ms: Int, easing: Easing = EaseOut): FiniteAnimationSpec<T> =
    if (LocalReducedMotion.current) snap() else tween(ms, easing = easing)

/**
 * Plays an entrance (fade, plus optional slide / scale) whenever [key] changes.
 * Runs in the draw phase only: no recomposition or relayout per frame.
 * The first run can use [firstScale] instead of the slide (e.g. after a List / Board switch).
 */
@Composable
fun Modifier.entrance(
    key: Any?,
    ms: Int,
    offsetY: Dp = 0.dp,
    fromScale: Float = 1f,
    firstScale: Float? = null,
    origin: TransformOrigin = TransformOrigin.Center,
): Modifier {
    val reduced = LocalReducedMotion.current
    val p = remember { Animatable(if (reduced) 1f else 0f) }
    var runs by remember { mutableStateOf(0) }
    LaunchedEffect(key) {
        runs++
        if (reduced) { p.snapTo(1f); return@LaunchedEffect }
        p.snapTo(0f)
        p.animateTo(1f, tween(ms, easing = EaseOut))
    }
    val first = runs <= 1 && firstScale != null
    val dy = with(LocalDensity.current) { (if (first) 0.dp else offsetY).toPx() }
    val s0 = if (first) firstScale!! else fromScale
    return graphicsLayer {
        val v = p.value
        alpha = v
        translationY = (1f - v) * dy
        val sc = s0 + (1f - s0) * v
        scaleX = sc; scaleY = sc
        transformOrigin = origin
    }
}
