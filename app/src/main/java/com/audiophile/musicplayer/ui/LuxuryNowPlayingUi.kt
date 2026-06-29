package com.audiophile.musicplayer.ui

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest

/**
 * Heavily blurred album art + dark gradient — Apple Music / Spotify-style ambient stage.
 */
@Composable
fun LuxuryBlurredArtworkBackground(
    artworkUrl: String?,
    seed: String,
    topColor: Color,
    midColor: Color,
    accentColor: Color,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val url = artworkUrl?.takeIf { it.startsWith("http://") || it.startsWith("https://") }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        if (url != null) {
            val imageModifier = Modifier
                .fillMaxSize()
                .scale(1.18f)
                .then(
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        Modifier.blur(84.dp)
                    } else {
                        Modifier.graphicsLayer {
                            alpha = 0.94f
                            scaleX = 1.40f
                            scaleY = 1.40f
                        }
                    }
                )

            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(url)
                    .crossfade(true)
                    .build(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = imageModifier
            )
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(topColor, midColor, Color.Black)
                        )
                    )
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.radialGradient(
                            colors = listOf(
                                accentColor.copy(alpha = 0.22f),
                                Color.Transparent
                            ),
                            radius = 900f
                        )
                    )
            )
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.42f))
        )

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colorStops = arrayOf(
                            0.0f to Color.Black.copy(alpha = 0.82f),
                            0.22f to Color.Black.copy(alpha = 0.16f),
                            0.55f to Color.Transparent,
                            1.0f to Color.Black.copy(alpha = 0.92f)
                        )
                    )
                )
        )

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            accentColor.copy(alpha = 0.10f),
                            Color.Transparent,
                            Color.Black.copy(alpha = 0.24f)
                        ),
                        radius = 1280f
                    )
                )
        )
    }
}
