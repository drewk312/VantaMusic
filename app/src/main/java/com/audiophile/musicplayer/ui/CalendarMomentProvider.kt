package com.audiophile.musicplayer.ui

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.cos
import kotlin.math.sin
import java.util.Calendar

data class CalendarMoment(
    val id: String,
    val title: String,
    val subtitle: String,
    val heroGradient: List<Color>,
    val playlists: List<MomentPlaylist>,
    val accentColor: Color
)

data class MomentPlaylist(
    val title: String,
    val subtitle: String,
    val icon: String
)

object CalendarMomentProvider {

    fun currentMoment(): CalendarMoment? {
        val cal = Calendar.getInstance()
        val month = cal.get(Calendar.MONTH)
        val day = cal.get(Calendar.DAY_OF_MONTH)

        if (month == Calendar.JULY && day == 4) {
            return CalendarMoment(
                id = "july4",
                title = "Happy 4th of July",
                subtitle = "Celebrate with the perfect summer soundtrack",
                heroGradient = listOf(Color(0xFF171217), Color(0xFF2A211A), Color(0xFF0D0D0F)),
                playlists = listOf(
                    MomentPlaylist("Fireworks After Dark", "Electronic \u00B7 50 tracks", "fireworks"),
                    MomentPlaylist("Cookout Country", "Country \u00B7 42 tracks", "country"),
                    MomentPlaylist("Lake Day Hits", "Summer Vibes \u00B7 38 tracks", "lake"),
                    MomentPlaylist("American Classics", "Rock & Soul \u00B7 45 tracks", "stars")
                ),
                accentColor = Color(0xFFFFD700)
            )
        }

        // Do not invent weekly editorial content. Calendar cards are reserved for
        // real dated moments; discovery and release claims come from the live feed.
        return null
    }
}

@Composable
fun FireworksOverlay(modifier: Modifier = Modifier) {
    val sparkCount = 24
    val infiniteTransition = rememberInfiniteTransition(label = "fw")
    val phase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(8000, easing = androidx.compose.animation.core.LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "phase"
    )

    Canvas(modifier = modifier.fillMaxSize()) {
        val cx = size.width / 2
        val cy = size.height * 0.2f
        val maxR = size.minDimension * 0.12f

        for (i in 0 until sparkCount) {
            val angleDeg = (phase + i * (360f / sparkCount))
            val angleRad = angleDeg * (Math.PI.toFloat() / 180f)
            val progress = ((phase + i * 15f) % 150f) / 150f
            val radius = maxR * (0.2f + progress * 0.8f)
            val alpha = (1f - progress).coerceIn(0f, 0.5f)
            val px = cx + cos(angleRad.toDouble()).toFloat() * radius
            val py = cy + sin(angleRad.toDouble()).toFloat() * radius

            drawCircle(
                color = Color(0xFFFFD700).copy(alpha = alpha),
                radius = (2f + (1f - progress) * 4f).coerceAtLeast(0.5f),
                center = Offset(px, py)
            )
        }
    }
}
