@file:OptIn(
    androidx.compose.foundation.ExperimentalFoundationApi::class,
    androidx.compose.material3.ExperimentalMaterial3Api::class
)
package com.audiophile.musicplayer.ui.nowplaying

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import coil.compose.AsyncImage
import com.audiophile.musicplayer.data.display.VantaQualityInfo
import com.audiophile.musicplayer.data.lyrics.LyricsData
import com.audiophile.musicplayer.data.lyrics.LyricsIdentity
import com.audiophile.musicplayer.data.lyrics.LyricsLine
import com.audiophile.musicplayer.data.lyrics.LyricsSyncPreferences
import com.audiophile.musicplayer.data.lyrics.LyricsWordTiming
import com.audiophile.musicplayer.data.lyrics.activeLyricLineIndex
import com.audiophile.musicplayer.data.lyrics.estimateLineSpeechDurationMs
import com.audiophile.musicplayer.playback.NowPlayingState
import com.audiophile.musicplayer.playback.dsp.VantaEqualizerPreferences
import com.audiophile.musicplayer.ui.AppAccent
import com.audiophile.musicplayer.ui.theme.VantaSans
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

private val LyricWarmWhite = Color(0xFFFFF6EE)
private val LyricWarmGold = Color(0xFFFFE8C8)
private val LyricRose = Color(0xFFC46B78)
private val LyricAmber = Color(0xFFD59A63)
private const val DEFAULT_LYRIC_VISUAL_LEAD_MS = 0L
private const val LYRIC_SYNC_STEP_MS = 250L

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
    Flow,
    Focus
}

internal fun supportsSynchronizedLyricFilm(identity: LyricsIdentity): Boolean =
    identity is LyricsIdentity.ExactSync || identity is LyricsIdentity.HighConfidenceSync

internal fun adjustedLyricClockPositionMs(
    playbackPositionMs: Long,
    manualOffsetMs: Long,
    pipelineLeadMs: Long = 0L
): Long = (playbackPositionMs + DEFAULT_LYRIC_VISUAL_LEAD_MS + manualOffsetMs + pipelineLeadMs)
    .coerceAtLeast(0L)

internal fun narrativeLyricFontSizeSp(length: Int): Float = when {
    length <= 24 -> 60f
    length <= 48 -> 54f
    length <= 78 -> 47f
    else -> 40f
}

/**
 * Living Lyrics 2.0:
 * Apple Music / Spotify caliber synced lyric presentation with continuous multi-line stream,
 * word-by-word karaoke sweeps, active line bloom glow, instrumental break count-ins,
 * full-bleed living artwork atmosphere, and intuitive micro-sync tuning.
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
    accentColor: Color = AppAccent,
    lyricsIdentity: LyricsIdentity = LyricsIdentity.Unavailable,
    controlsVisible: Boolean = true,
    onToggleControls: () -> Unit = {}
) {
    val trustedLyricsData = lyricsIdentity.lyricsData ?: lyricsData
    val lyricsForList = trustedLyricsData?.takeUnless { lyricsIdentity.noLyrics && lyricsData == null }
    val timedLines: List<LyricsLine> = remember(lyricsForList, nowPlayingState.durationMs) {
        val rawLines = lyricsForList?.lines.orEmpty()
        if (rawLines.any { it.startTimeMs != null }) {
            rawLines
        } else if (rawLines.isNotEmpty()) {
            val effDuration = nowPlayingState.durationMs.takeIf { it > 0L } ?: 210_000L
            com.audiophile.musicplayer.data.lyrics.LrcParser.estimatePlainLyricTimings(rawLines, effDuration)
        } else {
            rawLines
        }
    }
    val hasVerifiedSync = timedLines.isNotEmpty()

    var lyricsDisplayMode by remember(lyricsForList?.trackKey) {
        mutableStateOf(LyricsDisplayMode.Flow)
    }

    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()
    var autoScrollPaused by remember { mutableStateOf(false) }
    val isUserDragging by listState.interactionSource.collectIsDraggedAsState()

    val context = LocalContext.current
    val syncPreferences = remember(context) { LyricsSyncPreferences(context) }
    val syncTrackKey = lyricsForList?.trackKey ?: "${displayArtist.lowercase()}|${displayTitle.lowercase()}"
    var manualSyncOffsetMs by remember(syncTrackKey) {
        mutableLongStateOf(syncPreferences.getOffsetMs(syncTrackKey))
    }
    val pipelineLeadMs = remember(context) {
        VantaEqualizerPreferences(context).lyricsPipelineLeadMs()
    }
    val livePlaybackPositionMs = rememberLiveLyricPlaybackPosition(
        reportedPositionMs = nowPlayingState.positionMs.coerceAtLeast(0L),
        isPlaying = nowPlayingState.isPlaying && !nowPlayingState.isBuffering
    )
    val lyricClockPositionMs = if (hasVerifiedSync) {
        adjustedLyricClockPositionMs(
            playbackPositionMs = livePlaybackPositionMs,
            manualOffsetMs = manualSyncOffsetMs,
            pipelineLeadMs = pipelineLeadMs
        )
    } else {
        nowPlayingState.positionMs.coerceAtLeast(0L)
    }

    val activeIndex = remember(lyricClockPositionMs, timedLines, hasVerifiedSync) {
        if (!hasVerifiedSync) {
            -1
        } else {
            timedLines.indexOfLast { (it.startTimeMs ?: -1L) <= lyricClockPositionMs }
                .coerceIn(-1, timedLines.lastIndex)
        }
    }
    val activeLine = timedLines.getOrNull(activeIndex)

    // Handle user manual scroll pause: keep paused while dragging, then resume after 4s
    LaunchedEffect(isUserDragging) {
        if (isUserDragging) {
            autoScrollPaused = true
        } else if (autoScrollPaused) {
            delay(4_000L)
            autoScrollPaused = false
        }
    }

    // Auto-scroll smoothly to active line when playing
    LaunchedEffect(activeIndex, lyricsDisplayMode, autoScrollPaused, nowPlayingState.isPlaying) {
        if (lyricsDisplayMode == LyricsDisplayMode.Flow && !autoScrollPaused && activeIndex >= 0 && nowPlayingState.isPlaying) {
            try {
                listState.animateScrollToItem(index = activeIndex, scrollOffset = 0)
            } catch (_: Exception) {}
        }
    }

    val activeVisible by remember {
        derivedStateOf {
            listState.layoutInfo.visibleItemsInfo.any { it.index == activeIndex }
        }
    }

    val hasAnyTranslation = remember(timedLines) {
        timedLines.isNotEmpty()  // Show translation toggle whenever lyrics exist — don't require translations to already be present
    }

    Box(modifier = modifier.fillMaxSize()) {
        // Full-bleed living artwork atmosphere
        LivingLyricsAtmosphere(
            artworkUrl = nowPlayingState.artworkUrl,
            accentColor = accentColor,
            energy = energy,
            modifier = Modifier.fillMaxSize()
        )

        when {
            lyricsLoading -> {
                LyricsLoadingState(
                    accentColor = accentColor,
                    modifier = Modifier.fillMaxSize()
                )
            }
            timedLines.isEmpty() -> {
                LyricsEmptyState(
                    title = displayTitle,
                    artist = displayArtist,
                    accentColor = accentColor,
                    onRetry = onRetryLyrics,
                    modifier = Modifier.fillMaxSize()
                )
            }
            else -> {
                key(lyricsDisplayMode) {
                    when (lyricsDisplayMode) {
                        LyricsDisplayMode.Flow -> {
                            ContinuousLyricsStream(
                                lines = timedLines,
                                activeIndex = activeIndex,
                                lyricClockPositionMs = lyricClockPositionMs,
                                listState = listState,
                                hasVerifiedSync = hasVerifiedSync,
                                accentColor = accentColor,
                                energy = energy,
                                translationEnabled = translationEnabled,
                                onLineTap = { line ->
                                    line.startTimeMs?.let { onSeekTo(it) }
                                },
                                activeVisible = activeVisible,
                                autoScrollPaused = autoScrollPaused,
                                onReturnToCurrent = {
                                    autoScrollPaused = false
                                    if (activeIndex >= 0) {
                                        coroutineScope.launch {
                                            try {
                                                listState.animateScrollToItem(index = activeIndex, scrollOffset = 0)
                                            } catch (_: Exception) {}
                                        }
                                    }
                                },
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                        LyricsDisplayMode.Focus -> {
                            NarrativeLyricsScene(
                                nowPlayingState = nowPlayingState,
                                displayTitle = displayTitle,
                                displayArtist = displayArtist,
                                activeLine = run {
                                    val translated = activeLine?.translatedText
                                    if (translationEnabled && !translated.isNullOrBlank()) {
                                        translated
                                    } else {
                                        activeLine?.text
                                    }
                                },
                                nextLine = timedLines.getOrNull(activeIndex + 1)?.text,
                                energy = energy,
                                accentColor = accentColor,
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
                    }
                }
            }
        }

        // Floating top control pill: Sync state / Mode toggle / Translation / Timing adjustment
        if (controlsVisible && timedLines.isNotEmpty()) {
            LyricsTopControlBar(
                hasVerifiedSync = hasVerifiedSync,
                lyricsDisplayMode = lyricsDisplayMode,
                onModeSelected = { lyricsDisplayMode = it },
                manualSyncOffsetMs = manualSyncOffsetMs,
                onEarlier = {
                    manualSyncOffsetMs = (manualSyncOffsetMs + LYRIC_SYNC_STEP_MS).coerceIn(-15_000L, 15_000L)
                    syncPreferences.setOffsetMs(syncTrackKey, manualSyncOffsetMs)
                },
                onReset = {
                    manualSyncOffsetMs = 0L
                    syncPreferences.setOffsetMs(syncTrackKey, 0L)
                },
                onLater = {
                    manualSyncOffsetMs = (manualSyncOffsetMs - LYRIC_SYNC_STEP_MS).coerceIn(-15_000L, 15_000L)
                    syncPreferences.setOffsetMs(syncTrackKey, manualSyncOffsetMs)
                },
                hasTranslation = hasAnyTranslation,
                translationEnabled = translationEnabled,
                onToggleTranslation = onToggleTranslation,
                accentColor = accentColor,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 10.dp)
                    .zIndex(15f)
            )
        }
    }
}

/**
 * Living artwork blurred canvas with breathing ambient drift and reactive aura bloom.
 */
@Composable
private fun LivingLyricsAtmosphere(
    artworkUrl: String?,
    accentColor: Color,
    energy: Float,
    modifier: Modifier = Modifier
) {
    val ambientMotion by rememberInfiniteTransition(label = "living-lyrics-motion").animateFloat(
        initialValue = -1f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(22_000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "ambient-motion-val"
    )

    val softEnergy by animateFloatAsState(
        targetValue = energy.coerceIn(0f, 1f),
        animationSpec = tween(600),
        label = "energy-glow"
    )

    Box(modifier = modifier.background(Color(0xFF09090D))) {
        if (!artworkUrl.isNullOrBlank()) {
            AsyncImage(
                model = artworkUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        scaleX = 1.25f + softEnergy * 0.05f
                        scaleY = 1.25f + softEnergy * 0.05f
                        translationX = ambientMotion * 18f
                        translationY = -ambientMotion * 14f
                    }
                    .blur(56.dp)
                    .alpha(0.38f)
            )
        }

        // Cinematic lighting canvas with radial ambient blooms and multi-stop gradient scrim
        Canvas(modifier = Modifier.fillMaxSize()) {
            // Dark base scrim for perfect contrast
            drawRect(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        Color.Black.copy(alpha = 0.58f),
                        Color.Black.copy(alpha = 0.32f),
                        Color.Black.copy(alpha = 0.88f)
                    )
                )
            )

            // Dynamic accent radial light
            val auraCenter = Offset(
                x = size.width * (0.44f + ambientMotion * 0.08f),
                y = size.height * 0.40f
            )
            val auraRadius = size.minDimension * (0.55f + softEnergy * 0.12f)
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(accentColor.copy(alpha = 0.28f), Color.Transparent),
                    center = auraCenter,
                    radius = auraRadius
                ),
                radius = auraRadius,
                center = auraCenter
            )

            // Warm bottom bloom
            val warmCenter = Offset(size.width * 0.78f, size.height * 0.72f)
            val warmRadius = size.minDimension * 0.48f
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(LyricAmber.copy(alpha = 0.14f), Color.Transparent),
                    center = warmCenter,
                    radius = warmRadius
                ),
                radius = warmRadius,
                center = warmCenter
            )

            // Vignette edge protection
            drawRect(
                brush = Brush.horizontalGradient(
                    listOf(
                        Color.Black.copy(alpha = 0.40f),
                        Color.Transparent,
                        Color.Black.copy(alpha = 0.40f)
                    )
                )
            )
        }
    }
}

/**
 * Continuous synchronized multi-line stream with active line bloom, word-by-word sweeps,
 * tap-to-seek, and instrumental break count-in indicators.
 */
@Composable
private fun ContinuousLyricsStream(
    lines: List<LyricsLine>,
    activeIndex: Int,
    lyricClockPositionMs: Long,
    listState: androidx.compose.foundation.lazy.LazyListState,
    hasVerifiedSync: Boolean,
    accentColor: Color,
    energy: Float,
    translationEnabled: Boolean,
    onLineTap: (LyricsLine) -> Unit,
    activeVisible: Boolean,
    autoScrollPaused: Boolean,
    onReturnToCurrent: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            userScrollEnabled = true,
            horizontalAlignment = Alignment.Start,
            contentPadding = PaddingValues(top = 160.dp, bottom = 260.dp, start = 26.dp, end = 26.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            itemsIndexed(lines) { index, line ->
                val isActive = index == activeIndex
                val isPast = index < activeIndex

                // Instrumental break indicator before line if significant musical break
                val prevLine = lines.getOrNull(index - 1)
                val prevEnd = prevLine?.endTimeMs ?: prevLine?.startTimeMs
                val currentStart = line.startTimeMs
                val breakGap = if (prevEnd != null && currentStart != null) currentStart - prevEnd else 0L

                val isInstrumentalBreakNow = hasVerifiedSync && breakGap >= 3500L &&
                    prevEnd != null && currentStart != null &&
                    lyricClockPositionMs >= prevEnd && lyricClockPositionMs < currentStart

                if (breakGap >= 3500L) {
                    InstrumentalBreakRow(
                        isActive = isInstrumentalBreakNow,
                        accentColor = accentColor,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp)
                    )
                }

                LyricStreamLineItem(
                    line = line,
                    isActive = isActive,
                    isPast = isPast,
                    lyricClockPositionMs = lyricClockPositionMs,
                    accentColor = accentColor,
                    energy = energy,
                    translationEnabled = translationEnabled,
                    onClick = { onLineTap(line) }
                )
            }
        }

        // Floating "Sync to Voice" button if user scrolled away
        AnimatedVisibility(
            visible = (autoScrollPaused || !activeVisible) && activeIndex >= 0,
            enter = fadeIn(tween(250)) + slideInVertically(tween(350)) { it / 2 },
            exit = fadeOut(tween(200)) + slideOutVertically(tween(250)) { it / 2 },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 24.dp)
                .zIndex(12f)
        ) {
            Surface(
                onClick = onReturnToCurrent,
                shape = RoundedCornerShape(999.dp),
                color = Color(0xFF14131A).copy(alpha = 0.94f),
                border = BorderStroke(1.dp, accentColor.copy(alpha = 0.65f)),
                shadowElevation = 10.dp
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Sync,
                        contentDescription = "Sync",
                        tint = accentColor,
                        modifier = Modifier.size(15.dp)
                    )
                    Text(
                        text = "SYNC TO VOICE",
                        color = LyricWarmWhite,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.1.sp
                    )
                }
            }
        }
    }
}

/**
 * Individual lyric line in the continuous stream.
 * Renders word-by-word karaoke sweeps if wordTimings are present, or glowing bloom if line-synced.
 */
@Composable
private fun LyricStreamLineItem(
    line: LyricsLine,
    isActive: Boolean,
    isPast: Boolean,
    lyricClockPositionMs: Long,
    accentColor: Color,
    energy: Float,
    translationEnabled: Boolean,
    onClick: () -> Unit
) {
    val alpha by animateFloatAsState(
        targetValue = when {
            isActive -> 1f
            isPast -> 0.30f
            else -> 0.52f
        },
        animationSpec = tween(350),
        label = "lyric-line-alpha"
    )

    val scale by animateFloatAsState(
        targetValue = if (isActive) 1.035f else 1.0f,
        animationSpec = tween(320, easing = FastOutSlowInEasing),
        label = "lyric-line-scale"
    )

    val fontSize = if (isActive) 32.sp else 26.sp
    val lineHeight = fontSize * 1.28f

    val textShadow = if (isActive) {
        Shadow(
            color = accentColor.copy(alpha = 0.60f + energy.coerceIn(0f, 1f) * 0.20f),
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

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 6.dp, vertical = 8.dp)
    ) {
        if (isActive && line.wordTimings.isNotEmpty()) {
            // Karaoke word-by-word synchronized sweep with word-level timings
            val annotatedLyric = remember(line.wordTimings, lyricClockPositionMs) {
                buildAnnotatedString {
                    line.wordTimings.forEachIndexed { wordIndex, word ->
                        val wordActive = lyricClockPositionMs >= word.startTimeMs
                        withStyle(
                            SpanStyle(
                                color = if (wordActive) LyricWarmGold else LyricWarmWhite.copy(alpha = 0.40f),
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
                letterSpacing = (-0.4).sp,
                textAlign = TextAlign.Start,
                style = TextStyle(shadow = textShadow),
                modifier = Modifier.fillMaxWidth()
            )
        } else if (isActive && line.startTimeMs != null) {
            // Smooth natural word-by-word karaoke sweep for line-synced lyrics
            val words = remember(line.text) { line.text.trim().split(Regex("\\s+")).filter { it.isNotBlank() } }
            val weights = remember(words) { words.map { word -> 1f + kotlin.math.sqrt(word.count { it.isLetterOrDigit() }.coerceAtLeast(1).toFloat()) } }
            val totalWeight = weights.sum().coerceAtLeast(1f)
            val lineStart = line.startTimeMs ?: 0L
            val estimatedEnd = (line.endTimeMs ?: (lineStart + maxOf(1_200L, words.size * 340L))).coerceAtLeast(lineStart + 600L)
            val lineProgress = ((lyricClockPositionMs - lineStart).toFloat() / (estimatedEnd - lineStart).toFloat()).coerceIn(0f, 1f)

            val styledText = remember(line.text, lineProgress) {
                buildAnnotatedString {
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
                            withStyle(SpanStyle(color = LyricWarmGold, fontWeight = FontWeight.ExtraBold)) {
                                append(word.take(highlightedCharacters))
                            }
                        }
                        if (highlightedCharacters < word.length) {
                            withStyle(SpanStyle(color = LyricWarmWhite.copy(alpha = 0.40f), fontWeight = FontWeight.Bold)) {
                                append(word.drop(highlightedCharacters))
                            }
                        }
                        consumedWeight += weights[index]
                    }
                }
            }

            Text(
                text = styledText,
                fontFamily = VantaSans,
                fontSize = fontSize,
                lineHeight = lineHeight,
                letterSpacing = (-0.4).sp,
                textAlign = TextAlign.Start,
                style = TextStyle(shadow = textShadow),
                modifier = Modifier.fillMaxWidth()
            )
        } else {
            // Line-based presentation with glowing active typography
            Text(
                text = line.text,
                color = if (isActive) LyricWarmGold else LyricWarmWhite.copy(alpha = alpha),
                fontFamily = VantaSans,
                fontSize = fontSize,
                fontWeight = if (isActive) FontWeight.ExtraBold else FontWeight.SemiBold,
                lineHeight = lineHeight,
                letterSpacing = (-0.4).sp,
                textAlign = TextAlign.Start,
                style = TextStyle(shadow = textShadow),
                modifier = Modifier.fillMaxWidth()
            )
        }

        // Subtitle translation if available and active
        val translated = line.translatedText
        if (translationEnabled && !translated.isNullOrBlank()) {
            Spacer(Modifier.height(4.dp))
            Text(
                text = translated,
                color = LyricWarmGold.copy(alpha = if (isActive) 0.85f else 0.42f),
                fontFamily = VantaSans,
                fontSize = 16.sp,
                fontStyle = FontStyle.Italic,
                lineHeight = 22.sp,
                letterSpacing = 0.1.sp,
                textAlign = TextAlign.Start,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

/**
 * Animated instrumental break indicator pulsing with the rhythm.
 */
@Composable
private fun InstrumentalBreakRow(
    isActive: Boolean,
    accentColor: Color,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "instrumental-pulse")
    val dotPhase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 3f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "dot-phase"
    )

    Row(
        modifier = modifier
            .clip(RoundedCornerShape(999.dp))
            .background(if (isActive) accentColor.copy(alpha = 0.14f) else Color.Transparent)
            .border(
                1.dp,
                if (isActive) accentColor.copy(alpha = 0.35f) else Color.White.copy(alpha = 0.05f),
                RoundedCornerShape(999.dp)
            )
            .padding(horizontal = 14.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(
            imageVector = Icons.Default.MusicNote,
            contentDescription = null,
            tint = if (isActive) accentColor else Color.White.copy(alpha = 0.35f),
            modifier = Modifier.size(14.dp)
        )
        Text(
            text = "INSTRUMENTAL",
            color = if (isActive) LyricWarmGold else Color.White.copy(alpha = 0.35f),
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.3.sp
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(4.dp),
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
                        .size(4.dp)
                        .clip(CircleShape)
                        .background((if (isActive) accentColor else Color.White).copy(alpha = dotAlpha))
                )
            }
        }
    }
}

/**
 * Top floating bar with mode switcher (Flow / Focus), micro sync adjuster, and translation toggle.
 */
@Composable
private fun LyricsTopControlBar(
    hasVerifiedSync: Boolean,
    lyricsDisplayMode: LyricsDisplayMode,
    onModeSelected: (LyricsDisplayMode) -> Unit,
    manualSyncOffsetMs: Long,
    onEarlier: () -> Unit,
    onReset: () -> Unit,
    onLater: () -> Unit,
    hasTranslation: Boolean,
    translationEnabled: Boolean,
    onToggleTranslation: () -> Unit,
    accentColor: Color,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(999.dp))
            .background(Color(0xFF100F17).copy(alpha = 0.88f))
            .border(1.dp, Color.White.copy(alpha = 0.10f), RoundedCornerShape(999.dp))
            .padding(horizontal = 6.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        // Mode toggle: Flow (stream) vs Focus (single phrase)
        LyricsModeSwitchPill(
            selectedMode = lyricsDisplayMode,
            onModeSelected = onModeSelected,
            accentColor = accentColor
        )

        // Micro-timing sync adjuster
        LyricsMicroSyncPill(
            offsetMs = manualSyncOffsetMs,
            onEarlier = onEarlier,
            onReset = onReset,
            onLater = onLater
        )

        // Translation toggle if available
        if (hasTranslation) {
            TranslationTogglePill(
                enabled = translationEnabled,
                accentColor = accentColor,
                onClick = onToggleTranslation
            )
        }
    }
}

@Composable
private fun LyricsModeSwitchPill(
    selectedMode: LyricsDisplayMode,
    onModeSelected: (LyricsDisplayMode) -> Unit,
    accentColor: Color
) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(Color.White.copy(alpha = 0.05f))
            .padding(2.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        ModeOptionItem(
            text = "Flow",
            selected = selectedMode == LyricsDisplayMode.Flow,
            accentColor = accentColor,
            onClick = { onModeSelected(LyricsDisplayMode.Flow) }
        )
        ModeOptionItem(
            text = "Focus",
            selected = selectedMode == LyricsDisplayMode.Focus,
            accentColor = accentColor,
            onClick = { onModeSelected(LyricsDisplayMode.Focus) }
        )
    }
}

@Composable
private fun ModeOptionItem(
    text: String,
    selected: Boolean,
    accentColor: Color,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(if (selected) accentColor.copy(alpha = 0.22f) else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 4.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = if (selected) accentColor else Color.White.copy(alpha = 0.60f),
            fontSize = 11.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
        )
    }
}

@Composable
private fun LyricsMicroSyncPill(
    offsetMs: Long,
    onEarlier: () -> Unit,
    onReset: () -> Unit,
    onLater: () -> Unit
) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(Color.White.copy(alpha = 0.05f))
            .padding(horizontal = 3.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(1.dp)
    ) {
        Text(
            text = "-0.25s",
            color = LyricWarmWhite.copy(alpha = 0.65f),
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier
                .clip(RoundedCornerShape(999.dp))
                .clickable(onClick = onEarlier)
                .padding(horizontal = 6.dp, vertical = 4.dp)
        )
        Text(
            text = if (offsetMs == 0L) "SYNC" else "%+.2fs".format(offsetMs / 1_000f),
            color = LyricWarmGold,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier
                .clip(RoundedCornerShape(999.dp))
                .clickable(onClick = onReset)
                .padding(horizontal = 6.dp, vertical = 4.dp)
        )
        Text(
            text = "+0.25s",
            color = LyricWarmWhite.copy(alpha = 0.65f),
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier
                .clip(RoundedCornerShape(999.dp))
                .clickable(onClick = onLater)
                .padding(horizontal = 6.dp, vertical = 4.dp)
        )
    }
}

@Composable
private fun TranslationTogglePill(
    enabled: Boolean,
    accentColor: Color,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(if (enabled) accentColor.copy(alpha = 0.22f) else Color.White.copy(alpha = 0.05f))
            .border(
                1.dp,
                if (enabled) accentColor.copy(alpha = 0.40f) else Color.Transparent,
                RoundedCornerShape(999.dp)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 5.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "SUBTITLE",
            color = if (enabled) accentColor else Color.White.copy(alpha = 0.55f),
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.8.sp
        )
    }
}


/**
 * Loading state with pulsing waveform rhythm shimmer.
 */
@Composable
private fun LyricsLoadingState(
    accentColor: Color,
    modifier: Modifier = Modifier
) {
    val motion by rememberInfiniteTransition(label = "loading-pulse").animateFloat(
        initialValue = 0.3f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(900, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "loading-alpha"
    )

    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Icon(
                imageVector = Icons.Default.MusicNote,
                contentDescription = null,
                tint = accentColor.copy(alpha = motion),
                modifier = Modifier.size(36.dp)
            )
            Text(
                text = "Gathering synchronized lyrics...",
                color = LyricWarmWhite.copy(alpha = 0.70f),
                fontFamily = VantaSans,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                letterSpacing = 0.5.sp
            )
        }
    }
}

/**
 * Clean empty state when lyrics are unavailable with a direct retry button.
 */
@Composable
private fun LyricsEmptyState(
    title: String,
    artist: String,
    accentColor: Color,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.padding(horizontal = 32.dp)
        ) {
            Icon(
                imageVector = Icons.Default.MusicNote,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.35f),
                modifier = Modifier.size(44.dp)
            )
            Text(
                text = "No lyrics found for this track",
                color = LyricWarmWhite.copy(alpha = 0.78f),
                fontFamily = VantaSans,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
            Text(
                text = "$title • $artist",
                color = LyricWarmWhite.copy(alpha = 0.40f),
                fontFamily = VantaSans,
                fontSize = 13.sp,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(4.dp))
            Surface(
                onClick = onRetry,
                shape = RoundedCornerShape(999.dp),
                color = accentColor.copy(alpha = 0.16f),
                border = BorderStroke(1.dp, accentColor.copy(alpha = 0.45f))
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 9.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Retry",
                        tint = accentColor,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = "RETRY SEARCH",
                        color = accentColor,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.9.sp
                    )
                }
            }
        }
    }
}

/**
 * Focus kinetic typography scene: large expressive single-phrase presentation.
 */
@Composable
private fun NarrativeLyricsScene(
    nowPlayingState: NowPlayingState,
    displayTitle: String,
    displayArtist: String,
    activeLine: String?,
    nextLine: String?,
    energy: Float,
    accentColor: Color,
    modifier: Modifier = Modifier
) {
    val lyricText = activeLine?.takeIf { it.isNotBlank() } ?: displayTitle
    val ambientMotion by rememberInfiniteTransition(label = "lyric-atmosphere").animateFloat(
        initialValue = -1f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(18_000, easing = LinearEasing), RepeatMode.Reverse),
        label = "lyric-atmosphere-drift"
    )
    val softEnergy by animateFloatAsState(energy.coerceIn(0f, 1f), tween(900), label = "lyric-soft-glow")

    Box(modifier = modifier.background(Color(0xFF09090D))) {
        nowPlayingState.artworkUrl?.takeIf { it.isNotBlank() }?.let { artworkUrl ->
            AsyncImage(
                model = artworkUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        scaleX = 1.18f
                        scaleY = 1.18f
                        translationX = ambientMotion * 12f
                        translationY = -ambientMotion * 8f
                    }
                    .blur(52.dp)
                    .alpha(0.30f)
            )
        }
        RefinedNarrativeBackdrop(
            accentColor = accentColor,
            ambientMotion = ambientMotion,
            energy = softEnergy,
            modifier = Modifier.fillMaxSize()
        )
        NarrativeLyricTypography(
            displayArtist = displayArtist,
            lyricText = lyricText,
            nextLine = nextLine,
            accentColor = accentColor,
            modifier = Modifier
                .fillMaxSize()
                .padding(start = 30.dp, end = 30.dp, top = 88.dp, bottom = 32.dp)
        )
    }
}

@Composable
private fun NarrativeLyricTypography(
    displayArtist: String,
    lyricText: String,
    nextLine: String?,
    accentColor: Color,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.Start,
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        Text(
            text = displayArtist,
            color = LyricWarmWhite.copy(alpha = 0.58f),
            fontFamily = VantaSans,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            letterSpacing = 1.1.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.CenterStart) {
            val stageWidth = constraints.maxWidth
            val stageHeight = constraints.maxHeight
            val measurer = rememberTextMeasurer()
            AnimatedContent(
                targetState = lyricText,
                contentAlignment = Alignment.CenterStart,
                transitionSpec = {
                    (fadeIn(tween(560, delayMillis = 100)) +
                        slideInVertically(tween(660, easing = FastOutSlowInEasing)) { it / 18 })
                        .togetherWith(fadeOut(tween(180)))
                },
                label = "typographic-phrase",
                modifier = Modifier.fillMaxSize()
            ) { phrase ->
                val fittedSize = remember(phrase, stageWidth, stageHeight, measurer) {
                    var candidate = narrativeLyricFontSizeSp(phrase.length)
                    while (candidate > 14f) {
                        val measured = measurer.measure(
                            phrase,
                            style = typographyPhraseStyle(candidate.sp),
                            constraints = Constraints(maxWidth = stageWidth)
                        )
                        if (measured.size.height <= stageHeight) break
                        candidate -= 2f
                    }
                    candidate.sp
                }
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.CenterStart) {
                    Text(
                        text = phrase,
                        style = typographyPhraseStyle(fittedSize).copy(
                            brush = Brush.verticalGradient(
                                listOf(
                                    LyricWarmWhite,
                                    androidx.compose.ui.graphics.lerp(LyricWarmWhite, accentColor, 0.18f)
                                )
                            )
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
        Box(Modifier.fillMaxWidth().height(88.dp), contentAlignment = Alignment.TopStart) {
            AnimatedContent(
                targetState = nextLine.orEmpty(),
                transitionSpec = { fadeIn(tween(480)).togetherWith(fadeOut(tween(180))) },
                label = "next-lyric-phrase"
            ) { upcoming ->
                Text(
                    text = upcoming,
                    color = LyricWarmWhite.copy(alpha = 0.40f),
                    fontFamily = VantaSans,
                    fontSize = 20.sp,
                    lineHeight = 27.sp,
                    fontWeight = FontWeight.Medium,
                    letterSpacing = (-0.2).sp,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

private fun typographyPhraseStyle(fontSize: TextUnit) = TextStyle(
    fontFamily = VantaSans,
    fontWeight = FontWeight.Bold,
    fontSize = fontSize,
    lineHeight = fontSize * 1.10f,
    letterSpacing = (-0.7).sp,
    textAlign = TextAlign.Start,
    color = LyricWarmWhite,
    shadow = Shadow(Color.Black.copy(alpha = 0.22f), Offset(0f, 2f), 12f)
)

@Composable
private fun RefinedNarrativeBackdrop(
    accentColor: Color,
    ambientMotion: Float,
    energy: Float,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        drawRect(
            brush = Brush.verticalGradient(
                listOf(
                    Color(0xFF0C0910).copy(alpha = 0.54f),
                    Color.Black.copy(alpha = 0.36f),
                    Color.Black.copy(alpha = 0.88f)
                )
            )
        )
        val center = Offset(
            x = size.width * (0.43f + ambientMotion * 0.06f),
            y = size.height * 0.43f
        )
        val radius = size.minDimension * (0.48f + energy.coerceIn(0f, 1f) * 0.08f)
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(accentColor.copy(alpha = 0.23f), Color.Transparent),
                center = center,
                radius = radius
            ),
            radius = radius,
            center = center
        )
        val roseCenter = Offset(size.width * 0.82f, size.height * 0.30f)
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(LyricRose.copy(alpha = 0.13f), Color.Transparent),
                center = roseCenter,
                radius = size.minDimension * 0.62f
            ),
            radius = size.minDimension * 0.62f,
            center = roseCenter
        )
        val amberCenter = Offset(size.width * 0.12f, size.height * 0.76f)
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(LyricAmber.copy(alpha = 0.10f), Color.Transparent),
                center = amberCenter,
                radius = size.minDimension * 0.54f
            ),
            radius = size.minDimension * 0.54f,
            center = amberCenter
        )
        drawRect(
            brush = Brush.horizontalGradient(
                listOf(Color.Black.copy(alpha = 0.52f), Color.Transparent, Color.Black.copy(alpha = 0.52f))
            )
        )
    }
}

@Composable
private fun rememberLiveLyricPlaybackPosition(
    reportedPositionMs: Long,
    isPlaying: Boolean
): Long {
    var livePositionMs by remember { mutableLongStateOf(reportedPositionMs) }
    var anchorPositionMs by remember { mutableLongStateOf(reportedPositionMs) }
    var anchorWallMs by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var lastReportedPositionMs by remember { mutableLongStateOf(reportedPositionMs) }

    LaunchedEffect(isPlaying) {
        anchorPositionMs = reportedPositionMs.coerceAtLeast(0L)
        anchorWallMs = System.currentTimeMillis()
        lastReportedPositionMs = reportedPositionMs
        livePositionMs = reportedPositionMs
        if (!isPlaying) return@LaunchedEffect

        while (true) {
            livePositionMs = anchorPositionMs + (System.currentTimeMillis() - anchorWallMs)
            delay(50L)
        }
    }

    SideEffect {
        if (!isPlaying) {
            livePositionMs = reportedPositionMs
            anchorPositionMs = reportedPositionMs
            anchorWallMs = System.currentTimeMillis()
            lastReportedPositionMs = reportedPositionMs
            return@SideEffect
        }

        val predictedMs = anchorPositionMs + (System.currentTimeMillis() - anchorWallMs)
        val reportedStepMs = reportedPositionMs - lastReportedPositionMs
        val driftMs = kotlin.math.abs(reportedPositionMs - predictedMs)
        if (reportedStepMs < 0L || reportedStepMs > 2_000L || driftMs > 750L) {
            anchorPositionMs = reportedPositionMs
            anchorWallMs = System.currentTimeMillis()
            livePositionMs = reportedPositionMs
        }
        lastReportedPositionMs = reportedPositionMs
    }

    return livePositionMs.coerceAtLeast(0L)
}
