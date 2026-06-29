package com.audiophile.musicplayer.radio

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StreamingEraFilterTest {
    @Test
    fun rejectsObviousWrongDecade() {
        val seed = StreamingStationSeed(
            id = "era_90s",
            displayName = "90s Hits",
            kind = StreamingStationKind.ERA,
            eraStart = 1990,
            eraEnd = 1999
        )
        assertFalse(
            StreamingEraFilter.passesEraGate(seed, "Cars", "Gary Numan", "1979")
        )
    }

    @Test
    fun acceptsInDecadeYear() {
        val seed = StreamingStationSeed(
            id = "era_90s",
            displayName = "90s Hits",
            kind = StreamingStationKind.ERA,
            eraStart = 1990,
            eraEnd = 1999
        )
        assertTrue(
            StreamingEraFilter.passesEraGate(seed, "Smells Like Teen Spirit", "Nirvana", "1991")
        )
    }
}
