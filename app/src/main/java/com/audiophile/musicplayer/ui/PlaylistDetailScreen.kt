package com.audiophile.musicplayer.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
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
import com.audiophile.musicplayer.data.display.DisplayMetadataCleaner
import com.audiophile.musicplayer.data.local.entities.LocalSongEntity
import com.audiophile.musicplayer.ui.NetworkArtwork

@Composable
fun PlaylistDetailScreen(
    playlistName: String,
    playlistDescription: String?,
    playlistArtworkUrl: String?,
    tracks: List<LocalSongEntity>,
    onBack: () -> Unit,
    onPlayAll: () -> Unit,
    onShuffle: () -> Unit,
    onPlayTrack: (LocalSongEntity) -> Unit,
    onDeletePlaylist: () -> Unit,
    miniPlayerVisible: Boolean = false,
    bottomNavVisible: Boolean = false
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(AppBackgroundTop, AppBackgroundBottom)))
            .statusBarsPadding()
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Back",
                tint = AppText,
                modifier = Modifier.size(32.dp).clickable(onClick = onBack).padding(4.dp)
            )
            Spacer(Modifier.weight(1f))
            Icon(
                Icons.Filled.Delete,
                contentDescription = "Delete playlist",
                tint = AppWarning,
                modifier = Modifier.size(28.dp).clickable(onClick = onDeletePlaylist).padding(4.dp)
            )
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(4.dp),
            contentPadding = PaddingValues(
                bottom = appOverlayBottomPadding(
                    miniPlayerVisible = miniPlayerVisible,
                    bottomNavVisible = bottomNavVisible
                )
            )
        ) {
            item {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                Box(
                    modifier = Modifier
                        .size(200.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .background(AppAccent.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center
                ) {
                    if (playlistArtworkUrl != null) {
                        NetworkArtwork(artworkUrl = playlistArtworkUrl, seed = playlistName, modifier = Modifier.fillMaxSize())
                    } else {
                        Icon(
                            Icons.AutoMirrored.Filled.PlaylistPlay,
                            contentDescription = null,
                            tint = AppAccent,
                            modifier = Modifier.size(80.dp)
                        )
                    }
                }
                    Spacer(Modifier.height(20.dp))
                    Text(
                        playlistName,
                        color = AppText,
                        fontSize = 26.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (!playlistDescription.isNullOrBlank()) {
                        Spacer(Modifier.height(6.dp))
                        Text(
                            playlistDescription,
                            color = AppTextSecondary,
                            fontSize = 14.sp,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "${tracks.size} track${if (tracks.size != 1) "s" else ""}",
                        color = AppTextSecondary,
                        fontSize = 13.sp
                    )

                    Spacer(Modifier.height(20.dp))

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Spacer(Modifier.weight(1f))
                        Button(
                            onClick = onPlayAll,
                            colors = ButtonDefaults.buttonColors(containerColor = AppAccent)
                        ) {
                            Icon(Icons.Filled.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Play All")
                        }
                        OutlinedButton(onClick = onShuffle) {
                            Icon(Icons.Filled.Shuffle, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Shuffle")
                        }
                        Spacer(Modifier.weight(1f))
                    }
                }
            }

            itemsIndexed(tracks) { index, song ->
                PlaylistTrackRow(
                    song = song,
                    index = index + 1,
                    onClick = { onPlayTrack(song) }
                )
            }
        }
    }
}

@Composable
private fun PlaylistTrackRow(
    song: LocalSongEntity,
    index: Int,
    onClick: () -> Unit
) {
    val cleanTitle = remember(song.title, song.artist) {
        DisplayMetadataCleaner.computeDisplayMetadata(song.title, song.artist ?: "", null).title
    }
    val cleanArtist = remember(song.artist) {
        DisplayMetadataCleaner.cleanArtistName(song.artist) ?: song.artist
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            "$index",
            color = AppTextSecondary,
            fontSize = 14.sp,
            modifier = Modifier.width(28.dp)
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                cleanTitle,
                color = AppText,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                cleanArtist,
                color = AppTextSecondary,
                fontSize = 13.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        if (!song.streamUrl.isNullOrBlank()) {
            Icon(
                Icons.Filled.PlayCircleOutline,
                contentDescription = "Playable",
                tint = AppSuccess,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}
