package com.audiophile.musicplayer.data.dj

import org.junit.Assert.*
import org.junit.Test

class RadioIdentityPolicyTest {
    @Test fun dominantRegionalTasteAppliesToDailyRefillsButKeepsExplicitFavorites() {
        val taste = AiDjTasteProfile(favoriteGenres = listOf("Bollywood", "Pop"), favoriteArtists = listOf("Dream Lite"))
        assertFalse(RadioIdentityPolicy.acceptsTaste(taste, "Random Artist", "English Pop"))
        assertTrue(RadioIdentityPolicy.acceptsTaste(taste, "Hindi Artist", "Bollywood"))
        assertTrue(RadioIdentityPolicy.acceptsTaste(taste, "Dream Lite", null))
    }
    @Test fun exactArtistSearchRejectsUnrelatedHits() {
        assertTrue(RadioIdentityPolicy.matches("Song", "Dream Lite", null, "dream lite"))
        assertFalse(RadioIdentityPolicy.matches("Dream Lite", "Someone Else", null, "Dream Lite"))
        assertFalse(RadioIdentityPolicy.matches("Wrong Song", "Last Option", "Requested Song", "Last Option"))
    }
    @Test fun regionalStationDoesNotFallBackToEnglishOrUnknownTracks() {
        val station = JukeboxStation("hindi", "Hindi Bollywood", "", listOf("hindi", "bollywood"))
        assertTrue(RadioIdentityPolicy.acceptsStation(station, "Artist", "Hindi Film"))
        assertFalse(RadioIdentityPolicy.acceptsStation(station, "Artist", "English Pop"))
        assertFalse(RadioIdentityPolicy.acceptsStation(station, "Artist", null))
    }
    @Test fun artistStationKeepsItsSeedWhenPoolIsEmpty() {
        val station = JukeboxStation("dream", "Dream Lite", "", seedArtists = listOf("Dream Lite", "Last Option"))
        assertTrue(RadioIdentityPolicy.acceptsStation(station, "Last Option", null))
        assertFalse(RadioIdentityPolicy.acceptsStation(station, "Random English Artist", "pop"))
    }
}
