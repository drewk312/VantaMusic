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
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
    bottomNavVisible: Boolean = true,
    selectedDiscoveryMode: com.audiophile.musicplayer.radio.RadioDiscoveryMode = com.audiophile.musicplayer.radio.RadioDiscoveryMode.HYBRID_MIX,
    onDiscoveryModeSelected: (com.audiophile.musicplayer.radio.RadioDiscoveryMode) -> Unit = {}
) {
    val scrollState = rememberScrollState()
    var expandedOptions by remember { mutableStateOf(false) }
    val bottomPadding = appOverlayBottomPadding(
        miniPlayerVisible = miniPlayerVisible,
        bottomNavVisible = bottomNavVisible
    )
    val topPadding = WindowInsets.safeDrawing.only(WindowInsetsSides.Top).asPaddingValues().calculateTopPadding()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(AppBackground)
            .verticalScroll(scrollState)
            .padding(top = topPadding, bottom = bottomPadding)
    ) {
        // Inline header: back | emoji | title+kind | more
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
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
            Text(
                text = station.emoji,
                fontSize = 20.sp,
                modifier = Modifier.padding(top = 2.dp)
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = station.name,
                    style = VantaType.songTitle,
                    color = AppText,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = stationKindLabel(station),
                    style = VantaType.caption,
                    color = AppTextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Box {
                Icon(
                    imageVector = Icons.Filled.MoreVert,
                    contentDescription = "Options",
                    tint = AppTextSecondary,
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .clickable { expandedOptions = true }
                        .padding(8.dp)
                )
                DropdownMenu(
                    expanded = expandedOptions,
                    onDismissRequest = { expandedOptions = false }
                ) {
                    DropdownMenuItem(
                        text = { Text("Play station") },
                        leadingIcon = { Icon(Icons.Filled.PlayArrow, contentDescription = null) },
                        onClick = {
                            expandedOptions = false
                            onStartStation()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Shuffle station") },
                        leadingIcon = { Icon(Icons.Filled.Shuffle, contentDescription = null) },
                        onClick = {
                            expandedOptions = false
                            onShuffleStation()
                        }
                    )
                }
            }
        }

        // Description + chips
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp),
            horizontalAlignment = Alignment.Start
        ) {
            if (station.description.isNotBlank()) {
                Text(
                    text = station.description,
                    style = VantaType.subtitle,
                    color = AppTextSecondary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(8.dp))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                eraLabel(station)?.let { label -> StationChip(label) }
            }
            if (!statusMessage.isNullOrBlank() && !statusMessage.startsWith("Playing")) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = statusMessage,
                    style = VantaType.caption,
                    color = AppWarning,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        Spacer(Modifier.height(16.dp))

        // Compact action row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 48.dp)
                    .clip(RoundedCornerShape(28.dp))
                    .background(AppAccent)
                    .clickable(enabled = !isStarting, onClick = onStartStation)
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (isStarting) {
                    CircularProgressIndicator(
                        color = AppBackground,
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp
                    )
                } else {
                    Icon(
                        imageVector = Icons.Filled.PlayArrow,
                        contentDescription = null,
                        tint = AppBackground,
                        modifier = Modifier.size(22.dp)
                    )
                }
                Spacer(Modifier.width(8.dp))
                Text(
                    text = if (isStarting) "Starting..." else "Play Station",
                    style = VantaType.songTitle.copy(color = AppBackground)
                )
            }
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .border(0.5.dp, Color.White.copy(alpha = 0.12f), CircleShape)
                    .background(AppSurfaceRaised)
                    .clickable(enabled = !isStarting, onClick = onShuffleStation),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.Shuffle,
                    contentDescription = "Shuffle Station",
                    tint = AppAccent,
                    modifier = Modifier.size(22.dp)
                )
            }
        }

        Spacer(Modifier.height(14.dp))

        // Station Discovery Mode Selector (Favorites / Hybrid / Deep Discovery)
        StationTuningSelector(
            selectedMode = selectedDiscoveryMode,
            onModeSelected = onDiscoveryModeSelected,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
        )

        Spacer(Modifier.height(14.dp))

        // Tracks / empty / loading
        Column(modifier = Modifier.fillMaxWidth()) {
            when {
                isLoadingPreview -> {
                    TrackListShimmer(
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                        count = 5
                    )
                }
                previewTracks.isEmpty() -> {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 16.dp)
                            .clip(RoundedCornerShape(18.dp))
                            .background(AppSurfaceRaised)
                            .border(0.5.dp, AppOutline, RoundedCornerShape(18.dp))
                            .padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "Your station is taking shape.",
                            style = VantaType.songTitle,
                            color = AppText,
                            textAlign = TextAlign.Center
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = "Tap Play to curate the first tracks, or check back in a moment.",
                            style = VantaType.subtitle,
                            color = AppTextSecondary,
                            textAlign = TextAlign.Center
                        )
                        Spacer(Modifier.height(14.dp))
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(28.dp))
                                .background(AppAccent)
                                .clickable(enabled = !isStarting, onClick = onStartStation)
                                .padding(horizontal = 20.dp, vertical = 10.dp)
                        ) {
                            Text(
                                text = "Start Station",
                                style = VantaType.songTitle.copy(color = AppBackground)
                            )
                        }
                    }
                }
                else -> {
                    Text(
                        text = "${previewTracks.size} tracks in this mix",
                        style = VantaType.caption,
                        color = AppAccent.copy(alpha = 0.5f),
                        letterSpacing = 1.5.sp,
                        modifier = Modifier.padding(horizontal = 20.dp)
                    )
                    Spacer(Modifier.height(12.dp))
                    previewTracks.forEach { track ->
                        StationTrackRow(
                            track = track,
                            onPlay = { onPlayPreviewTrack(track) }
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun StationChip(label: String) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(AppAccent.copy(alpha = 0.12f))
            .border(0.5.dp, AppAccent.copy(alpha = 0.22f), RoundedCornerShape(20.dp))
            .padding(horizontal = 10.dp, vertical = 4.dp)
    ) {
        Text(
            text = label.uppercase(),
            style = VantaType.caption.copy(
                color = AppAccent,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 1.sp
            )
        )
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
            .padding(horizontal = 20.dp, vertical = 5.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(AppSurfaceRaised)
            .clickable(onClick = onPlay)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        NetworkArtwork(
            artworkUrl = track.track.coverArtUrl,
            seed = display.title,
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(10.dp))
        )

        Spacer(Modifier.width(12.dp))

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

        Spacer(Modifier.width(12.dp))

        Icon(
            imageVector = Icons.Filled.PlayArrow,
            contentDescription = "Play",
            tint = AppAccent,
            modifier = Modifier.size(24.dp)
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
            .height(110.dp)
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
                imageVector = Icons.Filled.PlayArrow,
                contentDescription = "Start station",
                tint = AppAccent,
                modifier = Modifier.size(22.dp)
            )
        }

        Column(
            modifier = Modifier.align(Alignment.BottomStart),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(station.emoji, fontSize = 20.sp)
            Text(
                station.name,
                color = AppText,
                fontSize = 17.sp,
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
    return if (end != null && end != start) "$startLabel-${end}s" else startLabel
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
