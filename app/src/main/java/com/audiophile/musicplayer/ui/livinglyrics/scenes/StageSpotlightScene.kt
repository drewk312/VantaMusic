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
fun StageSpotlightScene(
    accentColor: Color,
    energy: Float,
    modifier: Modifier = Modifier
) {
    val transition = rememberInfiniteTransition(label = "stageSpotlight")
    val spotlightSway by transition.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(12000, easing = LinearEasing), RepeatMode.Reverse),
        label = "spotlightSway"
    )
    val dustDrift by transition.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(10000, easing = LinearEasing), RepeatMode.Restart),
        label = "dustDrift"
    )
    val beamPulse by transition.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(2000, easing = LinearEasing), RepeatMode.Reverse),
        label = "beamPulse"
    )

    val stageColor = Color(0xFF1A1510)
    val beamColor = Color(0xFFFFF5E0)
    val edgeLight = Color(0xFFFFD080)

    Canvas(modifier = modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height

        drawRect(color = stageColor, topLeft = Offset.Zero, size = size)

        val stageFloor = h * 0.75f
        drawRect(
            color = Color(0xFF221E1A),
            topLeft = Offset(0f, stageFloor),
            size = androidx.compose.ui.geometry.Size(w, h - stageFloor)
        )
        drawRect(
            color = Color(0xFF2A2520),
            topLeft = Offset(0f, stageFloor),
            size = androidx.compose.ui.geometry.Size(w, 3f)
        )

        val beamX = w * 0.5f + kotlin.math.sin(spotlightSway * Math.PI.toFloat() * 2f) * w * 0.08f
        val beamTop = h * 0.05f
        val beamBottom = stageFloor

        val beamAlpha = 0.08f + beamPulse * 0.06f
        val beamWidthTop = w * 0.02f
        val beamWidthBottom = w * 0.25f

        val steps = 30
        for (i in 0 until steps) {
            val t = i.toFloat() / steps
            val bw = beamWidthTop + (beamWidthBottom - beamWidthTop) * t
            val bx = beamX - bw / 2f
            val by = beamTop + (beamBottom - beamTop) * t
            val bh = (beamBottom - beamTop) / steps + 2f
            val alpha = beamColor.copy(alpha = beamAlpha * (1f - t * 0.5f))
            drawRect(
                color = alpha,
                topLeft = Offset(bx, by),
                size = androidx.compose.ui.geometry.Size(bw, bh)
            )
        }

        val groundBeamWidth = beamWidthBottom * 0.8f
        val groundBeamX = beamX - groundBeamWidth / 2f
        drawRect(
            color = beamColor.copy(alpha = 0.04f + beamPulse * 0.04f),
            topLeft = Offset(groundBeamX - 10f, stageFloor),
            size = androidx.compose.ui.geometry.Size(groundBeamWidth + 20f, h - stageFloor)
        )

        for (i in 0 until 6) {
            val cx = beamX + kotlin.math.cos(i * Math.PI.toFloat() / 3f + spotlightSway * Math.PI.toFloat()) * beamWidthBottom * 0.3f
            val cy = beamBottom - 10f
            drawCircle(
                color = edgeLight.copy(alpha = 0.03f + beamPulse * 0.03f),
                radius = 8f + (i % 3) * 4f,
                center = Offset(cx, cy)
            )
        }

        for (i in 0 until 25) {
            val dx = beamX + ((dustDrift * beamWidthBottom * 2f + i * 67f) % beamWidthBottom) - beamWidthBottom / 2f
            val dy = beamTop + ((dustDrift * (beamBottom - beamTop) + i * 43f) % (beamBottom - beamTop))
            val size = 1.5f + (i % 4) * 1.5f
            val alpha = 0.05f + (i % 5) * 0.03f + beamPulse * 0.04f
            drawCircle(
                color = beamColor.copy(alpha = alpha.coerceIn(0f, 0.3f)),
                radius = size,
                center = Offset(dx, dy)
            )
        }

        for (i in 0 until 20) {
            val ringY = beamTop + (i.toFloat() / 20f) * (beamBottom - beamTop)
            val ringAlpha = 0.01f + (1f - i.toFloat() / 20f) * 0.03f
            val ringW = beamWidthTop + (beamWidthBottom - beamWidthTop) * (i.toFloat() / 20f)
            drawRect(
                color = beamColor.copy(alpha = ringAlpha),
                topLeft = Offset(beamX - ringW / 2f, ringY),
                size = androidx.compose.ui.geometry.Size(ringW, 1f)
            )
        }

        drawRect(
            color = Color.Black.copy(alpha = 0.25f),
            topLeft = Offset.Zero,
            size = size
        )
    }
}
