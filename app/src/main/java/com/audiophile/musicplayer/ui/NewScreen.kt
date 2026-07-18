package com.audiophile.musicplayer.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.NewReleases
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Radar
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.Spa
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material.icons.filled.PlaylistPlay
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.audiophile.musicplayer.data.local.entities.LocalSongEntity
import com.audiophile.musicplayer.data.local.entities.PlaylistEntity
import com.audiophile.musicplayer.data.source.SourceSearchResult

@Composable
fun NewScreen(
    localPlaylists: List<PlaylistEntity>,
    localSongs: List<LocalSongEntity>,
    editorialReleases: List<SourceSearchResult>,
    editorialReleasesLoading: Boolean,
    onLoadEditorialReleases: () -> Unit,
    onOpenPlaylist: (Long) -> Unit,
    onOpenImportFromLink: () -> Unit,
    onNavigateToAlbum: (String, String, String?) -> Unit,
    onPlayRelease: (SourceSearchResult) -> Unit,
    onPlayTodaysDrop: () -> Unit,
    onShuffleTodaysDrop: () -> Unit,
    onSaveTodaysDrop: () -> Unit,
    miniPlayerVisible: Boolean = false
) {
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.screenWidthDp > configuration.screenHeightDp
    LaunchedEffect(Unit) { onLoadEditorialReleases() }
    val freshPlaylists = remember(localPlaylists) {
        localPlaylists.filter { it.name.isNotBlank() }.take(6)
    }
    val recentSongs = remember(localSongs) {
        localSongs
            .filter { it.title.isNotBlank() && it.artist.isNotBlank() }
            .sortedByDescending { it.dateAdded }
            .take(8)
    }
    val editorialAlbums = remember(editorialReleases) {
        editorialReleases
            .filter { it.artist.isNotBlank() && (it.album?.isNotBlank() == true || it.title.isNotBlank()) }
            .distinctBy { "${it.album ?: it.title}|${it.artist}".lowercase() }
            .take(12)
    }
    val bestNewSongs = remember(editorialReleases) {
        editorialReleases
            .filter { it.title.isNotBlank() && it.artist.isNotBlank() }
            .distinctBy { "${it.title}|${it.artist}".lowercase() }
            .take(5)
    }
    val freshAlbums = remember(localSongs) {
        localSongs
            .filter { !it.album.isNullOrBlank() }
            .groupBy { "${it.album.orEmpty().trim()}|${it.artist.trim()}" }
            .values
            .mapNotNull { songs ->
                val first = songs.maxByOrNull { it.dateAdded } ?: return@mapNotNull null
                NewAlbumSummary(
                    title = first.album.orEmpty(),
                    artist = first.artist,
                    artworkUrl = first.artworkUrl,
                    latestAdded = songs.maxOf { it.dateAdded }
                )
            }
            .sortedByDescending { it.latestAdded }
            .take(8)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(top = appTopContentPadding())
            .padding(bottom = appBottomContentPadding(isMiniPlayerVisible = miniPlayerVisible)),
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        Column(modifier = Modifier.padding(horizontal = 24.dp)) {
            Text("New", style = VantaType.pageTitle)
            Text("A considered edit of what just arrived.", style = VantaType.editorialBody)
        }

        TodaysDropExperience(
            featured = editorialAlbums.firstOrNull() ?: bestNewSongs.firstOrNull(),
            songs = bestNewSongs,
            loading = editorialReleasesLoading,
            onPlayDrop = onPlayTodaysDrop,
            onShuffleDrop = onShuffleTodaysDrop,
            onSaveDrop = onSaveTodaysDrop,
            onPlaySong = onPlayRelease,
            modifier = Modifier.padding(horizontal = 16.dp)
        )

        if (editorialAlbums.isNotEmpty()) {
            if (editorialAlbums.size > 1) {
                VantaSectionHeader("Best new songs", modifier = Modifier.padding(horizontal = 24.dp))
                Column(modifier = Modifier.padding(horizontal = 24.dp)) {
                    bestNewSongs.forEach { release ->
                        BestNewSongRow(release = release, onPlay = { onPlayRelease(release) })
                    }
                }
                VantaSectionHeader("New this week", modifier = Modifier.padding(horizontal = 24.dp))
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(horizontal = 24.dp)
                ) {
                    items(editorialAlbums.drop(1), key = { "${it.album ?: it.title}|${it.artist}" }) { release ->
                    EditorialReleaseCard(release) {
                        onNavigateToAlbum(release.album ?: release.title, release.artist, release.artworkUrl)
                    }
                }
                }
            }
        } else if (editorialReleasesLoading) {
            NewEditorialLoading(isLandscape = isLandscape, modifier = Modifier.padding(horizontal = 24.dp))
        }

        if (localSongs.isEmpty()) {
            NewLibraryStartCard(
                modifier = Modifier.padding(horizontal = 24.dp),
                onClick = onOpenImportFromLink
            )
        }

        if (recentSongs.isNotEmpty()) {
            VantaSectionHeader("New to your library", modifier = Modifier.padding(horizontal = 24.dp))
            Spacer(Modifier.height(8.dp))
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(horizontal = 24.dp)
            ) {
                items(recentSongs) { song ->
                    NewSongCard(
                        song = song,
                        onClick = {
                            val album = song.album
                            if (!album.isNullOrBlank()) {
                                onNavigateToAlbum(album, song.artist, song.artworkUrl)
                            } else {
                                onOpenImportFromLink()
                            }
                        }
                    )
                }
            }
        }

        if (freshAlbums.isNotEmpty()) {
            VantaSectionHeader("Fresh Albums", modifier = Modifier.padding(horizontal = 24.dp))
            Spacer(Modifier.height(8.dp))
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(horizontal = 24.dp)
            ) {
                items(freshAlbums) { album ->
                    AlbumChip(
                        album = album.title,
                        artist = album.artist,
                        artworkUrl = album.artworkUrl,
                        onClick = { onNavigateToAlbum(album.title, album.artist, album.artworkUrl) }
                    )
                }
            }
        }

        if (freshPlaylists.isNotEmpty()) {
            VantaSectionHeader("Recently Updated", modifier = Modifier.padding(horizontal = 24.dp))
            Spacer(Modifier.height(8.dp))
            Column(
                modifier = Modifier.padding(horizontal = 24.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                freshPlaylists.forEach { playlist ->
                    PlaylistChip(
                        name = playlist.name,
                        onClick = { onOpenPlaylist(playlist.id) }
                    )
                }
            }
        }

        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun TodaysDropExperience(
    featured: SourceSearchResult?,
    songs: List<SourceSearchResult>,
    loading: Boolean,
    onPlayDrop: () -> Unit,
    onShuffleDrop: () -> Unit,
    onSaveDrop: () -> Unit,
    onPlaySong: (SourceSearchResult) -> Unit,
    modifier: Modifier = Modifier
) {
    val enabled = featured != null && songs.isNotEmpty()
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(AppSurface.copy(alpha = 0.72f))
            .border(0.5.dp, AppOutline.copy(alpha = 0.45f), RoundedCornerShape(22.dp))
            .padding(bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(0.92f)
                .clip(RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp))
        ) {
            NetworkArtwork(
                artworkUrl = featured?.artworkUrl,
                seed = featured?.let { "${it.title}-${it.artist}" } ?: "todays-drop",
                modifier = Modifier.fillMaxSize()
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            0f to Color.Black.copy(alpha = 0.08f),
                            0.48f to Color.Black.copy(alpha = 0.30f),
                            1f to Color.Black.copy(alpha = 0.94f)
                        )
                    )
            )
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(18.dp)
                    .clip(RoundedCornerShape(999.dp))
                    .background(Color.Black.copy(alpha = 0.38f))
                    .border(0.5.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(999.dp))
                    .padding(horizontal = 12.dp, vertical = 7.dp)
            ) {
                Text("VANTA DAILY", color = AppAccentSecondary, fontSize = 10.sp, fontWeight = FontWeight.Bold)
            }
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(horizontal = 22.dp, vertical = 26.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("Today's Drop", color = Color.White, fontSize = 34.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(6.dp))
                Text(
                    featured?.artist?.takeIf { it.isNotBlank() } ?: "Fresh picks for right now",
                    color = Color.White.copy(alpha = 0.78f),
                    fontSize = 14.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    if (loading) "Preparing your edit" else "${songs.size.coerceAtLeast(1)} premium picks",
                    color = Color.White.copy(alpha = 0.54f),
                    fontSize = 12.sp
                )
                Spacer(Modifier.height(22.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    DropRoundAction(
                        icon = Icons.Filled.Shuffle,
                        contentDescription = "Shuffle Today's Drop",
                        enabled = enabled,
                        onClick = onShuffleDrop
                    )
                    DropPlayButton(enabled = enabled, onClick = onPlayDrop)
                    DropRoundAction(
                        icon = Icons.Filled.Add,
                        contentDescription = "Save Today's Drop to library",
                        enabled = enabled,
                        onClick = onSaveDrop
                    )
                }
            }
        }

        DropReasonCard(modifier = Modifier.padding(horizontal = 14.dp))

        Column(
            modifier = Modifier.padding(horizontal = 14.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            songs.take(5).forEachIndexed { index, song ->
                DropTrackPreviewRow(
                    index = index + 1,
                    release = song,
                    onPlay = { onPlaySong(song) }
                )
            }
            if (songs.isEmpty()) {
                Text(
                    if (loading) "Fresh tracks are loading..." else "Today's Drop will appear when new releases are available.",
                    color = AppTextMuted,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 12.dp)
                )
            }
        }
    }
}

@Composable
private fun DropPlayButton(enabled: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .height(58.dp)
            .width(184.dp)
            .clip(RoundedCornerShape(999.dp))
            .background(if (enabled) Color.White else Color.White.copy(alpha = 0.32f))
            .clickable(enabled = enabled, onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        Icon(Icons.Filled.PlayArrow, contentDescription = null, tint = Color.Black, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(8.dp))
        Text("Play", color = Color.Black, fontSize = 18.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun DropRoundAction(
    icon: ImageVector,
    contentDescription: String,
    enabled: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(62.dp)
            .clip(RoundedCornerShape(999.dp))
            .background(Color.White.copy(alpha = if (enabled) 0.14f else 0.07f))
            .border(0.5.dp, Color.White.copy(alpha = 0.10f), RoundedCornerShape(999.dp))
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            icon,
            contentDescription = contentDescription,
            tint = Color.White.copy(alpha = if (enabled) 0.92f else 0.34f),
            modifier = Modifier.size(24.dp)
        )
    }
}

@Composable
private fun DropReasonCard(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(AppSurfaceRaised.copy(alpha = 0.82f))
            .border(0.5.dp, AppOutline.copy(alpha = 0.50f), RoundedCornerShape(16.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(13.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(Icons.Filled.Spa, contentDescription = null, tint = AppAccent, modifier = Modifier.size(16.dp))
            Text("WHY THIS", color = AppTextMuted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        }
        DropReasonLine(Icons.Filled.GraphicEq, "Built from the live VANTA new-release feed")
        DropReasonLine(Icons.Filled.Radar, "Every tap resolves a real playable source before playback")
    }
}

@Composable
private fun DropReasonLine(icon: ImageVector, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Icon(icon, contentDescription = null, tint = AppAccent, modifier = Modifier.size(17.dp))
        Text(text, color = AppText, fontSize = 14.sp, lineHeight = 19.sp)
    }
}

@Composable
private fun DropTrackPreviewRow(
    index: Int,
    release: SourceSearchResult,
    onPlay: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onPlay)
            .padding(horizontal = 10.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(index.toString(), color = AppTextMuted, fontSize = 13.sp, modifier = Modifier.width(24.dp))
        NetworkArtwork(
            artworkUrl = release.artworkUrl,
            seed = "${release.title}-${release.artist}",
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(7.dp))
        )
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                release.title,
                color = AppText,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                release.artist,
                color = AppTextSecondary,
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        release.durationMs?.takeIf { it > 0L }?.let {
            Text(formatDropDuration(it), color = AppTextMuted, fontSize = 12.sp)
            Spacer(Modifier.width(10.dp))
        }
        Icon(Icons.Filled.MoreVert, contentDescription = null, tint = AppTextMuted, modifier = Modifier.size(20.dp))
    }
}

private fun formatDropDuration(durationMs: Long): String {
    val totalSeconds = (durationMs / 1000L).coerceAtLeast(0L)
    val minutes = totalSeconds / 60L
    val seconds = totalSeconds % 60L
    return "$minutes:${seconds.toString().padStart(2, '0')}"
}

private data class NewAlbumSummary(
    val title: String,
    val artist: String,
    val artworkUrl: String?,
    val latestAdded: Long
)

@Composable
private fun NewEditorialHero(
    release: SourceSearchResult,
    isLandscape: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .then(if (isLandscape) Modifier.height(190.dp) else Modifier.aspectRatio(1.42f))
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
    ) {
        NetworkArtwork(
            artworkUrl = release.artworkUrl,
            seed = "${release.title}-${release.artist}",
            modifier = Modifier.fillMaxSize()
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0.25f to Color.Transparent,
                        1f to Color.Black.copy(alpha = 0.88f)
                    )
                )
        )
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(20.dp)
        ) {
            Text("VANTA EDIT", style = VantaType.caption, color = AppAccent)
            Spacer(Modifier.height(6.dp))
            Text(
                release.album?.takeIf { it.isNotBlank() } ?: release.title,
                style = VantaType.editorialLarge,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Text(release.artist, style = VantaType.subtitle, color = AppText)
        }
    }
}

@Composable
private fun NewEditorialLoading(isLandscape: Boolean, modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .then(if (isLandscape) Modifier.height(190.dp) else Modifier.aspectRatio(1.42f))
                .clip(RoundedCornerShape(8.dp))
                .background(AppSurfaceRaised)
                .border(0.5.dp, AppOutline, RoundedCornerShape(8.dp))
        )
        Text("Preparing today's edit", style = VantaType.editorialBody, color = AppTextSecondary)
    }
}

@Composable
private fun BestNewSongRow(release: SourceSearchResult, onPlay: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onPlay)
            .padding(vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        NetworkArtwork(
            artworkUrl = release.artworkUrl,
            seed = "${release.title}-${release.artist}",
            modifier = Modifier
                .size(52.dp)
                .clip(RoundedCornerShape(6.dp))
        )
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                release.title,
                color = AppText,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                release.artist,
                color = AppTextSecondary,
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Icon(
            Icons.Filled.PlayArrow,
            contentDescription = "Play ${release.title}",
            tint = AppAccent,
            modifier = Modifier.size(26.dp)
        )
    }
}

@Composable
private fun NewLibraryStartCard(modifier: Modifier = Modifier, onClick: () -> Unit) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(AppAccent.copy(alpha = 0.08f))
            .clickable(onClick = onClick)
            .padding(18.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Icon(Icons.Filled.AutoAwesome, contentDescription = null, tint = AppAccent, modifier = Modifier.size(30.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text("Build your VANTA library", color = AppText, fontSize = 17.sp, fontWeight = FontWeight.Bold)
            Text("Import links and local tracks to unlock fresh releases, quality shelves, and smarter radio.", color = AppTextSecondary, fontSize = 13.sp)
        }
    }
}

@Composable
private fun NewSongCard(song: LocalSongEntity, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .width(168.dp)
            .height(194.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(AppSurface.copy(alpha = 0.5f))
            .clickable(onClick = onClick)
            .padding(14.dp)
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(104.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(AppAccent.copy(alpha = 0.10f)),
            ) {
                NetworkArtwork(
                    artworkUrl = song.artworkUrl,
                    seed = "${song.title}-${song.artist}",
                    modifier = Modifier.fillMaxSize()
                )
            }
            Spacer(Modifier.height(10.dp))
            Text(song.title, color = AppText, fontSize = 14.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(song.artist, color = AppTextSecondary, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            song.quality?.takeIf { it.isNotBlank() }?.let {
                Spacer(Modifier.height(6.dp))
                Text(it, color = AppAccent, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
private fun EditorialReleaseCard(release: SourceSearchResult, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .width(176.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(AppSurface.copy(alpha = 0.5f))
            .clickable(onClick = onClick)
            .padding(12.dp)
    ) {
        Column {
            NetworkArtwork(
                artworkUrl = release.artworkUrl,
                seed = "${release.title}-${release.artist}",
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .clip(RoundedCornerShape(6.dp))
            )
            Spacer(Modifier.height(9.dp))
            Text(release.album?.takeIf { it.isNotBlank() } ?: release.title, color = AppText, fontSize = 14.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(release.artist, color = AppTextSecondary, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("ALBUM", color = AppAccent, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun QualitySongCard(song: LocalSongEntity) {
    Box(
        modifier = Modifier
            .width(170.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(AppAccent.copy(alpha = 0.08f))
            .padding(14.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Icon(Icons.Filled.AutoAwesome, contentDescription = null, tint = AppAccent, modifier = Modifier.size(24.dp))
            Text(song.title, color = AppText, fontSize = 14.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(song.artist, color = AppTextSecondary, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(song.quality.orEmpty(), color = AppAccent, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun QualityDiscoveryStrip(modifier: Modifier = Modifier, onClick: () -> Unit) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(AppSurface.copy(alpha = 0.5f))
            .clickable(onClick = onClick)
            .padding(18.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Icon(Icons.Filled.Radar, contentDescription = null, tint = AppAccent, modifier = Modifier.size(28.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text("Find higher-quality sources", color = AppText, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            Text("VANTA will only label lossless, spatial, surround, or Atmos when a source actually reports it.", color = AppTextSecondary, fontSize = 13.sp)
        }
    }
}

@Composable
private fun NewReleaseCard(title: String, subtitle: String, index: Int) {
    val colors = listOf(
        listOf(Color(0xFF6B2FA0), Color(0xFF2D1B69)),
        listOf(Color(0xFFE94E77), Color(0xFF6B2FA0)),
        listOf(Color(0xFF2E8B57), Color(0xFF1A4A2E)),
        listOf(Color(0xFFD4A017), Color(0xFF8B4513))
    )
    val gradient = colors[index % colors.size]
    Box(
        modifier = Modifier
            .width(160.dp)
            .height(180.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(Brush.verticalGradient(gradient))
            .clickable { }
            .padding(16.dp),
        contentAlignment = Alignment.BottomStart
    ) {
        Column {
            Icon(
                Icons.Filled.NewReleases,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.5f),
                modifier = Modifier.size(32.dp)
            )
            Spacer(Modifier.weight(1f))
            Text(title, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(4.dp))
            Text(subtitle, color = Color.White.copy(alpha = 0.6f), fontSize = 12.sp)
        }
    }
}

@Composable
private fun AlbumChip(album: String, artist: String, artworkUrl: String?, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .width(140.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(AppSurface.copy(alpha = 0.5f))
            .clickable(onClick = onClick)
            .padding(12.dp)
    ) {
        Column {
            Box(
                modifier = Modifier
                    .size(116.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(AppAccent.copy(alpha = 0.08f)),
            ) {
                NetworkArtwork(
                    artworkUrl = artworkUrl,
                    seed = "$album-$artist",
                    modifier = Modifier.fillMaxSize()
                )
            }
            Spacer(Modifier.height(8.dp))
            Text(album, color = AppText, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(artist, color = AppTextSecondary, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun ReleaseRadarStrip(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(AppAccent.copy(alpha = 0.06f))
            .clickable { }
            .padding(18.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(AppAccent.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Filled.Radar,
                contentDescription = null,
                tint = AppAccent,
                modifier = Modifier.size(26.dp)
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text("Release Radar", color = AppText, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            Text("New tracks from artists you play most", color = AppTextSecondary, fontSize = 13.sp)
        }
    }
}

@Composable
private fun TrendingChips(genres: List<String>, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        genres.take(4).forEach { genre ->
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(14.dp))
                    .background(AppAccent.copy(alpha = 0.08f))
                    .clickable { }
                    .padding(vertical = 12.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(genre, color = AppAccent, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun SeasonalPlaylistCard(title: String, subtitle: String) {
    Box(
        modifier = Modifier
            .width(180.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(AppSurface.copy(alpha = 0.4f))
            .clickable { }
            .padding(14.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Icon(
                Icons.Filled.CalendarMonth,
                contentDescription = null,
                tint = AppAccent,
                modifier = Modifier.size(28.dp)
            )
            Spacer(Modifier.height(4.dp))
            Text(title, color = AppText, fontSize = 15.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(subtitle, color = AppTextSecondary, fontSize = 12.sp)
        }
    }
}

@Composable
private fun PlaylistChip(name: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(AppSurface.copy(alpha = 0.3f))
            .clickable(onClick = onClick)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(
            Icons.Filled.PlaylistPlay,
            contentDescription = null,
            tint = AppAccent,
            modifier = Modifier.size(22.dp)
        )
        Text(name, color = AppText, fontSize = 14.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}
