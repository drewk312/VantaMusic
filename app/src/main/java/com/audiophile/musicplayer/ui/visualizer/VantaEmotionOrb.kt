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
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import com.audiophile.musicplayer.audio.visualizer.VantaAudioFrame
import com.audiophile.musicplayer.ui.nowplaying.MoodOrbPalette
import com.audiophile.musicplayer.ui.nowplaying.TrackMood
import com.audiophile.musicplayer.ui.nowplaying.TrackMoodEngine
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun VantaEmotionOrb(
    mood: TrackMood,
    audioFrame: VantaAudioFrame?,
    energy: Float,
    modifier: Modifier = Modifier
) {
    val palette = remember(mood) { TrackMoodEngine.moodToOrbPalette(mood) }

    val infiniteTransition = rememberInfiniteTransition(label = "orb_breath")
    val breathe by infiniteTransition.animateFloat(
        initialValue = 0.92f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(
            animation = tween(4000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "breathe"
    )

    val smoothedEnergy = remember(energy) {
        if (energy > 0.1f) energy.coerceAtMost(1f) else 0.10f
    }

    val audioEnergy = audioFrame?.let { frame ->
        frame.bassEnergy * 0.7f + frame.midEnergy * 0.3f
    } ?: smoothedEnergy

    val pulseMultiplier = 1f + audioEnergy * 0.25f

    Canvas(modifier = modifier) {
        val center = Offset(size.width / 2f, size.height / 2f)
        val baseRadius = size.minDimension * 0.35f
        val pulseRadius = baseRadius * breathe * pulseMultiplier

        when (mood) {
            TrackMood.CALM -> drawCalmOrb(center, pulseRadius, palette, audioEnergy)
            TrackMood.ENERGETIC -> drawEnergeticOrb(center, pulseRadius, palette, audioEnergy)
            TrackMood.DARK -> drawDarkOrb(center, pulseRadius, palette, audioEnergy)
            TrackMood.NOSTALGIC -> drawNostalgicOrb(center, pulseRadius, palette, audioEnergy)
            TrackMood.ROMANTIC -> drawRomanticOrb(center, pulseRadius, palette, audioEnergy)
            TrackMood.AGGRESSIVE -> drawAggressiveOrb(center, pulseRadius, palette, audioEnergy)
            TrackMood.CELEBRATORY -> drawCelebratoryOrb(center, pulseRadius, palette, audioEnergy)
            TrackMood.UNKNOWN -> drawDefaultOrb(center, pulseRadius, palette, audioEnergy)
        }
    }
}

private fun DrawScope.drawCalmOrb(center: Offset, radius: Float, p: MoodOrbPalette, energy: Float) {
    val bloomAlpha = (0.12f + energy * 0.20f).coerceAtMost(0.35f)
    drawCircle(Brush.radialGradient(listOf(p.glow, p.glow.copy(alpha = 0f))), radius * 1.5f, center, alpha = bloomAlpha)
    drawCircle(Brush.radialGradient(listOf(p.core, p.primary.copy(alpha = 0.5f), p.primary.copy(alpha = 0f))), radius * 0.85f, center, alpha = 0.8f)
    drawCircle(p.core, radius * 0.25f, center, alpha = 0.7f)
    drawCircle(p.primary.copy(alpha = 0.08f), radius * 1.1f, center, style = androidx.compose.ui.graphics.drawscope.Stroke(1.5f))
}

private fun DrawScope.drawEnergeticOrb(center: Offset, radius: Float, p: MoodOrbPalette, energy: Float) {
    val bloomAlpha = (0.15f + energy * 0.30f).coerceAtMost(0.50f)
    drawCircle(Brush.radialGradient(listOf(p.glow, p.glow.copy(alpha = 0f))), radius * 1.8f, center, alpha = bloomAlpha)
    drawCircle(Brush.radialGradient(listOf(p.core, p.primary, p.primary.copy(alpha = 0f))), radius * 0.9f, center, alpha = 0.85f)
    drawCircle(p.core, radius * 0.35f, center, alpha = 0.8f)
    drawCircle(p.accent.copy(alpha = 0.12f + energy * 0.15f), radius * 1.2f, center, style = androidx.compose.ui.graphics.drawscope.Stroke(2f))
}

private fun DrawScope.drawDarkOrb(center: Offset, radius: Float, p: MoodOrbPalette, energy: Float) {
    val darkAlpha = 0.20f + energy * 0.15f
    drawCircle(Brush.radialGradient(listOf(p.glow, p.glow.copy(alpha = 0f))), radius * 1.3f, center, alpha = darkAlpha)
    drawCircle(Brush.radialGradient(listOf(p.core.copy(alpha = 0.6f), p.primary, p.primary.copy(alpha = 0f))), radius * 0.8f, center, alpha = 0.7f)
    drawCircle(p.accent, radius * 0.15f, center, alpha = 0.4f)
    drawCircle(p.primary.copy(alpha = 0.06f), radius * 1.0f, center, style = androidx.compose.ui.graphics.drawscope.Stroke(1f))
}

private fun DrawScope.drawNostalgicOrb(center: Offset, radius: Float, p: MoodOrbPalette, energy: Float) {
    val glowAlpha = (0.15f + energy * 0.15f).coerceAtMost(0.35f)
    drawCircle(Brush.radialGradient(listOf(p.glow, p.glow.copy(alpha = 0f))), radius * 1.6f, center, alpha = glowAlpha)
    drawCircle(Brush.radialGradient(listOf(p.core, p.primary, p.primary.copy(alpha = 0f))), radius * 0.85f, center, alpha = 0.8f)
    drawCircle(p.core, radius * 0.3f, center, alpha = 0.6f)
    drawCircle(p.accent.copy(alpha = 0.10f), radius * 1.15f, center, style = androidx.compose.ui.graphics.drawscope.Stroke(1.5f))
}

private fun DrawScope.drawRomanticOrb(center: Offset, radius: Float, p: MoodOrbPalette, energy: Float) {
    val bloomAlpha = (0.15f + energy * 0.20f).coerceAtMost(0.40f)
    drawCircle(Brush.radialGradient(listOf(p.glow, p.glow.copy(alpha = 0f))), radius * 1.7f, center, alpha = bloomAlpha)
    drawCircle(Brush.radialGradient(listOf(p.core, p.primary, p.primary.copy(alpha = 0f))), radius * 0.9f, center, alpha = 0.85f)
    drawCircle(p.core, radius * 0.3f, center, alpha = 0.7f)
    drawCircle(p.accent.copy(alpha = 0.08f), radius * 1.2f, center, style = androidx.compose.ui.graphics.drawscope.Stroke(1.5f))
}

private fun DrawScope.drawAggressiveOrb(center: Offset, radius: Float, p: MoodOrbPalette, energy: Float) {
    val sharpAlpha = (0.20f + energy * 0.35f).coerceAtMost(0.55f)
    drawCircle(Brush.radialGradient(listOf(p.glow, p.glow.copy(alpha = 0f))), radius * 2.0f, center, alpha = sharpAlpha)
    drawCircle(Brush.radialGradient(listOf(p.core, p.primary, p.primary.copy(alpha = 0f))), radius * 0.95f, center, alpha = 0.9f)
    drawCircle(p.core, radius * 0.3f, center, alpha = 0.85f)
    drawCircle(p.accent.copy(alpha = 0.15f + energy * 0.20f), radius * 1.3f, center, style = androidx.compose.ui.graphics.drawscope.Stroke(2.5f))
}

private fun DrawScope.drawCelebratoryOrb(center: Offset, radius: Float, p: MoodOrbPalette, energy: Float) {
    val sparkAlpha = (0.20f + energy * 0.25f).coerceAtMost(0.50f)
    drawCircle(Brush.radialGradient(listOf(p.glow, p.glow.copy(alpha = 0f))), radius * 1.9f, center, alpha = sparkAlpha)
    drawCircle(Brush.radialGradient(listOf(p.core, p.primary, p.primary.copy(alpha = 0f))), radius * 0.9f, center, alpha = 0.85f)
    drawCircle(p.core, radius * 0.35f, center, alpha = 0.8f)
    drawCircle(p.accent.copy(alpha = 0.10f + energy * 0.12f), radius * 1.25f, center, style = androidx.compose.ui.graphics.drawscope.Stroke(2f))
    if (energy > 0.3f) {
        val sparkCount = 8
        for (i in 0 until sparkCount) {
            val angle = Math.PI.toFloat() * 2f * i / sparkCount
            val sparkRadius = radius * (1.1f + energy * 0.3f)
            drawCircle(
                p.core.copy(alpha = (0.3f + energy * 0.3f) * (0.5f + 0.5f * sin(angle * 3f))),
                radius * 0.04f,
                center + Offset(cos(angle) * sparkRadius, sin(angle) * sparkRadius)
            )
        }
    }
}

private fun DrawScope.drawDefaultOrb(center: Offset, radius: Float, p: MoodOrbPalette, energy: Float) {
    val bloomAlpha = (0.10f + energy * 0.20f).coerceAtMost(0.35f)
    drawCircle(Brush.radialGradient(listOf(p.glow, p.glow.copy(alpha = 0f))), radius * 1.5f, center, alpha = bloomAlpha)
    drawCircle(Brush.radialGradient(listOf(p.core, p.primary.copy(alpha = 0.5f), p.primary.copy(alpha = 0f))), radius * 0.85f, center, alpha = 0.8f)
    drawCircle(p.core, radius * 0.25f, center, alpha = 0.7f)
    drawCircle(p.accent.copy(alpha = 0.08f), radius * 1.1f, center, style = androidx.compose.ui.graphics.drawscope.Stroke(1.5f))
}
