package com.audiophile.musicplayer.tv

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.audiophile.musicplayer.data.lyrics.LyricsData
import com.audiophile.musicplayer.data.lyrics.LyricsLine
import com.audiophile.musicplayer.data.lyrics.activeLyricLineIndex
import com.audiophile.musicplayer.ui.theme.VantaSans

private val TvLyricWarmWhite = Color(0xFFFFF6EE)

/**
 * Living Lyrics 2.0 for Android TV:
 * 10-foot synchronized lyrics stage with dynamic blurred album art atmosphere,
 * real-time word-by-word karaoke sweeps, glowing active line bloom,
 * pulsating instrumental break count-ins, and TV remote D-pad selection.
 */
@Composable
fun TvLyricsStage(
    lyrics: LyricsData?,
    isLoading: Boolean,
    positionMs: Long,
    durationMs: Long,
    isPlaying: Boolean,
    metrics: TvMetrics,
    modifier: Modifier = Modifier,
    artworkUrl: String? = null,
    accentColor: Color = TvTheme.HiResGold,
    onSeekToMs: ((Long) -> Unit)? = null,
    onRetry: (() -> Unit)? = null
) {
    val listState = rememberLazyListState()

    val timedLines = remember(lyrics) {
        lyrics?.lines.orEmpty()
    }

    val activeIndex = remember(positionMs, timedLines, lyrics?.isSynced) {
        if (lyrics?.isSynced == true && timedLines.isNotEmpty()) {
            activeLyricLineIndex(timedLines, positionMs).coerceIn(-1, timedLines.lastIndex)
        } else {
            -1
        }
    }

    // Smoothly scroll active line to top-middle third of the TV screen
    LaunchedEffect(activeIndex, isPlaying) {
        if (activeIndex >= 0 && isPlaying) {
            val targetIndex = (activeIndex - 1).coerceAtLeast(0)
            listState.animateScrollToItem(targetIndex, scrollOffset = 0)
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        // Living artwork background atmosphere for Android TV
        TvLivingLyricsAtmosphere(
            artworkUrl = artworkUrl,
            accentColor = accentColor,
            modifier = Modifier.fillMaxSize()
        )

        when {
            isLoading -> {
                TvLyricsLoading(metrics)
            }
            timedLines.isEmpty() -> {
                TvLyricsEmpty(
                    metrics = metrics,
                    onRetry = onRetry
                )
            }
            else -> {
                Column(modifier = Modifier.fillMaxSize()) {
                    // Stage Header Info
                    TvLyricsStageHeader(
                        isSynced = lyrics?.isSynced == true,
                        sourceLabel = lyrics?.sourceLabel ?: lyrics?.providerId?.uppercase() ?: "LRCLIB",
                        accentColor = accentColor,
                        metrics = metrics
                    )

                    // Scrolling Lyrics Stream
                    LazyColumn(
                        state = listState,
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentPadding = PaddingValues(
                            top = 20.dp,
                            bottom = 120.dp,
                            start = metrics.pagePadding,
                            end = metrics.pagePadding
                        ),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        itemsIndexed(
                            items = timedLines,
                            key = { index, line -> "${line.startTimeMs ?: index}_${line.text.take(12)}" }
                        ) { index, line ->
                            val isActive = index == activeIndex
                            val isPast = activeIndex >= 0 && index < activeIndex

                            // Instrumental break indicator before line if musical break >= 3.5s
                            val prevLine = timedLines.getOrNull(index - 1)
                            val prevEnd = prevLine?.endTimeMs ?: prevLine?.startTimeMs
                            val currentStart = line.startTimeMs
                            val breakGap = if (prevEnd != null && currentStart != null) currentStart - prevEnd else 0L

                            val isInstrumentalBreakNow = lyrics?.isSynced == true && breakGap >= 3500L &&
                                prevEnd != null && currentStart != null &&
                                positionMs >= prevEnd && positionMs < currentStart

                            if (breakGap >= 3500L) {
                                TvInstrumentalBreakRow(
                                    isActive = isInstrumentalBreakNow,
                                    accentColor = accentColor,
                                    metrics = metrics,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 6.dp)
                                )
                            }

                            TvLyricLineRow(
                                line = line,
                                isActive = isActive,
                                isPast = isPast,
                                positionMs = positionMs,
                                accentColor = accentColor,
                                metrics = metrics,
                                onClick = {
                                    line.startTimeMs?.let { start ->
                                        onSeekToMs?.invoke(start)
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Living artwork blurred atmosphere for 10-foot TV displays.
 */
@Composable
private fun TvLivingLyricsAtmosphere(
    artworkUrl: String?,
    accentColor: Color,
    modifier: Modifier = Modifier
) {
    val ambientMotion by rememberInfiniteTransition(label = "tv-ambient-motion").animateFloat(
        initialValue = -1f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(24_000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "tv-drift-val"
    )

    Box(modifier = modifier.background(Color(0xFF07060A))) {
        if (!artworkUrl.isNullOrBlank()) {
            AsyncImage(
                model = artworkUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        scaleX = 1.25f
                        scaleY = 1.25f
                        translationX = ambientMotion * 20f
                        translationY = -ambientMotion * 16f
                    }
                    .blur(64.dp)
                    .alpha(0.35f)
            )
        }

        Canvas(modifier = Modifier.fillMaxSize()) {
            // Dark base gradient scrim for deep black contrast
            drawRect(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        Color.Black.copy(alpha = 0.58f),
                        Color.Black.copy(alpha = 0.35f),
                        Color.Black.copy(alpha = 0.88f)
                    )
                )
            )

            // Dynamic ambient light bloom
            val center = Offset(
                x = size.width * (0.46f + ambientMotion * 0.06f),
                y = size.height * 0.38f
            )
            val radius = size.minDimension * 0.55f
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(accentColor.copy(alpha = 0.24f), Color.Transparent),
                    center = center,
                    radius = radius
                ),
                radius = radius,
                center = center
            )

            // Side edge vignette
            drawRect(
                brush = Brush.horizontalGradient(
                    listOf(
                        Color.Black.copy(alpha = 0.45f),
                        Color.Transparent,
                        Color.Black.copy(alpha = 0.45f)
                    )
                )
            )
        }
    }
}

@Composable
private fun TvLyricsStageHeader(
    isSynced: Boolean,
    sourceLabel: String,
    accentColor: Color,
    metrics: TvMetrics
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = metrics.pagePadding, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(if (isSynced) accentColor else TvTheme.TextMuted)
            )
            Text(
                text = if (isSynced) "SYNCED KARAOKE" else "TEXT LYRICS",
                color = if (isSynced) accentColor else TvTheme.TextSecondary,
                fontSize = metrics.caption,
                fontFamily = VantaSans,
                fontWeight = FontWeight.Bold,
                letterSpacing = 2.sp
            )
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Outlined.Mic,
                contentDescription = null,
                tint = TvTheme.TextMuted,
                modifier = Modifier.size(14.dp)
            )
            Text(
                text = sourceLabel,
                color = TvTheme.TextMuted,
                fontSize = 11.sp,
                fontFamily = VantaSans,
                letterSpacing = 1.sp
            )
        }
    }
}

/**
 * TV Lyric row with active bloom, word-by-word karaoke sweep, and remote D-pad focus.
 */
@Composable
private fun TvLyricLineRow(
    line: LyricsLine,
    isActive: Boolean,
    isPast: Boolean,
    positionMs: Long,
    accentColor: Color,
    metrics: TvMetrics,
    onClick: () -> Unit
) {
    val alpha by animateFloatAsState(
        targetValue = when {
            isActive -> 1f
            isPast -> 0.35f
            else -> 0.55f
        },
        animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessLow),
        label = "lyricAlpha"
    )

    val scale by animateFloatAsState(
        targetValue = if (isActive) 1.03f else 1.0f,
        animationSpec = tween(320, easing = FastOutSlowInEasing),
        label = "tv-lyric-scale"
    )

    val textColor by animateColorAsState(
        targetValue = when {
            isActive -> TvLyricWarmWhite
            isPast -> TvTheme.TextSecondary
            else -> TvTheme.TextMuted
        },
        label = "lyricColor"
    )

    val textShadow = if (isActive) {
        Shadow(
            color = accentColor.copy(alpha = 0.65f),
            offset = Offset(0f, 0f),
            blurRadius = 26f
        )
    } else {
        Shadow(
            color = Color.Black.copy(alpha = 0.40f),
            offset = Offset(0f, 3f),
            blurRadius = 10f
        )
    }

    val fontSize = if (isActive) (metrics.heroTitle.value * 0.74f).sp else (metrics.rowTitle.value * 1.1f).sp
    val lineHeight = if (isActive) (metrics.heroTitle.value * 0.92f).sp else (metrics.rowTitle.value * 1.35f).sp

    TvFocusable(
        onClick = onClick,
        cornerRadius = 16,
        focusScale = 1.02f,
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
    ) { focused ->
        val bg = when {
            focused -> TvTheme.SurfaceSoft.copy(alpha = 0.9f)
            isActive -> accentColor.copy(alpha = 0.12f)
            else -> Color.Transparent
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(bg)
                .border(
                    width = if (focused) 1.5.dp else if (isActive) 1.dp else 0.dp,
                    color = if (focused) TvTheme.FocusRing else if (isActive) accentColor.copy(alpha = 0.38f) else Color.Transparent,
                    shape = RoundedCornerShape(16.dp)
                )
                .padding(horizontal = 22.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            if (isActive && line.wordTimings.isNotEmpty()) {
                // Karaoke word-by-word synchronized sweep on TV
                val annotatedLyric = remember(line.wordTimings, positionMs) {
                    buildAnnotatedString {
                        line.wordTimings.forEachIndexed { wordIndex, word ->
                            val wordActive = positionMs >= word.startTimeMs
                            withStyle(
                                SpanStyle(
                                    color = if (wordActive) TvLyricWarmWhite else TvLyricWarmWhite.copy(alpha = 0.38f),
                                    fontWeight = if (wordActive) FontWeight.ExtraBold else FontWeight.Bold
                                )
                            ) {
                                append(word.text)
                            }
                            if (wordIndex < line.wordTimings.lastIndex) {
                                append(" ")
                            }
                        }
                    }
                }

                Text(
                    text = annotatedLyric,
                    fontFamily = VantaSans,
                    fontSize = fontSize,
                    lineHeight = lineHeight,
                    letterSpacing = (-0.3).sp,
                    textAlign = TextAlign.Start,
                    style = TextStyle(shadow = textShadow),
                    modifier = Modifier.fillMaxWidth()
                )
            } else {
                Text(
                    text = line.text,
                    color = if (focused) TvLyricWarmWhite else textColor.copy(alpha = alpha),
                    fontSize = fontSize,
                    fontFamily = VantaSans,
                    fontWeight = if (isActive) FontWeight.ExtraBold else FontWeight.Medium,
                    lineHeight = lineHeight,
                    letterSpacing = (-0.3).sp,
                    textAlign = TextAlign.Start,
                    style = TextStyle(shadow = textShadow),
                    modifier = Modifier.fillMaxWidth()
                )
            }

            // Subtitle translation if available
            val translation = line.translatedText
            if (!translation.isNullOrBlank()) {
                Text(
                    text = translation,
                    color = if (isActive) accentColor.copy(alpha = 0.9f) else TvTheme.TextMuted.copy(alpha = 0.55f),
                    fontSize = metrics.body,
                    fontFamily = VantaSans,
                    fontWeight = FontWeight.Normal
                )
            }
        }
    }
}

/**
 * 10-foot TV Instrumental break indicator pulsing with the tempo.
 */
@Composable
private fun TvInstrumentalBreakRow(
    isActive: Boolean,
    accentColor: Color,
    metrics: TvMetrics,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "tv-instrumental-pulse")
    val dotPhase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 3f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "tv-dot-phase"
    )

    Row(
        modifier = modifier
            .clip(RoundedCornerShape(999.dp))
            .background(if (isActive) accentColor.copy(alpha = 0.16f) else Color.Transparent)
            .border(
                1.dp,
                if (isActive) accentColor.copy(alpha = 0.40f) else Color.White.copy(alpha = 0.06f),
                RoundedCornerShape(999.dp)
            )
            .padding(horizontal = 18.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Icon(
            imageVector = Icons.Outlined.MusicNote,
            contentDescription = null,
            tint = if (isActive) accentColor else Color.White.copy(alpha = 0.35f),
            modifier = Modifier.size(16.dp)
        )
        Text(
            text = "INSTRUMENTAL",
            color = if (isActive) TvLyricWarmWhite else Color.White.copy(alpha = 0.35f),
            fontSize = metrics.caption,
            fontWeight = FontWeight.Bold,
            fontFamily = VantaSans,
            letterSpacing = 1.5.sp
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            repeat(3) { dotIndex ->
                val dotAlpha = if (isActive) {
                    val dist = kotlin.math.abs(dotPhase - dotIndex)
                    (1f - dist.coerceIn(0f, 1f)).coerceIn(0.25f, 1f)
                } else {
                    0.25f
                }
                Box(
                    modifier = Modifier
                        .size(5.dp)
                        .clip(CircleShape)
                        .background((if (isActive) accentColor else Color.White).copy(alpha = dotAlpha))
                )
            }
        }
    }
}

@Composable
private fun TvLyricsLoading(metrics: TvMetrics) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Icon(
                imageVector = Icons.Outlined.Sync,
                contentDescription = null,
                tint = TvTheme.HiResGold,
                modifier = Modifier.size(44.dp)
            )
            Text(
                text = "Fetching synchronized lyrics…",
                color = TvTheme.Text,
                fontSize = metrics.rowTitle,
                fontFamily = VantaSans,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = "Connecting to LRCLIB & cloud database",
                color = TvTheme.TextMuted,
                fontSize = metrics.caption,
                fontFamily = VantaSans
            )
        }
    }
}

@Composable
private fun TvLyricsEmpty(
    metrics: TvMetrics,
    onRetry: (() -> Unit)? = null
) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(18.dp),
            modifier = Modifier.padding(horizontal = 48.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .clip(CircleShape)
                    .background(TvTheme.BgElevated)
                    .border(1.dp, TvTheme.Hairline, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Outlined.MusicNote,
                    contentDescription = null,
                    tint = TvTheme.HiResGold.copy(alpha = 0.7f),
                    modifier = Modifier.size(36.dp)
                )
            }

            Text(
                text = "No synchronized lyrics for this song",
                color = TvTheme.Text,
                fontSize = metrics.rowTitle,
                fontFamily = VantaSans,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center
            )

            Text(
                text = "Enjoy the uncompressed audiophile master. Check back later or retry the cloud index.",
                color = TvTheme.TextMuted,
                fontSize = metrics.caption,
                fontFamily = VantaSans,
                textAlign = TextAlign.Center,
                modifier = Modifier.width(360.dp)
            )

            if (onRetry != null) {
                TvFocusable(
                    onClick = onRetry,
                    cornerRadius = 14,
                    focusScale = 1.05f
                ) { focused ->
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(14.dp))
                            .background(if (focused) TvTheme.HiResGold else TvTheme.SurfaceSoft)
                            .padding(horizontal = 24.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "Search Lyrics Again",
                            color = if (focused) TvTheme.HiResGoldDark else TvTheme.Text,
                            fontSize = metrics.body,
                            fontFamily = VantaSans,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }
    }
}
