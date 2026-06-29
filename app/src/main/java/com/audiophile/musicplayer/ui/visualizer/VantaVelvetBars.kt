package com.audiophile.musicplayer.ui.visualizer

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import com.audiophile.musicplayer.audio.visualizer.AuraPalette
import com.audiophile.musicplayer.audio.visualizer.VantaAudioFrame
import kotlin.math.PI
import kotlin.math.sin

@Composable
fun VantaVelvetBars(
    palette: AuraPalette,
    audioFrame: VantaAudioFrame,
    isPlaying: Boolean,
    modifier: Modifier,
    barCount: Int = 12
) {
    Canvas(modifier = modifier) {
        val width = size.width
        val height = size.height
        val barWidth = (width / barCount) * 0.65f
        val gap = (width / barCount) * 0.35f
        val maxBarHeight = height * 0.7f
        val baseHeight = height * 0.1f

        val phase = (System.currentTimeMillis() % 3000L) / 3000f

        for (i in 0 until barCount) {
            val audioVal = audioFrame.fftBuckets.getOrElse(i % audioFrame.fftBuckets.size.coerceAtLeast(1)) { 0f }
            val waveFactor = sin(phase * PI.toFloat() * 2f + i * 0.8f) * 0.15f + 0.15f
            val energy = if (isPlaying) audioVal * 0.5f + waveFactor else waveFactor * 0.3f
            val barHeight = (baseHeight + energy * maxBarHeight).coerceIn(baseHeight, maxBarHeight)
            val x = i * (barWidth + gap) + gap / 2f
            val y = (height - barHeight) / 2f
            val barAlpha = (0.25f + audioVal * 0.3f).coerceIn(0.1f, 0.55f)

            drawRoundRect(
                color = palette.warmCream.copy(alpha = barAlpha),
                topLeft = Offset(x, y),
                size = Size(barWidth, barHeight),
                cornerRadius = CornerRadius(barWidth / 2f, barWidth / 2f)
            )
        }
    }
}
