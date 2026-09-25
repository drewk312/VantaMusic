package com.audiophile.musicplayer.tv.jukebox

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Twin Audiophile 300B Triode Vacuum Tubes:
 * Features realistic glass reflections, glowing tungsten filaments,
 * audio-reactive warmth bloom, and internal anode plates.
 */
@Composable
fun TvVacuumTubes(
    isPlaying: Boolean,
    modifier: Modifier = Modifier,
    tubeWidth: Dp = 56.dp,
    tubeHeight: Dp = 95.dp
) {
    // Continuous subtle filament thermionic pulse (60Hz micro-fluctuation)
    val infiniteTransition = rememberInfiniteTransition(label = "tubePulse")
    val organicPulse by infiniteTransition.animateFloat(
        initialValue = 0.88f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1200, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "filamentPulse"
    )

    // Warm-up / standby intensity
    val glowIntensity by animateFloatAsState(
        targetValue = if (isPlaying) 1.0f * organicPulse else 0.45f,
        animationSpec = tween(durationMillis = 800),
        label = "tubeGlowIntensity"
    )

    Row(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFF0D0B09).copy(alpha = 0.8f))
            .border(1.dp, Color(0xFF2B241C), RoundedCornerShape(12.dp))
            .padding(horizontal = 12.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.Bottom
    ) {
        TvSingleVacuumTube(glowIntensity = glowIntensity, width = tubeWidth, height = tubeHeight)
        TvSingleVacuumTube(glowIntensity = glowIntensity, width = tubeWidth, height = tubeHeight)
    }
}

@Composable
private fun TvSingleVacuumTube(
    glowIntensity: Float,
    width: Dp,
    height: Dp
) {
    Box(
        modifier = Modifier
            .width(width)
            .height(height),
        contentAlignment = Alignment.BottomCenter
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height

            // 1. Bakelite Socket Base
            val baseHeight = h * 0.22f
            drawRoundRect(
                brush = Brush.verticalGradient(
                    listOf(Color(0xFF2B2520), Color(0xFF141210), Color(0xFF080706))
                ),
                topLeft = Offset(w * 0.15f, h - baseHeight),
                size = Size(w * 0.7f, baseHeight),
                cornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx())
            )
            // Gold socket rim
            drawLine(
                color = Color(0xFFC9A227).copy(alpha = 0.65f),
                start = Offset(w * 0.15f, h - baseHeight),
                end = Offset(w * 0.85f, h - baseHeight),
                strokeWidth = 1.5.dp.toPx()
            )

            // 2. Glass Envelope Outline & Dark Interior
            val glassTop = 8.dp.toPx()
            val glassBottom = h - baseHeight
            val glassWidth = w * 0.76f
            val glassLeft = (w - glassWidth) / 2f

            val glassPath = Path().apply {
                moveTo(glassLeft, glassBottom)
                lineTo(glassLeft, glassTop + 14.dp.toPx())
                // Domed glass crown
                cubicTo(
                    glassLeft, glassTop,
                    glassLeft + glassWidth, glassTop,
                    glassLeft + glassWidth, glassTop + 14.dp.toPx()
                )
                lineTo(glassLeft + glassWidth, glassBottom)
                close()
            }

            // Dark evacuated vacuum interior
            drawPath(
                path = glassPath,
                brush = Brush.radialGradient(
                    colors = listOf(Color(0xFF1F1A14), Color(0xFF0A0806)),
                    center = Offset(w / 2f, h * 0.45f),
                    radius = w
                )
            )

            // 3. Top Getter Flash (Silver/Violet Mirror Coat inside glass)
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color(0xFF8A99AD).copy(alpha = 0.55f),
                        Color(0xFF42566E).copy(alpha = 0.2f),
                        Color.Transparent
                    ),
                    center = Offset(w / 2f, glassTop + 8.dp.toPx()),
                    radius = w * 0.35f
                ),
                radius = w * 0.35f,
                center = Offset(w / 2f, glassTop + 8.dp.toPx())
            )

            // 4. Internal Anode Plates (Blackened Carbon/Nickel Plates)
            val plateWidth = glassWidth * 0.55f
            val plateLeft = (w - plateWidth) / 2f
            drawRoundRect(
                color = Color(0xFF26211C),
                topLeft = Offset(plateLeft, h * 0.32f),
                size = Size(plateWidth, h * 0.38f),
                cornerRadius = CornerRadius(3.dp.toPx(), 3.dp.toPx())
            )

            // 5. Glowing Tungsten Filament (2200K Amber Heat Bloom)
            val filamentCenter = Offset(w / 2f, h * 0.48f)

            // Ambient warmth bloom behind filament
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color(0xFFFFB347).copy(alpha = 0.45f * glowIntensity),
                        Color(0xFFFF7A00).copy(alpha = 0.22f * glowIntensity),
                        Color(0xFFB84500).copy(alpha = 0.08f * glowIntensity),
                        Color.Transparent
                    ),
                    center = filamentCenter,
                    radius = w * 0.65f
                ),
                radius = w * 0.65f,
                center = filamentCenter
            )

            // Filament Wire (Double loop)
            val wireColor = if (glowIntensity > 0.6f) Color(0xFFFFF6D6) else Color(0xFFFF9E3D)
            val filamentPath = Path().apply {
                moveTo(w * 0.42f, h * 0.64f)
                lineTo(w * 0.45f, h * 0.38f)
                cubicTo(w * 0.46f, h * 0.35f, w * 0.54f, h * 0.35f, w * 0.55f, h * 0.38f)
                lineTo(w * 0.58f, h * 0.64f)
            }
            drawPath(
                path = filamentPath,
                color = wireColor.copy(alpha = glowIntensity),
                style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round)
            )

            // 6. Glass Specular Highlight (Reflection of room lighting on outer curved glass)
            drawPath(
                path = Path().apply {
                    moveTo(glassLeft + 3.dp.toPx(), glassBottom - 4.dp.toPx())
                    lineTo(glassLeft + 3.dp.toPx(), glassTop + 14.dp.toPx())
                    cubicTo(
                        glassLeft + 3.dp.toPx(), glassTop + 4.dp.toPx(),
                        glassLeft + 12.dp.toPx(), glassTop + 2.dp.toPx(),
                        glassLeft + 18.dp.toPx(), glassTop + 2.dp.toPx()
                    )
                },
                brush = Brush.verticalGradient(
                    listOf(
                        Color.White.copy(alpha = 0.35f),
                        Color.White.copy(alpha = 0.08f),
                        Color.Transparent
                    )
                ),
                style = Stroke(width = 1.8.dp.toPx(), cap = StrokeCap.Round)
            )

            // Outer glass border stroke
            drawPath(
                path = glassPath,
                color = Color(0xFF5C5245).copy(alpha = 0.35f),
                style = Stroke(width = 1.dp.toPx())
            )
        }
    }
}
