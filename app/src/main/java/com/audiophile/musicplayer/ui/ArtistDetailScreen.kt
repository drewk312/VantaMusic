package com.audiophile.musicplayer.ui

import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.ui.layout.ContentScale
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.audiophile.musicplayer.data.display.TrackDisplayResolver
import com.audiophile.musicplayer.data.display.VantaQualityInfo
import com.audiophile.musicplayer.data.display.DisplayMetadataCleaner
import com.audiophile.musicplayer.data.catalog.ArtistCatalog
import com.audiophile.musicplayer.data.canonical.CanonicalTrack
import com.audiophile.musicplayer.data.local.entities.UnifiedTrackWithSources
import com.audiophile.musicplayer.data.source.SearchItemStatus
import com.audiophile.musicplayer.data.source.isConfirmedPlayable
import com.audiophile.musicplayer.data.source.sourceValidityStatus

@Composable
fun ArtistDetailScreen(
    artistName: String,
    artistId: String?,
    tracks: List<UnifiedTrackWithSources> = emptyList(),
    albums: List<com.audiophile.musicplayer.data.local.entities.Album> = emptyList(),
    catalog: ArtistCatalog? = null,
    catalogLoading: Boolean = false,
    onBack: () -> Unit,
    onPlayArtistRadio: () -> Unit,
    onShuffleTopSongs: () -> Unit,
    onPlayArtist: () -> Unit = onPlayArtistRadio,
    onNavigateToAlbum: (albumName: String, artistName: String) -> Unit,
    onNavigateToTrackSheet: (track: UnifiedTrackWithSources) -> Unit,
    onPlayCatalogTrack: (CanonicalTrack) -> Unit = {},
    onAddToQueue: (UnifiedTrackWithSources) -> Unit = {},
    searchTracks: List<CanonicalTrack> = emptyList(),
    miniPlayerVisible: Boolean = false,
    bottomNavVisible: Boolean = false
) {
    val allCatalogTracks = remember(catalog, searchTracks) {
        com.audiophile.musicplayer.search.SearchPresentation.songs(catalog?.tracks.orEmpty() + searchTracks)
    }
    val artistAlbums = remember(catalog, allCatalogTracks) {
        com.audiophile.musicplayer.search.SearchPresentation.albums(catalog?.albums.orEmpty(), allCatalogTracks,
            com.audiophile.musicplayer.data.canonical.CanonicalArtist(name = artistName))
    }
    val localTracks = tracks.distinctBy { it.track.title.trim().lowercase() }
    val hasSongs = allCatalogTracks.isNotEmpty() || localTracks.isNotEmpty()
    var showAllAlbums by androidx.compose.runtime.saveable.rememberSaveable(artistName) { mutableStateOf(false) }
    var showAllSongs by androidx.compose.runtime.saveable.rememberSaveable(artistName) { mutableStateOf(false) }
    val listState = androidx.compose.foundation.lazy.rememberLazyListState()
    androidx.compose.runtime.LaunchedEffect(artistName) { listState.scrollToItem(0) }

    Column(Modifier.fillMaxSize().background(AppBackground).statusBarsPadding()
        .padding(bottom = appOverlayBottomPadding(miniPlayerVisible = miniPlayerVisible, bottomNavVisible = bottomNavVisible))) {
        Row(Modifier.fillMaxWidth().height(52.dp).padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            androidx.compose.material3.IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = AppText)
            }
            Text(artistName, color = AppText, fontSize = 17.sp, fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        LazyColumn(state = listState, modifier = Modifier.weight(1f).fillMaxWidth().clipToBounds(),
            verticalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(bottom = 24.dp)) {
            item(key = "artist_header") {
                Box(Modifier.fillMaxWidth().height(220.dp)) {
                    NetworkArtwork(artworkUrl = catalog?.artist?.artworkUrl ?: allCatalogTracks.firstOrNull()?.artworkUrl,
                        seed = artistName, modifier = Modifier.fillMaxSize())
                    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.9f)))))
                    Column(Modifier.align(Alignment.BottomStart).padding(24.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        Text("ARTIST", color = Color.White.copy(alpha = 0.75f), fontSize = 11.sp,
                            letterSpacing = 1.8.sp, fontWeight = FontWeight.Bold)
                        Text(artistName, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 32.sp,
                            maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
            if (hasSongs) item(key = "artist_actions") {
                Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(onClick = onPlayArtist, modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = AppAccent, contentColor = AppBackground)) {
                        Icon(Icons.Filled.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp)); Text("Play")
                    }
                    androidx.compose.material3.OutlinedButton(onClick = onShuffleTopSongs, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Filled.Shuffle, contentDescription = null, tint = AppAccent, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp)); Text("Shuffle", color = AppAccent)
                    }
                    androidx.compose.material3.IconButton(onClick = onPlayArtistRadio) {
                        Icon(Icons.Filled.Radio, contentDescription = "Artist radio", tint = AppAccent)
                    }
                }
            }
            if (catalogLoading && !hasSongs) item {
                Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = AppAccent)
                }
            }
            if (artistAlbums.isNotEmpty()) {
                item(key = "album_heading") {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp), verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween) {
                        Column {
                            Text("Albums & singles", color = AppText, fontWeight = FontWeight.Bold, fontSize = 20.sp)
                            Text("${artistAlbums.size} releases", color = AppTextSecondary, fontSize = 12.sp)
                        }
                        androidx.compose.material3.TextButton(onClick = { showAllAlbums = !showAllAlbums }) {
                            Text(if (showAllAlbums) "Show less" else "See all", color = AppAccent)
                        }
                    }
                }
                if (showAllAlbums) {
                    itemsIndexed(artistAlbums.chunked(2), key = { rowIndex, _ -> "album_row_$rowIndex" }) { _, row ->
                        Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                            row.forEach { album ->
                                CatalogAlbumCard(album, onTap = { onNavigateToAlbum(album.title, artistName) })
                            }
                        }
                    }
                } else item(key = "album_shelf") {
                    LazyRow(contentPadding = PaddingValues(horizontal = 24.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        itemsIndexed(artistAlbums.take(6), key = { index, album -> "${album.id ?: album.title}_${album.artist}_$index" }) { _, album ->
                            CatalogAlbumCard(album, onTap = { onNavigateToAlbum(album.title, artistName) })
                        }
                    }
                }
            }
            if (hasSongs) {
                item(key = "song_heading") {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp), verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(if (showAllSongs) "All songs" else "Top songs", color = AppText, fontWeight = FontWeight.Bold, fontSize = 20.sp)
                        if (maxOf(allCatalogTracks.size, localTracks.size) > 5) androidx.compose.material3.TextButton(onClick = { showAllSongs = !showAllSongs }) {
                            Text(if (showAllSongs) "Show less" else "See all", color = AppAccent)
                        }
                    }
                }
                if (allCatalogTracks.isNotEmpty()) {
                    itemsIndexed(if (showAllSongs) allCatalogTracks else allCatalogTracks.take(5), key = { index, track -> "catalog_${track.externalTrackId ?: track.title}_$index" }) { index, track ->
                        CatalogTrackRow(track, index + 1, onTap = { onPlayCatalogTrack(track) },
                            onAlbumTap = { track.album?.let { onNavigateToAlbum(it, artistName) } },
                            modifier = Modifier.padding(horizontal = 24.dp))
                    }
                } else {
                    itemsIndexed(if (showAllSongs) localTracks else localTracks.take(5), key = { index, track -> "local_${track.track.trackId}_$index" }) { _, track ->
                        TrackRow(track, onTap = { onNavigateToTrackSheet(track) }, modifier = Modifier.padding(horizontal = 24.dp))
                    }
                }
            } else if (!catalogLoading) item(key = "empty_artist_state") {
                VantaEmptyState(title = "Could not load this artist", description = "Try again when your connection is available.",
                    icon = Icons.Filled.Person, actionLabel = "Find music", onAction = onPlayArtistRadio)
            }
        }
    }
}

@Composable
private fun CatalogTrackRow(
    track: CanonicalTrack,
    index: Int,
    onTap: () -> Unit,
    onAlbumTap: () -> Unit,
    modifier: Modifier = Modifier
) {
    val (displayTitle, displayArtist) = remember(track) {
        DisplayMetadataCleaner.computeDisplayTitleArtist(track.title, track.artist)
    }
    Row(
        modifier = modifier.fillMaxWidth().clickable(onClick = onTap).padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        NetworkArtwork(
            artworkUrl = track.artworkUrl,
            seed = displayTitle,
            modifier = Modifier.size(48.dp).clip(RoundedCornerShape(8.dp)).clickable(onClick = onAlbumTap)
        )
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(displayTitle, color = Color.White, fontWeight = FontWeight.Medium, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (track.explicit == true) VantaExplicitBadge()
            }
            Text(
                track.album?.takeUnless { it.equals(track.title, ignoreCase = true) }
                    ?.let { DisplayMetadataCleaner.cleanDisplayName(it).ifBlank { it } } ?: displayArtist,
                color = Color.White.copy(alpha = 0.6f),
                fontSize = 14.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Icon(Icons.Filled.PlayArrow, contentDescription = "Play $displayTitle", tint = Color.White.copy(alpha = 0.85f), modifier = Modifier.size(24.dp))
    }
}

@Composable
private fun CatalogAlbumCard(
    album: com.audiophile.musicplayer.data.canonical.CanonicalAlbum,
    onTap: () -> Unit
) {
    Column(modifier = Modifier.width(138.dp).clickable(onClick = onTap)) {
        NetworkArtwork(
            artworkUrl = album.artworkUrl,
            seed = album.title,
            modifier = Modifier.size(138.dp).clip(RoundedCornerShape(12.dp))
        )
        Spacer(Modifier.height(8.dp))
        Text(DisplayMetadataCleaner.cleanDisplayName(album.title).ifBlank { album.title }, color = AppText, fontWeight = FontWeight.Bold, fontSize = 14.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Text(listOfNotNull(album.releaseYear?.toString(), DisplayMetadataCleaner.cleanDisplayName(album.artist).ifBlank { album.artist }.takeIf { it.isNotBlank() }).joinToString(" • "), color = AppTextSecondary, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
fun SectionHeader(title: String, modifier: Modifier = Modifier) {
    Text(title, color = AppText, fontSize = 22.sp, fontWeight = FontWeight.Bold, modifier = modifier)
}

@Composable
fun TrackRow(
    track: UnifiedTrackWithSources,
    onTap: () -> Unit,
    modifier: Modifier = Modifier,
    showIndex: Boolean = false,
    index: Int = 0
) {
    val display = remember(track.track) {
        TrackDisplayResolver.resolve(track.track)
    }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onTap)
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (showIndex) {
            Text(
                "$index",
                color = Color.White.copy(alpha = 0.45f),
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.width(24.dp)
            )
        }
        Box(
            modifier = Modifier
                .size(52.dp)
                .clip(RoundedCornerShape(10.dp))
        ) {
            NetworkArtwork(artworkUrl = display.artworkUrl, seed = display.title, modifier = Modifier.fillMaxSize())
        }
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    display.title,
                    color = Color.White,
                    fontWeight = FontWeight.Medium,
                    fontSize = 16.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (display.explicit == true) VantaExplicitBadge()
            }
            Text(
                display.album ?: display.artist,
                color = Color.White.copy(alpha = 0.6f),
                fontSize = 14.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Icon(Icons.Filled.MoreHoriz, contentDescription = "More", tint = Color.White.copy(alpha = 0.5f), modifier = Modifier.size(24.dp).clickable(onClick = onTap))
    }
}

@Composable
fun AlbumCard(
    albumName: String,
    onTap: (albumName: String, artistName: String) -> Unit,
    artistName: String = ""
) {
    Column(
        modifier = Modifier.width(160.dp).clickable { onTap(albumName, artistName) },
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(160.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(AppSurface)
                .border(0.5.dp, AppOutline, RoundedCornerShape(12.dp)),
            contentAlignment = Alignment.Center
        ) {
            NetworkArtwork(artworkUrl = null, seed = albumName, modifier = Modifier.fillMaxSize())
        }
        Spacer(Modifier.height(8.dp))
        Text(DisplayMetadataCleaner.cleanDisplayName(albumName).ifBlank { albumName }, color = AppText, fontWeight = FontWeight.Bold, fontSize = 14.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Text(DisplayMetadataCleaner.cleanDisplayName(artistName).ifBlank { artistName }, color = AppTextSecondary, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
fun ArtistAvatarCard(artistName: String) {
    Column(
        modifier = Modifier.width(120.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(100.dp)
                .clip(CircleShape)
                .background(AppSurface)
                .border(0.5.dp, AppOutline, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            NetworkArtwork(artworkUrl = null, seed = artistName, modifier = Modifier.fillMaxSize().clip(CircleShape))
        }
        Spacer(Modifier.height(8.dp))
        Text(DisplayMetadataCleaner.cleanDisplayName(artistName).ifBlank { artistName }, color = AppText, fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
    }
}
