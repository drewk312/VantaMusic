package com.audiophile.musicplayer.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.audiophile.musicplayer.data.metadata.EnhancedMetadata
import com.audiophile.musicplayer.playback.NowPlayingState

@Composable
fun SourceDetailsSheet(
    nowPlayingState: NowPlayingState,
    enhancedMetadata: EnhancedMetadata?,
    onDismiss: () -> Unit
) {
    VantaBottomSheet(visible = true, onDismiss = onDismiss) {
        Text(
            "Source Details",
            color = AppText,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 24.dp)
        )
        Spacer(Modifier.height(4.dp))
        Text(
            "Diagnostic information about the current track source",
            color = AppTextSecondary,
            fontSize = 13.sp,
            modifier = Modifier.padding(horizontal = 24.dp)
        )
        Spacer(Modifier.height(20.dp))
        VantaSheetDivider()
        Spacer(Modifier.height(12.dp))

        val details = buildList {
            add("Track ID" to (nowPlayingState.trackId?.toString() ?: "N/A"))
            add("Canonical ID" to (nowPlayingState.canonicalTrackId ?: "N/A"))
            add("ISRC" to (nowPlayingState.isrc ?: "N/A"))
            add("Duration" to formatDuration(nowPlayingState.durationMs))
            add("Playing" to if (nowPlayingState.isPlaying) "Yes" else "No")
            add("Queue Position" to "${nowPlayingState.queuePosition + 1}/${nowPlayingState.queueSize}")
            add("Favorite" to if (nowPlayingState.isFavorite) "Yes" else "No")
            add("Playback Error" to (nowPlayingState.errorMessage ?: "None"))
            if (enhancedMetadata != null) {
                add("Provider Confidence" to "${(enhancedMetadata.sourceConfidence * 100).toInt()}%")
                add("Release Year" to (enhancedMetadata.releaseYear?.toString() ?: "N/A"))
                add("Genre" to (enhancedMetadata.genres.joinToString(", ").ifEmpty { "N/A" }))
            }
        }

        details.forEach { (label, value) ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(label, color = AppTextSecondary, fontSize = 13.sp)
                Text(
                    value,
                    color = if (value == "None" || value == "N/A" || value.startsWith("Not")) AppTextMuted else AppText,
                    fontSize = 13.sp,
                    fontFamily = FontFamily.Monospace,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                    modifier = Modifier.widthIn(max = 200.dp)
                )
            }
        }
    }
}
