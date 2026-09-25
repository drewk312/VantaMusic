package com.audiophile.musicplayer.ui.nowplaying

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.Icon
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.audiophile.musicplayer.ui.AppAccent
import com.audiophile.musicplayer.ui.AppAccentSecondary
import com.audiophile.musicplayer.ui.AppBackgroundBottom
import com.audiophile.musicplayer.ui.AppText
import com.audiophile.musicplayer.ui.NetworkArtwork
import com.audiophile.musicplayer.ui.preview.ArtworkPlaceholder
import kotlin.math.PI
import kotlin.math.sin

@Composable
fun rememberMotionPhase(active: Boolean, durationMillis: Int, label: String): Float {
    if (!active) return 0f
    val transition = rememberInfiniteTransition(label = label)
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = durationMillis, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "${label}Value"
    )
    return phase
}

fun beatValue(phase: Float): Float {
    val normalized = phase - phase.toInt()
    return ((sin(normalized * PI.toFloat() * 2f) + 1f) / 2f).coerceIn(0f, 1f)
}

@Composable
fun VantaArtworkStage(
    coverArtUrl: String?,
    seed: String,
    hasArtwork: Boolean,
    isPlaying: Boolean,
    animatedArtworkEnabled: Boolean,
    accentColor: Color,
    isFavorite: Boolean,
    onToggleFavorite: () -> Unit,
    modifier: Modifier = Modifier,
    artworkSize: Dp? = null,
    isSquare: Boolean = true,
    showQualityBadge: Boolean = false,
    qualityLabel: String? = null,
    onQualityBadgeClick: (() -> Unit)? = null
) {
    Box(modifier = modifier) {
        val artModifier = artworkSize?.let { size -> Modifier.size(size) }
            ?: Modifier.fillMaxHeight(0.78f).aspectRatio(1f)

        Column(
            modifier = Modifier.fillMaxSize().padding(horizontal = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Top
        ) {
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.TopCenter
            ) {
                PremiumArtworkGlow(
                    phase = rememberMotionPhase(animatedArtworkEnabled && isPlaying, 1800, "vantaArtworkGlow"),
                    isPlaying = isPlaying,
                    accentColor = accentColor,
                    hasArtwork = hasArtwork,
                    modifier = Modifier.size((artworkSize ?: 360.dp) + 88.dp)
                )
                MainStageHeroCard(
                    coverArtUrl = coverArtUrl,
                    seed = seed,
                    hasArtwork = hasArtwork,
                    isPlaying = isPlaying,
                    animatedArtworkEnabled = animatedArtworkEnabled,
                    accentColor = accentColor,
                    isFavorite = isFavorite,
                    onToggleFavorite = onToggleFavorite,
                    modifier = artModifier,
                    isSquare = isSquare,
                    showQualityBadge = showQualityBadge,
                    qualityLabel = qualityLabel,
                    onQualityBadgeClick = onQualityBadgeClick
                )
            }
        }
    }
}

@Composable
fun PremiumArtworkGlow(
    phase: Float,
    isPlaying: Boolean,
    accentColor: Color,
    hasArtwork: Boolean,
    modifier: Modifier = Modifier
) {
    val beat = if (isPlaying) beatValue(phase) else 0f
    Canvas(modifier = modifier.graphicsLayer(alpha = if (hasArtwork) 1.0f else 0.5f)) {
        val glowRadius = size.minDimension * (0.55f + beat * 0.04f)
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    accentColor.copy(alpha = if (hasArtwork) 0.18f else 0.08f),
                    accentColor.copy(alpha = 0.035f),
                    Color.Transparent
                ),
                center = Offset(size.width / 2f, size.height / 2f),
                radius = glowRadius
            ),
            center = Offset(size.width / 2f, size.height / 2f),
            radius = glowRadius
        )
    }
}

@Composable
fun LuxuryArtworkPlaceholder(
    seed: String,
    accentColor: Color,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        ArtworkPlaceholder(
            seed = seed,
            showInitials = true,
            accentColor = accentColor,
            modifier = Modifier.fillMaxSize()
        )
        Box(
            modifier = Modifier.matchParentSize().background(
                Brush.verticalGradient(
                    listOf(
                        Color.White.copy(alpha = 0.04f),
                        Color.Transparent,
                        AppBackgroundBottom.copy(alpha = 0.22f)
                    )
                )
            )
        )
    }
}

@Composable
fun MainStageHeroCard(
    coverArtUrl: String?,
    seed: String,
    hasArtwork: Boolean,
    isPlaying: Boolean,
    animatedArtworkEnabled: Boolean,
    accentColor: Color,
    isFavorite: Boolean,
    onToggleFavorite: () -> Unit,
    modifier: Modifier = Modifier,
    isSquare: Boolean = true,
    showQualityBadge: Boolean = false,
    qualityLabel: String? = null,
    onQualityBadgeClick: (() -> Unit)? = null
) {
    val corner = if (isSquare) 12.dp else 26.dp
    val shape = RoundedCornerShape(corner)
    Box(
        modifier = modifier
            .shadow(
                elevation = 22.dp,
                shape = shape,
                ambientColor = Color.Black.copy(alpha = 0.48f),
                spotColor = accentColor.copy(alpha = 0.16f)
            )
            .clip(shape)
            .border(
                width = 0.75.dp,
                brush = Brush.linearGradient(
                    listOf(
                        Color.White.copy(alpha = 0.34f),
                        accentColor.copy(alpha = 0.18f),
                        AppAccentSecondary.copy(alpha = 0.12f),
                        Color.White.copy(alpha = 0.06f)
                    )
                ),
                shape = shape
            )
    ) {
        if (hasArtwork && coverArtUrl != null) {
            NetworkArtwork(artworkUrl = coverArtUrl, seed = seed, modifier = Modifier.fillMaxSize())
        } else {
            LuxuryArtworkPlaceholder(seed = seed, accentColor = accentColor, modifier = Modifier.fillMaxSize())
        }
        Box(
            modifier = Modifier.fillMaxSize().background(
                Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.12f), Color.Black.copy(alpha = 0.42f)))
            )
        )
        // Double-tap anywhere on the artwork to love this track — same truth as
        // the heart controls, with a large center heart flash for feedback.
        var heartFlashProgress by remember { mutableStateOf(0f) }
        LaunchedEffect(isFavorite, onToggleFavorite) {
            if (isFavorite) {
                heartFlashProgress = 0f
                animate(0f, 1f, animationSpec = tween(620)) { value, _ -> heartFlashProgress = value }
            } else {
                heartFlashProgress = 0f
            }
        }
        if (heartFlashProgress > 0f) {
            val flashScale = 0.6f + 0.7f * heartFlashProgress
            val flashAlpha = (1f - heartFlashProgress).coerceIn(0f, 1f)
            Icon(
                imageVector = Icons.Filled.Favorite,
                contentDescription = null,
                tint = AppAccent.copy(alpha = flashAlpha * 0.92f),
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(120.dp)
                    .graphicsLayer { scaleX = flashScale; scaleY = flashScale },
            )
        }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(onToggleFavorite) {
                    detectTapGestures(
                        onDoubleTap = {
                            android.util.Log.d("VANTA_UI_ACTION", "control='nowplaying_doubletap_like' result='${if (isFavorite) "unlike" else "like"}'")
                            onToggleFavorite()
                        }
                    )
                }
        )
        if (showQualityBadge && !qualityLabel.isNullOrBlank() && onQualityBadgeClick != null) {
            when {
                com.audiophile.musicplayer.ui.isDolbyAtmosLabel(qualityLabel) -> {
                    com.audiophile.musicplayer.ui.DolbyAtmosTag(
                        modifier = Modifier.align(Alignment.TopStart).padding(10.dp),
                        size = com.audiophile.musicplayer.ui.SpatialTagSize.Compact,
                        onClick = onQualityBadgeClick
                    )
                }
                com.audiophile.musicplayer.ui.isSony360Label(qualityLabel) -> {
                    com.audiophile.musicplayer.ui.Sony360Tag(
                        modifier = Modifier.align(Alignment.TopStart).padding(10.dp),
                        size = com.audiophile.musicplayer.ui.SpatialTagSize.Compact,
                        onClick = onQualityBadgeClick
                    )
                }
                else -> {
                    Row(
                        modifier = Modifier.align(Alignment.TopStart).padding(10.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color.Black.copy(alpha = 0.62f))
                            .border(0.5.dp, accentColor.copy(alpha = 0.35f), RoundedCornerShape(8.dp))
                            .clickable(onClick = onQualityBadgeClick)
                            .padding(horizontal = 8.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Box(
                            modifier = Modifier.size(6.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF72DDF7))
                        )
                        androidx.compose.material3.Text(
                            text = qualityLabel.uppercase(),
                            color = Color.White.copy(alpha = 0.92f),
                            fontSize = androidx.compose.ui.unit.TextUnit(9f, androidx.compose.ui.unit.TextUnitType.Sp),
                            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                            letterSpacing = 0.6.sp
                        )
                    }
                }
            }
        }
        Row(
            modifier = Modifier.align(Alignment.TopEnd).padding(14.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = if (isFavorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                contentDescription = if (isFavorite) "Remove from favorites" else "Add to favorites",
                tint = if (isFavorite) AppAccent else Color.White.copy(alpha = 0.84f),
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(Color(0xB312141B))
                    .border(0.75.dp, Color.White.copy(alpha = 0.20f), CircleShape)
                    .clickable(onClick = onToggleFavorite)
                    .padding(9.dp)
            )
        }
    }
}
