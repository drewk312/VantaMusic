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
 * Country / folk open field: warm sunset field, porch light,
 * dirt road, fence, fireflies. Johnny Cash, Willie Nelson, Zach Bryan.
 */
@Composable
fun OpenFieldRoadScene(
    accentColor: Color,
    energy: Float,
    intensity: Float = 0.5f,
    modifier: Modifier = Modifier
) {
    val transition = rememberInfiniteTransition(label = "openFieldRoad")
    val grassSway by transition.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(8000, easing = LinearEasing), RepeatMode.Reverse),
        label = "grassSway"
    )
    val fireflyDrift by transition.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(5000, easing = LinearEasing), RepeatMode.Restart),
        label = "fireflyDrift"
    )
    val sunPulse by transition.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(10000, easing = LinearEasing), RepeatMode.Reverse),
        label = "sunPulse"
    )

    val warmSky = Color(0xFFB87840)
    val skyTop = Color(0xFF3A4060)
    val fieldGreen = Color(0xFF2A3A18)
    val dirtRoad = Color(0xFF4A3A28)
    val fenceColor = Color(0xFF3A3020)
    val porchGlow = Color(0xFFFFD080)

    Canvas(modifier = modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height

        // Sky gradient — warm sunset fading to deep blue
        val skyBands = listOf(
            skyTop, Color(0xFF4A5070), Color(0xFF6A5A60),
            Color(0xFF8A6850), Color(0xFFA87840), warmSky
        )
        val bandH = h * 0.08f
        skyBands.forEachIndexed { i, color ->
            drawRect(
                color = color,
                topLeft = Offset(0f, i * bandH),
                size = androidx.compose.ui.geometry.Size(w, bandH + 1f)
            )
        }

        // Sun disk on horizon
        val sunX = w * 0.35f
        val sunY = h * 0.42f
        drawCircle(
            color = Color(0xFFFFD060).copy(alpha = 0.08f + sunPulse * 0.04f),
            radius = w * 0.15f,
            center = Offset(sunX, sunY)
        )
        drawCircle(
            color = Color(0xFFFFE080).copy(alpha = 0.2f + sunPulse * 0.1f),
            radius = w * 0.04f,
            center = Offset(sunX, sunY)
        )

        // Horizon
        val horizonY = h * 0.45f
        drawRect(
            color = warmSky.copy(alpha = 0.5f),
            topLeft = Offset(0f, horizonY - 2f),
            size = androidx.compose.ui.geometry.Size(w, 4f)
        )

        // Field
        drawRect(
            color = fieldGreen,
            topLeft = Offset(0f, horizonY),
            size = androidx.compose.ui.geometry.Size(w, h - horizonY)
        )

        // Grass wisps — swaying
        for (i in 0 until 30) {
            val gx = (i * 37f) % w
            val gy = horizonY + (i * 23f) % (h * 0.25f)
            val sway = sin(grassSway * PI.toFloat() * 2f + i) * 4f
            drawLine(
                color = Color(0xFF4A5A30).copy(alpha = 0.3f),
                start = Offset(gx, gy + 12f),
                end = Offset(gx + sway, gy - 5f),
                strokeWidth = 1.5f
            )
        }

        // Dirt road — two tracks converging at horizon
        val roadLeftStart = w * 0.35f
        val roadRightStart = w * 0.65f
        val roadLeftEnd = w * 0.48f
        val roadRightEnd = w * 0.52f
        for (i in 0 until 15) {
            val t = i / 15f
            val leftX = roadLeftStart + (roadLeftEnd - roadLeftStart) * (1f - t)
            val rightX = roadRightStart + (roadRightEnd - roadRightStart) * (1f - t)
            val trackY = horizonY + t * (h - horizonY)
            val trackH = (h - horizonY) / 15f + 2f
            drawRect(
                color = dirtRoad.copy(alpha = 0.3f + t * 0.3f),
                topLeft = Offset(leftX, trackY),
                size = androidx.compose.ui.geometry.Size(rightX - leftX, trackH)
            )
        }

        // Fence posts — along left side
        for (i in 0 until 6) {
            val t = i / 6f
            val fenceX = w * (0.08f + t * 0.15f)
            val fenceY = horizonY + t * (h * 0.3f)
            val fenceH = 20f + t * 15f
            drawRect(
                color = fenceColor.copy(alpha = 0.5f - t * 0.2f),
                topLeft = Offset(fenceX, fenceY - fenceH),
                size = androidx.compose.ui.geometry.Size(2.5f, fenceH)
            )
        }
        // Fence wire
        for (i in 0 until 5) {
            val t1 = i / 6f
            val t2 = (i + 1) / 6f
            val x1 = w * (0.08f + t1 * 0.15f)
            val y1 = horizonY + t1 * (h * 0.3f) - 10f
            val x2 = w * (0.08f + t2 * 0.15f)
            val y2 = horizonY + t2 * (h * 0.3f) - 10f
            drawLine(
                color = fenceColor.copy(alpha = 0.3f),
                start = Offset(x1, y1),
                end = Offset(x2, y2),
                strokeWidth = 0.5f
            )
        }

        // Fireflies — warm dots
        for (i in 0 until 10) {
            val fx = ((fireflyDrift * w + i * 107f) % (w * 0.8f)) + w * 0.1f
            val fy = horizonY + ((fireflyDrift * h * 0.3f + i * 43f) % (h * 0.25f))
            val fAlpha = (0.2f + sin(fireflyDrift * PI.toFloat() * 4f + i) * 0.15f).coerceIn(0f, 0.5f)
            drawCircle(
                color = porchGlow.copy(alpha = fAlpha * 0.4f),
                radius = 4f,
                center = Offset(fx, fy)
            )
            drawCircle(
                color = porchGlow.copy(alpha = fAlpha),
                radius = 1.5f,
                center = Offset(fx, fy)
            )
        }

        // Vignette
        drawRect(
            color = Color.Black.copy(alpha = 0.18f),
            topLeft = Offset.Zero,
            size = size
        )
    }
}
