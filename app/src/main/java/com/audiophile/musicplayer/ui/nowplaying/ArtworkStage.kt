package com.audiophile.musicplayer.ui.nowplaying

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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.audiophile.musicplayer.ui.AppAccent
import com.audiophile.musicplayer.ui.AppBackgroundBottom
import com.audiophile.musicplayer.ui.AppText
import com.audiophile.musicplayer.ui.NetworkArtwork
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
    artworkSize: Dp? = null
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
                    modifier = artModifier
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
    val seedTone = remember(seed) {
        val hash = seed.hashCode()
        val warm = ((hash ushr 8) and 0xFF) / 255f
        Color(red = 0.10f + warm * 0.035f, green = 0.095f + warm * 0.025f, blue = 0.105f + warm * 0.035f)
    }
    Box(
        modifier = modifier.background(
            Brush.linearGradient(
                colors = listOf(seedTone, Color(0xFF161316), Color(0xFF09090B)),
                start = Offset.Zero, end = Offset.Infinite
            )
        ),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.matchParentSize()) {
            val lineColor = Color.White.copy(alpha = 0.035f)
            val accentLine = accentColor.copy(alpha = 0.08f)
            for (index in 0..8) {
                val x = size.width * (index / 8f)
                drawLine(
                    color = if (index % 3 == 0) accentLine else lineColor,
                    start = Offset(x, size.height * 0.02f),
                    end = Offset(x - size.width * 0.28f, size.height * 0.98f),
                    strokeWidth = 1.dp.toPx()
                )
            }
            drawRoundRect(
                brush = Brush.verticalGradient(
                    listOf(Color.White.copy(alpha = 0.08f), Color.Transparent, Color.Black.copy(alpha = 0.24f))
                ),
                size = size,
                cornerRadius = CornerRadius(18.dp.toPx(), 18.dp.toPx())
            )
        }
        Box(
            modifier = Modifier.size(92.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.045f))
                .border(0.5.dp, Color.White.copy(alpha = 0.08f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Filled.MusicNote, contentDescription = null, tint = AppText.copy(alpha = 0.54f), modifier = Modifier.size(46.dp))
        }
        Box(
            modifier = Modifier.matchParentSize().background(
                Brush.verticalGradient(listOf(Color.Transparent, AppBackgroundBottom.copy(alpha = 0.28f)))
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
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
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
        Row(
            modifier = Modifier.align(Alignment.TopEnd).padding(14.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = if (isFavorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                contentDescription = if (isFavorite) "Remove from favorites" else "Add to favorites",
                tint = if (isFavorite) AppAccent else Color.White.copy(alpha = 0.75f),
                modifier = Modifier.size(40.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.28f))
                    .clickable(onClick = onToggleFavorite).padding(9.dp)
            )
        }
    }
}
