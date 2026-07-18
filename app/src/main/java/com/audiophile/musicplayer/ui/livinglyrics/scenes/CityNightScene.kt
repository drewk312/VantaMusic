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

@Composable
fun CityNightScene(
    accentColor: Color,
    energy: Float,
    modifier: Modifier = Modifier
) {
    val transition = rememberInfiniteTransition(label = "cityNight")
    val lightDrift by transition.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(6000, easing = LinearEasing), RepeatMode.Reverse),
        label = "lightDrift"
    )
    val rainOffset by transition.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(2000, easing = LinearEasing), RepeatMode.Restart),
        label = "rainOffset"
    )

    val skyColor = Color(0xFF0A0A14)
    val buildingColor = Color(0xFF1A1A2A)
    val windowColor = Color(0xFFFFD8A0)
    val neonColor = Color(0xFFE04070)
    val wetColor = Color(0xFF2A2A3A)

    Canvas(modifier = modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height

        drawRect(color = skyColor, topLeft = Offset.Zero, size = size)

        val buildingConfigs = listOf(
            0.02f to 0.7f, 0.12f to 0.55f, 0.2f to 0.75f, 0.32f to 0.5f,
            0.4f to 0.8f, 0.5f to 0.6f, 0.6f to 0.85f, 0.72f to 0.55f,
            0.8f to 0.7f, 0.88f to 0.65f
        )
        buildingConfigs.forEach { (x, heightRatio) ->
            val bh = h * heightRatio
            val bw = w * 0.09f
            drawRect(
                color = buildingColor,
                topLeft = Offset(x * w, h - bh),
                size = androidx.compose.ui.geometry.Size(bw, bh)
            )

            val windowRows = (bh / 18f).toInt().coerceAtLeast(1)
            val windowCols = (bw / 12f).toInt().coerceAtLeast(1)
            for (row in 0 until windowRows) {
                for (col in 0 until windowCols) {
                    val isLit = ((row * 7 + col * 13) % 5) != 0
                    if (isLit) {
                        val wx = x * w + col * 12f + 3f
                        val wy = h - bh + row * 18f + 3f
                        val alpha = (0.4f + lightDrift * 0.3f) * if (row % 3 == 0) 0.5f else 1f
                        drawRect(
                            color = windowColor.copy(alpha = alpha.coerceIn(0.1f, 0.8f)),
                            topLeft = Offset(wx, wy),
                            size = androidx.compose.ui.geometry.Size(6f, 8f)
                        )
                    }
                }
            }
        }

        val neonSigns = listOf(
            Pair(w * 0.15f, h * 0.45f),
            Pair(w * 0.45f, h * 0.35f),
            Pair(w * 0.72f, h * 0.4f)
        )
        neonSigns.forEach { (nx, ny) ->
            val glow = 0.3f + lightDrift * 0.3f
            val neonColors = listOf(Color(0xFFE04070), Color(0xFF40A0E0), Color(0xFF40E080))
            val nc = neonColors[(neonSigns.indexOf(nx to ny) % neonColors.size)]
            drawCircle(
                color = nc.copy(alpha = glow * 0.15f),
                radius = w * 0.06f,
                center = Offset(nx, ny)
            )
            drawCircle(
                color = nc.copy(alpha = glow * 0.4f),
                radius = w * 0.015f,
                center = Offset(nx, ny)
            )
        }

        val groundY = h * 0.85f
        drawRect(
            color = Color(0xFF0F0F1A),
            topLeft = Offset(0f, groundY),
            size = androidx.compose.ui.geometry.Size(w, h - groundY)
        )
        drawRect(
            color = wetColor,
            topLeft = Offset(0f, groundY + 2f),
            size = androidx.compose.ui.geometry.Size(w, 3f)
        )

        for (i in 0 until 3) {
            val reflectX = w * (0.2f + i * 0.3f) + lightDrift * 5f
            val reflectColor = Color(0xFFFFD8A0).copy(alpha = 0.04f + lightDrift * 0.04f)
            drawRect(
                color = reflectColor,
                topLeft = Offset(reflectX - 15f, groundY + 5f),
                size = androidx.compose.ui.geometry.Size(30f, h * 0.1f)
            )
        }

        for (i in 0 until 60) {
            val rx = ((rainOffset * w * 2f + i * 47f) % (w + 20f)) - 10f
            val ry = ((rainOffset * h * 3f + i * 73f) % h)
            val rainAlpha = 0.08f + (i % 5) * 0.03f
            drawLine(
                color = Color.White.copy(alpha = rainAlpha.coerceIn(0f, 0.2f)),
                start = Offset(rx, ry),
                end = Offset(rx - 4f, ry + 20f),
                strokeWidth = 0.5f
            )
        }

        drawRect(
            color = Color.Black.copy(alpha = 0.3f),
            topLeft = Offset.Zero,
            size = size
        )
    }
}
