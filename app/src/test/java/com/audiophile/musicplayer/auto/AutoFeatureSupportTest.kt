package com.audiophile.musicplayer.auto

import com.audiophile.musicplayer.data.lyrics.LyricsLine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AutoFeatureSupportTest {
    @Test
    fun lruCacheRetainsRecentEntriesAndEvictsOldest() {
        val cache = AutoLruCache<String, Int>(2)
        cache.put("one", 1)
        cache.put("two", 2)
        assertEquals(1, cache.get("one")) // one is now most recently used
        cache.put("three", 3)

        assertNull(cache.get("two"))
        assertEquals(1, cache.get("one"))
        assertEquals(3, cache.get("three"))
    }

    @Test
    fun remoteMediaIdRoundTripsReservedCharacters() {
        val prefix = "vanta:remote:"
        val id = AutoMediaIdCodec.remoteTrackId(prefix, "provider/a+b", "song | id/42")
        val decoded = AutoMediaIdCodec.parseRemoteTrackId(prefix, id)

        assertEquals("provider/a+b", decoded?.providerId)
        assertEquals("song | id/42", decoded?.externalTrackId)
    }

    @Test
    fun malformedRemoteMediaIdIsRejected() {
        assertNull(AutoMediaIdCodec.parseRemoteTrackId("vanta:remote:", "vanta:remote:missing-separator"))
        assertNull(AutoMediaIdCodec.parseRemoteTrackId("vanta:remote:", "other:value|id"))
    }

    @Test
    fun karaokeTimelineSelectsCurrentLineAndSurvivesTimingGaps() {
        val lines = listOf(
            LyricsLine(startTimeMs = 1_000, endTimeMs = 2_000, text = "First"),
            LyricsLine(startTimeMs = 3_000, endTimeMs = 4_000, text = "Second"),
        )

        assertEquals("First", KaraokeTimeline.lineAt(lines, 1_500))
        assertEquals("First", KaraokeTimeline.lineAt(lines, 2_500))
        assertEquals("Second", KaraokeTimeline.lineAt(lines, 3_500))
        assertNull(KaraokeTimeline.lineAt(lines, 500))
    }

    @Test
    fun cacheRequiresPositiveCapacity() {
        assertTrue(runCatching { AutoLruCache<String, String>(0) }.isFailure)
    }

    @Test
    fun lyricTruncationPrefersWordBoundary() {
        val longLine = "Puffin' red-cup kisses, sweet redemption in the morning light"
        val truncated = AutoMainStageLyrics.truncateForAutoDisplay(longLine)

        assertTrue(truncated.length <= AutoMainStageLyrics.MAX_DESCRIPTION_CHARS + 1)
        assertTrue(truncated.endsWith("…"))
        assertEquals(
            "Puffin' red-cup kisses, sweet redemption in the…",
            truncated,
        )
    }

    @Test
    fun lyricTruncationKeepsShortLinesIntact() {
        assertEquals("Short line", AutoMainStageLyrics.truncateForAutoDisplay("Short line"))
    }

    @Test
    fun artistAlbumLineCombinesAlbumWhenPresent() {
        assertEquals(
            "Taylor Swift • Midnights",
            AutoMainStageLyrics.formatArtistAlbumLine("Anti-Hero", "Taylor Swift", "Midnights"),
        )
    }

    @Test
    fun artistAlbumLineOmitsAlbumWhenMissing() {
        assertEquals(
            "Taylor Swift",
            AutoMainStageLyrics.formatArtistAlbumLine("Anti-Hero", "Taylor Swift", null),
        )
    }
}
