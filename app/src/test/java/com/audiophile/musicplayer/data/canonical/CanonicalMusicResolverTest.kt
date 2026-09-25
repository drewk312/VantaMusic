package com.audiophile.musicplayer.data.canonical

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CanonicalMusicResolverTest {

    @Test
    fun weekndDoesNotNormalizeToWeekend() {
        val weeknd = CanonicalMusicResolver.normalizeArtistName("The Weeknd")
        val weekend = CanonicalMusicResolver.normalizeArtistName("Weekend")
        assertNotEquals(weeknd, weekend)
        assertFalse(CanonicalMusicResolver.artistsEquivalent("The Weeknd", "Weekend"))
        assertTrue(CanonicalMusicResolver.artistsEquivalent("The Weeknd", "THE WEEKND"))
    }

    @Test
    fun versionsAreNotCollapsed() {
        assertFalse(
            CanonicalMusicResolver.versionsCompatible(
                "Blinding Lights",
                "Blinding Lights (Live)"
            )
        )
        assertFalse(
            CanonicalMusicResolver.versionsCompatible(
                "Blinding Lights",
                "Blinding Lights (Official Lyric Video)"
            )
        )
        assertTrue(
            CanonicalMusicResolver.versionsCompatible(
                "Blinding Lights",
                "Blinding Lights"
            )
        )
    }

    @Test
    fun durationsCompatibleWithinTolerance() {
        assertTrue(CanonicalMusicResolver.durationsCompatible(200_000L, 220_000L))
        assertFalse(CanonicalMusicResolver.durationsCompatible(200_000L, 260_000L))
        assertTrue(CanonicalMusicResolver.durationsCompatible(null, 200_000L))
    }

    @Test
    fun sanitizeArtworkRejectsNonHttp() {
        assertNull(CanonicalMusicResolver.sanitizeArtwork("coverSeed-123"))
        assertNull(CanonicalMusicResolver.sanitizeArtwork(""))
        assertEquals(
            "https://cdn.example/art.jpg",
            CanonicalMusicResolver.sanitizeArtwork("https://cdn.example/art.jpg")
        )
    }

    @Test
    fun metadataQualityPenalizesVevoAndOfficialAudio() {
        val clean = CanonicalMusicResolver.metadataQualityTrack(
            CanonicalMusicResolver.TrackInput(
                title = "Blinding Lights",
                artist = "The Weeknd",
                album = "After Hours",
                isrc = "USUG11903892",
                artworkUrl = "https://cdn.example/a.jpg",
                providerId = "qobuz",
                externalTrackId = "1"
            ),
            title = "Blinding Lights",
            artist = "The Weeknd"
        )
        val dirty = CanonicalMusicResolver.metadataQualityTrack(
            CanonicalMusicResolver.TrackInput(
                title = "The Weeknd - Blinding Lights (Official Audio)",
                artist = "TheWeekndVEVO",
                album = null,
                isrc = null,
                artworkUrl = null,
                providerId = "youtube_music",
                externalTrackId = "vid"
            ),
            title = "The Weeknd - Blinding Lights (Official Audio)",
            artist = "TheWeekndVEVO"
        )
        assertTrue(clean > dirty)
    }

    @Test
    fun matchConfidenceFuzzyNeverImpliesExact() {
        assertEquals(MatchConfidence.FUZZY_CANDIDATE.name, "FUZZY_CANDIDATE")
        assertTrue(MatchConfidence.EXACT_ISRC.ordinal < MatchConfidence.FUZZY_CANDIDATE.ordinal)
    }
}
