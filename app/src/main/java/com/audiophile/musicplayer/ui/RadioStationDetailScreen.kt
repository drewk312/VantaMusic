package com.audiophile.musicplayer.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.audiophile.musicplayer.data.display.TrackDisplayResolver
import com.audiophile.musicplayer.data.dj.JukeboxStation
import com.audiophile.musicplayer.data.dj.JukeboxStationType
import com.audiophile.musicplayer.data.local.entities.UnifiedTrackWithSources
import com.audiophile.musicplayer.ui.theme.TrackListShimmer

@Composable
fun RadioStationDetailScreen(
    station: JukeboxStation,
    previewTracks: List<UnifiedTrackWithSources>,
    isLoadingPreview: Boolean,
    isStarting: Boolean = false,
    statusMessage: String? = null,
    onBack: () -> Unit,
    onStartStation: () -> Unit,
    onShuffleStation: () -> Unit = onStartStation,
    onPlayPreviewTrack: (UnifiedTrackWithSources) -> Unit,
    miniPlayerVisible: Boolean = false,
    bottomNavVisible: Boolean = true
) {
    val scrollState = rememberScrollState()
    val bottomPadding = appOverlayBottomPadding(
        miniPlayerVisible = miniPlayerVisible,
        bottomNavVisible = bottomNavVisible
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(AppBackground)
            .verticalScroll(scrollState)
            .padding(bottom = bottomPadding)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Filled.KeyboardArrowDown,
                contentDescription = "Back",
                tint = AppTextSecondary,
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .clickable(onClick = onBack)
                    .padding(8.dp)
            )
            Spacer(Modifier.weight(1f))
            Text(text = station.emoji, fontSize = 28.sp)
            Spacer(Modifier.width(12.dp))
            Icon(
                imageVector = Icons.Filled.MoreVert,
                contentDescription = "Options",
                tint = AppTextSecondary,
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .clickable { }
                    .padding(8.dp)
            )
        }

        Spacer(Modifier.height(16.dp))

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = station.name,
                style = VantaType.editorialLarge.copy(
                    fontSize = 32.sp,
                    lineHeight = 36.sp
                ),
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                color = AppText
            )

            Spacer(Modifier.height(12.dp))

            Box(
                modifier = Modifier
                    .padding(horizontal = 60.dp)
                    .height(1.dp)
                    .fillMaxWidth()
                    .background(
                        Brush.horizontalGradient(
                            listOf(
                                Color.Transparent,
                                AppAccent.copy(alpha = 0f),
                                AppAccent.copy(alpha = 0.4f),
                                AppAccent.copy(alpha = 0f),
                            )
                        )
                    )
            )

            Spacer(Modifier.height(16.dp))

            if (station.description.isNotBlank()) {
                Text(
                    text = station.description,
                    style = VantaType.subtitle,
                    color = AppTextSecondary,
                    textAlign = TextAlign.Center,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    lineHeight = 20.sp
                )
            }

            eraLabel(station)?.let { label ->
                Spacer(Modifier.height(6.dp))
                Text(
                    text = label,
                    style = VantaType.caption,
                    color = AppAccent.copy(alpha = 0.6f),
                    letterSpacing = 1.5.sp
                )
            }

            if (!statusMessage.isNullOrBlank() && !statusMessage.startsWith("Playing")) {
                Spacer(Modifier.height(10.dp))
                Text(
                    text = statusMessage,
                    style = VantaType.caption,
                    color = AppWarning,
                    textAlign = TextAlign.Center,
                    lineHeight = 16.sp
                )
            }
        }

        Spacer(Modifier.height(32.dp))

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 48.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(28.dp))
                    .border(0.5.dp, Color.White.copy(alpha = 0.06f), RoundedCornerShape(28.dp))
                    .clickable(enabled = !isStarting, onClick = onShuffleStation)
                    .padding(16.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Filled.Shuffle,
                    contentDescription = "Shuffle Station",
                    tint = AppAccent.copy(alpha = 0.7f),
                    modifier = Modifier.size(28.dp)
                )
                Spacer(Modifier.width(12.dp))
                Text(
                    text = "Shuffle Station",
                    style = VantaType.songTitle,
                    color = AppText
                )
            }

            Spacer(Modifier.height(20.dp))

            Box(
                modifier = Modifier
                    .size(88.dp)
                    .shadow(
                        elevation = 16.dp,
                        shape = CircleShape,
                        ambientColor = Color.Black.copy(alpha = 0.5f),
                        spotColor = AppAccent.copy(alpha = 0.2f)
                    )
                    .clip(CircleShape)
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                AppAccent.copy(alpha = 0.2f),
                                AppAccent.copy(alpha = 0.08f)
                            )
                        )
                    )
                    .border(0.5.dp, AppAccent.copy(alpha = 0.15f), CircleShape)
                    .clickable(enabled = !isStarting, onClick = onStartStation),
                contentAlignment = Alignment.Center
            ) {
                if (isStarting) {
                    CircularProgressIndicator(
                        color = Color.White,
                        modifier = Modifier.size(32.dp),
                        strokeWidth = 2.5.dp
                    )
                } else {
                    Icon(
                        Icons.Filled.PlayArrow,
                        contentDescription = "Play station",
                        tint = Color.White,
                        modifier = Modifier.size(40.dp)
                    )
                }
            }

            Spacer(Modifier.height(8.dp))

            Text(
                text = if (isStarting) "Starting…" else "Play Station",
                style = VantaType.subtitle,
                color = AppTextSecondary
            )
        }

        Spacer(Modifier.height(40.dp))

        if (isLoadingPreview) {
            TrackListShimmer(
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
                count = 5
            )
        } else if (previewTracks.isEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 32.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    "No tracks loaded yet",
                    style = VantaType.songTitle,
                    color = AppTextMuted
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    "Tap Play to start the AI curation",
                    style = VantaType.subtitle,
                    color = AppTextMuted
                )
            }
        } else {
            Text(
                text = "Tracks in this mix",
                style = VantaType.caption,
                color = AppAccent.copy(alpha = 0.5f),
                letterSpacing = 1.5.sp,
                modifier = Modifier.padding(horizontal = 24.dp)
            )

            Spacer(Modifier.height(12.dp))

            previewTracks.forEach { track ->
                StationTrackRow(
                    track = track,
                    onPlay = { onPlayPreviewTrack(track) }
                )
            }
        }

        Spacer(Modifier.height(32.dp))
    }
}

@Composable
private fun StationTrackRow(
    track: UnifiedTrackWithSources,
    onPlay: () -> Unit
) {
    val display = remember(track.track) { TrackDisplayResolver.resolve(track.track) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 6.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(AppSurfaceRaised)
            .clickable(onClick = onPlay)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        NetworkArtwork(
            artworkUrl = track.track.coverArtUrl,
            seed = display.title,
            modifier = Modifier
                .size(52.dp)
                .clip(RoundedCornerShape(10.dp))
        )

        Spacer(Modifier.width(14.dp))

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = display.title,
                style = VantaType.songTitle,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = AppText
            )
            if (display.artist.isNotBlank()) {
                Text(
                    text = display.artist,
                    style = VantaType.caption,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = AppTextSecondary
                )
            }
        }

        Spacer(Modifier.width(14.dp))

        Icon(
            Icons.Filled.PlayArrow,
            contentDescription = "Play",
            tint = AppAccent,
            modifier = Modifier.size(28.dp)
        )
    }
}

@Composable
fun RadioStationSearchCard(
    station: JukeboxStation,
    onOpen: () -> Unit,
    onStart: () -> Unit,
    modifier: Modifier = Modifier
) {
    val accent = stationAccentColor(station)
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(132.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(AppSurface)
            .border(0.5.dp, accent.copy(alpha = 0.18f), RoundedCornerShape(18.dp))
            .clickable(onClick = onOpen)
            .padding(16.dp)
    ) {
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .size(40.dp)
                .clip(CircleShape)
                .background(accent.copy(alpha = 0.14f))
                .clickable(onClick = onStart)
                .padding(8.dp),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Filled.PlayArrow,
                contentDescription = "Start station",
                tint = AppAccent,
                modifier = Modifier.size(22.dp)
            )
        }

        Column(
            modifier = Modifier.align(Alignment.BottomStart),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(station.emoji, fontSize = 22.sp)
            Text(
                station.name,
                color = AppText,
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                stationKindLabel(station),
                color = AppTextMuted,
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

private fun eraLabel(station: JukeboxStation): String? {
    val start = station.decadeStart ?: return null
    val startLabel = "${start}s"
    val end = station.decadeEnd
    return if (end != null && end != start) "$startLabel–${end}s" else startLabel
}

private fun stationAccentColor(station: JukeboxStation): Color {
    val decade = station.decadeStart
    if (decade != null) {
        return when {
            decade < 1970 -> Color(0xFFD4C574)
            decade < 1980 -> Color(0xFFB8A0D4)
            decade < 1990 -> Color(0xFF7BC8C8)
            decade < 2000 -> Color(0xFFE8A88A)
            else -> AppAccentSecondary
        }
    }
    return when (station.stationType) {
        JukeboxStationType.MOOD -> Color(0xFF88D4A8)
        JukeboxStationType.GENRE -> Color(0xFFD48CA8)
        else -> AppAccent
    }
}

private fun stationKindLabel(station: JukeboxStation): String = when (station.stationType) {
    JukeboxStationType.ERA -> station.decadeStart?.let { "${it}s era mix" } ?: "Era mix"
    JukeboxStationType.GENRE -> "Genre mix"
    JukeboxStationType.MOOD -> "Mood mix"
    JukeboxStationType.PRESET -> "Curated mix"
    else -> "Endless mix"
}
