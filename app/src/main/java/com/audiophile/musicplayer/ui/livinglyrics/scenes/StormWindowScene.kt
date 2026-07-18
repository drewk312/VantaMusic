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
fun StormWindowScene(
    accentColor: Color,
    energy: Float,
    modifier: Modifier = Modifier
) {
    val transition = rememberInfiniteTransition(label = "stormWindow")
    val rainOffset by transition.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1500, easing = LinearEasing), RepeatMode.Restart),
        label = "rainOffset"
    )
    val lightningFlash by transition.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(8000, easing = LinearEasing), RepeatMode.Restart),
        label = "lightningFlash"
    )
    val fogBreath by transition.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(6000, easing = LinearEasing), RepeatMode.Reverse),
        label = "fogBreath"
    )

    val wallColor = Color(0xFF1A1A1A)
    val glassColor = Color(0xFF1A2A3A)
    val flashColor = Color(0xFFE8F0FF)
    val stormCloud = Color(0xFF2A2A32)

    Canvas(modifier = modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height

        drawRect(color = wallColor, topLeft = Offset.Zero, size = size)

        val margin = w * 0.08f
        drawRect(
            color = glassColor,
            topLeft = Offset(margin, margin),
            size = androidx.compose.ui.geometry.Size(w - margin * 2f, h - margin * 2f)
        )

        val leftWall = margin * 0.6f
        drawRect(
            color = Color(0xFF2A2A2A),
            topLeft = Offset(0f, 0f),
            size = androidx.compose.ui.geometry.Size(leftWall, h)
        )
        drawRect(
            color = Color(0xFF2A2A2A),
            topLeft = Offset(w - leftWall, 0f),
            size = androidx.compose.ui.geometry.Size(leftWall, h)
        )
        drawRect(
            color = Color(0xFF2A2A2A),
            topLeft = Offset(0f, 0f),
            size = androidx.compose.ui.geometry.Size(w, margin * 0.6f)
        )
        drawRect(
            color = Color(0xFF2A2A2A),
            topLeft = Offset(0f, h - margin * 0.6f),
            size = androidx.compose.ui.geometry.Size(w, margin * 0.6f)
        )

        val windowW = w - margin * 2f
        val windowH = h - margin * 2f
        val glassLeft = margin
        val glassTop = margin

        val rainCount = 80
        for (i in 0 until rainCount) {
            val rx = glassLeft + ((rainOffset * windowW * 2f + i * 53f) % windowW)
            val ry = glassTop + ((rainOffset * windowH * 4f + i * 37f) % windowH)
            val streakLen = 15f + (i % 10) * 5f
            val alpha = 0.1f + (i % 6) * 0.03f
            drawLine(
                color = Color(0xFF8AB0D0).copy(alpha = alpha.coerceIn(0f, 0.3f)),
                start = Offset(rx, ry),
                end = Offset(rx - 3f, ry + streakLen),
                strokeWidth = 0.8f
            )
        }

        for (i in 0 until 15) {
            val rx = glassLeft + ((rainOffset * windowW * 1.5f + i * 131f) % windowW)
            val ry = glassTop + ((rainOffset * windowH * 3f + i * 97f) % windowH)
            drawLine(
                color = Color(0xFF6A8AAA).copy(alpha = 0.08f),
                start = Offset(rx, ry),
                end = Offset(rx, ry + 8f),
                strokeWidth = 2.5f
            )
        }

        val fogAlpha = 0.04f + fogBreath * 0.08f
        drawRect(
            color = Color.White.copy(alpha = fogAlpha),
            topLeft = Offset(glassLeft, glassTop),
            size = androidx.compose.ui.geometry.Size(windowW, windowH * 0.3f)
        )

        val flashValue = kotlin.math.sin(lightningFlash * Math.PI.toFloat() * 6f)
        val isFlash = lightningFlash > 0.45f && lightningFlash < 0.55f && flashValue > 0.8f
        if (isFlash) {
            drawRect(
                color = flashColor.copy(alpha = 0.15f * (1f - kotlin.math.abs(flashValue - 1f))),
                topLeft = Offset(glassLeft, glassTop),
                size = androidx.compose.ui.geometry.Size(windowW, windowH)
            )
        }

        val cloudY = h * 0.05f
        for (i in 0 until 6) {
            val cx = w * (0.05f + i * 0.18f) + (rainOffset * 30f) % (w * 0.1f)
            drawCircle(
                color = stormCloud.copy(alpha = 0.3f + (i % 3) * 0.08f),
                radius = w * 0.1f + (i % 4) * 10f,
                center = Offset(cx, cloudY)
            )
        }

        drawRect(
            color = Color.Black.copy(alpha = 0.3f),
            topLeft = Offset.Zero,
            size = size
        )
    }
}
