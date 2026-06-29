package com.audiophile.musicplayer.discovery.personalized

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DiscoveryCandidatePipelineTest {
    @Test
    fun dedupe_prefersIsrcThenNormKey() {
        val candidates = listOf(
            MixCandidate(title = "Song", artist = "Artist", isrc = "US123", normKey = "song|artist", score = 1.0),
            MixCandidate(title = "Song", artist = "Artist", isrc = "US123", normKey = "song|artist", score = 2.0),
            MixCandidate(title = "Song", artist = "Other", normKey = "song|other", score = 3.0)
        )
        assertEquals(2, DiscoveryCandidateFilters.dedupe(candidates).size)
    }

    @Test
    fun dedupe_collapsesSameTitleArtist() {
        val candidates = listOf(
            MixCandidate(title = "  Song ", artist = " Artist ", normKey = "song|artist"),
            MixCandidate(title = "Song", artist = "Artist", normKey = "song|artist")
        )
        assertEquals(1, DiscoveryCandidateFilters.dedupe(candidates).size)
    }

    @Test
    fun diversityCaps_limitPerArtistAndAlbum() {
        val candidates = listOf(
            MixCandidate(title = "A1", artist = "X", album = "Al1", score = 3.0),
            MixCandidate(title = "A2", artist = "X", album = "Al2", score = 2.0),
            MixCandidate(title = "A3", artist = "X", album = "Al3", score = 1.0),
            MixCandidate(title = "B1", artist = "Y", album = "Be", score = 4.0)
        )
        val capped = DiscoveryCandidateFilters.applyDiversityCaps(candidates, maxPerArtist = 2, maxPerAlbum = 1)
        assertEquals(2, capped.count { it.artist == "X" })
        assertTrue(capped.any { it.artist == "Y" })
    }

    @Test
    fun normalize_buildsNormKey() {
        val normalized = DiscoveryCandidateFilters.normalize(MixCandidate(title = " Hello ", artist = " World "))
        assertEquals("hello|world", normalized.normKey)
    }
}
