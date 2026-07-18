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
 * 80s synth-pop widescreen city: blue/gold daylight, glass reflections,
 * skyline, soft clouds. Tears For Fears, Depeche Mode, A-ha.
 */
@Composable
fun CityReflectionScene(
    accentColor: Color,
    energy: Float,
    intensity: Float = 0.5f,
    modifier: Modifier = Modifier
) {
    val transition = rememberInfiniteTransition(label = "cityReflection")
    val cloudDrift by transition.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(20000, easing = LinearEasing), RepeatMode.Restart),
        label = "cloudDrift"
    )
    val lightShimmer by transition.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(6000, easing = LinearEasing), RepeatMode.Reverse),
        label = "lightShimmer"
    )

    val skyBlue = Color(0xFF4A7AA8)
    val skyGold = Color(0xFFD4B870)
    val glassBlue = Color(0xFF6A9AC0)
    val buildingDark = Color(0xFF2A3040)

    Canvas(modifier = modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height

        // Sky gradient — blue to gold
        val skyBands = listOf(
            Color(0xFF2A4A6A), Color(0xFF3A5A7A), Color(0xFF4A7AA8),
            Color(0xFF8AA0B8), Color(0xFFC0B890), Color(0xFFD4B870)
        )
        val bandH = h * 0.1f
        skyBands.forEachIndexed { i, color ->
            drawRect(
                color = color,
                topLeft = Offset(0f, i * bandH),
                size = androidx.compose.ui.geometry.Size(w, bandH + 1f)
            )
        }

        // Clouds — soft, drifting
        val cloudBaseX = (cloudDrift * w * 1.5f) % (w + 300f) - 150f
        for (i in 0 until 5) {
            val cx = cloudBaseX + i * w * 0.22f
            val cy = h * (0.06f + i * 0.04f)
            val cr = w * (0.08f + (i % 3) * 0.03f)
            drawCircle(
                color = Color.White.copy(alpha = 0.08f + (i % 3) * 0.02f),
                radius = cr,
                center = Offset(cx, cy)
            )
            drawCircle(
                color = Color.White.copy(alpha = 0.06f),
                radius = cr * 0.7f,
                center = Offset(cx + cr * 0.4f, cy - cr * 0.1f)
            )
        }

        // City skyline
        val horizonY = h * 0.48f
        val buildings = listOf(
            0.05f to 0.35f, 0.12f to 0.45f, 0.22f to 0.4f, 0.3f to 0.55f,
            0.38f to 0.6f, 0.46f to 0.5f, 0.54f to 0.65f, 0.62f to 0.52f,
            0.7f to 0.58f, 0.78f to 0.42f, 0.85f to 0.48f, 0.92f to 0.38f
        )
        buildings.forEach { (xRatio, heightRatio) ->
            val bh = h * heightRatio * 0.5f
            val bw = w * 0.07f
            val bx = xRatio * w
            val by = horizonY - bh
            drawRect(
                color = buildingDark,
                topLeft = Offset(bx, by),
                size = androidx.compose.ui.geometry.Size(bw, bh + (h - horizonY))
            )
            // Glass reflections on buildings
            val shimmer = lightShimmer * 0.5f + 0.5f
            for (row in 0 until (bh / 12f).toInt().coerceAtMost(15)) {
                for (col in 0 until (bw / 8f).toInt().coerceAtMost(4)) {
                    val winAlpha = if ((row + col) % 3 == 0) 0.15f * shimmer else 0.04f
                    val winColor = if ((row + col) % 4 == 0) skyGold else glassBlue
                    drawRect(
                        color = winColor.copy(alpha = winAlpha),
                        topLeft = Offset(bx + col * 8f + 2f, by + row * 12f + 2f),
                        size = androidx.compose.ui.geometry.Size(5f, 7f)
                    )
                }
            }
        }

        // Water/highway reflection surface below horizon
        drawRect(
            color = Color(0xFF1A2030),
            topLeft = Offset(0f, horizonY),
            size = androidx.compose.ui.geometry.Size(w, h - horizonY)
        )

        // Reflected light streaks on water
        for (i in 0 until 8) {
            val rx = w * (0.1f + i * 0.12f) + sin(lightShimmer * PI.toFloat() * 2f + i) * 3f
            val ry = horizonY + h * 0.05f + i * 8f
            val rAlpha = 0.03f + lightShimmer * 0.04f
            drawRect(
                color = skyGold.copy(alpha = rAlpha),
                topLeft = Offset(rx - 1f, ry),
                size = androidx.compose.ui.geometry.Size(3f, h * 0.12f)
            )
        }

        // Horizon line — golden
        drawRect(
            color = skyGold.copy(alpha = 0.3f + energy * 0.15f),
            topLeft = Offset(0f, horizonY - 2f),
            size = androidx.compose.ui.geometry.Size(w, 4f)
        )

        // Vignette
        drawRect(
            color = Color.Black.copy(alpha = 0.2f),
            topLeft = Offset.Zero,
            size = size
        )
    }
}
