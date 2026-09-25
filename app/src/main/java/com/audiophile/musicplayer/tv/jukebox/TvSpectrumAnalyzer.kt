package com.audiophile.musicplayer.tv.jukebox

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.audiophile.musicplayer.audio.visualizer.VantaAudioFrame
import kotlinx.coroutines.delay

/**
 * Classic Hi-Fi spectrum analyzer — the amber LED bar graph recessed into the
 * amplifier sill, like a golden-era Sansui/Technics receiver. While a live
 * [VantaAudioFrame] streams real DSP FFT buckets every LED dances to the
 * actual playback spectrum; peak-hold caps hang a beat longer exactly like
 * analogue displays. When nothing live is incoming a gentle idle breath keeps
 * the glass lit, and the whole array dims to rest when paused.
 */
@Composable
fun TvSpectrumAnalyzer(
    isPlaying: Boolean,
    audioFrame: VantaAudioFrame? = null,
    modifier: Modifier = Modifier,
    barCount: Int = 24,
    barGap: Dp = 3.dp
) {
    val liveFrame = audioFrame?.takeIf { it.isLiveAudio || it.timestampMs > 0L }

    // Raw target levels: real spectrum when live, idle 3-bar breath otherwise.
    // Idle bars are always visible so the analyzer reads as a live instrument,
    // not a dark void, even before playback starts or during transitions.
    val targets = FloatArray(barCount)
    if (liveFrame != null) {
        val buckets = liveFrame.fftBuckets
        for (i in 0 until barCount) {
            val src = if (buckets.isNotEmpty()) {
                (i * buckets.size) / barCount
            } else 0
            targets[i] = if (buckets.isNotEmpty()) {
                buckets[src.coerceAtMost(buckets.size - 1)].coerceIn(0f, 1f)
            } else 0f
        }
    } else {
        val phase = (System.currentTimeMillis() % 1600L) / 1600f
        val wave = ((kotlin.math.sin(phase * kotlin.math.PI * 2) + 1f) / 2f).toFloat()
        for (i in 0 until barCount) {
            val rel = 1f - ((i - barCount / 2).toFloat().let { if (it < 0f) -it else it } / (barCount / 2f))
            val idle = if (isPlaying) 0.35f else 0.12f
            targets[i] = (wave * idle * (0.25f + rel * 0.75f)).coerceIn(0f, 1f)
        }
    }

    // Smoothed LED levels + slow-fall peak caps. The loop reads the freshest
    // targets each frame via rememberUpdatedState, so live FFT buckets stream
    // straight into the bars without ever restarting the animation.
    val currentTargets by androidx.compose.runtime.rememberUpdatedState(targets)
    var levels by remember { mutableStateOf(FloatArray(barCount)) }
    var peaks by remember { mutableStateOf(FloatArray(barCount)) }

    LaunchedEffect(isPlaying) {
        val smooth = 0.45f
        val peakDecay = 0.965f
        while (true) {
            val t = currentTargets
            val next = FloatArray(barCount)
            val nextPeaks = FloatArray(barCount)
            for (i in 0 until barCount) {
                val boosted = (t[i] * t[i]).coerceIn(0f, 1f)
                next[i] = (levels[i] + (boosted - levels[i]) * smooth).coerceIn(0f, 1f)
                nextPeaks[i] = (peaks[i] * peakDecay).coerceAtLeast(next[i]).coerceIn(0f, 1f)
            }
            levels = next
            peaks = nextPeaks
            delay(16L)
        }
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(
                Brush.verticalGradient(
                    listOf(Color(0xFF1E1A16), Color(0xFF12100D))
                )
            )
            .border(1.dp, Color(0xFF4A3F33), RoundedCornerShape(10.dp))
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            val gapPx = barGap.toPx()
            val ledWidth = (w - gapPx * (barCount + 1)) / barCount
            val floor = h - 3.dp.toPx()
            val maxBarH = h - 8.dp.toPx()

            // Warm backlight spill so the LED well reads as illuminated glass.
            drawRect(
                brush = Brush.verticalGradient(
                    listOf(
                        Color(0xFFD49B3D).copy(alpha = 0.32f),
                        Color(0xFF8A5D18).copy(alpha = 0.16f),
                        Color.Transparent
                    )
                )
            )

            for (i in 0 until barCount) {
                val x = gapPx + i * (ledWidth + gapPx)
                val barH = (levels[i] * maxBarH).coerceAtLeast(if (levels[i] > 0.01f) 2.dp.toPx() else 0f)
                val topY = floor - barH

                // LED column: brushed housing + gradient lit segment.
                drawRoundRect(
                    brush = Brush.verticalGradient(
                        listOf(Color(0xFF14110E), Color(0xFF0A0908))
                    ),
                    topLeft = Offset(x, 3.dp.toPx()),
                    size = Size(ledWidth, maxBarH),
                    cornerRadius = CornerRadius(2.dp.toPx(), 2.dp.toPx())
                )
                if (barH > 0f) {
                    val lit = Brush.verticalGradient(
                        listOf( // bass: warm green → gold → hi-mid amber → sizzle
                            Color(0xFF4F8B3A).copy(alpha = 0.95f),
                            Color(0xFFC9A227).copy(alpha = 0.95f),
                            Color(0xFFE2C45A).copy(alpha = 0.95f)
                        )
                    )
                    drawRoundRect(
                        brush = lit,
                        topLeft = Offset(x, topY),
                        size = Size(ledWidth, barH),
                        cornerRadius = CornerRadius(1.5.dp.toPx(), 1.5.dp.toPx())
                    )
                    // Hot top segment brightens as the column approaches its cap.
                    val hotH = 3.dp.toPx()
                    if (barH > hotH) {
                        drawRoundRect(
                            color = Color(0xFFFFE9B0).copy(alpha = 0.7f),
                            topLeft = Offset(x, topY),
                            size = Size(ledWidth, hotH),
                            cornerRadius = CornerRadius(1.5.dp.toPx(), 1.5.dp.toPx())
                        )
                    }
                }

                // Peak-hold cap LED riding just above the peak level.
                val capY = floor - (peaks[i] * maxBarH)
                drawRoundRect(
                    color = Color(0xFFFFD98A).copy(alpha = if (peaks[i] > 0.04f) 0.95f else 0f),
                    topLeft = Offset(x, capY - 2.dp.toPx()),
                    size = Size(ledWidth, 2.dp.toPx()),
                    cornerRadius = CornerRadius(1.dp.toPx(), 1.dp.toPx())
                )
            }

            // Beat flash — a quick full-width glow at the glass rim on the downbeat.
            if (liveFrame?.isBeat == true && liveFrame.beatIntensity > 0.3f) {
                drawRoundRect(
                    brush = Brush.horizontalGradient(
                        listOf(Color.Transparent, Color(0xFFE2C45A).copy(alpha = 0.25f), Color.Transparent)
                    ),
                    topLeft = Offset(0f, 0f),
                    size = Size(w, 2.dp.toPx()),
                    cornerRadius = CornerRadius(2.dp.toPx(), 2.dp.toPx())
                )
            }

            // Glass reflection bevel.
            drawRoundRect(
                brush = Brush.verticalGradient(
                    listOf(Color.White.copy(alpha = 0.10f), Color.Transparent)
                ),
                topLeft = Offset.Zero,
                size = Size(w, h * 0.35f),
                cornerRadius = CornerRadius(10.dp.toPx(), 10.dp.toPx())
            )
        }
    }
}