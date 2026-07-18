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
import androidx.compose.ui.graphics.drawscope.DrawScope

@Composable
fun PremiumFallbackScene(
    accentColor: Color,
    energy: Float,
    modifier: Modifier = Modifier
) {
    val transition = rememberInfiniteTransition(label = "premiumFallback")
    val floatOffset by transition.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(12000, easing = LinearEasing), RepeatMode.Reverse),
        label = "floatOffset"
    )
    val pulse by transition.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(4000, easing = LinearEasing), RepeatMode.Reverse),
        label = "pulse"
    )
    val driftX by transition.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(20000, easing = LinearEasing), RepeatMode.Restart),
        label = "driftX"
    )

    val bgTop = Color(0xFF1A1518)
    val bgBottom = Color(0xFF0F0C10)
    val particleColor = Color(0xFFFFE8C0)
    val accentGlow = accentColor.copy(alpha = 0.08f)

    Canvas(modifier = modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height

        drawRect(color = Color(0xFF181418), topLeft = Offset.Zero, size = size)

        drawRect(
            color = bgTop.copy(alpha = 0.6f),
            topLeft = Offset.Zero,
            size = androidx.compose.ui.geometry.Size(w, h * 0.4f)
        )

        val horizonY = h * 0.65f
        drawRect(
            color = Color(0xFF1A1518),
            topLeft = Offset(0f, horizonY),
            size = androidx.compose.ui.geometry.Size(w, h - horizonY)
        )

        val glowCenterX = w * 0.5f + kotlin.math.sin(floatOffset * Math.PI.toFloat() * 2f) * w * 0.1f
        val glowY = horizonY - h * 0.05f
        val glowRadius = w * 0.3f + pulse * w * 0.05f
        val glowAlpha = 0.04f + pulse * 0.06f + energy * 0.04f
        drawCircle(
            color = accentColor.copy(alpha = glowAlpha.coerceIn(0f, 0.18f)),
            radius = glowRadius,
            center = Offset(glowCenterX, glowY)
        )
        drawCircle(
            color = accentColor.copy(alpha = glowAlpha * 0.5f),
            radius = glowRadius * 0.5f,
            center = Offset(glowCenterX, glowY)
        )

        for (i in 0 until 12) {
            val angle = i.toFloat() / 12f * Math.PI.toFloat() * 2f + floatOffset * Math.PI.toFloat() * 2f
            val dist = glowRadius * 0.3f + kotlin.math.sin(floatOffset * Math.PI.toFloat() * 3f + i * 0.5f) * 15f
            val px = glowCenterX + kotlin.math.cos(angle) * dist
            val py = glowY + kotlin.math.sin(angle) * dist * 0.5f
            drawCircle(
                color = particleColor.copy(alpha = 0.06f + pulse * 0.04f),
                radius = 3f + (i % 3) * 2f,
                center = Offset(px, py)
            )
        }

        for (i in 0 until 30) {
            val px = ((driftX * w * 2f + i * 83f) % (w + 30f)) - 15f
            val py = ((driftX * h * 1.5f + i * 47f) % h)
            val size = 1.5f + (i % 5) * 1f
            val alpha = 0.04f + (i % 4) * 0.03f + pulse * 0.04f
            drawCircle(
                color = particleColor.copy(alpha = alpha.coerceIn(0f, 0.2f)),
                radius = size,
                center = Offset(px, py)
            )
        }

        for (i in 0 until 3) {
            val lightX = w * (0.2f + i * 0.3f) + kotlin.math.sin(floatOffset * Math.PI.toFloat() * 1.5f + i) * 20f
            drawRect(
                color = Color(0xFFFFF5E0).copy(alpha = 0.015f + pulse * 0.015f),
                topLeft = Offset(lightX, 0f),
                size = androidx.compose.ui.geometry.Size(2f, horizonY)
            )
        }

        drawRect(
            color = Color.Black.copy(alpha = 0.3f),
            topLeft = Offset.Zero,
            size = size
        )
    }
}
