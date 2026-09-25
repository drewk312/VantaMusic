package com.audiophile.musicplayer.tv.jukebox

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.audiophile.musicplayer.data.local.entities.UnifiedTrackWithSources
import com.audiophile.musicplayer.tv.TvTheme
import com.audiophile.musicplayer.ui.theme.VantaSans

/**
 * Physical 45-rpm Record Magazine — a walnut rack of vertical slots. Each slot
 * holds a vinyl 45 whose edge peeks out of the rack. Amber-lit divider rails
 * pick out the focused selection; a brass index tag shows the A/B/C position.
 * D-pad focus slides the chosen disc further out of its slot (mechanically).
 */
@Composable
fun TvRecordMagazine(
    queue: List<UnifiedTrackWithSources>,
    currentQueueIndex: Int,
    selectedIndex: Int,
    onSelectIndex: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxHeight()
            .background(Color(0xFF0B0907))
            .padding(vertical = 10.dp)
    ) {
        // Walnut cabinet backboard with vertical grain suggestion
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawRect(
                brush = Brush.verticalGradient(
                    listOf(Color(0xFF241B12), Color(0xFF170F08), Color(0xFF0F0A05))
                )
            )
            for (x in 0..24) {
                drawLine(
                    color = Color(0xFF3A2A18).copy(alpha = (x % 7) * 0.02f + 0.05f),
                    start = Offset(size.width * x / 25f, 0f),
                    end = Offset(size.width * (x + 0.4f) / 25f, size.height),
                    strokeWidth = 1.dp.toPx()
                )
            }
            // Left trim strip — an illuminated amber rail lamp, not a dark line.
            // Wide tungsten bloom + hot core, brightest at the top where the
            // fixture sits, exactly like a lit magazine lamp on a real changer.
            val railX = 4.dp.toPx()
            drawRect(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF8A6418).copy(alpha = 0.5f),
                        Color(0xFF5A452A).copy(alpha = 0.28f),
                        Color(0xFF5A452A).copy(alpha = 0.4f)
                    )
                ),
                topLeft = Offset(0f, 0f),
                size = Size(railX, size.height)
            )
            drawRect(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFFFFC66B).copy(alpha = 0.35f),
                        Color.Transparent,
                        Color(0xFFF5A623).copy(alpha = 0.18f)
                    )
                ),
                topLeft = Offset(0f, 0f),
                size = Size(railX * 3f, size.height)
            )
            drawRect(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFFFFE9B0).copy(alpha = 0.9f),
                        Color(0xFFB8861F).copy(alpha = 0.55f),
                        Color(0xFFFFD98A).copy(alpha = 0.6f)
                    )
                ),
                topLeft = Offset(0f, 0f),
                size = Size(2.dp.toPx(), size.height)
            )
        }

        Column(modifier = Modifier.fillMaxSize()) {
            // Brass index panel header (part of the machine, not a card header)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF0C0A07))
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Canvas(modifier = Modifier.size(10.dp)) {
                        drawCircle(
                            brush = Brush.radialGradient(
                                listOf(Color(0xFFFFE2A0), Color(0xFFC9A227), Color(0xFF6B4E12))
                            ),
                            radius = size.width / 2f,
                            center = Offset(size.width / 2f, size.height / 2f)
                        )
                    }
                    Text(
                        text = "RECORD MAGAZINE",
                        color = TvTheme.HiResGoldBright,
                        fontSize = 11.sp,
                        fontFamily = VantaSans,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 2.sp
                    )
                }
                Text(
                    text = "${queue.size} SEL",
                    color = TvTheme.TextMuted,
                    fontSize = 10.sp,
                    fontFamily = VantaSans,
                    fontWeight = FontWeight.Medium,
                    letterSpacing = 1.sp
                )
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(2.dp)
                    .background(TvTheme.HiResGold.copy(alpha = 0.35f))
            )

            if (queue.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        "Magazine empty · queuing radio…",
                        color = TvTheme.TextMuted,
                        fontSize = 12.sp,
                        fontFamily = VantaSans
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(horizontal = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    itemsIndexed(queue, key = { idx, item -> "${item.track.trackId}_$idx" }) { index, item ->
                        val isPlaying = index == currentQueueIndex
                        val slotCode = when {
                            index < 26 -> "A-${String.format("%02d", index + 1)}"
                            index < 52 -> "B-${String.format("%02d", index - 25)}"
                            else -> "C-${String.format("%02d", index - 51)}"
                        }
                        TvMagazineSlot(
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

@Composable
private fun TvMagazineSlot(
    slotCode: String,
    title: String,
    artist: String,
    artworkUrl: String?,
    isPlaying: Boolean,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val focused by interactionSource.collectIsFocusedAsState()

    val discPeek by animateDpAsState(
        targetValue = if (focused || isPlaying) 18.dp else 2.dp,
        animationSpec = tween(220),
        label = "magazineDiscPeek"
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(46.dp)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
    ) {
        // Slot body — stamped aluminum channel with a warm rail lamp
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(start = 6.dp, end = 0.dp)
                .background(
                    brush = if (focused) {
                        Brush.horizontalGradient(
                            listOf(Color(0xFF3A2C18), Color(0xFF241A0E))
                        )
                    } else {
                        Brush.horizontalGradient(
                            listOf(if (isPlaying) Color(0xFF2E2212) else Color(0xFF18130D), Color(0xFF100C07))
                        )
                    },
                    shape = RoundedCornerShape(topStart = 10.dp, bottomStart = 10.dp)
                )
                .border(
                    width = if (focused) 1.5.dp else 1.dp,
                    color = when {
                        focused -> TvTheme.HiResGoldBright
                        isPlaying -> TvTheme.HiResGold.copy(alpha = 0.7f)
                        else -> Color(0xFF2E2418)
                    },
                    shape = RoundedCornerShape(topStart = 10.dp, bottomStart = 10.dp)
                )
        ) {
            Row(
                modifier = Modifier.fillMaxSize().padding(horizontal = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Brass index tag
                Box(
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(if (isPlaying) Color(0xFF8A6E2B) else Color(0xFF201811))
                        .border(
                            1.dp,
                            if (isPlaying) TvTheme.HiResGoldBright else Color(0xFF4A3A24),
                            CircleShape
                        )
                        .size(30.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = slotCode.removePrefix("A-").takeIf { it.length == 2 } ?: slotCode,
                        color = if (isPlaying) Color(0xFF14100B) else TvTheme.HiResGoldBright.copy(alpha = 0.75f),
                        fontSize = 11.sp,
                        fontFamily = VantaSans,
                        fontWeight = FontWeight.Bold
                    )
                }

                // Printed title strip
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.Center) {
                    Text(
                        text = title,
                        color = if (isPlaying || focused) TvTheme.HiResGoldBright else TvTheme.Text,
                        fontSize = 12.sp,
                        fontFamily = VantaSans,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = artist,
                        color = TvTheme.TextMuted,
                        fontSize = 10.sp,
                        fontFamily = VantaSans,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }

        // The vinyl 45 peeking out of the rack — a real disc edge with label art
        Box(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .width(40.dp)
                .height(36.dp)
                .graphicsLayer {
                    translationX = discPeek.toPx()
                },
            contentAlignment = Alignment.CenterEnd
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .graphicsLayer {
                        // Slot-cut: only the right crescent shows
                        clip = false
                    }
                    .background(
                        Brush.radialGradient(
                            listOf(Color(0xFF2B251D), Color(0xFF14110D), Color(0xFF080605))
                        )
                    )
                    .border(1.dp, Color(0xFF3D372E), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                // Groove rings
                Canvas(modifier = Modifier.size(30.dp)) {
                    for (r in 6..14 step 3) {
                        drawCircle(
                            color = Color(0xFF4A4136).copy(alpha = 0.4f),
                            radius = r.dp.toPx(),
                            center = Offset(size.width / 2f, size.height / 2f),
                            style = Stroke(width = 0.8.dp.toPx())
                        )
                    }
                }
                if (!artworkUrl.isNullOrBlank()) {
                    AsyncImage(
                        model = artworkUrl,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(12.dp)
                            .clip(CircleShape)
                    )
                }
            }
        }
    }
}