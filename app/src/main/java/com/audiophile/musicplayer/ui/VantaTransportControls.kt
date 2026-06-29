package com.audiophile.musicplayer.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

@Composable
fun PremiumTransportButton(
    isPrimary: Boolean,
    isPlaying: Boolean,
    isLoading: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    contentDescription: String
) {
    val size = if (isPrimary) 76.dp else 54.dp
    val iconSize = if (isPrimary) 42.dp else 30.dp
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed && enabled) 0.96f else 1f,
        animationSpec = tween(120),
        label = "transportScale"
    )
    val alpha = if (enabled) 1f else 0.38f

    Box(
        modifier = modifier
            .size(size)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                this.alpha = alpha
            }
            .shadow(
                elevation = if (isPrimary) 12.dp else 6.dp,
                shape = CircleShape,
                ambientColor = Color.Black.copy(alpha = 0.45f),
                spotColor = if (isPrimary) AppAccentGlow else Color.Black.copy(alpha = 0.3f)
            )
            .clip(CircleShape)
            .let { m ->
                if (isPrimary) m.background(Color.White)
                else m.background(
                    Brush.radialGradient(
                        colors = listOf(
                            Color.White.copy(alpha = 0.12f),
                            Color.White.copy(alpha = 0.04f),
                            Color.Transparent
                        )
                    )
                )
            }
            .border(
                width = 1.dp,
                brush = if (isPrimary) Brush.verticalGradient(listOf(Color.White, Color.White))
                else Brush.verticalGradient(
                    colors = listOf(
                        Color.White.copy(alpha = 0.15f),
                        Color.White.copy(alpha = 0.03f)
                    )
                ),
                shape = CircleShape
            )
            .semantics { this.contentDescription = contentDescription }
            .clickable(
                interactionSource = interaction,
                indication = null,
                enabled = enabled,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        if (isLoading && isPrimary) {
            CircularProgressIndicator(
                modifier = Modifier.size(iconSize + 8.dp),
                color = AppAccent.copy(alpha = 0.7f),
                strokeWidth = 2.dp
            )
        }
        AnimatedContent(
            targetState = isPlaying,
            transitionSpec = { fadeIn(tween(150)) togetherWith fadeOut(tween(100)) },
            label = "playPauseIcon"
        ) { playing ->
            Icon(
                imageVector = if (playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                contentDescription = null,
                tint = if (isPrimary) Color.Black else Color.White,
                modifier = Modifier.size(iconSize)
            )
        }
    }
}

@Composable
fun PremiumSkipButton(
    isNext: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed && enabled) 0.96f else 1f,
        animationSpec = tween(120),
        label = "skipScale"
    )
    val description = if (isNext) "Next track" else "Previous track"

    Box(
        modifier = modifier
            .size(54.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                alpha = if (enabled) 0.78f else 0.35f
            }
            .shadow(6.dp, CircleShape, ambientColor = Color.Black.copy(alpha = 0.4f))
            .clip(CircleShape)
            .background(
                Brush.radialGradient(
                    colors = listOf(
                        Color.White.copy(alpha = 0.12f),
                        Color.White.copy(alpha = 0.05f),
                        Color.Black.copy(alpha = 0.28f)
                    )
                )
            )
            .border(1.dp, Color.White.copy(alpha = 0.14f), CircleShape)
            .semantics { contentDescription = description }
            .clickable(interactionSource = interaction, indication = null, enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = if (isNext) Icons.Rounded.SkipNext else Icons.Rounded.SkipPrevious,
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(30.dp)
        )
    }
}
