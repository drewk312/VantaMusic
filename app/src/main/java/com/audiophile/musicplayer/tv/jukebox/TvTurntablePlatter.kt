package com.audiophile.musicplayer.tv.jukebox

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
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.audiophile.musicplayer.tv.TvTheme
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Hyper-Realistic Vinyl Turntable Platter:
 * - 33⅓ RPM continuous rotation with realistic motor inertia.
 * - Dynamic anisotropic specular highlights (hourglass light reflection).
 * - Concentric micro-grooves with realistic light scattering.
 * - Rim strobe dots calibrated for quartz-lock visuals.
 * - Circular album artwork label with spindle hole & brass record clamp.
 */
@Composable
fun TvTurntablePlatter(
    artworkUrl: String?,
    isPlaying: Boolean,
    modifier: Modifier = Modifier,
    size: Dp = 380.dp,
    discPresence: Float = 1f, // 0f = platter empty, 1f = disc seated on platter
    motorSpeedOverride: Float? = null // machine-driven spin up/down during disc changes
) {
    // 33⅓ RPM = 1.8 seconds per revolution (360 degrees)
    val infiniteTransition = rememberInfiniteTransition(label = "platterRotation")
    val continuousAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1800, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "continuousSpin"
    )

    // Smooth motor spin up / spin down inertia (0.0 to 1.0)
    val motorSpeed by animateFloatAsState(
        targetValue = motorSpeedOverride
            ?: if (isPlaying && discPresence > 0.8f) 1f else 0f,
        animationSpec = tween(durationMillis = 900, easing = FastOutSlowInEasing),
        label = "motorInertia"
    )

    // Wobble factor: minute organic eccentricity (0.05% tilt/drift)
    val microWobble by infiniteTransition.animateFloat(
        initialValue = -0.35f,
        targetValue = 0.35f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 900, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "microWobble"
    )

    Box(
        modifier = modifier
            .size(size)
            .shadow(24.dp, CircleShape, ambientColor = Color.Black, spotColor = Color(0xFF1A140C)),
        contentAlignment = Alignment.Center
    ) {
        // 1. Cast Aluminum Platter Base & Strobe Rim
        Canvas(modifier = Modifier.fillMaxSize()) {
            val center = Offset(this.size.width / 2f, this.size.height / 2f)
            val radius = this.size.width / 2f

            // Outer cast-metal bezel rim
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color(0xFF2A2826),
                        Color(0xFF1E1C1A),
                        Color(0xFF141210),
                        Color(0xFF0D0C0B)
                    ),
                    center = center,
                    radius = radius
                ),
                radius = radius,
                center = center
            )

            // Platter Bevel Ring (Brushed silver/champagne)
            drawCircle(
                color = Color(0xFF3F3B36),
                radius = radius - 3.dp.toPx(),
                center = center,
                style = Stroke(width = 2.dp.toPx())
            )

            // Calibrated Quartz-Lock Strobe Dots (4 rows on rim)
            val numStrobeDots = 72
            val strobeRadius1 = radius - 7.dp.toPx()
            val strobeRadius2 = radius - 11.dp.toPx()
            val strobeDotSize = 2.2.dp.toPx()

            // As motor spins, strobe dots shift with simulated optical persistence
            val strobeOffsetAngle = (continuousAngle * motorSpeed * 0.4f) % (360f / numStrobeDots)

            for (i in 0 until numStrobeDots) {
                val angleDeg = (i * (360f / numStrobeDots)) + strobeOffsetAngle
                val angleRad = angleDeg * (PI / 180f)

                val dotX1 = center.x + strobeRadius1 * cos(angleRad).toFloat()
                val dotY1 = center.y + strobeRadius1 * sin(angleRad).toFloat()
                drawCircle(
                    color = Color(0xFF8C867D).copy(alpha = 0.85f),
                    radius = strobeDotSize / 2f,
                    center = Offset(dotX1, dotY1)
                )

                if (i % 2 == 0) {
                    val dotX2 = center.x + strobeRadius2 * cos(angleRad).toFloat()
                    val dotY2 = center.y + strobeRadius2 * sin(angleRad).toFloat()
                    drawCircle(
                        color = Color(0xFF6B655C).copy(alpha = 0.65f),
                        radius = (strobeDotSize * 0.8f) / 2f,
                        center = Offset(dotX2, dotY2)
                    )
                }
            }

            // Platter Mat Recess (Damped Rubber Slipmat)
            val matRadius = radius - 14.dp.toPx()
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color(0xFF181615),
                        Color(0xFF100F0E),
                        Color(0xFF080706)
                    ),
                    center = center,
                    radius = matRadius
                ),
                radius = matRadius,
                center = center
            )
        }

        // 2. Vinyl Record (Only rendered if disc is on platter)
        if (discPresence > 0.05f) {
            val recordSize = size - 32.dp

            Box(
                modifier = Modifier
                    .size(recordSize)
                    .graphicsLayer {
                        scaleX = discPresence
                        scaleY = discPresence
                        rotationZ = (continuousAngle * motorSpeed) + microWobble
                    },
                contentAlignment = Alignment.Center
            ) {
                // Vinyl Grooves & Body Canvas
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val center = Offset(this.size.width / 2f, this.size.height / 2f)
                    val recordRadius = this.size.width / 2f

                    // Black Virgin Vinyl Body
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                Color(0xFF121110),
                                Color(0xFF0A0908),
                                Color(0xFF050505)
                            ),
                            center = center,
                            radius = recordRadius
                        ),
                        radius = recordRadius,
                        center = center
                    )

                    // Outer Lead-in Lip Groove
                    drawCircle(
                        color = Color(0xFF282420).copy(alpha = 0.7f),
                        radius = recordRadius - 2.dp.toPx(),
                        center = center,
                        style = Stroke(width = 1.5.dp.toPx())
                    )

                    // Micro-Grooves (Concentric Music Tracks)
                    val grooveStartRadius = recordRadius - 8.dp.toPx()
                    val grooveEndRadius = recordRadius * 0.42f
                    val grooveStep = 3.5.dp.toPx()
                    var r = grooveStartRadius
                    var ringIndex = 0

                    while (r > grooveEndRadius) {
                        val isTrackGap = ringIndex % 18 == 0
                        val alpha = if (isTrackGap) 0.35f else (0.08f + ((ringIndex % 5) * 0.03f))
                        val strokeW = if (isTrackGap) 2.dp.toPx() else 0.75.dp.toPx()

                        drawCircle(
                            color = if (isTrackGap) Color(0xFF38332C).copy(alpha = alpha) else Color(0xFFD4C8B5).copy(alpha = alpha),
                            radius = r,
                            center = center,
                            style = Stroke(width = strokeW)
                        )
                        r -= grooveStep
                        ringIndex++
                    }

                    // Run-out Groove with subtle etched matrix numbers
                    drawCircle(
                        color = Color(0xFF25221F).copy(alpha = 0.8f),
                        radius = grooveEndRadius - 4.dp.toPx(),
                        center = center,
                        style = Stroke(width = 1.2.dp.toPx())
                    )
                }

                // Anisotropic Specular Highlight (Hourglass sheen on micro-grooves)
                // Stays fixed relative to overhead room light, even as vinyl rotates!
                Canvas(
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            // Counter-rotate so highlight remains aligned with ambient room light source
                            rotationZ = -((continuousAngle * motorSpeed) + microWobble)
                        }
                ) {
                    val center = Offset(this.size.width / 2f, this.size.height / 2f)
                    val recordRadius = this.size.width / 2f

                    // Draw two opposing soft bowtie / wedge highlights simulating light on microgrooves
                    val highlightColors = listOf(
                        Color.Transparent,
                        Color(0xFFF5E8D0).copy(alpha = 0.06f),
                        Color(0xFFFFF6E5).copy(alpha = 0.15f),
                        Color(0xFFF5E8D0).copy(alpha = 0.06f),
                        Color.Transparent
                    )

                    // Top-Right to Bottom-Left sheen
                    rotate(degrees = 42f, pivot = center) {
                        drawRect(
                            brush = Brush.horizontalGradient(
                                colors = highlightColors,
                                startX = center.x - 70.dp.toPx(),
                                endX = center.x + 70.dp.toPx()
                            ),
                            topLeft = Offset(center.x - 70.dp.toPx(), center.y - recordRadius),
                            size = Size(140.dp.toPx(), recordRadius * 2f)
                        )
                    }

                    // Perpendicular counter-sheen (secondary reflection)
                    rotate(degrees = 132f, pivot = center) {
                        drawRect(
                            brush = Brush.horizontalGradient(
                                colors = listOf(
                                    Color.Transparent,
                                    Color(0xFFE8DCC8).copy(alpha = 0.05f),
                                    Color(0xFFE8DCC8).copy(alpha = 0.09f),
                                    Color(0xFFE8DCC8).copy(alpha = 0.05f),
                                    Color.Transparent
                                ),
                                startX = center.x - 55.dp.toPx(),
                                endX = center.x + 55.dp.toPx()
                            ),
                            topLeft = Offset(center.x - 55.dp.toPx(), center.y - recordRadius),
                            size = Size(110.dp.toPx(), recordRadius * 2f)
                        )
                    }
                }

                // 3. Center Record Label (Circular Artwork + Spindle Rim)
                val labelSize = recordSize * 0.38f
                Box(
                    modifier = Modifier
                        .size(labelSize)
                        .clip(CircleShape)
                        .background(TvTheme.Surface)
                        .border(1.5.dp, Color(0xFF6B5838), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    if (!artworkUrl.isNullOrBlank()) {
                        AsyncImage(
                            model = artworkUrl,
                            contentDescription = "Vinyl Label Artwork",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .fillMaxSize()
                                .clip(CircleShape)
                        )
                    } else {
                        // Default vintage label badge
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(
                                    Brush.radialGradient(
                                        listOf(Color(0xFF382A18), Color(0xFF1E160C), Color(0xFF0F0B06))
                                    )
                                )
                        )
                    }

                    // Gold foil ring on label perimeter
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .border(1.dp, TvTheme.HiResGold.copy(alpha = 0.55f), CircleShape)
                    )

                    // Spindle Hole
                    Box(
                        modifier = Modifier
                            .size(16.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF080706))
                            .border(1.dp, Color(0xFF8C7D68), CircleShape)
                    )
                }
            }
        }

        // 4. Center Spindle & Heavy Brass Record Stabilizer Clamp
        val clampSize = size * 0.16f
        Box(
            modifier = Modifier
                .size(clampSize)
                .shadow(12.dp, CircleShape)
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            Color(0xFFE8D28A), // Polished brass highlight
                            Color(0xFFC4A245),
                            Color(0xFF7A6020),
                            Color(0xFF3B2E0E)
                        )
                    )
                )
                .border(1.5.dp, Color(0xFFFFF0B8).copy(alpha = 0.6f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            // Inner Knurled Grip Ring
            Box(
                modifier = Modifier
                    .size(clampSize * 0.6f)
                    .clip(CircleShape)
                    .background(Color(0xFF261E10))
                    .border(1.dp, Color(0xFF8A6E2B), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                // Center Spindle Pin (Chrome silver)
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(Color(0xFFE6E6E6))
                        .border(0.5.dp, Color(0xFF595959), CircleShape)
                )
            }
        }
    }
}
