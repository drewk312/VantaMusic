
package com.audiophile.musicplayer.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistPlay
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.audiophile.musicplayer.data.display.TrackDisplayResolver
import com.audiophile.musicplayer.data.local.entities.LocalSongEntity
import com.audiophile.musicplayer.data.local.entities.UnifiedTrackWithSources
import com.audiophile.musicplayer.ui.preview.ArtworkPlaceholder
import com.audiophile.musicplayer.common.VantaLogger

private enum class LibraryView(val label: String) {
    Overview("Overview"), Songs("Songs"), Albums("Albums"), Artists("Artists"), Playlists("Playlists")
}

@Composable
fun LibraryScreen(
    uiState: MainUiState,
    miniPlayerVisible: Boolean = false,
    onOpenSettings: () -> Unit,
    onOpenImports: () -> Unit,
    onToggleFavoriteSong: (LocalSongEntity) -> Unit,
    onPlay: (UnifiedTrackWithSources) -> Unit,
    onPlayNext: (UnifiedTrackWithSources) -> Unit,
    onAddToQueue: (UnifiedTrackWithSources) -> Unit,
    onDownload: (UnifiedTrackWithSources) -> Unit,
    onNavigateToArtist: (String, String?) -> Unit,
    onNavigateToAlbum: (String, String, String?) -> Unit,
    onOpenTrackSheet: ((UnifiedTrackWithSources) -> Unit)? = null,
    onOpenPlaylist: (Long) -> Unit,
    onPlayPlaylist: (Long) -> Unit,
    onOpenImportFromLink: () -> Unit
) {
    val counts = uiState.localLibraryCounts
    val localSongs = uiState.localSongs
    val playlists = uiState.localPlaylists
    var selectedView by rememberSaveable { mutableStateOf(LibraryView.Overview) }
    // Downloads not tracked separately yet; treat Downloaded tile as placeholder until persistence added.
    val downloads = remember(localSongs) { emptyList<LocalSongEntity>() }

    // Data truth: counts derived the same way the UI shows them.
    val distinctArtists = remember(localSongs) {
        localSongs.map { it.artist.trim() }.filter { it.isNotBlank() }.distinct().size
    }
    val distinctAlbums = remember(localSongs) {
        localSongs.map { it.album?.trim() ?: "" }.filter { it.isNotBlank() }.distinct().size
    }
    val countMap = mapOf(
        "Playlists" to counts.playlistsCount,
        "Artists" to distinctArtists,
        "Albums" to distinctAlbums,
        "Songs" to counts.songsCount,
        "Made For You" to 0,
        "Downloaded" to downloads.size,
        "Imports" to counts.importsCount
    )

    val recentSongs = remember(localSongs) {
        localSongs
            .filter { it.lastPlayedAt != null }
            .sortedByDescending { it.lastPlayedAt }
            .take(12)
    }
    val addedSongs = remember(localSongs) {
        localSongs
            .sortedByDescending { it.createdAt }
            .take(16)
    }
    val likedSongs = remember(localSongs) { localSongs.filter { it.isFavorite }.take(12) }

    // Log real counts for verification.
    LaunchedEffect(countMap) {
        VantaLogger.d(
            VantaLogger.Tag.LIBRARY,
            "VANTA_LIBRARY_TRUTH songs=${countMap["Songs"]} artists=${countMap["Artists"]} albums=${countMap["Albums"]} playlists=${countMap["Playlists"]} imports=${countMap["Imports"]} downloaded=${countMap["Downloaded"]}"
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(top = appTopContentPadding())
            .padding(bottom = appBottomContentPadding(isMiniPlayerVisible = miniPlayerVisible))
            .padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        Spacer(modifier = Modifier.height(4.dp))

        // Header
        LibraryHeader(
            onOpenImportFromLink = onOpenImportFromLink,
            onOpenSettings = onOpenSettings
        )

        // Compact, useful quick access — no duplicate heading or settings-grid feel.
        LibraryViewSelector(selected = selectedView, onSelect = { selectedView = it })

        if (selectedView == LibraryView.Songs) {
            LibrarySongList(songs = addedSongs, uiState = uiState, onPlay = onPlay)
        }

        if (selectedView == LibraryView.Albums) {
            LibraryAlbumList(
                songs = localSongs,
                onNavigateToAlbum = onNavigateToAlbum
            )
        }

        if (selectedView == LibraryView.Artists) {
            LibraryArtistList(
                songs = localSongs,
                onNavigateToArtist = onNavigateToArtist
            )
        }

        // Recently Added
        if (selectedView == LibraryView.Overview && addedSongs.isNotEmpty()) {
            VantaSectionHeader("Recently Added")
            RecentlyAddedCarousel(
                songs = addedSongs,
                uiState = uiState,
                onPlay = onPlay
            )
        } else if (selectedView == LibraryView.Overview && localSongs.isEmpty()) {
            VantaEmptyState(
                title = "Your new music will appear here",
                description = "Import a playlist or save a song to begin your library.",
                icon = Icons.Filled.MusicNote,
                actionLabel = "Import music",
                onAction = onOpenImportFromLink
            )
        }

        // Liked Songs
        if (selectedView == LibraryView.Overview && likedSongs.isNotEmpty()) {
            VantaSectionHeader("Liked Songs")
            LikedSongsCarousel(
                songs = likedSongs,
                uiState = uiState,
                onPlay = onPlay,
                onToggleFavorite = onToggleFavoriteSong
            )
        }

        // Recently Played
        if (selectedView == LibraryView.Overview && recentSongs.isNotEmpty()) {
            VantaSectionHeader("Recently Played")
            RecentlyPlayedCarousel(
                songs = recentSongs,
                uiState = uiState,
                onPlay = onPlay
            )
        }

        // Playlists
        if ((selectedView == LibraryView.Overview || selectedView == LibraryView.Playlists) && playlists.isNotEmpty()) {
            VantaSectionHeader("Playlists")
            playlists.take(6).forEach { playlist ->
                PlaylistRow(
                    playlist = playlist,
                    onClick = { onOpenPlaylist(playlist.id) },
                    onPlay = { onPlayPlaylist(playlist.id) }
                )
            }
        }

        // Imports empty nudge
        if (selectedView == LibraryView.Overview && counts.importsCount > 0) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(AppSurfaceRaised)
                    .border(0.5.dp, AppOutline, RoundedCornerShape(14.dp))
                    .clickable(onClick = onOpenImports)
                    .padding(16.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.FileUpload, contentDescription = null, tint = AppAccent, modifier = Modifier.size(22.dp))
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Review imports", color = AppText, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                        Text("${counts.importsCount} import batch${if (counts.importsCount == 1) "" else "es"} ready", color = AppTextMuted, fontSize = 12.sp)
                    }
                    Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = AppTextMuted, modifier = Modifier.size(20.dp))
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
    }
}

@Composable
private fun LibraryHeader(
    onOpenImportFromLink: () -> Unit,
    onOpenSettings: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Library", style = VantaType.pageTitle)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                IconButton(onClick = onOpenImportFromLink) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(AppSurface)
                            .border(0.5.dp, AppOutline, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Filled.Add, contentDescription = "Import", tint = AppAccent, modifier = Modifier.size(20.dp))
                    }
                }
                IconButton(onClick = onOpenSettings) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(AppSurface)
                            .border(0.5.dp, AppOutline, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Filled.Settings, contentDescription = "Settings", tint = AppTextSecondary, modifier = Modifier.size(20.dp))
                    }
                }
            }
        }
        Text(
            "Your saved music",
            color = AppTextMuted,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
private fun LibraryViewSelector(selected: LibraryView, onSelect: (LibraryView) -> Unit) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(LibraryView.entries) { view ->
            val active = view == selected
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (active) AppAccent else AppSurface)
                    .clickable { onSelect(view) }
                    .padding(horizontal = 14.dp, vertical = 9.dp)
            ) {
                Text(view.label, color = if (active) Color.Black else AppTextSecondary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun LibrarySongList(songs: List<LocalSongEntity>, uiState: MainUiState, onPlay: (UnifiedTrackWithSources) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        songs.forEach { song ->
            val unified = uiState.library.firstOrNull { it.track.trackId == song.id }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(enabled = unified != null) { unified?.let(onPlay) }
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                NetworkArtwork(song.artworkUrl, song.title, Modifier.size(50.dp).clip(RoundedCornerShape(6.dp)))
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(song.title, color = AppText, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(song.artist, color = AppTextMuted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Icon(Icons.Filled.PlayArrow, contentDescription = "Play ${song.title}", tint = AppAccent)
            }
        }
    }
}

@Composable
private fun LibraryAlbumList(songs: List<LocalSongEntity>, onNavigateToAlbum: (String, String, String?) -> Unit) {
    val albums = remember(songs) { songs.filter { !it.album.isNullOrBlank() }.distinctBy { "${it.album}|${it.artist}" } }
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        albums.forEach { song ->
            LibraryIdentityRow(song.album.orEmpty(), song.artist, song.artworkUrl) {
                onNavigateToAlbum(song.album.orEmpty(), song.artist, song.artworkUrl)
            }
        }
    }
}

@Composable
private fun LibraryArtistList(songs: List<LocalSongEntity>, onNavigateToArtist: (String, String?) -> Unit) {
    val artists = remember(songs) { songs.filter { it.artist.isNotBlank() }.distinctBy { it.artist.lowercase() } }
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        artists.forEach { song ->
            LibraryIdentityRow(song.artist, "Artist", song.artworkUrl) { onNavigateToArtist(song.artist, null) }
        }
    }
}

@Composable
private fun LibraryIdentityRow(title: String, subtitle: String, artworkUrl: String?, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        NetworkArtwork(artworkUrl, title, Modifier.size(52.dp).clip(RoundedCornerShape(6.dp)))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = AppText, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(subtitle, color = AppTextMuted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = AppTextMuted)
    }
}

@Composable
private fun RecentlyAddedCarousel(
    songs: List<LocalSongEntity>,
    uiState: MainUiState,
    onPlay: (UnifiedTrackWithSources) -> Unit
) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        items(songs, key = { it.id }) { song ->
            TrackCard(
                song = song,
                uiState = uiState,
                onPlay = onPlay
            )
        }
    }
}

@Composable
private fun LikedSongsCarousel(
    songs: List<LocalSongEntity>,
    uiState: MainUiState,
    onPlay: (UnifiedTrackWithSources) -> Unit,
    onToggleFavorite: (LocalSongEntity) -> Unit
) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        items(songs, key = { it.id }) { song ->
            TrackCard(
                song = song,
                uiState = uiState,
                onPlay = onPlay,
                showFavorite = true,
                isFavorite = true,
                onToggleFavorite = onToggleFavorite
            )
        }
    }
}

@Composable
private fun RecentlyPlayedCarousel(
    songs: List<LocalSongEntity>,
    uiState: MainUiState,
    onPlay: (UnifiedTrackWithSources) -> Unit
) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        items(songs, key = { it.id }) { song ->
            TrackCard(
                song = song,
                uiState = uiState,
                onPlay = onPlay
            )
        }
    }
}

@Composable
private fun TrackCard(
    song: LocalSongEntity,
    uiState: MainUiState,
    onPlay: (UnifiedTrackWithSources) -> Unit,
    showFavorite: Boolean = false,
    isFavorite: Boolean = false,
    onToggleFavorite: ((LocalSongEntity) -> Unit)? = null
) {
    val unified = remember(song.id) {
        uiState.library.firstOrNull { it.track.trackId == song.id }
    }
    Column(
        modifier = Modifier.width(130.dp)
    ) {
        Box(
            modifier = Modifier
                .size(130.dp)
                .clip(RoundedCornerShape(VantaRadius.artwork))
                .background(AppSurface)
                .border(0.5.dp, AppOutline, RoundedCornerShape(VantaRadius.artwork))
                .clickable {
                    unified?.let { onPlay(it) }
                }
        ) {
            if (!song.artworkUrl.isNullOrBlank()) {
                NetworkArtwork(
                    artworkUrl = song.artworkUrl,
                    seed = song.title,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                ArtworkPlaceholder(
                    seed = song.title,
                    modifier = Modifier.fillMaxSize()
                )
            }
            if (showFavorite) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(6.dp)
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.45f))
                        .clickable {
                            onToggleFavorite?.invoke(song)
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.Favorite,
                        contentDescription = null,
                        tint = AppAccent,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            song.title,
            color = AppText,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            song.artist,
            color = AppTextSecondary,
            fontSize = 11.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun PlaylistRow(
    playlist: com.audiophile.musicplayer.data.local.entities.PlaylistEntity,
    onClick: () -> Unit,
    onPlay: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(AppSurface)
            .border(0.5.dp, AppOutline, RoundedCornerShape(14.dp))
            .clickable { onClick() }
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(AppAccent.copy(alpha = 0.08f)),
            contentAlignment = Alignment.Center
        ) {
            if (playlist.artworkUrl != null) {
                NetworkArtwork(artworkUrl = playlist.artworkUrl, seed = playlist.name, modifier = Modifier.fillMaxSize())
            } else {
                Icon(Icons.AutoMirrored.Filled.PlaylistPlay, contentDescription = null, tint = AppAccent, modifier = Modifier.size(24.dp))
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(playlist.name, color = AppText, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 14.sp)
            if (!playlist.description.isNullOrBlank()) {
                Text(playlist.description, color = AppTextMuted, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .clickable { onPlay() },
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Filled.PlayArrow, contentDescription = "Play", tint = AppAccent, modifier = Modifier.size(22.dp))
        }
    }
}
