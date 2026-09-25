package com.audiophile.musicplayer.audio.visualizer

import androidx.compose.ui.graphics.Color
import com.audiophile.musicplayer.ui.AppAccent
import com.audiophile.musicplayer.ui.AppBackground

enum class AuraMode {
    AMBIENT,
    SILK_WAVE,
    VELVET_BARS,
    LYRIC_GLOW,
    VINYL_ROOM,
    NIGHT_DRIVE,
    REDUCED_MOTION
}

data class VantaAuraState(
    val mode: AuraMode = AuraMode.AMBIENT,
    val isActive: Boolean = false,
    val currentTrackId: Long? = null,
    val isPlaying: Boolean = false,
    val reducedMotion: Boolean = false,
    val audioReactiveEnabled: Boolean = true,
    val artworkFallback: Boolean = true,
    val reduceMotionInCar: Boolean = true,
    val palette: AuraPalette = AuraPalette(),
    val lyricLineEnergy: Float = 0f,
    val beatDetected: Boolean = false
)

data class AuraPalette(
    val primary: Color = AppAccent,
    val secondary: Color = Color(0xFFDDB892),
    val ambient: Color = AppAccent.copy(alpha = 0.12f),
    val background: Color = AppBackground,
    val glowLow: Color = AppAccent.copy(alpha = 0.06f),
    val glowMedium: Color = AppAccent.copy(alpha = 0.10f),
    val glowHigh: Color = AppAccent.copy(alpha = 0.18f),
    val warmCream: Color = Color(0xFFFFE8C8),
    val amber: Color = Color(0xFFC4843E),
    val deepGold: Color = Color(0xFF8B6F47),
    val softBlack: Color = Color(0xFF0B0A0D),
    val shadow: Color = Color(0xFF000000).copy(alpha = 0.4f)
) {
    fun forArtwork(artworkColors: List<Color>): AuraPalette {
        if (artworkColors.isEmpty()) return this

        val primaryRaw = artworkColors.getOrNull(0) ?: AppAccent
        val secondaryRaw = artworkColors.getOrNull(1) ?: primaryRaw
        val tertiaryRaw = artworkColors.getOrNull(2) ?: secondaryRaw

        val p = clampSaturation(primaryRaw)
        val s = clampSaturation(secondaryRaw)
        // Preserve cover fidelity — only a light warm nudge for saturated hues.
        val warmP = if (saturationOf(p) < 0.14f) p else warmFilter(p)
        val dimP = darken(warmP, 0.45f)
        val glow = darken(warmP, 0.75f)
        val cream = when {
            isLight(tertiaryRaw) -> tertiaryRaw
            isLight(secondaryRaw) -> secondaryRaw
            saturationOf(p) < 0.14f -> Color(0xFFE8E0D4)
            else -> warmCream
        }

        return copy(
            primary = warmP,
            secondary = s,
            ambient = dimP.copy(alpha = 0.32f),
            glowLow = glow.copy(alpha = 0.16f),
            glowMedium = glow.copy(alpha = 0.28f),
            glowHigh = glow.copy(alpha = 0.42f),
            warmCream = cream
        )
    }

    private fun saturationOf(color: Color): Float {
        val max = maxOf(color.red, color.green, color.blue)
        val min = minOf(color.red, color.green, color.blue)
        return if (max > 0f) (max - min) / max else 0f
    }

    private fun isLight(color: Color): Boolean {
        val luminance = 0.299f * color.red + 0.587f * color.green + 0.114f * color.blue
        return luminance > 0.5f
    }

    private fun clampSaturation(color: Color): Color {
        val r = color.red
        val g = color.green
        val b = color.blue
        val max = maxOf(r, g, b)
        val min = minOf(r, g, b)
        val saturation = if (max > 0f) (max - min) / max else 0f
        // Leave near-monochrome covers alone so charcoal/cream stays charcoal/cream.
        if (saturation < 0.14f) return color
        if (saturation <= 0.6f) return color
        val factor = 0.6f / saturation
        val avg = (r + g + b) / 3f
        return Color(
            avg + (r - avg) * factor,
            avg + (g - avg) * factor,
            avg + (b - avg) * factor,
            color.alpha
        )
    }

    private fun warmFilter(color: Color): Color {
        val warmth = 0.15f
        return Color(
            (color.red + warmth * (1f - color.red)).coerceIn(0f, 1f),
            (color.green + warmth * 0.5f * (1f - color.green)).coerceIn(0f, 1f),
            (color.blue * (1f - warmth)).coerceIn(0f, 1f),
            color.alpha
        )
    }

    private fun darken(color: Color, factor: Float): Color =
        Color(color.red * factor, color.green * factor, color.blue * factor, color.alpha)
}
