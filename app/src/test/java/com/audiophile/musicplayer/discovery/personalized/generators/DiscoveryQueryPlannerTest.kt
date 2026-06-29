package com.audiophile.musicplayer.discovery.personalized.generators

import org.junit.Assert.assertTrue
import org.junit.Test

class DiscoveryWeeklyQueryPlannerTest {
    @Test
    fun planQueries_includesArtistAndGenreQueries() {
        val queries = DiscoveryWeeklyQueryPlanner.planQueries(
            seedArtists = listOf("Radiohead"),
            seedGenres = listOf("indie"),
            limit = 45
        )
        assertTrue(queries.any { it.contains("Radiohead", ignoreCase = true) })
        assertTrue(queries.any { it.contains("indie", ignoreCase = true) })
    }

    @Test
    fun planQueries_addsAdjacentGenre() {
        val queries = DiscoveryWeeklyQueryPlanner.planQueries(
            seedArtists = emptyList(),
            seedGenres = listOf("indie"),
            limit = 45
        )
        assertTrue(queries.any { it.contains("dream pop", ignoreCase = true) })
    }

    @Test
    fun planQueries_fallbackWhenNoSeeds() {
        val queries = DiscoveryWeeklyQueryPlanner.planQueries(emptyList(), emptyList(), 45)
        assertTrue(queries.isNotEmpty())
    }
}

class ReleaseRadarQueryPlannerTest {
    @Test
    fun planQueries_yearFiltersForSeedArtists() {
        val queries = ReleaseRadarQueryPlanner.planQueries(
            seedArtists = listOf("Taylor Swift"),
            currentYear = 2026,
            recencyDays = 21
        )
        assertTrue(queries.any { it.contains("2026") })
        assertTrue(queries.any { it.contains("Taylor Swift", ignoreCase = true) })
    }
}
