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
import androidx.compose.ui.graphics.drawscope.Stroke

@Composable
fun DesertHighwayScene(
    accentColor: Color,
    energy: Float,
    modifier: Modifier = Modifier
) {
    val transition = rememberInfiniteTransition(label = "desertHighway")
    val roadOffset by transition.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(8000, easing = LinearEasing), RepeatMode.Restart),
        label = "roadOffset"
    )
    val cloudOffset by transition.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(25000, easing = LinearEasing), RepeatMode.Restart),
        label = "cloudOffset"
    )
    val dustDrift by transition.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(12000, easing = LinearEasing), RepeatMode.Restart),
        label = "dustDrift"
    )

    val skyColor = Color(0xFFD4A56A)
    val horizonColor = Color(0xFFE8C87A)
    val groundColor = Color(0xFF8B7355)
    val roadColor = Color(0xFF3D3D3D)
    val lineColor = Color(0xFFE8D5A3)
    val sunColor = Color(0xFFFFDDB0)

    Canvas(modifier = modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height

        val skyGradient = listOf(
            Color(0xFF8B6914),
            Color(0xFFB8853A),
            Color(0xFFD4A56A),
            Color(0xFFE8C87A),
            Color(0xFFF0DBA0)
        )
        val bandHeight = h * 0.12f
        skyGradient.forEachIndexed { i, color ->
            drawRect(
                color = color,
                topLeft = Offset(0f, i * bandHeight),
                size = androidx.compose.ui.geometry.Size(w, bandHeight + 1f)
            )
        }

        val horizonY = h * 0.52f
        drawRect(
            color = Color(0xFFC4954A),
            topLeft = Offset(0f, horizonY - 4f),
            size = androidx.compose.ui.geometry.Size(w, 4f)
        )

        drawRect(
            color = groundColor,
            topLeft = Offset(0f, horizonY),
            size = androidx.compose.ui.geometry.Size(w, h - horizonY)
        )

        drawRect(
            color = Color(0xFF6B5B3A),
            topLeft = Offset(0f, horizonY),
            size = androidx.compose.ui.geometry.Size(w, 8f)
        )

        val sunRadius = w * 0.08f
        drawCircle(
            color = sunColor.copy(alpha = 0.7f + energy * 0.3f),
            radius = sunRadius,
            center = Offset(w * 0.65f, horizonY - sunRadius * 0.5f)
        )
        drawCircle(
            color = Color(0xFFFFF5E0).copy(alpha = 0.08f + energy * 0.08f),
            radius = sunRadius * 2.5f,
            center = Offset(w * 0.65f, horizonY - sunRadius * 0.5f)
        )

        val cloudX = ((cloudOffset * w * 1.5f) % (w + 200f)) - 100f
        drawCloud(Offset(cloudX, h * 0.08f), w * 0.25f)
        drawCloud(Offset(cloudX + w * 0.4f, h * 0.14f), w * 0.18f)
        drawCloud(Offset(cloudX + w * 0.7f, h * 0.06f), w * 0.2f)

        val roadWidth = w * 0.35f
        val roadLeft = (w - roadWidth) / 2f
        val roadRight = roadLeft + roadWidth
        drawRect(
            color = roadColor,
            topLeft = Offset(roadLeft, horizonY),
            size = androidx.compose.ui.geometry.Size(roadWidth, h - horizonY)
        )

        val lineDashHeight = (h - horizonY) * 0.08f
        val totalDash = lineDashHeight * 2f
        val effectiveOffset = ((roadOffset * (h - horizonY)) % totalDash)
        var y = horizonY + effectiveOffset
        while (y < h) {
            drawRect(
                color = lineColor,
                topLeft = Offset(size.width / 2f - 2f, y),
                size = androidx.compose.ui.geometry.Size(4f, lineDashHeight * 0.8f)
            )
            y += totalDash
        }

        val dustY = h * 0.7f + (dustDrift * 200f) % (h * 0.25f)
        dustDrift.let { drift ->
            for (i in 0 until 8) {
                val dx = ((drift * w + i * 137f) % (w + 50f)) - 25f
                val dy = dustY + (i * 37f) % (h * 0.2f)
                val size = 3f + (i % 4) * 2f
                drawCircle(
                    color = Color(0xFFE8D5A3).copy(alpha = 0.12f + (i % 3) * 0.04f),
                    radius = size,
                    center = Offset(dx, dy)
                )
            }
        }

        drawRect(
            color = Color.Black.copy(alpha = 0.35f),
            topLeft = Offset.Zero,
            size = size
        )
    }
}

private fun DrawScope.drawCloud(center: Offset, width: Float) {
    val cloudColor = Color(0xFFFFF8E0).copy(alpha = 0.08f)
    val r = width * 0.25f
    drawCircle(cloudColor, radius = r, center = center)
    drawCircle(cloudColor, radius = r * 0.8f, center = center + Offset(r * 0.6f, -r * 0.15f))
    drawCircle(cloudColor, radius = r * 0.7f, center = center + Offset(-r * 0.5f, -r * 0.1f))
    drawCircle(cloudColor, radius = r * 0.6f, center = center + Offset(r * 0.3f, r * 0.1f))
}
