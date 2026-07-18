package com.audiophile.musicplayer.radio

import com.audiophile.musicplayer.data.source.SearchItemStatus
import com.audiophile.musicplayer.data.source.SourceSearchResult
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StreamingStationCandidateRankerTest {

    private val seed = StreamingStationSeed(
        id = "artist_weeknd",
        displayName = "The Weeknd Radio",
        kind = StreamingStationKind.ARTIST,
        seedArtist = "The Weeknd",
        queryPhrases = listOf("The Weeknd songs")
    )

    @Test
    fun isStationJunk_rejectsKaraoke() {
        val result = sampleResult(title = "Blinding Lights (Karaoke Version)", artist = "Karaoke Stars")
        assertTrue(StreamingStationCandidateRanker.isStationJunk(result, seed))
    }

    @Test
    fun isStationJunk_rejectsCountryRadioTitle() {
        val seed = StreamingStationSeed(
            id = "genre_country",
            displayName = "Country Radio",
            kind = StreamingStationKind.GENRE,
            seedArtists = listOf("Johnny Cash", "Luke Combs"),
            hintKeywords = listOf("country", "americana")
        )
        val junk = sampleResult(title = "Country Radio", artist = "Horseshoes & Hand Grenades")
        assertTrue(StreamingStationCandidateRanker.isStationJunk(junk, seed))
    }

    @Test
    fun isStationJunk_rejectsTopElectronicPlaylist() {
        val seed = StreamingStationSeed(
            id = "genre_country",
            displayName = "Country Radio",
            kind = StreamingStationKind.GENRE,
            seedArtists = listOf("Johnny Cash"),
            hintKeywords = listOf("country")
        )
        val junk = sampleResult(title = "Run (Top Electronic Songs)", artist = "Some DJ")
        assertTrue(StreamingStationCandidateRanker.isStationJunk(junk, seed))
    }

    @Test
    fun isStationJunk_allowsCountryRoadsWithCountryArtist() {
        val countrySeed = StreamingStationSeed(
            id = "genre_country",
            displayName = "Country Radio",
            kind = StreamingStationKind.GENRE,
            seedArtists = listOf("John Denver", "Johnny Cash"),
            hintKeywords = listOf("country")
        )
        val result = sampleResult(title = "Take Me Home, Country Roads", artist = "John Denver")
        assertFalse(StreamingStationCandidateRanker.isStationJunk(result, countrySeed))
    }

    @Test
    fun isStationJunk_allowsRealTrack() {
        val result = sampleResult(title = "Blinding Lights", artist = "The Weeknd")
        assertFalse(StreamingStationCandidateRanker.isStationJunk(result, seed))
    }

    @Test
    fun scoreCandidate_boostsSeedArtist() {
        val onTheme = sampleResult(title = "Starboy", artist = "The Weeknd")
        val offTheme = sampleResult(title = "Starboy", artist = "Random Cover Band")
        val onScore = StreamingStationCandidateRanker.scoreCandidate(onTheme, seed, StreamingStationTasteSignals())
        val offScore = StreamingStationCandidateRanker.scoreCandidate(offTheme, seed, StreamingStationTasteSignals())
        assertTrue(onScore > offScore)
    }

    @Test
    fun rankCandidates_capsArtistWithinSingleBatch() {
        val ranked = StreamingStationCandidateRanker.rankCandidates(
            results = listOf(
                sampleResult(id = "1", title = "Blinding Lights", artist = "The Weeknd"),
                sampleResult(id = "2", title = "Save Your Tears", artist = "The Weeknd"),
                sampleResult(id = "3", title = "Starboy", artist = "The Weeknd"),
                sampleResult(id = "4", title = "Levitating", artist = "Dua Lipa")
            ),
            seed = seed,
            taste = StreamingStationTasteSignals(),
            seenNormKeys = emptySet(),
            artistCounts = emptyMap(),
            maxPerArtist = 2
        )

        assertTrue(ranked.count { it.artist == "The Weeknd" } <= 2)
        assertTrue(ranked.any { it.artist == "Dua Lipa" })
    }

    @Test
    fun isStationJunk_rejectsEdmInCountryStation() {
        val seed = StreamingStationSeed(
            id = "genre_country",
            displayName = "Country Radio",
            kind = StreamingStationKind.GENRE,
            hintKeywords = listOf("country", "americana"),
            seedArtists = listOf("Johnny Cash", "Dolly Parton")
        )
        val edm = sampleResult(title = "Run (Top Electronic Songs)", artist = "EDM Tribe")
        val radio = sampleResult(title = "Country Radio", artist = "Various Artists")
        assertTrue(StreamingStationCandidateRanker.isStationJunk(edm, seed))
        assertTrue(StreamingStationCandidateRanker.isStationJunk(radio, seed))
    }

    @Test
    fun isStationJunk_allowsCountryArtist() {
        val seed = StreamingStationSeed(
            id = "genre_country",
            displayName = "Country Radio",
            kind = StreamingStationKind.GENRE,
            hintKeywords = listOf("country", "americana"),
            seedArtists = listOf("Johnny Cash", "Dolly Parton")
        )
        val track = sampleResult(title = "Ring of Fire", artist = "Johnny Cash")
        assertFalse(StreamingStationCandidateRanker.isStationJunk(track, seed))
    }

    private fun sampleResult(
        title: String,
        artist: String,
        id: String = "123"
    ): SourceSearchResult =
        SourceSearchResult(
            id = id,
            providerId = "deezer_gateway",
            title = title,
            artist = artist,
            album = "Album",
            coverSeed = "https://example.com/cover.jpg",
            durationMs = 200_000L,
            isrc = "USRC123",
            status = SearchItemStatus.SOURCE_FOUND,
            qualityLabel = "FLAC"
        )
}
