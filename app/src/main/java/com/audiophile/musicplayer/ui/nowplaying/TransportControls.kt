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
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Shuffle
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
import androidx.compose.ui.graphics.graphicsLayer
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

@Composable
fun CleanProgressSection(
    state: NowPlayingState,
    accentColor: Color,
    onSeekTo: (Long) -> Unit,
    audioFrame: VantaAudioFrame? = null,
    auraEnabled: Boolean = false,
    reducedMotion: Boolean = true
) {
    val seekable = state.durationMs > 0L
    val currentProgress = if (seekable) {
        (state.positionMs.toFloat() / state.durationMs.toFloat()).coerceIn(0f, 1f)
    } else 0f
    var scrubbing by remember(state.trackId) { mutableStateOf(false) }
    var scrubProgress by remember(state.trackId) { mutableFloatStateOf(currentProgress) }
    LaunchedEffect(currentProgress, scrubbing) {
        if (!scrubbing) scrubProgress = currentProgress
    }
    val visibleProgress = if (scrubbing) scrubProgress else currentProgress
    val visiblePosition = if (scrubbing && seekable) {
        (state.durationMs * visibleProgress).toLong()
    } else state.positionMs

    Column(modifier = Modifier.fillMaxWidth()) {
        Slider(
            value = visibleProgress,
            onValueChange = { value ->
                if (seekable) {
                    scrubbing = true
                    scrubProgress = value.coerceIn(0f, 1f)
                }
            },
            onValueChangeFinished = {
                if (seekable) onSeekTo((state.durationMs * scrubProgress).toLong())
                scrubbing = false
            },
            enabled = seekable,
            colors = SliderDefaults.colors(
                thumbColor = Color.White,
                activeTrackColor = accentColor,
                inactiveTrackColor = Color.White.copy(alpha = 0.16f),
                disabledThumbColor = AppTextMuted,
                disabledActiveTrackColor = Color.White.copy(alpha = 0.10f),
                disabledInactiveTrackColor = Color.White.copy(alpha = 0.08f)
            ),
            modifier = Modifier.fillMaxWidth().height(48.dp).semantics { contentDescription = "Seek position" }
        )
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 2.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(formatDuration(visiblePosition), style = VantaType.tabularNumbers, color = AppTextSecondary, fontSize = 11.sp)
            Text(formatDuration(state.durationMs), style = VantaType.tabularNumbers, color = AppTextMuted, fontSize = 11.sp)
        }
    }
}

@Composable
fun LuxuryControlsRow(
    isPlaying: Boolean,
    canPlayPrevious: Boolean,
    canPlayNext: Boolean,
    isLoading: Boolean = false,
    isFavorite: Boolean = false,
    onToggleFavorite: (() -> Unit)? = null,
    isShuffleEnabled: Boolean = false,
    onToggleShuffle: (() -> Unit)? = null,
    onPrevious: () -> Unit,
    onTogglePlayPause: () -> Unit,
    onNext: () -> Unit
) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val compact = maxWidth < 340.dp
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically) {
            if (!compact) {
                if (onToggleFavorite != null) VantaLoveButton(isFavorite, onToggleFavorite)
                else Spacer(Modifier.size(48.dp))
            }
            PremiumSkipButton(isNext = false, enabled = canPlayPrevious, onClick = onPrevious,
                modifier = Modifier.size(48.dp))
            PremiumTransportButton(isPrimary = true, isPlaying = isPlaying, isLoading = isLoading,
                onClick = onTogglePlayPause, contentDescription = if (isPlaying) "Pause" else "Play")
            PremiumSkipButton(isNext = true, enabled = canPlayNext, onClick = onNext,
                modifier = Modifier.size(48.dp))
            if (!compact) {
                if (onToggleShuffle != null) {
                    VantaShuffleButton(isShuffleEnabled = isShuffleEnabled, onClick = onToggleShuffle)
                } else {
                    Spacer(Modifier.size(48.dp))
                }
            }
        }
    }
}

@Composable
fun VantaShuffleButton(
    isShuffleEnabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val activeColor = AppAccent
    val inactiveColor = AppTextSecondary.copy(alpha = 0.65f)
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(48.dp)
            .clip(CircleShape)
            .clickable(onClick = onClick)
    ) {
        if (isShuffleEnabled) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(activeColor.copy(alpha = 0.15f))
            )
        }
        Icon(
            imageVector = Icons.Filled.Shuffle,
            contentDescription = if (isShuffleEnabled) "Shuffle On" else "Shuffle Off",
            tint = if (isShuffleEnabled) activeColor else inactiveColor,
            modifier = Modifier.size(24.dp)
        )
    }
}

/**
 * The VANTA love control: heart pops with a spring, a burst ring and particles
 * fire outward, and a glow halo lives under the heart while favorited.
 */
@Composable
fun VantaLoveButton(isFavorite: Boolean, onClick: () -> Unit) {
    val loveColor = Color(0xFFFF2E63)
    val heartScale = remember { Animatable(1f) }
    val burstProgress = remember { Animatable(0f) }
    LaunchedEffect(isFavorite) {
        if (isFavorite) {
            burstProgress.snapTo(0f)
            heartScale.snapTo(1f)
            heartScale.animateTo(1.55f, spring(dampingRatio = 0.34f, stiffness = 520f))
            heartScale.animateTo(1f, spring(dampingRatio = 0.55f, stiffness = 360f))
            burstProgress.animateTo(1f, tween(560))
            burstProgress.snapTo(0f)
        } else {
            heartScale.snapTo(1.35f)
            heartScale.animateTo(1f, spring(dampingRatio = 0.5f, stiffness = 420f))
            burstProgress.snapTo(0f)
        }
    }
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(48.dp)
            .clip(CircleShape)
            .clickable(onClick = onClick)
    ) {
        // Glow halo under the heart while favorited.
        if (isFavorite) {
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .clip(CircleShape)
                    .background(Brush.radialGradient(listOf(loveColor.copy(alpha = 0.34f), loveColor.copy(alpha = 0f))))
            )
        }
        // Burst ring + particles fire outward while favoriting.
        if (isFavorite && burstProgress.value > 0f) {
            Canvas(modifier = Modifier.size(96.dp)) {
                val progress = burstProgress.value
                val radius = 18.dp.toPx() + (30.dp.toPx() * progress)
                drawCircle(
                    color = Color.White.copy(alpha = (1f - progress) * 0.55f),
                    radius = radius,
                    style = androidx.compose.ui.graphics.drawscope.Stroke(width = (3.dp.toPx() * (1f - progress)).coerceAtLeast(0.5f))
                )
                val count = 8
                for (i in 0 until count) {
                    val angle = (Math.PI * 2.0 * i / count).toFloat()
                    val particleRadius = radius + 6.dp.toPx() * progress
                    drawCircle(
                        color = if (i % 2 == 0) loveColor else Color.White,
                        radius = (4.dp.toPx() * (1f - progress)).coerceAtLeast(0.5f),
                        center = Offset(
                            x = size.width / 2f + particleRadius * kotlin.math.cos(angle),
                            y = size.height / 2f + particleRadius * kotlin.math.sin(angle)
                        ),
                        alpha = (1f - progress).coerceIn(0f, 1f)
                    )
                }
            }
        }
        Icon(
            imageVector = if (isFavorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
            contentDescription = if (isFavorite) "Remove from favorites" else "Add to favorites",
            tint = if (isFavorite) loveColor else AppTextSecondary,
            modifier = Modifier
                .size(26.dp)
                .graphicsLayer { scaleX = heartScale.value; scaleY = heartScale.value }
        )
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
    val body = if (isNowPlaying) cleanMessage.removePrefix("Playing ").removePrefix("playing ").trim() else cleanMessage.orEmpty()
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
