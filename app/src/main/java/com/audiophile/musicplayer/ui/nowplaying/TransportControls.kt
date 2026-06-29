package com.audiophile.musicplayer.ui.nowplaying

import android.util.Log
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.audiophile.musicplayer.audio.visualizer.VantaAudioFrame
import com.audiophile.musicplayer.playback.NowPlayingState
import com.audiophile.musicplayer.ui.AppAccent
import com.audiophile.musicplayer.ui.AppBackgroundBottom
import com.audiophile.musicplayer.ui.AppSurfaceRaised
import com.audiophile.musicplayer.ui.AppText
import com.audiophile.musicplayer.ui.AppTextMuted
import com.audiophile.musicplayer.ui.AppTextSecondary
import com.audiophile.musicplayer.ui.PremiumSkipButton
import com.audiophile.musicplayer.ui.PremiumTransportButton
import com.audiophile.musicplayer.ui.VantaType
import com.audiophile.musicplayer.ui.visualizer.VantaBeatProgressBar

@Composable
fun CleanProgressSection(
    state: NowPlayingState,
    accentColor: Color,
    onSeekTo: (Long) -> Unit,
    audioFrame: VantaAudioFrame? = null,
    auraEnabled: Boolean = false,
    reducedMotion: Boolean = true
) {
    VantaBeatProgressBar(
        positionMs = state.positionMs,
        durationMs = state.durationMs,
        isPlaying = state.isPlaying,
        audioFrame = audioFrame,
        auraEnabled = auraEnabled,
        reducedMotion = reducedMotion,
        onSeek = onSeekTo
    )
}

@Composable
fun LuxuryControlsRow(
    isPlaying: Boolean,
    canPlayPrevious: Boolean,
    canPlayNext: Boolean,
    isLoading: Boolean = false,
    onPrevious: () -> Unit,
    onTogglePlayPause: () -> Unit,
    onNext: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        PremiumSkipButton(isNext = false, enabled = canPlayPrevious, onClick = { Log.d("VANTA_UI_ACTION", "control='nowplaying_previous' result='tap'"); onPrevious() })
        PremiumTransportButton(isPrimary = true, isPlaying = isPlaying, isLoading = isLoading, onClick = { Log.d("VANTA_UI_ACTION", "control='nowplaying_play_pause' result='tap'"); onTogglePlayPause() }, contentDescription = if (isPlaying) "Pause" else "Play")
        PremiumSkipButton(isNext = true, enabled = canPlayNext, onClick = { Log.d("VANTA_UI_ACTION", "control='nowplaying_next' result='tap'"); onNext() })
    }
}

@Composable
fun BeatPlayButton(isPlaying: Boolean, enabled: Boolean, onClick: () -> Unit) {
    val warmGold = Color(0xFFFFE8C8)
    Box(
        modifier = Modifier
            .size(80.dp)
            .shadow(12.dp, CircleShape, ambientColor = Color.Black.copy(alpha = 0.4f), spotColor = Color.Black.copy(alpha = 0.4f))
            .clip(CircleShape).background(warmGold).clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
            contentDescription = if (isPlaying) "Pause" else "Play",
            tint = Color(0xFF111111),
            modifier = Modifier.size(34.dp)
        )
    }
}

@Composable
fun NowPlayingStatusSignal(message: String?, accentColor: Color, modifier: Modifier = Modifier) {
    val cleanMessage = message?.trim()?.takeIf { it.isNotBlank() }
    val isNowPlaying = cleanMessage?.startsWith("Playing ", ignoreCase = true) == true
    val body = if (isNowPlaying) cleanMessage?.removePrefix("Playing ")?.removePrefix("playing ")?.trim().orEmpty() else cleanMessage.orEmpty()
    AnimatedVisibility(
        visible = cleanMessage != null,
        enter = fadeIn(animationSpec = tween(180)) + slideInVertically(animationSpec = tween(220), initialOffsetY = { it / 3 }),
        exit = fadeOut(animationSpec = tween(180)) + slideOutVertically(animationSpec = tween(220), targetOffsetY = { it / 3 }),
        modifier = modifier
    ) {
        Row(
            modifier = Modifier.widthIn(min = 180.dp, max = 360.dp).clip(RoundedCornerShape(18.dp))
                .background(Brush.horizontalGradient(listOf(accentColor.copy(alpha = 0.18f), AppSurfaceRaised.copy(alpha = 0.92f), AppBackgroundBottom.copy(alpha = 0.95f))))
                .border(0.5.dp, accentColor.copy(alpha = 0.18f), RoundedCornerShape(18.dp)).padding(horizontal = 14.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            SignalDot(accentColor = accentColor, modifier = Modifier.size(18.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(if (isNowPlaying) "NOW PLAYING" else "VANTA", color = accentColor.copy(alpha = 0.78f), fontSize = 9.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                Text(body, color = AppText, fontSize = 14.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
private fun SignalDot(accentColor: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        drawCircle(accentColor.copy(alpha = 0.26f), radius = size.minDimension / 2f)
        drawCircle(accentColor.copy(alpha = 0.82f), radius = size.minDimension / 4f)
    }
}

@Composable
private fun LuxuryProgressSection(state: NowPlayingState, onSeekTo: (Long) -> Unit) {
    val progress = if (state.durationMs > 0L) (state.positionMs.toFloat() / state.durationMs.toFloat()).coerceIn(0f, 1f) else 0f
    Column(modifier = Modifier.fillMaxWidth()) {
        Slider(
            value = progress,
            onValueChange = { fraction -> if (state.durationMs > 0L) onSeekTo((state.durationMs * fraction.coerceIn(0f, 1f)).toLong()) },
            colors = SliderDefaults.colors(thumbColor = Color.White, activeTrackColor = Color.White.copy(alpha = 0.9f), inactiveTrackColor = Color.White.copy(alpha = 0.3f)),
            modifier = Modifier.fillMaxWidth()
        )
        Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(formatDuration(state.positionMs), style = VantaType.tabularNumbers, color = Color.White.copy(alpha = 0.55f), fontSize = 12.sp)
            Text(formatDuration(state.durationMs), style = VantaType.tabularNumbers, color = Color.White.copy(alpha = 0.45f), fontSize = 12.sp)
        }
    }
}
