@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class,
    androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.audiophile.musicplayer.ui.nowplaying

import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.zIndex
import com.audiophile.musicplayer.ui.AppAccent
import com.audiophile.musicplayer.data.lyrics.LrcParser
import com.audiophile.musicplayer.data.lyrics.activeLyricLineIndex
import com.audiophile.musicplayer.data.lyrics.LyricsData
import com.audiophile.musicplayer.data.lyrics.LyricsIdentity
import com.audiophile.musicplayer.data.display.VantaQualityInfo
import com.audiophile.musicplayer.playback.NowPlayingState
import coil.compose.AsyncImage
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

private val LyricWarmWhite = Color(0xFFFFF4E8)
private val LyricWarmGold = Color(0xFFFFE8C8)
private val LyricTextShadow = Shadow(
    color = Color.Black.copy(alpha = 0.55f),
    offset = Offset(0f, 4f),
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
    letterSpacing = 0.sp,
    color = color,
    shadow = LyricTextShadow
)

private enum class LyricsDisplayMode {
    Normal,
    Narrative
}

private enum class NarrativeMood {
    Celestial,
    NightDrive,
    TailoredSuit,
    VictoryCircuit,
    SoftBloom,
    ElectricPulse,
    RainGlass,
    ShadowRift
}

/**
 * Lyrics stage that layers a cinematic Living Lyrics video view with a swipe-up
 * scrollable synced-lyrics sheet. The sheet supports tap-to-seek, auto-scroll to
 * the active line, and a manual-scroll pause so it never fights the user.
 */
@Composable
fun VantaLyricsStage(
    lyricsData: LyricsData?,
    lyricsLoading: Boolean,
    nowPlayingState: NowPlayingState,
    displayTitle: String,
    displayArtist: String,
    displayAlbum: String?,
    qualityInfo: VantaQualityInfo?,
    onSeekTo: (Long) -> Unit,
    onRetryLyrics: () -> Unit,
    onOpenQualityDetails: () -> Unit,
    modifier: Modifier = Modifier,
    translationEnabled: Boolean = false,
    onToggleTranslation: () -> Unit = {},
    energy: Float = 0.5f,
    lyricsIdentity: LyricsIdentity = LyricsIdentity.Unavailable,
    controlsVisible: Boolean = true,
    onToggleControls: () -> Unit = {}
) {
    val trustedLyricsData = lyricsIdentity.lyricsData
    val lyricsForList = trustedLyricsData?.takeUnless { lyricsIdentity.noLyrics }

    // Normalize timings: if lyrics are plain/unsynced, estimate timings across track duration
    val timedLines = remember(lyricsForList, nowPlayingState.durationMs) {
        val raw = lyricsForList?.lines.orEmpty()
        val hasTimings = raw.any { it.startTimeMs != null }
        if (hasTimings) {
            raw
        } else {
            LrcParser.estimatePlainLyricTimings(raw, nowPlayingState.durationMs.coerceAtLeast(0L))
        }
    }

    // Synced lyrics are the composed default. Narrative remains an opt-in visual mode.
    var lyricsDisplayMode by remember { mutableStateOf(LyricsDisplayMode.Normal) }
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()
    var autoScrollPaused by remember { mutableStateOf(false) }
    val isUserDragging by listState.interactionSource.collectIsDraggedAsState()

    val activeIndex = remember(nowPlayingState.positionMs, timedLines) {
        activeLyricLineIndex(
            lines = timedLines,
            positionMs = nowPlayingState.positionMs.coerceAtLeast(0L)
        ).coerceIn(-1, timedLines.lastIndex)
    }
    val activeLine = timedLines.getOrNull(activeIndex)
    val activeLineProgress = remember(nowPlayingState.positionMs, activeIndex, timedLines) {
        val startMs = activeLine?.startTimeMs ?: nowPlayingState.positionMs.coerceAtLeast(0L)
        val nextStartMs = timedLines.getOrNull(activeIndex + 1)?.startTimeMs
        val endMs = (nextStartMs ?: (startMs + 3_600L)).coerceAtLeast(startMs + 900L)
        ((nowPlayingState.positionMs - startMs).toFloat() / (endMs - startMs).toFloat()).coerceIn(0f, 1f)
    }
    val normalLyricsVisible = lyricsDisplayMode == LyricsDisplayMode.Normal

    // Pause auto-scroll while the user is dragging the list
    LaunchedEffect(isUserDragging) {
        if (isUserDragging) {
            autoScrollPaused = true
            delay(5_000)
            autoScrollPaused = false
        }
    }

    // Auto-scroll to active line when playing and not paused
    LaunchedEffect(activeIndex, normalLyricsVisible, autoScrollPaused, nowPlayingState.isPlaying) {
        if (normalLyricsVisible && !autoScrollPaused && activeIndex >= 0 && nowPlayingState.isPlaying) {
            listState.animateScrollToItem(index = activeIndex, scrollOffset = -120)
        }
    }

    val activeVisible by remember { derivedStateOf {
        listState.layoutInfo.visibleItemsInfo.any { it.index == activeIndex }
    } }

    Box(modifier = modifier.fillMaxSize()) {
        key(lyricsDisplayMode) {
            when (lyricsDisplayMode) {
                LyricsDisplayMode.Narrative -> {
                    NarrativeLyricsScene(
                        nowPlayingState = nowPlayingState,
                        displayTitle = displayTitle,
                        displayArtist = displayArtist,
                        activeLine = activeLine?.translatedText?.takeIf { it.isNotBlank() } ?: activeLine?.text,
                        nextLine = timedLines.getOrNull(activeIndex + 1)?.text,
                        lineProgress = activeLineProgress,
                        energy = energy,
                        modifier = Modifier
                            .fillMaxSize()
                            .pointerInput(Unit) {
                                detectVerticalDragGestures { change, _ ->
                                    change.consume()
                                }
                            }
                            .clickable(
                                indication = null,
                                interactionSource = remember { MutableInteractionSource() }
                            ) { onToggleControls() }
                    )
                }
                LyricsDisplayMode.Normal -> {
                    NormalLyricsPanel(
                        lines = timedLines,
                        activeIndex = activeIndex,
                        listState = listState,
                        onLineTap = { line ->
                            line.startTimeMs?.let { onSeekTo(it) }
                        },
                        activeVisible = activeVisible,
                        onReturnToCurrent = {
                            autoScrollPaused = false
                            if (activeIndex >= 0) {
                                coroutineScope.launch {
                                    listState.animateScrollToItem(index = activeIndex, scrollOffset = -120)
                                }
                            }
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }

        if (controlsVisible && timedLines.isNotEmpty()) {
            LyricsModeToggle(
                selectedMode = lyricsDisplayMode,
                onModeSelected = { mode ->
                    lyricsDisplayMode = mode
                },
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 12.dp)
                    .zIndex(14f)
            )
        }
    }
}

@Composable
private fun LyricsModeToggle(
    selectedMode: LyricsDisplayMode,
    onModeSelected: (LyricsDisplayMode) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(999.dp))
            .background(Color.Black.copy(alpha = 0.30f))
            .border(1.dp, Color.White.copy(alpha = 0.10f), RoundedCornerShape(999.dp))
            .padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        LyricsModeToggleItem(
            text = "Normal",
            selected = selectedMode == LyricsDisplayMode.Normal,
            onClick = { onModeSelected(LyricsDisplayMode.Normal) }
        )
        LyricsModeToggleItem(
            text = "Narrative",
            selected = selectedMode == LyricsDisplayMode.Narrative,
            onClick = { onModeSelected(LyricsDisplayMode.Narrative) }
        )
    }
}

@Composable
private fun LyricsModeToggleItem(
    text: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(if (selected) Color.White.copy(alpha = 0.20f) else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = Color.White.copy(alpha = if (selected) 0.96f else 0.56f),
            fontSize = 12.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
        )
    }
}

@Composable
private fun NarrativeLyricsScene(
    nowPlayingState: NowPlayingState,
    displayTitle: String,
    displayArtist: String,
    activeLine: String?,
    nextLine: String?,
    lineProgress: Float,
    energy: Float,
    modifier: Modifier = Modifier
) {
    val lyricText = activeLine?.takeIf { it.isNotBlank() } ?: displayTitle
    val mood = remember(displayTitle, displayArtist, lyricText, energy) {
        chooseNarrativeMood(
            title = displayTitle,
            artist = displayArtist,
            lyric = lyricText,
            energy = energy
        )
    }
    val smoothProgress by animateFloatAsState(
        targetValue = lineProgress,
        animationSpec = tween(durationMillis = 260, easing = FastOutSlowInEasing),
        label = "narrative-line-progress"
    )
    val effectiveProgress = if (activeLine.isNullOrBlank()) {
        1f
    } else {
        smoothProgress.coerceAtLeast(0.24f)
    }
    val words = remember(lyricText) {
        lyricText.trim().split(Regex("\\s+")).filter { it.isNotBlank() }.ifEmpty { listOf(displayTitle) }
    }
    val phraseRows = remember(words) { narrativePhraseRows(words) }

    Box(modifier = modifier.background(Color.Black)) {
        nowPlayingState.artworkUrl?.takeIf { it.isNotBlank() }?.let { artworkUrl ->
            AsyncImage(
                model = artworkUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .scale(1.12f)
                    .blur(30.dp)
                    .alpha(0.42f)
            )
        }

        NarrativeMoodOverlay(
            mood = mood,
            lineProgress = effectiveProgress,
            energy = energy,
            modifier = Modifier.fillMaxSize()
        )

        NarrativeLyricTypography(
            mood = mood,
            displayArtist = displayArtist,
            lyricText = lyricText,
            nextLine = nextLine,
            words = words,
            phraseRows = phraseRows,
            lineProgress = effectiveProgress,
            modifier = Modifier
                .align(Alignment.Center)
                .fillMaxWidth()
                .padding(horizontal = 22.dp)
        )
    }
}

@Composable
private fun NarrativeLyricTypography(
    mood: NarrativeMood,
    displayArtist: String,
    lyricText: String,
    nextLine: String?,
    words: List<String>,
    phraseRows: List<List<String>>,
    lineProgress: Float,
    modifier: Modifier = Modifier
) {
    val shortLine = words.size <= 3
    val longLine = words.size >= 8
    val baseWordSize = when {
        shortLine -> 38.sp
        longLine -> 25.sp
        lyricText.length <= 34 -> 38.sp
        else -> 31.sp
    }
    val accent = when (mood) {
        NarrativeMood.Celestial -> Color(0xFFFFE9A6)
        NarrativeMood.NightDrive -> Color(0xFFBFE7FF)
        NarrativeMood.TailoredSuit -> Color(0xFFFFE1A8)
        NarrativeMood.VictoryCircuit -> Color(0xFFFFF069)
        NarrativeMood.SoftBloom -> Color(0xFFFFD7E8)
        NarrativeMood.ElectricPulse -> Color(0xFFB8FFEC)
        NarrativeMood.RainGlass -> LyricWarmWhite
        NarrativeMood.ShadowRift -> Color(0xFFE7D7FF)
    }
    val columnSpacing = if (longLine) 7.dp else 11.dp

    Box(modifier = modifier) {
        if (mood == NarrativeMood.ElectricPulse) {
            Text(
                text = lyricText.uppercase(),
                color = accent.copy(alpha = 0.07f),
                fontSize = baseWordSize * 1.32f,
                fontWeight = FontWeight.Black,
                lineHeight = baseWordSize * 1.1f,
                textAlign = TextAlign.Center,
                maxLines = if (longLine) 4 else 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .align(Alignment.Center)
                    .fillMaxWidth()
                    .scale(1.04f + lineProgress * 0.03f)
            )
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = if (shortLine) 8.dp else 0.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(columnSpacing)
        ) {
            Text(
                text = displayArtist.uppercase(),
                color = accent.copy(alpha = 0.62f),
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth()
            )

            phraseRows.forEachIndexed { rowIndex, rowWords ->
                Row(
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    rowWords.forEachIndexed { wordIndex, word ->
                        val absoluteIndex = phraseRows.take(rowIndex).sumOf { it.size } + wordIndex
                        val reveal = ((lineProgress * (words.size + 1.15f)) - absoluteIndex).coerceIn(0f, 1f)
                        val moodDrift = when (mood) {
                            NarrativeMood.Celestial -> sin((lineProgress * 2.2f) + absoluteIndex).toFloat() * 2.5f
                            NarrativeMood.NightDrive -> (lineProgress - 0.5f) * (8f + rowIndex * 2f)
                            NarrativeMood.TailoredSuit -> sin((lineProgress * 2.8f) + absoluteIndex).toFloat() * 1.6f
                            NarrativeMood.VictoryCircuit -> (lineProgress - 0.5f) * (14f + absoluteIndex * 1.2f)
                            NarrativeMood.SoftBloom -> sin((lineProgress + absoluteIndex) * 1.7f).toFloat() * 4f
                            NarrativeMood.ElectricPulse -> if (reveal > 0.1f) sin(lineProgress * 18f + absoluteIndex).toFloat() * 3.5f else 0f
                            NarrativeMood.RainGlass -> lineProgress * (rowIndex + 1) * 2f
                            NarrativeMood.ShadowRift -> -sin(lineProgress * 3.2f + absoluteIndex).toFloat() * 5f
                        }
                        val wordAlpha = if (lineProgress >= 0.98f) {
                            0.96f
                        } else {
                            0.48f + (0.52f * reveal)
                        }
                        val wordScale = when (mood) {
                            NarrativeMood.Celestial -> 0.98f + (0.08f * reveal) + (lineProgress * 0.025f)
                            NarrativeMood.TailoredSuit -> 0.98f + (0.06f * reveal)
                            NarrativeMood.VictoryCircuit -> 0.93f + (0.16f * reveal)
                            NarrativeMood.SoftBloom -> 0.95f + (0.09f * reveal) + (lineProgress * 0.025f)
                            NarrativeMood.ElectricPulse -> 0.94f + (0.12f * reveal)
                            else -> 0.96f + (0.07f * reveal)
                        }

                        Text(
                            text = word.uppercase(),
                            color = if (reveal > 0.48f) accent.copy(alpha = wordAlpha) else LyricWarmWhite.copy(alpha = wordAlpha),
                            fontSize = baseWordSize,
                            fontWeight = FontWeight.Black,
                            lineHeight = baseWordSize * 1.04f,
                            textAlign = TextAlign.Center,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier
                                .weight(1f, fill = false)
                                .scale(wordScale)
                                .padding(horizontal = if (longLine) 3.dp else 5.dp)
                                .offset(
                                    x = moodDrift.dp,
                                    y = if (mood == NarrativeMood.ShadowRift) ((1f - reveal) * 10f).dp else 0.dp
                                )
                        )
                    }
                }
            }

            if (!nextLine.isNullOrBlank()) {
                Text(
                    text = nextLine,
                    color = accent.copy(alpha = 0.40f),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    lineHeight = 18.sp,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp)
                )
            }
        }

    }
}

@Composable
private fun NarrativeMoodOverlay(
    mood: NarrativeMood,
    lineProgress: Float,
    energy: Float,
    modifier: Modifier = Modifier
) {
    val motion by rememberInfiniteTransition(label = "narrative-mood-motion").animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 3_400, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "narrative-mood-motion-value"
    )
    val palette = narrativePalette(mood)
    val glowAlpha = min(0.42f, 0.14f + (energy.coerceIn(0f, 1f) * 0.18f) + (lineProgress * 0.1f))

    Canvas(modifier = modifier) {
        drawRect(
            Brush.verticalGradient(
                colors = listOf(
                    palette.first.copy(alpha = 0.38f),
                    palette.second.copy(alpha = 0.6f),
                    Color.Black.copy(alpha = 0.88f)
                )
            )
        )
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    palette.third.copy(alpha = glowAlpha),
                    Color.Transparent
                ),
                center = Offset(size.width * (0.42f + lineProgress * 0.16f), size.height * 0.47f),
                radius = max(size.width, size.height) * 0.42f
            ),
            radius = max(size.width, size.height) * 0.42f,
            center = Offset(size.width * (0.42f + lineProgress * 0.16f), size.height * 0.47f)
        )

        when (mood) {
            NarrativeMood.Celestial -> {
                repeat(34) { index ->
                    val x = (((index * 41) % 100) / 100f) * size.width
                    val y = (((index * 67) % 100) / 100f) * size.height * 0.74f
                    val twinkle = 0.035f + (sin(motion * 6.28f + index).toFloat().coerceAtLeast(0f) * 0.055f)
                    drawCircle(
                        color = Color.White.copy(alpha = twinkle),
                        radius = 1.4f + (index % 3) * 0.75f,
                        center = Offset(x, y)
                    )
                }
                repeat(7) { index ->
                    val x = size.width * (0.18f + index * 0.11f)
                    drawLine(
                        color = palette.third.copy(alpha = 0.06f + lineProgress * 0.035f),
                        start = Offset(size.width * 0.5f, size.height * 0.53f),
                        end = Offset(x, size.height * 0.08f),
                        strokeWidth = 2f + index * 0.45f
                    )
                }
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            palette.third.copy(alpha = 0.16f),
                            Color.Transparent
                        ),
                        center = Offset(size.width * 0.5f, size.height * 0.46f),
                        radius = size.minDimension * 0.42f
                    ),
                    radius = size.minDimension * 0.42f,
                    center = Offset(size.width * 0.5f, size.height * 0.46f)
                )
            }
            NarrativeMood.NightDrive -> {
                repeat(18) { index ->
                    val y = size.height * (0.28f + index * 0.035f)
                    val phase = ((motion + index * 0.071f) % 1f)
                    val startX = -size.width * 0.25f + phase * size.width * 1.35f
                    drawLine(
                        color = palette.third.copy(alpha = 0.055f + (index % 3) * 0.025f),
                        start = Offset(startX, y),
                        end = Offset(startX + size.width * 0.38f, y + 20f),
                        strokeWidth = 2.2f + (index % 4)
                    )
                }
                repeat(8) { index ->
                    val lane = size.width * (0.18f + index * 0.09f)
                    drawLine(
                        color = Color.White.copy(alpha = 0.035f),
                        start = Offset(size.width * 0.5f, size.height * 0.58f),
                        end = Offset(lane, size.height),
                        strokeWidth = 1.2f
                    )
                }
            }
            NarrativeMood.TailoredSuit -> {
                repeat(7) { index ->
                    val x = size.width * (0.18f + index * 0.11f)
                    drawLine(
                        color = Color.White.copy(alpha = 0.06f + lineProgress * 0.035f),
                        start = Offset(x, 0f),
                        end = Offset(size.width * 0.5f, size.height * 0.54f),
                        strokeWidth = 3f + index * 0.55f
                    )
                }
                drawRoundRect(
                    color = Color.Black.copy(alpha = 0.42f),
                    topLeft = Offset(size.width * 0.18f, size.height * 0.40f),
                    size = Size(size.width * 0.26f, size.height * 0.52f),
                    cornerRadius = CornerRadius(22f, 22f)
                )
                drawRoundRect(
                    color = Color.Black.copy(alpha = 0.42f),
                    topLeft = Offset(size.width * 0.56f, size.height * 0.40f),
                    size = Size(size.width * 0.26f, size.height * 0.52f),
                    cornerRadius = CornerRadius(22f, 22f)
                )
                repeat(18) { index ->
                    val x = size.width * ((index * 0.17f + motion * 0.08f) % 1f)
                    val y = size.height * (0.14f + ((index * 0.11f + motion * 0.12f) % 0.72f))
                    drawCircle(
                        color = palette.third.copy(alpha = 0.04f + (index % 3) * 0.012f),
                        radius = 2f + (index % 4),
                        center = Offset(x, y)
                    )
                }
            }
            NarrativeMood.VictoryCircuit -> {
                repeat(20) { index ->
                    val lane = index / 20f
                    val phase = ((motion * 1.7f + index * 0.061f) % 1f)
                    val y = size.height * (0.24f + lane * 0.52f)
                    drawLine(
                        color = palette.third.copy(alpha = 0.07f + lineProgress * 0.05f),
                        start = Offset(size.width * phase - size.width * 0.32f, y),
                        end = Offset(size.width * phase + size.width * 0.2f, y + 42f),
                        strokeWidth = 4f + (index % 4)
                    )
                }
                repeat(8) { index ->
                    val blockWidth = size.width / 8f
                    val alpha = if ((index + (motion * 8).toInt()) % 2 == 0) 0.05f else 0.015f
                    drawRect(
                        color = Color.White.copy(alpha = alpha),
                        topLeft = Offset(index * blockWidth, size.height * 0.08f),
                        size = Size(blockWidth, size.height * 0.09f)
                    )
                    drawRect(
                        color = Color.White.copy(alpha = alpha),
                        topLeft = Offset(index * blockWidth, size.height * 0.83f),
                        size = Size(blockWidth, size.height * 0.09f)
                    )
                }
                repeat(5) { index ->
                    drawCircle(
                        color = palette.third.copy(alpha = 0.035f),
                        radius = size.minDimension * (0.12f + lineProgress * 0.25f + index * 0.06f),
                        center = Offset(size.width * 0.5f, size.height * 0.52f)
                    )
                }
            }
            NarrativeMood.SoftBloom -> {
                repeat(18) { index ->
                    val angle = motion * 6.28f + index * 0.7f
                    val radius = size.minDimension * (0.08f + (index % 6) * 0.035f)
                    val center = Offset(
                        x = size.width * (0.5f + cos(angle).toFloat() * 0.34f),
                        y = size.height * (0.48f + sin(angle * 0.7f).toFloat() * 0.2f)
                    )
                    drawCircle(
                        color = palette.third.copy(alpha = 0.026f + (index % 4) * 0.011f),
                        radius = radius,
                        center = center
                    )
                }
            }
            NarrativeMood.ElectricPulse -> {
                repeat(16) { index ->
                    val pulse = ((motion * 1.6f + index * 0.09f) % 1f)
                    val x = size.width * pulse
                    drawLine(
                        color = palette.third.copy(alpha = 0.06f + lineProgress * 0.04f),
                        start = Offset(x - size.width * 0.25f, size.height * 0.18f),
                        end = Offset(x + size.width * 0.18f, size.height * 0.84f),
                        strokeWidth = 2.5f + (index % 5)
                    )
                }
                repeat(4) { index ->
                    drawCircle(
                        color = Color.White.copy(alpha = 0.035f),
                        radius = size.minDimension * (0.16f + lineProgress * 0.24f + index * 0.09f),
                        center = Offset(size.width * 0.5f, size.height * 0.5f)
                    )
                }
            }
            NarrativeMood.RainGlass -> {
                repeat(44) { index ->
                    val baseX = ((index * 37) % 100) / 100f
                    val baseY = ((index * 61) % 100) / 100f
                    val x = (baseX * size.width) + ((index % 5) - 2) * 3f
                    val y = ((baseY + motion) % 1f) * size.height
                    val length = 34f + ((index % 6) * 8f)
                    drawLine(
                        color = Color.White.copy(alpha = 0.055f + ((index % 4) * 0.018f)),
                        start = Offset(x, y),
                        end = Offset(x + 8f, y + length),
                        strokeWidth = 1f + (index % 3) * 0.35f
                    )
                }
                repeat(14) { index ->
                    val x = (((index * 53) % 100) / 100f) * size.width
                    val y = ((((index * 29) % 100) / 100f + motion * 0.35f) % 1f) * size.height
                    val dropSize = Size(width = 3.5f + (index % 3), height = 16f + (index % 5) * 3f)
                    drawRoundRect(
                        color = Color.White.copy(alpha = 0.055f),
                        topLeft = Offset(x, y),
                        size = dropSize,
                        cornerRadius = CornerRadius(8f, 8f)
                    )
                }
            }
            NarrativeMood.ShadowRift -> {
                repeat(12) { index ->
                    val x = size.width * ((index * 0.13f + motion * 0.12f) % 1f)
                    drawRoundRect(
                        color = Color.Black.copy(alpha = 0.18f + (index % 3) * 0.04f),
                        topLeft = Offset(x - 18f, 0f),
                        size = Size(34f + (index % 4) * 12f, size.height),
                        cornerRadius = CornerRadius(40f, 40f)
                    )
                }
                repeat(6) { index ->
                    drawCircle(
                        color = palette.third.copy(alpha = 0.04f),
                        radius = size.minDimension * (0.18f + index * 0.06f),
                        center = Offset(size.width * (0.25f + index * 0.1f), size.height * (0.42f + sin(motion * 6.28f + index).toFloat() * 0.08f))
                    )
                }
            }
        }

        drawRect(
            Brush.verticalGradient(
                colors = listOf(
                    Color.Black.copy(alpha = 0.24f),
                    Color.Transparent,
                    Color.Black.copy(alpha = 0.62f)
                )
            )
        )
        drawRect(
            Brush.horizontalGradient(
                colors = listOf(
                    Color.Black.copy(alpha = 0.34f),
                    Color.Transparent,
                    Color.Black.copy(alpha = 0.34f)
                )
            )
        )
        repeat(72) { index ->
            val x = (((index * 47) % 100) / 100f) * size.width
            val y = (((index * 83) % 100) / 100f) * size.height
            drawCircle(
                color = Color.White.copy(alpha = 0.012f + (index % 3) * 0.004f),
                radius = 0.7f + (index % 2) * 0.35f,
                center = Offset(x, y)
            )
        }
    }
}

private fun chooseNarrativeMood(
    title: String,
    artist: String,
    lyric: String,
    energy: Float
): NarrativeMood {
    val text = "$title $artist $lyric".lowercase()
    return when {
        listOf("victory lap", "skepta", "plaqueboymax", "denzel curry", "hanumankind", "d double e", "that mexican ot").any { it in text } -> NarrativeMood.VictoryCircuit
        listOf("sharp dressed man", "dressed", "suit", "clean shirt", "new shoes", "zz top").any { it in text } -> NarrativeMood.TailoredSuit
        listOf("sky", "spirit", "heaven", "soul", "star", "light").any { it in text } -> NarrativeMood.Celestial
        listOf("rain", "tears", "cry", "water", "storm", "cold").any { it in text } -> NarrativeMood.RainGlass
        listOf("drive", "passenger", "side", "road", "street", "city", "night").any { it in text } -> NarrativeMood.NightDrive
        listOf("love", "heart", "home", "hold", "with you", "less").any { it in text } && energy < 0.68f -> NarrativeMood.SoftBloom
        listOf("fear", "die", "dark", "alone", "blood", "ghost").any { it in text } -> NarrativeMood.ShadowRift
        energy > 0.68f || listOf("go", "run", "fire", "dance", "up").any { it in text } -> NarrativeMood.ElectricPulse
        else -> NarrativeMood.NightDrive
    }
}

private fun narrativePalette(mood: NarrativeMood): Triple<Color, Color, Color> = when (mood) {
    NarrativeMood.Celestial -> Triple(Color(0xFF070816), Color(0xFF171326), Color(0xFFFFD66D))
    NarrativeMood.NightDrive -> Triple(Color(0xFF030713), Color(0xFF081B2A), Color(0xFF7DD5FF))
    NarrativeMood.TailoredSuit -> Triple(Color(0xFF050403), Color(0xFF17110A), Color(0xFFFFC36D))
    NarrativeMood.VictoryCircuit -> Triple(Color(0xFF05060A), Color(0xFF15101E), Color(0xFFFFED4A))
    NarrativeMood.SoftBloom -> Triple(Color(0xFF100713), Color(0xFF211024), Color(0xFFFFB6D4))
    NarrativeMood.ElectricPulse -> Triple(Color(0xFF04110F), Color(0xFF092421), Color(0xFF58FFD6))
    NarrativeMood.RainGlass -> Triple(Color(0xFF05070B), Color(0xFF111018), LyricWarmGold)
    NarrativeMood.ShadowRift -> Triple(Color(0xFF07050D), Color(0xFF130B1C), Color(0xFFBFA7FF))
}

private fun narrativePhraseRows(words: List<String>): List<List<String>> {
    if (words.size <= 3) return words.map { listOf(it) }
    val chunkSize = when {
        words.size <= 6 -> 2
        words.size <= 10 -> 3
        else -> 4
    }
    return words.chunked(chunkSize)
}

@Composable
private fun NormalLyricsPanel(
    lines: List<com.audiophile.musicplayer.data.lyrics.LyricsLine>,
    activeIndex: Int,
    listState: androidx.compose.foundation.lazy.LazyListState,
    onLineTap: (com.audiophile.musicplayer.data.lyrics.LyricsLine) -> Unit,
    activeVisible: Boolean,
    onReturnToCurrent: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .background(
                Brush.verticalGradient(
                    listOf(
                        Color(0xFF08090D),
                        Color(0xFF121018),
                        Color.Black
                    )
                )
            )
            .padding(top = 70.dp, bottom = 24.dp, start = 24.dp, end = 24.dp)
    ) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            userScrollEnabled = true,
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(top = 26.dp, bottom = 86.dp)
        ) {
            itemsIndexed(lines) { index, line ->
                val isActive = index == activeIndex
                val isPast = index < activeIndex
                val alpha = when {
                    isActive -> 1f
                    isPast -> 0.38f
                    else -> 0.58f
                }
                val fontSize = if (isActive) 23.sp else 18.sp
                val text = line.translatedText?.takeIf { it.isNotBlank() } ?: line.text

                Text(
                    text = text,
                    color = LyricWarmWhite.copy(alpha = alpha),
                    fontSize = fontSize,
                    fontWeight = if (isActive) FontWeight.ExtraBold else FontWeight.Medium,
                    lineHeight = fontSize * 1.25f,
                    textAlign = TextAlign.Center,
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .fillMaxWidth()
                        // Weight and color already identify the active line; a
                        // changing background looked like a flashing rectangle.
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { onLineTap(line) }
                        .padding(horizontal = 8.dp, vertical = 9.dp)
                )
            }
        }

        if (!activeVisible && activeIndex >= 0) {
            FloatingActionButton(
                onClick = onReturnToCurrent,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(4.dp)
                    .size(48.dp),
                containerColor = AppAccent,
                contentColor = Color.White,
                shape = CircleShape
            ) {
                Icon(
                    imageVector = Icons.Default.KeyboardArrowDown,
                    contentDescription = "Return to current line",
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    }
}
