package com.audiophile.musicplayer.tv.jukebox

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.audiophile.musicplayer.data.local.entities.UnifiedTrackWithSources
import com.audiophile.musicplayer.tv.TvFocusable
import com.audiophile.musicplayer.tv.TvMetrics
import com.audiophile.musicplayer.tv.TvTheme
import com.audiophile.musicplayer.ui.theme.VantaSans

/**
 * Slotted Magazine Disc Carousel:
 * Displays upcoming queued records in an illuminated vintage Jukebox rack
 * with mechanical title strips and slotted vinyl discs.
 */
@Composable
fun TvDiscCarousel(
    queue: List<UnifiedTrackWithSources>,
    currentQueueIndex: Int,
    selectedIndex: Int,
    onSelectIndex: (Int) -> Unit,
    metrics: TvMetrics,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxHeight()
            .clip(RoundedCornerShape(20.dp))
            .background(TvTheme.BgElevated.copy(alpha = 0.85f))
            .border(1.dp, TvTheme.Hairline, RoundedCornerShape(20.dp))
            .padding(14.dp)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Header Bar: Vintage Jukebox Title Strip Display
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(
                        Brush.horizontalGradient(
                            listOf(Color(0xFF261E14), Color(0xFF18130C), Color(0xFF261E14))
                        )
                    )
                    .border(1.dp, TvTheme.HiResGold.copy(alpha = 0.4f), RoundedCornerShape(10.dp))
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(TvTheme.HiResGoldBright)
                    )
                    Text(
                        text = "MAGAZINE CAROUSEL",
                        color = TvTheme.HiResGoldBright,
                        fontSize = 11.sp,
                        fontFamily = VantaSans,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.5.sp
                    )
                }
                Text(
                    text = "${queue.size} SEL",
                    color = TvTheme.TextSecondary,
                    fontSize = 10.sp,
                    fontFamily = VantaSans,
                    fontWeight = FontWeight.Medium
                )
            }

            Spacer(Modifier.height(10.dp))

            // Carousel Magazine Slots
            if (queue.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "Magazine Empty · Queuing Radio...",
                        color = TvTheme.TextSecondary,
                        fontSize = metrics.caption
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    itemsIndexed(queue, key = { idx, item -> "${item.track.trackId}_$idx" }) { index, item ->
                        val isPlaying = index == currentQueueIndex
                        val slotCode = when {
                            index < 26 -> "A-${String.format("%02d", index + 1)}"
                            index < 52 -> "B-${String.format("%02d", index - 25)}"
                            else -> "C-${String.format("%02d", index - 51)}"
                        }

                        TvCarouselSlot(
                            slotCode = slotCode,
                            title = item.track.title,
                            artist = item.track.artist,
                            artworkUrl = item.track.coverArtUrl,
                            isPlaying = isPlaying,
                            onClick = { onSelectIndex(index) }
                        )
                    }
                }
            }
        }
    }
}

/**
 * Individual Jukebox Magazine Slot:
 * Features an edge-peeking vinyl record and printed mechanical title strip.
 */
@Composable
private fun TvCarouselSlot(
    slotCode: String,
    title: String,
    artist: String,
    artworkUrl: String?,
    isPlaying: Boolean,
    onClick: () -> Unit
) {
    TvFocusable(
        onClick = onClick,
        cornerRadius = 10,
        focusScale = 1.02f,
        modifier = Modifier.fillMaxWidth()
    ) { focused ->
        val stripBg by animateColorAsState(
            targetValue = when {
                focused -> Color(0xFF332B22)
                isPlaying -> Color(0xFF261D13)
                else -> Color(0xFF171513)
            },
            animationSpec = tween(150),
            label = "slotStripBg"
        )

        val discPeekOffset by animateDpAsState(
            targetValue = if (focused || isPlaying) 16.dp else 0.dp,
            animationSpec = tween(200),
            label = "discPeek"
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(stripBg)
                .border(
                    width = if (focused) 1.5.dp else if (isPlaying) 1.dp else 0.5.dp,
                    color = when {
                        focused -> TvTheme.FocusRing
                        isPlaying -> TvTheme.HiResGold
                        else -> TvTheme.Hairline
                    },
                    shape = RoundedCornerShape(10.dp)
                )
                .padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Mechanical Slot Index Tag (e.g. "A-01")
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(if (isPlaying) TvTheme.HiResGoldDark else Color(0xFF0F0E0C))
                    .border(
                        1.dp,
                        if (isPlaying) TvTheme.HiResGold else Color(0xFF3D372E),
                        RoundedCornerShape(6.dp)
                    )
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = slotCode,
                    color = if (isPlaying) TvTheme.HiResGoldBright else TvTheme.TextSecondary,
                    fontSize = 11.sp,
                    fontFamily = VantaSans,
                    fontWeight = FontWeight.Bold
                )
            }

            // Printed Title Strip Content
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = title,
                    color = if (isPlaying) TvTheme.HiResGoldBright else TvTheme.Text,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = artist,
                    color = TvTheme.TextSecondary,
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // Peeking Vinyl Disc in Rack Slot
            Box(
                modifier = Modifier
                    .width(42.dp)
                    .height(34.dp)
                    .graphicsLayer {
                        translationX = discPeekOffset.toPx()
                    },
                contentAlignment = Alignment.CenterEnd
            ) {
                // Sliced Vinyl Disc Silhouette
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .shadow(4.dp, CircleShape)
                        .clip(CircleShape)
                        .background(
                            Brush.radialGradient(
                                listOf(Color(0xFF26221E), Color(0xFF141210), Color(0xFF080706))
                            )
                        )
                        .border(1.dp, Color(0xFF3D372E), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    // Micro label
                    if (!artworkUrl.isNullOrBlank()) {
                        AsyncImage(
                            model = artworkUrl,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(14.dp)
                                .clip(CircleShape)
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .size(14.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF594524))
                        )
                    }
                }
            }
        }
    }
}
