@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
package com.audiophile.musicplayer.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.audiophile.musicplayer.data.display.DisplayMetadataCleaner
import com.audiophile.musicplayer.playback.NowPlayingState
import kotlin.math.PI
import kotlin.math.sin

@Composable
fun MiniPlayer(
    nowPlayingState: NowPlayingState,
    onOpen: () -> Unit,
    onTogglePlayPause: () -> Unit,
    onNext: () -> Unit,
    modifier: Modifier = Modifier,
    onPrevious: () -> Unit = {},
    onToggleFavorite: () -> Unit = {},
    animatedArtworkEnabled: Boolean = true,
    pulseHint: String? = null,
    queueSnapshot: com.audiophile.musicplayer.playback.QueueSnapshot? = null
) {
    val queueTrack = queueSnapshot?.currentTrack?.track
    val rawTitle = nowPlayingState.title?.takeIf { it.isNotBlank() }
        ?: queueTrack?.title?.takeIf { it.isNotBlank() }
    val rawArtist = nowPlayingState.artist?.takeIf { it.isNotBlank() }
        ?: queueTrack?.artist?.takeIf { it.isNotBlank() }
    val artworkUrl = nowPlayingState.artworkUrl?.takeIf { it.isNotBlank() }
        ?: queueTrack?.coverArtUrl?.takeIf { it.isNotBlank() }

    val cleaned = remember(rawTitle, rawArtist) {
        DisplayMetadataCleaner.computeDisplayMetadata(
            rawTitle = rawTitle.orEmpty(),
            rawArtist = rawArtist.orEmpty(),
            rawAlbum = nowPlayingState.album ?: queueTrack?.albumName
        )
    }
    val displayTitle = DisplayMetadataCleaner.cleanMiniBarTitle(
        cleaned.title.ifBlank { rawTitle ?: "Unknown Title" }
    )
    val displayArtist = buildString {
        append(cleaned.artist.ifBlank { rawArtist ?: "Unknown Artist" })
        if (nowPlayingState.featuredArtists.isNotEmpty()) {
            append(" feat. ")
            append(nowPlayingState.featuredArtists.joinToString(", "))
        }
    }
    if (displayTitle == "Unknown Title" || displayArtist == "Unknown Artist") {
        android.util.Log.d(
            "VANTA_METADATA_FALLBACK",
            "source=${when {
                nowPlayingState.title != null -> "nowPlaying"
                queueTrack != null -> "queue"
                else -> "none"
            }} title=$displayTitle artist=$displayArtist"
        )
    }

    val playable = rawTitle?.isNotBlank() == true

    val miniShape = RoundedCornerShape(16.dp)
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(VantaChrome.miniPlayerHeight)
            .glassSurfaceElevated(shape = miniShape, surfaceAlpha = 0.96f)
            .clickable(onClick = onOpen)
            .drawBehind {
                val goldLineHeight = 1.5.dp.toPx()
                drawRect(
                    brush = Brush.horizontalGradient(
                        colors = listOf(
                            AppAccent.copy(alpha = 0.0f),
                            AppAccent.copy(alpha = 0.50f),
                            AppAccent.copy(alpha = 0.0f)
                        ),
                        startX = size.width * 0.15f,
                        endX = size.width * 0.85f
                    ),
                    topLeft = Offset(0f, 0f),
                    size = androidx.compose.ui.geometry.Size(size.width, goldLineHeight)
                )
            }
    ) {
        if (playable) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight()
                    .padding(start = 14.dp, end = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(46.dp)
                            .clip(RoundedCornerShape(14.dp))
                    ) {
                        NetworkArtwork(
                            artworkUrl = artworkUrl,
                            seed = displayTitle,
                            modifier = Modifier.fillMaxSize()
                        )
                    }

                    Spacer(Modifier.width(10.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = displayTitle,
                            color = Color.White,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier
                                .fillMaxWidth()
                                .basicMarquee()
                        )
                        Text(
                            text = displayArtist,
                            color = Color.White.copy(alpha = 0.6f),
                            fontSize = 12.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier
                                .fillMaxWidth()
                                .basicMarquee(velocity = 14.dp)
                        )
                    }
                }

                Spacer(Modifier.width(4.dp))

                Icon(
                    imageVector = if (nowPlayingState.isFavorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                    contentDescription = "Favorite",
                    tint = if (nowPlayingState.isFavorite) AppAccent else Color.White.copy(alpha = 0.5f),
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .clickable { onToggleFavorite() }
                        .padding(6.dp)
                )

                Icon(
                    imageVector = Icons.Filled.SkipPrevious,
                    contentDescription = "Previous",
                    tint = Color.White.copy(alpha = 0.7f),
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .clickable { onPrevious() }
                        .padding(8.dp)
                )

                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(AppAccent.copy(alpha = 0.16f))
                        .border(0.5.dp, AppAccent.copy(alpha = 0.28f), CircleShape)
                        .clickable(onClick = onTogglePlayPause),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (nowPlayingState.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                        contentDescription = "Play/Pause",
                        tint = Color.White,
                        modifier = Modifier.size(28.dp)
                    )
                }

                Icon(
                    imageVector = Icons.Filled.SkipNext,
                    contentDescription = "Next",
                    tint = Color.White.copy(alpha = 0.7f),
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .clickable { onNext() }
                        .padding(8.dp)
                )
            }
        }

        MiniPlayerWaveform(
            isPlaying = nowPlayingState.isPlaying,
            progress = if (nowPlayingState.durationMs > 0L)
                (nowPlayingState.positionMs.toFloat() / nowPlayingState.durationMs).coerceIn(0f, 1f)
            else 0f,
            barColor = AppAccent,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(2.dp)
        )
    }
}

@Composable
private fun MiniPlayerWaveform(
    isPlaying: Boolean,
    progress: Float,
    barColor: Color,
    modifier: Modifier = Modifier
) {
    val transition = rememberInfiniteTransition(label = "mini_wave_transition")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = (2 * PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "mini_wave_phase"
    )

    val targetAlpha by animateFloatAsState(
        targetValue = if (isPlaying) 1f else 0.3f,
        animationSpec = tween(300),
        label = "mini_wave_alpha"
    )

    Canvas(modifier = modifier) {
        val barCount = 120
        val barWidth = size.width / barCount
        val centerY = size.height / 2f
        
        val progressX = progress * size.width

        for (i in 0 until barCount) {
            val barLeft = i * barWidth
            val isActive = barLeft <= progressX

            val wave = if (isPlaying) {
                sin(phase + i * 0.2f) * 0.5f + 0.5f
            } else 0f

            val barHeight = size.height * (0.4f + wave * 0.6f)
            val barTop = centerY - barHeight / 2f

            drawRect(
                color = if (isActive) barColor else barColor.copy(alpha = 0.2f),
                topLeft = Offset(barLeft, 0f),
                size = Size(barWidth, size.height)
            )
        }
    }
}
