package com.audiophile.musicplayer.ui.nowplaying

import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.audiophile.musicplayer.playback.NowPlayingState
import com.audiophile.musicplayer.audio.visualizer.VantaAudioFrame
import kotlinx.coroutines.delay

@Composable
fun StoryMomentChip(
    nowPlayingState: NowPlayingState,
    audioFrame: VantaAudioFrame?,
    visible: Boolean,
    modifier: Modifier = Modifier
) {
    if (!visible) return

    val story = rememberStoryMoment(nowPlayingState, audioFrame)
    var showChip by remember { mutableStateOf(false) }

    LaunchedEffect(story) {
        if (story != null) {
            showChip = true
            delay(8000)
            showChip = false
        }
    }

    if (story != null && showChip) {
        Box(
            modifier = modifier
                .alpha(if (showChip) 1f else 0f)
                .background(
                    Color.White.copy(alpha = 0.08f),
                    RoundedCornerShape(20.dp)
                )
                .padding(horizontal = 14.dp, vertical = 7.dp)
        ) {
            Text(
                text = story,
                color = Color.White.copy(alpha = 0.85f),
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun rememberStoryMoment(
    state: NowPlayingState,
    audioFrame: VantaAudioFrame?
): String? {
    return remember(state.trackId, state.title, audioFrame?.bassEnergy) {
        val signals = mutableListOf<String>()

        if (state.preferredExternalTrackId != null || state.preferredProviderId != null) {
            if (state.queueSize > 0) signals.add("From your library")
        }

        if (state.durationMs > 0 && state.durationMs < 180000) {
            val positionRatio = if (state.durationMs > 0) state.positionMs.toFloat() / state.durationMs else 0f
            if (positionRatio in 0.45f..0.65f) signals.add("Big chorus incoming")
            if (positionRatio in 0.05f..0.15f) signals.add("Quiet start")
            if (positionRatio in 0.75f..0.90f) signals.add("Final stretch")
        }

        val energy = audioFrame?.let { it.bassEnergy * 0.6f + it.midEnergy * 0.4f } ?: 0.5f
        val prevEnergy = energy - 0.1f
        if (energy > 0.6f && prevEnergy <= 0.4f) signals.add("Energy rising")

        if (energy < 0.25f && state.durationMs > 60000) signals.add("Quiet moment")

        signals.firstOrNull()
    }
}
