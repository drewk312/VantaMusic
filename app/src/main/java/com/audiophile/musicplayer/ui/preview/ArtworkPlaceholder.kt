package com.audiophile.musicplayer.ui.preview

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.min

private data class PlaceholderPalette(
    val start: Color,
    val end: Color,
    val glow: Color
)

/**
 * Deliberately colorful, deterministic palettes. Missing cover art should still
 * look like an authored VANTA surface instead of a failed black image request.
 */
private val palettes = listOf(
    PlaceholderPalette(Color(0xFF30276B), Color(0xFF0A1632), Color(0xFF8B7CFF)),
    PlaceholderPalette(Color(0xFF542B5E), Color(0xFF190D28), Color(0xFFF48BD5)),
    PlaceholderPalette(Color(0xFF124C58), Color(0xFF071C2A), Color(0xFF62E7F5)),
    PlaceholderPalette(Color(0xFF603B25), Color(0xFF20101B), Color(0xFFFFB76A)),
    PlaceholderPalette(Color(0xFF233F71), Color(0xFF0A142B), Color(0xFF74B8FF)),
    PlaceholderPalette(Color(0xFF3E315D), Color(0xFF111326), Color(0xFFB8A7FF))
)

@Composable
fun ArtworkPlaceholder(
    seed: String?,
    modifier: Modifier = Modifier,
    showInitials: Boolean = false,
    accentColor: Color? = null
) {
    val palette = remember(seed, accentColor) {
        val hash = seed?.hashCode() ?: 0
        val base = palettes[(hash and Int.MAX_VALUE) % palettes.size]
        if (accentColor == null) base else base.copy(
            start = accentColor,
            glow = accentColor
        )
    }
    val initial = remember(seed) {
        seed
            ?.trim()
            ?.firstOrNull { it.isLetterOrDigit() }
            ?.uppercase()
            ?: "V"
    }

    BoxWithConstraints(
        modifier = modifier.background(
            Brush.linearGradient(
                colors = listOf(palette.start, palette.end, Color(0xFF05070E)),
                start = Offset.Zero,
                end = Offset.Infinite
            )
        ),
        contentAlignment = Alignment.Center
    ) {
        val shortestDp = minOf(maxWidth, maxHeight)
        Canvas(modifier = Modifier.fillMaxSize()) {
            val shortest = min(size.width, size.height)
            val center = Offset(size.width * 0.52f, size.height * 0.47f)

            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(palette.glow.copy(alpha = 0.34f), Color.Transparent),
                    center = center,
                    radius = shortest * 0.55f
                ),
                center = center,
                radius = shortest * 0.55f
            )

            // Record-like grooves make the fallback read as music artwork at
            // thumbnail and full-screen sizes without imitating a generic icon.
            for (index in 0..8) {
                drawCircle(
                    color = Color.White.copy(alpha = 0.055f + index * 0.006f),
                    radius = shortest * (0.18f + index * 0.035f),
                    center = center,
                    style = Stroke(width = 1.dp.toPx())
                )
            }
            drawCircle(
                color = Color(0xFF070910).copy(alpha = 0.72f),
                radius = shortest * 0.16f,
                center = center
            )
            drawCircle(
                color = palette.glow.copy(alpha = 0.58f),
                radius = shortest * 0.035f,
                center = center
            )
            drawLine(
                brush = Brush.linearGradient(
                    listOf(Color.Transparent, Color.White.copy(alpha = 0.24f), Color.Transparent)
                ),
                start = Offset(size.width * 0.12f, size.height * 0.04f),
                end = Offset(size.width * 0.86f, size.height * 0.92f),
                strokeWidth = shortest * 0.025f
            )
        }

        Box(
            modifier = Modifier
                .size(minOf(shortestDp * 0.38f, 96.dp))
                .clip(CircleShape)
                .background(Color(0x99060910)),
            contentAlignment = Alignment.Center
        ) {
            if (showInitials) {
                Text(
                    text = initial,
                    color = Color.White.copy(alpha = 0.90f),
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Black
                )
            } else {
                Icon(
                    imageVector = Icons.Filled.MusicNote,
                    contentDescription = null,
                    tint = Color.White.copy(alpha = 0.88f),
                    modifier = Modifier.size(minOf(shortestDp * 0.18f, 42.dp))
                )
            }
        }
    }
}
