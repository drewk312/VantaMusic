package com.audiophile.musicplayer.ui.nowplaying

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Shuffle
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
import com.audiophile.musicplayer.data.display.VantaQualityInfo
import com.audiophile.musicplayer.data.local.entities.UnifiedTrackWithSources
import com.audiophile.musicplayer.data.source.sourceValidityStatus
import com.audiophile.musicplayer.playback.QueueSnapshot
import com.audiophile.musicplayer.ui.AppAccent
import com.audiophile.musicplayer.ui.AppOutline
import com.audiophile.musicplayer.ui.AppSurface
import com.audiophile.musicplayer.ui.AppText
import com.audiophile.musicplayer.ui.AppTextMuted
import com.audiophile.musicplayer.ui.AppTextSecondary
import com.audiophile.musicplayer.ui.NetworkArtwork
import com.audiophile.musicplayer.ui.VantaEmptyState
import com.audiophile.musicplayer.ui.VantaExplicitBadge
import com.audiophile.musicplayer.ui.VantaQualityBadge

private val NowPlayingAccent = Color(0xFF4CC9F0)

@Composable
fun QueueView(
    queueSnapshot: QueueSnapshot,
    onMoveQueueItem: (Int) -> Unit,
    onRemoveQueueItem: (Int) -> Unit,
    modifier: Modifier = Modifier,
    onOpenTrackSheet: ((UnifiedTrackWithSources) -> Unit)? = null,
    onShuffleQueue: (() -> Unit)? = null
) {
    val listState = rememberLazyListState()
    val upNext = queueSnapshot.upNextQueue

    Column(modifier = modifier.padding(horizontal = 20.dp)) {
        queueSnapshot.currentTrack?.let { current ->
            QueueNowPlayingCard(current)
            Spacer(Modifier.height(18.dp))
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Up Next",
                color = AppText, fontSize = 20.sp, fontWeight = FontWeight.Bold
            )
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (upNext.isNotEmpty()) {
                    Text(
                        text = "${upNext.size} TRACKS",
                        color = AppTextMuted, fontSize = 11.sp, fontWeight = FontWeight.SemiBold,
                        letterSpacing = 0.5.sp
                    )
                }
                if (onShuffleQueue != null && upNext.isNotEmpty()) {
                    Icon(
                        imageVector = Icons.Filled.Shuffle,
                        contentDescription = "Shuffle Queue",
                        tint = AppAccent,
                        modifier = Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .clickable { onShuffleQueue() }
                            .padding(4.dp)
                    )
                }
            }
        }
        if (upNext.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                VantaEmptyState(
                    title = "Queue Empty",
                    description = "Add tracks to your queue to keep the music going.",
                    icon = Icons.AutoMirrored.Filled.QueueMusic
                )
            }
        } else {
            LazyColumn(
                state = listState,
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 16.dp)
            ) {
                itemsIndexed(upNext, key = { _, track -> track.track.trackId }) { index, track ->
                    QueueTrackRow(index, track, isFirst = index == 0, onMoveQueueItem, onRemoveQueueItem, onOpenTrackSheet)
                }
            }
        }
    }
}

/** Pinned now-playing card sits above the queue list. */
@Composable
private fun QueueNowPlayingCard(track: UnifiedTrackWithSources) {
    val display = remember(track.track) { TrackDisplayResolver.resolve(track.track) }
    val qualityLabel = remember(track.sources) {
        VantaQualityInfo.fromTrackSource(
            source = track.sources.maxByOrNull { it.bitrate },
            status = track.sourceValidityStatus()
        )?.compactQualityLabel()
    }
    Column(
        modifier = Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(
                Brush.verticalGradient(
                    listOf(Color(0xFF152331), Color(0xFF0C1620))
                )
            )
            .border(0.5.dp, NowPlayingAccent.copy(alpha = 0.45f), RoundedCornerShape(20.dp))
            .padding(14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Box(
                modifier = Modifier.size(8.dp).clip(CircleShape).background(NowPlayingAccent)
            )
            Text("NOW PLAYING", color = NowPlayingAccent, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier.size(96.dp).clip(RoundedCornerShape(14.dp))
                    .border(0.5.dp, AppOutline, RoundedCornerShape(14.dp))
            ) {
                NetworkArtwork(artworkUrl = display.artworkUrl, seed = display.title, modifier = Modifier.fillMaxSize())
            }
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    display.title,
                    color = Color.White,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(display.artist, color = AppTextSecondary, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                display.album?.let { album ->
                    if (album.isNotBlank()) {
                        Text(album, color = AppTextMuted, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
                if (qualityLabel != null || display.explicit == true) {
                    Spacer(Modifier.height(6.dp))
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        VantaQualityBadge(qualityLabel)
                        if (display.explicit == true) VantaExplicitBadge()
                    }
                }
            }
        }
    }
}

@Composable
private fun QueueTrackRow(
    index: Int,
    track: UnifiedTrackWithSources,
    isFirst: Boolean,
    onMoveQueueItem: (Int) -> Unit,
    onRemoveQueueItem: (Int) -> Unit,
    onOpenTrackSheet: ((UnifiedTrackWithSources) -> Unit)? = null
) {
    val display = remember(track.track) { TrackDisplayResolver.resolve(track.track) }
    val qualityLabel = remember(track.sources) {
        VantaQualityInfo.fromTrackSource(
            source = track.sources.maxByOrNull { it.bitrate },
            status = track.sourceValidityStatus()
        )?.compactQualityLabel()
    }
    Row(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(AppSurface)
            .border(0.5.dp, AppOutline, RoundedCornerShape(14.dp)).padding(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier.size(58.dp).clip(RoundedCornerShape(10.dp))
                .border(0.5.dp, AppOutline, RoundedCornerShape(10.dp))
        ) {
            NetworkArtwork(artworkUrl = display.artworkUrl, seed = display.title, modifier = Modifier.fillMaxSize())
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    display.title,
                    color = AppText,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (display.explicit == true) VantaExplicitBadge()
            }
            Text(display.artist, color = AppTextSecondary, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            qualityLabel?.let {
                Spacer(Modifier.height(4.dp))
                VantaQualityBadge(it)
            }
        }
        Spacer(Modifier.width(6.dp))
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                contentDescription = "Move up",
                tint = if (isFirst) AppTextMuted else AppAccent,
                modifier = Modifier.size(30.dp).clip(CircleShape)
                    .clickable(enabled = !isFirst) { onMoveQueueItem(index) }.padding(3.dp)
            )
            Icon(
                Icons.Filled.Close,
                contentDescription = "Remove from queue",
                tint = AppTextMuted,
                modifier = Modifier.size(30.dp).clip(CircleShape)
                    .clickable { onRemoveQueueItem(index) }.padding(3.dp)
            )
            if (onOpenTrackSheet != null) {
                Icon(
                    Icons.Filled.MoreVert,
                    contentDescription = "More options",
                    tint = AppTextSecondary,
                    modifier = Modifier.size(30.dp).clip(CircleShape)
                        .clickable { onOpenTrackSheet(track) }.padding(6.dp)
                )
            }
        }
    }
}