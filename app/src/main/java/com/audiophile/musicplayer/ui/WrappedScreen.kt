package com.audiophile.musicplayer.ui

import androidx.compose.foundation.background
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.audiophile.musicplayer.data.local.ListeningAlbumAggregate
import com.audiophile.musicplayer.data.local.ListeningArtistAggregate
import com.audiophile.musicplayer.data.local.ListeningTrackAggregate
import com.audiophile.musicplayer.data.local.ListeningYearStats

@Composable
fun WrappedScreen(
    year: Int,
    stats: ListeningYearStats?,
    onBack: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(top = appTopContentPadding())
            .padding(bottom = appBottomWindowInsets() + 32.dp)
            .padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(22.dp)
    ) {
        Spacer(modifier = Modifier.height(4.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            IconButton(onClick = onBack) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = AppText,
                    modifier = Modifier.size(26.dp)
                )
            }
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    "YOUR YEAR IN MUSIC",
                    color = AppAccent,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.6.sp
                )
                Text("Wrapped $year", style = VantaType.pageTitle)
            }
        }

        when {
            stats == null -> {
                Box(
                    modifier = Modifier.fillMaxWidth().glassSurface().padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("Crunching your year...", color = AppTextSecondary, fontSize = 14.sp)
                }
            }
            stats.totalPlayCount == 0 -> {
                Box(
                    modifier = Modifier.fillMaxWidth().glassSurface().padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("No listening history yet", color = AppText, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                        Text(
                            "Keep playing music, or import your Spotify streaming history or an Apple Music library export from Imports.",
                            color = AppTextSecondary,
                            fontSize = 13.sp
                        )
                    }
                }
            }
            else -> {
                WrappedStatTiles(stats)
                if (stats.topArtists.isNotEmpty()) {
                    VantaSectionHeader("TOP ARTISTS")
                    stats.topArtists.take(5).forEachIndexed { index, artist ->
                        WrappedArtistRow(index + 1, artist)
                    }
                }
                if (stats.topTracks.isNotEmpty()) {
                    VantaSectionHeader("TOP TRACKS")
                    stats.topTracks.take(5).forEachIndexed { index, track ->
                        WrappedTrackRow(index + 1, track)
                    }
                }
                if (stats.topAlbums.isNotEmpty()) {
                    VantaSectionHeader("TOP ALBUMS")
                    stats.topAlbums.take(5).forEachIndexed { index, album ->
                        WrappedAlbumRow(index + 1, album)
                    }
                }
                WrappedHabitsCard(stats)
            }
        }
    }
}

@Composable
private fun WrappedStatTiles(stats: ListeningYearStats) {
    val skipRate = if (stats.totalPlayCount > 0) {
        (stats.skippedCount * 100) / stats.totalPlayCount
    } else 0
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        WrappedStatTile("Plays", stats.totalPlayCount.toString(), Modifier.weight(1f))
        WrappedStatTile("Listened", formatMinutes(stats.totalMinutesPlayed), Modifier.weight(1f))
        WrappedStatTile("Skipped", "$skipRate%", Modifier.weight(1f))
    }
}

@Composable
private fun WrappedStatTile(label: String, value: String, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(
                brush = Brush.horizontalGradient(
                    listOf(AppAuroraViolet.copy(alpha = 0.30f), AppAccent.copy(alpha = 0.26f), AppAuroraCyan.copy(alpha = 0.22f))
                )
            )
            .padding(horizontal = 14.dp, vertical = 16.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(label.uppercase(), color = AppAccentSecondary, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.1.sp)
            Text(value, color = AppText, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun WrappedArtistRow(rank: Int, artist: ListeningArtistAggregate) {
    Row(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).glassSurface().padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text(rank.toString(), color = AppAccent, fontSize = 18.sp, fontWeight = FontWeight.Bold, modifier = Modifier.size(24.dp))
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(artist.artist, color = AppText, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            Text(
                "${artist.playCount} plays · ${formatMinutes(artist.totalMsPlayed / 60_000L)}",
                color = AppTextMuted,
                fontSize = 12.sp
            )
        }
    }
}

@Composable
private fun WrappedTrackRow(rank: Int, track: ListeningTrackAggregate) {
    Row(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).glassSurface().padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text(rank.toString(), color = AppAccent, fontSize = 18.sp, fontWeight = FontWeight.Bold, modifier = Modifier.size(24.dp))
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(track.title, color = AppText, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            Text(
                "${track.artist} · ${track.playCount} plays",
                color = AppTextMuted,
                fontSize = 12.sp
            )
        }
    }
}

@Composable
private fun WrappedAlbumRow(rank: Int, album: ListeningAlbumAggregate) {
    Row(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).glassSurface().padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text(rank.toString(), color = AppAccent, fontSize = 18.sp, fontWeight = FontWeight.Bold, modifier = Modifier.size(24.dp))
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(album.album, color = AppText, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            Text(
                "${album.artist} · ${album.playCount} plays · ${formatMinutes(album.totalMsPlayed / 60_000L)}",
                color = AppTextMuted,
                fontSize = 12.sp
            )
        }
    }
}

@Composable
private fun WrappedHabitsCard(stats: ListeningYearStats) {
    val peak = stats.hours.maxByOrNull { it.playCount }
    val quiet = stats.hours.filter { it.playCount > 0 }.minByOrNull { it.playCount } ?: stats.hours.firstOrNull()
    VantaSectionHeader("LISTENING HABITS")
    Column(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).glassSurface().padding(16.dp)) {
        Text("Peak listening hour", color = AppTextSecondary, fontSize = 12.sp)
        peak?.let { Text(formatHour(it.hourOfDay), color = AppText, fontSize = 20.sp, fontWeight = FontWeight.Bold) }
            ?: Text("Not enough data", color = AppTextMuted, fontSize = 14.sp)
        Spacer(modifier = Modifier.height(12.dp))
        Text("Quietest hour", color = AppTextSecondary, fontSize = 12.sp)
        quiet?.let { Text(formatHour(it.hourOfDay), color = AppText, fontSize = 20.sp, fontWeight = FontWeight.Bold) }
            ?: Text("Not enough data", color = AppTextMuted, fontSize = 14.sp)
    }
}

private fun formatHour(hourOfDay: Int): String {
    val amPm = if (hourOfDay < 12) "AM" else "PM"
    val h = if (hourOfDay % 12 == 0) 12 else hourOfDay % 12
    return "$h $amPm"
}

private fun formatMinutes(totalMinutes: Long): String {
    val h = totalMinutes / 60
    val m = totalMinutes % 60
    return when {
        h > 0 && m > 0 -> "${h}h ${m}m"
        h > 0 -> "${h}h"
        else -> "${m}m"
    }
}