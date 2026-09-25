package com.audiophile.musicplayer.tv

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp

/**
 * Focus-aware container for D-pad navigation. On 4K, focus lifts the card
 * with a soft glow so it reads from across the room. Also brings the focused
 * item into view inside LazyRow / LazyColumn so D-pad scrolling works.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TvFocusable(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    focusRequester: FocusRequester? = null,
    cornerRadius: Int = 18,
    focusScale: Float = 1.07f,
    content: @Composable (focused: Boolean) -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val focused by interactionSource.collectIsFocusedAsState()
    val bringIntoViewRequester = remember { BringIntoViewRequester() }
    val scale by animateFloatAsState(
        targetValue = if (focused) focusScale else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMediumLow),
        label = "tvFocusScale"
    )
    val elevation by animateFloatAsState(
        targetValue = if (focused) 28f else 0f,
        label = "tvFocusElevation"
    )
    val shape = RoundedCornerShape(cornerRadius.dp)

    LaunchedEffect(focused) {
        if (focused) {
            runCatching { bringIntoViewRequester.bringIntoView() }
        }
    }

    val base = modifier
        .bringIntoViewRequester(bringIntoViewRequester)
        .graphicsLayer {
            scaleX = scale
            scaleY = scale
        }
        .shadow(
            elevation = elevation.dp,
            shape = shape,
            ambientColor = TvTheme.Glow.copy(alpha = 0.45f),
            spotColor = TvTheme.HiResGold.copy(alpha = 0.35f),
            clip = false
        )
        .clip(shape)
        .border(
            width = if (focused) 2.dp else 0.dp,
            color = if (focused) TvTheme.FocusRing else TvTheme.FocusRing.copy(alpha = 0f),
            shape = shape
        )
        .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
        .clickable(
            interactionSource = interactionSource,
            indication = null,
            onClick = onClick
        )

    Box(modifier = base) {
        content(focused)
    }
}
