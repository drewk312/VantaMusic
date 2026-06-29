@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
package com.audiophile.musicplayer.ui.nowplaying

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.Icon
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.TextUnit
import com.audiophile.musicplayer.ui.VantaType
import com.audiophile.musicplayer.data.lyrics.LyricsData
import com.audiophile.musicplayer.data.lyrics.LyricsSyncPreferences
import com.audiophile.musicplayer.data.lyrics.LyricsWordTiming
import com.audiophile.musicplayer.data.display.VantaQualityInfo
import com.audiophile.musicplayer.playback.dsp.VantaEqualizerPreferences
import com.audiophile.musicplayer.playback.NowPlayingState
import com.audiophile.musicplayer.ui.AppAccent
import com.audiophile.musicplayer.ui.AppText
import com.audiophile.musicplayer.ui.AppTextMuted
import com.audiophile.musicplayer.ui.AppTextSecondary
import com.audiophile.musicplayer.ui.VantaCompactQualityChip
import com.audiophile.musicplayer.ui.VantaStatusBadge

private val LyricWarmWhite = Color(0xFFFFF4E8)
private val LyricWarmGold = Color(0xFFFFE8C8)
private val LyricTextShadow = Shadow(
    color = Color.Black.copy(alpha = 0.55f),
    offset = androidx.compose.ui.geometry.Offset(0f, 4f),
    blurRadius = 18f
)

internal fun lyricLineStyle(
    fontSize: TextUnit,
    color: Color,
    fontWeight: FontWeight = FontWeight.SemiBold
) = TextStyle(
    fontFamily = FontFamily.SansSerif,
    fontWeight = fontWeight,
    fontSize = fontSize,
    lineHeight = fontSize * 1.15f,
    letterSpacing = (-0.3).sp,
    color = color,
    shadow = LyricTextShadow
)

@Composable
fun VantaLyricsStage(
    lyricsData: LyricsData?,
    lyricsLoading: Boolean,
    nowPlayingState: NowPlayingState,
    displayTitle: String,
    displayArtist: String,
    displayAlbum: String?,
    qualityInfo: VantaQualityInfo?,
    translationEnabled: Boolean = false,
    onToggleTranslation: () -> Unit = {},
    onSeekTo: (Long) -> Unit,
    onRetryLyrics: () -> Unit,
    onOpenQualityDetails: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(0.dp)
    ) {
        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            if (lyricsData != null) {
                LyricsView(
                    lyricsData = lyricsData,
                    positionMs = nowPlayingState.positionMs,
                    durationMs = nowPlayingState.durationMs,
                    onSeekTo = onSeekTo,
                    isPlaying = nowPlayingState.isPlaying,
                    translationEnabled = translationEnabled,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                LyricsFallbackView(
                    isLoading = lyricsLoading,
                    onRetry = onRetryLyrics,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
        if (lyricsData != null) {
            TranslationToggleRow(
                translationEnabled = translationEnabled,
                onToggleTranslation = onToggleTranslation,
                hasTranslations = lyricsData.lines?.any { it.translatedText != null } == true
            )
        }
    }
}

@Composable
fun LyricsView(
    lyricsData: LyricsData,
    positionMs: Long,
    durationMs: Long,
    onSeekTo: (Long) -> Unit,
    translationEnabled: Boolean = false,
    modifier: Modifier = Modifier,
    isPlaying: Boolean = true
) {
    val context = LocalContext.current
    val listState = rememberLazyListState()
    val syncPreferences = remember(context) { LyricsSyncPreferences(context) }
    val pipelineLeadMs = remember(context) { VantaEqualizerPreferences(context).lyricsPipelineLeadMs() }

    var livePositionMs by remember { mutableLongStateOf(positionMs.coerceAtLeast(0L)) }
    val latestPositionMs by rememberUpdatedState(positionMs)
    val latestIsPlaying by rememberUpdatedState(isPlaying)

    var seekGeneration by remember { mutableIntStateOf(0) }
    var seekTargetMs by remember { mutableLongStateOf(0L) }
    var autoScrolling by remember { mutableStateOf(false) }
    var userScrollHoldUntilMs by remember { mutableLongStateOf(0L) }

    LaunchedEffect(lyricsData.trackKey, seekGeneration) {
        val initialMs = if (seekGeneration > 0) seekTargetMs else latestPositionMs.coerceAtLeast(0L)
        var extrapolateFromMs = initialMs
        var extrapolateFromWall = System.currentTimeMillis()
        livePositionMs = extrapolateFromMs
        while (true) {
            if (!latestIsPlaying) {
                livePositionMs = latestPositionMs.coerceAtLeast(0L)
                extrapolateFromMs = livePositionMs
                extrapolateFromWall = System.currentTimeMillis()
            } else {
                val reported = latestPositionMs.coerceAtLeast(0L)
                val predicted = extrapolateFromMs + (System.currentTimeMillis() - extrapolateFromWall)
                if (kotlin.math.abs(reported - predicted) > 1_500L) {
                    extrapolateFromMs = reported
                    extrapolateFromWall = System.currentTimeMillis()
                }
                livePositionMs = extrapolateFromMs + (System.currentTimeMillis() - extrapolateFromWall)
            }
            kotlinx.coroutines.delay(50)
        }
    }

    var showOffsetSlider by remember(lyricsData.trackKey) { mutableStateOf(false) }
    var offsetMs by remember(lyricsData.trackKey) {
        mutableLongStateOf(syncPreferences.getOffsetMs(lyricsData.trackKey))
    }

    val adjustedPositionMs = (livePositionMs + offsetMs + pipelineLeadMs).coerceAtLeast(0L)
    val effectiveDurationMs = durationMs.takeIf { it > 0L } ?: 200_000L

    val seekToLine: (Long) -> Unit = { targetMs ->
        val maxMs = effectiveDurationMs.coerceAtLeast(1L)
        val rawPlaybackMs = (targetMs - offsetMs - pipelineLeadMs).coerceAtLeast(0L)
        val clamped = rawPlaybackMs.coerceIn(0L, (maxMs - 1_000L).coerceAtLeast(0L))
        livePositionMs = clamped
        seekTargetMs = clamped
        seekGeneration++
        userScrollHoldUntilMs = 0L
        onSeekTo(clamped)
    }

    fun lineSeekTargetMs(index: Int, line: com.audiophile.musicplayer.data.lyrics.LyricsLine): Long {
        line.startTimeMs?.takeIf { it >= 0L }?.let { return it }
        val lineCount = lyricsData.lines.size.coerceAtLeast(1)
        val fallbackDuration = effectiveDurationMs.coerceAtLeast(210_000L)
        return ((index.toFloat() / lineCount.toFloat()) * fallbackDuration).toLong().coerceAtLeast(2_000L)
    }

    val hasTimings = lyricsData.lines?.any { (it.startTimeMs ?: 0L) > 0L } == true
    val activeIndex = remember(lyricsData, adjustedPositionMs) {
        if (lyricsData.lines.isNullOrEmpty()) return@remember -1
        lyricsData.lines.indexOfLast { (it.startTimeMs ?: -1L) <= adjustedPositionMs }
    }

    LaunchedEffect(listState) {
        while (true) {
            if (listState.isScrollInProgress && !autoScrolling) {
                userScrollHoldUntilMs = System.currentTimeMillis() + 4_500L
            }
            kotlinx.coroutines.delay(100)
        }
    }

    LaunchedEffect(activeIndex) {
        if (activeIndex >= 0) {
            while (true) {
                val waitMs = (userScrollHoldUntilMs - System.currentTimeMillis()).coerceAtLeast(0L)
                if (waitMs > 0L) {
                    kotlinx.coroutines.delay(waitMs)
                } else {
                    break
                }
            }
            autoScrolling = true
            try {
                listState.animateScrollToItem(index = activeIndex, scrollOffset = 0)
            } finally {
                autoScrolling = false
            }
        }
    }

    Box(modifier = modifier) {
        val focusTopPadding = 12.dp
        val focusBottomPadding = if (showOffsetSlider) 120.dp else 48.dp

        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(
                start = 8.dp, end = 8.dp,
                top = 20.dp, bottom = 120.dp
            )
        ) {
            val lines = lyricsData.lines ?: emptyList()
            itemsIndexed(lines, key = { index, line -> line.startTimeMs ?: index }) { index, line ->
                val isActive = hasTimings && index == activeIndex
                val distance = if (activeIndex >= 0) kotlin.math.abs(index - activeIndex) else Int.MAX_VALUE
                val targetAlpha = when {
                    !hasTimings -> 0.72f
                    isActive -> 1f
                    distance == 1 -> 0.50f
                    distance == 2 -> 0.38f
                    else -> 0.22f
                }
                val alpha by animateFloatAsState(targetValue = targetAlpha, animationSpec = tween(420), label = "lyricAlpha")
                val scale by animateFloatAsState(
                    targetValue = if (isActive) 1f else 0.96f,
                    animationSpec = tween(420), label = "lyricScale"
                )
                val canSeekLine = lines.isNotEmpty()

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .then(
                            if (canSeekLine) {
                                Modifier.clickable(onClick = { seekToLine(lineSeekTargetMs(index, line)) })
                            } else Modifier
                        )
                        .graphicsLayer(alpha = alpha, scaleX = scale, scaleY = scale, transformOrigin = TransformOrigin(0f, 0.5f))
                        .padding(horizontal = 8.dp, vertical = 10.dp)
                ) {
                    Column {
                        val lineStart = line.startTimeMs
                        if (isActive && lineStart != null) {
                            KaraokeLyricText(
                                text = line.text, startTimeMs = lineStart, endTimeMs = line.endTimeMs,
                                positionMs = adjustedPositionMs, wordTimings = line.wordTimings
                            )
                        } else {
                        Text(
                            text = line.text,
                            style = lyricLineStyle(
                                fontSize = if (isActive) 28.sp else 22.sp,
                                color = LyricWarmWhite.copy(alpha = if (isActive) 1f else alpha.coerceIn(0.35f, 0.55f)),
                                fontWeight = if (isActive) FontWeight.SemiBold else FontWeight.Medium
                            )
                        )
                        }
                        val translated = line.translatedText
                        if (translationEnabled && translated != null && translated != line.text) {
                            Spacer(Modifier.height(6.dp))
                            Text(
                                text = translated,
                                style = VantaType.editorialBody.copy(
                                    fontSize = if (isActive) 18.sp else 15.sp,
                                    color = LyricWarmWhite.copy(alpha = (if (isActive) 0.55f else 0.35f).coerceAtMost(alpha)),
                                    fontWeight = FontWeight.Normal,
                                    lineHeight = if (isActive) 24.sp else 20.sp
                                )
                            )
                        }
                    }
                }
            }
        }

        AnimatedVisibility(
            visible = showOffsetSlider,
            modifier = Modifier.align(Alignment.BottomCenter),
            enter = fadeIn() + slideInVertically { it },
            exit = fadeOut() + slideOutVertically { it }
        ) {
            val offsetSeconds = offsetMs / 1000f
            Column(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = "Lyrics sync  ${if (offsetSeconds >= 0) "+" else ""}${"%.1f".format(offsetSeconds)}s",
                    color = AppText.copy(alpha = 0.7f), fontSize = 11.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.5.sp
                )
                Row(
                    modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("−10s", color = AppTextMuted, fontSize = 10.sp)
                    Slider(
                        value = offsetSeconds,
                        onValueChange = { offsetMs = (it * 1000).toLong(); syncPreferences.setOffsetMs(lyricsData.trackKey, offsetMs) },
                        valueRange = -10f..10f, steps = 39, modifier = Modifier.weight(1f),
                        colors = SliderDefaults.colors(thumbColor = AppAccent, activeTrackColor = AppAccent, inactiveTrackColor = AppAccent.copy(alpha = 0.25f))
                    )
                    Text("+10s", color = AppTextMuted, fontSize = 10.sp)
                }
            }
        }
    }
}

@Composable
private fun KaraokeLyricText(
    text: String, startTimeMs: Long, endTimeMs: Long?,
    positionMs: Long, wordTimings: List<LyricsWordTiming> = emptyList()
) {
    if (wordTimings.isNotEmpty()) {
        val styledText = buildAnnotatedString {
            wordTimings.forEachIndexed { index, timing ->
                if (index > 0) append(" ")
                val nextStart = wordTimings.getOrNull(index + 1)?.startTimeMs ?: endTimeMs ?: (timing.startTimeMs + 700L)
                val isFullyHighlighted = positionMs >= nextStart
                val isPartial = positionMs >= timing.startTimeMs && positionMs < nextStart
                when {
                    isFullyHighlighted -> {
                        withStyle(SpanStyle(color = LyricWarmGold, fontWeight = FontWeight.SemiBold)) { append(timing.text) }
                    }
                    isPartial -> {
                        val span = (nextStart - timing.startTimeMs).coerceAtLeast(1L)
                        val progress = ((positionMs - timing.startTimeMs).toFloat() / span).coerceIn(0f, 1f)
                        val highlightedChars = (timing.text.length * progress).toInt().coerceIn(0, timing.text.length)
                        if (highlightedChars > 0) {
                            withStyle(SpanStyle(color = LyricWarmGold, fontWeight = FontWeight.SemiBold)) { append(timing.text.take(highlightedChars)) }
                        }
                        if (highlightedChars < timing.text.length) {
                            withStyle(SpanStyle(color = LyricWarmWhite.copy(alpha = 0.48f), fontWeight = FontWeight.Medium)) { append(timing.text.drop(highlightedChars)) }
                        }
                    }
                    else -> {
                        withStyle(SpanStyle(color = LyricWarmWhite.copy(alpha = 0.48f), fontWeight = FontWeight.Medium)) { append(timing.text) }
                    }
                }
            }
        }
        Text(text = styledText, style = lyricLineStyle(fontSize = 32.sp, color = LyricWarmGold, fontWeight = FontWeight.SemiBold))
        return
    }

    val words = remember(text) { text.trim().split(Regex("\\s+")).filter { it.isNotBlank() } }
    val weights = remember(words) { words.map { word -> 1f + kotlin.math.sqrt(word.count { it.isLetterOrDigit() }.coerceAtLeast(1).toFloat()) } }
    val totalWeight = weights.sum().coerceAtLeast(1f)
    val estimatedEnd = (endTimeMs ?: (startTimeMs + maxOf(1_200L, words.size * 340L))).coerceAtLeast(startTimeMs + 600L)
    val lineProgress = ((positionMs - startTimeMs).toFloat() / (estimatedEnd - startTimeMs).toFloat()).coerceIn(0f, 1f)

    val styledText = buildAnnotatedString {
        var consumedWeight = 0f
        words.forEachIndexed { index, word ->
            if (index > 0) append(" ")
            val wordStart = consumedWeight / totalWeight
            val wordEnd = (consumedWeight + weights[index]) / totalWeight
            val wordProgress = ((lineProgress - wordStart) / (wordEnd - wordStart)).coerceIn(0f, 1f)
            val highlightedCharacters = when {
                lineProgress >= wordEnd -> word.length
                lineProgress <= wordStart -> 0
                else -> (word.length * wordProgress).toInt().coerceIn(0, word.length)
            }
            if (highlightedCharacters > 0) {
                withStyle(SpanStyle(color = LyricWarmGold, fontWeight = FontWeight.SemiBold)) { append(word.take(highlightedCharacters)) }
            }
            if (highlightedCharacters < word.length) {
                withStyle(SpanStyle(color = LyricWarmWhite.copy(alpha = 0.48f), fontWeight = FontWeight.Medium)) { append(word.drop(highlightedCharacters)) }
            }
            consumedWeight += weights[index]
        }
    }
    Text(text = styledText, style = lyricLineStyle(fontSize = 32.sp, color = LyricWarmGold, fontWeight = FontWeight.SemiBold))
}

@Composable
private fun LyricsAtmosphereEdgeFades(showOffsetSlider: Boolean, modifier: Modifier = Modifier) {
    Box(modifier = modifier) {
        Box(
            modifier = Modifier.align(Alignment.TopCenter).fillMaxWidth().height(110.dp).drawBehind {
                drawRect(brush = androidx.compose.ui.graphics.Brush.verticalGradient(
                    listOf(Color.Black.copy(alpha = 0.20f), Color.Transparent)
                ))
            }
        )
        Box(
            modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                .height(if (showOffsetSlider) 160.dp else 120.dp).drawBehind {
                drawRect(brush = androidx.compose.ui.graphics.Brush.verticalGradient(
                    listOf(Color.Transparent, Color.Black.copy(alpha = 0.35f))
                ))
            }
        )
    }
}

@Composable
private fun LyricsFallbackView(isLoading: Boolean, onRetry: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.padding(horizontal = 28.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (isLoading) {
            CircularProgressIndicator(modifier = Modifier.size(30.dp), color = AppAccent, strokeWidth = 2.dp)
            Spacer(Modifier.height(18.dp))
            Text("Finding synced lyrics…", color = AppText, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(6.dp))
            Text("Matching this recording and timing each line.", color = AppTextMuted, fontSize = 12.sp, textAlign = TextAlign.Center)
        } else {
            Box(
                modifier = Modifier.size(54.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.045f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Filled.MusicNote, contentDescription = null, tint = AppTextSecondary, modifier = Modifier.size(24.dp))
            }
            Spacer(Modifier.height(16.dp))
            Text("No lyrics found", color = AppText, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(6.dp))
            Text("The metadata may still be settling. Try the match again.", color = AppTextMuted, fontSize = 12.sp, textAlign = TextAlign.Center)
            Spacer(Modifier.height(18.dp))
            Box(
                modifier = Modifier.clip(RoundedCornerShape(50)).background(AppAccent.copy(alpha = 0.1f))
                    .border(0.5.dp, AppAccent.copy(alpha = 0.22f), RoundedCornerShape(50))
                    .clickable(onClick = onRetry).padding(horizontal = 20.dp, vertical = 10.dp)
            ) {
                Text("Try again", color = AppAccent, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun TranslationToggleRow(
    translationEnabled: Boolean,
    onToggleTranslation: () -> Unit,
    hasTranslations: Boolean
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggleTranslation)
            .padding(horizontal = 20.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(20))
                .background(if (translationEnabled) AppAccent.copy(alpha = 0.15f) else Color.White.copy(alpha = 0.05f))
                .padding(horizontal = 14.dp, vertical = 6.dp)
        ) {
            Text(
                text = if (translationEnabled) "TRANSLATE ON" else "TRANSLATE OFF",
                style = VantaType.caption.copy(
                    fontSize = 11.sp,
                    color = if (translationEnabled) AppAccent else AppTextSecondary,
                    fontWeight = FontWeight.SemiBold
                ),
                letterSpacing = 0.5.sp
            )
        }
        if (hasTranslations && translationEnabled) {
            Spacer(Modifier.width(8.dp))
            Text(
                text = "translated",
                style = VantaType.caption.copy(fontSize = 10.sp, color = AppTextMuted)
            )
        }
    }
}
