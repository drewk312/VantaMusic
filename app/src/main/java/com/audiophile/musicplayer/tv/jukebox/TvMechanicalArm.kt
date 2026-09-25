package com.audiophile.musicplayer.tv.jukebox

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * State of the mechanical pick-and-place changer arm.
 */
enum class JukeboxChangerState {
    IDLE,
    SWING_TO_RACK,
    CLAMP_DISC,
    LIFT_FROM_RACK,
    SWING_TO_PLATTER,
    LOWER_TO_SPINDLE,
    RELEASE_AND_RETRACT
}

/**
 * Precision Audiophile Tonearm with fluid-damped cueing and groove tracking.
 * Placed on the top-right / right side of the turntable platter.
 */
@Composable
fun TvTonearm(
    isPlaying: Boolean,
    progress: Float, // 0.0f (lead-in) to 1.0f (run-out)
    isChangingDisc: Boolean,
    modifier: Modifier = Modifier,
    machineAngle: Float? = null, // machine-driven: 14° clear, 18° rest, 28°–46° tracking
    machineLift: Float? = null    // machine-driven: 0f = on vinyl, 1f = lifted on cue lever
) {
    // Target tracking angle:
    // Rest post: 18 degrees
    // Lead-in groove: 28 degrees
    // Runout groove: 46 degrees
    val targetAngle = if (machineAngle != null) {
        machineAngle
    } else when {
        isChangingDisc -> 14f // Swung completely clear of platter during disc change
        !isPlaying -> 18f     // Parked on arm rest
        else -> 28f + (progress.coerceIn(0f, 1f) * 18f) // Tracking record grooves
    }

    val animatedAngle by animateFloatAsState(
        targetValue = targetAngle,
        animationSpec = tween(
            durationMillis = if (isChangingDisc) 600 else 900,
            easing = FastOutSlowInEasing
        ),
        label = "tonearmAngle"
    )

    // Vertical cueing height: 0f = resting on vinyl, 1f = lifted by hydraulic cue lever
    val cueLift by animateFloatAsState(
        targetValue = machineLift ?: if (isPlaying && !isChangingDisc) 0f else 1f,
        animationSpec = tween(durationMillis = 500, easing = LinearOutSlowInEasing),
        label = "tonearmCueLift"
    )

    Box(modifier = modifier) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val pivot = Offset(size.width * 0.82f, size.height * 0.16f)

            // 1. Tonearm Gimbal Pivot Base (Heavy Anodized Brushed Metal & Brass)
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(Color(0xFF4A443D), Color(0xFF26221D), Color(0xFF141210)),
                    center = pivot,
                    radius = 28.dp.toPx()
                ),
                radius = 28.dp.toPx(),
                center = pivot
            )
            drawCircle(
                color = Color(0xFFC9A227).copy(alpha = 0.7f), // Brass bearing ring
                radius = 20.dp.toPx(),
                center = pivot,
                style = Stroke(width = 2.dp.toPx())
            )
            drawCircle(
                color = Color(0xFF1C1A17),
                radius = 12.dp.toPx(),
                center = pivot
            )

            // 2. Arm Rest Post & Cueing Lift Platform
            val restAngleRad = 18f * (PI / 180f)
            val restDistance = 45.dp.toPx()
            val restPos = Offset(
                pivot.x - restDistance * cos(restAngleRad).toFloat(),
                pivot.y + restDistance * sin(restAngleRad).toFloat()
            )
            // Rest cradle
            drawCircle(
                color = Color(0xFF2E2923),
                radius = 6.dp.toPx(),
                center = restPos
            )
            drawLine(
                color = Color(0xFF6B6053),
                start = Offset(restPos.x - 6.dp.toPx(), restPos.y),
                end = Offset(restPos.x + 6.dp.toPx(), restPos.y),
                strokeWidth = 2.dp.toPx()
            )

            // 3. Rotating Tonearm Wand, Counterweight & Cartridge.
            // The cue lever lifts the WHOLE wand assembly (tip + headshell +
            // stylus) off the vinyl, not just its shadow, so the needle truly
            // clears the record on lift/return instead of dragging across it.
            rotate(degrees = animatedAngle, pivot = pivot) {
                val liftElevation = cueLift * 10.dp.toPx()
                withTransform({ translate(top = -liftElevation) }) {
                    // Rear Counterweight (Brass & Stainless Steel)
                    val counterweightPos = Offset(pivot.x + 24.dp.toPx(), pivot.y - 14.dp.toPx())
                    drawRoundRect(
                        brush = Brush.linearGradient(
                            colors = listOf(Color(0xFFD4B86A), Color(0xFF8A6E2B), Color(0xFF423514)),
                            start = Offset(counterweightPos.x - 8.dp.toPx(), counterweightPos.y - 12.dp.toPx()),
                            end = Offset(counterweightPos.x + 8.dp.toPx(), counterweightPos.y + 12.dp.toPx())
                        ),
                        topLeft = Offset(counterweightPos.x - 10.dp.toPx(), counterweightPos.y - 12.dp.toPx()),
                        size = androidx.compose.ui.geometry.Size(20.dp.toPx(), 24.dp.toPx()),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(4.dp.toPx(), 4.dp.toPx())
                    )
                    // Tracking force calibration rings
                    drawLine(
                        color = Color(0xFF1F180A),
                        start = Offset(counterweightPos.x, counterweightPos.y - 12.dp.toPx()),
                        end = Offset(counterweightPos.x, counterweightPos.y + 12.dp.toPx()),
                        strokeWidth = 1.5.dp.toPx()
                    )

                    // Arm Wand (Polished S-shape stainless steel tube)
                    val armPath = Path().apply {
                        moveTo(pivot.x, pivot.y)
                        cubicTo(
                            pivot.x - 60.dp.toPx(), pivot.y + 40.dp.toPx(),
                            pivot.x - 120.dp.toPx(), pivot.y + 120.dp.toPx(),
                            pivot.x - 190.dp.toPx(), pivot.y + 190.dp.toPx()
                        )
                    }

                    // Arm drop shadow on plinth (sits on the vinyl, not the arm)
                    drawPath(
                        path = armPath,
                        color = Color.Black.copy(alpha = 0.30f + (cueLift * 0.20f)),
                        style = Stroke(width = 5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
                    )

                    // Polished Arm Wand (Chrome Highlight)
                    drawPath(
                        path = armPath,
                        brush = Brush.linearGradient(
                            listOf(Color(0xFFE8E8E8), Color(0xFFA6A6A6), Color(0xFF5E5E5E))
                        ),
                        style = Stroke(width = 4.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
                    )

                    // Headshell & Phono Cartridge (Audiophile Moving Magnet
                    // Cartridge). The headshell pitch follows the wand so the
                    // stylus rides rigidly at the tip — no orbiting during swings.
                    val headshellPos = Offset(pivot.x - 190.dp.toPx(), pivot.y + 190.dp.toPx())
                    val headshellPitch = -26f - (animatedAngle - 28f) * 0.35f
                    rotate(degrees = headshellPitch, pivot = headshellPos) {
                    // Carbon fiber headshell
                    drawRoundRect(
                        brush = Brush.horizontalGradient(
                            listOf(Color(0xFF1E1C1A), Color(0xFF302C28), Color(0xFF141210))
                        ),
                        topLeft = Offset(headshellPos.x - 6.dp.toPx(), headshellPos.y),
                        size = androidx.compose.ui.geometry.Size(12.dp.toPx(), 26.dp.toPx()),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(3.dp.toPx(), 3.dp.toPx())
                    )
                    // Gold finger lift tab
                    drawLine(
                        color = Color(0xFFE2C45A),
                        start = Offset(headshellPos.x + 6.dp.toPx(), headshellPos.y + 6.dp.toPx()),
                        end = Offset(headshellPos.x + 14.dp.toPx(), headshellPos.y + 8.dp.toPx()),
                        strokeWidth = 1.5.dp.toPx()
                    )
                    // Cartridge stylus tip (Gold & Red accent)
                    drawCircle(
                        color = Color(0xFFD4382B),
                        radius = 2.5.dp.toPx(),
                        center = Offset(headshellPos.x, headshellPos.y + 24.dp.toPx())
                    )
                    // Diamond needle tip
                    drawCircle(
                        color = Color(0xFFFFFFFF),
                        radius = 1.dp.toPx(),
                        center = Offset(headshellPos.x, headshellPos.y + 26.dp.toPx())
                    )
                }
                }
            }
        }
    }
}

/**
 * Mechanical Pick-and-Place Transfer Gripper:
 * Moves between the carousel magazine on the right and the platter on the left.
 */
@Composable
fun TvPickAndPlaceArm(
    changerProgress: Float, // 0f = parked at carousel, 1f = extended over platter
    isClamping: Boolean,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        val rackX = size.width * 0.85f
        val platterX = size.width * 0.28f
        val armY = size.height * 0.45f

        // Interpolated gripper position
        val currentX = rackX - (changerProgress * (rackX - platterX))
        // Vertical arc: arm lifts during transit
        val arcLift = sin(changerProgress * PI.toFloat()) * 32.dp.toPx()
        val currentY = armY - arcLift

        // Main Heavy Articulated Transfer Rail
        drawLine(
            brush = Brush.horizontalGradient(
                listOf(Color(0xFF262320), Color(0xFF3F3B36), Color(0xFF1E1C1A))
            ),
            start = Offset(rackX + 20.dp.toPx(), armY - 20.dp.toPx()),
            end = Offset(platterX - 20.dp.toPx(), armY - 20.dp.toPx()),
            strokeWidth = 6.dp.toPx(),
            cap = StrokeCap.Round
        )

        // Gripper Carriage Head
        val carriageTop = Offset(currentX, currentY - 20.dp.toPx())
        val carriageBottom = Offset(currentX, currentY)

        // Drop shadow
        drawCircle(
            color = Color.Black.copy(alpha = 0.4f),
            radius = 18.dp.toPx(),
            center = Offset(currentX, currentY + 12.dp.toPx())
        )

        // Vertical Piston Cylinder (Brass / Chrome)
        drawLine(
            brush = Brush.verticalGradient(
                listOf(Color(0xFFE2C45A), Color(0xFF8A6E2B), Color(0xFFE2C45A))
            ),
            start = carriageTop,
            end = carriageBottom,
            strokeWidth = 5.dp.toPx(),
            cap = StrokeCap.Round
        )

        // Gripper Claw Assembly (Twin mechanical fingers)
        val clawSpan = if (isClamping) 14.dp.toPx() else 26.dp.toPx()
        // Left Claw Finger
        drawLine(
            color = Color(0xFFC7BBAA),
            start = Offset(carriageBottom.x - 4.dp.toPx(), carriageBottom.y),
            end = Offset(carriageBottom.x - clawSpan / 2f, carriageBottom.y + 14.dp.toPx()),
            strokeWidth = 3.dp.toPx(),
            cap = StrokeCap.Round
        )
        // Right Claw Finger
        drawLine(
            color = Color(0xFFC7BBAA),
            start = Offset(carriageBottom.x + 4.dp.toPx(), carriageBottom.y),
            end = Offset(carriageBottom.x + clawSpan / 2f, carriageBottom.y + 14.dp.toPx()),
            strokeWidth = 3.dp.toPx(),
            cap = StrokeCap.Round
        )

        // Solenoid Actuator Housing
        drawRoundRect(
            brush = Brush.radialGradient(
                listOf(Color(0xFF453F38), Color(0xFF1C1A18)),
                center = carriageBottom,
                radius = 12.dp.toPx()
            ),
            topLeft = Offset(carriageBottom.x - 10.dp.toPx(), carriageBottom.y - 10.dp.toPx()),
            size = androidx.compose.ui.geometry.Size(20.dp.toPx(), 14.dp.toPx()),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(3.dp.toPx(), 3.dp.toPx())
        )
    }
}
