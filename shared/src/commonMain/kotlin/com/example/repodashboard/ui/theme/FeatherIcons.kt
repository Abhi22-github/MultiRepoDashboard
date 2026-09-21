package com.example.repodashboard.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathBuilder
import androidx.compose.ui.graphics.vector.PathData
import androidx.compose.ui.unit.dp

/**
 * Hand-built subset of Feather Icons (https://feathericons.com, MIT).
 * Stroke-based, 24x24, round caps/joins — recolored by Icon's tint.
 */
object FeatherIcons {

    private fun stroke(name: String, block: PathBuilder.() -> Unit): ImageVector =
        ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f).apply {
            addPath(
                pathData = PathData { block() },
                stroke = SolidColor(Color(0xFF000000)),
                strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            )
        }.build()

    private fun filled(name: String, block: PathBuilder.() -> Unit): ImageVector =
        ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f).apply {
            addPath(pathData = PathData { block() }, fill = SolidColor(Color(0xFF000000)))
        }.build()

    private fun PathBuilder.circleP(cx: Float, cy: Float, r: Float) {
        moveTo(cx - r, cy)
        arcTo(r, r, 0f, true, true, cx + r, cy)
        arcTo(r, r, 0f, true, true, cx - r, cy)
    }
    private fun PathBuilder.lineP(x1: Float, y1: Float, x2: Float, y2: Float) { moveTo(x1, y1); lineTo(x2, y2) }
    private fun PathBuilder.rectP(x: Float, y: Float, w: Float, h: Float) {
        moveTo(x, y); lineTo(x + w, y); lineTo(x + w, y + h); lineTo(x, y + h); close()
    }

    val GitBranch: ImageVector = stroke("git-branch") {
        lineP(6f, 3f, 6f, 15f)
        circleP(18f, 6f, 3f)
        circleP(6f, 18f, 3f)
        moveTo(18f, 9f); arcToRelative(9f, 9f, 0f, false, true, -9f, 9f)
    }

    val Grid: ImageVector = stroke("grid") {
        rectP(3f, 3f, 7f, 7f); rectP(14f, 3f, 7f, 7f); rectP(14f, 14f, 7f, 7f); rectP(3f, 14f, 7f, 7f)
    }

    val RefreshCw: ImageVector = stroke("refresh-cw") {
        moveTo(23f, 4f); lineTo(23f, 10f); lineTo(17f, 10f)
        moveTo(1f, 20f); lineTo(1f, 14f); lineTo(7f, 14f)
        moveTo(3.51f, 9f); arcToRelative(9f, 9f, 0f, false, true, 14.85f, -3.36f); lineTo(23f, 10f)
        moveTo(1f, 14f); lineToRelative(4.64f, 4.36f); arcTo(9f, 9f, 0f, false, false, 20.49f, 15f)
    }

    val Moon: ImageVector = stroke("moon") {
        moveTo(21f, 12.79f)
        arcTo(9f, 9f, 0f, true, true, 11.21f, 3f)
        arcTo(7f, 7f, 0f, false, false, 21f, 12.79f)
        close()
    }

    val Sun: ImageVector = stroke("sun") {
        circleP(12f, 12f, 5f)
        lineP(12f, 1f, 12f, 3f); lineP(12f, 21f, 12f, 23f)
        lineP(4.22f, 4.22f, 5.64f, 5.64f); lineP(18.36f, 18.36f, 19.78f, 19.78f)
        lineP(1f, 12f, 3f, 12f); lineP(21f, 12f, 23f, 12f)
        lineP(4.22f, 19.78f, 5.64f, 18.36f); lineP(18.36f, 5.64f, 19.78f, 4.22f)
    }

    val Sliders: ImageVector = stroke("sliders") {
        lineP(4f, 21f, 4f, 14f); lineP(4f, 10f, 4f, 3f)
        lineP(12f, 21f, 12f, 12f); lineP(12f, 8f, 12f, 3f)
        lineP(20f, 21f, 20f, 16f); lineP(20f, 12f, 20f, 3f)
        lineP(1f, 14f, 7f, 14f); lineP(9f, 8f, 15f, 8f); lineP(17f, 16f, 23f, 16f)
    }

    val LogOut: ImageVector = stroke("log-out") {
        moveTo(9f, 21f); horizontalLineTo(5f); arcToRelative(2f, 2f, 0f, false, true, -2f, -2f)
        verticalLineTo(5f); arcToRelative(2f, 2f, 0f, false, true, 2f, -2f); horizontalLineToRelative(4f)
        moveTo(16f, 17f); lineTo(21f, 12f); lineTo(16f, 7f)
        lineP(21f, 12f, 9f, 12f)
    }

    val Star: ImageVector = stroke("star") {
        moveTo(12f, 2f); lineTo(15.09f, 8.26f); lineTo(22f, 9.27f); lineTo(17f, 14.14f)
        lineTo(18.18f, 21.02f); lineTo(12f, 17.77f); lineTo(5.82f, 21.02f); lineTo(7f, 14.14f)
        lineTo(2f, 9.27f); lineTo(8.91f, 8.26f); close()
    }

    val StarFilled: ImageVector = filled("star-filled") {
        moveTo(12f, 2f); lineTo(15.09f, 8.26f); lineTo(22f, 9.27f); lineTo(17f, 14.14f)
        lineTo(18.18f, 21.02f); lineTo(12f, 17.77f); lineTo(5.82f, 21.02f); lineTo(7f, 14.14f)
        lineTo(2f, 9.27f); lineTo(8.91f, 8.26f); close()
    }

    val ExternalLink: ImageVector = stroke("external-link") {
        moveTo(18f, 13f); verticalLineToRelative(6f); arcToRelative(2f, 2f, 0f, false, true, -2f, 2f)
        horizontalLineTo(5f); arcToRelative(2f, 2f, 0f, false, true, -2f, -2f); verticalLineTo(8f)
        arcToRelative(2f, 2f, 0f, false, true, 2f, -2f); horizontalLineToRelative(6f)
        moveTo(15f, 3f); lineTo(21f, 3f); lineTo(21f, 9f)
        lineP(10f, 14f, 21f, 3f)
    }

    val CheckCircle: ImageVector = stroke("check-circle") {
        moveTo(22f, 11.08f); verticalLineTo(12f); arcToRelative(10f, 10f, 0f, true, true, -5.93f, -9.14f)
        moveTo(22f, 4f); lineTo(12f, 14.01f); lineTo(9f, 11.01f)
    }

    val Edit: ImageVector = stroke("edit-3") {
        moveTo(12f, 20f); horizontalLineToRelative(9f)
        moveTo(16.5f, 3.5f); arcToRelative(2.121f, 2.121f, 0f, false, true, 3f, 3f)
        lineTo(7f, 19f); lineToRelative(-4f, 1f); lineToRelative(1f, -4f); lineTo(16.5f, 3.5f); close()
    }

    val AlertTriangle: ImageVector = stroke("alert-triangle") {
        moveTo(10.29f, 3.86f); lineTo(1.82f, 18f); arcToRelative(2f, 2f, 0f, false, false, 1.71f, 3f)
        horizontalLineToRelative(16.94f); arcToRelative(2f, 2f, 0f, false, false, 1.71f, -3f)
        lineTo(13.71f, 3.86f); arcToRelative(2f, 2f, 0f, false, false, -3.42f, 0f); close()
        lineP(12f, 9f, 12f, 13f); lineP(12f, 17f, 12.01f, 17f)
    }

    val ChevronRight: ImageVector = stroke("chevron-right") { moveTo(9f, 18f); lineTo(15f, 12f); lineTo(9f, 6f) }
    val ChevronDown: ImageVector  = stroke("chevron-down")  { moveTo(6f, 9f); lineTo(12f, 15f); lineTo(18f, 9f) }

    val Search: ImageVector = stroke("search") {
        circleP(11f, 11f, 8f); lineP(21f, 21f, 16.65f, 16.65f)
    }

    val Lock: ImageVector = stroke("lock") {
        rectP(3f, 11f, 18f, 11f)
        moveTo(7f, 11f); verticalLineTo(7f); arcToRelative(5f, 5f, 0f, false, true, 10f, 0f); verticalLineToRelative(4f)
    }

    val Tag: ImageVector = stroke("tag") {
        moveTo(20.59f, 13.41f); lineToRelative(-7.17f, 7.17f)
        arcToRelative(2f, 2f, 0f, false, true, -2.83f, 0f); lineTo(2f, 12f); verticalLineTo(2f)
        horizontalLineToRelative(10f); lineToRelative(8.59f, 8.59f)
        arcToRelative(2f, 2f, 0f, false, true, 0f, 2.82f); close()
        lineP(7f, 7f, 7.01f, 7f)
    }

    val GitPullRequest: ImageVector = stroke("git-pull-request") {
        circleP(18f, 18f, 3f); circleP(6f, 6f, 3f)
        moveTo(13f, 6f); horizontalLineToRelative(3f); arcToRelative(2f, 2f, 0f, false, true, 2f, 2f); verticalLineToRelative(7f)
        lineP(6f, 9f, 6f, 21f)
    }

    val Clock: ImageVector = stroke("clock") {
        circleP(12f, 12f, 10f); moveTo(12f, 6f); lineTo(12f, 12f); lineTo(16f, 14f)
    }

    val Package: ImageVector = stroke("package") {
        lineP(16.5f, 9.4f, 7.5f, 4.21f)
        moveTo(21f, 16f); verticalLineTo(8f); arcToRelative(2f, 2f, 0f, false, false, -1f, -1.73f)
        lineToRelative(-7f, -4f); arcToRelative(2f, 2f, 0f, false, false, -2f, 0f); lineToRelative(-7f, 4f)
        arcTo(2f, 2f, 0f, false, false, 3f, 8f); verticalLineToRelative(8f)
        arcToRelative(2f, 2f, 0f, false, false, 1f, 1.73f); lineToRelative(7f, 4f)
        arcToRelative(2f, 2f, 0f, false, false, 2f, 0f); lineToRelative(7f, -4f)
        arcTo(2f, 2f, 0f, false, false, 21f, 16f); close()
        moveTo(3.27f, 6.96f); lineTo(12f, 12.01f); lineTo(20.73f, 6.96f)
        lineP(12f, 22.08f, 12f, 12f)
    }
}
