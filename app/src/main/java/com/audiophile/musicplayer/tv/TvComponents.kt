package com.audiophile.musicplayer.tv

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Radio
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.SkipNext
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalContext
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.audiophile.musicplayer.ui.VantaType
import com.audiophile.musicplayer.ui.theme.VantaSans

@Composable
fun TvLuxuryBackdrop(
    modifier: Modifier = Modifier,
    artworkUrl: String? = null
) {
    val context = LocalContext.current
    Box(modifier = modifier.background(TvTheme.Bg)) {
        if (!artworkUrl.isNullOrBlank()) {
            val imageRequest = remember(artworkUrl) {
                ImageRequest.Builder(context)
                    .data(artworkUrl)
                    .size(128, 128)
                    .crossfade(true)
                    .build()
            }
            AsyncImage(
                model = imageRequest,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .alpha(0.28f)
            )
            Box(
                Modifier
                    .fillMaxSize()
                    .background(
                        Brush.radialGradient(
                            colors = listOf(
                                Color.Transparent,
                                TvTheme.Bg.copy(alpha = 0.65f),
                                TvTheme.Bg.copy(alpha = 0.95f)
                            )
                        )
                    )
            )
            Box(
                Modifier
                    .fillMaxSize()
                    .background(TvTheme.Bg.copy(alpha = 0.50f))
            )
        } else {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            0f to TvTheme.BgElevated,
                            0.45f to TvTheme.Bg,
                            1f to Color(0xFF050403)
                        )
                    )
            )
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .fillMaxWidth(0.55f)
                    .fillMaxHeight(0.7f)
                    .background(
                        Brush.radialGradient(
                            colors = listOf(
                                TvTheme.HiResGold.copy(alpha = 0.07f),
                                Color.Transparent
                            )
                        )
                    )
            )
        }
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0f to TvTheme.Bg.copy(alpha = 0.35f),
                        0.15f to Color.Transparent,
                        0.82f to Color.Transparent,
                        1f to TvTheme.Bg.copy(alpha = 0.75f)
                    )
                )
        )
    }
}

@Composable
fun TvQualityBadge(label: String, compact: Boolean = false) {
    val atmos = label.contains("Atmos", ignoreCase = true)
    val bg = if (atmos) TvTheme.AtmosDark.copy(alpha = 0.88f) else Color(0xE612100C)
    val fg = if (atmos) TvTheme.AtmosBlue else TvTheme.HiResGoldBright
    val display = when {
        label.equals("Hi-Res", ignoreCase = true) -> "Hi-Res"
        label.equals("Lossless", ignoreCase = true) -> "Lossless"
        label.contains("Hi-Res AUDIO", ignoreCase = true) -> "Hi-Res"
        else -> label
    }
    Text(
        display.uppercase(),
        color = fg,
        fontSize = if (compact) 10.sp else 11.sp,
        fontFamily = VantaSans,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 1.1.sp,
        maxLines = 1,
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .border(1.dp, fg.copy(alpha = 0.55f), RoundedCornerShape(4.dp))
            .background(bg)
            .padding(horizontal = if (compact) 7.dp else 9.dp, vertical = if (compact) 3.dp else 4.dp)
    )
}

@Composable
fun TvArtwork(
    url: String?,
    size: Dp,
    radius: Dp = 12.dp,
    elevated: Boolean = false
) {
    val shape = RoundedCornerShape(radius)
    val context = androidx.compose.ui.platform.LocalContext.current
    val request = remember(url, size) {
        coil.request.ImageRequest.Builder(context)
            .data(url)
            .crossfade(true)
            .size(coil.size.Size.ORIGINAL)
            .build()
    }
    Box(
        modifier = Modifier
            .size(size)
            .then(
                if (elevated) {
                    Modifier.shadow(
                        elevation = 36.dp,
                        shape = shape,
                        ambientColor = Color.Black.copy(alpha = 0.55f),
                        spotColor = TvTheme.Glow.copy(alpha = 0.65f)
                    )
                } else {
                    Modifier
                }
            )
            .clip(shape)
            .background(
                Brush.linearGradient(
                    listOf(TvTheme.SurfaceSoft, TvTheme.BgElevated, TvTheme.Surface)
                )
            )
            .border(1.dp, TvTheme.Hairline, shape),
        contentAlignment = Alignment.Center
    ) {
        if (!url.isNullOrBlank()) {
            AsyncImage(
                model = request,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Text("♪", color = TvTheme.HiResGold.copy(alpha = 0.55f), fontSize = (size.value * 0.22f).sp)
        }
    }
}

@Composable
fun TvNavPill(
    label: String,
    selected: Boolean,
    metrics: TvMetrics,
    onClick: () -> Unit,
    icon: ImageVector? = null,
    focusRequester: FocusRequester? = null,
    compact: Boolean = false
) {
    TvFocusable(
        onClick = onClick,
        focusRequester = focusRequester,
        cornerRadius = if (compact) 12 else 14,
        focusScale = 1.04f
    ) { focused ->
        val bg = when {
            selected && focused -> TvTheme.Text
            selected -> TvTheme.PillSelected
            focused -> TvTheme.SurfaceSoft
            else -> Color.Transparent
        }
        val fg = when {
            selected -> TvTheme.PillSelectedText
            focused -> TvTheme.Text
            else -> TvTheme.TextSecondary
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .background(bg)
                .border(
                    width = if (!selected && focused) 1.dp else 0.dp,
                    color = if (focused) TvTheme.FocusRing.copy(alpha = 0.35f) else Color.Transparent,
                    shape = RoundedCornerShape(if (compact) 12.dp else 14.dp)
                )
                .padding(
                    horizontal = if (compact) 16.dp else 20.dp,
                    vertical = if (compact) 10.dp else 11.dp
                )
        ) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = fg,
                    modifier = Modifier.size(if (compact) 16.dp else 18.dp)
                )
            }
            Text(
                label,
                color = fg,
                fontSize = if (compact) metrics.caption else metrics.body,
                fontFamily = VantaSans,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                maxLines = 1
            )
        }
    }
}

@Composable
fun AnimatedEqualizerBars(
    modifier: Modifier = Modifier,
    color: Color = TvTheme.HiResGold
) {
    val infiniteTransition = rememberInfiniteTransition(label = "eqBars")
    val bar1 by infiniteTransition.animateFloat(
        initialValue = 0.25f,
        targetValue = 0.95f,
        animationSpec = infiniteRepeatable(
            animation = tween(420, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "b1"
    )
    val bar2 by infiniteTransition.animateFloat(
        initialValue = 0.85f,
        targetValue = 0.3f,
        animationSpec = infiniteRepeatable(
            animation = tween(530, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "b2"
    )
    val bar3 by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(360, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "b3"
    )

    Row(
        modifier = modifier
            .clip(RoundedCornerShape(4.dp))
            .background(Color.Black.copy(alpha = 0.65f))
            .padding(horizontal = 5.dp, vertical = 3.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp),
        verticalAlignment = Alignment.Bottom
    ) {
        Box(
            Modifier
                .width(3.dp)
                .height((16 * bar1).dp.coerceAtLeast(3.dp))
                .background(color, RoundedCornerShape(1.dp))
        )
        Box(
            Modifier
                .width(3.dp)
                .height((16 * bar2).dp.coerceAtLeast(3.dp))
                .background(color, RoundedCornerShape(1.dp))
        )
        Box(
            Modifier
                .width(3.dp)
                .height((16 * bar3).dp.coerceAtLeast(3.dp))
                .background(color, RoundedCornerShape(1.dp))
        )
    }
}

@Composable
fun TvAlbumCard(
    title: String,
    artist: String,
    artworkUrl: String?,
    badge: String?,
    metrics: TvMetrics,
    onClick: () -> Unit,
    banner: String? = null,
    isPlaying: Boolean = false
) {
    TvFocusable(
        onClick = onClick,
        cornerRadius = 14,
        focusScale = metrics.focusScale,
        modifier = Modifier.width(metrics.cardWidth)
    ) { focused ->
        Column(
            modifier = Modifier.padding(bottom = 4.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box {
                TvArtwork(
                    url = artworkUrl,
                    size = metrics.artSize,
                    radius = 12.dp,
                    elevated = focused
                )
                if (badge != null) {
                    Box(
                        Modifier
                            .align(Alignment.TopStart)
                            .padding(10.dp)
                    ) {
                        TvQualityBadge(badge, compact = true)
                    }
                }
                if (isPlaying) {
                    Box(
                        Modifier
                            .align(Alignment.BottomEnd)
                            .padding(8.dp)
                    ) {
                        AnimatedEqualizerBars(color = TvTheme.HiResGoldBright)
                    }
                }
                if (banner != null) {
                    Text(
                        banner.uppercase(),
                        color = TvTheme.Text,
                        fontSize = 10.sp,
                        fontFamily = VantaSans,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.2.sp,
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(10.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(TvTheme.WeekBanner)
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        maxLines = 1
                    )
                }
                if (focused) {
                    Box(
                        Modifier
                            .matchParentSize()
                            .clip(RoundedCornerShape(12.dp))
                            .border(2.dp, TvTheme.FocusRing, RoundedCornerShape(12.dp))
                    )
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    title,
                    color = if (focused) TvTheme.Text else TvTheme.Text.copy(alpha = 0.92f),
                    fontSize = metrics.cardTitle,
                    fontFamily = VantaSans,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    artist,
                    color = TvTheme.TextSecondary,
                    fontSize = metrics.cardSubtitle,
                    fontFamily = VantaSans,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
fun TvNavRail(
    selectedDestination: String,
    onSelectDestination: (String) -> Unit,
    nowTitle: String?,
    nowArtist: String?,
    nowArt: String?,
    isPlaying: Boolean,
    qualityLabel: String?,
    positionMs: Long,
    durationMs: Long,
    metrics: TvMetrics,
    firstFocus: FocusRequester? = null,
    onTogglePlay: () -> Unit,
    onNextTrack: () -> Unit,
    onOpenNowPlaying: () -> Unit,
    modifier: Modifier = Modifier
) {
    var railFocused by remember { mutableStateOf(false) }

    val railWidth by animateDpAsState(
        targetValue = if (railFocused) 210.dp else 74.dp,
        label = "tvNavRailWidth"
    )

    Column(
        modifier = modifier
            .fillMaxHeight()
            .width(railWidth)
            .background(TvTheme.SurfaceGlass)
            .border(width = 1.dp, color = TvTheme.Hairline, shape = RoundedCornerShape(0.dp))
            .onFocusChanged { railFocused = it.hasFocus }
            .padding(horizontal = 8.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.SpaceBetween,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Column(
            horizontalAlignment = if (railFocused) Alignment.Start else Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 6.dp, vertical = 6.dp),
                contentAlignment = if (railFocused) Alignment.CenterStart else Alignment.Center
            ) {
                if (railFocused) {
                    Text(
                        "VANTA",
                        style = VantaType.editorialHero.copy(
                            color = TvTheme.Text,
                            fontSize = 22.sp,
                            letterSpacing = 5.sp,
                            fontWeight = FontWeight.Bold
                        )
                    )
                } else {
                    Text(
                        "V",
                        style = VantaType.editorialHero.copy(
                            color = TvTheme.HiResGoldBright,
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold
                        )
                    )
                }
            }

            Spacer(Modifier.height(8.dp))

            TvNavRailItem(
                label = "Discover",
                icon = Icons.Outlined.Explore,
                selected = selectedDestination == "Discover",
                expanded = railFocused,
                metrics = metrics,
                focusRequester = firstFocus,
                onClick = { onSelectDestination("Discover") }
            )

            TvNavRailItem(
                label = "Radio",
                icon = Icons.Outlined.Radio,
                selected = selectedDestination == "Radio",
                expanded = railFocused,
                metrics = metrics,
                onClick = { onSelectDestination("Radio") }
            )

            TvNavRailItem(
                label = "Search",
                icon = Icons.Outlined.Search,
                selected = selectedDestination == "Search",
                expanded = railFocused,
                metrics = metrics,
                onClick = { onSelectDestination("Search") }
            )

            TvNavRailItem(
                label = "Library",
                icon = Icons.Outlined.Folder,
                selected = selectedDestination == "Library",
                expanded = railFocused,
                metrics = metrics,
                onClick = { onSelectDestination("Library") }
            )

            TvNavRailItem(
                label = "Settings",
                icon = Icons.Outlined.Tune,
                selected = selectedDestination == "Settings",
                expanded = railFocused,
                metrics = metrics,
                onClick = { onSelectDestination("Settings") }
            )
        }

        if (!nowTitle.isNullOrBlank()) {
            TvNavRailMiniPlayer(
                title = nowTitle,
                artist = nowArtist,
                artworkUrl = nowArt,
                isPlaying = isPlaying,
                qualityLabel = qualityLabel,
                expanded = railFocused,
                metrics = metrics,
                onTogglePlay = onTogglePlay,
                onNextTrack = onNextTrack,
                onOpen = onOpenNowPlaying
            )
        }
    }
}

@Composable
fun TvNavRailItem(
    label: String,
    icon: ImageVector,
    selected: Boolean,
    expanded: Boolean,
    metrics: TvMetrics,
    onClick: () -> Unit,
    focusRequester: FocusRequester? = null
) {
    TvFocusable(
        onClick = onClick,
        focusRequester = focusRequester,
        cornerRadius = 14,
        focusScale = 1.04f,
        modifier = Modifier.fillMaxWidth()
    ) { focused ->
        val bg = when {
            selected && focused -> TvTheme.Text
            selected -> TvTheme.PillSelected
            focused -> TvTheme.SurfaceSoft
            else -> Color.Transparent
        }
        val fg = when {
            selected -> TvTheme.PillSelectedText
            focused -> TvTheme.Text
            else -> TvTheme.TextSecondary
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = if (expanded) Arrangement.spacedBy(12.dp) else Arrangement.Center,
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(bg)
                .border(
                    width = if (!selected && focused) 1.dp else 0.dp,
                    color = if (focused) TvTheme.FocusRing else Color.Transparent,
                    shape = RoundedCornerShape(14.dp)
                )
                .padding(horizontal = if (expanded) 14.dp else 8.dp, vertical = 10.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = fg,
                modifier = Modifier.size(20.dp)
            )
            if (expanded) {
                Text(
                    text = label,
                    color = fg,
                    fontSize = metrics.cardSubtitle,
                    fontFamily = VantaSans,
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun TvNavRailMiniPlayer(
    title: String,
    artist: String?,
    artworkUrl: String?,
    isPlaying: Boolean,
    qualityLabel: String?,
    expanded: Boolean,
    metrics: TvMetrics,
    onTogglePlay: () -> Unit,
    onNextTrack: () -> Unit,
    onOpen: () -> Unit
) {
    if (!expanded) {
        TvFocusable(
            onClick = onOpen,
            cornerRadius = 12,
            focusScale = 1.08f
        ) { focused ->
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (focused) TvTheme.SurfaceSoft else TvTheme.BgElevated)
                    .border(
                        1.dp,
                        if (focused) TvTheme.FocusRing else TvTheme.Hairline,
                        RoundedCornerShape(12.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                TvArtwork(url = artworkUrl, size = 48.dp, radius = 10.dp)
                if (isPlaying) {
                    Box(
                        Modifier
                            .align(Alignment.BottomEnd)
                            .padding(2.dp)
                    ) {
                        AnimatedEqualizerBars(
                            modifier = Modifier.padding(1.dp),
                            color = TvTheme.HiResGold
                        )
                    }
                }
            }
        }
    } else {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(TvTheme.BgElevated)
                .border(1.dp, TvTheme.Hairline, RoundedCornerShape(16.dp))
                .padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            TvFocusable(
                onClick = onOpen,
                cornerRadius = 10,
                focusScale = 1.02f,
                modifier = Modifier.fillMaxWidth()
            ) { focused ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (focused) TvTheme.SurfaceSoft else Color.Transparent)
                        .padding(4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    TvArtwork(url = artworkUrl, size = 36.dp, radius = 8.dp)
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = title,
                            color = TvTheme.Text,
                            fontSize = 11.sp,
                            fontFamily = VantaSans,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = artist.orEmpty(),
                            color = TvTheme.TextSecondary,
                            fontSize = 10.sp,
                            fontFamily = VantaSans,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TvFocusable(onClick = onTogglePlay, cornerRadius = 30, focusScale = 1.08f) { focused ->
                    Icon(
                        imageVector = if (isPlaying) Icons.Outlined.Pause else Icons.Outlined.PlayArrow,
                        contentDescription = if (isPlaying) "Pause" else "Play",
                        tint = if (focused) TvTheme.HiResGoldDark else TvTheme.Text,
                        modifier = Modifier
                            .background(
                                if (focused) TvTheme.HiResGold else TvTheme.SurfaceSoft,
                                CircleShape
                            )
                            .padding(8.dp)
                            .size(16.dp)
                    )
                }

                TvFocusable(onClick = onNextTrack, cornerRadius = 30, focusScale = 1.08f) { focused ->
                    Icon(
                        imageVector = Icons.Outlined.SkipNext,
                        contentDescription = "Next",
                        tint = if (focused) TvTheme.HiResGold else TvTheme.TextSecondary,
                        modifier = Modifier.padding(6.dp).size(18.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun TvTopChrome(
    brand: String = "VANTA",
    discoverSelected: Boolean,
    librarySelected: Boolean,
    searchSelected: Boolean,
    nowPlayingOpen: Boolean = false,
    nowTitle: String?,
    nowArtist: String?,
    nowArt: String?,
    isPlaying: Boolean,
    qualityLabel: String?,
    positionMs: Long,
    durationMs: Long,
    metrics: TvMetrics,
    firstFocus: FocusRequester,
    onDiscover: () -> Unit,
    onLibrary: () -> Unit,
    onSearch: () -> Unit,
    onToggle: () -> Unit,
    onNext: () -> Unit,
    onOpenNowPlaying: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = metrics.pagePadding, vertical = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text(
            brand,
            style = VantaType.editorialHero.copy(
                color = TvTheme.Text,
                fontSize = metrics.brand,
                letterSpacing = 6.sp,
                fontWeight = FontWeight.Medium
            )
        )
        Spacer(Modifier.width(8.dp))
        TvNavPill(
            label = "Discover",
            selected = discoverSelected && !nowPlayingOpen,
            metrics = metrics,
            onClick = onDiscover,
            icon = Icons.Outlined.Explore,
            focusRequester = firstFocus
        )
        TvNavPill(
            label = "Library",
            selected = librarySelected,
            metrics = metrics,
            onClick = onLibrary,
            icon = Icons.Outlined.Folder
        )
        TvNavPill(
            label = "Search",
            selected = searchSelected,
            metrics = metrics,
            onClick = onSearch,
            icon = Icons.Outlined.Search
        )
        Spacer(Modifier.weight(1f))
        if (!nowPlayingOpen) {
            TvLuxuryMiniPlayer(
                title = nowTitle,
                artist = nowArtist,
                artworkUrl = nowArt,
                isPlaying = isPlaying,
                qualityLabel = qualityLabel,
                positionMs = positionMs,
                durationMs = durationMs,
                metrics = metrics,
                onToggle = onToggle,
                onNext = onNext,
                onOpen = onOpenNowPlaying
            )
        }
    }
}

@Composable
fun TvLuxuryMiniPlayer(
    title: String?,
    artist: String?,
    artworkUrl: String?,
    isPlaying: Boolean,
    qualityLabel: String?,
    positionMs: Long,
    durationMs: Long,
    metrics: TvMetrics,
    onToggle: () -> Unit,
    onNext: () -> Unit,
    onOpen: () -> Unit
) {
    val playing = !title.isNullOrBlank()
    val progress = if (durationMs > 0L) {
        (positionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
    } else {
        0f
    }
    val shape = RoundedCornerShape(16.dp)
    Row(
        modifier = Modifier
            .widthIn(min = 300.dp, max = 460.dp)
            .clip(shape)
            .background(TvTheme.SurfaceGlass)
            .border(1.dp, TvTheme.Hairline, shape)
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        TvFocusable(onClick = onOpen, cornerRadius = 12, focusScale = 1.02f) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.widthIn(max = 280.dp)
            ) {
                if (playing) {
                    TvArtwork(url = artworkUrl, size = 52.dp, radius = 10.dp)
                } else {
                    Box(
                        modifier = Modifier
                            .size(52.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(TvTheme.BgElevated)
                            .border(1.dp, TvTheme.Hairline, RoundedCornerShape(10.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("♪", color = TvTheme.HiResGold, fontSize = 20.sp)
                    }
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        if (playing) title.orEmpty() else "Ready to listen",
                        color = TvTheme.Text,
                        fontSize = metrics.cardSubtitle,
                        fontFamily = VantaSans,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        when {
                            playing -> listOfNotNull(artist?.takeIf { it.isNotBlank() }, qualityLabel).joinToString(" · ")
                            else -> "Lossless · Atmos · Living room"
                        },
                        color = TvTheme.TextSecondary,
                        fontSize = metrics.caption,
                        fontFamily = VantaSans,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Box(
                        modifier = Modifier
                            .padding(top = 6.dp)
                            .fillMaxWidth()
                            .height(2.dp)
                            .clip(RoundedCornerShape(1.dp))
                            .background(TvTheme.ProgressTrack)
                    ) {
                        Box(
                            Modifier
                                .fillMaxWidth(if (playing) progress else 0f)
                                .height(2.dp)
                                .background(TvTheme.ProgressFill)
                        )
                    }
                }
            }
        }
        TvFocusable(onClick = onToggle, cornerRadius = 40, focusScale = 1.06f) { focused ->
            Icon(
                imageVector = if (isPlaying) Icons.Outlined.Pause else Icons.Outlined.PlayArrow,
                contentDescription = if (isPlaying) "Pause" else "Play",
                tint = if (focused) TvTheme.HiResGoldDark else TvTheme.Text,
                modifier = Modifier
                    .background(
                        if (focused) TvTheme.HiResGold else TvTheme.BgElevated,
                        CircleShape
                    )
                    .padding(12.dp)
                    .size(18.dp)
            )
        }
        TvFocusable(onClick = onNext, cornerRadius = 40, focusScale = 1.06f) { focused ->
            Icon(
                imageVector = Icons.Outlined.SkipNext,
                contentDescription = "Next",
                tint = if (focused) TvTheme.HiResGold else TvTheme.TextSecondary,
                modifier = Modifier.padding(8.dp).size(20.dp)
            )
        }
    }
}

@Composable
fun TvDiscoverTabRow(
    selectedHome: Boolean,
    selectedPlaylists: Boolean,
    selectedRadio: Boolean,
    selectedForYou: Boolean,
    metrics: TvMetrics,
    onHome: () -> Unit,
    onPlaylists: () -> Unit,
    onRadio: () -> Unit,
    onForYou: () -> Unit
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier
            .clip(RoundedCornerShape(14.dp))
            .background(TvTheme.Surface.copy(alpha = 0.55f))
            .padding(4.dp)
    ) {
        TvNavPill(
            label = "Home",
            selected = selectedHome,
            metrics = metrics,
            onClick = onHome,
            icon = Icons.Outlined.Home,
            compact = true
        )
        TvNavPill(
            label = "Playlists",
            selected = selectedPlaylists,
            metrics = metrics,
            onClick = onPlaylists,
            compact = true
        )
        TvNavPill(
            label = "Radio",
            selected = selectedRadio,
            metrics = metrics,
            onClick = onRadio,
            compact = true
        )
        TvNavPill(
            label = "For You",
            selected = selectedForYou,
            metrics = metrics,
            onClick = onForYou,
            compact = true
        )
    }
}

fun stationGradient(name: String): Brush {
    val h = kotlin.math.abs(name.hashCode())
    return when (h % 5) {
        0 -> Brush.linearGradient(listOf(Color(0xFF3E2412), Color(0xFF221307), TvTheme.BgElevated))
        1 -> Brush.linearGradient(listOf(Color(0xFF22163B), Color(0xFF130C22), TvTheme.BgElevated))
        2 -> Brush.linearGradient(listOf(Color(0xFF0F2B28), Color(0xFF071715), TvTheme.BgElevated))
        3 -> Brush.linearGradient(listOf(Color(0xFF381522), Color(0xFF1F0B13), TvTheme.BgElevated))
        else -> Brush.linearGradient(listOf(Color(0xFF1C2836), Color(0xFF0D141C), TvTheme.BgElevated))
    }
}

@Composable
fun TvStationCard(
    name: String,
    description: String,
    emoji: String,
    metrics: TvMetrics,
    onClick: () -> Unit,
    badge: String = "Radio"
) {
    TvFocusable(
        onClick = onClick,
        cornerRadius = 16,
        focusScale = metrics.focusScale,
        modifier = Modifier.width(metrics.cardWidth + 28.dp)
    ) { focused ->
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(if (focused) TvTheme.SurfaceSoft else TvTheme.Surface)
                .border(1.dp, if (focused) TvTheme.FocusRing else TvTheme.Hairline, RoundedCornerShape(16.dp))
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(metrics.artSize - 32.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(stationGradient(name))
                    .border(1.dp, TvTheme.Hairline, RoundedCornerShape(14.dp)),
                contentAlignment = Alignment.Center
            ) {
                Text(emoji, fontSize = 44.sp)
            }
            TvQualityBadge(badge, compact = true)
            Text(
                name,
                color = TvTheme.Text,
                fontSize = metrics.cardTitle,
                fontFamily = VantaSans,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                description,
                color = TvTheme.TextSecondary,
                fontSize = metrics.cardSubtitle,
                fontFamily = VantaSans,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
fun TvMixCard(
    title: String,
    subtitle: String,
    artworkUrl: String?,
    metrics: TvMetrics,
    onPlay: () -> Unit,
    loading: Boolean = false
) {
    TvFocusable(
        onClick = onPlay,
        cornerRadius = 16,
        focusScale = metrics.focusScale,
        modifier = Modifier.width(metrics.cardWidth + 16.dp)
    ) { focused ->
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Box {
                TvArtwork(url = artworkUrl, size = metrics.artSize, radius = 14.dp, elevated = focused)
                Box(
                    Modifier
                        .align(Alignment.BottomStart)
                        .padding(10.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(TvTheme.HiResGold)
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        if (loading) "LOADING" else "MIX",
                        color = TvTheme.HiResGoldDark,
                        fontSize = 10.sp,
                        fontFamily = VantaSans,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )
                }
            }
            Text(
                title,
                color = TvTheme.Text,
                fontSize = metrics.cardTitle,
                fontFamily = VantaSans,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                subtitle,
                color = TvTheme.TextSecondary,
                fontSize = metrics.cardSubtitle,
                fontFamily = VantaSans,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
fun TvSectionHeader(title: String, metrics: TvMetrics, subtitle: String? = null) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            title,
            color = TvTheme.Text,
            fontSize = metrics.rowTitle,
            fontFamily = VantaSans,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 0.2.sp
        )
        if (!subtitle.isNullOrBlank()) {
            Text(
                subtitle,
                color = TvTheme.TextMuted,
                fontSize = metrics.caption,
                fontFamily = VantaSans
            )
        }
    }
}
