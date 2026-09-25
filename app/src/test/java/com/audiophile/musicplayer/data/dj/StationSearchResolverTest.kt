package com.audiophile.musicplayer.data.dj

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StationSearchResolverTest {

    @Test
    fun genericSongFragmentDoesNotBecomeStationSearchResult() {
        assertTrue(StationSearchResolver.resolveForSearch("wonder").isEmpty())
    }

    @Test
    fun exactStationNameAndExplicitRadioFormStillResolve() {
        val exact = StationSearchResolver.resolveForSearch("One-Hit Wonders")
        assertEquals("One-Hit Wonders", exact.firstOrNull()?.name)

        val explicit = StationSearchResolver.resolveForSearch("One-Hit Wonders radio")
        assertEquals(exact.firstOrNull()?.id, explicit.firstOrNull()?.id)
    }
}
