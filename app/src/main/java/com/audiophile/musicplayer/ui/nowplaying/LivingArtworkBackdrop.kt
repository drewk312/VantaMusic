package com.audiophile.musicplayer.ui.nowplaying

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.audiophile.musicplayer.ui.rememberArtworkGradientColors
import com.audiophile.musicplayer.audio.visualizer.VantaAudioFrame

@Composable
fun LivingArtworkBackdrop(
    artworkUrl: String?,
    seed: String,
    audioFrame: VantaAudioFrame?,
    mood: TrackMood,
    moodEnergy: Float,
    modifier: Modifier = Modifier
) {
    val artworkColors = rememberArtworkGradientColors(artworkUrl = artworkUrl, seed = seed)

    val driftTransition = rememberInfiniteTransition(label = "drift")
    val driftX by driftTransition.animateFloat(
        initialValue = -4f, targetValue = 4f,
        animationSpec = infiniteRepeatable(tween(12000, easing = LinearEasing), RepeatMode.Reverse),
        label = "driftX"
    )
    val driftY by driftTransition.animateFloat(
        initialValue = -3f, targetValue = 3f,
        animationSpec = infiniteRepeatable(tween(15000, easing = LinearEasing), RepeatMode.Reverse),
        label = "driftY"
    )

    val beatGlowMultiplier = remember(audioFrame) {
        val beat = audioFrame?.let { it.bassEnergy * 0.6f + it.transientEnergy * 0.4f } ?: 0f
        1f + beat * 0.08f
    }

    val moodOverlayColor = remember(mood) {
        when (mood) {
            TrackMood.CALM -> Color(0x1A89CFF0)
            TrackMood.ENERGETIC -> Color(0x1AFF8C00)
            TrackMood.DARK -> Color(0x2A0A0015)
            TrackMood.NOSTALGIC -> Color(0x1AD4A017)
            TrackMood.ROMANTIC -> Color(0x1AFFB6C1)
            TrackMood.AGGRESSIVE -> Color(0x2AFF0000)
            TrackMood.CELEBRATORY -> Color(0x1AFFD700)
            TrackMood.UNKNOWN -> Color(0x02000000)
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        if (artworkUrl != null) {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(artworkUrl)
                    .crossfade(true)
                    .size(480)
                    .build(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        scaleX = 1.18f * beatGlowMultiplier
                        scaleY = 1.18f * beatGlowMultiplier
                        translationX = driftX
                        translationY = driftY
                    }
                    .blur(64.dp)
            )
        } else {
            // Neutral charcoal when cover art is missing — avoid seed-tinted greens.
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                Color(0xFF16141A),
                                Color(0xFF0C0B0F),
                                Color(0xFF07070A)
                            )
                        )
                    )
            )
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(moodOverlayColor)
        )

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.linearGradient(
                        colors = listOf(
                            Color.White.copy(alpha = 0.04f),
                            Color.Transparent,
                            Color.Black.copy(alpha = 0.12f)
                        ),
                        start = Offset.Zero,
                        end = Offset.Infinite
                    )
                )
        )

        // Lighter veil so blurred cover + aura visualizer stay readable.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Black.copy(alpha = 0.28f),
                            Color.Black.copy(alpha = 0.10f),
                            Color.Black.copy(alpha = 0.26f),
                            Color.Black.copy(alpha = 0.72f)
                        )
                    )
                )
        )

        if (audioFrame != null && audioFrame.bassEnergy > 0.18f) {
            val glowAlpha = (audioFrame.bassEnergy * 0.28f).coerceAtMost(0.42f)
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.radialGradient(
                            colors = listOf(
                                artworkColors.accentColor.copy(alpha = glowAlpha),
                                artworkColors.topColor.copy(alpha = glowAlpha * 0.35f),
                                Color.Transparent
                            )
                        )
                    )
            )
        }
    }
}
