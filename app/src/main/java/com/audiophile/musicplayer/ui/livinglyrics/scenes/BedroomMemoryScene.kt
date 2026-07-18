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
fun BedroomMemoryScene(
    accentColor: Color,
    energy: Float,
    modifier: Modifier = Modifier
) {
    val transition = rememberInfiniteTransition(label = "bedroomMemory")
    val curtainSway by transition.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(8000, easing = LinearEasing), RepeatMode.Reverse),
        label = "curtainSway"
    )
    val lampFlicker by transition.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(3000, easing = LinearEasing), RepeatMode.Reverse),
        label = "lampFlicker"
    )
    val shadowDrift by transition.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(12000, easing = LinearEasing), RepeatMode.Reverse),
        label = "shadowDrift"
    )

    val wallColor = Color(0xFF2A2528)
    val floorColor = Color(0xFF1A1618)
    val warmLight = Color(0xFFFFD8A0)

    Canvas(modifier = modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height

        drawRect(color = wallColor, topLeft = Offset.Zero, size = size)

        val floorY = h * 0.78f
        drawRect(
            color = floorColor,
            topLeft = Offset(0f, floorY),
            size = androidx.compose.ui.geometry.Size(w, h - floorY)
        )
        drawRect(
            color = Color(0xFF221E20),
            topLeft = Offset(0f, floorY),
            size = androidx.compose.ui.geometry.Size(w, 3f)
        )

        val windowLeft = w * 0.1f
        val windowRight = w * 0.55f
        val windowTop = h * 0.08f
        val windowBottom = h * 0.55f
        drawRect(
            color = Color(0xFF1A2A3A),
            topLeft = Offset(windowLeft, windowTop),
            size = androidx.compose.ui.geometry.Size(windowRight - windowLeft, windowBottom - windowTop)
        )

        val sashX = windowLeft + (windowRight - windowLeft) * 0.5f - 2f
        drawRect(
            color = Color(0xFF3A3538),
            topLeft = Offset(sashX, windowTop),
            size = androidx.compose.ui.geometry.Size(4f, windowBottom - windowTop)
        )
        val sashY = windowTop + (windowBottom - windowTop) * 0.5f - 2f
        drawRect(
            color = Color(0xFF3A3538),
            topLeft = Offset(windowLeft, sashY),
            size = androidx.compose.ui.geometry.Size(windowRight - windowLeft, 4f)
        )

        val curtainSwing = curtainSway * 8f
        val curtainColor = Color(0xFF3A3035)
        val segments = 12
        for (i in 0 until segments) {
            val t = i.toFloat() / segments
            val cx = windowLeft + (windowRight - windowLeft) * t
            val sway = kotlin.math.sin(t * Math.PI.toFloat() + curtainSway * Math.PI.toFloat() * 2f) * curtainSwing
            drawRect(
                color = curtainColor.copy(alpha = 0.5f + 0.3f * (1f - kotlin.math.abs(t - 0.5f) * 2f)),
                topLeft = Offset(cx + sway, windowTop + 10f),
                size = androidx.compose.ui.geometry.Size(6f, windowBottom - windowTop - 20f)
            )
        }

        val lampX = w * 0.8f
        val lampY = h * 0.45f
        val lampGlow = 0.3f + lampFlicker * 0.15f
        drawCircle(
            color = warmLight.copy(alpha = lampGlow * 0.6f),
            radius = w * 0.15f,
            center = Offset(lampX, lampY)
        )
        drawCircle(
            color = warmLight.copy(alpha = lampGlow * 0.15f),
            radius = w * 0.3f,
            center = Offset(lampX, lampY)
        )

        drawRect(
            color = Color(0xFF3A3035),
            topLeft = Offset(lampX - 4f, lampY - w * 0.06f),
            size = androidx.compose.ui.geometry.Size(8f, w * 0.06f)
        )

        val shadowOffset = shadowDrift * 15f
        drawRect(
            color = Color.Black.copy(alpha = 0.2f),
            topLeft = Offset(lampX - w * 0.12f + shadowOffset, lampY + 10f),
            size = androidx.compose.ui.geometry.Size(w * 0.18f, h * 0.25f)
        )

        val shelfY = h * 0.25f
        drawRect(
            color = Color(0xFF3A3538),
            topLeft = Offset(w * 0.65f, shelfY),
            size = androidx.compose.ui.geometry.Size(w * 0.3f, 4f)
        )

        for (i in 0 until 4) {
            val bookX = w * 0.67f + i * (w * 0.06f + 4f)
            val bookH = 20f + (i % 3) * 10f
            drawRect(
                color = Color(0xFF4A3A3A + (i * 0x101010)).copy(alpha = 0.5f),
                topLeft = Offset(bookX, shelfY - bookH),
                size = androidx.compose.ui.geometry.Size(w * 0.055f, bookH)
            )
        }

        drawRect(
            color = Color.Black.copy(alpha = 0.3f),
            topLeft = Offset.Zero,
            size = size
        )
    }
}
