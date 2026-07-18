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
    fun fromUserInput_artistRadioUsesCleanArtistSeed() {
        val seed = StreamingStationSeedResolver.fromUserInput("The Weeknd radio")
        assertEquals(StreamingStationKind.ARTIST, seed.kind)
        assertEquals("The Weeknd", seed.seedArtist)
        assertFalse(seed.queryPhrases.any { it.contains("radio", ignoreCase = true) })
    }

    @Test
    fun fromUserInput_vibeRadioStaysFreeTextIntent() {
        val seed = StreamingStationSeedResolver.fromUserInput("quiet car ride radio")
        assertEquals(StreamingStationKind.FREE_TEXT, seed.kind)
        assertTrue(seed.queryPhrases.any { it.contains("quiet car ride", ignoreCase = true) })
    }

    @Test
    fun fromUserInput_activityPromptDoesNotBecomeArtist() {
        val seed = StreamingStationSeedResolver.fromUserInput("workout")
        assertNotEquals(StreamingStationKind.ARTIST, seed.kind)
        assertFalse(seed.seedArtist.equals("workout", ignoreCase = true))
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

    @Test
    fun planInitialQueries_favoritesRadioDoesNotSearchLiteralFavorite() {
        val seed = StreamingStationSeedResolver.fromUserInput("favorites radio")
        val queries = StreamingStationQueryPlanner.planInitialQueries(seed)

        assertTrue(queries.isNotEmpty())
        assertFalse(queries.any { it.contains("favorite", ignoreCase = true) })
        assertFalse(queries.any { it.contains("favorites", ignoreCase = true) })
        assertFalse(queries.any { it.contains("radio", ignoreCase = true) })
    }

    @Test
    fun planInitialQueries_favoritesRadioUsesTasteAnchorsWhenAvailable() {
        val seed = StreamingStationSeedResolver.fromUserInput("favorites radio")
        val taste = StreamingStationTasteSignals(
            favoriteArtists = setOf("the weeknd", "dua lipa"),
            genreAffinities = mapOf("r&b" to 4f, "pop" to 2f)
        )
        val queries = StreamingStationQueryPlanner.planInitialQueries(seed, taste)

        assertTrue(queries.any { it.contains("the weeknd", ignoreCase = true) })
        assertTrue(queries.any { it.contains("dua lipa", ignoreCase = true) })
        assertTrue(queries.any { it.contains("r&b", ignoreCase = true) || it.contains("pop", ignoreCase = true) })
        assertFalse(queries.any { it.contains("favorite", ignoreCase = true) })
        assertFalse(queries.any { it.contains("radio", ignoreCase = true) })
    }

    @Test
    fun planInitialQueries_freeTextVibeDoesNotSearchPromptAsTrackTitle() {
        val seed = StreamingStationSeedResolver.fromUserInput("quiet car ride radio")
        val queries = StreamingStationQueryPlanner.planInitialQueries(seed)

        assertTrue(queries.any { it.contains("acoustic", ignoreCase = true) || it.contains("ambient", ignoreCase = true) })
        assertFalse(queries.any { it.contains("quiet car ride", ignoreCase = true) })
        assertFalse(queries.any { it.contains("radio", ignoreCase = true) })
    }

    @Test
    fun planExpansionQueries_favoritesRadioDoesNotSearchLiteralFavorite() {
        val seed = StreamingStationSeedResolver.fromUserInput("favorites radio")
        val queries = listOf(
            StreamingStationQueryPlanner.planExpansionQueries(seed, emptyList(), pass = 0),
            StreamingStationQueryPlanner.planExpansionQueries(seed, emptyList(), pass = 2),
            StreamingStationQueryPlanner.planExpansionQueries(seed, emptyList(), pass = 3)
        ).flatten()

        assertTrue(queries.isNotEmpty())
        assertFalse(queries.any { it.contains("favorite", ignoreCase = true) })
        assertFalse(queries.any { it.contains("favorites", ignoreCase = true) })
        assertFalse(queries.any { it.contains("radio", ignoreCase = true) })
    }

    @Test
    fun planExpansionQueries_favoritesRadioUsesTasteAnchorsWhenAvailable() {
        val seed = StreamingStationSeedResolver.fromUserInput("favorites radio")
        val taste = StreamingStationTasteSignals(
            favoriteArtists = setOf("Sade", "The Weeknd"),
            genreAffinities = mapOf("r&b" to 5f)
        )

        val queries = StreamingStationQueryPlanner.planExpansionQueries(
            seed = seed,
            discoveredArtists = emptyList(),
            pass = 2,
            taste = taste
        )

        assertTrue(queries.any { it.contains("Sade", ignoreCase = true) })
        assertTrue(queries.any { it.contains("The Weeknd", ignoreCase = true) })
        assertTrue(queries.any { it.contains("r&b", ignoreCase = true) })
        assertFalse(queries.any { it.contains("favorite", ignoreCase = true) })
        assertFalse(queries.any { it.contains("radio", ignoreCase = true) })
    }

    @Test
    fun planExpansionQueries_freeTextVibeDoesNotSearchPromptAsTrackTitle() {
        val seed = StreamingStationSeedResolver.fromUserInput("quiet car ride radio")
        val queries = listOf(
            StreamingStationQueryPlanner.planExpansionQueries(seed, emptyList(), pass = 0),
            StreamingStationQueryPlanner.planExpansionQueries(seed, emptyList(), pass = 2),
            StreamingStationQueryPlanner.planExpansionQueries(seed, emptyList(), pass = 3)
        ).flatten()

        assertTrue(queries.any { it.contains("acoustic", ignoreCase = true) || it.contains("ambient", ignoreCase = true) })
        assertFalse(queries.any { it.contains("quiet car ride", ignoreCase = true) })
        assertFalse(queries.any { it.contains("radio", ignoreCase = true) })
    }

    @Test
    fun planExpansionQueries_isDeterministicForDiscoveredArtists() {
        val seed = StreamingStationSeedResolver.fromUserInput("favorites radio")
        val artists = listOf("Sade", "The Weeknd", "Sade", "Dua Lipa")

        val first = StreamingStationQueryPlanner.planExpansionQueries(seed, artists, pass = 2)
        val second = StreamingStationQueryPlanner.planExpansionQueries(seed, artists, pass = 2)

        assertEquals(first, second)
    }

    @Test
    fun backendRequest_favoritesRadioUsesTasteIntentNotLiteralPrompt() {
        val seed = StreamingStationSeedResolver.fromUserInput("favorites radio")
        val request = StreamingStationRequest(
            seed = seed,
            taste = StreamingStationTasteSignals(
                favoriteArtists = setOf("Sade", "The Weeknd"),
                genreAffinities = mapOf("r&b" to 4f, "soul" to 2f),
                dislikedArtists = setOf("test artist"),
                skippedArtists = mapOf("skip artist" to 2)
            )
        )

        val payload = RadioBackendRequestPlanner.buildGenerateStationRequest(seed, request, emptyList())

        assertTrue(payload.seed.contains("personal taste", ignoreCase = true))
        assertFalse(payload.seed.contains("favorites radio", ignoreCase = true))
        assertFalse(payload.seed.contains("radio", ignoreCase = true))
        assertTrue(payload.tasteProfile.likes.any { it.equals("Sade", ignoreCase = true) })
        assertTrue(payload.tasteProfile.likes.any { it.equals("r&b", ignoreCase = true) })
        assertTrue(payload.tasteProfile.dislikes.any { it.equals("test artist", ignoreCase = true) })
        assertTrue(payload.tasteProfile.dislikes.any { it.equals("skip artist", ignoreCase = true) })
    }

    @Test
    fun backendRequest_freeTextVibeUsesTranslatedVibeNotLiteralPrompt() {
        val seed = StreamingStationSeedResolver.fromUserInput("quiet car ride radio")
        val request = StreamingStationRequest(seed = seed)

        val payload = RadioBackendRequestPlanner.buildGenerateStationRequest(seed, request, emptyList())

        assertTrue(payload.seed.contains("acoustic", ignoreCase = true) || payload.seed.contains("ambient", ignoreCase = true))
        assertFalse(payload.seed.contains("quiet car ride", ignoreCase = true))
        assertFalse(payload.seed.contains("radio", ignoreCase = true))
        assertTrue(payload.hintKeywords.orEmpty().any { it.contains("acoustic", ignoreCase = true) || it.contains("ambient", ignoreCase = true) })
    }
}
