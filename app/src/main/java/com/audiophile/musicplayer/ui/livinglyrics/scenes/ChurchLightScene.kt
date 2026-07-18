package com.audiophile.musicplayer.ui.livinglyrics.scenes

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope

@Composable
fun ChurchLightScene(
    accentColor: Color,
    energy: Float,
    modifier: Modifier = Modifier
) {
    val transition = rememberInfiniteTransition(label = "churchLight")
    val dustDrift by transition.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(15000, easing = LinearEasing), RepeatMode.Restart),
        label = "dustDrift"
    )
    val lightPulse by transition.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(4000, easing = LinearEasing), RepeatMode.Reverse),
        label = "lightPulse"
    )

    val wallColor = Color(0xFF2A2520)
    val floorColor = Color(0xFF1E1A16)
    val lightColor = Color(0xFFFFF0D0)
    val warmLight = Color(0xFFFFDCA0)

    Canvas(modifier = modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height

        drawRect(color = Color(0xFF1A1612), topLeft = Offset.Zero, size = size)

        val archHeight = h * 0.6f
        val archWidth = w * 0.14f
        val archGap = w * 0.08f
        val totalWidth = archWidth * 3f + archGap * 2f
        val startX = (w - totalWidth) / 2f

        for (i in 0 until 3) {
            val cx = startX + i * (archWidth + archGap) + archWidth / 2f
            drawArch(cx, archHeight, archWidth * 0.5f, wallColor)
        }

        val pulseAlpha = 0.35f + lightPulse * 0.35f
        val lightWidth = archWidth * 0.6f
        for (i in 0 until 3) {
            val cx = startX + i * (archWidth + archGap) + archWidth / 2f
            val beamLeft = cx - lightWidth / 2f
            val beamRight = cx + lightWidth / 2f
            val beamTop = archHeight * 0.3f
            val beamBottom = h

            val steps = 20
            for (j in 0 until steps) {
                val t = j.toFloat() / steps
                val alpha = lightColor.copy(alpha = (1f - t) * 0.15f * pulseAlpha)
                val by = beamTop + (beamBottom - beamTop) * t
                val bw = lightWidth * (1f - t * 0.5f)
                val bx = cx - bw / 2f
                drawRect(
                    color = alpha,
                    topLeft = Offset(bx, by),
                    size = androidx.compose.ui.geometry.Size(bw, (beamBottom - beamTop) / steps + 2f)
                )
            }
        }

        drawRect(
            color = floorColor,
            topLeft = Offset(0f, h * 0.55f),
            size = androidx.compose.ui.geometry.Size(w, h * 0.45f)
        )
        drawRect(
            color = wallColor.copy(alpha = 0.5f),
            topLeft = Offset(0f, 0f),
            size = androidx.compose.ui.geometry.Size(w, h * 0.55f)
        )

        drawRect(
            color = Color(0xFF3D3528),
            topLeft = Offset(0f, h * 0.55f),
            size = androidx.compose.ui.geometry.Size(w, 2f)
        )

        for (i in 0 until 30) {
            val dx = ((dustDrift * w + i * 97f) % (w + 40f)) - 20f
            val dy = ((dustDrift * h * 0.5f + i * 53f) % (h * 0.6f)) + h * 0.1f
            val size = 1.5f + (i % 5) * 1f
            val alpha = 0.08f + (i % 4) * 0.04f + lightPulse * 0.06f
            drawCircle(
                color = warmLight.copy(alpha = alpha.coerceIn(0f, 0.35f)),
                radius = size,
                center = Offset(dx, dy)
            )
        }

        drawRect(
            color = Color.Black.copy(alpha = 0.3f),
            topLeft = Offset.Zero,
            size = size
        )
    }
}

private fun DrawScope.drawArch(cx: Float, archHeight: Float, radius: Float, color: Color) {
    val left = cx - radius
    val right = cx + radius
    val top = archHeight * 0.15f
    val bottom = archHeight

    drawRect(color = color, topLeft = Offset(left, top), size = androidx.compose.ui.geometry.Size(radius * 0.12f, bottom - top))
    drawRect(color = color, topLeft = Offset(right - radius * 0.12f, top), size = androidx.compose.ui.geometry.Size(radius * 0.12f, bottom - top))

    val segments = 20
    for (i in 0 until segments) {
        val angle1 = Math.PI.toFloat() * (1f - i.toFloat() / segments)
        val angle2 = Math.PI.toFloat() * (1f - (i + 1).toFloat() / segments)
        val x1 = cx + kotlin.math.cos(angle1) * radius
        val y1 = top + radius - kotlin.math.sin(angle1) * radius
        val x2 = cx + kotlin.math.cos(angle2) * radius
        val y2 = top + radius - kotlin.math.sin(angle2) * radius

        val archColor = color.copy(alpha = 1f)
        val innerX1 = cx + kotlin.math.cos(angle1) * (radius - 4f)
        val innerY1 = top + radius - kotlin.math.sin(angle1) * (radius - 4f)
        val innerX2 = cx + kotlin.math.cos(angle2) * (radius - 4f)
        val innerY2 = top + radius - kotlin.math.sin(angle2) * (radius - 4f)

        val path = androidx.compose.ui.graphics.Path().apply {
            moveTo(x1, y1)
            lineTo(x2, y2)
            lineTo(innerX2, innerY2)
            lineTo(innerX1, innerY1)
            close()
        }
        drawPath(path, archColor)
    }

    for (i in 0 until 8) {
        val angle = Math.PI.toFloat() * (0.5f + i.toFloat() / 8f * 0.5f)
        val sx = cx + kotlin.math.cos(angle) * radius
        val sy = top + radius - kotlin.math.sin(angle) * radius
        val ex = cx + kotlin.math.cos(angle) * (radius + 20f)
        val ey = top + radius - kotlin.math.sin(angle) * (radius + 20f)
        drawLine(
            color = color.copy(alpha = 0.3f),
            start = Offset(sx, sy),
            end = Offset(ex, ey),
            strokeWidth = 1.5f
        )
    }
}
