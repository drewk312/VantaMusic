package com.audiophile.musicplayer.data.lyrics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LrcParserTest {
    @Test
    fun parseSyncedLines_handlesLrclibCentisecondFormat() {
        val lines = LrcParser.parseSyncedLines(
            """
            [00:14.26] Yeah
            [00:27.82] I've been tryna call
            """.trimIndent()
        )

        assertNotNull(lines)
        assertEquals(2, lines!!.size)
        assertEquals(14_260L, lines[0].startTimeMs)
        assertEquals("Yeah", lines[0].text)
        assertEquals(27_820L, lines[1].startTimeMs)
    }

    @Test
    fun parseSyncedLines_handlesSingleDigitMinutesAndNoFraction() {
        val lines = LrcParser.parseSyncedLines(
            """
            [0:12] Intro line
            [1:05.4] Next line
            """.trimIndent()
        )

        assertNotNull(lines)
        assertEquals(12_000L, lines!![0].startTimeMs)
        assertEquals(65_400L, lines[1].startTimeMs)
    }

    @Test
    fun parseSyncedLines_ignoresMetadataAndBlankRows() {
        val lines = LrcParser.parseSyncedLines(
            """
            [ar:The Weeknd]
            [00:18.11]
            [instrumental:true]
            [00:20.00] Real lyric
            """.trimIndent()
        )

        assertNotNull(lines)
        assertEquals(1, lines!!.size)
        assertEquals("Real lyric", lines[0].text)
    }

    @Test
    fun parseSyncedLines_rejectsInstrumentalOnlyMarker() {
        assertNull(LrcParser.parseSyncedLines("[instrumental:true]"))
    }

    @Test
    fun estimatePlainLyricTimings_assignsMonotonicStarts() {
        val plain = LrcParser.parsePlainLines("Line one\nLine two\nLine three")
        val timed = LrcParser.estimatePlainLyricTimings(plain, durationMs = 180_000L)

        assertEquals(3, timed.size)
        assertTrue(timed[0].startTimeMs!! < timed[1].startTimeMs!!)
        assertTrue(timed[1].startTimeMs!! < timed[2].startTimeMs!!)
        assertNotNull(timed[0].endTimeMs)
    }

    @Test
    fun activeLyricLineIndex_respectsLineEndWindows() {
        val lines = listOf(
            LyricsLine(startTimeMs = 1_000, endTimeMs = 2_000, text = "First"),
            LyricsLine(startTimeMs = 3_000, endTimeMs = 4_000, text = "Second"),
        )

        assertEquals(0, activeLyricLineIndex(lines, 1_500))
        assertEquals(0, activeLyricLineIndex(lines, 2_500))
        assertEquals(1, activeLyricLineIndex(lines, 3_500))
        assertEquals(-1, activeLyricLineIndex(lines, 500))
    }
}
