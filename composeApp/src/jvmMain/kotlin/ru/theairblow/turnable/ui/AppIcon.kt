package ru.theairblow.turnable.ui

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.painter.Painter

/** Placeholder app icon: a ring that turns back on itself around a relay point. */
object AppIcon : Painter() {
    override val intrinsicSize: Size = Size(256f, 256f)

    override fun DrawScope.onDraw() {
        val unit = size.minDimension / 108f
        drawRoundRect(Color(0xFF3F4FC4), size = size, cornerRadius = CornerRadius(24f * unit))
        fun p(x: Float, y: Float) = Offset(x * unit, y * unit)
        drawArc(
            Color.White, startAngle = -90f, sweepAngle = 270f, useCenter = false,
            topLeft = p(34f, 34f), size = Size(40f * unit, 40f * unit), style = Stroke(7f * unit, cap = StrokeCap.Round),
        )
        drawPath(Path().apply { moveTo(27f * unit, 54f * unit); lineTo(41f * unit, 54f * unit); lineTo(34f * unit, 42f * unit); close() }, Color.White)
        drawCircle(Color.White, radius = 6f * unit, center = p(54f, 54f))
    }
}
