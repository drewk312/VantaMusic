package com.audiophile.musicplayer.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp)
            .padding(bottom = appBottomWindowInsets()),
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Column {
                Text("Review Import", color = AppText, fontSize = 28.sp, fontWeight = FontWeight.Bold)
                Text("${tracks.size} parsed row(s)", color = AppTextSecondary, fontSize = 14.sp)
            }
            TextButton(onClick = onBack) { Text("Back") }
        }

        if (!statusMessage.isNullOrBlank()) {
            Text(statusMessage, color = AppWarning, fontSize = 13.sp)
        }

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ReviewMetric("Playable", playableCount.toString(), Modifier.weight(1f))
            ReviewMetric("Matched", metadataOnlyCount.toString(), Modifier.weight(1f))
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ReviewMetric("Needs Review", needsReviewCount.toString(), Modifier.weight(1f))
            ReviewMetric("Not Found", notFoundCount.toString(), Modifier.weight(1f))
        }

        Button(
            onClick = onSaveMatched,
            enabled = matchedCount > 0,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Resolve & Save Matched")
        }
        Button(
            onClick = onSaveAllMetadata,
            enabled = parsedMetadataCount > 0,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Save All Metadata")
        }
        TextButton(onClick = onResolveAgain, modifier = Modifier.fillMaxWidth()) {
            Text("Resolve Again")
        }

        if (tracks.isEmpty()) {
            Box(modifier = Modifier.fillMaxWidth().glassSurface().padding(16.dp)) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("No rows to review", color = AppText, fontWeight = FontWeight.SemiBold)
                    Text("Go back and paste a playlist or song list to start matching.", color = AppTextSecondary, fontSize = 14.sp)
                }
            }
        }

        ImportGroup("Playable", tracks.filter { it.playabilityStatus == PlayabilityStatus.PLAYABLE || it.playabilityStatus == PlayabilityStatus.PLAYABLE_ENHANCED }, onRemoveTrack)
        ImportGroup("Matched", tracks.filter { it.playabilityStatus == PlayabilityStatus.METADATA_ONLY || it.playabilityStatus == PlayabilityStatus.METADATA_ONLY_ENHANCED }, onRemoveTrack)
        ImportGroup("Needs Review", tracks.filter { it.matchStatus == ImportMatchStatus.NEEDS_REVIEW }, onRemoveTrack)
        ImportGroup("Not Found", tracks.filter { it.matchStatus == ImportMatchStatus.NOT_FOUND }, onRemoveTrack)
    }
}

@Composable
private fun ReviewMetric(label: String, value: String, modifier: Modifier = Modifier) {
    Box(modifier = modifier.glassSurface(shape = RoundedCornerShape(18.dp)).padding(14.dp)) {
        Column {
            Text(label, color = AppTextSecondary, fontSize = 12.sp)
            Text(value, color = AppText, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun ImportGroup(
    title: String,
    tracks: List<ImportedTrackEntity>,
    onRemoveTrack: (Long) -> Unit
) {
    Box(
        modifier = Modifier.fillMaxWidth().glassSurface()
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("$title (${tracks.size})", color = AppText, fontWeight = FontWeight.SemiBold, fontSize = 18.sp)
            if (tracks.isEmpty()) {
                Text("No tracks in this group", color = AppTextMuted, fontSize = 13.sp)
            } else {
                tracks.forEachIndexed { index, track ->
                    ImportTrackRow(track = track, onRemoveTrack = onRemoveTrack)
                }
            }
        }
    }
}

@Composable
private fun ImportTrackRow(
    track: ImportedTrackEntity,
    onRemoveTrack: (Long) -> Unit
) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(modifier = Modifier.weight(1f)) {
            Text(track.rawText, color = AppText, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                listOfNotNull(track.parsedTitle, track.parsedArtist).joinToString(" - ").ifBlank { "Unparsed" },
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
            if (track.playabilityStatus == PlayabilityStatus.METADATA_ONLY) {
                Text(
                    "Track was matched, but no playable source is connected yet.",
                    color = AppWarning,
                    fontSize = 12.sp
                )
            }
            track.matchReason?.let { reason ->
                Text(reason, color = AppTextMuted, fontSize = 11.sp)
            }
        }
        TextButton(onClick = { onRemoveTrack(track.id) }) {
            Text("Remove", color = AppTextSecondary)
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
