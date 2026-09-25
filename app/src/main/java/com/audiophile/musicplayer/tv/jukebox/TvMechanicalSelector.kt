package com.audiophile.musicplayer.tv.jukebox

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.sin

/**
 * Mechanical Record Selector — the brain of the changer.
 *
 * A heavy articulated transfer rail spans the machine between the magazine
 * (left) and the turntable platter (center). The carriage travels along it,
 * lifts a vertical piston, and its twin claws open/close around the record
 * edge. Positions are driven by [JukeboxMachineFrame.changerX] (0f = magazine,
 * 1f = over platter) so every disc change reads as one continuous mechanical
 * motion — not a fade.
 */
@Composable
fun TvMechanicalSelector(
    frame: JukeboxMachineFrame,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        val rackX = size.width * 0.06f
        val platterX = size.width * 0.72f
        val armY = size.height * 0.38f
        val span = (platterX - rackX).coerceAtLeast(1f)

        val currentX = rackX + (frame.changerX.coerceIn(0f, 1f)) * span
        // Vertical arc: the carriage lifts during transit at both ends.
        val travelT = frame.changerX.coerceIn(0f, 1f)
        val arcLift = sin(minOf(travelT, 1f - travelT) * PI.toFloat() * 2f).coerceAtLeast(0f) * 22.dp.toPx()
        val currentY = armY - arcLift

        // --- Transfer rail (polished gunmetal tube cast onto the changer frame) ---
        drawLine(
            brush = Brush.linearGradient(
                colors = listOf(Color(0xFF171513), Color(0xFF3B3731), Color(0xFF1E1C1A))
            ),
            start = Offset(rackX - 26.dp.toPx(), armY),
            end = Offset(platterX + 26.dp.toPx(), armY),
            strokeWidth = 7.dp.toPx(),
            cap = StrokeCap.Round
        )
        // Rail specular highlight
        drawLine(
            color = Color.White.copy(alpha = 0.12f),
            start = Offset(rackX - 26.dp.toPx(), armY - 2.5.dp.toPx()),
            end = Offset(platterX + 26.dp.toPx(), armY - 2.5.dp.toPx()),
            strokeWidth = 1.5.dp.toPx(),
            cap = StrokeCap.Round
        )

        // Rail mount bosses (bolted to the plinth)
        floatArrayOf(rackX - 14.dp.toPx(), platterX + 14.dp.toPx()).forEach { bossX ->
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(Color(0xFF4A443D), Color(0xFF221F1C)),
                    center = Offset(bossX, armY),
                    radius = 9.dp.toPx()
                ),
                radius = 9.dp.toPx(),
                center = Offset(bossX, armY)
            )
            drawCircle(
                color = Color(0xFFC9A227).copy(alpha = 0.5f),
                radius = 3.5.dp.toPx(),
                center = Offset(bossX, armY),
                style = Stroke(width = 1.dp.toPx())
            )
        }

        // --- Carriage drop shadow on the plinth ---
        drawCircle(
            color = Color.Black.copy(alpha = 0.45f),
            radius = 20.dp.toPx(),
            center = Offset(currentX, armY + 8.dp.toPx())
        )

        // --- Carriage housing (matched vertical reciprocating block) ---
        val carriageTop = Offset(currentX, armY - 22.dp.toPx())
        val carriageBottom = Offset(currentX, currentY + 4.dp.toPx())

        // Brass hydraulic piston cylinder (extends as the head reaches a station)
        drawLine(
            brush = Brush.verticalGradient(
                listOf(Color(0xFFE4C760), Color(0xFF8A6E2B), Color(0xFF5A4516))
            ),
            start = carriageTop,
            end = carriageBottom,
            strokeWidth = 5.dp.toPx(),
            cap = StrokeCap.Round
        )
        drawLine(
            color = Color.White.copy(alpha = 0.35f),
            start = Offset(carriageTop.x - 1.dp.toPx(), carriageTop.y + 2.dp.toPx()),
            end = Offset(carriageBottom.x - 1.dp.toPx(), carriageBottom.y + 2.dp.toPx()),
            strokeWidth = 1.dp.toPx(),
            cap = StrokeCap.Round
        )

        // Solenoid actuator housing
        drawRoundRect(
            brush = Brush.radialGradient(
                listOf(Color(0xFF433D34), Color(0xFF181512)),
                center = carriageTop,
                radius = 13.dp.toPx()
            ),
            topLeft = Offset(carriageTop.x - 12.dp.toPx(), carriageTop.y - 8.dp.toPx()),
            size = Size(24.dp.toPx(), 16.dp.toPx()),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(3.dp.toPx(), 3.dp.toPx())
        )

        // --- Twin metal claws gripping the record edge ---
        val clawTip = Offset(currentX, currentY + 18.dp.toPx())
        // Open wide while travelling, close while clamping
        val clawSpan = if (frame.isClamping) 16.dp.toPx() else 30.dp.toPx()

        // When carrying a record, the disc silhouette rides just under the claws.
        if (frame.isClamping) {
            drawCircle(
                color = Color(0xFF0B0A09),
                radius = 15.dp.toPx(),
                center = Offset(currentX, clawTip.y),
                style = Stroke(width = 3.dp.toPx())
            )
        }

        drawLine(
            color = Color(0xFFC9BFAD),
            start = Offset(currentX - 4.dp.toPx(), clawTip.y - 6.dp.toPx()),
            end = Offset(currentX - clawSpan / 2f, clawTip.y + 14.dp.toPx()),
            strokeWidth = 3.5.dp.toPx(),
            cap = StrokeCap.Round
        )
        drawLine(
            color = Color(0xFFC9BFAD),
            start = Offset(currentX + 4.dp.toPx(), clawTip.y - 6.dp.toPx()),
            end = Offset(currentX + clawSpan / 2f, clawTip.y + 14.dp.toPx()),
            strokeWidth = 3.5.dp.toPx(),
            cap = StrokeCap.Round
        )

        // Claw knuckle pins
        drawCircle(color = Color(0xFFF0E4C8), radius = 3.dp.toPx(), center = Offset(currentX, clawTip.y - 6.dp.toPx()))
        rotate(degrees = 180f, pivot = Offset(currentX, clawTip.y)) {
            drawCircle(color = Color(0xFFF0E4C8).copy(alpha = 0.6f), radius = 2.4.dp.toPx(), center = Offset(0f, 0f))
        }
    }
}