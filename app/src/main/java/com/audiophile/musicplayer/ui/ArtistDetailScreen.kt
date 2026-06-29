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
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.ui.layout.ContentScale
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
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
import com.audiophile.musicplayer.data.catalog.ArtistCatalog
import com.audiophile.musicplayer.data.canonical.CanonicalTrack
import com.audiophile.musicplayer.data.local.entities.UnifiedTrackWithSources
import com.audiophile.musicplayer.data.source.SearchItemStatus
import com.audiophile.musicplayer.data.source.isConfirmedPlayable
import com.audiophile.musicplayer.data.source.sourceValidityStatus

private fun resolveTrackReleaseYear(
    track: UnifiedTrackWithSources,
    albums: List<com.audiophile.musicplayer.data.local.entities.Album>
): Int? {
    val albumName = track.track.albumName
    if (!albumName.isNullOrBlank()) {
        val matchingAlbum = albums.find {
            it.album_name.equals(albumName, ignoreCase = true) &&
            it.artist_name.equals(track.track.artist, ignoreCase = true)
        }
        if (matchingAlbum?.release_year != null) {
            return matchingAlbum.release_year
        }
        val yearRegex = Regex("""\b(19|20)\d{2}\b""")
        val match = yearRegex.find(albumName)
        if (match != null) {
            return match.value.toIntOrNull()
        }
    }
    val title = track.track.title
    val yearRegex = Regex("""\b(19|20)\d{2}\b""")
    val match = yearRegex.find(title)
    if (match != null) {
        return match.value.toIntOrNull()
    }
    return null
}

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
    onNavigateToAlbum: (albumName: String, artistName: String) -> Unit,
    onNavigateToTrackSheet: (track: UnifiedTrackWithSources) -> Unit,
    onPlayCatalogTrack: (CanonicalTrack) -> Unit = {},
    onAddToQueue: (UnifiedTrackWithSources) -> Unit = {},
    miniPlayerVisible: Boolean = false,
    bottomNavVisible: Boolean = false
) {
    val confirmedPlayableTracks = tracks.filter { it.isConfirmedPlayable() }
    val confirmedPlayableCount = confirmedPlayableTracks.size
    val albumNames = tracks.map { it.track.albumName }.filterNotNull().distinct().take(10)
    val catalogTracks = catalog?.tracks.orEmpty()
    val hasKnownSongs = tracks.isNotEmpty() || catalogTracks.isNotEmpty()

    val newestTrack = remember(tracks, albums) {
        if (tracks.isEmpty()) null
        else {
            tracks.maxWithOrNull(
                compareBy<UnifiedTrackWithSources> {
                    resolveTrackReleaseYear(it, albums) ?: 0
                }.thenBy { it.track.trackId }
            )
        }
    }

    val dedupedTopTracks = remember(tracks, newestTrack) {
        tracks
            .filter { newestTrack == null || it.track.trackId != newestTrack.track.trackId }
            .distinctBy { it.track.title.trim().lowercase() }
            .sortedBy {
                val s = it.sourceValidityStatus()
                when (s) {
                    SearchItemStatus.LOCAL_PLAYABLE,
                    SearchItemStatus.VALIDATED_PLAYABLE,
                    SearchItemStatus.PREVIEW,
                    SearchItemStatus.DEMO_ONLY -> 0
                    SearchItemStatus.SOURCE_FOUND -> 1
                    else -> 2
                }
            }
            .take(10)
    }

    val actionsShown = when {
        confirmedPlayableCount > 0 -> "StartRadio,Shuffle"
        hasKnownSongs -> "StartRadio"
        else -> "none"
    }
    val hiddenActions = if (hasKnownSongs) "none" else "StartRadio,Shuffle"

    Log.w("VANTA_UI_TRUTH",
        "screen=ArtistDetail " +
        "title='$artistName' " +
        "type=artist " +
        "trackCount=${tracks.size} " +
        "confirmedPlayableCount=$confirmedPlayableCount " +
        "actionsShown=$actionsShown " +
        "hiddenActions=$hiddenActions")

    Log.w("VANTA_DETAIL_TRUTH",
        "screen=artist " +
        "artistName='$artistName' " +
        "trackCount=${tracks.size} " +
        "playableCount=$confirmedPlayableCount " +
        "albumCount=${albumNames.size} " +
        "actionsShown=$actionsShown")

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF121212))
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(bottom = appOverlayBottomPadding(miniPlayerVisible = miniPlayerVisible, bottomNavVisible = bottomNavVisible))
        ) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(280.dp),
                    contentAlignment = Alignment.BottomStart
                ) {
                    NetworkArtwork(
                        artworkUrl = catalog?.artist?.artworkUrl ?: catalogTracks.firstOrNull()?.artworkUrl,
                        seed = artistName,
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(RoundedCornerShape(bottomStart = 24.dp, bottomEnd = 24.dp))
                    )

                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(RoundedCornerShape(bottomStart = 24.dp, bottomEnd = 24.dp))
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(
                                        Color.Transparent,
                                        Color.Black.copy(alpha = 0.55f),
                                        Color.Black.copy(alpha = 0.88f)
                                    )
                                )
                            )
                    )

                    Icon(
                        Icons.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = Color.White,
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(16.dp)
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(Color.Black.copy(alpha = 0.35f))
                            .clickable(onClick = onBack)
                            .padding(4.dp)
                    )

                    Column(
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 28.dp)
                    ) {
                        Text(
                            text = artistName,
                            color = Color.White,
                            fontSize = 32.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (hasKnownSongs) {
                            Spacer(modifier = Modifier.height(14.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                Button(
                                    onClick = onPlayArtistRadio,
                                    shape = RoundedCornerShape(24.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = Color.White),
                                    contentPadding = PaddingValues(horizontal = 22.dp, vertical = 10.dp)
                                ) {
                                    Icon(
                                        Icons.Filled.PlayArrow,
                                        contentDescription = null,
                                        tint = Color.Black,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(Modifier.width(6.dp))
                                    Text(
                                        "Start Radio",
                                        color = Color.Black,
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 15.sp
                                    )
                                }
                                if (confirmedPlayableCount > 0) {
                                    Button(
                                        onClick = onShuffleTopSongs,
                                        shape = RoundedCornerShape(24.dp),
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = Color.White.copy(alpha = 0.14f)
                                        ),
                                        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 10.dp)
                                    ) {
                                        Icon(
                                            Icons.Filled.Shuffle,
                                            contentDescription = null,
                                            tint = Color.White,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(Modifier.width(6.dp))
                                        Text(
                                            "Shuffle",
                                            color = Color.White,
                                            fontWeight = FontWeight.SemiBold,
                                            fontSize = 15.sp
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            if (catalogLoading && !hasKnownSongs) {
                item {
                    Box(Modifier.fillMaxWidth().padding(48.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = AppAccent, modifier = Modifier.size(30.dp))
                    }
                }
            } else if (!hasKnownSongs) {
                item {
                    VantaEmptyState(
                        title = "Artist Catalog Unavailable",
                        description = "Artist metadata could not be loaded. Check your connection and try again.",
                        icon = Icons.Filled.Person,
                        actionLabel = "Find Matches",
                        onAction = onPlayArtistRadio
                    )
                }
            }

            if (newestTrack != null) {
                item {
                    val resolvedYear = remember(newestTrack, albums) { resolveTrackReleaseYear(newestTrack, albums) }
                    val display = remember(newestTrack.track) { TrackDisplayResolver.resolve(newestTrack.track) }
                    val status = newestTrack.sourceValidityStatus()
                    val qualityLabel = remember(newestTrack.sources, status) {
                        VantaQualityInfo.fromTrackSource(
                            source = newestTrack.sources.maxByOrNull { it.bitrate },
                            status = status
                        )?.bestQualityLabel()
                    }
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = "LATEST RELEASE" + (if (resolvedYear != null) " • $resolvedYear" else ""),
                            color = AppAccent,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.2.sp
                        )
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(16.dp))
                                .background(AppSurface.copy(alpha = 0.45f))
                                .border(0.5.dp, AppOutline.copy(alpha = 0.4f), RoundedCornerShape(16.dp))
                                .clickable { onNavigateToTrackSheet(newestTrack) }
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(80.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .border(0.5.dp, AppAccent.copy(alpha = 0.12f), RoundedCornerShape(12.dp))
                            ) {
                                NetworkArtwork(
                                    artworkUrl = display.artworkUrl,
                                    seed = display.title,
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Text(
                                        text = display.title,
                                        color = AppText,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 16.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    if (display.explicit == true) VantaExplicitBadge()
                                }
                                Text(
                                    text = display.album ?: display.artist,
                                    color = AppTextSecondary,
                                    fontSize = 14.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Spacer(Modifier.height(2.dp))
                                VantaQualityBadge(qualityLabel)
                            }
                            // Add to Queue button
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(AppAccent.copy(alpha = 0.12f))
                                    .border(0.5.dp, AppAccent.copy(alpha = 0.24f), RoundedCornerShape(12.dp))
                                    .clickable { 
                                        Log.d("VANTA_DETAIL_ACTION", "action='add_newest_to_queue' trackId=${newestTrack.track.trackId}")
                                        onAddToQueue(newestTrack)
                                    }
                                    .padding(horizontal = 14.dp, vertical = 8.dp)
                            ) {
                                Text("+ Queue", color = AppAccent, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }

            if (dedupedTopTracks.isNotEmpty()) {
                if (catalogTracks.isEmpty()) {
                item {
                    VantaSectionHeader("Top Songs", modifier = Modifier.padding(horizontal = 24.dp))
                }
                itemsIndexed(dedupedTopTracks) { i, track ->
                    TrackRow(
                        track = track,
                        onTap = { onNavigateToTrackSheet(track) },
                        showIndex = true,
                        index = i + 1,
                        modifier = Modifier.padding(horizontal = 24.dp)
                    )
                }
                }
            }

            if (catalogTracks.isNotEmpty()) {
                item {
                    VantaSectionHeader("Songs", modifier = Modifier.padding(horizontal = 24.dp))
                }
                itemsIndexed(catalogTracks, key = { _, track -> track.isrc ?: "${track.title}|${track.album}" }) { i, track ->
                    CatalogTrackRow(
                        track = track,
                        index = i + 1,
                        onTap = { onPlayCatalogTrack(track) },
                        modifier = Modifier.padding(horizontal = 24.dp)
                    )
                }
            }

            if (catalog?.albums?.isNotEmpty() == true) {
                item {
                    VantaSectionHeader("Albums", modifier = Modifier.padding(horizontal = 24.dp))
                }
                item {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 24.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        items(catalog.albums, key = { it.title }) { album ->
                            CatalogAlbumCard(album = album, onTap = { onNavigateToAlbum(album.title, artistName) })
                        }
                    }
                }
            } else if (albumNames.isNotEmpty()) {
                item {
                    VantaSectionHeader("Albums", modifier = Modifier.padding(horizontal = 24.dp))
                }
                item {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 24.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        items(albumNames) { album ->
                            AlbumCard(album, onNavigateToAlbum)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CatalogTrackRow(
    track: CanonicalTrack,
    index: Int,
    onTap: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth().clickable(onClick = onTap).padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("$index", color = Color.White.copy(alpha = 0.45f), fontSize = 14.sp, fontWeight = FontWeight.Medium, modifier = Modifier.width(24.dp))
        NetworkArtwork(
            artworkUrl = track.artworkUrl,
            seed = track.title,
            modifier = Modifier.size(52.dp).clip(RoundedCornerShape(10.dp))
        )
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(track.title, color = Color.White, fontWeight = FontWeight.Medium, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (track.explicit == true) VantaExplicitBadge()
            }
            Text(
                listOfNotNull(track.album, track.releaseYear?.toString()).joinToString(" • ").ifBlank { track.artist },
                color = Color.White.copy(alpha = 0.6f),
                fontSize = 14.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Icon(Icons.Filled.PlayArrow, contentDescription = "Play ${track.title}", tint = Color.White.copy(alpha = 0.85f), modifier = Modifier.size(24.dp))
    }
}

@Composable
private fun CatalogAlbumCard(
    album: com.audiophile.musicplayer.data.canonical.CanonicalAlbum,
    onTap: () -> Unit
) {
    Column(modifier = Modifier.width(160.dp).clickable(onClick = onTap)) {
        NetworkArtwork(
            artworkUrl = album.artworkUrl,
            seed = album.title,
            modifier = Modifier.size(160.dp).clip(RoundedCornerShape(12.dp))
        )
        Spacer(Modifier.height(8.dp))
        Text(album.title, color = AppText, fontWeight = FontWeight.Bold, fontSize = 14.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Text(listOfNotNull(album.releaseYear?.toString(), album.artist).joinToString(" • "), color = AppTextSecondary, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
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
    showIndex: Boolean = false,
    index: Int = 0,
    modifier: Modifier = Modifier
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
        Text(albumName, color = AppText, fontWeight = FontWeight.Bold, fontSize = 14.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Text(artistName, color = AppTextSecondary, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
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
        Text(artistName, color = AppText, fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
    }
}
