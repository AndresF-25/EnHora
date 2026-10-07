package com.enhora.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** Flor estática: tres capas de pétalos blancos y un centro con puntos. La pantalla de inicio usa [DaySky]. */
@Composable
fun Flower(modifier: Modifier = Modifier, size: Dp = 260.dp) {
    Canvas(modifier.size(size)) {
        val r = this.size.minDimension / 2f
        petalRing(center, count = 24, length = r, width = r * 0.17f, color = Color.White.copy(alpha = 0.28f), offsetDeg = 0f)
        petalRing(center, count = 18, length = r * 0.82f, width = r * 0.18f, color = Color.White.copy(alpha = 0.5f), offsetDeg = 10f)
        petalRing(center, count = 14, length = r * 0.62f, width = r * 0.17f, color = Color.White.copy(alpha = 0.92f), offsetDeg = 0f)

        drawCircle(Color(0xFFFFF1F4), radius = r * 0.2f)
        val dotRing = r * 0.12f
        repeat(10) { i ->
            val a = 2 * PI * i / 10
            drawCircle(
                Color(0xFFF7B8C6),
                radius = r * 0.016f,
                center = center + Offset((cos(a) * dotRing).toFloat(), (sin(a) * dotRing).toFloat()),
            )
        }
        drawCircle(Color(0xFFF7B8C6), radius = r * 0.02f)
    }
}

/** Un anillo de [count] pétalos alrededor de [center]; [offsetDeg] lo gira (así se animan los anillos). */
internal fun DrawScope.petalRing(center: Offset, count: Int, length: Float, width: Float, color: Color, offsetDeg: Float) {
    val c = center
    val petal = Path().apply {
        moveTo(c.x, c.y)
        cubicTo(c.x - width, c.y - length * 0.35f, c.x - width * 0.9f, c.y - length * 0.88f, c.x, c.y - length)
        cubicTo(c.x + width * 0.9f, c.y - length * 0.88f, c.x + width, c.y - length * 0.35f, c.x, c.y)
        close()
    }
    repeat(count) { i ->
        rotate(degrees = offsetDeg + 360f / count * i, pivot = c) {
            drawPath(petal, color)
        }
    }
}
