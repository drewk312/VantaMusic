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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import com.audiophile.musicplayer.audio.visualizer.AuraPalette
import com.audiophile.musicplayer.audio.visualizer.VantaAudioFrame
import kotlin.math.PI
import kotlin.math.sin

@Composable
fun VantaLuxuryWaveform(
    palette: AuraPalette,
    audioFrame: VantaAudioFrame,
    isPlaying: Boolean,
    reducedMotion: Boolean,
    modifier: Modifier = Modifier,
    strokeWidth: Float = 2f
) {
    val infiniteTransition = rememberInfiniteTransition(label = "waveform")
    val phase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = if (reducedMotion) 6000 else 3000,
                easing = LinearEasing
            ),
            repeatMode = RepeatMode.Restart
        ),
        label = "phase"
    )

    Canvas(modifier = modifier) {
        val width = size.width
        val height = size.height
        val centerY = height / 2f
        val barCount = 48
        val spacing = width / barCount
        val baseAmp = height * 0.12f
        val energy = if (isPlaying) audioFrame.rms * height * 0.35f else height * 0.04f

        val path = androidx.compose.ui.graphics.Path()
        val buckets = audioFrame.fftBuckets
        
        for (i in 0 until barCount) {
            val x = i * spacing
            val bucketIdx = (i % buckets.size.coerceAtLeast(1))
            val audioInfluence = buckets.getOrElse(bucketIdx) { 0f }
            
            // Mirroring effect for luxury feel
            val distFromCenter = Math.abs(i - barCount / 2f) / (barCount / 2f)
            val centerBias = 1f - distFromCenter
            
            val wavePhase = sin(phase * PI.toFloat() * 2f + i * 0.4f) * 0.5f + 0.5f
            val amp = baseAmp + energy + (audioInfluence * height * 0.25f * centerBias)
            val y = centerY + sin(i * 0.35f + phase * PI.toFloat() * 2f) * amp

            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }

        val waveColor = palette.warmCream.copy(alpha = if (isPlaying) 0.4f else 0.15f)
        drawPath(
            path = path,
            color = waveColor,
            style = Stroke(
                width = strokeWidth,
                cap = StrokeCap.Round,
                join = StrokeJoin.Round
            )
        )
    }
}
