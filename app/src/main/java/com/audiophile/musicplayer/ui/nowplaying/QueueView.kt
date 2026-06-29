package com.audiophile.musicplayer.ui.nowplaying

import android.util.Log
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
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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

@Composable
fun QueueView(
    queueSnapshot: QueueSnapshot,
    onMoveQueueItem: (Int) -> Unit,
    onRemoveQueueItem: (Int) -> Unit,
    onOpenTrackSheet: ((UnifiedTrackWithSources) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState()

    Column(modifier = modifier.padding(horizontal = 24.dp)) {
        Text(
            text = "Up Next",
            color = AppText, fontSize = 20.sp, fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 12.dp)
        )
        if (queueSnapshot.upNextQueue.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                VantaEmptyState(title = "Queue Empty", description = "Add tracks to your queue to keep the music going.", icon = Icons.Filled.QueueMusic)
            }
        } else {
            LazyColumn(
                state = listState,
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 16.dp)
            ) {
                itemsIndexed(queueSnapshot.upNextQueue, key = { _, track -> track.track.trackId }) { index, track ->
                    QueueTrackRow(index, track, onMoveQueueItem, onRemoveQueueItem, onOpenTrackSheet)
                }
            }
        }
    }
}

@Composable
private fun QueueTrackRow(
    index: Int,
    track: UnifiedTrackWithSources,
    onMoveQueueItem: (Int) -> Unit,
    onRemoveQueueItem: (Int) -> Unit,
    onOpenTrackSheet: ((UnifiedTrackWithSources) -> Unit)? = null
) {
    val display = remember(track.track) { TrackDisplayResolver.resolve(track.track) }
    val qualityLabel = remember(track.sources) {
        VantaQualityInfo.fromTrackSource(
            source = track.sources.maxByOrNull { it.bitrate },
            status = track.sourceValidityStatus()
        )?.bestQualityLabel()
    }
    Row(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(AppSurface)
            .border(0.5.dp, AppOutline, RoundedCornerShape(12.dp)).padding(12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier.size(48.dp).clip(RoundedCornerShape(8.dp))
                .border(0.5.dp, AppOutline, RoundedCornerShape(8.dp))
        ) {
            NetworkArtwork(artworkUrl = display.artworkUrl, seed = display.title, modifier = Modifier.fillMaxSize())
        }
        Spacer(Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(display.title, color = AppText, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (display.explicit == true) VantaExplicitBadge()
                VantaQualityBadge(qualityLabel)
            }
            Text(display.artist, color = AppTextSecondary, fontSize = 14.sp)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("\u2191", color = AppAccent, fontSize = 20.sp, modifier = Modifier.clickable { onMoveQueueItem(index) })
            Text("\u2715", color = AppTextMuted, fontSize = 20.sp, modifier = Modifier.clickable { onRemoveQueueItem(index) })
            if (onOpenTrackSheet != null) {
                Icon(
                    Icons.Filled.MoreVert, contentDescription = "More options", tint = AppTextSecondary,
                    modifier = Modifier.size(32.dp).clip(CircleShape).clickable { onOpenTrackSheet(track) }.padding(4.dp)
                )
            }
        }
    }
}
