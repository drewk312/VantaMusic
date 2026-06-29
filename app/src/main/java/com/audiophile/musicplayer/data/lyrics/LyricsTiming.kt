package com.audiophile.musicplayer.data.lyrics

/** Natural speech window for a line — avoids stretching karaoke across instrumental gaps. */
fun estimateLineSpeechDurationMs(text: String, wordTimings: List<LyricsWordTiming> = emptyList()): Long {
    if (wordTimings.isNotEmpty()) {
        val lastWord = wordTimings.last()
        return (lastWord.startTimeMs + lastWord.text.length * 180L + 400L).coerceAtLeast(900L)
    }
    val words = text.trim().split(Regex("\\s+")).filter { it.isNotBlank() }
    val wordCount = words.size.coerceAtLeast(1)
    return maxOf(1_100L, wordCount * 340L)
}

fun resolveLineEndTimeMs(
    startTimeMs: Long,
    text: String,
    nextLineStartMs: Long?,
    wordTimings: List<LyricsWordTiming> = emptyList()
): Long {
    val speechEnd = startTimeMs + estimateLineSpeechDurationMs(text, wordTimings)
    val capped = speechEnd + 350L
    return if (nextLineStartMs != null) {
        minOf(capped, nextLineStartMs)
    } else {
        capped
    }
}

fun applyLineEndTimes(lines: List<LyricsLine>): List<LyricsLine> {
    return lines.mapIndexed { index, line ->
        val start = line.startTimeMs ?: return@mapIndexed line
        val nextStart = lines.getOrNull(index + 1)?.startTimeMs
        val end = resolveLineEndTimeMs(start, line.text, nextStart, line.wordTimings.orEmpty())
        line.copy(endTimeMs = end)
    }
}

/** Index of the lyric line active at [positionMs], using line end windows when available. */
fun activeLyricLineIndex(lines: List<LyricsLine>, positionMs: Long): Int {
    if (lines.isEmpty()) return -1
    lines.forEachIndexed { index, line ->
        val start = line.startTimeMs ?: return@forEachIndexed
        val end = line.endTimeMs
        if (positionMs >= start && (end == null || positionMs < end)) {
            return index
        }
    }
    return lines.indexOfLast { (it.startTimeMs ?: Long.MAX_VALUE) <= positionMs }
}
