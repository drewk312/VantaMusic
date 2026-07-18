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
import kotlin.math.sin
import kotlin.math.PI

/**
 * Night highway with amber headlights, neon roadside signs, dust,
 * and stage-like energy. ZZ Top, AC/DC, classic rock road feel.
 */
@Composable
fun NightHighwayStageScene(
    accentColor: Color,
    energy: Float,
    intensity: Float = 0.5f,
    modifier: Modifier = Modifier
) {
    val transition = rememberInfiniteTransition(label = "nightHighway")
    val roadScroll by transition.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(5000, easing = LinearEasing), RepeatMode.Restart),
        label = "roadScroll"
    )
    val neonPulse by transition.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(3000, easing = LinearEasing), RepeatMode.Reverse),
        label = "neonPulse"
    )
    val dustDrift by transition.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(9000, easing = LinearEasing), RepeatMode.Restart),
        label = "dustDrift"
    )

    val nightSky = Color(0xFF0A0A18)
    val amber = Color(0xFFFFAA40)
    val neonRed = Color(0xFFFF3040)
    val asphalt = Color(0xFF1A1A1A)
    val lineYellow = Color(0xFFE8C840)

    Canvas(modifier = modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height

        // Dark night sky
        drawRect(color = nightSky, topLeft = Offset.Zero, size = size)

        // Stars
        for (i in 0 until 30) {
            val sx = (i * 137f) % w
            val sy = (i * 73f) % (h * 0.35f)
            val starAlpha = 0.15f + (i % 5) * 0.08f
            drawCircle(
                color = Color.White.copy(alpha = starAlpha),
                radius = 1f + (i % 3) * 0.5f,
                center = Offset(sx, sy)
            )
        }

        // Horizon glow — amber from headlights/city
        val horizonY = h * 0.4f
        drawRect(
            color = amber.copy(alpha = 0.04f + energy * 0.04f),
            topLeft = Offset(0f, horizonY - h * 0.08f),
            size = androidx.compose.ui.geometry.Size(w, h * 0.16f)
        )

        // Road
        val roadTop = horizonY
        drawRect(
            color = asphalt,
            topLeft = Offset(0f, roadTop),
            size = androidx.compose.ui.geometry.Size(w, h - roadTop)
        )

        // Center lane lines — scrolling
        val lineDashH = (h - roadTop) * 0.06f
        val totalDash = lineDashH * 2.5f
        val scrollOffset = (roadScroll * (h - roadTop)) % totalDash
        var y = roadTop + scrollOffset
        while (y < h) {
            drawRect(
                color = lineYellow.copy(alpha = 0.6f),
                topLeft = Offset(w / 2f - 2f, y),
                size = androidx.compose.ui.geometry.Size(4f, lineDashH)
            )
            y += totalDash
        }

        // Edge lines
        drawRect(
            color = Color.White.copy(alpha = 0.2f),
            topLeft = Offset(w * 0.15f, roadTop),
            size = androidx.compose.ui.geometry.Size(2f, h - roadTop)
        )
        drawRect(
            color = Color.White.copy(alpha = 0.2f),
            topLeft = Offset(w * 0.85f, roadTop),
            size = androidx.compose.ui.geometry.Size(2f, h - roadTop)
        )

        // Headlights from behind — two amber cones
        val headlightAlpha = 0.08f + intensity * 0.1f + energy * 0.05f
        for (i in 0 until 10) {
            val t = i / 10f
            val coneW = w * 0.05f + t * w * 0.15f
            val coneY = h - (h - roadTop) * t
            drawRect(
                color = amber.copy(alpha = headlightAlpha * (1f - t * 0.5f)),
                topLeft = Offset(w * 0.35f - coneW / 2f, coneY),
                size = androidx.compose.ui.geometry.Size(coneW, (h - roadTop) / 10f + 2f)
            )
            drawRect(
                color = amber.copy(alpha = headlightAlpha * (1f - t * 0.5f)),
                topLeft = Offset(w * 0.65f - coneW / 2f, coneY),
                size = androidx.compose.ui.geometry.Size(coneW, (h - roadTop) / 10f + 2f)
            )
        }

        // Neon roadside sign — pulsing red
        val neonX = w * 0.82f
        val neonY = horizonY + h * 0.05f
        val neonAlpha = 0.3f + neonPulse * 0.3f
        drawCircle(
            color = neonRed.copy(alpha = neonAlpha * 0.15f),
            radius = w * 0.06f,
            center = Offset(neonX, neonY)
        )
        drawCircle(
            color = neonRed.copy(alpha = neonAlpha * 0.5f),
            radius = w * 0.015f,
            center = Offset(neonX, neonY)
        )

        // Second neon sign — blue
        val neonX2 = w * 0.12f
        val neonY2 = horizonY + h * 0.08f
        drawCircle(
            color = Color(0xFF4080FF).copy(alpha = (0.2f + neonPulse * 0.2f) * 0.15f),
            radius = w * 0.05f,
            center = Offset(neonX2, neonY2)
        )
        drawCircle(
            color = Color(0xFF4080FF).copy(alpha = (0.2f + neonPulse * 0.2f) * 0.4f),
            radius = w * 0.012f,
            center = Offset(neonX2, neonY2)
        )

        // Dust particles
        for (i in 0 until 15) {
            val dx = ((dustDrift * w * 1.5f + i * 89f) % (w * 1.2f)) - w * 0.1f
            val dy = roadTop + ((dustDrift * (h - roadTop) + i * 53f) % (h - roadTop))
            val dustAlpha = (0.06f + (i % 4) * 0.03f) * intensity
            drawCircle(
                color = amber.copy(alpha = dustAlpha.coerceIn(0f, 0.2f)),
                radius = 2f + (i % 4) * 1.5f,
                center = Offset(dx, dy)
            )
        }

        // Final vignette
        drawRect(
            color = Color.Black.copy(alpha = 0.22f),
            topLeft = Offset.Zero,
            size = size
        )
    }
}
