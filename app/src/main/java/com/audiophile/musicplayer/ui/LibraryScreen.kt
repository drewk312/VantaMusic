
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.audiophile.musicplayer.data.display.TrackDisplayResolver
import com.audiophile.musicplayer.data.local.entities.LocalSongEntity
import com.audiophile.musicplayer.data.local.entities.UnifiedTrackWithSources
import com.audiophile.musicplayer.data.local.toPlayableQueueItem
import com.audiophile.musicplayer.ui.preview.ArtworkPlaceholder
import com.audiophile.musicplayer.common.VantaLogger

private enum class LibraryView(val label: String) {
    Overview("Overview"), Songs("Songs"), Liked("Liked Songs"), Albums("Albums"), Artists("Artists"), Playlists("Playlists")
}

@Composable
fun LibraryScreen(
    uiState: MainUiState,
    miniPlayerVisible: Boolean = false,
    onOpenSettings: () -> Unit,
    onOpenImports: () -> Unit,
    onOpenWrapped: () -> Unit,
    onToggleFavoriteSong: (LocalSongEntity) -> Unit,
    onPlay: (UnifiedTrackWithSources) -> Unit,
    onNavigateToArtist: (String, String?) -> Unit,
    onNavigateToAlbum: (String, String, String?) -> Unit,
    onOpenTrackSheet: ((UnifiedTrackWithSources) -> Unit)? = null,
    onOpenPlaylist: (Long) -> Unit,
    onPlayPlaylist: (Long) -> Unit,
    onCreatePlaylist: (String) -> Unit = {}
) {
    val counts = uiState.localLibraryCounts
    val localSongs = uiState.localSongs
    val playlists = uiState.localPlaylists
    var selectedView by rememberSaveable { mutableStateOf(LibraryView.Overview) }
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
    val allSongs = remember(localSongs) {
        localSongs.sortedWith(compareBy({ it.title.lowercase() }, { it.artist.lowercase() }))
    }
    val allLikedSongs = remember(localSongs) { localSongs.filter { it.isFavorite } }
    val likedSongs = remember(allLikedSongs) { allLikedSongs.take(12) }
    var showCreatePlaylist by remember { mutableStateOf(false) }
    var newPlaylistName by remember { mutableStateOf("") }

    // Log real counts for verification.
    LaunchedEffect(countMap) {
        VantaLogger.d(
            VantaLogger.Tag.LIBRARY,
            "VANTA_LIBRARY_TRUTH songs=${countMap["Songs"]} artists=${countMap["Artists"]} albums=${countMap["Albums"]} playlists=${countMap["Playlists"]} imports=${countMap["Imports"]}"
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
            onOpenImports = onOpenImports,
            onOpenSettings = onOpenSettings,
            onOpenWrapped = onOpenWrapped
        )

        // Compact, useful quick access — no duplicate heading or settings-grid feel.
        LibraryViewSelector(selected = selectedView, onSelect = { selectedView = it })

        if (selectedView == LibraryView.Songs) {
            LibrarySongList(
                songs = allSongs,
                uiState = uiState,
                onPlay = onPlay,
                onOpenTrackSheet = onOpenTrackSheet
            )
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
                onPlay = onPlay,
                onOpenTrackSheet = onOpenTrackSheet
            )
        } else if (selectedView == LibraryView.Overview && localSongs.isEmpty()) {
            VantaEmptyState(
                title = "Your new music will appear here",
                description = "Scan this device, choose an audio file, or import a playlist.",
                icon = Icons.Filled.MusicNote,
                actionLabel = "Import music",
                onAction = onOpenImports
            )
        }

        // Liked Songs
        if (selectedView == LibraryView.Overview && allLikedSongs.isNotEmpty()) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                VantaSectionHeader("Liked Songs", modifier = Modifier.weight(1f))
                androidx.compose.material3.TextButton(onClick = { selectedView = LibraryView.Liked }) {
                    Text("See all", color = AppAccent)
                }
            }
            LikedSongsCarousel(
                songs = likedSongs,
                uiState = uiState,
                onPlay = onPlay,
                onOpenTrackSheet = onOpenTrackSheet,
                onToggleFavorite = onToggleFavoriteSong
            )
        }

        if (selectedView == LibraryView.Liked) {
            LikedSongsView(
                songs = allLikedSongs,
                albums = uiState.libraryAlbums,
                library = uiState.library,
                onPlay = onPlay,
                onOpenTrackSheet = onOpenTrackSheet,
                onToggleFavorite = onToggleFavoriteSong
            )
        }

        // Recently Played
        if (selectedView == LibraryView.Overview && recentSongs.isNotEmpty()) {
            VantaSectionHeader("Recently Played")
            RecentlyPlayedCarousel(
                songs = recentSongs,
                uiState = uiState,
                onPlay = onPlay,
                onOpenTrackSheet = onOpenTrackSheet
            )
        }

        // Playlists
        if (selectedView == LibraryView.Playlists || (selectedView == LibraryView.Overview && playlists.isNotEmpty())) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                VantaSectionHeader(
                    if (selectedView == LibraryView.Playlists) "All playlists" else "Playlists",
                    modifier = Modifier.weight(1f)
                )
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (selectedView == LibraryView.Overview && playlists.size > 6) {
                        androidx.compose.material3.TextButton(onClick = { selectedView = LibraryView.Playlists }) {
                            Text("See all", color = AppAccent)
                        }
                    }
                    androidx.compose.material3.TextButton(onClick = { showCreatePlaylist = true }) {
                        Text("New", color = AppAccent)
                    }
                }
            }
            val visiblePlaylists = if (selectedView == LibraryView.Playlists) playlists else playlists.take(6)
            if (visiblePlaylists.isEmpty()) {
                VantaEmptyState(
                    title = "No playlists yet",
                    description = "Create one for mixes, imports, or anything you want to replay.",
                    icon = Icons.AutoMirrored.Filled.PlaylistPlay,
                    actionLabel = "Create playlist",
                    onAction = { showCreatePlaylist = true }
                )
            } else {
                visiblePlaylists.forEach { playlist ->
                    PlaylistRow(
                        playlist = playlist,
                        onClick = { onOpenPlaylist(playlist.id) },
                        onPlay = { onPlayPlaylist(playlist.id) }
                    )
                }
            }
        }

        if (showCreatePlaylist) {
            AlertDialog(
                onDismissRequest = { showCreatePlaylist = false; newPlaylistName = "" },
                title = { Text("New playlist") },
                text = {
                    OutlinedTextField(
                        value = newPlaylistName,
                        onValueChange = { newPlaylistName = it },
                        singleLine = true,
                        placeholder = { Text("Playlist name") }
                    )
                },
                confirmButton = {
                    TextButton(
                        enabled = newPlaylistName.isNotBlank(),
                        onClick = {
                            onCreatePlaylist(newPlaylistName.trim())
                            showCreatePlaylist = false
                            newPlaylistName = ""
                            selectedView = LibraryView.Playlists
                        }
                    ) { Text("Create") }
                },
                dismissButton = {
                    TextButton(onClick = { showCreatePlaylist = false; newPlaylistName = "" }) { Text("Cancel") }
                }
            )
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
    onOpenImports: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenWrapped: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            "YOUR COLLECTION",
            color = AppAccentSecondary,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.8.sp
        )
        Spacer(Modifier.height(2.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Library", style = VantaType.pageTitle)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                IconButton(onClick = onOpenWrapped) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .glassSurfaceElevated(shape = RoundedCornerShape(19.dp), surfaceAlpha = 0.66f),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Filled.AutoAwesome, contentDescription = "Your year in music", tint = AppAccent, modifier = Modifier.size(20.dp))
                    }
                }
                IconButton(onClick = onOpenImports) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .glassSurfaceElevated(shape = RoundedCornerShape(19.dp), surfaceAlpha = 0.66f),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Filled.Add, contentDescription = "Add music", tint = AppAccent, modifier = Modifier.size(20.dp))
                    }
                }
                IconButton(onClick = onOpenSettings) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .glassSurfaceElevated(shape = RoundedCornerShape(19.dp), surfaceAlpha = 0.66f),
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
                    .clip(RoundedCornerShape(14.dp))
                    .background(
                        brush = if (active) {
                            Brush.horizontalGradient(
                                listOf(AppAuroraViolet.copy(alpha = 0.74f), AppAccent.copy(alpha = 0.70f), AppAuroraCyan.copy(alpha = 0.48f))
                            )
                        } else {
                            Brush.horizontalGradient(listOf(AppSurfaceRaised.copy(alpha = 0.76f), AppSurface.copy(alpha = 0.70f)))
                        },
                        shape = RoundedCornerShape(14.dp)
                    )
                    .border(0.5.dp, if (active) Color.White.copy(alpha = 0.22f) else AppOutline, RoundedCornerShape(14.dp))
                    .clickable { onSelect(view) }
                    .padding(horizontal = 14.dp, vertical = 9.dp)
            ) {
                Text(view.label, color = if (active) Color.White else AppTextSecondary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun LibrarySongList(
    songs: List<LocalSongEntity>,
    uiState: MainUiState,
    onPlay: (UnifiedTrackWithSources) -> Unit,
    onOpenTrackSheet: ((UnifiedTrackWithSources) -> Unit)?
) {
    if (songs.isEmpty()) {
        VantaEmptyState(
            title = "No songs found",
            description = "Scan your device storage or import audio files to see your songs here.",
            icon = Icons.Filled.MusicNote
        )
        return
    }

    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            text = "${songs.size} song${if (songs.size == 1) "" else "s"}",
            color = AppTextMuted,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp)
        )
        songs.forEach { song ->
            val unified = libraryTrackForSong(song, uiState.library) ?: song.toPlayableQueueItem()
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
                if (unified != null && onOpenTrackSheet != null) {
                    IconButton(onClick = { onOpenTrackSheet(unified) }) {
                        Icon(
                            Icons.Filled.MoreVert,
                            contentDescription = "More options for ${song.title}",
                            tint = AppTextMuted
                        )
                    }
                }
                Icon(
                    Icons.Filled.PlayArrow,
                    contentDescription = "Play ${song.title}",
                    tint = if (unified != null) AppAccent else AppTextMuted
                )
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
    onPlay: (UnifiedTrackWithSources) -> Unit,
    onOpenTrackSheet: ((UnifiedTrackWithSources) -> Unit)?
) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        items(songs, key = { it.id }) { song ->
            TrackCard(
                song = song,
                uiState = uiState,
                onPlay = onPlay,
                onOpenTrackSheet = onOpenTrackSheet
            )
        }
    }
}

@Composable
private fun LikedSongsCarousel(
    songs: List<LocalSongEntity>,
    uiState: MainUiState,
    onPlay: (UnifiedTrackWithSources) -> Unit,
    onOpenTrackSheet: ((UnifiedTrackWithSources) -> Unit)?,
    onToggleFavorite: (LocalSongEntity) -> Unit
) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        items(songs, key = { it.id }) { song ->
            TrackCard(
                song = song,
                uiState = uiState,
                onPlay = onPlay,
                onOpenTrackSheet = onOpenTrackSheet,
                showFavorite = true,
                isFavorite = true,
                onToggleFavorite = onToggleFavorite
            )
        }
    }
}

private sealed interface LikedFilter {
    data object All : LikedFilter
    data class Genre(val name: String) : LikedFilter
    data class Decade(val decade: Int) : LikedFilter
}

@Composable
private fun LikedSongsView(
    songs: List<LocalSongEntity>,
    albums: List<com.audiophile.musicplayer.data.local.entities.Album>,
    library: List<UnifiedTrackWithSources>,
    onPlay: (UnifiedTrackWithSources) -> Unit,
    onOpenTrackSheet: ((UnifiedTrackWithSources) -> Unit)?,
    onToggleFavorite: (LocalSongEntity) -> Unit
) {
    // Resolve a release year per liked song via the album table (album_name + artist_name).
    val yearByAlbumKey = remember(albums) {
        albums.associate {
            "${it.album_name.trim().lowercase()}|${it.artist_name.trim().lowercase()}" to it.release_year
        }
    }
    fun decadeOf(song: LocalSongEntity): Int? {
        val album = song.album?.trim()?.takeIf { it.isNotBlank() } ?: return null
        val year = yearByAlbumKey["$album|${song.artist.trim()}"] ?: return null
        if (year < 1950 || year > 2030) return null
        return (year / 10) * 10
    }

    val genreCounts = remember(songs) {
        songs.flatMap { it.genres.map { g -> g.trim() }.filter { it.isNotBlank() } }
            .groupBy { it.lowercase() }
            .map { (key, list) -> list.first() to list.size }
            .sortedByDescending { it.second }
            .take(8)
    }
    val decadeCounts = remember(songs, yearByAlbumKey) {
        songs.mapNotNull { decadeOf(it) }
            .groupingBy { it }
            .eachCount()
            .toList()
            .sortedByDescending { it.second }
            .take(6)
    }

    val filters = remember(songs, genreCounts, decadeCounts) {
        buildList {
            add(LikedFilter.All)
            genreCounts.forEach { (name, _) -> add(LikedFilter.Genre(name)) }
            decadeCounts.forEach { (decade, _) -> add(LikedFilter.Decade(decade)) }
        }
    }
    var selectedFilter by rememberSaveable { mutableStateOf("all") }

    fun filterKey(filter: LikedFilter): String = when (filter) {
        is LikedFilter.All -> "all"
        is LikedFilter.Genre -> "genre:${filter.name.lowercase()}"
        is LikedFilter.Decade -> "decade:${filter.decade}"
    }

    val currentFilter = remember(filters, selectedFilter) {
        filters.firstOrNull { filterKey(it) == selectedFilter } ?: LikedFilter.All
    }

    val filteredSongs = remember(songs, currentFilter) {
        songs.filter { song ->
            when (currentFilter) {
                is LikedFilter.All -> true
                is LikedFilter.Genre -> song.genres.any { it.equals(currentFilter.name, ignoreCase = true) }
                is LikedFilter.Decade -> decadeOf(song) == currentFilter.decade
            }
        }
    }

    if (songs.isEmpty()) {
        VantaEmptyState(
            title = "No liked songs yet",
            description = "Tap the heart on any song, or import a loved-songs CSV from Imports.",
            icon = Icons.Filled.Favorite
        )
        return
    }

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            VantaSectionHeader("${songs.size} liked song${if (songs.size == 1) "" else "s"}", modifier = Modifier.weight(1f))
            if (filteredSongs.size != songs.size) {
                androidx.compose.material3.TextButton(onClick = { selectedFilter = "all" }) {
                    Text("Clear", color = AppAccent)
                }
            }
        }
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(filters) { filter ->
                val active = filterKey(filter) == selectedFilter
                val label = when (filter) {
                    is LikedFilter.All -> "All"
                    is LikedFilter.Genre -> filter.name
                    is LikedFilter.Decade -> "${filter.decade}s"
                }
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(14.dp))
                        .background(
                            brush = if (active) {
                                Brush.horizontalGradient(
                                    listOf(AppAuroraViolet.copy(alpha = 0.74f), AppAccent.copy(alpha = 0.70f), AppAuroraCyan.copy(alpha = 0.48f))
                                )
                            } else {
                                Brush.horizontalGradient(listOf(AppSurfaceRaised.copy(alpha = 0.76f), AppSurface.copy(alpha = 0.70f)))
                            },
                            shape = RoundedCornerShape(14.dp)
                        )
                        .border(0.5.dp, if (active) Color.White.copy(alpha = 0.22f) else AppOutline, RoundedCornerShape(14.dp))
                        .clickable { selectedFilter = filterKey(filter) }
                        .padding(horizontal = 14.dp, vertical = 9.dp)
                ) {
                    Text(label, color = if (active) Color.White else AppTextSecondary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }
        Spacer(Modifier.height(4.dp))
        LikedSongRowList(
            songs = filteredSongs,
            library = library,
            onPlay = onPlay,
            onOpenTrackSheet = onOpenTrackSheet,
            onToggleFavorite = onToggleFavorite
        )
    }
}

@Composable
private fun LikedSongRowList(
    songs: List<LocalSongEntity>,
    library: List<UnifiedTrackWithSources>,
    onPlay: (UnifiedTrackWithSources) -> Unit,
    onOpenTrackSheet: ((UnifiedTrackWithSources) -> Unit)?,
    onToggleFavorite: (LocalSongEntity) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        songs.forEach { song ->
            val unified = remember(song.id, song.title, song.artist, library) {
                libraryTrackForSong(song, library)
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                NetworkArtwork(song.artworkUrl, song.title, Modifier.size(50.dp).clip(RoundedCornerShape(6.dp)))
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(song.title, color = AppText, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(song.artist, color = AppTextMuted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                IconButton(onClick = { onToggleFavorite(song) }) {
                    Icon(Icons.Filled.Favorite, contentDescription = "Remove ${song.title} from favorites", tint = AppAccent)
                }
                if (unified != null && onOpenTrackSheet != null) {
                    IconButton(onClick = { onPlay(unified) }) {
                        Icon(Icons.Filled.PlayArrow, contentDescription = "Play ${song.title}", tint = AppAccent)
                    }
                }
            }
        }
    }
}

@Composable
private fun RecentlyPlayedCarousel(
    songs: List<LocalSongEntity>,
    uiState: MainUiState,
    onPlay: (UnifiedTrackWithSources) -> Unit,
    onOpenTrackSheet: ((UnifiedTrackWithSources) -> Unit)?
) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        items(songs, key = { it.id }) { song ->
            TrackCard(
                song = song,
                uiState = uiState,
                onPlay = onPlay,
                onOpenTrackSheet = onOpenTrackSheet
            )
        }
    }
}

@Composable
private fun TrackCard(
    song: LocalSongEntity,
    uiState: MainUiState,
    onPlay: (UnifiedTrackWithSources) -> Unit,
    onOpenTrackSheet: ((UnifiedTrackWithSources) -> Unit)? = null,
    showFavorite: Boolean = false,
    isFavorite: Boolean = false,
    onToggleFavorite: ((LocalSongEntity) -> Unit)? = null
) {
    val unified = remember(song.id, song.title, song.artist, uiState.library) {
        libraryTrackForSong(song, uiState.library)
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
            Row(
                modifier = Modifier.align(Alignment.TopEnd).padding(6.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                if (showFavorite) {
                    LibraryCardAction(
                        icon = Icons.Filled.Favorite,
                        contentDescription = "Remove ${song.title} from favorites",
                        onClick = { onToggleFavorite?.invoke(song) }
                    )
                }
                if (unified != null && onOpenTrackSheet != null) {
                    LibraryCardAction(
                        icon = Icons.Filled.MoreVert,
                        contentDescription = "More options for ${song.title}",
                        onClick = { onOpenTrackSheet(unified) },
                        tint = AppText
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
private fun LibraryCardAction(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    tint: Color = AppAccent
) {
    Box(
        modifier = Modifier
            .size(30.dp)
            .clip(CircleShape)
            .background(Color.Black.copy(alpha = 0.58f))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = tint,
            modifier = Modifier.size(17.dp)
        )
    }
}

/** Maps the local-library row to its independently-keyed unified playback row. */
internal fun libraryTrackForSong(
    song: LocalSongEntity,
    library: List<UnifiedTrackWithSources>
): UnifiedTrackWithSources? =
    library.firstOrNull { it.track.localLibraryId == song.id }
        ?: library.firstOrNull { candidate ->
            candidate.track.title.trim().equals(song.title.trim(), ignoreCase = true) &&
                candidate.track.artist.trim().equals(song.artist.trim(), ignoreCase = true)
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
