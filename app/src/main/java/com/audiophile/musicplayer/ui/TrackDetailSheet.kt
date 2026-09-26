package com.audiophile.musicplayer.ui

import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.audiophile.musicplayer.data.display.DisplayMetadataCleaner
import com.audiophile.musicplayer.data.metadata.EnhancedMetadata
import com.audiophile.musicplayer.playback.NowPlayingState

@Composable
fun TrackDetailSheet(
    nowPlayingState: NowPlayingState,
    enhancedMetadata: EnhancedMetadata?,
    onDismiss: () -> Unit,
    onViewArtist: () -> Unit,
    onViewAlbum: () -> Unit,
    onSongRadio: () -> Unit,
    onAddToQueue: () -> Unit,
    onPlayNext: () -> Unit,
    onSaveToLibrary: () -> Unit,
    onShare: () -> Unit,
    onAddToPlaylist: () -> Unit = {},
    onSleepTimer: () -> Unit = {},
    onSourceDetails: () -> Unit = {},
    showDiagnostics: Boolean = false
) {
    val cleaned = remember(nowPlayingState.title, nowPlayingState.artist) {
        DisplayMetadataCleaner.computeDisplayMetadata(
            rawTitle = nowPlayingState.title.orEmpty(),
            rawArtist = nowPlayingState.artist.orEmpty(),
            rawAlbum = nowPlayingState.album
        )
    }
    val sheetTitle = cleaned.title.ifBlank { nowPlayingState.title ?: "Unknown" }
    val sheetArtist = cleaned.artist.ifBlank { nowPlayingState.artist ?: "Unknown" }

    VantaBottomSheet(visible = true, onDismiss = onDismiss) {
        // Track info header
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .border(0.5.dp, AppOutline, RoundedCornerShape(12.dp))
            ) {
                NetworkArtwork(
                    artworkUrl = nowPlayingState.artworkUrl ?: enhancedMetadata?.artworkUrl,
                    seed = nowPlayingState.title ?: "Track",
                    modifier = Modifier.fillMaxSize()
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(sheetTitle, color = AppText, fontSize = 17.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (enhancedMetadata?.explicit == true) VantaExplicitBadge()
                    VantaQualityBadge(nowPlayingState.qualityInfo?.bestQualityLabel())
                }
                Text(sheetArtist, color = AppTextSecondary, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }

        Spacer(Modifier.height(20.dp))
        VantaSheetDivider()

        Column(modifier = Modifier.padding(vertical = 8.dp)) {
            VantaSheetAction(icon = Icons.Filled.LibraryAdd, label = "Add to Library", onClick = {
                Log.d("VANTA_ACTION_MENU", "clicked action='add_to_library'")
                onSaveToLibrary()
            })
            VantaSheetAction(icon = Icons.AutoMirrored.Filled.PlaylistAdd, label = "Add to Playlist", onClick = {
                Log.d("VANTA_ACTION_MENU", "clicked action='add_to_playlist'")
                onAddToPlaylist()
            })
            VantaSheetDivider()
            VantaSheetAction(icon = Icons.Filled.Radio, label = "Start Radio", onClick = {
                Log.d("VANTA_ACTION_MENU", "clicked action='start_radio'")
                onSongRadio()
            })
            VantaSheetAction(icon = Icons.Filled.Person, label = "View Artist", onClick = {
                Log.d("VANTA_ACTION_MENU", "clicked action='view_artist'")
                onViewArtist()
            })
            VantaSheetAction(icon = Icons.Filled.Album, label = "View Album", onClick = {
                Log.d("VANTA_ACTION_MENU", "clicked action='view_album'")
                onViewAlbum()
            })
            VantaSheetDivider()
            VantaSheetAction(icon = Icons.Filled.SkipNext, label = "Play Next", onClick = {
                Log.d("VANTA_ACTION_MENU", "clicked action='play_next'")
                onPlayNext()
            })
            VantaSheetAction(icon = Icons.AutoMirrored.Filled.QueueMusic, label = "Add to Queue", onClick = {
                Log.d("VANTA_ACTION_MENU", "clicked action='add_to_queue'")
                onAddToQueue()
            })
            VantaSheetDivider()
            VantaSheetAction(icon = Icons.Filled.Timer, label = "Sleep Timer", onClick = {
                Log.d("VANTA_ACTION_MENU", "clicked action='sleep_timer'")
                onSleepTimer()
            })
            VantaSheetAction(icon = Icons.Filled.Share, label = "Share", onClick = {
                Log.d("VANTA_ACTION_MENU", "clicked action='share'")
                onShare()
            })
            VantaSheetDivider()
            VantaSheetAction(
                icon = Icons.Filled.Info,
                label = "View File Info",
                subtitle = "Inspect audio codec, bitrate, sample rate & path",
                onClick = {
                    Log.d("VANTA_ACTION_MENU", "clicked action='view_file_info'")
                    onSourceDetails()
                }
            )
        }

        if (enhancedMetadata != null) {
            VantaSheetDivider()
            Column(
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                if (enhancedMetadata.releaseYear != null) {
                    CreditsRow("Released", enhancedMetadata.releaseYear.toString())
                }
                if (enhancedMetadata.genres.isNotEmpty()) {
                    CreditsRow("Genre", enhancedMetadata.genres.joinToString(", "))
                }
                val isrcValue = enhancedMetadata.isrc
                if (!isrcValue.isNullOrBlank()) {
                    CreditsRow("ISRC", isrcValue)
                }
                if (nowPlayingState.durationMs > 0) {
                    CreditsRow("Duration", formatDuration(nowPlayingState.durationMs))
                }
                val qualityRowLabel = nowPlayingState.qualityInfo?.bestQualityLabel()
                if (qualityRowLabel != null) {
                    CreditsRow("Quality", qualityRowLabel)
                }
                if (enhancedMetadata.syncedLyricsAvailable || enhancedMetadata.lyricsAvailable) {
                    CreditsRow("Lyrics", "Available")
                }
            }
        }
    }
}

@Composable
private fun CreditsRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = AppTextSecondary, fontSize = 13.sp)
        Text(value, color = AppText, fontSize = 13.sp, fontWeight = FontWeight.Medium)
    }
}
