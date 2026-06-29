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
import androidx.compose.ui.graphics.drawscope.DrawScope
import com.audiophile.musicplayer.audio.visualizer.AuraPalette
import com.audiophile.musicplayer.audio.visualizer.VantaAudioFrame

@Composable
fun VantaBassBloom(
    palette: AuraPalette,
    audioFrame: VantaAudioFrame,
    isPlaying: Boolean,
    modifier: Modifier
) {
    val targetBass = if (isPlaying) audioFrame.bassEnergy * 0.6f else 0f
    val bloomIntensity by animateFloatAsState(
        targetValue = targetBass,
        animationSpec = tween(durationMillis = 200),
        label = "bloom"
    )

    Canvas(modifier = modifier) {
        val center = Offset(size.width / 2f, size.height / 2f)
        val maxRadius = size.maxDimension * 0.5f
        val bloomRadius = maxRadius * (0.3f + bloomIntensity * 0.4f)
        val bloomAlpha = (bloomIntensity * 0.20f).coerceIn(0f, 0.20f)

        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    palette.glowHigh.copy(alpha = bloomAlpha),
                    Color.Transparent
                ),
                center = center,
                radius = bloomRadius
            ),
            radius = bloomRadius,
            center = center
        )
    }
}
