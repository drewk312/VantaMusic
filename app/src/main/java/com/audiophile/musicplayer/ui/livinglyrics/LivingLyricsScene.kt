@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
package com.audiophile.musicplayer.ui.livinglyrics

import android.util.Log
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.audiophile.musicplayer.ui.VantaType
import com.audiophile.musicplayer.ui.isDolbyAtmosLabel
import com.audiophile.musicplayer.ui.isSony360Label

@Composable
fun LivingLyricsScene(
    scenePlan: ScenePlan,
    positionMs: Long,
    isPlaying: Boolean,
    energy: Float,
    modifier: Modifier = Modifier,
    noLyrics: Boolean = false
) {
    val beats = scenePlan.beats

    val currentBeat = remember(positionMs, beats) {
        if (beats.isEmpty()) null
        else beats.lastOrNull { beat -> positionMs >= beat.startMs && positionMs < beat.endMs }
            ?: beats.firstOrNull()
    }
    val typography = scenePlan.typography
    val palette = scenePlan.palette

    val currentIndex = currentBeat?.let { beats.indexOf(it) } ?: -1
    val prevLine = remember(currentIndex, beats) {
        if (currentIndex > 0) findPrevLine(beats, currentIndex) else null
    }
    val nextLine = remember(currentIndex, beats) {
        if (currentIndex in 0 until beats.lastIndex) findNextLine(beats, currentIndex) else null
    }

    Box(modifier = modifier.fillMaxSize()) {
        SceneComposer(
            plan = scenePlan,
            currentBeat = currentBeat,
            energy = energy,
            modifier = Modifier.fillMaxSize()
        )

        // Subtle emotion color wash
        if (currentBeat?.emotion?.isNotBlank() == true) {
            val emotionColors = parseEmotionColors(currentBeat.emotion)
            if (emotionColors.isNotEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(emotionColors.first().copy(alpha = 0.05f))
                )
            }
        }

        // Cinematic lower-third lyric stack
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .background(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            Color.Transparent,
                            Color.Black.copy(alpha = 0.38f),
                            Color.Black.copy(alpha = 0.62f)
                        )
                    )
                )
                .padding(horizontal = 20.dp, vertical = 116.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.Start
            ) {
                val activeLyric = transformLyric(currentBeat?.lyricLine.orEmpty(), typography.textTransform)

                // Previous line (faint)
                AnimatedContent(
                    targetState = prevLine,
                    transitionSpec = {
                        (slideInVertically { it / 2 } + fadeIn(tween(400))) togetherWith
                            (slideOutVertically { -it / 2 } + fadeOut(tween(300)))
                    },
                    label = "prevLyric"
                ) { line ->
                    if (!line.isNullOrBlank()) {
                        Text(
                            text = transformLyric(line, typography.textTransform),
                            color = palette.textColor.copy(alpha = 0.38f),
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style = TextStyle(
                                shadow = Shadow(
                                    color = Color.Black.copy(alpha = 0.55f),
                                    offset = Offset(0f, 2f),
                                    blurRadius = 10f
                                )
                            )
                        )
                    } else {
                        Spacer(modifier = Modifier.height(20.sp.value.dp))
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                // Active line (large, glowing)
                if (!activeLyric.isNullOrBlank()) {
                    val dynamicSize = dynamicLyricSize(activeLyric.length, typography.fontSize)
                    val activeStyle = TextStyle(
                        color = palette.textColor,
                        fontFamily = FontFamily.SansSerif,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = dynamicSize,
                        lineHeight = dynamicSize * 1.10f,
                        letterSpacing = 0.sp,
                        textAlign = TextAlign.Start,
                        shadow = Shadow(
                            color = Color.Black.copy(alpha = 0.75f),
                            offset = Offset(0f, 3f),
                            blurRadius = 18f
                        )
                    )

                    AnimatedContent(
                        targetState = activeLyric,
                        transitionSpec = {
                            (slideInVertically { it / 3 } + fadeIn(tween(450, easing = LinearEasing))) togetherWith
                                (slideOutVertically { -it / 4 } + fadeOut(tween(350)))
                        },
                        label = "activeLyric"
                    ) { lyric ->
                        Box(
                            modifier = Modifier.fillMaxWidth(),
                            contentAlignment = Alignment.CenterStart
                        ) {
                            // Glow halo
                            Text(
                                text = lyric,
                                color = Color.Transparent,
                                fontSize = dynamicSize,
                                fontWeight = FontWeight.ExtraBold,
                                lineHeight = dynamicSize * 1.10f,
                                letterSpacing = 0.sp,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                style = activeStyle.copy(
                                    color = Color.Transparent,
                                    shadow = Shadow(
                                        color = palette.highlight.copy(alpha = 0.65f),
                                        offset = Offset(0f, 0f),
                                        blurRadius = 26f
                                    )
                                )
                            )
                            Text(
                                text = lyric,
                                style = activeStyle,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                } else if (noLyrics) {
                    Text(
                        text = "Lyrics unavailable for this source",
                        color = Color(0xFFFFF4E8).copy(alpha = 0.4f),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))

                // Next line (faint)
                AnimatedContent(
                    targetState = nextLine,
                    transitionSpec = {
                        (slideInVertically { it / 2 } + fadeIn(tween(400))) togetherWith
                            (slideOutVertically { -it / 2 } + fadeOut(tween(300)))
                    },
                    label = "nextLyric"
                ) { line ->
                    if (!line.isNullOrBlank()) {
                        Text(
                            text = transformLyric(line, typography.textTransform),
                            color = palette.textColor.copy(alpha = 0.32f),
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style = TextStyle(
                                shadow = Shadow(
                                    color = Color.Black.copy(alpha = 0.55f),
                                    offset = Offset(0f, 2f),
                                    blurRadius = 10f
                                )
                            )
                        )
                    } else {
                        Spacer(modifier = Modifier.height(20.sp.value.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun dynamicLyricSize(length: Int, base: Float): androidx.compose.ui.unit.TextUnit {
    val factor = when {
        length <= 18 -> 1.34f
        length <= 35 -> 1.16f
        length <= 65 -> 1.0f
        else -> 0.88f
    }
    return (base * factor).coerceIn(22f, 42f).sp
}

@Composable
fun LivingLyricsMetadataRow(
    title: String,
    artist: String,
    album: String?,
    qualityLabel: String?,
    accentColor: Color,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = VantaType.sectionTitle.copy(
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 16.sp
                ),
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.basicMarquee(
                    iterations = Int.MAX_VALUE,
                    velocity = 28.dp
                )
            )
            Spacer(modifier = Modifier.height(2.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = artist,
                    style = VantaType.subtitle.copy(
                        color = Color.White.copy(alpha = 0.78f),
                        fontSize = 12.sp
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .basicMarquee(
                            iterations = Int.MAX_VALUE,
                            velocity = 24.dp
                        )
                )
                if (album != null && album.isNotBlank()) {
                    Text(
                        text = " • $album",
                        style = VantaType.caption.copy(
                            color = Color.White.copy(alpha = 0.52f),
                            fontSize = 12.sp
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .padding(start = 6.dp)
                            .basicMarquee(
                                iterations = Int.MAX_VALUE,
                                velocity = 24.dp
                            )
                    )
                }
            }
        }
        if (qualityLabel != null) {
            Box(
                modifier = Modifier
                    .padding(start = 10.dp)
                    .background(
                        color = accentColor.copy(alpha = 0.16f),
                        shape = RoundedCornerShape(6.dp)
                    )
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Text(
                    text = when {
                        isDolbyAtmosLabel(qualityLabel) -> "ATMOS"
                        isSony360Label(qualityLabel) -> "360 RA"
                        qualityLabel.contains("hi-res", ignoreCase = true) || qualityLabel.contains("hi res", ignoreCase = true) -> "HI-RES"
                        qualityLabel.contains("lossless", ignoreCase = true) -> "LOSSLESS"
                        else -> qualityLabel.take(12)
                    },
                    color = accentColor,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 0.2.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Clip
                )
            }
        }
    }
}

private fun parseEmotionColors(emotion: String): List<Color> {
    val lower = emotion.lowercase()
    return when {
        lower.contains("somber") || lower.contains("melancholic") || lower.contains("brooding") ->
            listOf(Color(0xFF2A2A3A), Color(0xFF1A1A2A))
        lower.contains("hopeful") || lower.contains("joyful") || lower.contains("uplifted") ->
            listOf(Color(0xFFFFF0C0), Color(0xFFFFD8A0))
        lower.contains("tender") || lower.contains("warm") || lower.contains("intimate") ->
            listOf(Color(0xFFFFD8A0), Color(0xFFFFC080))
        lower.contains("nostalgic") || lower.contains("bittersweet") || lower.contains("wistful") ->
            listOf(Color(0xFFD4A56A), Color(0xFFC4954A))
        lower.contains("alone") || lower.contains("vulnerable") ->
            listOf(Color(0xFF4A3A5A), Color(0xFF3A2A4A))
        lower.contains("reverent") || lower.contains("transcendent") || lower.contains("spiritual") ->
            listOf(Color(0xFFFFE8C0), Color(0xFFFFDCA0))
        lower.contains("peaceful") || lower.contains("expansive") ->
            listOf(Color(0xFF8DB6D4), Color(0xFFB0D4E8))
        lower.contains("cathartic") || lower.contains("aching") ->
            listOf(Color(0xFF4A5A6A), Color(0xFF3A4A5A))
        lower.contains("contemplative") || lower.contains("reflective") ->
            listOf(Color(0xFF5A4A3A), Color(0xFF4A3A2A))
        else -> emptyList()
    }
}

private fun transformLyric(text: String, transform: TextTransform): String = when (transform) {
    TextTransform.NONE -> text
    TextTransform.UPPERCASE -> text.uppercase()
    TextTransform.LOWERCASE -> text.lowercase()
}

private fun findPrevLine(beats: List<LivingLyricBeat>, currentIndex: Int): String? {
    if (currentIndex <= 0) return null
    for (i in currentIndex - 1 downTo 0) {
        val line = beats[i].lyricLine
        if (line.isNotBlank() && line != beats[currentIndex].lyricLine) return line
    }
    return null
}

private fun findNextLine(beats: List<LivingLyricBeat>, currentIndex: Int): String? {
    if (currentIndex < 0 || currentIndex >= beats.lastIndex) return null
    for (i in currentIndex + 1..beats.lastIndex) {
        val line = beats[i].lyricLine
        if (line.isNotBlank() && line != beats[currentIndex].lyricLine) return line
    }
    return null
}
