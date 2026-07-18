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
fun MovingTrainScene(
    accentColor: Color,
    energy: Float,
    modifier: Modifier = Modifier
) {
    val transition = rememberInfiniteTransition(label = "movingTrain")
    val trainPos by transition.animateFloat(
        initialValue = -0.3f, targetValue = 1.3f,
        animationSpec = infiniteRepeatable(tween(14000, easing = LinearEasing), RepeatMode.Restart),
        label = "trainPos"
    )
    val polePass by transition.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(3000, easing = LinearEasing), RepeatMode.Restart),
        label = "polePass"
    )

    val skyColor = Color(0xFF4A5A6A)
    val trainColor = Color(0xFF3A2A25)
    val trainAccent = Color(0xFF6A4A3A)
    val windowColor = Color(0xFF8AB0C8)
    val railColor = Color(0xFF4A4A4A)

    Canvas(modifier = modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height

        drawRect(color = skyColor, topLeft = Offset.Zero, size = size)
        drawRect(
            color = Color(0xFF5A6A7A),
            topLeft = Offset(0f, 0f),
            size = androidx.compose.ui.geometry.Size(w, h * 0.4f)
        )
        drawRect(
            color = Color(0xFF3A4A5A),
            topLeft = Offset(0f, h * 0.4f),
            size = androidx.compose.ui.geometry.Size(w, h * 0.08f)
        )

        val trainBottom = h * 0.5f
        val trainHeight = h * 0.28f
        val trainWidth = w * 0.45f
        val tx = trainPos * (w + trainWidth) - trainWidth * 0.3f
        val ty = trainBottom - trainHeight

        drawRect(
            color = trainColor,
            topLeft = Offset(tx, ty),
            size = androidx.compose.ui.geometry.Size(trainWidth, trainHeight)
        )
        drawRect(
            color = trainAccent,
            topLeft = Offset(tx, ty),
            size = androidx.compose.ui.geometry.Size(trainWidth, trainHeight * 0.08f)
        )
        drawRect(
            color = Color(0xFF2A1A15),
            topLeft = Offset(tx, ty + trainHeight - trainHeight * 0.08f),
            size = androidx.compose.ui.geometry.Size(trainWidth, trainHeight * 0.08f)
        )

        val windowW = trainWidth * 0.12f
        val windowH = trainHeight * 0.35f
        val windowY = ty + trainHeight * 0.18f
        val windowGap = trainWidth * 0.06f
        val windowStart = tx + trainWidth * 0.08f
        val windowCount = ((trainWidth - trainWidth * 0.16f) / (windowW + windowGap)).toInt().coerceAtMost(8)
        for (i in 0 until windowCount) {
            val wx = windowStart + i * (windowW + windowGap)
            drawRect(
                color = windowColor.copy(alpha = 0.4f + (i % 3) * 0.1f),
                topLeft = Offset(wx, windowY),
                size = androidx.compose.ui.geometry.Size(windowW, windowH)
            )
            drawRect(
                color = Color(0xFF1A1A2A).copy(alpha = 0.5f),
                topLeft = Offset(wx + 2f, windowY + 2f),
                size = androidx.compose.ui.geometry.Size(windowW * 0.3f, windowH - 4f)
            )
        }

        val wheelRadius = trainHeight * 0.06f
        val wheelY = ty + trainHeight - wheelRadius
        val wheelCount = ((trainWidth - trainWidth * 0.1f) / (trainHeight * 0.2f)).toInt().coerceAtMost(6)
        for (i in 0 until wheelCount) {
            val wx = tx + trainWidth * 0.05f + i * trainHeight * 0.2f
            drawCircle(color = Color(0xFF1A1A1A), radius = wheelRadius, center = Offset(wx, wheelY))
            drawCircle(color = Color(0xFF3A3A3A), radius = wheelRadius * 0.6f, center = Offset(wx, wheelY))
        }

        val groundY = trainBottom + 10f
        drawRect(
            color = Color(0xFF3A352A),
            topLeft = Offset(0f, groundY),
            size = androidx.compose.ui.geometry.Size(w, h - groundY)
        )
        drawRect(
            color = railColor,
            topLeft = Offset(0f, groundY - 2f),
            size = androidx.compose.ui.geometry.Size(w, 4f)
        )

        val poleX = ((polePass * w * 2f) % (w + 60f)) - 30f
        drawRect(
            color = Color(0xFF3A3A3A),
            topLeft = Offset(poleX, h * 0.25f),
            size = androidx.compose.ui.geometry.Size(6f, groundY - h * 0.25f)
        )
        val wireY = h * 0.25f
        for (i in 0 until 3) {
            drawLine(
                color = Color(0xFF4A4A4A).copy(alpha = 0.3f),
                start = Offset(poleX + 3f, wireY + i * 8f),
                end = Offset(poleX + 80f, wireY + i * 8f + 12f),
                strokeWidth = 1f
            )
        }

        drawRect(
            color = Color.Black.copy(alpha = 0.3f),
            topLeft = Offset.Zero,
            size = size
        )
    }
}
