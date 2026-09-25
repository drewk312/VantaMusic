package com.audiophile.musicplayer.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.audiophile.musicplayer.data.metadata.EnhancedMetadata
import com.audiophile.musicplayer.playback.NowPlayingState

@Composable
fun SourceDetailsSheet(nowPlayingState: NowPlayingState, enhancedMetadata: EnhancedMetadata?, onDismiss: () -> Unit) {
    VantaBottomSheet(visible = true, onDismiss = onDismiss) {
        VantaSheetHeader("About this song", onDismiss = onDismiss)
        Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(nowPlayingState.title?.takeIf { it.isNotBlank() } ?: "Your next discovery",
                color = AppText, fontSize = 26.sp, lineHeight = 32.sp, fontWeight = FontWeight.Medium)
            nowPlayingState.artist?.takeIf { it.isNotBlank() }?.let {
                Text(it, color = AppAccent, fontSize = 16.sp, lineHeight = 23.sp)
            }
            Spacer(Modifier.height(12.dp))
            val details = buildList {
                nowPlayingState.album?.takeIf { it.isNotBlank() }?.let { add("Album" to it) }
                enhancedMetadata?.releaseYear?.let { add("Released" to it.toString()) }
                if (nowPlayingState.durationMs > 0) add("Length" to formatDuration(nowPlayingState.durationMs))
                enhancedMetadata?.genres?.filter { it.isNotBlank() }?.takeIf { it.isNotEmpty() }
                    ?.let { add("Genre" to it.joinToString(" · ")) }
            }
            details.forEach { (label, value) ->
                Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                    Text(label, color = AppTextSecondary, fontSize = 12.sp)
                    Text(value, color = AppText, fontSize = 15.sp, lineHeight = 22.sp, modifier = Modifier.padding(top = 4.dp))
                }
            }
            if (details.isEmpty()) Text("More recording details will appear when they’re available.",
                color = AppTextSecondary, fontSize = 14.sp, lineHeight = 21.sp)
            if (nowPlayingState.isFavorite) Text("In your Liked Songs", color = AppAccent, fontSize = 13.sp,
                modifier = Modifier.padding(top = 12.dp))
            nowPlayingState.errorMessage?.takeIf { it.isNotBlank() }?.let {
                Column(Modifier.fillMaxWidth().background(AppSurfaceRaised, RoundedCornerShape(16.dp)).padding(16.dp)) {
                    Text("Playback needs attention", color = AppText, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                    Text(it, color = AppTextSecondary, fontSize = 13.sp, lineHeight = 20.sp, modifier = Modifier.padding(top = 6.dp))
                }
            }
        }
    }
}
