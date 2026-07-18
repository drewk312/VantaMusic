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
 * Urban night pulse: neon-lit streets, bass-reactive glow, car streaks,
 * concrete skyline. Rap, trap, hip-hop.
 */
@Composable
fun NightCityPulseScene(
    accentColor: Color,
    energy: Float,
    intensity: Float = 0.5f,
    modifier: Modifier = Modifier
) {
    val transition = rememberInfiniteTransition(label = "nightCityPulse")
    val bassPulse by transition.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1200, easing = LinearEasing), RepeatMode.Reverse),
        label = "bassPulse"
    )
    val carStreak by transition.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(4000, easing = LinearEasing), RepeatMode.Restart),
        label = "carStreak"
    )
    val neonFlicker by transition.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(2500, easing = LinearEasing), RepeatMode.Reverse),
        label = "neonFlicker"
    )

    val deepBlack = Color(0xFF080810)
    val neonPurple = Color(0xFF8020E0)
    val neonCyan = Color(0xFF20D0E0)
    val neonPink = Color(0xFFE04080)
    val streetOrange = Color(0xFFFF8030)

    Canvas(modifier = modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height

        // Black sky
        drawRect(color = deepBlack, topLeft = Offset.Zero, size = size)

        // Distant skyline glow
        drawCircle(
            color = neonPurple.copy(alpha = 0.04f + bassPulse * 0.03f * intensity),
            radius = w * 0.5f,
            center = Offset(w * 0.5f, h * 0.3f)
        )

        // Buildings — blocky silhouettes
        val skyline = listOf(
            0.0f to 0.5f, 0.08f to 0.65f, 0.16f to 0.55f, 0.24f to 0.72f,
            0.32f to 0.6f, 0.4f to 0.78f, 0.48f to 0.58f, 0.56f to 0.7f,
            0.64f to 0.5f, 0.72f to 0.68f, 0.8f to 0.55f, 0.88f to 0.62f
        )
        skyline.forEach { (xRatio, heightRatio) ->
            val bh = h * heightRatio * 0.55f
            val bw = w * 0.08f
            drawRect(
                color = Color(0xFF0E0E18),
                topLeft = Offset(xRatio * w, h * 0.4f - bh + h * 0.15f),
                size = androidx.compose.ui.geometry.Size(bw, bh + h * 0.5f)
            )
        }

        // Street level
        val streetY = h * 0.72f
        drawRect(
            color = Color(0xFF0A0A12),
            topLeft = Offset(0f, streetY),
            size = androidx.compose.ui.geometry.Size(w, h - streetY)
        )

        // Streetlights
        for (i in 0 until 5) {
            val lx = w * (0.1f + i * 0.2f)
            val ly = streetY - 5f
            drawCircle(
                color = streetOrange.copy(alpha = 0.15f + neonFlicker * 0.1f),
                radius = w * 0.04f,
                center = Offset(lx, ly)
            )
            drawCircle(
                color = streetOrange.copy(alpha = 0.4f + neonFlicker * 0.2f),
                radius = 3f,
                center = Offset(lx, ly)
            )
            // Light cone down
            drawRect(
                color = streetOrange.copy(alpha = 0.03f),
                topLeft = Offset(lx - w * 0.03f, ly),
                size = androidx.compose.ui.geometry.Size(w * 0.06f, h - ly)
            )
        }

        // Car light streaks
        val streakY = streetY + (h - streetY) * 0.4f
        val streakX = (carStreak * w * 1.5f) % (w + 100f) - 50f
        drawRect(
            color = Color.White.copy(alpha = 0.15f),
            topLeft = Offset(streakX, streakY),
            size = androidx.compose.ui.geometry.Size(w * 0.12f, 2f)
        )
        drawRect(
            color = Color.Red.copy(alpha = 0.1f),
            topLeft = Offset(streakX + w * 0.15f, streakY + 8f),
            size = androidx.compose.ui.geometry.Size(w * 0.08f, 2f)
        )

        // Neon accent glow — bass reactive
        val pulseAlpha = bassPulse * intensity * 0.06f
        drawCircle(
            color = neonPink.copy(alpha = pulseAlpha),
            radius = w * 0.3f,
            center = Offset(w * 0.3f, streetY)
        )
        drawCircle(
            color = neonCyan.copy(alpha = pulseAlpha * 0.8f),
            radius = w * 0.25f,
            center = Offset(w * 0.7f, streetY)
        )

        // Wet street reflections
        for (i in 0 until 12) {
            val rx = w * (i / 12f) + sin(neonFlicker * PI.toFloat() + i) * 2f
            val ry = streetY + 3f + (i % 3) * 5f
            val rColor = when (i % 3) {
                0 -> neonPink
                1 -> neonCyan
                else -> streetOrange
            }
            drawRect(
                color = rColor.copy(alpha = 0.04f + neonFlicker * 0.03f),
                topLeft = Offset(rx, ry),
                size = androidx.compose.ui.geometry.Size(w * 0.06f, h * 0.08f)
            )
        }

        // Vignette
        drawRect(
            color = Color.Black.copy(alpha = 0.2f),
            topLeft = Offset.Zero,
            size = size
        )
    }
}
