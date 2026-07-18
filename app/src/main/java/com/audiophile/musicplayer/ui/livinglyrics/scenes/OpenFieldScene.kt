package com.audiophile.musicplayer.ui.livinglyrics.scenes

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope

@Composable
fun OpenFieldScene(
    accentColor: Color,
    energy: Float,
    modifier: Modifier = Modifier
) {
    val transition = rememberInfiniteTransition(label = "openField")
    val swayOffset by transition.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(6000, easing = LinearEasing), RepeatMode.Reverse),
        label = "swayOffset"
    )
    val cloudDrift by transition.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(30000, easing = LinearEasing), RepeatMode.Restart),
        label = "cloudDrift"
    )
    val lightDrift by transition.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(9000, easing = LinearEasing), RepeatMode.Reverse),
        label = "lightDrift"
    )

    val skyBlue = Color(0xFF5B7FA5)
    val skyLight = Color(0xFF8DB6D4)
    val fieldGreen = Color(0xFF5A7A3A)
    val fieldGold = Color(0xFF9BB86A)
    val grassDark = Color(0xFF3D5A25)

    Canvas(modifier = modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height

        val skyGradient = listOf(
            Color(0xFF3A5A7A),
            Color(0xFF5B7FA5),
            Color(0xFF8DB6D4),
            Color(0xFFB0D4E8),
            Color(0xFFD0E8F0)
        )
        val bandHeight = h * 0.08f
        skyGradient.forEachIndexed { i, color ->
            drawRect(
                color = color,
                topLeft = Offset(0f, i * bandHeight),
                size = androidx.compose.ui.geometry.Size(w, bandHeight + 1f)
            )
        }

        val horizonY = h * 0.45f
        val grassTop = horizonY + 4f
        drawRect(
            color = fieldGreen,
            topLeft = Offset(0f, grassTop),
            size = androidx.compose.ui.geometry.Size(w, h - grassTop)
        )

        drawRect(
            color = Color(0xFF4A6B2A),
            topLeft = Offset(0f, horizonY),
            size = androidx.compose.ui.geometry.Size(w, 4f)
        )

        val cloudX = ((cloudDrift * w * 2f) % (w * 1.5f)) - w * 0.25f
        drawCloud(Offset(cloudX, h * 0.05f), w * 0.3f, 0.12f)
        drawCloud(Offset(cloudX + w * 0.5f, h * 0.1f), w * 0.22f, 0.1f)
        drawCloud(Offset(cloudX + w * 0.8f, h * 0.04f), w * 0.25f, 0.08f)

        val sunX = w * 0.5f + lightDrift * w * 0.1f
        val sunY = h * 0.2f
        drawCircle(
            color = Color(0xFFFFF8E0).copy(alpha = 0.15f + energy * 0.1f),
            radius = w * 0.12f,
            center = Offset(sunX, sunY)
        )
        drawCircle(
            color = Color(0xFFFFF0C0).copy(alpha = 0.08f),
            radius = w * 0.25f,
            center = Offset(sunX, sunY)
        )

        val grassSegments = 40
        for (i in 0 until grassSegments) {
            val gx = (i.toFloat() / grassSegments) * w
            val gh = 20f + (i % 7) * 8f + swayOffset * 12f
            val sway = kotlin.math.sin(swayOffset * Math.PI.toFloat() * 2f + i * 0.5f) * 6f
            val grassColor = if (i % 3 == 0) fieldGold else if (i % 2 == 0) fieldGreen else grassDark
            val alpha = 0.3f + (i % 5) * 0.06f

            val path = Path().apply {
                moveTo(gx, grassTop + gh)
                quadraticBezierTo(
                    gx + sway, grassTop + gh * 0.5f,
                    gx + sway * 1.5f, grassTop
                )
                lineTo(gx + sway * 1.5f + 3f, grassTop + 2f)
                quadraticBezierTo(
                    gx + sway + 2f, grassTop + gh * 0.5f + 2f,
                    gx + 2f, grassTop + gh
                )
                close()
            }
            drawPath(path, grassColor.copy(alpha = alpha.coerceIn(0.2f, 0.7f)))
        }

        for (i in 0 until 5) {
            val treeX = w * (0.1f + i * 0.22f)
            val treeY = grassTop + 10f
            val treeH = 40f + (i % 4) * 15f
            val treeW = 20f + (i % 3) * 10f

            drawRect(
                color = Color(0xFF3A2A1A),
                topLeft = Offset(treeX - 3f, treeY + treeH * 0.4f),
                size = androidx.compose.ui.geometry.Size(6f, treeH * 0.6f)
            )

            drawCircle(
                color = Color(0xFF2A4A1A).copy(alpha = 0.4f),
                radius = treeW * 0.5f,
                center = Offset(treeX, treeY + treeH * 0.2f)
            )
            drawCircle(
                color = Color(0xFF3A6A2A).copy(alpha = 0.3f),
                radius = treeW * 0.35f,
                center = Offset(treeX - treeW * 0.15f, treeY + treeH * 0.3f)
            )
            drawCircle(
                color = Color(0xFF2A5A1A).copy(alpha = 0.35f),
                radius = treeW * 0.3f,
                center = Offset(treeX + treeW * 0.2f, treeY + treeH * 0.25f)
            )
        }

        drawRect(
            color = Color.Black.copy(alpha = 0.25f),
            topLeft = Offset.Zero,
            size = size
        )
    }
}

private fun DrawScope.drawCloud(center: Offset, width: Float, alpha: Float) {
    val cloudColor = Color.White.copy(alpha = alpha)
    val r = width * 0.2f
    drawCircle(cloudColor, radius = r, center = center)
    drawCircle(cloudColor, radius = r * 0.8f, center = center + Offset(r * 0.7f, -r * 0.1f))
    drawCircle(cloudColor, radius = r * 0.7f, center = center + Offset(-r * 0.6f, r * 0.05f))
    drawCircle(cloudColor, radius = r * 0.5f, center = center + Offset(r * 0.3f, r * 0.15f))
}
