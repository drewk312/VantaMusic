package com.audiophile.musicplayer.ui.preview

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.Icon
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
    Color(0xFF17171B), Color(0xFF1B1A20), Color(0xFF16181E), Color(0xFF1D1A1B),
    Color(0xFF15181A), Color(0xFF1B1916), Color(0xFF18161D), Color(0xFF151517)
)

@Composable
fun ArtworkPlaceholder(seed: String?, modifier: Modifier = Modifier, showInitials: Boolean = false) {
    val color = palette[abs(seed?.hashCode() ?: 0) % palette.size]
    val initials = seed?.split(" ", "-", "_")?.take(2)?.joinToString("") { it.firstOrNull()?.uppercase() ?: "" } ?: "?"
    val accent = Color(0xFFE0A050)
    Box(
        modifier = modifier.background(color),
        contentAlignment = Alignment.Center
    ) {
        if (showInitials) {
            Text(initials, color = Color.White.copy(alpha = 0.6f), fontSize = 28.sp, fontWeight = FontWeight.Black)
        } else {
            Icon(
                imageVector = Icons.Filled.MusicNote,
                contentDescription = null,
                tint = accent.copy(alpha = 0.34f)
            )
        }
    }
}
