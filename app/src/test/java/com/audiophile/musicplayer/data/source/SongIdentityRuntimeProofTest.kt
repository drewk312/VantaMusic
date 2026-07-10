package com.audiophile.musicplayer.data.source

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * P0 Song Identity Runtime Proof.
 *
 * These tests exercise the exact ranking path used at playback time
 * (SourceCandidateRanker.rankSearchResults) against realistic junk candidates.
 *
 * Goal: when the user requests a famous song, the original artist wins and
 * covers/karaoke/tribute/gospel/8-bit/etc. are rejected before any stream is resolved.
 */
class SongIdentityRuntimeProofTest {

    private fun searchResult(
        id: String,
        title: String,
        artist: String,
        album: String? = null,
        durationMs: Long = 200_000L,
        providerId: String = "test_provider",
        bitrateKbps: Int = 320
    ) = SourceSearchResult(
        id = id,
        providerId = providerId,
        title = title,
        artist = artist,
        album = album,
        coverSeed = title,
        durationMs = durationMs,
        status = SearchItemStatus.SOURCE_FOUND,
        qualityLabel = " kbps"
    )

    @Test
    fun sweetChildOMine_runtimeProof_gunsNRosesWinsOverAllJunk() {
        val selected = SelectedRecordingIdentity(
            title = "Sweet Child O Mine",
            artist = "Guns N Roses",
            durationMs = 356_000L,
            userQuery = "Sweet Child O Mine Guns N Roses"
        )

        val candidates = listOf(
            // Original artist must win
            searchResult(id = "original", title = "Sweet Child O Mine", artist = "Guns N Roses", durationMs = 356_000L),
            // Obvious variants
            searchResult(id = "karaoke", title = "Sweet Child O Mine (Karaoke Version)", artist = "Karaoke Hits", durationMs = 356_000L),
            searchResult(id = "instrumental", title = "Sweet Child O Mine (Instrumental)", artist = "Rock Instrumentals", durationMs = 356_000L),
            searchResult(id = "piano", title = "Sweet Child O Mine (Piano Cover)", artist = "Piano Tribute Players", durationMs = 356_000L),
            searchResult(id = "acoustic", title = "Sweet Child O Mine (Acoustic Cover)", artist = "Acoustic Covers", durationMs = 356_000L),
            searchResult(id = "live", title = "Sweet Child O Mine (Live at Wembley)", artist = "Guns N Roses", durationMs = 420_000L),
            searchResult(id = "remix", title = "Sweet Child O Mine (Slowed + Reverb)", artist = "8D Remix", durationMs = 400_000L),
            searchResult(id = "8bit", title = "Sweet Child O Mine (8-Bit Version)", artist = "8-Bit Arcade", durationMs = 356_000L),
            searchResult(id = "string", title = "Sweet Child O Mine", artist = "Vitamin String Quartet", durationMs = 356_000L),
            searchResult(id = "kids", title = "Sweet Child O Mine", artist = "Kidz Bop", durationMs = 356_000L),
            // Identity confusers
            searchResult(id = "gospel", title = "Sweet Child O Mine", artist = "Praise and Worship Collective", durationMs = 356_000L),
            searchResult(id = "lullaby", title = "Sweet Child O Mine", artist = "Rockabye Baby!", album = "Lullaby Renditions of Guns N Roses", durationMs = 356_000L),
            searchResult(id = "orchestra", title = "Sweet Child O Mine", artist = "London Symphony Orchestra", durationMs = 356_000L),
            searchResult(id = "tribute", title = "Sweet Child O Mine", artist = "Ultimate Tribute Stars", durationMs = 356_000L),
            searchResult(id = "in-style", title = "Sweet Child O Mine", artist = "The Crew", album = "In the Style of Guns N Roses", durationMs = 356_000L),
            searchResult(id = "made-famous", title = "Sweet Child O Mine", artist = "Cover Hits", album = "Made Famous by Guns N Roses", durationMs = 356_000L)
        ).shuffled() // shuffle to prove ranking, not order

        val ranked = SourceCandidateRanker.rankSearchResults(selected, candidates)

        assertEquals("original", ranked.first().id)
        assertEquals(1, ranked.size)
    }

    @Test
    fun bohemianRhapsody_runtimeProof_queenWinsOverAllJunk() {
        val selected = SelectedRecordingIdentity(
            title = "Bohemian Rhapsody",
            artist = "Queen",
            durationMs = 354_000L,
            userQuery = "Bohemian Rhapsody Queen"
        )

        val candidates = listOf(
            searchResult(id = "original", title = "Bohemian Rhapsody", artist = "Queen", durationMs = 354_000L),
            searchResult(id = "piano", title = "Bohemian Rhapsody (Piano Cover)", artist = "Piano Tribute Players", durationMs = 354_000L),
            searchResult(id = "karaoke", title = "Bohemian Rhapsody (Karaoke)", artist = "Karaoke Stars", durationMs = 354_000L),
            searchResult(id = "orchestra", title = "Bohemian Rhapsody", artist = "Royal Philharmonic Orchestra", durationMs = 354_000L),
            searchResult(id = "tribute", title = "Bohemian Rhapsody", artist = "Tribute to Queen", durationMs = 354_000L),
            searchResult(id = "lullaby", title = "Bohemian Rhapsody", artist = "Rockabye Baby!", album = "Lullaby Renditions of Queen", durationMs = 354_000L),
            searchResult(id = "kids", title = "Bohemian Rhapsody", artist = "Kidz Bop", durationMs = 354_000L)
        ).shuffled()

        val ranked = SourceCandidateRanker.rankSearchResults(selected, candidates)

        assertEquals("original", ranked.first().id)
        assertEquals(1, ranked.size)
    }

    @Test
    fun smellsLikeTeenSpirit_runtimeProof_nirvanaWinsOverAllJunk() {
        val selected = SelectedRecordingIdentity(
            title = "Smells Like Teen Spirit",
            artist = "Nirvana",
            durationMs = 301_000L,
            userQuery = "Smells Like Teen Spirit Nirvana"
        )

        val candidates = listOf(
            searchResult(id = "original", title = "Smells Like Teen Spirit", artist = "Nirvana", durationMs = 301_000L),
            searchResult(id = "cover", title = "Smells Like Teen Spirit", artist = "In the Style of Nirvana", durationMs = 301_000L),
            searchResult(id = "tribute", title = "Smells Like Teen Spirit", artist = "Ultimate Tribute Stars", durationMs = 301_000L),
            searchResult(id = "karaoke", title = "Smells Like Teen Spirit (Karaoke Version)", artist = "Party Tyme Karaoke", durationMs = 301_000L),
            searchResult(id = "orchestra", title = "Smells Like Teen Spirit", artist = "Roma Symphony Orchestra", durationMs = 301_000L),
            searchResult(id = "lullaby", title = "Smells Like Teen Spirit", artist = "Rockabye Baby!", album = "Lullaby Renditions of Nirvana", durationMs = 301_000L),
            searchResult(id = "acoustic", title = "Smells Like Teen Spirit (Acoustic)", artist = "Acoustic Unplugged", durationMs = 301_000L)
        ).shuffled()

        val ranked = SourceCandidateRanker.rankSearchResults(selected, candidates)

        assertEquals("original", ranked.first().id)
        assertEquals(1, ranked.size)
    }

    @Test
    fun wrongSongIdentity_runtimeProof_rejectsWrongSongEvenWhenArtistMatches() {
        val selected = SelectedRecordingIdentity(
            title = "Welcome to the Jungle",
            artist = "Guns N Roses",
            durationMs = 275_000L,
            userQuery = "Welcome to the Jungle Guns N Roses"
        )

        val candidates = listOf(
            searchResult(id = "correct", title = "Welcome to the Jungle", artist = "Guns N Roses", durationMs = 275_000L),
            searchResult(id = "wrong-song", title = "Sweet Child O Mine", artist = "Guns N Roses", durationMs = 356_000L),
            searchResult(id = "wrong-artist", title = "Welcome to the Jungle", artist = "Tribute Band", durationMs = 275_000L)
        ).shuffled()

        val ranked = SourceCandidateRanker.rankSearchResults(selected, candidates)

        assertEquals("correct", ranked.first().id)
        assertEquals(1, ranked.size)
    }

    @Test
    fun noOriginalPresent_runtimeProof_returnsEmpty() {
        val selected = SelectedRecordingIdentity(
            title = "Sweet Child O Mine",
            artist = "Guns N Roses",
            durationMs = 356_000L,
            userQuery = "Sweet Child O Mine Guns N Roses"
        )

        val candidates = listOf(
            searchResult(id = "karaoke", title = "Sweet Child O Mine (Karaoke Version)", artist = "Karaoke Hits", durationMs = 356_000L),
            searchResult(id = "instrumental", title = "Sweet Child O Mine (Instrumental)", artist = "Rock Instrumentals", durationMs = 356_000L),
            searchResult(id = "tribute", title = "Sweet Child O Mine", artist = "Tribute Stars", durationMs = 356_000L)
        )

        val best = SourceCandidateRanker.bestSearchResult(selected, candidates)

        assertNull(best)
    }
}
