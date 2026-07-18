package com.audiophile.musicplayer.ui.livinglyrics

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import kotlin.math.PI
import kotlin.math.sin
import kotlin.math.cos

/**
 * Reusable Canvas-level drawing functions. Each motif draws one visual element
 * into a [DrawScope]. SceneComposer calls the appropriate motifs per-song
 * based on the ScenePlan's motif list.
 *
 * All motifs accept animation time (0..1 cyclical), palette colors, and
 * an intensity multiplier so the same motif looks different across songs.
 */
object VisualMotifs {

    // ═══════════════════════════════════════════════════════════════
    // STRUCTURE / ENVIRONMENT
    // ═══════════════════════════════════════════════════════════════

    fun DrawScope.drawRoadLines(
        time: Float, palette: ScenePalette, intensity: Float,
        horizonY: Float = size.height * 0.4f
    ) {
        val w = size.width
        val h = size.height
        val road = Path().apply {
            moveTo(w * 0.46f, horizonY)
            lineTo(w * 0.54f, horizonY)
            lineTo(w * 0.88f, h)
            lineTo(w * 0.12f, h)
            close()
        }
        drawPath(
            path = road,
            brush = Brush.verticalGradient(
                colors = listOf(
                    palette.background.copy(alpha = 0.24f),
                    Color(0xFF11120F).copy(alpha = 0.78f),
                    Color(0xFF050505).copy(alpha = 0.94f)
                ),
                startY = horizonY,
                endY = h
            )
        )
        drawLine(palette.highlight.copy(alpha = 0.22f), Offset(w * 0.46f, horizonY), Offset(w * 0.12f, h), 1.5f)
        drawLine(palette.highlight.copy(alpha = 0.22f), Offset(w * 0.54f, horizonY), Offset(w * 0.88f, h), 1.5f)

        val lineColor = palette.highlight.copy(alpha = 0.75f)
        val dashH = (h - horizonY) * 0.06f
        val totalDash = dashH * 2.5f
        val scrollOffset = (time * (h - horizonY) * 0.45f) % totalDash
        var y = horizonY + scrollOffset
        while (y < h) {
            val depth = ((y - horizonY) / (h - horizonY)).coerceIn(0f, 1f)
            val dashW = 2f + depth * 7f
            drawRect(
                color = lineColor.copy(alpha = (0.18f + depth * 0.62f) * intensity.coerceAtLeast(0.5f)),
                topLeft = Offset(w / 2f - dashW / 2f, y),
                size = Size(dashW, dashH * (0.45f + depth))
            )
            y += totalDash
        }
    }

    fun DrawScope.drawSteelBeams(palette: ScenePalette, intensity: Float) {
        val w = size.width
        val h = size.height
        val beamColor = palette.primary
        val beamWidth = w * 0.03f
        val positions = listOf(0.08f, 0.25f, 0.75f, 0.92f)
        positions.forEach { xRatio ->
            drawRect(
                color = beamColor,
                topLeft = Offset(w * xRatio - beamWidth / 2f, 0f),
                size = Size(beamWidth, h)
            )
            drawRect(
                color = Color.White.copy(alpha = 0.06f * intensity),
                topLeft = Offset(w * xRatio - beamWidth / 2f, 0f),
                size = Size(beamWidth * 0.3f, h)
            )
        }
        // Cross beam
        drawRect(
            color = beamColor.copy(alpha = 0.8f),
            topLeft = Offset(0f, h * 0.08f),
            size = Size(w, h * 0.02f)
        )
    }

    fun DrawScope.drawStageTruss(palette: ScenePalette, intensity: Float) {
        val w = size.width
        val h = size.height
        val trussColor = palette.primary.copy(alpha = 0.7f)
        // Horizontal truss bars at top
        drawRect(color = trussColor, topLeft = Offset(0f, h * 0.03f), size = Size(w, 6f))
        drawRect(color = trussColor, topLeft = Offset(0f, h * 0.06f), size = Size(w, 4f))
        // Vertical supports
        for (x in listOf(0.1f, 0.3f, 0.7f, 0.9f)) {
            drawRect(
                color = trussColor,
                topLeft = Offset(w * x - 2f, 0f),
                size = Size(4f, h * 0.1f)
            )
        }
        // Diagonal cross bracing
        for (i in 0 until 4) {
            val sx = w * (0.1f + i * 0.2f)
            val ex = sx + w * 0.2f
            drawLine(trussColor, Offset(sx, h * 0.03f), Offset(ex, h * 0.06f), 1.5f)
            drawLine(trussColor, Offset(ex, h * 0.03f), Offset(sx, h * 0.06f), 1.5f)
        }
    }

    fun DrawScope.drawBuildingSilhouette(
        palette: ScenePalette, time: Float, intensity: Float,
        horizonY: Float = size.height * 0.45f
    ) {
        val w = size.width
        val h = size.height
        val buildings = listOf(
            0.02f to 0.45f, 0.1f to 0.6f, 0.2f to 0.5f, 0.28f to 0.7f,
            0.36f to 0.55f, 0.44f to 0.75f, 0.52f to 0.58f, 0.6f to 0.68f,
            0.68f to 0.5f, 0.76f to 0.62f, 0.84f to 0.48f, 0.92f to 0.55f
        )
        buildings.forEach { (xRatio, heightRatio) ->
            val bh = h * heightRatio * 0.5f
            val bw = w * 0.075f
            drawRect(
                color = palette.background.copy(alpha = 0.95f),
                topLeft = Offset(xRatio * w, horizonY - bh),
                size = Size(bw, bh + (h - horizonY))
            )
            // Windows
            val shimmer = time * 0.5f + 0.5f
            for (row in 0 until (bh / 14f).toInt().coerceAtMost(12)) {
                for (col in 0 until (bw / 9f).toInt().coerceAtMost(4)) {
                    if ((row + col) % 3 != 0) continue
                    val winAlpha = (0.08f + shimmer * 0.08f) * intensity
                    drawRect(
                        color = palette.secondary.copy(alpha = winAlpha),
                        topLeft = Offset(xRatio * w + col * 9f + 2f, horizonY - bh + row * 14f + 2f),
                        size = Size(5f, 8f)
                    )
                }
            }
        }
    }

    fun DrawScope.drawChapelSilhouette(palette: ScenePalette, intensity: Float) {
        val w = size.width
        val h = size.height
        val cx = w * 0.5f
        val baseY = h * 0.5f
        val chapelColor = palette.primary.copy(alpha = 0.4f * intensity)
        // Chapel body
        drawRect(chapelColor, Offset(cx - w * 0.04f, baseY - h * 0.06f), Size(w * 0.08f, h * 0.06f))
        // Steeple
        drawRect(chapelColor, Offset(cx - 3f, baseY - h * 0.12f), Size(6f, h * 0.06f))
        // Cross at top
        drawRect(chapelColor, Offset(cx - 1.5f, baseY - h * 0.15f), Size(3f, h * 0.04f))
        drawRect(chapelColor, Offset(cx - 5f, baseY - h * 0.135f), Size(10f, 2.5f))
    }

    fun DrawScope.drawFencePosts(
        palette: ScenePalette, intensity: Float,
        horizonY: Float = size.height * 0.45f
    ) {
        val w = size.width
        val h = size.height
        for (i in 0 until 6) {
            val t = i / 6f
            val fx = w * (0.08f + t * 0.15f)
            val fy = horizonY + t * (h * 0.3f)
            val fh = 20f + t * 15f
            drawRect(
                color = palette.primary.copy(alpha = (0.5f - t * 0.2f) * intensity),
                topLeft = Offset(fx, fy - fh),
                size = Size(2.5f, fh)
            )
        }
        // Wire between posts
        for (i in 0 until 5) {
            val t1 = i / 6f; val t2 = (i + 1) / 6f
            drawLine(
                color = palette.primary.copy(alpha = 0.3f * intensity),
                start = Offset(w * (0.08f + t1 * 0.15f), horizonY + t1 * h * 0.3f - 10f),
                end = Offset(w * (0.08f + t2 * 0.15f), horizonY + t2 * h * 0.3f - 10f),
                strokeWidth = 0.5f
            )
        }
    }

    fun DrawScope.drawConcreteWall(palette: ScenePalette, intensity: Float) {
        drawRect(color = palette.background, topLeft = Offset.Zero, size = size)
        // Texture cracks
        for (i in 0 until 20) {
            val cx = (i * 113f) % size.width
            val cy = (i * 79f) % (size.height * 0.7f)
            drawLine(Color.Black.copy(alpha = 0.06f * intensity), Offset(cx, cy), Offset(cx + 15f, cy + 8f), 0.5f)
        }
    }

    fun DrawScope.drawBedroomWindow(palette: ScenePalette, time: Float, intensity: Float) {
        val w = size.width; val h = size.height
        val wx = w * 0.6f; val wy = h * 0.15f
        val ww = w * 0.25f; val wh = h * 0.3f
        // Window frame
        drawRect(palette.primary.copy(alpha = 0.6f), Offset(wx, wy), Size(ww, wh))
        // Panes
        drawRect(Color(0xFF2A3A5A).copy(alpha = 0.3f + time * 0.1f), Offset(wx + 3f, wy + 3f), Size(ww / 2f - 5f, wh / 2f - 5f))
        drawRect(Color(0xFF2A3A5A).copy(alpha = 0.3f + time * 0.1f), Offset(wx + ww / 2f + 2f, wy + 3f), Size(ww / 2f - 5f, wh / 2f - 5f))
        // Light shaft through window
        val lightAlpha = 0.04f + time * 0.03f * intensity
        drawRect(palette.accent.copy(alpha = lightAlpha), Offset(wx - 10f, wy + wh), Size(ww + 20f, h * 0.4f))
    }

    // ═══════════════════════════════════════════════════════════════
    // ATMOSPHERE
    // ═══════════════════════════════════════════════════════════════

    fun DrawScope.drawSmoke(time: Float, palette: ScenePalette, intensity: Float, baseY: Float = size.height * 0.78f) {
        for (i in 0 until 12) {
            val bx = (size.width * (i / 12f) + time * size.width * 0.3f) % (size.width * 1.2f) - size.width * 0.1f
            val by = baseY - (time * size.height * 0.15f + i * 17f) % (size.height * 0.3f)
            val alpha = (0.06f + (i % 3) * 0.02f) * intensity
            val radius = 15f + (i % 5) * 8f + time * 5f
            drawCircle(Color(0xFF555555).copy(alpha = alpha.coerceIn(0f, 0.15f)), radius, Offset(bx, by))
        }
    }

    fun DrawScope.drawDustParticles(time: Float, palette: ScenePalette, intensity: Float, count: Int = 20) {
        val w = size.width; val h = size.height
        for (i in 0 until count) {
            val dx = ((time * w * 1.5f + i * 89f) % (w * 1.2f)) - w * 0.1f
            val dy = ((time * h + i * 53f) % (h * 0.8f)) + h * 0.1f
            val alpha = (0.06f + (i % 4) * 0.03f) * intensity
            drawCircle(palette.accent.copy(alpha = alpha.coerceIn(0f, 0.2f)), 2f + (i % 4) * 1.5f, Offset(dx, dy))
        }
    }

    fun DrawScope.drawRainDrops(time: Float, palette: ScenePalette, intensity: Float, count: Int = 60) {
        val w = size.width; val h = size.height
        for (i in 0 until count) {
            val rx = ((time * w * 2f + i * 47f) % (w + 20f)) - 10f
            val ry = ((time * h * 3f + i * 73f) % h)
            val alpha = (0.08f + (i % 5) * 0.03f) * intensity
            drawLine(Color.White.copy(alpha = alpha.coerceIn(0f, 0.2f)), Offset(rx, ry), Offset(rx - 4f, ry + 20f), 0.5f)
        }
    }

    fun DrawScope.drawClouds(time: Float, palette: ScenePalette, intensity: Float) {
        val w = size.width; val h = size.height
        val baseX = (time * w * 1.5f) % (w + 300f) - 150f
        for (i in 0 until 5) {
            val cx = baseX + i * w * 0.22f
            val cy = h * (0.06f + i * 0.04f)
            val cr = w * (0.08f + (i % 3) * 0.03f)
            drawCircle(Color.White.copy(alpha = (0.08f + (i % 3) * 0.02f) * intensity), cr, Offset(cx, cy))
            drawCircle(Color.White.copy(alpha = 0.06f * intensity), cr * 0.7f, Offset(cx + cr * 0.4f, cy - cr * 0.1f))
        }
    }

    fun DrawScope.drawFog(palette: ScenePalette, intensity: Float) {
        drawRect(palette.background.copy(alpha = 0.08f * intensity), Offset.Zero, size)
    }

    // ═══════════════════════════════════════════════════════════════
    // LIGHT
    // ═══════════════════════════════════════════════════════════════

    fun DrawScope.drawHeadlights(
        time: Float, palette: ScenePalette, intensity: Float,
        horizonY: Float = size.height * 0.4f
    ) {
        val w = size.width; val h = size.height
        val shimmer = 0.75f + sin(time * PI.toFloat() * 2f) * 0.18f
        for (beamCenterX in listOf(0.37f, 0.63f)) {
            val source = Offset(w * beamCenterX, horizonY + h * 0.04f)
            val cone = Path().apply {
                moveTo(source.x - w * 0.018f, source.y)
                lineTo(source.x + w * 0.018f, source.y)
                lineTo(source.x + w * 0.25f, h)
                lineTo(source.x - w * 0.25f, h)
                close()
            }
            drawPath(
                path = cone,
                brush = Brush.verticalGradient(
                    colors = listOf(
                        palette.secondary.copy(alpha = 0.18f * intensity * shimmer),
                        palette.secondary.copy(alpha = 0.06f * intensity),
                        Color.Transparent
                    ),
                    startY = source.y,
                    endY = h
                )
            )
            drawCircle(palette.accent.copy(alpha = 0.26f * intensity), w * 0.035f, source)
            drawCircle(Color.White.copy(alpha = 0.42f * intensity), w * 0.01f, source)
        }
    }

    fun DrawScope.drawNeonSign(
        time: Float, palette: ScenePalette, intensity: Float,
        x: Float = size.width * 0.82f, y: Float = size.height * 0.45f
    ) {
        val alpha = 0.3f + time * 0.3f
        drawCircle(palette.accent.copy(alpha = alpha * 0.15f * intensity), size.width * 0.06f, Offset(x, y))
        drawCircle(palette.accent.copy(alpha = alpha * 0.5f * intensity), size.width * 0.015f, Offset(x, y))
    }

    fun DrawScope.drawSpotlightBeam(
        time: Float, palette: ScenePalette, intensity: Float,
        beamX: Float = size.width * 0.5f, floorY: Float = size.height * 0.75f
    ) {
        val w = size.width; val h = size.height
        val bx = beamX + sin(time * PI.toFloat() * 2f) * w * 0.08f
        val top = h * 0.04f
        val beam = Path().apply {
            moveTo(bx - w * 0.035f, top)
            lineTo(bx + w * 0.035f, top)
            lineTo(bx + w * 0.24f, floorY)
            lineTo(bx - w * 0.24f, floorY)
            close()
        }
        drawPath(
            path = beam,
            brush = Brush.verticalGradient(
                colors = listOf(
                    palette.accent.copy(alpha = (0.16f + time * 0.05f) * intensity),
                    palette.accent.copy(alpha = 0.06f * intensity),
                    Color.Transparent
                ),
                startY = top,
                endY = floorY
            )
        )
        drawCircle(palette.accent.copy(alpha = 0.55f * intensity), 7f, Offset(bx, top))
    }

    fun DrawScope.drawStrobeFlash(time: Float, intensity: Float) {
        val active = time < 0.15f && intensity > 0.5f
        if (active) {
            drawRect(Color.White.copy(alpha = (0.06f + intensity * 0.12f).coerceAtMost(0.2f)), Offset.Zero, size)
        }
    }

    fun DrawScope.drawSunHorizon(
        time: Float, palette: ScenePalette, intensity: Float,
        horizonY: Float = size.height * 0.42f
    ) {
        val w = size.width
        val sunX = w * 0.35f
        drawCircle(palette.secondary.copy(alpha = 0.08f + time * 0.04f), w * 0.15f, Offset(sunX, horizonY))
        drawCircle(palette.accent.copy(alpha = 0.2f + time * 0.1f), w * 0.04f, Offset(sunX, horizonY))
        // Horizon glow
        drawRect(palette.secondary.copy(alpha = 0.04f + intensity * 0.04f), Offset(0f, horizonY - size.height * 0.04f), Size(w, size.height * 0.08f))
    }

    fun DrawScope.drawWarmLamp(time: Float, palette: ScenePalette, intensity: Float) {
        val w = size.width; val h = size.height
        val lampX = w * 0.3f; val lampY = h * 0.2f
        drawCircle(palette.accent.copy(alpha = (0.1f + time * 0.05f) * intensity), w * 0.12f, Offset(lampX, lampY))
        drawCircle(palette.accent.copy(alpha = (0.3f + time * 0.1f) * intensity), 5f, Offset(lampX, lampY))
    }

    fun DrawScope.drawLightningFlash(time: Float, intensity: Float) {
        if (time > 0.92f && intensity > 0.4f) {
            drawRect(Color.White.copy(alpha = 0.12f * intensity), Offset.Zero, size)
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // OBJECTS / DETAIL
    // ═══════════════════════════════════════════════════════════════

    fun DrawScope.drawSparks(time: Float, palette: ScenePalette, intensity: Float, count: Int = 20) {
        val w = size.width; val h = size.height
        for (i in 0 until count) {
            val sx = ((time * w * 2f + i * 97f) % (w * 1.2f)) - w * 0.1f
            val sy = ((time * h * 1.5f + i * 63f) % (h * 0.6f)) + h * 0.1f
            val alpha = (0.3f + (i % 4) * 0.15f) * intensity
            val sparkColor = if (i % 3 == 0) palette.secondary else palette.highlight
            drawCircle(sparkColor.copy(alpha = alpha.coerceIn(0f, 0.8f)), 1.5f + (i % 3), Offset(sx, sy))
        }
    }

    fun DrawScope.drawStars(palette: ScenePalette, intensity: Float, count: Int = 30) {
        val w = size.width; val h = size.height
        for (i in 0 until count) {
            val sx = (i * 137f) % w
            val sy = (i * 73f) % (h * 0.35f)
            val alpha = (0.15f + (i % 5) * 0.08f) * intensity
            drawCircle(Color.White.copy(alpha = alpha), 1f + (i % 3) * 0.5f, Offset(sx, sy))
        }
    }

    fun DrawScope.drawFireflies(time: Float, palette: ScenePalette, intensity: Float, count: Int = 10) {
        val w = size.width; val h = size.height
        for (i in 0 until count) {
            val fx = ((time * w + i * 107f) % (w * 0.8f)) + w * 0.1f
            val fy = h * 0.5f + ((time * h * 0.3f + i * 43f) % (h * 0.25f))
            val alpha = (0.2f + sin(time * PI.toFloat() * 4f + i) * 0.15f).coerceIn(0f, 0.5f) * intensity
            drawCircle(palette.accent.copy(alpha = alpha * 0.4f), 4f, Offset(fx, fy))
            drawCircle(palette.accent.copy(alpha = alpha), 1.5f, Offset(fx, fy))
        }
    }

    fun DrawScope.drawCrowdSilhouette(palette: ScenePalette, intensity: Float, floorY: Float = size.height * 0.82f) {
        val w = size.width
        for (i in 0 until 20) {
            val cx = w * (i / 20f) + 5f
            val headY = floorY - 8f - (i % 3) * 4f
            drawCircle(palette.background.copy(alpha = 0.6f * intensity), 4f + (i % 2) * 2f, Offset(cx, headY))
            drawRect(palette.background.copy(alpha = 0.5f * intensity), Offset(cx - 3f, headY + 3f), Size(6f, floorY - headY))
        }
    }

    fun DrawScope.drawGrassWisps(time: Float, palette: ScenePalette, intensity: Float, horizonY: Float = size.height * 0.45f) {
        val w = size.width; val h = size.height
        for (i in 0 until 30) {
            val gx = (i * 37f) % w
            val gy = horizonY + (i * 23f) % (h * 0.25f)
            val sway = sin(time * PI.toFloat() * 2f + i) * 4f
            drawLine(
                palette.highlight.copy(alpha = 0.3f * intensity),
                Offset(gx, gy + 12f), Offset(gx + sway, gy - 5f), 1.5f
            )
        }
    }

    fun DrawScope.drawWaterReflection(time: Float, palette: ScenePalette, intensity: Float, surfaceY: Float = size.height * 0.5f) {
        val w = size.width; val h = size.height
        for (i in 0 until 8) {
            val rx = w * (0.1f + i * 0.12f) + sin(time * PI.toFloat() * 2f + i) * 3f
            val ry = surfaceY + h * 0.05f + i * 8f
            drawRect(palette.accent.copy(alpha = (0.03f + time * 0.04f) * intensity), Offset(rx - 1f, ry), Size(3f, h * 0.12f))
        }
    }

    fun DrawScope.drawWetStreetReflection(time: Float, palette: ScenePalette, intensity: Float, streetY: Float = size.height * 0.72f) {
        val w = size.width; val h = size.height
        val colors = listOf(palette.secondary, palette.accent, palette.highlight)
        for (i in 0 until 12) {
            val rx = w * (i / 12f) + sin(time * PI.toFloat() + i) * 2f
            drawRect(
                colors[i % colors.size].copy(alpha = (0.04f + time * 0.03f) * intensity),
                Offset(rx, streetY + 3f), Size(w * 0.06f, h * 0.08f)
            )
        }
    }

    fun DrawScope.drawCarStreaks(time: Float, palette: ScenePalette, intensity: Float, streetY: Float = size.height * 0.8f) {
        val w = size.width
        val streakX = (time * w * 1.5f) % (w + 100f) - 50f
        drawRect(Color.White.copy(alpha = 0.15f * intensity), Offset(streakX, streetY), Size(w * 0.12f, 2f))
        drawRect(Color.Red.copy(alpha = 0.1f * intensity), Offset(streakX + w * 0.15f, streetY + 8f), Size(w * 0.08f, 2f))
    }

    fun DrawScope.drawMicrophoneStand(palette: ScenePalette, intensity: Float, floorY: Float = size.height * 0.75f) {
        val cx = size.width * 0.5f
        val topY = floorY - size.height * 0.25f
        drawRect(Color(0xFF333333).copy(alpha = intensity), Offset(cx - 1.5f, topY), Size(3f, size.height * 0.25f))
        drawCircle(Color(0xFF444444).copy(alpha = intensity), 6f, Offset(cx, topY))
    }

    fun DrawScope.drawGraffitiMarks(palette: ScenePalette, intensity: Float) {
        val w = size.width; val h = size.height
        val colors = listOf(Color.White, palette.accent, palette.secondary)
        for (i in 0 until 8) {
            val gx = w * (0.05f + (i * 0.12f) % 0.9f)
            val gy = h * (0.4f + (i * 0.07f) % 0.3f)
            drawLine(colors[i % colors.size].copy(alpha = 0.1f * intensity), Offset(gx, gy), Offset(gx + 20f, gy - 10f), 2f)
        }
    }

    fun DrawScope.drawTornPosters(palette: ScenePalette, intensity: Float) {
        val w = size.width; val h = size.height
        val posterData = listOf(
            Triple(w * 0.1f, h * 0.15f, palette.accent),
            Triple(w * 0.55f, h * 0.2f, palette.secondary),
            Triple(w * 0.8f, h * 0.12f, palette.highlight)
        )
        posterData.forEach { (px, py, color) ->
            val pw = w * 0.12f; val ph = h * 0.15f
            drawRect(color.copy(alpha = 0.4f * intensity), Offset(px, py), Size(pw, ph))
            drawRect(palette.background, Offset(px + pw * 0.6f, py), Size(pw * 0.4f, ph * 0.3f))
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // FLOORS / BACKGROUNDS (base layer helpers)
    // ═══════════════════════════════════════════════════════════════

    fun DrawScope.drawStageFloor(palette: ScenePalette, floorY: Float = size.height * 0.78f) {
        val w = size.width
        val h = size.height
        drawRect(
            brush = Brush.verticalGradient(
                colors = listOf(Color(0xFF242424), Color(0xFF0A0A0A)),
                startY = floorY,
                endY = h
            ),
            topLeft = Offset(0f, floorY),
            size = Size(w, h - floorY)
        )
        drawRect(palette.secondary.copy(alpha = 0.28f), Offset(0f, floorY), Size(w, 3f))
        for (i in 0 until 8) {
            val x = w * (i / 7f)
            drawLine(Color.White.copy(alpha = 0.035f), Offset(w * 0.5f, floorY), Offset(x, h), 1f)
        }
        for (i in 1 until 5) {
            val y = floorY + (h - floorY) * (i / 5f)
            drawLine(Color.Black.copy(alpha = 0.22f), Offset(0f, y), Offset(w, y), 1f)
        }
    }

    fun DrawScope.drawSkyGradient(colors: List<Color>) {
        val resolved = if (colors.isEmpty()) listOf(Color(0xFF121212), Color(0xFF050505)) else colors
        drawRect(
            brush = Brush.verticalGradient(resolved),
            topLeft = Offset.Zero,
            size = size
        )
        val w = size.width
        val h = size.height
        colors.take(4).forEachIndexed { i, color ->
            val cx = w * (0.18f + i * 0.22f)
            val cy = h * (0.15f + (i % 2) * 0.16f)
            drawCircle(color.copy(alpha = 0.09f), w * (0.22f + i * 0.03f), Offset(cx, cy))
        }
        drawRect(Color.White.copy(alpha = 0.025f), Offset.Zero, Size(w, h * 0.32f))
    }

    fun DrawScope.drawHorizonLine(palette: ScenePalette, y: Float, intensity: Float) {
        drawRect(palette.secondary.copy(alpha = 0.3f + intensity * 0.15f), Offset(0f, y - 2f), Size(size.width, 4f))
    }

    fun DrawScope.drawVignette(alpha: Float = 0.2f) {
        val edge = alpha.coerceIn(0f, 0.55f)
        drawRect(
            brush = Brush.radialGradient(
                colors = listOf(Color.Transparent, Color.Black.copy(alpha = edge)),
                center = Offset(size.width * 0.5f, size.height * 0.46f),
                radius = size.maxDimension * 0.72f
            ),
            topLeft = Offset.Zero,
            size = size
        )
        drawRect(Color.Black.copy(alpha = edge * 0.18f), Offset.Zero, size)
    }

    fun DrawScope.drawFieldGround(palette: ScenePalette, horizonY: Float = size.height * 0.45f) {
        val w = size.width
        val h = size.height
        val farHill = Path().apply {
            moveTo(0f, horizonY + h * 0.04f)
            cubicTo(w * 0.18f, horizonY - h * 0.025f, w * 0.42f, horizonY + h * 0.07f, w * 0.62f, horizonY + h * 0.02f)
            cubicTo(w * 0.78f, horizonY - h * 0.02f, w * 0.9f, horizonY + h * 0.045f, w, horizonY + h * 0.015f)
            lineTo(w, h)
            lineTo(0f, h)
            close()
        }
        drawPath(
            path = farHill,
            brush = Brush.verticalGradient(
                colors = listOf(
                    palette.highlight.copy(alpha = 0.72f),
                    palette.primary.copy(alpha = 0.95f),
                    Color(0xFF0E1A0A)
                ),
                startY = horizonY,
                endY = h
            )
        )
        val nearHill = Path().apply {
            moveTo(0f, horizonY + h * 0.22f)
            cubicTo(w * 0.2f, horizonY + h * 0.14f, w * 0.46f, horizonY + h * 0.28f, w * 0.68f, horizonY + h * 0.18f)
            cubicTo(w * 0.82f, horizonY + h * 0.11f, w * 0.96f, horizonY + h * 0.23f, w, horizonY + h * 0.2f)
            lineTo(w, h)
            lineTo(0f, h)
            close()
        }
        drawPath(
            path = nearHill,
            brush = Brush.verticalGradient(
                colors = listOf(palette.primary.copy(alpha = 0.88f), Color(0xFF071004)),
                startY = horizonY + h * 0.18f,
                endY = h
            )
        )
        for (i in 0 until 18) {
            val y = horizonY + h * (0.08f + i * 0.035f)
            drawLine(palette.highlight.copy(alpha = 0.04f), Offset(0f, y), Offset(w, y + sin(i.toFloat()) * 9f), 1f)
        }
    }

    fun DrawScope.drawStreetGround(palette: ScenePalette, streetY: Float = size.height * 0.72f) {
        drawRect(
            brush = Brush.verticalGradient(
                colors = listOf(Color(0xFF11131A), Color(0xFF030305)),
                startY = streetY,
                endY = size.height
            ),
            topLeft = Offset(0f, streetY),
            size = Size(size.width, size.height - streetY)
        )
        drawLine(palette.accent.copy(alpha = 0.18f), Offset(0f, streetY), Offset(size.width, streetY), 2f)
    }

    fun DrawScope.drawRedStageBeams(
        time: Float, palette: ScenePalette, intensity: Float,
        floorY: Float = size.height * 0.78f
    ) {
        val w = size.width
        val redAlpha = 0.12f + intensity * 0.15f
        val sway = sin(time * PI.toFloat() * 2f) * w * 0.05f
        for (beamX in listOf(w * 0.2f + sway, w * 0.8f - sway)) {
            val beam = Path().apply {
                moveTo(beamX - w * 0.02f, 0f)
                lineTo(beamX + w * 0.02f, 0f)
                lineTo(beamX + w * 0.22f, floorY)
                lineTo(beamX - w * 0.22f, floorY)
                close()
            }
            drawPath(
                path = beam,
                brush = Brush.verticalGradient(
                    colors = listOf(
                        palette.secondary.copy(alpha = redAlpha),
                        palette.secondary.copy(alpha = redAlpha * 0.45f),
                        Color.Transparent
                    ),
                    startY = 0f,
                    endY = floorY
                )
            )
        }
    }

    fun DrawScope.drawStreetlights(time: Float, palette: ScenePalette, intensity: Float, streetY: Float = size.height * 0.72f) {
        val w = size.width; val h = size.height
        for (i in 0 until 5) {
            val lx = w * (0.1f + i * 0.2f)
            val ly = streetY - 5f
            drawCircle(palette.secondary.copy(alpha = (0.15f + time * 0.1f) * intensity), w * 0.04f, Offset(lx, ly))
            drawCircle(palette.secondary.copy(alpha = (0.4f + time * 0.2f) * intensity), 3f, Offset(lx, ly))
            drawRect(palette.secondary.copy(alpha = 0.03f * intensity), Offset(lx - w * 0.03f, ly), Size(w * 0.06f, h - ly))
        }
    }

    fun DrawScope.drawHarshLamp(time: Float, palette: ScenePalette, intensity: Float, floorY: Float = size.height * 0.75f) {
        val w = size.width
        val lampX = w * 0.5f; val lampY = size.height * 0.02f
        val flicker = if (time > 0.85f) 0.5f else 1f
        val alpha = (0.4f + intensity * 0.2f) * flicker
        for (i in 0 until 12) {
            val t = i / 12f
            val coneW = w * 0.03f + t * w * 0.4f
            val coneY = lampY + t * (floorY - lampY)
            drawRect(
                palette.secondary.copy(alpha = alpha * 0.08f * (1f - t * 0.5f)),
                Offset(lampX - coneW / 2f, coneY),
                Size(coneW, (floorY - lampY) / 12f + 2f)
            )
        }
        drawCircle(palette.secondary.copy(alpha = alpha), 6f, Offset(lampX, lampY))
    }

    /**
     * Soft radial haze to unify the scene and reduce flatness.
     */
    fun DrawScope.drawAtmosphereHaze(palette: ScenePalette, intensity: Float) {
        val w = size.width
        val h = size.height
        drawRect(
            brush = Brush.radialGradient(
                colors = listOf(
                    palette.secondary.copy(alpha = 0.08f * intensity),
                    palette.primary.copy(alpha = 0.04f * intensity),
                    Color.Transparent
                ),
                center = Offset(w * 0.5f, h * 0.35f),
                radius = w * 0.8f
            ),
            size = size
        )
    }

    /**
     * Subtle animated film-grain texture using tiny random dots.
     * Keeps the scene from looking like a clean digital slab.
     */
    fun DrawScope.drawFilmGrain(time: Float, palette: ScenePalette, intensity: Float) {
        val w = size.width
        val h = size.height
        val bgLuma = palette.background.red * 0.2126f +
            palette.background.green * 0.7152f +
            palette.background.blue * 0.0722f
        val grainColor = if (bgLuma > 0.3f) Color.Black else Color.White
        val alpha = 0.03f * intensity.coerceIn(0.5f, 1f)
        val seed = (time * 400f).toInt()
        val random = kotlin.random.Random(seed)
        val count = ((w * h) / 1800f).toInt().coerceIn(60, 320)
        repeat(count) {
            val x = random.nextFloat() * w
            val y = random.nextFloat() * h
            drawCircle(grainColor.copy(alpha = alpha), radius = 0.8f + random.nextFloat() * 1.2f, center = Offset(x, y))
        }
    }


    // ═══════════════════════════════════════════════════════════════
    // CINEMATIC PRODUCTION PASS — backplates, bloom, glow, vignette
    // ═══════════════════════════════════════════════════════════════

    fun DrawScope.drawSceneBackplate(
        family: LivingSceneType,
        time: Float,
        palette: ScenePalette,
        intensity: Float
    ) {
        when (family) {
            LivingSceneType.INDUSTRIAL_STAGE,
            LivingSceneType.BASEMENT_STAGE,
            LivingSceneType.STAGE_SPOTLIGHT -> drawIndustrialBackplate(time, palette, intensity)
            LivingSceneType.NIGHT_HIGHWAY_STAGE -> drawNightHighwayBackplate(time, palette, intensity)
            LivingSceneType.OPEN_ROAD_SKY,
            LivingSceneType.OPEN_FIELD,
            LivingSceneType.OPEN_FIELD_ROAD,
            LivingSceneType.DESERT_HIGHWAY,
            LivingSceneType.CHURCH_LIGHT -> drawOpenSkyBackplate(time, palette, intensity)
            LivingSceneType.CITY_REFLECTION,
            LivingSceneType.NIGHT_CITY_PULSE,
            LivingSceneType.CITY_NIGHT -> drawCityReflectionBackplate(time, palette, intensity)
            LivingSceneType.STORM_WINDOW -> drawStormWindowBackplate(time, palette, intensity)
            LivingSceneType.BEDROOM_MEMORY -> drawBedroomMemoryBackplate(time, palette, intensity)
            else -> drawPremiumFallbackBackplate(time, palette, intensity)
        }
    }

    fun DrawScope.drawCinematicVignette(strength: Float = 0.35f) {
        val w = size.width
        val h = size.height
        val edge = strength.coerceIn(0f, 0.7f)
        drawRect(
            brush = Brush.radialGradient(
                colors = listOf(
                    Color.Transparent,
                    Color.Black.copy(alpha = edge * 0.5f),
                    Color.Black.copy(alpha = edge)
                ),
                center = Offset(w * 0.5f, h * 0.42f),
                radius = w * 0.75f
            ),
            topLeft = Offset.Zero,
            size = size
        )
        drawRect(
            brush = Brush.verticalGradient(
                colors = listOf(Color.Black.copy(alpha = edge * 0.7f), Color.Transparent),
                startY = 0f,
                endY = h * 0.28f
            ),
            topLeft = Offset.Zero,
            size = Size(w, h * 0.28f)
        )
        drawRect(
            brush = Brush.verticalGradient(
                colors = listOf(Color.Transparent, Color.Black.copy(alpha = edge * 0.9f)),
                startY = h * 0.65f,
                endY = h
            ),
            topLeft = Offset(0f, h * 0.65f),
            size = Size(w, h * 0.35f)
        )
    }

    fun DrawScope.drawBloomGlow(
        palette: ScenePalette,
        intensity: Float,
        center: Offset,
        radius: Float
    ) {
        drawCircle(
            color = palette.secondary.copy(alpha = 0.10f * intensity),
            radius = radius,
            center = center
        )
        drawCircle(
            color = palette.accent.copy(alpha = 0.06f * intensity),
            radius = radius * 0.55f,
            center = center
        )
    }

    fun DrawScope.drawLightShaft(
        time: Float,
        palette: ScenePalette,
        intensity: Float,
        centerX: Float,
        horizonY: Float
    ) {
        val w = size.width
        val sway = sin(time * PI.toFloat() * 2f) * w * 0.06f
        val shaft = Path().apply {
            moveTo(centerX - w * 0.04f + sway, 0f)
            lineTo(centerX + w * 0.04f + sway, 0f)
            lineTo(centerX + w * 0.35f, horizonY)
            lineTo(centerX - w * 0.35f, horizonY)
            close()
        }
        drawPath(
            path = shaft,
            brush = Brush.verticalGradient(
                colors = listOf(
                    palette.secondary.copy(alpha = 0.10f * intensity),
                    palette.accent.copy(alpha = 0.04f * intensity),
                    Color.Transparent
                ),
                startY = 0f,
                endY = horizonY
            )
        )
    }

    fun DrawScope.drawIndustrialBackplate(time: Float, palette: ScenePalette, intensity: Float) {
        val w = size.width
        val h = size.height
        val floorY = h * 0.78f
        drawRect(color = Color(0xFF030303), topLeft = Offset.Zero, size = size)
        drawRect(
            brush = Brush.radialGradient(
                colors = listOf(
                    palette.secondary.copy(alpha = 0.30f * intensity),
                    palette.primary.copy(alpha = 0.12f),
                    Color.Transparent
                ),
                center = Offset(w * 0.5f, h * 0.10f),
                radius = w * 0.95f
            ),
            topLeft = Offset.Zero,
            size = size
        )
        drawStageTruss(palette, intensity)
        drawSteelBeams(palette, intensity)
        drawStageFloor(palette, floorY)
        drawSmoke(time, palette, intensity, floorY)
        drawSparks(time, palette, intensity)
        drawRedStageBeams(time, palette, intensity, floorY)
        drawBloomGlow(palette, intensity, Offset(w * 0.5f, h * 0.22f), w * 0.55f)
    }

    fun DrawScope.drawNightHighwayBackplate(time: Float, palette: ScenePalette, intensity: Float) {
        val w = size.width
        val h = size.height
        val horizonY = h * 0.40f
        drawRect(
            brush = Brush.verticalGradient(
                colors = listOf(Color(0xFF020208), Color(0xFF0A0A18), palette.background),
                startY = 0f,
                endY = h
            ),
            topLeft = Offset.Zero,
            size = size
        )
        drawRect(
            brush = Brush.radialGradient(
                colors = listOf(
                    palette.primary.copy(alpha = 0.22f * intensity),
                    Color.Transparent
                ),
                center = Offset(w * 0.5f, horizonY),
                radius = w * 0.8f
            ),
            topLeft = Offset.Zero,
            size = size
        )
        drawStars(palette, intensity)
        drawBuildingSilhouette(palette, time, intensity, horizonY)
        drawStreetGround(palette, h * 0.72f)
        drawRoadLines(time, palette, intensity, horizonY)
        drawHeadlights(time, palette, intensity, horizonY)
        drawBloomGlow(palette, intensity, Offset(w * 0.5f, h * 0.55f), w * 0.7f)
    }

    fun DrawScope.drawOpenSkyBackplate(time: Float, palette: ScenePalette, intensity: Float) {
        val w = size.width
        val h = size.height
        val horizonY = h * 0.45f
        drawRect(
            brush = Brush.verticalGradient(
                colors = listOf(
                    Color(0xFF1A2440),
                    Color(0xFF2A3A5A),
                    Color(0xFF4A6A8A),
                    Color(0xFF8AA0B8),
                    Color(0xFFC0B890),
                    palette.secondary
                ),
                startY = 0f,
                endY = h
            ),
            topLeft = Offset.Zero,
            size = size
        )
        drawLightShaft(time, palette, intensity, w * 0.5f, horizonY)
        drawSunHorizon(time, palette, intensity, horizonY)
        drawClouds(time, palette, intensity)
        drawFieldGround(palette, horizonY)
        drawGrassWisps(time, palette, intensity, horizonY)
        drawBloomGlow(palette, intensity, Offset(w * 0.5f, horizonY), w * 0.85f)
    }

    fun DrawScope.drawCityReflectionBackplate(time: Float, palette: ScenePalette, intensity: Float) {
        val w = size.width
        val h = size.height
        val horizonY = h * 0.48f
        drawRect(
            brush = Brush.verticalGradient(
                colors = listOf(
                    Color(0xFF101A28),
                    Color(0xFF1A2A3A),
                    Color(0xFF2A4A6A),
                    palette.accent.copy(alpha = 0.35f)
                ),
                startY = 0f,
                endY = h
            ),
            topLeft = Offset.Zero,
            size = size
        )
        drawClouds(time, palette, intensity * 0.6f)
        drawBuildingSilhouette(palette, time, intensity, horizonY)
        drawWaterReflection(time, palette, intensity, horizonY)
        drawBloomGlow(palette, intensity, Offset(w * 0.5f, horizonY), w * 0.65f)
    }

    fun DrawScope.drawStormWindowBackplate(time: Float, palette: ScenePalette, intensity: Float) {
        val w = size.width
        val h = size.height
        drawRect(
            brush = Brush.verticalGradient(
                colors = listOf(Color(0xFF080810), Color(0xFF12121E), Color(0xFF1A1A2A)),
                startY = 0f,
                endY = h
            ),
            topLeft = Offset.Zero,
            size = size
        )
        val frameL = w * 0.08f
        val frameT = h * 0.08f
        val frameW = w * 0.84f
        val frameH = h * 0.55f
        drawRect(Color(0xFF05050A), Offset(frameL, frameT), Size(frameW, frameH))
        drawRect(
            brush = Brush.verticalGradient(
                colors = listOf(palette.secondary.copy(alpha = 0.12f * intensity), Color.Transparent),
                startY = frameT,
                endY = frameT + frameH
            ),
            topLeft = Offset(frameL + 4f, frameT + 4f),
            size = Size(frameW - 8f, frameH - 8f)
        )
        drawLine(
            color = palette.primary.copy(alpha = 0.5f),
            start = Offset(frameL + frameW / 2f, frameT),
            end = Offset(frameL + frameW / 2f, frameT + frameH),
            strokeWidth = 3f
        )
        drawLine(
            color = palette.primary.copy(alpha = 0.5f),
            start = Offset(frameL, frameT + frameH / 2f),
            end = Offset(frameL + frameW, frameT + frameH / 2f),
            strokeWidth = 3f
        )
        drawRainDrops(time, palette, intensity, 80)
        drawLightningFlash(time, intensity)
        drawBloomGlow(palette, intensity, Offset(w * 0.55f, h * 0.25f), w * 0.5f)
    }

    fun DrawScope.drawBedroomMemoryBackplate(time: Float, palette: ScenePalette, intensity: Float) {
        val w = size.width
        val h = size.height
        drawRect(
            brush = Brush.verticalGradient(
                colors = listOf(Color(0xFF0F0D0B), Color(0xFF1A1510), Color(0xFF252018)),
                startY = 0f,
                endY = h
            ),
            topLeft = Offset.Zero,
            size = size
        )
        drawBedroomWindow(palette, time, intensity)
        drawWarmLamp(time, palette, intensity)
        drawDustParticles(time, palette, intensity * 0.7f, 18)
        drawBloomGlow(palette, intensity, Offset(w * 0.62f, h * 0.28f), w * 0.45f)
    }

    fun DrawScope.drawPremiumFallbackBackplate(time: Float, palette: ScenePalette, intensity: Float) {
        val w = size.width
        val h = size.height
        drawRect(color = Color(0xFF030303), topLeft = Offset.Zero, size = size)
        drawCircle(palette.secondary.copy(alpha = 0.10f), w * 0.55f, Offset(w * 0.35f, h * 0.35f))
        drawCircle(palette.accent.copy(alpha = 0.07f), w * 0.65f, Offset(w * 0.70f, h * 0.55f))
        drawCircle(palette.highlight.copy(alpha = 0.05f), w * 0.45f, Offset(w * 0.50f, h * 0.40f))
        drawDustParticles(time, palette, 0.8f, 24)
        drawSmoke(time, palette, 0.5f, h * 0.75f)
        drawBloomGlow(palette, intensity, Offset(w * 0.5f, h * 0.40f), w * 0.75f)
    }

}
