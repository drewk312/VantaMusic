package com.audiophile.musicplayer.ui.visualizer

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import com.audiophile.musicplayer.audio.visualizer.AuraPalette
import com.audiophile.musicplayer.audio.visualizer.VantaAudioFrame

@Composable
fun VantaLyricPulse(
    palette: AuraPalette,
    audioFrame: VantaAudioFrame,
    isPlaying: Boolean,
    isLyricsActive: Boolean,
    modifier: Modifier
) {
    val targetGlow = if (isPlaying && isLyricsActive) audioFrame.midEnergy * 0.3f else 0f
    val glowIntensity by animateFloatAsState(
        targetValue = targetGlow,
        animationSpec = tween(durationMillis = 250),
        label = "lyricGlow"
    )

    Canvas(modifier = modifier) {
        if (glowIntensity < 0.01f) return@Canvas
        val center = Offset(size.width / 2f, size.height / 2f)
        val radius = size.maxDimension * 0.45f
        val alpha = glowIntensity.coerceIn(0f, 0.15f)

        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    palette.warmCream.copy(alpha = alpha),
                    palette.primary.copy(alpha = alpha * 0.5f),
                    Color.Transparent
                ),
                center = center,
                radius = radius
            ),
            radius = radius,
            center = center
        )
    }
}
