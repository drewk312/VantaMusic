package com.audiophile.musicplayer.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.audiophile.musicplayer.common.AcceptanceTruth
import com.audiophile.musicplayer.data.display.DisplayMetadataCleaner
import com.audiophile.musicplayer.playback.NowPlayingState

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
    val displayArtist = cleaned.artist.ifBlank { rawArtist ?: "Unknown Artist" }
    AcceptanceTruth.mini(
        trackId = nowPlayingState.trackId,
        title = displayTitle,
        artist = displayArtist,
        isPlaying = nowPlayingState.isPlaying,
        positionMs = nowPlayingState.positionMs,
        durationMs = nowPlayingState.durationMs,
        artworkPresent = !artworkUrl.isNullOrBlank()
    )

    if (rawTitle.isNullOrBlank()) return
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(VantaChrome.miniPlayerHeight)
            .clip(RoundedCornerShape(18.dp))
            .background(AppChrome)
            .border(0.5.dp, Color.White.copy(alpha = 0.10f), RoundedCornerShape(18.dp))
    ) {
        Row(
            Modifier.fillMaxSize().padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                Modifier.weight(1f).fillMaxHeight().clickable(onClickLabel = "Open now playing", onClick = onOpen),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                NetworkArtwork(
                    artworkUrl = artworkUrl,
                    seed = displayTitle,
                    modifier = Modifier.size(46.dp).clip(RoundedCornerShape(10.dp))
                )
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        displayTitle,
                        color = AppText,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        displayArtist,
                        color = AppTextSecondary,
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            androidx.compose.material3.IconButton(onClick = onPrevious, modifier = Modifier.size(44.dp)) {
                Icon(
                    Icons.Filled.SkipPrevious,
                    contentDescription = "Previous",
                    tint = AppTextSecondary,
                    modifier = Modifier.size(24.dp)
                )
            }
            androidx.compose.material3.IconButton(onClick = onTogglePlayPause, modifier = Modifier.size(48.dp)) {
                if (nowPlayingState.isBuffering) androidx.compose.material3.CircularProgressIndicator(
                    Modifier.size(22.dp),
                    color = AppAccent,
                    strokeWidth = 2.dp
                ) else Icon(
                    if (nowPlayingState.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                    contentDescription = if (nowPlayingState.isPlaying) "Pause" else "Play",
                    tint = AppText,
                    modifier = Modifier.size(28.dp)
                )
            }
            androidx.compose.material3.IconButton(onClick = onNext, modifier = Modifier.size(48.dp)) {
                Icon(
                    Icons.Filled.SkipNext,
                    contentDescription = "Next",
                    tint = AppTextSecondary,
                    modifier = Modifier.size(26.dp)
                )
            }
        }
        if (nowPlayingState.durationMs > 0) {
            Box(
                Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .height(2.dp)
                    .background(AppText.copy(alpha = 0.08f))
            ) {
                Box(
                    Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(
                            (nowPlayingState.positionMs.toFloat() / nowPlayingState.durationMs).coerceIn(0f, 1f)
                        )
                        .background(AppAccent)
                )
            }
        }
    }
}
