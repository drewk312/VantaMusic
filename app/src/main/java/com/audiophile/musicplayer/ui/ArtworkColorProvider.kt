package com.audiophile.musicplayer.ui

import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.core.graphics.ColorUtils
import androidx.palette.graphics.Palette
import android.graphics.drawable.BitmapDrawable
import coil.Coil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import coil.request.ImageRequest
import coil.request.SuccessResult
import kotlin.math.abs

data class ArtworkGradientColors(
    val topColor: Color,
    val midColor: Color,
    val bottomColor: Color = Color.Black,
    val accentColor: Color,
    val isExtracted: Boolean
)

private val fallbackPalette = listOf(
    Color(0xFF1DB954), Color(0xFFE1332D), Color(0xFF6B3FA0),
    Color(0xFFF59B23), Color(0xFFE8115B), Color(0xFFC4B08A),
    Color(0xFFB87740), Color(0xFFD840B8)
)

fun artworkColorFromSeed(seed: String?): Color {
    return fallbackPalette[abs(seed?.hashCode() ?: 0) % fallbackPalette.size]
}

fun darkenForBackground(color: Color, factor: Float = 0.32f): Color {
    return Color(
        red = color.red * factor,
        green = color.green * factor,
        blue = color.blue * factor,
        alpha = 1f
    )
}

private fun clampSwatchColor(rgb: Int): Color {
    val hsl = FloatArray(3)
    ColorUtils.RGBToHSL(
        AndroidColor.red(rgb),
        AndroidColor.green(rgb),
        AndroidColor.blue(rgb),
        hsl
    )
    hsl[1] = hsl[1].coerceIn(0.28f, 0.72f)
    hsl[2] = hsl[2].coerceIn(0.14f, 0.42f)
    return Color(ColorUtils.HSLToColor(hsl))
}

private fun extractAppleMusicPalette(bitmap: Bitmap): ArtworkGradientColors? {
    val palette = Palette.from(bitmap).generate()
    val vibrant = palette.darkVibrantSwatch
        ?: palette.vibrantSwatch
        ?: palette.lightVibrantSwatch
    val muted = palette.darkMutedSwatch
        ?: palette.mutedSwatch
        ?: palette.lightMutedSwatch
        ?: palette.dominantSwatch

    val accent = when {
        vibrant != null -> Color(vibrant.rgb)
        muted != null -> Color(muted.rgb)
        else -> return null
    }
    val darkVibrant = palette.darkVibrantSwatch
    val top = when {
        darkVibrant != null -> clampSwatchColor(darkVibrant.rgb)
        vibrant != null -> darkenForBackground(Color(vibrant.rgb), 0.38f)
        muted != null -> clampSwatchColor(muted.rgb)
        else -> return null
    }
    val mid = when {
        muted != null -> clampSwatchColor(muted.rgb)
        vibrant != null -> darkenForBackground(Color(vibrant.rgb), 0.22f)
        else -> top
    }

    return ArtworkGradientColors(
        topColor = top,
        midColor = mid,
        bottomColor = Color.Black,
        accentColor = accent,
        isExtracted = true
    )
}

@Composable
fun rememberArtworkGradientColors(
    artworkUrl: String?,
    seed: String?
): ArtworkGradientColors {
    val defaultColor = artworkColorFromSeed(seed)
    var colors by remember(seed) {
        mutableStateOf(
            ArtworkGradientColors(
                topColor = darkenForBackground(defaultColor, 0.34f),
                midColor = darkenForBackground(defaultColor, 0.2f),
                bottomColor = Color.Black,
                accentColor = defaultColor,
                isExtracted = false
            )
        )
    }

    val context = androidx.compose.ui.platform.LocalContext.current

    LaunchedEffect(artworkUrl, seed) {
        if (artworkUrl == null) return@LaunchedEffect

        withContext(Dispatchers.Default) {
            try {
                val imageLoader = Coil.imageLoader(context)
                val request = ImageRequest.Builder(context)
                    .data(artworkUrl)
                    .size(256)
                    .allowHardware(false)
                    .memoryCacheKey("palette_$artworkUrl")
                    .build()
                val result = imageLoader.execute(request)

                if (result is SuccessResult) {
                    val bitmap = (result.drawable as? BitmapDrawable)?.bitmap
                    if (bitmap != null) {
                        val extracted = extractAppleMusicPalette(bitmap)
                        if (extracted != null) {
                            colors = extracted
                            Log.d(
                                "VANTA_COLORS",
                                "apple-music palette top=#${extracted.topColor.value.toUInt().toString(16)} seed=$seed"
                            )
                            return@withContext
                        }
                    }
                }
            } catch (e: Exception) {
                Log.w("VANTA_COLORS", "color extraction failed for seed=$seed: ${e.message}")
            }
        }

        val fallback = artworkColorFromSeed(seed)
        colors = ArtworkGradientColors(
            topColor = darkenForBackground(fallback, 0.34f),
            midColor = darkenForBackground(fallback, 0.2f),
            bottomColor = Color.Black,
            accentColor = fallback,
            isExtracted = false
        )
    }

    return colors
}
