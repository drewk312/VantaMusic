package com.audiophile.musicplayer.ui

import androidx.compose.animation.core.EaseInOutCubic
import androidx.compose.animation.core.EaseOutCubic
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

private data class SplashQuote(val text: String, val attribution: String? = null)

private val splashQuotes = listOf(
    SplashQuote("Listen deeper.", "VANTA"),
    SplashQuote("Every source. One place to feel it.", "VANTA"),
    SplashQuote("Headphones on. World off.", "VANTA"),
    SplashQuote("The song you meant to hear — in the best version.", "VANTA"),
    SplashQuote("Where the library ends, the mood begins.", "VANTA"),
    SplashQuote("Music is the shorthand of emotion.", "Hans Christian Andersen"),
    SplashQuote("After silence, that which comes nearest to expressing the inexpressible is music.", "Aldous Huxley"),
    SplashQuote("Music washes away from the soul the dust of everyday life.", "Berthold Auerbach")
)

@Composable
fun VantaSplashScreen(
    onFinished: () -> Unit,
    modifier: Modifier = Modifier
) {
    val quote = remember { splashQuotes.random() }
    var contentReady by remember { mutableStateOf(false) }
    var exiting by remember { mutableStateOf(false) }

    val contentAlpha by animateFloatAsState(
        targetValue = when {
            exiting -> 0f
            contentReady -> 1f
            else -> 0f
        },
        animationSpec = tween(durationMillis = 700, easing = FastOutSlowInEasing),
        label = "splashContentAlpha"
    )

    val infinite = rememberInfiniteTransition(label = "splashAmbient")
    val glowPulse by infinite.animateFloat(
        initialValue = 0.35f,
        targetValue = 0.85f,
        animationSpec = infiniteRepeatable(
            animation = tween(2200, easing = EaseInOutCubic),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glowPulse"
    )
    val ringRotation by infinite.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(14000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "ringRotation"
    )

    LaunchedEffect(Unit) {
        delay(120)
        contentReady = true
        delay(2600)
        exiting = true
        delay(650)
        onFinished()
    }

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        VantaAppBackground()
        Canvas(modifier = Modifier.fillMaxSize()) {
            val center = Offset(size.width / 2f, size.height * 0.38f)
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        AppAccent.copy(alpha = 0.14f * glowPulse),
                        Color.Transparent
                    ),
                    center = center,
                    radius = size.minDimension * 0.55f
                ),
                radius = size.minDimension * 0.55f,
                center = center
            )
            drawCircle(
                color = AppAccent.copy(alpha = 0.08f * glowPulse),
                radius = size.minDimension * 0.22f,
                center = center,
                style = Stroke(width = 1.5f)
            )
            val ringRadius = size.minDimension * 0.28f
            drawCircle(
                color = AppAccent.copy(alpha = 0.12f),
                radius = ringRadius,
                center = center,
                style = Stroke(width = 1f)
            )
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 36.dp)
                .alpha(contentAlpha),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            SplashOrb(
                rotationDegrees = ringRotation,
                pulse = glowPulse
            )

            Spacer(Modifier.height(28.dp))

            Text(
                text = "VANTA",
                style = VantaType.editorialHero.copy(
                    fontStyle = androidx.compose.ui.text.font.FontStyle.Normal,
                    fontWeight = FontWeight.Light,
                    letterSpacing = 10.sp
                ),
                textAlign = TextAlign.Center
            )
            Text(
                text = "AUDIO",
                color = AppAccent,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 4.sp
            )

            Spacer(Modifier.height(40.dp))

            Text(
                text = quote.text,
                style = VantaType.editorialLarge,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
            if (!quote.attribution.isNullOrBlank()) {
                Spacer(Modifier.height(12.dp))
                Text(
                    text = quote.attribution.uppercase(),
                    color = AppTextMuted,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    letterSpacing = 2.sp,
                    textAlign = TextAlign.Center
                )
            }

            Spacer(Modifier.height(36.dp))

            SplashWaveBars(active = contentReady && !exiting)
        }
    }
}

@Composable
private fun SplashOrb(
    rotationDegrees: Float,
    pulse: Float
) {
    val barCount = 5
    val infinite = rememberInfiniteTransition(label = "orbBars")
    Canvas(modifier = Modifier.size(88.dp)) {
        val center = Offset(size.width / 2f, size.height / 2f)
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    AppAccent.copy(alpha = 0.18f * pulse),
                    Color.Transparent
                ),
                center = center,
                radius = size.minDimension / 2f
            ),
            radius = size.minDimension / 2f,
            center = center
        )
        val baseRadius = size.minDimension * 0.32f
        for (i in 0 until barCount) {
            val angle = (rotationDegrees + i * (360f / barCount)) * (PI / 180f).toFloat()
            val x = center.x + cos(angle) * baseRadius
            val y = center.y + sin(angle) * baseRadius
            drawCircle(
                color = AppAccent.copy(alpha = 0.35f + pulse * 0.25f),
                radius = 3.5f,
                center = Offset(x, y)
            )
        }
        drawCircle(
            color = AppAccent.copy(alpha = 0.5f),
            radius = 10f,
            center = center
        )
    }
}

@Composable
private fun SplashWaveBars(active: Boolean) {
    val infinite = rememberInfiniteTransition(label = "waveBars")
    RowWaveBars(
        active = active,
        phase = infinite.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(1200, easing = LinearEasing),
                repeatMode = RepeatMode.Restart
            ),
            label = "wavePhase"
        ).value
    )
}

@Composable
private fun RowWaveBars(active: Boolean, phase: Float) {
    val heights = listOf(0.45f, 0.72f, 1f, 0.68f, 0.52f)
    Canvas(
        modifier = Modifier
            .fillMaxWidth(0.35f)
            .height(28.dp)
    ) {
        val barWidth = 4f
        val gap = 8f
        val totalWidth = heights.size * barWidth + (heights.size - 1) * gap
        var x = (size.width - totalWidth) / 2f
        heights.forEachIndexed { index, base ->
            val wave = sin((phase * 2f * PI + index * 0.9f).toFloat()) * 0.22f
            val h = size.height * (base + if (active) wave else 0f).coerceIn(0.2f, 1f)
            drawLine(
                color = AppAccent.copy(alpha = if (active) 0.75f else 0.25f),
                start = Offset(x + barWidth / 2f, size.height),
                end = Offset(x + barWidth / 2f, size.height - h),
                strokeWidth = barWidth,
                cap = StrokeCap.Round
            )
            x += barWidth + gap
        }
    }
}
