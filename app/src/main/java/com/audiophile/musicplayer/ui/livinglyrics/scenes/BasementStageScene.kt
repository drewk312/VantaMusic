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

/**
 * Punk / garage basement: concrete walls, harsh lamps, torn posters,
 * graffiti, fast energy. Ramones, Green Day, The Clash.
 */
@Composable
fun BasementStageScene(
    accentColor: Color,
    energy: Float,
    intensity: Float = 0.5f,
    modifier: Modifier = Modifier
) {
    val transition = rememberInfiniteTransition(label = "basementStage")
    val lightFlicker by transition.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(600, easing = LinearEasing), RepeatMode.Reverse),
        label = "lightFlicker"
    )
    val cameraShake by transition.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(300, easing = LinearEasing), RepeatMode.Reverse),
        label = "cameraShake"
    )

    val wallColor = Color(0xFF2A2520)
    val concreteFloor = Color(0xFF1A1815)
    val harshYellow = Color(0xFFFFE880)
    val posterRed = Color(0xFFE04040)
    val posterBlue = Color(0xFF4060D0)

    Canvas(modifier = modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height
        val shake = if (intensity > 0.6f) (cameraShake - 0.5f) * 3f else 0f

        // Concrete wall
        drawRect(color = wallColor, topLeft = Offset(shake, shake), size = size)

        // Wall texture — subtle cracks
        for (i in 0 until 20) {
            val cx = (i * 113f) % w
            val cy = (i * 79f) % (h * 0.7f)
            drawLine(
                color = Color.Black.copy(alpha = 0.06f),
                start = Offset(cx + shake, cy + shake),
                end = Offset(cx + 15f + shake, cy + 8f + shake),
                strokeWidth = 0.5f
            )
        }

        // Floor
        val floorY = h * 0.75f
        drawRect(
            color = concreteFloor,
            topLeft = Offset(0f, floorY + shake),
            size = androidx.compose.ui.geometry.Size(w, h - floorY)
        )

        // Torn posters on wall
        val posters = listOf(
            Triple(w * 0.1f, h * 0.15f, posterRed),
            Triple(w * 0.55f, h * 0.2f, posterBlue),
            Triple(w * 0.8f, h * 0.12f, Color(0xFF40A040))
        )
        posters.forEach { (px, py, color) ->
            val pw = w * 0.12f
            val ph = h * 0.15f
            drawRect(
                color = color.copy(alpha = 0.4f),
                topLeft = Offset(px + shake, py + shake),
                size = androidx.compose.ui.geometry.Size(pw, ph)
            )
            // Torn edge
            drawRect(
                color = wallColor,
                topLeft = Offset(px + pw * 0.6f + shake, py + shake),
                size = androidx.compose.ui.geometry.Size(pw * 0.4f, ph * 0.3f)
            )
        }

        // Harsh overhead lamp — single yellow bulb
        val lampX = w * 0.5f
        val lampY = h * 0.02f
        val flickerMultiplier = if (lightFlicker > 0.85f) 0.5f else 1f
        val bulbAlpha = (0.4f + energy * 0.2f) * flickerMultiplier

        // Lamp glow cone
        for (i in 0 until 12) {
            val t = i / 12f
            val coneW = w * 0.03f + t * w * 0.4f
            val coneY = lampY + t * (floorY - lampY)
            drawRect(
                color = harshYellow.copy(alpha = bulbAlpha * 0.08f * (1f - t * 0.5f)),
                topLeft = Offset(lampX - coneW / 2f + shake, coneY + shake),
                size = androidx.compose.ui.geometry.Size(coneW, (floorY - lampY) / 12f + 2f)
            )
        }

        // Bulb
        drawCircle(
            color = harshYellow.copy(alpha = bulbAlpha),
            radius = 6f,
            center = Offset(lampX + shake, lampY + shake)
        )

        // Microphone stand silhouette — center stage
        val micBaseX = w * 0.5f
        val micTopY = floorY - h * 0.25f
        drawRect(
            color = Color(0xFF333333),
            topLeft = Offset(micBaseX - 1.5f + shake, micTopY + shake),
            size = androidx.compose.ui.geometry.Size(3f, h * 0.25f)
        )
        drawCircle(
            color = Color(0xFF444444),
            radius = 6f,
            center = Offset(micBaseX + shake, micTopY + shake)
        )

        // Graffiti marks — abstract lines
        for (i in 0 until 8) {
            val gx = w * (0.05f + (i * 0.12f) % 0.9f)
            val gy = h * (0.4f + (i * 0.07f) % 0.3f)
            val gColor = when (i % 3) {
                0 -> Color.White
                1 -> posterRed
                else -> posterBlue
            }
            drawLine(
                color = gColor.copy(alpha = 0.1f),
                start = Offset(gx + shake, gy + shake),
                end = Offset(gx + 20f + shake, gy - 10f + shake),
                strokeWidth = 2f
            )
        }

        // Vignette — heavier for basement feel
        drawRect(
            color = Color.Black.copy(alpha = 0.3f),
            topLeft = Offset.Zero,
            size = size
        )
    }
}
