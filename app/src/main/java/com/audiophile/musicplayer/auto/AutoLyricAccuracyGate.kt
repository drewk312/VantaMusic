package com.audiophile.musicplayer.auto

import com.audiophile.musicplayer.data.lyrics.LyricsData

object AutoLyricAccuracyGate {

    private const val MIN_CONFIDENCE = 0.3f
    private const val SANITY_CHECK_LINES = 3

    fun assess(lyrics: LyricsData, title: String, artist: String): LyricsAssessment {
        if (lyrics.lines.isEmpty()) return LyricsAssessment.REJECTED

        val firstLines = lyrics.lines.take(SANITY_CHECK_LINES)
            .mapNotNull { it.text.trim().takeIf { l -> l.isNotBlank() } }
        if (firstLines.isEmpty()) return LyricsAssessment.ACCEPTED

        val titleWords = title.lowercase().split(Regex("\\s+")).filter { it.length > 2 }.toSet()
        val artistWords = artist.lowercase().split(Regex("\\s+")).filter { it.length > 2 }.toSet()

        val nonRepeated = firstLines.count { line ->
            val words = line.lowercase().split(Regex("\\s+")).filter { it.length > 2 }.toSet()
            val titleOverlap = if (titleWords.isEmpty()) 0f else
                words.intersect(titleWords).size.toFloat() / titleWords.size
            val artistOverlap = if (artistWords.isEmpty()) 0f else
                words.intersect(artistWords).size.toFloat() / artistWords.size
            titleOverlap < MIN_CONFIDENCE && artistOverlap < MIN_CONFIDENCE
        }

        return if (nonRepeated >= SANITY_CHECK_LINES) {
            LyricsAssessment.REJECTED
        } else {
            LyricsAssessment.ACCEPTED
        }
    }

    enum class LyricsAssessment { ACCEPTED, REJECTED }
}
