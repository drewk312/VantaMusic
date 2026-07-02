package com.audiophile.musicplayer.ui.visualizer

import android.util.Log
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.audiophile.musicplayer.audio.visualizer.VantaAudioFrame
import com.audiophile.musicplayer.ui.AppTextMuted
import com.audiophile.musicplayer.ui.AppTextSecondary
import com.audiophile.musicplayer.ui.VantaType
import com.audiophile.musicplayer.ui.nowplaying.formatDuration
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin
import kotlin.math.cos

private val WarmGold = Color(0xFFFFE8C8)
private val WarmGoldBright = Color(0xFFFFF0D0)
private val WarmAmber = Color(0xFFE8A040)
private val WarmCreamActive = Color(0xFFFFF4E0)
private val DimInactive = Color(0xFFFFF4E0).copy(alpha = 0.12f)
private val GlowColor = WarmGold.copy(alpha = 0.35f)
private val AmbientGlow = WarmAmber.copy(alpha = 0.15f)
private const val ANIMATION_PERIOD_MS = 2400f

private const val TAG = "VANTA_BEAT_BAR"

@Composable
fun VantaBeatProgressBar(
    positionMs: Long,
    durationMs: Long,
    isPlaying: Boolean,
    audioFrame: VantaAudioFrame?,
    auraEnabled: Boolean,
    reducedMotion: Boolean,
    onSeek: (Long) -> Unit,
    modifier: Modifier = Modifier,
    mini: Boolean = false
) {
    val seekable = durationMs > 0L

    val bucketCount = if (mini) 24 else 48
    val touchTargetHeight = 44.dp

    var isDragging by remember { mutableStateOf(false) }
    var dragFraction by remember { mutableFloatStateOf(0f) }

    val actualFraction = if (seekable && !mini) {
        (positionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
    } else {
        0f
    }
    val displayFraction = if (isDragging) dragFraction else actualFraction

    val animatedProgress by animateFloatAsState(
        targetValue = displayFraction,
        animationSpec = tween(durationMillis = if (isDragging) 0 else 200),
        label = "beatProgress"
    )

    val displayMs = if (isDragging && seekable) (durationMs * displayFraction).toLong() else positionMs

    var beatPhaseMs by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(Unit) {
        while (true) {
            kotlinx.coroutines.delay(16L)
            beatPhaseMs = (beatPhaseMs + 16f) % ANIMATION_PERIOD_MS
        }
    }
    val normalizedPhase = beatPhaseMs / ANIMATION_PERIOD_MS

    val audioReactive = audioFrame != null && auraEnabled && !reducedMotion
    val hasLiveAudio = audioFrame?.isLiveAudio == true

    Box(modifier = modifier.fillMaxWidth()) {
        // Visual + drag layer: the invisible Slider handles drag scrubbing.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(touchTargetHeight)
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                drawBeatProgress(
                    progress = animatedProgress,
                    isPlaying = isPlaying,
                    audioFrame = audioFrame,
                    reducedMotion = reducedMotion,
                    bucketCount = bucketCount,
                    isDragging = isDragging,
                    dragFraction = dragFraction,
                    normalizedPhase = normalizedPhase,
                    audioReactive = audioReactive,
                    hasLiveAudio = hasLiveAudio
                )
            }
            if (seekable && !mini) {
                Slider(
                    value = displayFraction,
                    onValueChange = {
                        isDragging = true
                        dragFraction = it
                    },
                    onValueChangeFinished = {
                        isDragging = false
                        val targetMs = seekTargetMs(durationMs, dragFraction)
                        onSeek(targetMs)
                        Log.d(TAG, "seek positionMs=$targetMs durationMs=$durationMs result=seek_to")
                    },
                    valueRange = 0f..1f,
                    colors = SliderDefaults.colors(
                        thumbColor = Color.Transparent,
                        activeTrackColor = Color.Transparent,
                        inactiveTrackColor = Color.Transparent,
                        disabledThumbColor = Color.Transparent,
                        disabledActiveTrackColor = Color.Transparent,
                        disabledInactiveTrackColor = Color.Transparent
                    ),
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
        // Transparent tap overlay on top so taps always seek even if the Slider does not
        // consume them. Drag gestures are ignored here and fall through to the Slider.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(touchTargetHeight)
                .pointerInput(seekable, durationMs) {
                    if (seekable) {
                        detectTapGestures { offset ->
                            val fraction = (offset.x / size.width).coerceIn(0f, 1f)
                            val targetMs = seekTargetMs(durationMs, fraction)
                            onSeek(targetMs)
                            Log.d(TAG, "tap_seek positionMs=$targetMs durationMs=$durationMs fraction=$fraction")
                        }
                    }
                }
        )

        if (!mini) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 24.dp)
            ) {
                Text(
                    formatDuration(displayMs),
                    style = VantaType.tabularNumbers,
                    color = AppTextSecondary,
                    fontSize = 11.sp,
                    modifier = Modifier.align(Alignment.CenterStart)
                )
                Text(
                    formatDuration(durationMs),
                    style = VantaType.tabularNumbers,
                    color = AppTextMuted,
                    fontSize = 11.sp,
                    modifier = Modifier.align(Alignment.CenterEnd)
                )
            }
        }
    }
}

private fun seekTargetMs(durationMs: Long, fraction: Float): Long {
    val safeDuration = durationMs.coerceAtLeast(0L)
    val maxSeek = safeDuration.coerceAtLeast(0L)
    return (safeDuration * fraction.coerceIn(0f, 1f)).toLong().coerceIn(0L, maxSeek)
}

private fun DrawScope.drawBeatProgress(
    progress: Float,
    isPlaying: Boolean,
    audioFrame: VantaAudioFrame?,
    reducedMotion: Boolean,
    bucketCount: Int,
    isDragging: Boolean,
    dragFraction: Float,
    normalizedPhase: Float,
    audioReactive: Boolean,
    hasLiveAudio: Boolean
) {
    val w = size.width
    val h = size.height
    if (w <= 0f || h <= 0f || bucketCount <= 0) return

    val barHeight = if (bucketCount <= 24) h * 0.22f else h * 0.40f
    val lineY = h * 0.5f
    val lineThickness = (barHeight * 0.20f).coerceAtLeast(if (bucketCount <= 24) 2f else 2.5f)

    val rms = audioFrame?.rms ?: 0f
    val bass = audioFrame?.bassEnergy ?: 0f
    val mid = audioFrame?.midEnergy ?: 0f
    val treble = audioFrame?.trebleEnergy ?: 0f

    val activeWidth = w * progress.coerceIn(0f, 1f)

    val gradientTrack = Brush.horizontalGradient(
        colors = listOf(
            DimInactive,
            DimInactive.copy(alpha = 0.08f)
        ),
        startX = 0f,
        endX = w
    )
    drawRoundRect(
        brush = gradientTrack,
        topLeft = Offset(0f, lineY - lineThickness / 2f),
        size = Size(w, lineThickness),
        cornerRadius = CornerRadius(lineThickness / 2f)
    )

    if (activeWidth > 0f) {
        val activeGradient = Brush.horizontalGradient(
            colors = listOf(
                WarmAmber,
                WarmGold,
                WarmGoldBright
            ),
            startX = 0f,
            endX = activeWidth
        )
        drawRoundRect(
            brush = activeGradient,
            topLeft = Offset(0f, lineY - lineThickness / 2f),
            size = Size(activeWidth, lineThickness),
            cornerRadius = CornerRadius(lineThickness / 2f)
        )

        val glowIntensity = if (audioReactive && isPlaying) 1f + bass * 0.8f else 1f
        val glowRadius = lineThickness * 2.2f * glowIntensity
        drawCircle(
            color = GlowColor.copy(alpha = (0.3f + rms * 0.4f).coerceIn(0.1f, 0.7f)),
            radius = glowRadius,
            center = Offset(activeWidth, lineY)
        )
        drawCircle(
            color = AmbientGlow.copy(alpha = (0.1f + bass * 0.3f).coerceIn(0.05f, 0.4f)),
            radius = glowRadius * 2.5f,
            center = Offset(activeWidth, lineY)
        )
    }

    val peakSpacing = w / (bucketCount + 1)
    val maxPeakHeight = if (bucketCount <= 24) h * 0.50f else h * 0.70f
    val basePeakHeight = if (bucketCount <= 24) h * 0.06f else h * 0.10f

    val time = normalizedPhase * (ANIMATION_PERIOD_MS / 1000f)

    for (i in 0 until bucketCount) {
        val x = peakSpacing * (i + 1)
        val effectiveProgress = if (isDragging) dragFraction else progress
        val isActive = x <= w * effectiveProgress

        val fft = audioFrame?.fftBuckets.orEmpty()
        val bucketEnergy = if (audioReactive && isPlaying && fft.isNotEmpty()) {
                val energyIdx = (i.toFloat() / bucketCount * fft.size)
                    .toInt()
                    .coerceIn(0, (fft.size - 1).coerceAtLeast(0))
                fft[energyIdx]
        } else 0f

        val bassBoost = if (i < bucketCount / 3) bass * 0.6f else 0f
        val midBoost = mid * 0.3f * (1f - abs(i - bucketCount / 2).toFloat() / (bucketCount / 2).toFloat().coerceAtLeast(1f))

        val breath = if (!audioReactive || !isPlaying) {
            val phase = (time * 0.6f + i * 0.15f) % 1f
            sin(phase * PI.toFloat() * 2f) * 0.10f + 0.10f
        } else 0f

        val sparkle = if (audioReactive && isPlaying) treble * 0.08f * sin(time * 3f + i * 1.7f) else 0f

        val peakHeight = (
            basePeakHeight +
            bucketEnergy * maxPeakHeight * 0.35f +
            bassBoost * maxPeakHeight +
            midBoost * maxPeakHeight +
            breath * maxPeakHeight +
            sparkle * maxPeakHeight
        ).coerceIn(0f, maxPeakHeight.coerceAtLeast(0f))

        if (peakHeight <= 0f) continue

        val decayFactor = if (isDragging) 0.35f else if (!isPlaying && !audioReactive) 0.6f else 1f
        val finalHeight = (peakHeight * decayFactor * 2.2f).coerceAtMost(h * 0.88f)
        val peakTop = lineY - (finalHeight / 2f)

        val activeAlpha = (0.75f + rms * 0.25f * decayFactor).coerceIn(0.3f, 0.98f)
        val inactiveAlpha = (0.25f + rms * 0.2f * decayFactor).coerceIn(0.12f, 0.45f)

        val peakColor = if (isActive) {
            val gradient = Brush.verticalGradient(
                colors = listOf(
                    WarmGoldBright.copy(alpha = activeAlpha),
                    WarmGold.copy(alpha = activeAlpha * 0.8f),
                    WarmAmber.copy(alpha = activeAlpha * 0.6f)
                ),
                startY = peakTop,
                endY = peakTop + finalHeight
            )
            gradient
        } else {
            DimInactive.copy(alpha = inactiveAlpha)
        }

        val peakWidth = if (bucketCount <= 24) 3f else 4f
        val cornerRad = CornerRadius(peakWidth / 2f)

        if (isActive) {
            drawRoundRect(
                brush = peakColor as Brush,
                topLeft = Offset(x - peakWidth / 2f, peakTop),
                size = Size(peakWidth, finalHeight),
                cornerRadius = cornerRad
            )
        } else {
            drawRoundRect(
                color = peakColor as Color,
                topLeft = Offset(x - peakWidth / 2f, peakTop),
                size = Size(peakWidth, finalHeight),
                cornerRadius = cornerRad
            )
        }
    }

    val playheadX = activeWidth.coerceIn(0f, w)
    val playheadRadius = if (isDragging) 6.dp.toPx() else 4.dp.toPx()
    val pulseRingRadius = playheadRadius * if (isPlaying) (2.5f + sin(time * 4f) * 0.5f) else 2f

    drawCircle(
        color = WarmCreamActive.copy(alpha = 0.15f),
        radius = pulseRingRadius,
        center = Offset(playheadX, lineY)
    )
    drawCircle(
        color = WarmCreamActive,
        radius = playheadRadius,
        center = Offset(playheadX, lineY)
    )
    drawCircle(
        color = Color.White.copy(alpha = 0.3f),
        radius = playheadRadius * 0.4f,
        center = Offset(playheadX, lineY)
    )
    if (isDragging) {
        drawCircle(
            color = WarmCreamActive.copy(alpha = 0.4f),
            radius = playheadRadius * 2.2f,
            center = Offset(playheadX, lineY)
        )
    }
}
