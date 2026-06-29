package com.audiophile.musicplayer.ui.visualizer

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import com.audiophile.musicplayer.audio.visualizer.AuraPalette
import com.audiophile.musicplayer.audio.visualizer.VantaAudioFrame
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

private val VantaBlackGlass = Color(0xFF080C11)
private val VantaDeepViolet = Color(0xFF1A0B2E)
private val VantaEmerald = Color(0xFF00E676)
private val VantaTeal = Color(0xFF00B0FF)

@Composable
fun VantaAuraBackground(
    palette: AuraPalette,
    audioFrame: VantaAudioFrame,
    isPlaying: Boolean,
    reducedMotion: Boolean,
    modifier: Modifier = Modifier,
    intensityModifier: Float = 1.0f
) {
    val infiniteTransition = androidx.compose.animation.core.rememberInfiniteTransition(label = "aura_bg")
    val breathPhase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = androidx.compose.animation.core.infiniteRepeatable(
            animation = tween(durationMillis = 8000, easing = androidx.compose.animation.core.LinearEasing),
            repeatMode = androidx.compose.animation.core.RepeatMode.Restart
        ),
        label = "breath"
    )

    // Blend Vanta house aesthetic with 20% of the album palette
    val blendedTeal = lerp(VantaTeal, palette.primary, 0.20f)
    val blendedEmerald = lerp(VantaEmerald, palette.ambient, 0.20f)
    
    // Hidden energy logic
    val rawEnergy = if (isPlaying) audioFrame.rms else 0f
    val hiddenEnergy = if (rawEnergy < 0.04f) 0f else rawEnergy
    
    val targetAlpha = if (isPlaying && !reducedMotion) {
        0.04f + hiddenEnergy * 0.22f
    } else {
        0.08f // Calm static glow for silence/reduced motion
    }

    val smoothedAlpha by animateFloatAsState(
        targetValue = targetAlpha * intensityModifier,
        animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessLow),
        label = "smooth_alpha"
    )

    val targetBass = if (isPlaying && !reducedMotion) audioFrame.bassEnergy else 0f
    val smoothedBass by animateFloatAsState(
        targetValue = targetBass,
        animationSpec = spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessLow),
        label = "smooth_bass"
    )

    Canvas(modifier = modifier.fillMaxSize()) {
        // Base glass layer
        drawRect(color = VantaBlackGlass)
        
        // Deep violet shadow corners
        drawRect(
            brush = Brush.radialGradient(
                colors = listOf(VantaDeepViolet.copy(alpha = 0.4f), Color.Transparent),
                center = Offset(size.width, size.height), // Bottom right
                radius = size.maxDimension * 0.7f
            )
        )
        drawRect(
            brush = Brush.radialGradient(
                colors = listOf(VantaDeepViolet.copy(alpha = 0.2f), Color.Transparent),
                center = Offset(0f, 0f), // Top left
                radius = size.maxDimension * 0.5f
            )
        )

        val center = Offset(size.width / 2f, size.height / 2f)
        
        // Aura glow 1 (Teal base)
        val maxRadius = size.maxDimension * (0.85f + smoothedBass * 0.15f)
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    blendedTeal.copy(alpha = smoothedAlpha * 0.8f),
                    blendedTeal.copy(alpha = smoothedAlpha * 0.3f),
                    Color.Transparent
                ),
                center = center,
                radius = maxRadius
            ),
            radius = maxRadius,
            center = center
        )

        // Aura glow 2 (Emerald bloom)
        if (!reducedMotion) {
            val angle = breathPhase * PI.toFloat() * 2f
            val orbitRadius = size.minDimension * 0.2f
            val driftX = cos(angle) * orbitRadius + center.x
            val driftY = sin(angle * 0.8f) * orbitRadius * 0.6f + center.y
            
            val bloomRadius = size.maxDimension * (0.6f + smoothedBass * 0.4f)
            val bloomAlpha = smoothedAlpha * 1.2f

            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        blendedEmerald.copy(alpha = bloomAlpha),
                        blendedEmerald.copy(alpha = bloomAlpha * 0.4f),
                        Color.Transparent
                    ),
                    center = Offset(driftX, driftY),
                    radius = bloomRadius
                ),
                radius = bloomRadius,
                center = Offset(driftX, driftY)
            )
        } else {
            // Calm static bloom centered
            val bloomRadius = size.maxDimension * 0.6f
            val bloomAlpha = smoothedAlpha
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        blendedEmerald.copy(alpha = bloomAlpha),
                        blendedEmerald.copy(alpha = bloomAlpha * 0.4f),
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
}
