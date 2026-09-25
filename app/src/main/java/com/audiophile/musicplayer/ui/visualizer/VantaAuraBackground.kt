package com.audiophile.musicplayer.ui.visualizer

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import com.audiophile.musicplayer.audio.visualizer.AuraPalette
import com.audiophile.musicplayer.audio.visualizer.VantaAudioFrame
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

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

    // Cover-art colors only — no house emerald/teal that washes NP green.
    val primaryGlow = palette.primary
    val ambientGlow = palette.secondary
    val creamHighlight = palette.warmCream

    val rawEnergy = if (isPlaying) audioFrame.rms else 0f
    val hiddenEnergy = if (rawEnergy < 0.03f) 0f else rawEnergy

    val targetAlpha = if (isPlaying && !reducedMotion) {
        0.14f + hiddenEnergy * 0.48f
    } else if (isPlaying) {
        0.10f
    } else {
        0.04f
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
        // Transparent base so LivingArtworkBackdrop / cover palette show through.
        val center = Offset(size.width / 2f, size.height / 2f)
        val glowAlpha = smoothedAlpha.coerceIn(0f, 1f)

        val maxRadius = size.maxDimension * (0.90f + smoothedBass.coerceIn(0f, 1f) * 0.18f)
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    primaryGlow.withClampedAlpha(glowAlpha * 0.90f),
                    primaryGlow.withClampedAlpha(glowAlpha * 0.40f),
                    Color.Transparent
                ),
                center = center,
                radius = maxRadius
            ),
            radius = maxRadius,
            center = center
        )

        if (!reducedMotion) {
            val angle = breathPhase * PI.toFloat() * 2f
            val orbitRadius = size.minDimension * 0.22f
            val driftX = cos(angle) * orbitRadius + center.x
            val driftY = sin(angle * 0.8f) * orbitRadius * 0.6f + center.y

            val bloomRadius = size.maxDimension * (0.62f + smoothedBass.coerceIn(0f, 1f) * 0.42f)
            val bloomAlpha = (glowAlpha * 1.15f).coerceIn(0f, 1f)

            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        ambientGlow.withClampedAlpha(bloomAlpha),
                        creamHighlight.withClampedAlpha(bloomAlpha * 0.35f),
                        Color.Transparent
                    ),
                    center = Offset(driftX, driftY),
                    radius = bloomRadius
                ),
                radius = bloomRadius,
                center = Offset(driftX, driftY)
            )

            // Soft mid-band ribbon so the visualizer reads even on dark covers.
            val midAlpha = (glowAlpha * (0.35f + audioFrame.midEnergy.coerceIn(0f, 1f) * 0.55f)).coerceIn(0f, 1f)
            val ribbonY = size.height * (0.42f + sin(angle * 1.3f) * 0.04f)
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        creamHighlight.withClampedAlpha(midAlpha * 0.55f),
                        primaryGlow.withClampedAlpha(midAlpha * 0.25f),
                        Color.Transparent
                    ),
                    center = Offset(size.width * 0.5f, ribbonY),
                    radius = size.width * 0.72f
                ),
                radius = size.width * 0.72f,
                center = Offset(size.width * 0.5f, ribbonY)
            )
        } else {
            val bloomRadius = size.maxDimension * 0.65f
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        ambientGlow.withClampedAlpha(glowAlpha),
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

private fun Color.withClampedAlpha(alpha: Float): Color =
    copy(alpha = alpha.coerceIn(0f, 1f))
