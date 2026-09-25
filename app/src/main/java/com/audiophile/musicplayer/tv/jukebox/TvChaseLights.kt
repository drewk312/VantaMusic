package com.audiophile.musicplayer.tv.jukebox

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * The jukebox's voice — a running comet-chase row of tungsten bulbs framing
 * the cabinet glass, exactly like a golden-age Wurlitzer arch frame. The bulbs
 * march continuously around the rounded-rect perimeter; the leading bulb runs
 * hotter and trailing bulbs fade through amber, so the light visibly chases
 * even in a static screenshot. Fasts up while playing, slows to a calm crawl
 * when paused — but the machine is never dark.
 */
@Composable
fun TvChaseLights(
    isPlaying: Boolean,
    modifier: Modifier = Modifier,
    bulbCount: Int = 52
) {
    val transition = rememberInfiniteTransition(label = "chaseLights")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = if (isPlaying) 2600 else 9000,
                easing = LinearEasing
            ),
            repeatMode = RepeatMode.Restart
        ),
        label = "chasePhase"
    )

    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        if (w <= 4.dp.toPx() || h <= 4.dp.toPx()) return@Canvas
        val r = 26.dp.toPx() // cabinet corner radius
        val a = (w - 2f * r).coerceAtLeast(1f)
        val b = (h - 2f * r).coerceAtLeast(1f)
        val corner = r * (PI.toFloat() / 2f)
        val perimeter = 2f * a + 2f * b + 4f * corner

        val bulbRadius = 2.0.dp.toPx()
        val step = perimeter / bulbCount
        val head = (phase * perimeter) % perimeter * 1f
        val span = perimeter * 0.10f

        for (i in 0 until bulbCount) {
            val dist = (i * step * 1f)
            val d = dist % perimeter
            val back = (head - d)
            val gap = ((back % perimeter) + perimeter) % perimeter
            val glowK = (1f - gap / span).coerceIn(0f, 1f)
            if (glowK <= 0.015f) continue

            val p = roundedRectPoint(w, h, r, a, b, corner, d)
            // One layer of soft bloom + a hot core = an LED lamp, not a dot
            drawCircle(
                color = Color(0xFFFFC66B).copy(alpha = 0.18f * glowK),
                radius = bulbRadius * 3.2f,
                center = p
            )
            drawCircle(
                color = Color(0xFFF5A623).copy(alpha = 0.85f * glowK),
                radius = bulbRadius * 1.5f,
                center = p
            )
            drawCircle(
                color = Color(0xFFFFF3D6).copy(alpha = 0.95f * glowK),
                radius = bulbRadius * 0.7f,
                center = p
            )
        }
    }
}

private fun roundedRectPoint(
    w: Float,
    h: Float,
    r: Float,
    a: Float,
    b: Float,
    corner: Float,
    d: Float
): Offset {
    var s = d
    if (s < a) return Offset(r + s, 0f)
    s -= a
    if (s < corner) {
        val t = (s / corner) * (PI.toFloat() / 2f)
        return Offset(w - r + r * cos(t), r - r * sin(t))
    }
    s -= corner
    if (s < b) return Offset(w, r + s)
    s -= b
    if (s < corner) {
        val t = (s / corner) * (PI.toFloat() / 2f)
        return Offset(w - r + r * cos(t), h - r + r * sin(t))
    }
    s -= corner
    if (s < a) return Offset(w - r - s, h)
    s -= a
    if (s < corner) {
        val t = (s / corner) * (PI.toFloat() / 2f)
        return Offset(r + r * cos(t), h - r + r * sin(t))
    }
    s -= corner
    if (s < b) return Offset(0f, h - r - s)
    s -= b
    val t = (s / corner) * (PI.toFloat() / 2f)
    return Offset(r + r * cos(PI.toFloat() + t), r + r * sin(PI.toFloat() + t))
}