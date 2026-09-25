package com.audiophile.musicplayer.radio

import com.audiophile.musicplayer.data.dj.JukeboxCatalog
import com.audiophile.musicplayer.data.dj.StationSearchResolver
import com.audiophile.musicplayer.data.local.entities.TrackFeatureEntity
import com.audiophile.musicplayer.radio.sonic.RadioDiscoveryMode as SonicRadioDiscoveryMode
import com.audiophile.musicplayer.radio.sonic.SonicStationSpec
import com.audiophile.musicplayer.radio.sonic.StreamingEraFilter as SonicStreamingEraFilter
import com.audiophile.musicplayer.radio.sonic.StreamingStationCandidateRanker as SonicCandidateRanker
import org.junit.Assert.*
import org.junit.Test

class OldiesStationCatalogTest {

    @Test
    fun testSearchOldiesResolvesAllSixOldiesStations() {
        val candidates = StationSearchResolver.resolve("oldies")
        val ids = candidates.map { it.id }.toSet()

        assertTrue("Should contain golden_oldies", ids.contains("golden_oldies"))
        assertTrue("Should contain fifties_rock_roll", ids.contains("fifties_rock_roll"))
        assertTrue("Should contain sixties_invasion", ids.contains("sixties_invasion"))
        assertTrue("Should contain motown_soul", ids.contains("motown_soul"))
        assertTrue("Should contain doo_wop_classics", ids.contains("doo_wop_classics"))
        assertTrue("Should contain rockabilly_stomp", ids.contains("rockabilly_stomp"))
        assertTrue("Expected 6 or more candidates, got ${candidates.size}", candidates.size >= 6)
    }

    @Test
    fun testSearchYachtRockResolvesYachtStation() {
        val candidates = StationSearchResolver.resolve("yacht rock")
        assertTrue("Candidates should contain yacht_rock", candidates.any { it.id == "yacht_rock" })
        val spec = JukeboxCatalog.sonicStationSpecs["yacht_rock"]
        assertNotNull("Spec must exist", spec)
        assertEquals(1973, spec?.eraStart)
        assertEquals(1984, spec?.eraEnd)
        assertTrue(spec?.seeds?.contains("Steely Dan") == true)
        assertTrue(spec?.forbiddenGenres?.contains("EDM") == true)
    }

    @Test
    fun testSearchOneHitWondersResolvesOneHitWonderShowcase() {
        val candidates = StationSearchResolver.resolve("one-hit wonders")
        assertTrue("Candidates should contain one_hit_wonders", candidates.any { it.id == "one_hit_wonders" })
        val spec = JukeboxCatalog.sonicStationSpecs["one_hit_wonders"]
        assertNotNull(spec)
        assertTrue(spec?.seeds?.contains("Norman Greenbaum") == true)
    }

    @Test
    fun testEraGateEnforcementRejectsOutOfEraTracks() {
        val goldenOldies = JukeboxCatalog.sonicStationSpecs["golden_oldies"]
        assertNotNull(goldenOldies)

        // Track from 1958 should pass
        assertTrue(
            "1958 Chuck Berry must pass Golden Oldies era gate",
            SonicStreamingEraFilter.passesEraGate(1958, "Johnny B. Goode", goldenOldies!!)
        )

        // Track from 1965 should pass
        assertTrue(
            "1965 Beatles must pass Golden Oldies era gate",
            SonicStreamingEraFilter.passesEraGate(1965, "Help!", goldenOldies)
        )

        // 2024 EDM track must fail era gate
        assertFalse(
            "2024 track must be rejected by Golden Oldies era gate",
            SonicStreamingEraFilter.passesEraGate(2024, "Modern Pop Anthem", goldenOldies)
        )

        // 1985 track must fail era gate
        assertFalse(
            "1985 track must be rejected by Golden Oldies (1950-1969) era gate",
            SonicStreamingEraFilter.passesEraGate(1985, "Synth Anthem", goldenOldies)
        )
    }

    @Test
    fun testEraGateRejectsTrapKeywords() {
        val goldenOldies = JukeboxCatalog.sonicStationSpecs["golden_oldies"]!!

        // Track with "karaoke"
        assertFalse(
            "Karaoke track must be rejected",
            SonicStreamingEraFilter.passesEraGate(1962, "Stand By Me (Karaoke Version)", goldenOldies)
        )

        // Track with "tribute"
        assertFalse(
            "Tribute track must be rejected",
            SonicStreamingEraFilter.passesEraGate(1965, "Elvis Tribute Band Live", goldenOldies)
        )

        // Track with "remix"
        assertFalse(
            "Remix track must be rejected",
            SonicStreamingEraFilter.passesEraGate(1968, "Hey Jude (Club Remix 2022)", goldenOldies)
        )

        // Track with "re-recorded"
        assertFalse(
            "Re-recorded track must be rejected",
            SonicStreamingEraFilter.passesEraGate(1960, "Only The Lonely (Re-Recorded / Remastered)", goldenOldies)
        )
    }

    @Test
    fun testRadioDiscoveryModeRankerBoostsFavoritesInMyFavoritesMode() {
        val favoriteEntity = TrackFeatureEntity(
            trackId = "101",
            title = "Stand by Me",
            artist = "Ben E. King",
            energy = 0.7,
            valence = 0.8,
            danceability = 0.7,
            acousticness = 0.2
        )
        val nonFavoriteEntity = TrackFeatureEntity(
            trackId = "102",
            title = "Unchained Melody",
            artist = "The Righteous Brothers",
            energy = 0.7,
            valence = 0.8,
            danceability = 0.7,
            acousticness = 0.2
        )

        val favorites = setOf("101")
        val history = emptySet<String>()

        val rankerFavorites = SonicCandidateRanker(SonicRadioDiscoveryMode.MY_FAVORITES)
        val rankedInFavoritesMode = rankerFavorites.rankCandidates(
            candidates = listOf(nonFavoriteEntity, favoriteEntity),
            userFavoritesIds = favorites,
            recentHistoryTrackIds = history
        )

        assertEquals("Favorite track must rank #1 in MY_FAVORITES mode", "101", rankedInFavoritesMode.first().trackId)

        val rankerDeepDiscovery = SonicCandidateRanker(SonicRadioDiscoveryMode.DEEP_DISCOVERY)
        val rankedInDeepDiscoveryMode = rankerDeepDiscovery.rankCandidates(
            candidates = listOf(favoriteEntity, nonFavoriteEntity),
            userFavoritesIds = favorites,
            recentHistoryTrackIds = history
        )

        assertEquals("Non-favorite fresh cut must rank #1 in DEEP_DISCOVERY mode", "102", rankedInDeepDiscoveryMode.first().trackId)
    }
}
