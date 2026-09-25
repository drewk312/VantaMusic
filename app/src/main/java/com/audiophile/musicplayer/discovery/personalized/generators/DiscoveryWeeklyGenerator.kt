package com.audiophile.musicplayer.discovery.personalized.generators

import com.audiophile.musicplayer.discovery.personalized.MixCandidate
import com.audiophile.musicplayer.discovery.personalized.PersonalizedMixConfig
import com.audiophile.musicplayer.discovery.personalized.PersonalizedMixDeps
import com.audiophile.musicplayer.discovery.personalized.PersonalizedMixGenerator
import com.audiophile.musicplayer.discovery.personalized.TrackNormKey
import kotlin.random.Random

/**
 * Discovery Weekly — SoulSync Archives / Discover Weekly inspired serendipity mix.
 * Streaming-first: works with an empty local library via taste signals + genre expansion.
 */
class DiscoveryWeeklyGenerator : PersonalizedMixGenerator {
    override suspend fun generate(
        deps: PersonalizedMixDeps,
        variant: String,
        config: PersonalizedMixConfig
    ): List<MixCandidate> {
        val rng = config.seed?.let { Random(it) } ?: Random.Default
        val taste = deps.tasteEngine.getTasteProfile()
        val seedArtists = buildSeedArtists(deps, taste.topArtists, taste.favoriteArtists)
        val seedGenres = taste.topGenres.ifEmpty { defaultGenreSeeds() }

        val queries = DiscoveryWeeklyQueryPlanner.planQueries(
            seedArtists = seedArtists,
            seedGenres = seedGenres,
            limit = config.limit
        )

        val relatedArtists = mutableListOf<String>()
        for (artist in seedArtists.take(4)) {
            relatedArtists += deps.artistResolver.relatedArtists(artist, limit = 6)
        }

        val allQueries = (queries + relatedArtists.flatMap { artist ->
            listOf("$artist songs", "$artist deep cuts", "$artist essentials")
        }).distinct()

        val candidates = mutableListOf<MixCandidate>()
        val seenNorm = mutableSetOf<String>()
        val familiarArtistSet = seedArtists.map { it.lowercase() }.toSet()

        for (query in allQueries) {
            if (candidates.size >= config.limit * 2) break
            val results = deps.sourceRegistry.searchAll(query)
            for (result in results.take(8)) {
                val norm = TrackNormKey.normalize(result.title, result.artist)
                if (!seenNorm.add(norm)) continue
                val artistLower = result.artist.lowercase()
                val isFamiliar = artistLower in familiarArtistSet
                val serendipity = scoreSerendipity(
                    artistLower = artistLower,
                    familiarArtistSet = familiarArtistSet,
                    seedGenres = seedGenres,
                    rng = rng,
                    isFamiliar = isFamiliar
                )
                candidates.add(
                    MixCandidate(
                        title = result.title,
                        artist = result.artist,
                        album = result.album,
                        artworkUrl = result.artworkUrl,
                        providerId = result.providerId,
                        externalTrackId = result.id,
                        isrc = result.isrc,
                        normKey = norm,
                        score = serendipity,
                        source = "discovery_weekly"
                    )
                )
            }
        }

        val familiarAnchor = candidates
            .filter { it.artist.lowercase() in familiarArtistSet }
            .sortedByDescending { it.score }
            .take((config.limit * 0.2).toInt().coerceAtLeast(3))
        val exploration = candidates
            .filter { it.artist.lowercase() !in familiarArtistSet }
            .sortedByDescending { it.score }
            .take(config.limit)

        return (familiarAnchor + exploration)
            .distinctBy { it.normKey }
            .shuffled(rng)
            .take(config.limit)
    }

    private suspend fun buildSeedArtists(
        deps: PersonalizedMixDeps,
        topArtists: List<String>,
        favoriteArtists: List<String>
    ): List<String> {
        val historyArtists = deps.listeningHistory?.recommendationArtists().orEmpty()
        val fromTaste = (favoriteArtists + historyArtists + topArtists)
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinct()
        if (fromTaste.isNotEmpty()) return fromTaste.take(10)

        val localFavorites = deps.localLibraryRepository.allSongsSnapshot()
            .filter { it.isFavorite }
            .map { it.artist }
            .distinct()
            .take(5)
        if (localFavorites.isNotEmpty()) return localFavorites

        return defaultArtistSeeds()
    }

  private fun scoreSerendipity(
        artistLower: String,
        familiarArtistSet: Set<String>,
        seedGenres: List<String>,
        rng: Random,
        isFamiliar: Boolean
    ): Double {
        var score = rng.nextDouble() * 0.2
        if (!isFamiliar && artistLower !in familiarArtistSet) score += 0.5
        if (isFamiliar) score += 0.25
        if (seedGenres.any { genre -> artistLower.contains(genre.lowercase().take(4)) }) score += 0.1
        return score
    }

    private fun defaultArtistSeeds(): List<String> = listOf(
        "Radiohead", "Daft Punk", "Fleetwood Mac", "Kendrick Lamar", "The Weeknd"
    )

    private fun defaultGenreSeeds(): List<String> = listOf("indie", "alternative", "pop", "r&b")
}
