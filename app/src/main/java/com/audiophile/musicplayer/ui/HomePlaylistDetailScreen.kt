package com.audiophile.musicplayer.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.audiophile.musicplayer.data.canonical.CanonicalTrack
import com.audiophile.musicplayer.data.source.GatewayHomePlaylist

@Composable
fun HomePlaylistDetailScreen(
    playlist: GatewayHomePlaylist?,
    tracks: List<CanonicalTrack>,
    loading: Boolean,
    error: String?,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onPlay: () -> Unit,
    onShuffle: () -> Unit,
    onPlayTrack: (Int) -> Unit,
    miniPlayerVisible: Boolean = false,
    bottomNavVisible: Boolean = false
) {
    Column(
        Modifier
            .fillMaxSize()
            .background(AppBackgroundBottom)
            .statusBarsPadding()
    ) {
        Row(
            Modifier.fillMaxWidth().height(52.dp).padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            androidx.compose.material3.IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = AppText)
            }
            Text(
                playlist?.name ?: "Playlist",
                color = AppText,
                fontSize = 17.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        LazyColumn(
            contentPadding = PaddingValues(
                start = 20.dp,
                end = 20.dp,
                bottom = appOverlayBottomPadding(miniPlayerVisible = miniPlayerVisible, bottomNavVisible = bottomNavVisible)
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                    NetworkArtwork(
                        artworkUrl = playlist?.artworkUrl,
                        seed = playlist?.name.orEmpty(),
                        modifier = Modifier.size(220.dp).clip(RoundedCornerShape(20.dp))
                    )
                    Spacer(Modifier.height(20.dp))
                    Text(
                        playlist?.name ?: "Playlist",
                        color = AppText,
                        fontSize = 26.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                    playlist?.curator?.takeIf { it.isNotBlank() }?.let {
                        Text(com.audiophile.musicplayer.data.source.debrandCuratorLabel(it), color = AppAccent, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                    }
                    playlist?.description?.takeIf { it.isNotBlank() }?.let {
                        Spacer(Modifier.height(10.dp))
                        Text(it, color = AppTextSecondary, fontSize = 14.sp)
                    }
                    if (tracks.isNotEmpty()) {
                        Spacer(Modifier.height(8.dp))
                        Text("${tracks.size} songs", color = AppTextSecondary, fontSize = 13.sp)
                    }
                }
            }
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    androidx.compose.material3.Button(
                        onClick = onPlay,
                        enabled = tracks.isNotEmpty() && !loading,
                        modifier = Modifier.weight(1f).height(48.dp),
                        colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                            containerColor = AppAccent,
                            contentColor = AppBackground
                        )
                    ) {
                        Icon(Icons.Filled.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Play")
                    }
                    androidx.compose.material3.OutlinedButton(
                        onClick = onShuffle,
                        enabled = tracks.isNotEmpty() && !loading,
                        modifier = Modifier.weight(1f).height(48.dp)
                    ) {
                        Icon(Icons.Filled.Shuffle, contentDescription = null, tint = AppAccent, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Shuffle", color = AppAccent)
                    }
                }
            }
            if (loading) item {
                Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = AppAccent)
                }
            }
            if (error != null || playlist == null) item {
                Text(error ?: "Open a playlist from Home or Search to see its songs.", color = AppTextSecondary)
                if (playlist != null) TextButton(onClick = onRetry) { Text("Try again", color = AppAccent) }
            }
            itemsIndexed(tracks, key = { index, track -> "${track.externalTrackId ?: track.title}-$index" }) { index, track ->
                Row(
                    Modifier.fillMaxWidth().clickable { onPlayTrack(index) }.padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text("${index + 1}", color = AppTextSecondary, modifier = Modifier.width(26.dp), fontSize = 13.sp)
                    NetworkArtwork(
                        artworkUrl = track.artworkUrl,
                        seed = track.title,
                        modifier = Modifier.size(48.dp).clip(RoundedCornerShape(8.dp))
                    )
                    Column(Modifier.weight(1f)) {
                        Text(track.title, color = AppText, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Medium)
                        Text(track.artist, color = AppTextSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 13.sp)
                    }
                    track.durationMs?.takeIf { it > 0 }?.let { duration ->
                        val seconds = duration / 1000
                        Text(
                            "${seconds / 60}:${(seconds % 60).toString().padStart(2, '0')}",
                            color = AppTextSecondary,
                            fontSize = 12.sp
                        )
                    }
                }
            }
        }
    }
}
