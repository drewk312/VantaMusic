package com.audiophile.musicplayer.tv.jukebox

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.audiophile.musicplayer.audio.visualizer.VantaAudioFrame
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Twin Analog Illuminated VU Meters — McIntosh/Accuphase style ballistic
 * needles. When a live [VantaAudioFrame] is supplied the needles dance to the
 * real playback spectrum (bass/mid drive the left, mid/treble the right);
 * otherwise a subtle simulated pulse keeps the dials alive.
 */
@Composable
fun TvVuMeters(
    isPlaying: Boolean,
    modifier: Modifier = Modifier,
    meterWidth: Dp = 100.dp,
    meterHeight: Dp = 58.dp,
    audioFrame: VantaAudioFrame? = null
) {
    // Organic pulse — always the baseline while playing. Real DSP only takes
    // over when it has audible energy; silent/zero FFT frames must not pin
    // the needles at rest (common on TV when the analyzer is attached but quiet).
    val pulse = rememberInfiniteTransition(label = "vuIdlePulse")
    val breath by pulse.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1400, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "vuBreath"
    )
    val phase = (breath * (PI * 2)).toFloat()
    val simL = ((sin(phase.toDouble()) + 1.0) / 2.0 * 0.55 + 0.22).toFloat()
    val simR = ((sin(phase.toDouble() + 0.9) + 1.0) / 2.0 * 0.50 + 0.20).toFloat()

    val frameL = audioFrame?.let { ((it.bassEnergy * 0.7f) + (it.midEnergy * 0.3f)).coerceIn(0f, 1f) }
    val frameR = audioFrame?.let { ((it.midEnergy * 0.55f) + (it.trebleEnergy * 0.45f)).coerceIn(0f, 1f) }
    val hasAudibleSignal = (frameL ?: 0f) > 0.06f || (frameR ?: 0f) > 0.06f ||
        ((audioFrame?.rms ?: 0f) > 0.05f)

    val liveL = when {
        !isPlaying -> 0f
        hasAudibleSignal -> frameL ?: simL
        else -> simL
    }
    val liveR = when {
        !isPlaying -> 0f
        hasAudibleSignal -> frameR ?: simR
        else -> simR
    }

    // Ballistic needle targets: -44° rest → +22° full-scale
    val targetL by animateFloatAsState(
        targetValue = if (isPlaying) -44f + (liveL * 66f) else -44f,
        animationSpec = spring(dampingRatio = 0.55f, stiffness = 280f),
        label = "vuNeedleL"
    )
    val targetR by animateFloatAsState(
        targetValue = if (isPlaying) -44f + (liveR * 66f) else -44f,
        animationSpec = spring(dampingRatio = 0.55f, stiffness = 280f),
        label = "vuNeedleR"
    )

    Row(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFF100E0C))
            .border(1.dp, Color(0xFF2E271F), RoundedCornerShape(12.dp))
            .padding(horizontal = 10.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        TvSingleMeter(needleAngle = targetL, channelLabel = "L", width = meterWidth, height = meterHeight)
        TvSingleMeter(needleAngle = targetR, channelLabel = "R", width = meterWidth, height = meterHeight)
    }
}

@Composable
private fun TvSingleMeter(
    needleAngle: Float,
    channelLabel: String,
    width: Dp,
    height: Dp
) {
    Box(
        modifier = Modifier
            .width(width)
            .height(height)
            .clip(RoundedCornerShape(8.dp))
            .background(
                Brush.verticalGradient(
                    listOf(Color(0xFF2E2416), Color(0xFF1A140B), Color(0xFF0F0C07))
                )
            )
            .border(1.dp, Color(0xFF524430), RoundedCornerShape(8.dp))
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height

            // Backlit warm amber glow on the dial face
            drawRect(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color(0xFFD49B3D).copy(alpha = 0.35f),
                        Color(0xFF8A5D18).copy(alpha = 0.15f),
                        Color.Transparent
                    ),
                    center = Offset(w / 2f, h * 0.4f),
                    radius = w * 0.7f
                )
            )

            // Dial arc, pivot below the visible window
            val pivot = Offset(w / 2f, h * 1.15f)
            val arcRadius = h * 0.95f

            val tickAngles = listOf(
                -45f to false, -36f to false, -28f to false,
                -20f to false, -12f to false, -5f to false,
                0f to true,
                12f to true,
                22f to true
            )

            for ((angleDeg, isRed) in tickAngles) {
                val rad = (angleDeg - 90f) * (PI / 180f)
                val tickOuter = Offset(
                    pivot.x + arcRadius * cos(rad).toFloat(),
                    pivot.y + arcRadius * sin(rad).toFloat()
                )
                val tickInner = Offset(
                    pivot.x + (arcRadius - 7.dp.toPx()) * cos(rad).toFloat(),
                    pivot.y + (arcRadius - 7.dp.toPx()) * sin(rad).toFloat()
                )
                drawLine(
                    color = if (isRed) Color(0xFFD4382B) else Color(0xFFE8DCC8).copy(alpha = 0.75f),
                    start = tickInner,
                    end = tickOuter,
                    strokeWidth = if (angleDeg == 0f) 2.dp.toPx() else 1.2.dp.toPx(),
                    cap = StrokeCap.Round
                )
            }

            // Red overload arc (+0 to +3 dB)
            drawPath(
                path = Path().apply {
                    arcTo(
                        rect = androidx.compose.ui.geometry.Rect(
                            center = pivot,
                            radius = arcRadius - 1.dp.toPx()
                        ),
                        startAngleDegrees = -90f,
                        sweepAngleDegrees = 22f,
                        forceMoveTo = false
                    )
                },
                color = Color(0xFFD4382B).copy(alpha = 0.7f),
                style = Stroke(width = 2.dp.toPx())
            )

            // Ballistic needle (shadow + razor needle)
            rotate(degrees = needleAngle, pivot = pivot) {
                drawLine(
                    color = Color.Black.copy(alpha = 0.5f),
                    start = Offset(pivot.x + 1.5.dp.toPx(), pivot.y + 1.5.dp.toPx()),
                    end = Offset(pivot.x + 1.5.dp.toPx(), pivot.y - arcRadius + 1.5.dp.toPx()),
                    strokeWidth = 1.5.dp.toPx(),
                    cap = StrokeCap.Round
                )
                drawLine(
                    brush = Brush.verticalGradient(
                        listOf(Color(0xFFE83D2A), Color(0xFF1E1A16))
                    ),
                    start = pivot,
                    end = Offset(pivot.x, pivot.y - arcRadius - 2.dp.toPx()),
                    strokeWidth = 1.6.dp.toPx(),
                    cap = StrokeCap.Round
                )
            }

            // Pivot cap
            drawCircle(
                brush = Brush.radialGradient(
                    listOf(Color(0xFF5E5448), Color(0xFF1F1B16)),
                    center = pivot,
                    radius = 8.dp.toPx()
                ),
                radius = 8.dp.toPx(),
                center = pivot
            )

            // Glass reflection bevel
            drawRoundRect(
                brush = Brush.verticalGradient(
                    listOf(Color.White.copy(alpha = 0.13f), Color.Transparent)
                ),
                topLeft = Offset.Zero,
                size = Size(w, h * 0.35f),
                cornerRadius = CornerRadius(8.dp.toPx(), 8.dp.toPx())
            )

            // Channel label etched in the glass corner
            drawContext.canvas.nativeCanvas.drawText(
                channelLabel,
                w * 0.12f,
                h * 0.72f,
                android.graphics.Paint().apply {
                    color = android.graphics.Color.argb(160, 232, 220, 200)
                    textSize = 8.dp.toPx()
                    typeface = android.graphics.Typeface.MONOSPACE
                }
            )
        }
    }
}