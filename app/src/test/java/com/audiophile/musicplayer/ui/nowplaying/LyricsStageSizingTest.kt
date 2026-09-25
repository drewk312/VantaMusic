package com.audiophile.musicplayer.ui.nowplaying

import com.audiophile.musicplayer.data.lyrics.LyricsData
import com.audiophile.musicplayer.data.lyrics.LyricsIdentity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LyricsStageSizingTest {

    private val lyricData = LyricsData(
        trackKey = "test-track",
        isSynced = true,
        lines = emptyList(),
        providerId = "test"
    )

    @Test
    fun focusLyricSizing_stepsDownForLongerLines() {
        val short = narrativeLyricFontSizeSp(18)
        val medium = narrativeLyricFontSizeSp(44)
        val long = narrativeLyricFontSizeSp(76)
        val veryLong = narrativeLyricFontSizeSp(110)

        assertEquals(60f, short)
        assertTrue(short > medium)
        assertTrue(medium > long)
        assertTrue(long > veryLong)
    }

    @Test
    fun focusFilm_onlyUsesVerifiedSynchronizedLyrics() {
        assertTrue(supportsSynchronizedLyricFilm(LyricsIdentity.ExactSync(lyricData)))
        assertTrue(supportsSynchronizedLyricFilm(LyricsIdentity.HighConfidenceSync(lyricData)))
        assertTrue(!supportsSynchronizedLyricFilm(LyricsIdentity.EstimatedTiming(lyricData.copy(isSynced = false))))
    }

    @Test
    fun lyricClock_leadsPlaybackAndHonorsManualCorrection() {
        assertEquals(10_000L, adjustedLyricClockPositionMs(10_000L, 0L))
        assertEquals(10_500L, adjustedLyricClockPositionMs(10_000L, 500L))
        assertEquals(9_750L, adjustedLyricClockPositionMs(10_000L, -250L))
    }
}
