package com.audiophile.musicplayer.ui.visualizer

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.core.graphics.ColorUtils
import com.audiophile.musicplayer.audio.visualizer.AuraPalette
import com.audiophile.musicplayer.audio.visualizer.VantaAudioFrame
import kotlin.math.PI
import kotlin.math.sin

// Warm champagne / gold house palette — no cold blues
private val HouseWarmCream = Color(0xFFFFF4E0)
private val HouseGold = Color(0xFFE8B87A)
private val HouseAmber = Color(0xFFC4843E)
private val HouseDeepAmber = Color(0xFF8B5A2B)

@Composable
fun VantaBeatOrb(
    palette: AuraPalette,
    audioFrame: VantaAudioFrame?,
    isPlaying: Boolean,
    reducedMotion: Boolean,
    modifier: Modifier = Modifier
) {
    if (reducedMotion) return

    // Blend album palette with house warm gold (30% album, 70% warm gold)
    // This keeps the orb on-brand while gently hinting at the track's palette.
    val primary = lerp(HouseGold, palette.primary.coerceNonBlue(), 0.30f)
    val ambient = lerp(HouseAmber, palette.ambient.coerceNonBlue(), 0.30f)

    val frame = audioFrame ?: com.audiophile.musicplayer.audio.visualizer.VantaAudioFrame()
    val rawEnergy = if (isPlaying) {
        frame.bassEnergy * 0.7f + frame.midEnergy * 0.3f
    } else 0f

    // Always keep a faint core so the orb never disappears completely.
    val targetEnergy = (rawEnergy.coerceAtLeast(0.10f)).coerceIn(0f, 1f)

    val smoothedEnergy by animateFloatAsState(
        targetValue = targetEnergy,
        animationSpec = tween(durationMillis = 480),
        label = "smooth_orb_energy"
    )

    // Gentle breathing phase even when paused.
    val breatheTransition = rememberInfiniteTransition(label = "orb_breathe")
    val breathe by breatheTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 4000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "orb_breathe_value"
    )
    val breatheFactor = 0.92f + (sin(breathe * PI.toFloat() * 2f) * 0.08f)

    Canvas(modifier = modifier.fillMaxSize()) {
        val center = Offset(size.width / 2f, size.height / 2f)
        val baseRadius = size.minDimension * 0.38f * breatheFactor
        val pulseRadius = baseRadius + (smoothedEnergy * baseRadius * 0.25f)
        val alphaMultiplier = smoothedEnergy.coerceIn(0f, 1f)

        // Soft outer bloom
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    primary.copy(alpha = alphaMultiplier * 0.35f),
                    primary.copy(alpha = alphaMultiplier * 0.12f),
                    Color.Transparent
                ),
                center = center,
                radius = pulseRadius * 1.6f
            ),
            radius = pulseRadius * 1.6f,
            center = center
        )

        // Dense inner core
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    HouseWarmCream.copy(alpha = alphaMultiplier * 0.75f),
                    ambient.copy(alpha = alphaMultiplier * 0.55f),
                    ambient.copy(alpha = alphaMultiplier * 0.18f),
                    Color.Transparent
                ),
                center = center,
                radius = pulseRadius * 0.85f
            ),
            radius = pulseRadius * 0.85f,
            center = center
        )

        // Subtle rim ring
        drawCircle(
            color = HouseDeepAmber.copy(alpha = alphaMultiplier * 0.10f),
            radius = pulseRadius * 1.05f,
            center = center,
            style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.5f)
        )
    }
}

private fun Color.coerceNonBlue(): Color {
    val hsl = FloatArray(3)
    ColorUtils.RGBToHSL(
        (red * 255).toInt().coerceIn(0, 255),
        (green * 255).toInt().coerceIn(0, 255),
        (blue * 255).toInt().coerceIn(0, 255),
        hsl
    )
    // Push cold cyan/blue/purple hues toward warm champagne/amber
    val h = hsl[0]
    val safeHue = when (h) {
        in 160f..210f -> 38f
        in 210f..270f -> 32f
        in 270f..330f -> 14f
        else -> h
    }
    return Color(ColorUtils.HSLToColor(floatArrayOf(safeHue, hsl[1].coerceIn(0.25f, 0.75f), hsl[2].coerceIn(0.25f, 0.65f))))
}
