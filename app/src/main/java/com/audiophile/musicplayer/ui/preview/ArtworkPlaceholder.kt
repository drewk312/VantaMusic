package com.audiophile.musicplayer.ui.preview

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.audiophile.musicplayer.ui.AppText
import kotlin.math.abs

private val palette = listOf(
    Color(0xFF4A3F35), Color(0xFF3D3028), Color(0xFF2E2520),
    Color(0xFF1A1412), Color(0xFF2A221D), Color(0xFF1E1815),
    Color(0xFF3A2F28), Color(0xFF251D18)
)

@Composable
fun ArtworkPlaceholder(seed: String?, modifier: Modifier = Modifier, showInitials: Boolean = false) {
    val color = palette[abs(seed?.hashCode() ?: 0) % palette.size]
    val initials = seed?.split(" ", "-", "_")?.take(2)?.joinToString("") { it.firstOrNull()?.uppercase() ?: "" } ?: "?"
    Box(
        modifier = modifier.background(color),
        contentAlignment = Alignment.Center
    ) {
        if (showInitials) {
            Text(initials, color = Color.White.copy(alpha = 0.6f), fontSize = 28.sp, fontWeight = FontWeight.Black)
        }
    }
}
