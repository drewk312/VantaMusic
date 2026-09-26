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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.audiophile.musicplayer.data.display.DisplayMetadataCleaner
import com.audiophile.musicplayer.data.importer.ImportMatchStatus
import com.audiophile.musicplayer.data.importer.PlayabilityStatus
import com.audiophile.musicplayer.data.local.entities.ImportedTrackEntity

@Composable
fun ImportReviewScreen(
    tracks: List<ImportedTrackEntity>,
    statusMessage: String?,
    onBack: () -> Unit,
    onRemoveTrack: (Long) -> Unit,
    onSaveMatched: () -> Unit,
    onSaveAllMetadata: () -> Unit,
    onResolveAgain: () -> Unit
) {
    val matchedCount = tracks.count { it.matchStatus == ImportMatchStatus.MATCHED }
    val parsedMetadataCount = tracks.count { !it.parsedTitle.isNullOrBlank() }
    val playableCount = tracks.count { it.playabilityStatus == PlayabilityStatus.PLAYABLE || it.playabilityStatus == PlayabilityStatus.PLAYABLE_ENHANCED }
    val metadataOnlyCount = tracks.count { it.playabilityStatus == PlayabilityStatus.METADATA_ONLY || it.playabilityStatus == PlayabilityStatus.METADATA_ONLY_ENHANCED }
    val needsReviewCount = tracks.count { it.matchStatus == ImportMatchStatus.NEEDS_REVIEW }
    val notFoundCount = tracks.count { it.matchStatus == ImportMatchStatus.NOT_FOUND }

    val matchedTracks = tracks.filter { it.matchStatus == ImportMatchStatus.MATCHED }
    val needsReviewTracks = tracks.filter { it.matchStatus == ImportMatchStatus.NEEDS_REVIEW }
    val notFoundTracks = tracks.filter { it.matchStatus == ImportMatchStatus.NOT_FOUND }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(AppBackgroundTop, AppBackgroundBottom)))
            .padding(horizontal = 24.dp)
            .padding(top = appTopContentPadding(extra = 16.dp))
            .padding(bottom = appBottomWindowInsets()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = AppAccent, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Back", color = AppAccent)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("Review Import", color = AppText, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                    Text("${tracks.size} tracks parsed", color = AppTextSecondary, fontSize = 13.sp)
                }
            }
        }

        if (!statusMessage.isNullOrBlank()) {
            item {
                Text(statusMessage, color = AppWarning, fontSize = 13.sp)
            }
        }

        // Top Action Card - Resolving/import buttons at top so user never has to scroll through thousands of songs
        item {
            VantaCard {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(
                        onClick = onSaveMatched,
                        enabled = matchedCount > 0 || parsedMetadataCount > 0,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = AppAccent)
                    ) {
                        Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(
                            if (tracks.isEmpty()) "Import Tracks"
                            else "Resolve & Save All (${tracks.size} tracks)"
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (parsedMetadataCount > 0 && matchedCount != tracks.size) {
                            Button(
                                onClick = onSaveAllMetadata,
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("Save All Metadata", maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                        TextButton(
                            onClick = onResolveAgain,
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Resolve Again")
                        }
                    }
                }
            }
        }

        item {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ReviewMetric("Matched", (matchedCount).toString(), Modifier.weight(1f))
                ReviewMetric("Ready to Stream", (playableCount + metadataOnlyCount).toString(), Modifier.weight(1f))
                ReviewMetric("Review", needsReviewCount.toString(), Modifier.weight(1f))
                ReviewMetric("Not Found", notFoundCount.toString(), Modifier.weight(1f))
            }
        }

        if (tracks.isEmpty()) {
            item {
                Box(modifier = Modifier.fillMaxWidth().glassSurface().padding(16.dp)) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("No rows to review", color = AppText, fontWeight = FontWeight.SemiBold)
                        Text("Go back and paste a playlist or song list to start matching.", color = AppTextSecondary, fontSize = 14.sp)
                    }
                }
            }
        }

        if (matchedTracks.isNotEmpty()) {
            item {
                Text("Matched & Ready (${matchedTracks.size})", color = AppText, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
            }
            items(matchedTracks, key = { "m_${it.id}" }) { track ->
                VantaCard {
                    ImportTrackRow(track = track, onRemoveTrack = onRemoveTrack)
                }
            }
        }

        if (needsReviewTracks.isNotEmpty()) {
            item {
                Text("Needs Review (${needsReviewTracks.size})", color = AppWarning, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
            }
            items(needsReviewTracks, key = { "nr_${it.id}" }) { track ->
                VantaCard {
                    ImportTrackRow(track = track, onRemoveTrack = onRemoveTrack)
                }
            }
        }

        if (notFoundTracks.isNotEmpty()) {
            item {
                Text("Not Found (${notFoundTracks.size})", color = AppTextMuted, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
            }
            items(notFoundTracks, key = { "nf_${it.id}" }) { track ->
                VantaCard {
                    ImportTrackRow(track = track, onRemoveTrack = onRemoveTrack)
                }
            }
        }
    }
}

@Composable
private fun ReviewMetric(label: String, value: String, modifier: Modifier = Modifier) {
    Box(modifier = modifier.glassSurface(shape = RoundedCornerShape(14.dp)).padding(10.dp)) {
        Column {
            Text(label, color = AppTextSecondary, fontSize = 11.sp, maxLines = 1)
            Text(value, color = AppText, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun ImportTrackRow(
    track: ImportedTrackEntity,
    onRemoveTrack: (Long) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                track.rawText,
                color = AppText,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                fontWeight = FontWeight.Medium
            )
            Text(
                listOfNotNull(
                    track.parsedTitle?.let { DisplayMetadataCleaner.cleanDisplayName(it).ifBlank { it } },
                    track.parsedArtist?.let { DisplayMetadataCleaner.cleanDisplayName(it).ifBlank { it } }
                ).joinToString(" - ").ifBlank { "Unparsed" },
                color = AppTextSecondary,
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                "${friendlyStatus(track)} · ${(track.confidenceScore * 100).toInt()}% confidence" +
                    (track.friendlySourceLabel?.let { " · $it" } ?: ""),
                color = AppAccent,
                fontSize = 12.sp
            )
            track.matchReason?.let { reason ->
                Text(reason, color = AppTextMuted, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        TextButton(onClick = { onRemoveTrack(track.id) }) {
            Text("Remove", color = AppTextMuted, fontSize = 12.sp)
        }
    }
}

private fun friendlyStatus(track: ImportedTrackEntity): String = when (track.playabilityStatus) {
    PlayabilityStatus.PLAYABLE, PlayabilityStatus.PLAYABLE_ENHANCED -> "Playable"
    PlayabilityStatus.METADATA_ONLY, PlayabilityStatus.METADATA_ONLY_ENHANCED -> "Matched"
    PlayabilityStatus.NEEDS_REVIEW -> "Needs Review"
    PlayabilityStatus.NOT_FOUND -> "Not Found"
    PlayabilityStatus.ERROR -> "Needs Review"
}
