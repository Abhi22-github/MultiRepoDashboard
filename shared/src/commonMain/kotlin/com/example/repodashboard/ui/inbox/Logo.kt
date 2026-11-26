package com.example.repodashboard.ui.inbox

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.Dp

/** App mark (design logo 3): a branch with an amber tip on a blue tile. */
@Composable
fun AppLogo(size: Dp) {
    Canvas(Modifier.size(size).clearAndSetSemantics { }) {
        val k = this.size.width / 48f
        drawRoundRect(Color(0xFF2563EB), cornerRadius = CornerRadius(12f * k))
        val stroke = Stroke(3.5f * k, cap = StrokeCap.Round)
        val p = Path().apply {
            moveTo(16f * k, 14f * k); lineTo(16f * k, 34f * k)
            moveTo(16f * k, 22f * k)
            cubicTo(16f * k, 18f * k, 20f * k, 16f * k, 25f * k, 16f * k)
            lineTo(28f * k, 16f * k)
        }
        drawPath(p, Color.White, style = stroke)
        drawCircle(Color.White, 4f * k, Offset(16f * k, 14f * k))
        drawCircle(Color.White, 4f * k, Offset(16f * k, 34f * k))
        drawCircle(Color(0xFFFBBF24), 4.5f * k, Offset(32f * k, 16f * k))
    }
}
