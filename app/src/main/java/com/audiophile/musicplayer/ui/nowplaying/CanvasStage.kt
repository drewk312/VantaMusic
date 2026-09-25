package com.audiophile.musicplayer.ui.nowplaying

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.audiophile.musicplayer.audio.visualizer.VantaAudioFrame
import com.audiophile.musicplayer.ui.AppAccent
import com.audiophile.musicplayer.ui.AppAccentSecondary
import com.audiophile.musicplayer.ui.AppSurfaceRaised
import com.audiophile.musicplayer.ui.AppText
import com.audiophile.musicplayer.ui.preview.ArtworkPlaceholder
import com.audiophile.musicplayer.ui.rememberArtworkGradientColors
import kotlin.math.cos
import kotlin.math.sin

/**
 * VANTA Canvas Stage:
 * An immersive, full-height motion canvas designed in the spirit of Spotify Canvas.
 * Features:
 * - Fluid Ken Burns breathing zoom and slow pan
 * - Audio-reactive beat kick based on bass energy & audio frames
 * - Atmospheric particle motes and luminous edge aura
 * - Subtle luxury "CANVAS" live status badge
 */
@Composable
fun VantaCanvasStage(
    coverArtUrl: String?,
    seed: String,
    hasArtwork: Boolean,
    isPlaying: Boolean,
    accentColor: Color,
    audioFrame: VantaAudioFrame? = null,
    modifier: Modifier = Modifier,
    artworkSize: Dp? = null,
    onTap: (() -> Unit)? = null
) {
    val artworkColors = rememberArtworkGradientColors(artworkUrl = coverArtUrl, seed = seed)
    val effectiveAccent = if (hasArtwork) artworkColors.accentColor else accentColor

    // Ken Burns slow cinematic zoom
    val infiniteTransition = rememberInfiniteTransition(label = "canvasMotion")
    val zoomScale by infiniteTransition.animateFloat(
        initialValue = 1.04f,
        targetValue = 1.14f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 14000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "canvasZoom"
    )

    // Gentle floating translation drift
    val driftX by infiniteTransition.animateFloat(
        initialValue = -12f,
        targetValue = 12f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 18000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "canvasDriftX"
    )
    val driftY by infiniteTransition.animateFloat(
        initialValue = -8f,
        targetValue = 8f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 12000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "canvasDriftY"
    )

    // Canvas badge pulsing light
    val badgePulse by infiniteTransition.animateFloat(
        initialValue = 0.5f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "canvasBadgePulse"
    )

    // Real-time audio reactive scale bump
    val audioBump = remember(audioFrame, isPlaying) {
        if (!isPlaying || audioFrame == null) 0f
        else (audioFrame.bassEnergy * 0.06f + audioFrame.transientEnergy * 0.04f).coerceIn(0f, 0.10f)
    }

    val particlePhase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 10000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "canvasParticles"
    )

    val stageShape = RoundedCornerShape(26.dp)

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .then(
                if (onTap != null) {
                    Modifier.clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onTap
                    )
                } else Modifier
            ),
        contentAlignment = Alignment.Center
    ) {
        val containerWidth = maxWidth
        val containerHeight = maxHeight
        val isLandscape = containerWidth > containerHeight

        val cardModifier = if (isLandscape) {
            Modifier
                .fillMaxHeight(0.96f)
                .aspectRatio(9f / 14f)
        } else {
            Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.96f)
        }

        Box(
            modifier = cardModifier
                .shadow(
                    elevation = 28.dp,
                    shape = stageShape,
                    spotColor = effectiveAccent.copy(alpha = 0.35f),
                    ambientColor = Color.Black
                )
                .clip(stageShape)
                .background(Color(0xFF0A090D))
                .border(1.dp, Color.White.copy(alpha = 0.12f), stageShape)
        ) {
            // Main Canvas Media / Artwork Layer
            if (coverArtUrl != null) {
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(coverArtUrl)
                        .crossfade(true)
                        .size(1080)
                        .build(),
                    contentDescription = "Canvas Artwork",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            scaleX = (zoomScale + audioBump)
                            scaleY = (zoomScale + audioBump)
                            translationX = driftX
                            translationY = driftY
                        }
                )
            } else {
                ArtworkPlaceholder(
                    seed = seed,
                    accentColor = effectiveAccent,
                    modifier = Modifier.fillMaxSize()
                )
            }

            // Atmospheric Ambient Vignette
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.Black.copy(alpha = 0.35f),
                                Color.Transparent,
                                Color.Black.copy(alpha = 0.25f),
                                Color.Black.copy(alpha = 0.75f)
                            )
                        )
                    )
            )

            // Accent light flare overlay
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.radialGradient(
                            colors = listOf(
                                effectiveAccent.copy(alpha = 0.18f),
                                Color.Transparent
                            ),
                            center = Offset(driftX * 10f + 200f, driftY * 10f + 300f),
                            radius = 600f
                        )
                    )
            )

            // Floating luminous atmospheric particle motes
            Canvas(modifier = Modifier.fillMaxSize()) {
                val canvasW = size.width
                val canvasH = size.height
                if (canvasW > 0 && canvasH > 0) {
                    val count = 14
                    for (i in 0 until count) {
                        val seedX = ((i * 73) % 100) / 100f
                        val speedMult = 0.5f + (((i * 37) % 50) / 100f)
                        val progress = (particlePhase * speedMult + seedX) % 1f
                        val y = canvasH * (1f - progress)
                        val x = (seedX * canvasW) + sin(progress * 6.28f + i) * 24f
                        val radius = (1.5f + (i % 3) * 1.2f).dp.toPx()
                        val alpha = (sin(progress * 3.14159f) * 0.45f).coerceIn(0f, 1f)

                        drawCircle(
                            color = if (i % 2 == 0) effectiveAccent else Color.White,
                            radius = radius,
                            center = Offset(x, y),
                            alpha = alpha
                        )
                    }
                }
            }

            // Top luxury "CANVAS" pill indicator
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(start = 16.dp, top = 16.dp)
                    .clip(CircleShape)
                    .background(AppSurfaceRaised.copy(alpha = 0.72f))
                    .border(0.5.dp, Color.White.copy(alpha = 0.16f), CircleShape)
                    .padding(horizontal = 10.dp, vertical = 5.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(7.dp)
                            .clip(CircleShape)
                            .background(effectiveAccent.copy(alpha = badgePulse))
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = "CANVAS",
                        color = AppText,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.6.sp
                    )
                }
            }
        }
    }
}
