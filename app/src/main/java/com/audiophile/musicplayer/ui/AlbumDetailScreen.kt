package com.audiophile.musicplayer.ui

import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.audiophile.musicplayer.data.display.TrackDisplayResolver
import com.audiophile.musicplayer.data.display.VantaQualityInfo
import com.audiophile.musicplayer.data.catalog.AlbumCatalog
import com.audiophile.musicplayer.data.canonical.CanonicalTrack
import com.audiophile.musicplayer.data.local.entities.UnifiedTrackWithSources
import com.audiophile.musicplayer.data.source.SearchItemStatus
import com.audiophile.musicplayer.data.source.isConfirmedPlayable
import com.audiophile.musicplayer.data.source.sourceValidityStatus

@Composable
fun AlbumDetailScreen(
    albumName: String,
    artistName: String,
    artworkUrl: String?,
    releaseYear: Int?,
    genre: String?,
    explicit: Boolean?,
    tracks: List<UnifiedTrackWithSources>,
    catalog: AlbumCatalog? = null,
    catalogLoading: Boolean = false,
    onBack: () -> Unit,
    onPlayAlbum: () -> Unit,
    onShuffleAlbum: () -> Unit,
    onFindMatches: () -> Unit = onPlayAlbum,
    onPlayTrack: (UnifiedTrackWithSources) -> Unit,
    onPlayCatalogTrack: (CanonicalTrack) -> Unit = {},
    onNavigateToArtist: (String, String?) -> Unit,
    onNavigateToTrackSheet: (UnifiedTrackWithSources) -> Unit,
    miniPlayerVisible: Boolean = false,
    bottomNavVisible: Boolean = false
) {
    val confirmedPlayableCount = tracks.count { it.isConfirmedPlayable() }
    val sourceFoundCount = tracks.count { it.sourceValidityStatus() == SearchItemStatus.SOURCE_FOUND }
    val metadataOnlyCount = tracks.count { it.sourceValidityStatus() == SearchItemStatus.METADATA_ONLY }
    val catalogTracks = catalog?.tracks.orEmpty()
    val hasTrackListing = tracks.isNotEmpty() || catalogTracks.isNotEmpty()
    val effectiveArtwork = catalog?.album?.artworkUrl ?: artworkUrl
    val effectiveYear = catalog?.album?.releaseYear ?: releaseYear
    val effectiveGenre = catalog?.album?.genre ?: genre

    val actionsShown = when {
        tracks.isEmpty() -> "none"
        confirmedPlayableCount == 0 && sourceFoundCount == 0 -> "FindMatches"
        confirmedPlayableCount == 1 -> "Play"
        confirmedPlayableCount > 1 -> "Play,Shuffle"
        else -> "none"
    }

    val hiddenActions = when {
        tracks.isEmpty() -> "Play,Shuffle"
        confirmedPlayableCount == 0 -> "Play,Shuffle"
        confirmedPlayableCount == 1 -> "Shuffle"
        else -> "none"
    }

    Log.w("VANTA_UI_TRUTH",
        "screen=AlbumDetail " +
        "title='$albumName' " +
        "type=album " +
        "trackCount=${tracks.size} " +
        "confirmedPlayableCount=$confirmedPlayableCount " +
        "sourceFoundCount=$sourceFoundCount " +
        "metadataOnlyCount=$metadataOnlyCount " +
        "actionsShown=$actionsShown " +
        "hiddenActions=$hiddenActions")

    Log.w("VANTA_DETAIL_TRUTH",
        "screen=album " +
        "albumTitle='$albumName' " +
        "artist='$artistName' " +
        "trackCount=${tracks.size} " +
        "playableCount=$confirmedPlayableCount " +
        "metadataOnly=${tracks.isEmpty()} " +
        "actionsShown=$actionsShown")

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(AppBackgroundTop, AppBackgroundBottom)))
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Filled.ArrowBack, contentDescription = "Back", tint = AppText, modifier = Modifier.size(32.dp).clickable(onClick = onBack).padding(4.dp))
            Spacer(Modifier.weight(1f))
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(bottom = appOverlayBottomPadding(miniPlayerVisible = miniPlayerVisible, bottomNavVisible = bottomNavVisible))
        ) {
            item {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier
                            .size(240.dp)
                            .clip(RoundedCornerShape(20.dp))
                            .border(0.5.dp, AppAccent.copy(alpha = 0.08f), RoundedCornerShape(20.dp))
                    ) {
                        NetworkArtwork(
                            artworkUrl = effectiveArtwork,
                            seed = albumName,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                    Spacer(Modifier.height(20.dp))
                    Text(albumName, color = AppText, fontSize = 26.sp, fontWeight = FontWeight.Black, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        artistName,
                        color = AppAccent,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.clickable { onNavigateToArtist(artistName, null) }
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (!hasTrackListing) {
                            VantaStatusBadge("Metadata Only", AppWarning)
                        } else {
                            if (effectiveYear != null) {
                                Text(effectiveYear.toString(), color = AppTextSecondary, fontSize = 14.sp)
                            }
                            if (effectiveGenre != null) {
                                Text("\u2022", color = AppTextMuted, fontSize = 14.sp)
                                Text(effectiveGenre, color = AppTextSecondary, fontSize = 14.sp)
                            }
                            if (explicit == true) {
                                Text("\u2022", color = AppTextMuted, fontSize = 14.sp)
                                VantaExplicitBadge()
                            }
                        }
                    }
                }
            }

            if (catalogLoading && !hasTrackListing) {
                item {
                    Box(Modifier.fillMaxWidth().padding(48.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = AppAccent, modifier = Modifier.size(30.dp))
                    }
                }
            } else if (!hasTrackListing) {
                item {
                    VantaEmptyState(
                        title = "Metadata Only",
                        description = "Track listing is not available yet. Search the album and artist to find playable matches.",
                        icon = Icons.Filled.Album,
                        actionLabel = "Find Matches",
                        onAction = onPlayAlbum
                    )
                }
            } else if (catalogTracks.isNotEmpty()) {
                item {
                    Text("${catalogTracks.size} songs", color = AppTextSecondary, fontSize = 14.sp, modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp))
                }
                itemsIndexed(catalogTracks, key = { _, track -> track.isrc ?: "${track.discNumber}|${track.trackNumber}|${track.title}" }) { index, track ->
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp).clickable { onPlayCatalogTrack(track) },
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text("${track.trackNumber ?: index + 1}", color = AppTextMuted, fontSize = 14.sp, fontWeight = FontWeight.Bold, modifier = Modifier.width(24.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(track.title, color = AppText, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                if (track.explicit == true) VantaExplicitBadge()
                            }
                            Text(track.artist, color = AppTextSecondary, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        Icon(Icons.Filled.PlayArrow, contentDescription = "Play ${track.title}", tint = AppAccent, modifier = Modifier.size(24.dp))
                    }
                }
            } else {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        if (confirmedPlayableCount > 0) {
                            Box(
                                modifier = Modifier.weight(1f).clip(RoundedCornerShape(16.dp)).background(AppAccent).clickable(onClick = onPlayAlbum).padding(vertical = 14.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Icon(Icons.Filled.PlayArrow, contentDescription = null, tint = AppBackgroundBottom, modifier = Modifier.size(20.dp))
                                    Text("Play", color = AppBackgroundBottom, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                                }
                            }
                            if (confirmedPlayableCount > 1) {
                                Box(
                                    modifier = Modifier.weight(1f).clip(RoundedCornerShape(16.dp)).background(AppSurfaceRaised).border(0.5.dp, AppOutline, RoundedCornerShape(16.dp)).clickable(onClick = onShuffleAlbum).padding(vertical = 14.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Icon(Icons.Filled.Shuffle, contentDescription = null, tint = AppText, modifier = Modifier.size(20.dp))
                                        Text("Shuffle", color = AppText, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                                    }
                                }
                            }
                        } else {
                            VantaEmptyState(
                                title = "No Playable Tracks",
                                description = "This album is saved as metadata. Search the album and artist to find playable matches.",
                                icon = Icons.Filled.Album,
                                actionLabel = "Find Matches",
                                onAction = onFindMatches,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }

                item {
                    Text(
                        "${tracks.size} songs",
                        color = AppTextSecondary,
                        fontSize = 14.sp,
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp)
                    )
                }

                itemsIndexed(tracks) { index, track ->
                    val display = remember(track.track) { TrackDisplayResolver.resolve(track.track) }
                    val status = track.sourceValidityStatus()
                    val qualityLabel = remember(track.sources) {
                        VantaQualityInfo.fromTrackSource(
                            source = track.sources.maxByOrNull { it.bitrate },
                            status = status
                        )?.bestQualityLabel()
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 6.dp).clickable { onPlayTrack(track) },
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text("${index + 1}", color = AppTextMuted, fontSize = 14.sp, fontWeight = FontWeight.Bold, modifier = Modifier.width(24.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(display.title, color = AppText, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                if (display.explicit == true) VantaExplicitBadge()
                                VantaQualityBadge(qualityLabel)
                            }
                            Text(display.artist, color = AppTextSecondary, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        when (status) {
                            SearchItemStatus.LOCAL_PLAYABLE,
                            SearchItemStatus.VALIDATED_PLAYABLE,
                            SearchItemStatus.PREVIEW,
                            SearchItemStatus.DEMO_ONLY ->
                                Icon(Icons.Filled.MoreHoriz, contentDescription = "More", tint = AppTextSecondary, modifier = Modifier.size(24.dp).clickable { onNavigateToTrackSheet(track) })
                            SearchItemStatus.SOURCE_FOUND ->
                                VantaStatusBadge("Source Found", AppTextSecondary)
                            else ->
                                VantaStatusBadge("Metadata", AppWarning)
                        }
                    }
                }
            }
        }
    }
}
