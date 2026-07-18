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
import kotlin.math.PI
import kotlin.math.sin

/**
 * Industrial metal stage: black void, steel beams, red/white strobes,
 * smoke plumes, sparks, harsh shadows. Rammstein / NIN / Ministry.
 */
@Composable
fun IndustrialStageScene(
    accentColor: Color,
    energy: Float,
    intensity: Float = 0.5f,
    modifier: Modifier = Modifier
) {
    val transition = rememberInfiniteTransition(label = "industrialStage")
    val strobeCycle by transition.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(400, easing = LinearEasing), RepeatMode.Restart),
        label = "strobeCycle"
    )
    val smokeDrift by transition.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(6000, easing = LinearEasing), RepeatMode.Restart),
        label = "smokeDrift"
    )
    val sparkCycle by transition.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(3000, easing = LinearEasing), RepeatMode.Restart),
        label = "sparkCycle"
    )
    val beamSway by transition.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(8000, easing = LinearEasing), RepeatMode.Reverse),
        label = "beamSway"
    )

    val fireRed = Color(0xFFCC2020)
    val strobeWhite = Color(0xFFFFFFFF)
    val steelGray = Color(0xFF3A3A3A)
    val smokeColor = Color(0xFF555555)

    Canvas(modifier = modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height

        // Pure black void
        drawRect(color = Color(0xFF050505), topLeft = Offset.Zero, size = size)

        // Steel beams — vertical structural elements
        val beamWidth = w * 0.03f
        val beamPositions = listOf(0.08f, 0.25f, 0.75f, 0.92f)
        beamPositions.forEach { xRatio ->
            drawRect(
                color = steelGray,
                topLeft = Offset(w * xRatio - beamWidth / 2f, 0f),
                size = androidx.compose.ui.geometry.Size(beamWidth, h)
            )
            // Beam highlight
            drawRect(
                color = Color.White.copy(alpha = 0.06f),
                topLeft = Offset(w * xRatio - beamWidth / 2f, 0f),
                size = androidx.compose.ui.geometry.Size(beamWidth * 0.3f, h)
            )
        }

        // Cross beam at top
        drawRect(
            color = steelGray.copy(alpha = 0.8f),
            topLeft = Offset(0f, h * 0.08f),
            size = androidx.compose.ui.geometry.Size(w, h * 0.02f)
        )

        // Stage floor — dark concrete
        val floorY = h * 0.78f
        drawRect(
            color = Color(0xFF1A1A1A),
            topLeft = Offset(0f, floorY),
            size = androidx.compose.ui.geometry.Size(w, h - floorY)
        )
        drawRect(
            color = Color(0xFF222222),
            topLeft = Offset(0f, floorY),
            size = androidx.compose.ui.geometry.Size(w, 3f)
        )

        // Red stage lights — wide beams from top
        val lightIntensity = intensity.coerceIn(0.3f, 1f)
        val redAlpha = 0.12f + lightIntensity * 0.15f + energy * 0.08f
        // Left red light
        val leftBeamX = w * 0.2f + sin(beamSway * PI.toFloat() * 2f) * w * 0.05f
        for (i in 0 until 15) {
            val t = i / 15f
            val bw = w * 0.02f + t * w * 0.2f
            val by = t * floorY
            drawRect(
                color = fireRed.copy(alpha = redAlpha * (1f - t * 0.4f)),
                topLeft = Offset(leftBeamX - bw / 2f, by),
                size = androidx.compose.ui.geometry.Size(bw, floorY / 15f + 2f)
            )
        }
        // Right red light
        val rightBeamX = w * 0.8f - sin(beamSway * PI.toFloat() * 2f) * w * 0.05f
        for (i in 0 until 15) {
            val t = i / 15f
            val bw = w * 0.02f + t * w * 0.2f
            val by = t * floorY
            drawRect(
                color = fireRed.copy(alpha = redAlpha * (1f - t * 0.4f)),
                topLeft = Offset(rightBeamX - bw / 2f, by),
                size = androidx.compose.ui.geometry.Size(bw, floorY / 15f + 2f)
            )
        }

        // Strobe flashes — harsh white pulses on beat
        val strobeActive = strobeCycle < 0.15f && intensity > 0.5f
        if (strobeActive) {
            val strobeAlpha = (0.06f + intensity * 0.12f).coerceAtMost(0.2f)
            drawRect(
                color = strobeWhite.copy(alpha = strobeAlpha),
                topLeft = Offset.Zero,
                size = size
            )
        }

        // Smoke plumes — drifting from bottom
        for (i in 0 until 12) {
            val baseX = (w * (i / 12f) + smokeDrift * w * 0.3f) % (w * 1.2f) - w * 0.1f
            val baseY = floorY - (smokeDrift * h * 0.15f + i * 17f) % (h * 0.3f)
            val smokeAlpha = (0.06f + (i % 3) * 0.02f).coerceIn(0f, 0.15f)
            val smokeRadius = 15f + (i % 5) * 8f + smokeDrift * 5f
            drawCircle(
                color = smokeColor.copy(alpha = smokeAlpha),
                radius = smokeRadius,
                center = Offset(baseX, baseY)
            )
        }

        // Sparks — small bright particles cascading down
        for (i in 0 until 20) {
            val sx = ((sparkCycle * w * 2f + i * 97f) % (w * 1.2f)) - w * 0.1f
            val sy = ((sparkCycle * h * 1.5f + i * 63f) % (h * 0.6f)) + h * 0.1f
            val sparkAlpha = (0.3f + (i % 4) * 0.15f) * intensity
            val sparkSize = 1.5f + (i % 3) * 1f
            val sparkColor = if (i % 3 == 0) fireRed else Color(0xFFFFAA40)
            drawCircle(
                color = sparkColor.copy(alpha = sparkAlpha.coerceIn(0f, 0.8f)),
                radius = sparkSize,
                center = Offset(sx, sy)
            )
        }

        // Red floor glow
        drawRect(
            color = fireRed.copy(alpha = 0.04f + energy * 0.06f),
            topLeft = Offset(0f, floorY),
            size = androidx.compose.ui.geometry.Size(w, h - floorY)
        )

        // Final vignette
        drawRect(
            color = Color.Black.copy(alpha = 0.2f),
            topLeft = Offset.Zero,
            size = size
        )
    }
}
