package com.audiophile.musicplayer.ui

import androidx.compose.foundation.background
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
import com.audiophile.musicplayer.data.local.entities.UnifiedTrackWithSources

@Composable
fun MixDetailScreen(
    moodName: String,
    tracks: List<UnifiedTrackWithSources>,
    onBack: () -> Unit,
    onPlayTrack: (UnifiedTrackWithSources) -> Unit,
    onPlayAll: () -> Unit,
    onShuffle: () -> Unit,
    onNavigateToArtist: (String, String?) -> Unit = { _, _ -> },
    onNavigateToAlbum: (String, String, String?) -> Unit = { _, _, _ -> }
) {
    val gradientColors = when (moodName.lowercase()) {
        "chill" -> listOf(Color(0xFF1A2A3A), AppBackgroundBottom)
        "focus" -> listOf(Color(0xFF1A1A2A), AppBackgroundBottom)
        "energy" -> listOf(Color(0xFF2A1A1A), AppBackgroundBottom)
        "late night" -> listOf(Color(0xFF0A0A1A), AppBackgroundBottom)
        "happy" -> listOf(Color(0xFF2A2A1A), AppBackgroundBottom)
        "deep focus" -> listOf(Color(0xFF1A1A2A), AppBackgroundBottom)
        else -> listOf(AppBackgroundTop, AppBackgroundBottom)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(gradientColors))
            .statusBarsPadding()
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = AppText, modifier = Modifier.size(32.dp).clickable(onClick = onBack).padding(4.dp))
            Spacer(Modifier.weight(1f))
            Icon(Icons.Filled.MoreVert, contentDescription = "More", tint = AppTextSecondary, modifier = Modifier.size(32.dp).padding(4.dp))
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = appOverlayBottomPadding(miniPlayerVisible = false))
        ) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(240.dp)
                        .background(
                            Brush.radialGradient(
                                colors = listOf(AppAccent.copy(alpha = 0.3f), Color.Transparent)
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Filled.MusicNote,
                            contentDescription = null,
                            tint = AppAccent,
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(Modifier.height(16.dp))
                        Text("$moodName Mix", color = AppText, fontSize = 32.sp, fontWeight = FontWeight.Black)
                        Text(
                            "${tracks.size} tracks",
                            color = AppTextSecondary,
                            fontSize = 14.sp,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                }
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        modifier = Modifier.weight(1f).clip(RoundedCornerShape(16.dp)).background(AppAccent).clickable(onClick = onPlayAll).padding(vertical = 14.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(Icons.Filled.PlayArrow, contentDescription = null, tint = AppBackgroundBottom, modifier = Modifier.size(20.dp))
                            Text("Play", color = AppBackgroundBottom, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                        }
                    }
                    Box(
                        modifier = Modifier.weight(1f).clip(RoundedCornerShape(16.dp)).background(AppCard).clickable(onClick = onShuffle).padding(vertical = 14.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(Icons.Filled.Shuffle, contentDescription = null, tint = AppText, modifier = Modifier.size(20.dp))
                            Text("Shuffle", color = AppText, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                        }
                    }
                }
            }

            if (tracks.isEmpty()) {
                item {
                    Box(modifier = Modifier.fillMaxWidth().padding(48.dp), contentAlignment = Alignment.Center) {
                        Text("Not enough playable tracks yet — refresh and try again.", color = AppTextMuted, fontSize = 14.sp)
                    }
                }
            } else {
                itemsIndexed(tracks) { index, track ->
                    val display = remember(track.track) { TrackDisplayResolver.resolve(track.track) }
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 6.dp).clickable { onPlayTrack(track) },
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text("${index + 1}", color = AppTextMuted, fontSize = 14.sp, fontWeight = FontWeight.Bold, modifier = Modifier.width(24.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(display.title, color = AppText, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(display.artist, color = AppTextSecondary, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        if (track.sources.any { it.streamUrl.isNotBlank() }) {
                            Icon(Icons.Filled.PlayArrow, contentDescription = "Play", tint = AppAccent, modifier = Modifier.size(20.dp))
                        }
                    }
                }
            }
        }
    }
}
