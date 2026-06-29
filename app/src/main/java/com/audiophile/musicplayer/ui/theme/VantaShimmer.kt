package com.audiophile.musicplayer.ui.theme

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.audiophile.musicplayer.ui.AppSurface
import com.audiophile.musicplayer.ui.AppSurfaceRaised

@Composable
fun VantaShimmer(
    modifier: Modifier = Modifier,
    shimmerHeight: Dp = 16.dp,
    cornerRadius: Dp = 8.dp
) {
    val infiniteTransition = rememberInfiniteTransition(label = "shimmer")
    val shimmerOffset by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1200f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "shimmer_offset"
    )

    val shimmerColor = AppSurfaceRaised.copy(alpha = 0.3f)
    val baseColor = AppSurface.copy(alpha = 0.5f)

    Box(
        modifier = modifier
            .height(shimmerHeight)
            .clip(RoundedCornerShape(cornerRadius))
            .background(
                Brush.linearGradient(
                    colors = listOf(baseColor, shimmerColor, baseColor),
                    start = Offset(shimmerOffset - 400f, 0f),
                    end = Offset(shimmerOffset, 0f)
                )
            )
    )
}

@Composable
fun TrackListShimmer(modifier: Modifier = Modifier, count: Int = 5) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        repeat(count) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                VantaShimmer(
                    modifier = Modifier.height(48.dp).width(48.dp),
                    cornerRadius = 8.dp
                )
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    VantaShimmer(modifier = Modifier.fillMaxWidth(0.7f), shimmerHeight = 14.dp, cornerRadius = 4.dp)
                    VantaShimmer(modifier = Modifier.fillMaxWidth(0.4f), shimmerHeight = 12.dp, cornerRadius = 4.dp)
                }
            }
        }
    }
}

@Composable
fun ArtworkGridShimmer(modifier: Modifier = Modifier, count: Int = 6) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        repeat(count) {
            VantaShimmer(
                modifier = Modifier.weight(1f).height(120.dp),
                cornerRadius = 12.dp
            )
        }
    }
}
