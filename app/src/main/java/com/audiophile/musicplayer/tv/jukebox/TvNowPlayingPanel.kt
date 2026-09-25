package com.audiophile.musicplayer.tv.jukebox

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.audiophile.musicplayer.data.display.VantaQualityInfo
import com.audiophile.musicplayer.tv.TvTheme
import com.audiophile.musicplayer.ui.FormatBadgeSize
import com.audiophile.musicplayer.ui.VantaFormatBadgeRow
import com.audiophile.musicplayer.ui.theme.VantaSans

/**
 * Smoked-Glass Now Playing Panel — the engraved plaque embedded in the right
 * cabinet wing. Milk-glass backlight with the artwork softly glowing inside,
 * plus a brushed brass ID plate carrying the title, artist, album, and the
 * active format / bit-depth engraving underneath.
 */
@Composable
fun TvNowPlayingPanel(
    title: String?,
    artist: String?,
    album: String?,
    artworkUrl: String?,
    qualityInfo: VantaQualityInfo?,
    positionMs: Long,
    durationMs: Long,
    isPlaying: Boolean,
    modifier: Modifier = Modifier,
    acousticness: Double? = null
) {
    val progress = if (durationMs > 0L) (positionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f) else 0f

    Box(
        modifier = modifier
            .padding(horizontal = 18.dp, vertical = 26.dp)
            .clip(RoundedCornerShape(22.dp))
            .background(
                Brush.verticalGradient(
                    listOf(Color(0xFF3A3226), Color(0xFF241E15), Color(0xFF14100B))
                )
            )
            .padding(1.dp),
        contentAlignment = Alignment.Center
    ) {
        // Smoked glass face
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(21.dp))
                .background(Color(0xFF0F0D0B).copy(alpha = 0.72f))
                .padding(horizontal = 18.dp, vertical = 16.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Artwork glowing through the glass
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color(0xFF0B0908)),
                    contentAlignment = Alignment.Center
                ) {
                    if (!artworkUrl.isNullOrBlank()) {
                        AsyncImage(
                            model = artworkUrl,
                            contentDescription = "Album artwork",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(
                                    Brush.radialGradient(
                                        listOf(Color(0xFF2B2114), Color(0xFF0B0908))
                                    )
                                )
                        )
                    }

                    // Glass specular veil over the artwork
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.linearGradient(
                                    listOf(
                                        Color.White.copy(alpha = 0.12f),
                                        Color.Transparent,
                                        Color.Black.copy(alpha = 0.25f)
                                    )
                                )
                            )
                    )
                }

                // Engraved ID plate
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFF070605).copy(alpha = 0.55f))
                        .borderlessBrassPlate()
                        .padding(horizontal = 12.dp, vertical = 10.dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        Text(
                            text = title ?: "Selecting Disc…",
                            color = TvTheme.HiResGoldBright,
                            fontSize = 15.sp,
                            fontFamily = VantaSans,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            letterSpacing = 0.3.sp
                        )
                        Text(
                            text = artist.orEmpty().ifBlank { "—" },
                            color = Color(0xFFD8CFC0),
                            fontSize = 12.sp,
                            fontFamily = VantaSans,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = album.orEmpty().ifBlank { "—" },
                            color = Color(0xFFC4B8A4),
                            fontSize = 11.sp,
                            fontFamily = VantaSans,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                // Rich Format Badge Row (Dolby Atmos, TrueHD, Hi-Res, FLAC, Pure Acoustic)
                VantaFormatBadgeRow(
                    qualityInfo = qualityInfo,
                    acousticness = acousticness,
                    size = FormatBadgeSize.Compact,
                    modifier = Modifier.padding(horizontal = 2.dp)
                )

                // Progress: engraved timecode + warm amber dial line
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = formatClock(positionMs),
                        color = TvTheme.HiResGoldBright,
                        fontSize = 11.sp,
                        fontFamily = VantaSans,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = formatClock(durationMs),
                        color = Color(0xFFC4B8A4),
                        fontSize = 11.sp,
                        fontFamily = VantaSans,
                        fontWeight = FontWeight.Medium
                    )
                }
                Canvas(modifier = Modifier.fillMaxWidth().height(4.dp)) {
                    drawLine(
                        color = TvTheme.ProgressTrack.copy(alpha = 0.8f),
                        start = Offset(0f, size.height / 2f),
                        end = Offset(size.width, size.height / 2f),
                        strokeWidth = 4.dp.toPx(),
                        cap = StrokeCap.Round
                    )
                    drawLine(
                        color = TvTheme.HiResGoldBright,
                        start = Offset(0f, size.height / 2f),
                        end = Offset(size.width * progress.coerceIn(0f, 1f), size.height / 2f),
                        strokeWidth = 4.dp.toPx(),
                        cap = StrokeCap.Round
                    )
                    // Marker nub at the playhead
                    drawCircle(
                        color = if (isPlaying) TvTheme.HiResGoldBright else TvTheme.TextSecondary,
                        radius = 5.dp.toPx(),
                        center = Offset(size.width * progress.coerceIn(0f, 1f), size.height / 2f)
                    )
                }

                // Status dot + "VANTA · Audiophile Changer"
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(top = 2.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(7.dp)
                            .clip(CircleShape)
                            .background(
                                if (isPlaying) Color(0xFFE2C45A)
                                else Color(0xFF6E675C)
                            )
                    )
                    Text(
                        text = if (isPlaying) "PLAYING" else "STANDBY",
                        color = if (isPlaying) Color(0xFFE3CF96) else Color(0xFFC4B8A4),
                        fontSize = 9.sp,
                        fontFamily = VantaSans,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.2.sp,
                        maxLines = 1
                    )
                }
            }
        }
    }
}

private fun Modifier.borderlessBrassPlate(): Modifier =
    drawBehind {
        drawLine(
            color = TvTheme.HiResGold.copy(alpha = 0.18f),
            start = Offset.Zero,
            end = Offset(size.width, 0f),
            strokeWidth = 1.dp.toPx()
        )
    }

private fun qualityLine(info: VantaQualityInfo?): String {
    if (info == null) return "LOSSLESS ENGINE"
    val codec = info.format?.takeIf { it.isNotBlank() }?.uppercase()
        ?: if ((info.sampleRateHz ?: 0) >= 96000) "HI-RES" else "LOSSLESS"
    val bits = info.bitDepth?.takeIf { it > 0 }?.let { "$it-bit" } ?: "16-bit"
    val rate = info.sampleRateHz?.takeIf { it > 0 }?.let {
        if (it % 1000 == 0) "${it / 1000} kHz" else String.format("%.1f kHz", it / 1000.0)
    } ?: "44.1 kHz"
    return "$codec • $bits • $rate"
}

private fun formatClock(ms: Long): String {
    if (ms <= 0L) return "0:00"
    val totalSec = ms / 1000
    return String.format("%d:%02d", totalSec / 60, totalSec % 60)
}