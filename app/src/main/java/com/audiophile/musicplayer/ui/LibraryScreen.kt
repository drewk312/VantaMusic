package com.audiophile.musicplayer.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.remember
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.Icon
import androidx.compose.material3.TextButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.audiophile.musicplayer.data.display.DisplayMetadataCleaner
import com.audiophile.musicplayer.data.display.TrackDisplayResolver
import com.audiophile.musicplayer.data.display.VantaQualityInfo
import com.audiophile.musicplayer.data.local.entities.LocalSongEntity
import com.audiophile.musicplayer.data.local.entities.UnifiedTrackWithSources
import com.audiophile.musicplayer.data.source.SearchItemStatus
import com.audiophile.musicplayer.data.source.sourceValidityStatus
import com.audiophile.musicplayer.ui.NetworkArtwork

@Composable
fun LibraryScreen(
    uiState: MainUiState,
    onOpenSettings: () -> Unit,
    onOpenImports: () -> Unit,
    onToggleFavoriteSong: (Long) -> Unit,
    onPlay: (UnifiedTrackWithSources) -> Unit,
    onPlayNext: (UnifiedTrackWithSources) -> Unit,
    onAddToQueue: (UnifiedTrackWithSources) -> Unit,
    onDownload: (UnifiedTrackWithSources) -> Unit,
    onNavigateToArtist: (String, String?) -> Unit = { _, _ -> },
    onNavigateToAlbum: (String, String, String?) -> Unit = { _, _, _ -> },
    onOpenTrackSheet: ((UnifiedTrackWithSources) -> Unit)? = null,
    miniPlayerVisible: Boolean = false
) {
    val playable = uiState.library.filter { it.sources.any { source -> source.streamUrl.isNotBlank() } }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 24.dp)
            .padding(bottom = appBottomContentPadding(isMiniPlayerVisible = miniPlayerVisible)),
        verticalArrangement = Arrangement.spacedBy(32.dp)
    ) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("Library", color = AppText, fontSize = 32.sp, fontWeight = FontWeight.Black)
            TextButton(onClick = onOpenSettings) { Text("Settings", color = AppAccent) }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.fillMaxWidth()) {
            CountTile("Songs", uiState.localLibraryCounts.songsCount.toString(), Modifier.weight(1f))
            CountTile("Recent", uiState.localLibraryCounts.recentlyPlayedCount.toString(), Modifier.weight(1f))
            CountTile("Playlists", uiState.localLibraryCounts.playlistsCount.toString(), Modifier.weight(1f))
        }

        // Imported Music
        VantaCard(
            onClick = onOpenImports
        ) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column {
                    Text("Imported Music", color = AppText, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(4.dp))
                    Text("${uiState.importBatches.size} recently added", color = AppTextSecondary, fontSize = 14.sp)
                }
                Text("\u3009", color = AppAccent, fontSize = 24.sp, fontWeight = FontWeight.Bold)
            }
        }

        // Available Music
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            VantaSectionHeader("Available Music")
            if (playable.isEmpty()) {
                VantaEmptyState(
                    title = "No Playable Tracks",
                    description = "Search or import music to add playable tracks to your library.",
                    icon = Icons.Filled.LibraryMusic
                )
            } else {
                playable.take(12).forEach { track ->
                    PlayableTrackRow(
                        track, onPlay, onPlayNext, onAddToQueue, onDownload,
                        onNavigateToArtist,
                        onOpenTrackSheet = onOpenTrackSheet?.let { fn -> { fn(track) } }
                    )
                }
            }
        }

        // Saved Songs
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            VantaSectionHeader("Saved Songs")
            if (uiState.localSongs.isEmpty()) {
                VantaEmptyState(
                    title = "No Saved Songs",
                    description = "Saved imports and favorites will appear here.",
                    icon = Icons.Filled.FavoriteBorder
                )
            } else {
                uiState.localSongs.take(20).forEach { song ->
                    LocalSongRow(song, onToggleFavoriteSong, onNavigateToArtist)
                }
            }
        }
    }
}

@Composable
private fun PlayableTrackRow(
    track: UnifiedTrackWithSources,
    onPlay: (UnifiedTrackWithSources) -> Unit,
    onPlayNext: (UnifiedTrackWithSources) -> Unit,
    onAddToQueue: (UnifiedTrackWithSources) -> Unit,
    onDownload: (UnifiedTrackWithSources) -> Unit,
    onNavigateToArtist: (String, String?) -> Unit = { _, _ -> },
    onOpenTrackSheet: (() -> Unit)? = null
) {
    val display = remember(track.track) {
        TrackDisplayResolver.resolve(track.track)
    }
    val qualityLabel = remember(track.sources) {
        VantaQualityInfo.fromTrackSource(
            source = track.sources.maxByOrNull { it.bitrate },
            status = track.sourceValidityStatus()
        )?.bestQualityLabel()
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .vantaCard()
            .clickable { onPlay(track) }
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(RoundedCornerShape(12.dp))
                .border(0.5.dp, AppAccent.copy(alpha = 0.15f), RoundedCornerShape(12.dp))
        ) {
            NetworkArtwork(artworkUrl = display.artworkUrl, seed = track.track.title, modifier = Modifier.fillMaxSize())
        }
        Spacer(Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(display.title, color = AppText, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (display.explicit == true) VantaExplicitBadge()
                VantaQualityBadge(qualityLabel)
            }
            Text(display.artist, color = AppTextSecondary, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.clickable { onNavigateToArtist(display.artist, null) })
        }
        if (onOpenTrackSheet != null) {
            // Centralized More sheet — replaces scattered individual action buttons
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .clickable { onOpenTrackSheet() },
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Filled.MoreVert, contentDescription = "More options", tint = AppTextSecondary, modifier = Modifier.size(22.dp))
            }
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(modifier = Modifier.size(40.dp).clip(CircleShape).clickable { onPlayNext(track) }, contentAlignment = Alignment.Center) {
                    Icon(Icons.Filled.SkipNext, contentDescription = "Play Next", tint = AppTextSecondary, modifier = Modifier.size(22.dp))
                }
                Box(modifier = Modifier.size(40.dp).clip(CircleShape).clickable { onAddToQueue(track) }, contentAlignment = Alignment.Center) {
                    Icon(Icons.Filled.QueueMusic, contentDescription = "Add to Queue", tint = AppTextSecondary, modifier = Modifier.size(22.dp))
                }
            }
        }
    }
}

@Composable
private fun LocalSongRow(song: LocalSongEntity, onToggleFavoriteSong: (Long) -> Unit, onNavigateToArtist: (String, String?) -> Unit = { _, _ -> }) {
    val cleanTitle = remember(song.title, song.artist) {
        DisplayMetadataCleaner.computeDisplayMetadata(song.title, song.artist ?: "", null).title
    }
    val cleanArtist = remember(song.artist) {
        DisplayMetadataCleaner.cleanArtistName(song.artist) ?: song.artist
    }
    val qualityLabel = remember(song.quality, song.streamUrl, song.sourceType) {
        VantaQualityInfo.fromSource(
            bitrate = null,
            quality = song.quality,
            mime = null,
            status = if (song.streamUrl.isNullOrBlank()) null else SearchItemStatus.LOCAL_PLAYABLE,
            isValidated = !song.streamUrl.isNullOrBlank(),
            sourceProviderId = song.sourceType.name.lowercase(),
            reason = if (song.streamUrl.isNullOrBlank()) "unknown_quality" else "local_playable"
        ).bestQualityLabel()
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .vantaCard()
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(RoundedCornerShape(12.dp))
                .border(0.5.dp, AppAccent.copy(alpha = 0.15f), RoundedCornerShape(12.dp))
        ) {
            NetworkArtwork(artworkUrl = song.artworkUrl, seed = song.title, modifier = Modifier.fillMaxSize())
        }
        Spacer(Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(cleanTitle, color = AppText, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (song.explicit == true) VantaExplicitBadge()
                VantaQualityBadge(qualityLabel)
            }
            Text(cleanArtist, color = AppTextSecondary, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.clickable { onNavigateToArtist(cleanArtist, null) })
            Spacer(Modifier.height(4.dp))
            if (song.streamUrl.isNullOrBlank()) {
                VantaStatusBadge("Metadata Only", AppWarning)
            } else {
                VantaStatusBadge("Playable", AppSuccess)
            }
        }
        Box(modifier = Modifier.size(44.dp).clip(CircleShape).clickable { onToggleFavoriteSong(song.id) }, contentAlignment = Alignment.Center) {
            Icon(
                imageVector = if (song.isFavorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                contentDescription = if (song.isFavorite) "Unfavorite" else "Favorite",
                tint = if (song.isFavorite) AppAccent else AppTextSecondary,
                modifier = Modifier.size(24.dp)
            )
        }
    }
}

@Composable
fun CountTile(label: String, value: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(AppSurfaceRaised)
            .border(0.5.dp, AppAccent.copy(alpha = 0.08f), RoundedCornerShape(16.dp))
            .padding(16.dp)
    ) {
        Text(value, color = AppAccent, fontSize = 26.sp, fontWeight = FontWeight.Black)
        Text(label, color = AppTextSecondary, fontSize = 13.sp)
    }
}
