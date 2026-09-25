package com.audiophile.musicplayer.tv.jukebox

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.FastForward
import androidx.compose.material.icons.outlined.FastRewind
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.SkipNext
import androidx.compose.material.icons.outlined.SkipPrevious
import androidx.compose.material.icons.outlined.ThumbDown
import androidx.compose.material.icons.outlined.ThumbUp
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import com.audiophile.musicplayer.tv.TvTheme

/**
 * Physical Jukebox Transport Controls — recessed circular metal bezels with a
 * tungsten-lit amber illumination ring. Each button reads as a machined part,
 * not a Material icon chip: recessed face, knurled bezel, press depth on
 * focus, and a warm lamp that lights the active transport.
 */
@Composable
fun TvJukeboxControls(
    isPlaying: Boolean,
    isLiked: Boolean,
    onPrevious: () -> Unit,
    onRewind: () -> Unit,
    onToggle: () -> Unit,
    onFastForward: () -> Unit,
    onNext: () -> Unit,
    onThumbsUp: () -> Unit,
    onThumbsDown: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        TvMechanicalButton(
            onClick = onPrevious,
            contentDescription = "Previous track"
        ) { focused, pressed ->
            Icon(
                imageVector = Icons.Outlined.SkipPrevious,
                contentDescription = null,
                tint = if (focused) Color(0xFFFFF6DE) else Color(0xFFE8DCC8),
                modifier = Modifier.size(22.dp)
            )
        }

        TvMechanicalButton(
            onClick = onRewind,
            contentDescription = "Rewind"
        ) { focused, pressed ->
            Icon(
                imageVector = Icons.Outlined.FastRewind,
                contentDescription = null,
                tint = if (focused) Color(0xFFFFF6DE) else Color(0xFFE8DCC8),
                modifier = Modifier.size(22.dp)
            )
        }

        TvMechanicalButton(
            onClick = onToggle,
            primary = true,
            active = isPlaying,
            contentDescription = if (isPlaying) "Pause" else "Play"
        ) { focused, pressed ->
            Icon(
                imageVector = if (isPlaying) Icons.Outlined.Pause else Icons.Outlined.PlayArrow,
                contentDescription = null,
                tint = when {
                    focused -> Color(0xFFFFF6DE)
                    isPlaying -> Color(0xFFFFF0C8)
                    else -> Color(0xFFFFE9B0)
                },
                modifier = Modifier.size(26.dp)
            )
        }

        TvMechanicalButton(
            onClick = onFastForward,
            contentDescription = "Fast forward"
        ) { focused, pressed ->
            Icon(
                imageVector = Icons.Outlined.FastForward,
                contentDescription = null,
                tint = if (focused) Color(0xFFFFF6DE) else Color(0xFFE8DCC8),
                modifier = Modifier.size(22.dp)
            )
        }

        TvMechanicalButton(
            onClick = onNext,
            contentDescription = "Next track"
        ) { focused, pressed ->
            Icon(
                imageVector = Icons.Outlined.SkipNext,
                contentDescription = null,
                tint = if (focused) Color(0xFFFFF6DE) else Color(0xFFE8DCC8),
                modifier = Modifier.size(22.dp)
            )
        }

        Box(
            modifier = Modifier.size(10.dp)
                .background(Color(0xFF2E2923), CircleShape)
        )

        // Genome steering — rejected / liked lamps
        TvMechanicalButton(
            onClick = onThumbsDown,
            accent = Color(0xFFB85A3A),
            contentDescription = "Thumbs down — reject this sound"
        ) { focused, pressed ->
            Icon(
                imageVector = Icons.Outlined.ThumbDown,
                contentDescription = null,
                tint = if (focused) Color(0xFFE8A98A) else Color(0xFFD8CFC0),
                modifier = Modifier.size(20.dp)
            )
        }

        TvMechanicalButton(
            onClick = onThumbsUp,
            accent = TvTheme.HiResGoldBright,
            active = isLiked,
            contentDescription = "Thumbs up — tune station to this sound"
        ) { focused, pressed ->
            Icon(
                imageVector = Icons.Outlined.ThumbUp,
                contentDescription = null,
                tint = if (focused || isLiked) TvTheme.HiResGoldBright else Color(0xFFE8DCC8),
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
private fun TvMechanicalButton(
    onClick: () -> Unit,
    contentDescription: String,
    primary: Boolean = false,
    active: Boolean = false,
    accent: Color = TvTheme.HiResGoldBright,
    content: @Composable (focused: Boolean, pressed: Boolean) -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val focused by interactionSource.collectIsFocusedAsState()
    val pressed by interactionSource.collectIsPressedAsState()

    val scale by animateFloatAsState(
        targetValue = if (focused) 1.1f else 1f,
        animationSpec = spring(dampingRatio = 0.5f, stiffness = 420f),
        label = "jukeboxBtnScale"
    )

    val bezel = if (primary) {
        Brush.radialGradient(
            colors = listOf(
                if (active) Color(0xFFFFE9B0) else Color(0xFFE8DCC8),
                if (active) Color(0xFFE2C45A) else Color(0xFFB2A68F),
                if (active) Color(0xFF6B4E12) else Color(0xFF5A5142)
            )
        )
    } else {
        Brush.radialGradient(
            colors = listOf(
                if (active) Color(0xFFFFE3A0) else Color(0xFF4A4138),
                if (active) Color(0xFFC9A227) else Color(0xFF2A241E),
                Color(0xFF12100D)
            )
        )
    }

    // Amber illumination ring when focused / active

    Box(
        modifier = Modifier
            .size(if (primary) 58.dp else 46.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .shadow(
                elevation = if (focused) 18.dp else 4.dp,
                shape = CircleShape,
                spotColor = if (focused) accent.copy(alpha = 0.6f) else Color.Black.copy(alpha = 0.5f),
                ambientColor = if (focused) accent.copy(alpha = 0.4f) else Color.Black
            )
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
            .padding(4.dp),
        contentAlignment = Alignment.Center
    ) {
        // Machined bezel ring
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(bezel, CircleShape)
        )

        // Recessed keywell
        Box(
            modifier = Modifier
                .size(if (primary) 36.dp else 28.dp)
                .background(Color(0xFF0C0A08), CircleShape)
                .border(
                    width = 1.dp,
                    color = Color.White.copy(alpha = if (pressed) 0.15f else 0.05f),
                    shape = CircleShape
                )
                .graphicsLayer {
                    translationY = if (pressed) 1.5f else 0f
                },
            contentAlignment = Alignment.Center
        ) {
            content(focused, pressed)
        }

        // Focus / active illumination ring
        if (focused || active) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .border(
                        BorderStroke(2.dp, accent),
                        CircleShape
                    )
            )
        }
    }
}