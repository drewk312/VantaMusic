package com.audiophile.musicplayer.data.source

import com.audiophile.musicplayer.data.dj.JukeboxCatalog
import com.audiophile.musicplayer.data.dj.toStreamingSeed
import com.audiophile.musicplayer.radio.toStationSeed
import com.audiophile.musicplayer.radio.toStreamingSeedParams
import com.audiophile.musicplayer.radio.StreamingStationQueryPlanner
import org.junit.Assert.*
import org.junit.Test

class SingleRecordingRadioTest {
    @Test fun rejectsYearRankingsEvenWithoutDuration() {
        listOf("Top Songs of 1954", "Top 50 Songs of 1954", "Greatest Hits From 1962", "Best 100 Songs", "Golden Oldies Mix").forEach {
            assertTrue(it, ContentPurityFilter.isClearlyNonMusicContent(it, "The Top Music Retro", durationMs = 3_600_000))
            assertFalse(ContentPurityFilter.isAllowed(it, "The Top Music Retro", null, 3_600_000, "youtube"))
        }
        assertTrue(ContentPurityFilter.isClearlyNonMusicContent("Top Songs of 1954", "The Top Music Retro"))
    }
    @Test fun preservesIndividualSongsAndAnthologyAlbums() {
        assertFalse(ContentPurityFilter.isClearlyNonMusicContent("Summer of '69", "Bryan Adams"))
        assertFalse(ContentPurityFilter.isClearlyNonMusicContent("Top of the World", "Carpenters"))
        assertFalse(ContentPurityFilter.isClearlyNonMusicContent("1985", "Bowling for Soup"))
        assertFalse(isPlaylistCompilationArtifact("Blueberry Hill", "Fats Domino", "Greatest Hits Compilation", 150_000))
        assertTrue(ContentPurityFilter.isAllowed("Blueberry Hill", "Fats Domino", "Greatest Hits", 150_000, "catalog"))
        assertFalse(ContentPurityFilter.isCompilationUpload("Autobahn", 1_350_000))
    }
    @Test fun oldiesArtistAnchorsSurviveBothSeedConversions() {
        val station = JukeboxCatalog.findStation("golden_oldies")!!
        val seed = station.toStreamingSeed().toStationSeed()
        assertTrue(seed.seedArtists.contains("Buddy Holly"))
        assertEquals(seed.seedArtists, seed.toStreamingSeedParams().toStationSeed().seedArtists)
        val queries = StreamingStationQueryPlanner.planInitialQueries(seed)
        assertTrue(queries.take(8).any { it.contains("Buddy Holly") })
    }
}
