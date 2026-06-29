package com.audiophile.musicplayer.ui.visualizer

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import com.audiophile.musicplayer.audio.visualizer.AuraPalette
import com.audiophile.musicplayer.audio.visualizer.VantaAudioFrame

private val VantaEmerald = Color(0xFF00E676)
private val VantaTeal = Color(0xFF00B0FF)

@Composable
fun VantaBeatOrb(
    palette: AuraPalette,
    audioFrame: VantaAudioFrame,
    isPlaying: Boolean,
    reducedMotion: Boolean,
    modifier: Modifier = Modifier
) {
    if (reducedMotion) return // Do not draw the pulsing orb if reduced motion is enabled

    // Blend Vanta house aesthetic with 20% of the album palette
    val blendedTeal = lerp(VantaTeal, palette.primary, 0.20f)
    val blendedEmerald = lerp(VantaEmerald, palette.ambient, 0.20f)

    // Calculate energy from bass and mid bands
    val rawEnergy = if (isPlaying) {
        audioFrame.bassEnergy * 0.7f + audioFrame.midEnergy * 0.3f
    } else {
        0f
    }

    val hiddenEnergy = if (rawEnergy < 0.05f) 0f else rawEnergy

    val smoothedEnergy by animateFloatAsState(
        targetValue = hiddenEnergy,
        animationSpec = spring(dampingRatio = 0.7f, stiffness = Spring.StiffnessMediumLow),
        label = "smooth_orb_energy"
    )

    if (smoothedEnergy < 0.01f) return // Skip drawing if practically invisible

    Canvas(modifier = modifier.fillMaxSize()) {
        val center = Offset(size.width / 2f, size.height / 2f)
        
        // Base pulse scale and alpha
        val baseRadius = size.minDimension * 0.4f
        val pulseRadius = baseRadius + (smoothedEnergy * baseRadius * 0.3f)
        val alphaMultiplier = smoothedEnergy.coerceIn(0f, 1f)
        
        // Soft outer bloom
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    blendedTeal.copy(alpha = alphaMultiplier * 0.4f),
                    blendedTeal.copy(alpha = alphaMultiplier * 0.15f),
                    Color.Transparent
                ),
                center = center,
                radius = pulseRadius * 1.5f
            ),
            radius = pulseRadius * 1.5f,
            center = center
        )

        // Dense inner core
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    Color.White.copy(alpha = alphaMultiplier * 0.8f),
                    blendedEmerald.copy(alpha = alphaMultiplier * 0.6f),
                    blendedEmerald.copy(alpha = alphaMultiplier * 0.2f),
                    Color.Transparent
                ),
                center = center,
                radius = pulseRadius * 0.8f
            ),
            radius = pulseRadius * 0.8f,
            center = center
        )
    }
}
