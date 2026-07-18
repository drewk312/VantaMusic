package com.audiophile.musicplayer.data.lyrics

/** Parses LRC / synced lyric text into timed [LyricsLine] rows. */
object LrcParser {
    private val timestampRegex = Regex(
        """\[(\d{1,2}):(\d{2})(?:[.:](\d{1,3}))?\]"""
    )
    private val wordTagRegex = Regex(
        """<(\d{1,2}):(\d{2})(?:[.:](\d{1,3}))?>([^<]*)"""
    )
    private val metadataTagRegex = Regex(
        """^\[(?:ar|ti|al|by|length|re|ve|lang|tool|instrumental):.*\]$""",
        RegexOption.IGNORE_CASE
    )
    private val OFFSET_TAG_REGEX = Regex(
        """^\[offset:([+-]?\d+)\]$""",
        RegexOption.IGNORE_CASE
    )

    fun parseSyncedLines(lrcText: String?): List<LyricsLine>? {
        if (lrcText.isNullOrBlank()) return null
        if (lrcText.trim().equals("[instrumental:true]", ignoreCase = true)) return null

        var globalOffsetMs = 0L
        val parsed = lrcText.lines().flatMap { rawLine ->
            val trimmed = rawLine.trim()
            if (trimmed.isBlank()) return@flatMap emptyList()

            // Parse global offset tag
            val offsetMatch = OFFSET_TAG_REGEX.matchEntire(trimmed)
            if (offsetMatch != null) {
                globalOffsetMs = offsetMatch.groupValues[1].toLongOrNull() ?: 0L
                return@flatMap emptyList()
            }
            if (metadataTagRegex.matches(trimmed)) return@flatMap emptyList()

            val timestamps = timestampRegex.findAll(trimmed).mapNotNull { match ->
                parseTimestamp(
                    match.groupValues[1],
                    match.groupValues[2],
                    match.groupValues.getOrNull(3).orEmpty()
                )
            }.toList()
            if (timestamps.isEmpty()) return@flatMap emptyList()

            val rawText = timestampRegex.replace(trimmed, "").trim()
            if (rawText.isBlank()) return@flatMap emptyList()

            val wordTimings = wordTagRegex.findAll(rawText).mapNotNull { wordMatch ->
                val wordStart = parseTimestamp(
                    wordMatch.groupValues[1],
                    wordMatch.groupValues[2],
                    wordMatch.groupValues.getOrNull(3).orEmpty()
                ) ?: return@mapNotNull null
                val word = wordMatch.groupValues[4].trim()
                if (word.isBlank()) return@mapNotNull null
                LyricsWordTiming(startTimeMs = wordStart, text = word)
            }.toList()

            val displayText = if (wordTimings.isNotEmpty()) {
                wordTimings.joinToString(" ") { it.text }
            } else {
                wordTagRegex.replace(rawText, "$4").replace(Regex("""\s+"""), " ").trim()
            }
            if (displayText.isBlank()) return@flatMap emptyList()

            timestamps.map { lineStartMs ->
                LyricsLine(
                    startTimeMs = lineStartMs,
                    endTimeMs = null,
                    text = displayText,
                    wordTimings = wordTimings
                )
            }
        }.sortedBy { it.startTimeMs ?: Long.MAX_VALUE }

        // Apply global offset to all timestamps
        val offsetApplied = if (globalOffsetMs != 0L) {
            parsed.map { line ->
                line.copy(
                    startTimeMs = line.startTimeMs?.plus(globalOffsetMs)?.coerceAtLeast(0L),
                    wordTimings = line.wordTimings.map { it.copy(startTimeMs = it.startTimeMs + globalOffsetMs) }
                )
            }
        } else parsed

        return offsetApplied
            .ifEmpty { null }
            ?.let { applyLineEndTimes(it) }
    }

    fun parsePlainLines(plainText: String?): List<LyricsLine> {
        if (plainText.isNullOrBlank()) return emptyList()
        return plainText.lines()
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .map { LyricsLine(startTimeMs = null, endTimeMs = null, text = it) }
    }

    /** Spread plain lyrics across track duration so tap-to-seek and highlight still work. */
    fun estimatePlainLyricTimings(lines: List<LyricsLine>, durationMs: Long): List<LyricsLine> {
        val textLines = lines.filter { it.text.isNotBlank() }
        if (textLines.isEmpty()) return lines

        val effectiveDuration = durationMs.takeIf { it > 0L } ?: 210_000L
        val interval = effectiveDuration / (textLines.size + 1).coerceAtLeast(1)

        return textLines.mapIndexed { index, line ->
            val start = ((index + 1) * interval).coerceAtLeast(2_000L)
            val end = if (index == textLines.lastIndex) {
                (effectiveDuration - 500L).coerceAtLeast(start + 800L)
            } else {
                ((index + 2) * interval).coerceAtLeast(start + 800L)
            }
            line.copy(startTimeMs = start, endTimeMs = end)
        }
    }

    fun parseTimestamp(minutes: String, seconds: String, fractional: String): Long? {
        val mins = minutes.toIntOrNull() ?: return null
        val secs = seconds.toIntOrNull() ?: return null
        val fractionMs = when (fractional.length) {
            0 -> 0
            1 -> fractional.toIntOrNull()?.times(100) ?: 0
            2 -> fractional.toIntOrNull()?.times(10) ?: 0
            else -> fractional.toIntOrNull() ?: 0
        }
        return (mins * 60L + secs) * 1_000L + fractionMs
    }
}
