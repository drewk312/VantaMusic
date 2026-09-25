package com.audiophile.musicplayer.ui.nowplaying

import android.content.Intent
import android.util.Log
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.audiophile.musicplayer.ui.AppAccent
import com.audiophile.musicplayer.ui.AppAccentSecondary
import com.audiophile.musicplayer.ui.AppSurfaceRaised
import com.audiophile.musicplayer.ui.AppText
import com.audiophile.musicplayer.ui.AppTextMuted
import com.audiophile.musicplayer.ui.AppTextSecondary

@Composable
fun PortraitUtilityBar(
    mode: NowPlayingMode,
    displayTitle: String,
    displayArtist: String,
    onModeChange: (NowPlayingMode) -> Unit
) {
    val context = LocalContext.current
    val dockShape = RoundedCornerShape(24.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(58.dp)
            .clip(dockShape)
            .background(AppSurfaceRaised.copy(alpha = 0.92f))
            .border(0.75.dp, Color.White.copy(alpha = 0.12f), dockShape)
            .padding(horizontal = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        UtilityAction(icon = Icons.AutoMirrored.Filled.QueueMusic, label = "Queue", active = mode == NowPlayingMode.QUEUE, modifier = Modifier.weight(1f), onClick = { onModeChange(if (mode == NowPlayingMode.QUEUE) NowPlayingMode.ARTWORK else NowPlayingMode.QUEUE) })
        UtilityAction(icon = Icons.AutoMirrored.Filled.Article, label = "Lyrics", active = mode == NowPlayingMode.LYRICS, modifier = Modifier.weight(1f), onClick = { onModeChange(if (mode == NowPlayingMode.LYRICS) NowPlayingMode.ARTWORK else NowPlayingMode.LYRICS) })
        UtilityAction(icon = Icons.Filled.Share, label = "Share", active = false, modifier = Modifier.weight(1f), onClick = {
            val intent = Intent(Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(Intent.EXTRA_TEXT, "Listening to $displayTitle by $displayArtist on VANTA") }
            context.startActivity(Intent.createChooser(intent, "Share").apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) })
        })
    }
}

@Composable
private fun UtilityAction(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, active: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val tint = if (active) AppAccentSecondary else AppTextSecondary.copy(alpha = 0.84f)
    Column(
        modifier = modifier
            .fillMaxHeight()
            .padding(vertical = 5.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(if (active) AppAccent.copy(alpha = 0.16f) else Color.Transparent)
            .border(
                0.5.dp,
                if (active) AppAccentSecondary.copy(alpha = 0.14f) else Color.Transparent,
                RoundedCornerShape(16.dp)
            )
            .clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(icon, contentDescription = label, tint = tint, modifier = Modifier.size(20.dp))
        Spacer(Modifier.height(2.dp))
        Text(label, color = if (active) AppText else tint, fontSize = 10.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.2.sp)
    }
}

@Composable
fun NowPlayingActionIcons(isFavorite: Boolean, mode: NowPlayingMode, displayTitle: String, displayArtist: String, onToggleFavorite: () -> Unit, onModeChange: (NowPlayingMode) -> Unit) {
    val ctx = LocalContext.current
    val likeScale by animateFloatAsState(targetValue = if (isFavorite) 1.15f else 1.0f, animationSpec = spring(dampingRatio = 0.5f, stiffness = 500f), label = "likeScale")
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
        Icon(
            imageVector = if (isFavorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
            contentDescription = "Like", tint = if (isFavorite) AppAccent else AppTextSecondary,
            modifier = Modifier.size(48.dp).graphicsLayer(scaleX = likeScale, scaleY = likeScale).clickable { Log.d("VANTA_UI_ACTION", "control='nowplaying_heart' result='tap'"); Log.d("VANTA_LIBRARY_ACTION", "liked=${!isFavorite} track='${displayTitle}'"); onToggleFavorite() }
        )
        Icon(Icons.AutoMirrored.Filled.QueueMusic, contentDescription = "Queue", tint = if (mode == NowPlayingMode.QUEUE) AppAccent else AppTextSecondary,
            modifier = Modifier.size(48.dp).clickable { Log.d("VANTA_UI_ACTION", "control='nowplaying_queue' result='tap'"); onModeChange(if (mode == NowPlayingMode.QUEUE) NowPlayingMode.ARTWORK else NowPlayingMode.QUEUE) })
        Icon(Icons.AutoMirrored.Filled.Article, contentDescription = "Lyrics", tint = if (mode == NowPlayingMode.LYRICS) AppAccent else AppTextSecondary,
            modifier = Modifier.size(48.dp).clickable { Log.d("VANTA_UI_ACTION", "control='nowplaying_lyrics' result='tap'"); onModeChange(if (mode == NowPlayingMode.LYRICS) NowPlayingMode.ARTWORK else NowPlayingMode.LYRICS) })
        Icon(Icons.Filled.Share, contentDescription = "Share", tint = AppTextSecondary,
            modifier = Modifier.size(48.dp).clickable { Log.d("VANTA_UI_ACTION", "control='nowplaying_share' result='tap'"); Log.d("VANTA_SHARE", "track='${displayTitle}'"); val intent = Intent(Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(Intent.EXTRA_TEXT, "Listening to $displayTitle by $displayArtist on VANTA") }; ctx.startActivity(Intent.createChooser(intent, "Share").apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }) })
    }
}
