package com.audiophile.musicplayer.ui.visualizer

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import com.audiophile.musicplayer.audio.visualizer.AuraPalette
import com.audiophile.musicplayer.audio.visualizer.VantaAudioFrame
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun VantaCarAura(
    palette: AuraPalette,
    audioFrame: VantaAudioFrame,
    isPlaying: Boolean,
    modifier: Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "car_aura")
    val phase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 6000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "phase"
    )

    Canvas(modifier = modifier) {
        val width = size.width
        val height = size.height
        val breath = sin(phase * PI.toFloat() * 2f) * 0.08f + 0.10f
        val energy = if (isPlaying) audioFrame.rms * 0.06f else 0f
        val alpha = (breath + energy).coerceIn(0.02f, 0.20f)

        val tintColor = palette.warmCream
        val topGradient = Brush.verticalGradient(
            colors = listOf(tintColor.copy(alpha = alpha), Color.Transparent),
            startY = 0f,
            endY = height * 0.5f
        )
        drawRect(brush = topGradient, size = Size(width, height * 0.5f))

        val angle = phase * PI.toFloat() * 2f
        val spotX = cos(angle) * width * 0.2f + width * 0.5f
        val spotY = sin(angle * 0.5f) * height * 0.15f + height * 0.35f
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    tintColor.copy(alpha = alpha * 0.4f),
                    Color.Transparent
                ),
                center = Offset(spotX, spotY),
                radius = width * 0.35f
            ),
            radius = width * 0.35f,
            center = Offset(spotX, spotY)
        )
    }
}
