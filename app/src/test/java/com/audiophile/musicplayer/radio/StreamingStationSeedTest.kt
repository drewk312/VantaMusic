package com.audiophile.musicplayer.radio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import com.audiophile.musicplayer.data.dj.toStreamingSeed
import com.audiophile.musicplayer.radio.toStationSeed
import org.junit.Test

class StreamingStationSeedResolverTest {
    @Test
    fun fromUserInput_parsesSongsLikePattern() {
        val seed = StreamingStationSeedResolver.fromUserInput("songs like Africa by Toto")
        assertEquals(StreamingStationKind.SONG_SIMILAR, seed.kind)
        assertEquals("Africa", seed.seedTitle)
        assertEquals("Toto", seed.seedArtist)
    }

    @Test
    fun fromUserInput_parsesEraGenreCombo() {
        val seed = StreamingStationSeedResolver.fromUserInput("90s alternative")
        assertEquals(StreamingStationKind.GENRE, seed.kind)
        assertEquals(1990, seed.eraStart)
        assertEquals(1999, seed.eraEnd)
        assertTrue(seed.displayName.contains("90s", ignoreCase = true))
    }

    @Test
    fun fromUserInput_resolvesYachtRockPreset() {
        val seed = StreamingStationSeedResolver.fromUserInput("yacht rock")
        assertEquals("yacht_rock", seed.id)
        assertTrue(seed.displayName.contains("Yacht", ignoreCase = true))
    }

    @Test
    fun fromJukeboxStation_mapsPreset() {
        val station = com.audiophile.musicplayer.data.dj.JukeboxCatalog.findStation("yacht_rock")
        requireNotNull(station)
        val seed = StreamingStationSeedResolver.fromJukeboxStation(station)
        assertEquals("yacht_rock", seed.id)
        assertTrue(seed.seedArtists.isNotEmpty())
    }

    @Test
    fun fromJukeboxStation_countryGenre_doesNotSearchStationTitle() {
        val station = com.audiophile.musicplayer.data.dj.JukeboxCatalog.stationFromGenre("country")
        requireNotNull(station)
        val seed = StreamingStationSeedResolver.fromJukeboxStation(station)
        assertEquals("Country Radio", seed.displayName)
        assertTrue(seed.seedArtists.isNotEmpty())
        assertFalse(seed.queryPhrases.any { it.equals("country radio", ignoreCase = true) })
        assertFalse(StationQuerySanitizer.isStationMetaPhrase(seed.primaryQuery))
        val queries = StreamingStationQueryPlanner.planInitialQueries(seed)
        assertFalse(queries.any { it.equals("country radio", ignoreCase = true) })
        assertTrue(queries.any { it.contains("Johnny Cash", ignoreCase = true) || it.contains("Luke Combs", ignoreCase = true) })
    }

    @Test
    fun toStationSeed_genreCountry_usesArtistSeedsNotDisplayName() {
        val station = com.audiophile.musicplayer.data.dj.JukeboxCatalog.stationFromGenre("country")
        requireNotNull(station)
        val seed = station.toStreamingSeed().toStationSeed()
        assertEquals("Country Radio", seed.displayName)
        assertTrue(seed.seedArtists.isNotEmpty())
        assertFalse(seed.queryPhrases.any { it.equals("country radio", ignoreCase = true) })
        assertFalse(StationQuerySanitizer.isStationMetaPhrase(seed.primaryQuery))
    }
}

class StreamingStationQueryPlannerTest {
    @Test
    fun planInitialQueries_yachtRockIncludesEssentials() {
        val seed = StreamingStationSeedResolver.fromUserInput("yacht rock")
        val queries = StreamingStationQueryPlanner.planInitialQueries(seed)
        assertTrue(queries.any { it.contains("yacht rock", ignoreCase = true) })
        assertTrue(queries.any { it.contains("essential", ignoreCase = true) || it.contains("classic", ignoreCase = true) })
    }

    @Test
    fun planInitialQueries_differsFor90sAlternative() {
        val yacht = StreamingStationQueryPlanner.planInitialQueries(
            StreamingStationSeedResolver.fromUserInput("yacht rock")
        )
        val alt = StreamingStationQueryPlanner.planInitialQueries(
            StreamingStationSeedResolver.fromUserInput("90s alternative")
        )
        assertNotEquals(yacht.toSet(), alt.toSet())
        assertTrue(alt.any { it.contains("90", ignoreCase = true) || it.contains("alternative", ignoreCase = true) })
    }

    @Test
    fun planInitialQueries_songSimilarIncludesArtistSongs() {
        val seed = StreamingStationSeedResolver.fromUserInput("songs like Africa by Toto")
        val queries = StreamingStationQueryPlanner.planInitialQueries(seed)
        assertTrue(queries.any { it.contains("Toto", ignoreCase = true) })
        assertTrue(queries.any { it.contains("Africa", ignoreCase = true) })
    }

    @Test
    fun fromUserInput_countryGenreIncludesSeedArtists() {
        val seed = StreamingStationSeedResolver.fromUserInput("genre_country")
        assertTrue(seed.seedArtists.isNotEmpty())
        assertTrue(seed.seedArtists.any { it.contains("Cash", ignoreCase = true) })
    }

    @Test
    fun planInitialQueries_countryUsesArtistSeedsNotBareKeyword() {
        val seed = StreamingStationSeedResolver.fromUserInput("genre_country")
        val queries = StreamingStationQueryPlanner.planInitialQueries(seed)
        assertTrue(queries.any { it.contains("Johnny Cash", ignoreCase = true) })
        assertFalse(queries.any { it.equals("country", ignoreCase = true) })
    }
}
