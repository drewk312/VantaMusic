package com.audiophile.musicplayer.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Radio
import androidx.compose.material.icons.rounded.Explore
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.audiophile.musicplayer.ui.theme.VantaMotion

@Composable
fun BottomNavBar(
    currentRoute: AppRoute,
    onRouteSelected: (AppRoute) -> Unit,
    modifier: Modifier = Modifier
) {
    val tabs = listOf(
        Triple(AppRoute.Home, "Home", Icons.Rounded.Home),
        Triple(AppRoute.Discover, "New", Icons.Rounded.Explore),
        Triple(AppRoute.AiDj, "Radio", Icons.Rounded.Radio),
        Triple(AppRoute.Library, "Library", Icons.Rounded.LibraryMusic),
        Triple(AppRoute.Search, "Search", Icons.Rounded.Search)
    )
    val shape = RoundedCornerShape(30.dp)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .glassSurfaceElevated(shape = shape, surfaceAlpha = 0.80f)
            .drawBehind {
                val specularHeight = 1.dp.toPx()
                drawRect(
                    brush = Brush.horizontalGradient(
                        colors = listOf(
                            Color.Transparent,
                            AppAuroraViolet.copy(alpha = 0.28f),
                            Color.White.copy(alpha = 0.62f),
                            AppAuroraCyan.copy(alpha = 0.34f),
                            Color.Transparent
                        ),
                        startX = size.width * 0.08f,
                        endX = size.width * 0.92f
                    ),
                    topLeft = Offset(0f, 0f),
                    size = androidx.compose.ui.geometry.Size(size.width, specularHeight)
                )
            }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(VantaChrome.bottomNavHeight)
                .padding(horizontal = 7.dp, vertical = 7.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            tabs.forEach { (route, label, icon) ->
                val selected = route == currentRoute
                val tint by animateColorAsState(
                    targetValue = if (selected) AppAccent else AppTextMuted,
                    animationSpec = tween(VantaMotion.chromeFadeMs, easing = VantaMotion.easeInOutCozy),
                    label = "navTint"
                )

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .semantics { this.selected = selected }
                        .clip(RoundedCornerShape(19.dp))
                        .clickable(
                            onClickLabel = label,
                            role = Role.Tab,
                            onClick = {
                                android.util.Log.d("VANTA_UI_NAV", "target_route=$route current_route=$currentRoute result='tap'")
                                onRouteSelected(route)
                            }
                        )
                        .background(
                            brush = if (selected) {
                                Brush.horizontalGradient(
                                    listOf(
                                        AppAccent.copy(alpha = 0.13f),
                                        AppAccent.copy(alpha = 0.09f),
                                        AppAccent.copy(alpha = 0.06f)
                                    )
                                )
                            } else {
                                Brush.horizontalGradient(listOf(Color.Transparent, Color.Transparent))
                            },
                            shape = RoundedCornerShape(19.dp)
                        )
                        .semantics { contentDescription = if (selected) "$label selected" else label }
                        .padding(vertical = 6.dp, horizontal = 2.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = tint,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = label,
                        color = tint,
                        fontSize = 10.sp,
                        lineHeight = 12.sp,
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
}
