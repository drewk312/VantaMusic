package com.audiophile.musicplayer.data.source

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class SourceCandidateRankerRegressionTest {

    @Test
    fun exactProviderIdDoesNotBypassWrongArtist() {
        val selected = SelectedRecordingIdentity(
            title = "La Grange",
            artist = "ZZ Top",
            preferredProviderId = "cloudflare_gateway",
            preferredExternalTrackId = "deezer:cover"
        )
        val cover = searchResult(
            id = "deezer:cover",
            title = "La Grange (ZZ Top)",
            artist = "Matteo Leonetti",
            album = "L'alfabeto Del ROCK"
        ).copy(providerId = "cloudflare_gateway")

        val evaluation = SourceIdentityGate.evaluateSearchResult(selected, cover)

        assertFalse(evaluation.accepted)
        assertEquals("wrong_title", evaluation.rejectionReason)
    }

    @Test
    fun repairRanking_keepsOnlyIdentityApprovedCandidates() {
        val selected = SelectedRecordingIdentity(
            title = "Hello",
            artist = "Adele",
            durationMs = 295_000L
        )
        val ranked = SourceCandidateRanker.rankSearchResults(
            selected = selected,
            candidates = listOf(
                searchResult(id = "bad", title = "Hello", artist = "Tribute Band", album = "Covers"),
                searchResult(id = "good", title = "Hello", artist = "Adele", album = "25", durationMs = 295_000L)
            )
        )

        assertEquals(listOf("good"), ranked.map { it.id })
    }

    @Test
    fun repairRanking_returnsNullWhenIdentityGateRejectsEverything() {
        val selected = SelectedRecordingIdentity(
            title = "Piano Man",
            artist = "Billy Joel"
        )

        val best = SourceCandidateRanker.bestSearchResult(
            selected = selected,
            candidates = listOf(
                searchResult(id = "wrong-song", title = "Uptown Girl", artist = "Billy Joel"),
                searchResult(id = "wrong-artist", title = "Piano Man", artist = "Tribute Players")
            )
        )

        assertNull(best)
    }

    @Test
    fun spiritInTheSky_keepsNormanGreenbaumAndRejectsOtherArtists() {
        val selected = SelectedRecordingIdentity(
            title = "Spirit in the Sky",
            artist = "Norman Greenbaum",
            durationMs = 240_000L
        )

        val ranked = SourceCandidateRanker.rankSearchResults(
            selected = selected,
            candidates = listOf(
                searchResult(id = "wrong-hit", title = "Spirit in the Sky", artist = "Doctor and the Medics", durationMs = 240_000L),
                searchResult(id = "tribute", title = "Spirit in the Sky", artist = "Classic Rock Tribute Band", durationMs = 240_000L),
                searchResult(id = "correct", title = "Spirit in the Sky", artist = "Norman Greenbaum", durationMs = 240_000L)
            )
        )

        assertEquals(listOf("correct"), ranked.map { it.id })
    }

    @Test
    fun tearsForFears_rejectsWrongSongEvenWhenArtistMatches() {
        val selected = SelectedRecordingIdentity(
            title = "Everybody Wants To Rule The World",
            artist = "Tears for Fears",
            durationMs = 251_000L
        )

        val ranked = SourceCandidateRanker.rankSearchResults(
            selected = selected,
            candidates = listOf(
                searchResult(id = "wrong-song", title = "Shout", artist = "Tears for Fears", durationMs = 393_000L),
                searchResult(id = "cover", title = "Everybody Wants To Rule The World", artist = "Cover Band", durationMs = 251_000L),
                searchResult(id = "correct", title = "Everybody Wants To Rule The World", artist = "Tears for Fears", durationMs = 251_000L)
            )
        )

        assertEquals(listOf("correct"), ranked.map { it.id })
    }

    private fun searchResult(
        id: String,
        title: String,
        artist: String,
        album: String? = null,
        durationMs: Long = 200_000L
    ) = SourceSearchResult(
        id = id,
        providerId = "test_provider",
        title = title,
        artist = artist,
        album = album,
        coverSeed = title,
        durationMs = durationMs,
        status = SearchItemStatus.SOURCE_FOUND,
        qualityLabel = "320 kbps"
    )

    @Test
    fun sweetChildOMine_acceptsGunsNRosesRejectsCoverKaraokeAndGospel() {
        val selected = SelectedRecordingIdentity(
            title = "Sweet Child O Mine",
            artist = "Guns N Roses",
            durationMs = 356_000L
        )
        val ranked = SourceCandidateRanker.rankSearchResults(
            selected = selected,
            candidates = listOf(
                searchResult(id = "gospel", title = "Sweet Child O Mine", artist = "Worship Praise Band", durationMs = 356_000L),
                searchResult(id = "karaoke", title = "Sweet Child O Mine (Karaoke Version)", artist = "Karaoke Hits", durationMs = 356_000L),
                searchResult(id = "instrumental", title = "Sweet Child O Mine (Instrumental)", artist = "Rock Instrumentals", durationMs = 356_000L),
                searchResult(id = "correct", title = "Sweet Child O Mine", artist = "Guns N Roses", durationMs = 356_000L)
            )
        )
        assertEquals(listOf("correct"), ranked.map { it.id })
    }

    @Test
    fun blindingLights_acceptsTheWeekndRejectsCoverRemixKaraoke() {
        val selected = SelectedRecordingIdentity(
            title = "Blinding Lights",
            artist = "The Weeknd",
            durationMs = 200_000L
        )
        val ranked = SourceCandidateRanker.rankSearchResults(
            selected = selected,
            candidates = listOf(
                searchResult(id = "remix", title = "Blinding Lights Remix", artist = "DJ Remix", durationMs = 240_000L),
                searchResult(id = "cover", title = "Blinding Lights (Piano Cover)", artist = "Piano Tribute Players", durationMs = 200_000L),
                searchResult(id = "karaoke", title = "Blinding Lights (Karaoke)", artist = "Karaoke Stars", durationMs = 200_000L),
                searchResult(id = "correct", title = "Blinding Lights", artist = "The Weeknd", durationMs = 200_000L)
            )
        )
        assertEquals(listOf("correct"), ranked.map { it.id })
    }

    @Test
    fun elPaso_acceptsMartyRobbinsRejectsTravelNewsAndCover() {
        val selected = SelectedRecordingIdentity(
            title = "El Paso",
            artist = "Marty Robbins",
            durationMs = 257_000L
        )
        val ranked = SourceCandidateRanker.rankSearchResults(
            selected = selected,
            candidates = listOf(
                searchResult(id = "travel", title = "El Paso Travel Guide", artist = "Travel Channel", durationMs = 600_000L),
                searchResult(id = "news", title = "El Paso News Report", artist = "News Channel", durationMs = 120_000L),
                searchResult(id = "cover", title = "El Paso", artist = "Cover Artist", durationMs = 257_000L),
                searchResult(id = "correct", title = "El Paso", artist = "Marty Robbins", durationMs = 257_000L)
            )
        )
        assertEquals(listOf("correct"), ranked.map { it.id })
    }

    @Test
    fun theLessIKnowTheBetter_acceptsTameImpalaRejectsLullabyAndCover() {
        val selected = SelectedRecordingIdentity(
            title = "The Less I Know The Better",
            artist = "Tame Impala",
            durationMs = 216_000L
        )
        val ranked = SourceCandidateRanker.rankSearchResults(
            selected = selected,
            candidates = listOf(
                searchResult(id = "lullaby", title = "The Less I Know The Better", artist = "Lullaby Players", durationMs = 216_000L),
                searchResult(id = "cover", title = "The Less I Know The Better (Acoustic Cover)", artist = "Acoustic Covers", durationMs = 216_000L),
                searchResult(id = "correct", title = "The Less I Know The Better", artist = "Tame Impala", durationMs = 216_000L)
            )
        )
        assertEquals(listOf("correct"), ranked.map { it.id })
    }

    @Test
    fun victoryLap_acceptsNipseyHussleRejectsSportsMotivationAndTravel() {
        val selected = SelectedRecordingIdentity(
            title = "Victory Lap",
            artist = "Nipsey Hussle",
            durationMs = 222_000L
        )
        val ranked = SourceCandidateRanker.rankSearchResults(
            selected = selected,
            candidates = listOf(
                searchResult(id = "motivation", title = "Victory Lap", artist = "Motivational Speaker", durationMs = 222_000L),
                searchResult(id = "sports", title = "Victory Lap Sports Highlights", artist = "Sports Channel", durationMs = 180_000L),
                searchResult(id = "cover", title = "Victory Lap", artist = "Cover Band", durationMs = 222_000L),
                searchResult(id = "correct", title = "Victory Lap", artist = "Nipsey Hussle", durationMs = 222_000L)
            )
        )
        assertEquals(listOf("correct"), ranked.map { it.id })
    }

}
