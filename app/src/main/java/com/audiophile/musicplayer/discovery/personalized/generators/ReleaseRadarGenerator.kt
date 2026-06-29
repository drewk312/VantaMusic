package com.audiophile.musicplayer.discovery.personalized.generators

import com.audiophile.musicplayer.discovery.personalized.MixCandidate
import com.audiophile.musicplayer.discovery.personalized.PersonalizedMixConfig
import com.audiophile.musicplayer.discovery.personalized.PersonalizedMixDeps
import com.audiophile.musicplayer.discovery.personalized.PersonalizedMixGenerator
import com.audiophile.musicplayer.discovery.personalized.ReleaseDateGate
import com.audiophile.musicplayer.discovery.personalized.TrackNormKey
import java.util.Calendar

/**
 * Release Radar — SoulSync Fresh Tape / Release Radar inspired recent-release mix.
 * Year-filtered catalog search with future-release and remaster gating.
 */
class ReleaseRadarGenerator : PersonalizedMixGenerator {
    override suspend fun generate(
        deps: PersonalizedMixDeps,
        variant: String,
        config: PersonalizedMixConfig
    ): List<MixCandidate> {
        val taste = deps.tasteEngine.getTasteProfile()
        val seedArtists = buildSeedArtists(deps, taste)
        val currentYear = Calendar.getInstance().get(Calendar.YEAR)
        val recencyDays = config.recencyDays ?: 21
        val queries = ReleaseRadarQueryPlanner.planQueries(seedArtists, currentYear, recencyDays)

        val candidates = mutableListOf<MixCandidate>()
        val seenNorm = mutableSetOf<String>()
        val artistCaps = mutableMapOf<String, Int>()

        for (query in queries) {
            if (candidates.size >= config.limit * 3) break
            val results = deps.sourceRegistry.searchAll(query)
            for (result in results.take(10)) {
                val norm = TrackNormKey.normalize(result.title, result.artist)
                if (!seenNorm.add(norm)) continue

                val releaseYear = extractYearFromQuery(query) ?: currentYear
                val releaseDate = "$releaseYear-01-01"
                if (ReleaseDateGate.isFutureRelease(releaseDate)) continue

                val candidate = MixCandidate(
                    title = result.title,
                    artist = result.artist,
                    album = result.album,
                    artworkUrl = result.artworkUrl,
                    providerId = result.providerId,
                    externalTrackId = result.id,
                    isrc = result.isrc,
                    releaseYear = releaseYear,
                    releaseDate = releaseDate,
                    normKey = norm,
                    score = scoreReleaseRadar(result.artist, seedArtists, releaseYear, currentYear),
                    source = "release_radar"
                )

                if (ReleaseDateGate.isLikelyRemasterOrReupload(candidate, currentYear)) continue

                val artistKey = result.artist.lowercase()
                val count = artistCaps[artistKey] ?: 0
                if (count >= 6) continue
                artistCaps[artistKey] = count + 1
                candidates.add(candidate)
            }
        }

        return candidates
            .sortedByDescending { it.score }
            .take(config.limit)
    }

    private suspend fun buildSeedArtists(
        deps: PersonalizedMixDeps,
        taste: com.audiophile.musicplayer.data.dj.AiDjRecommendationEngine.TasteProfile
    ): List<String> {
        val combined = (taste.favoriteArtists + taste.topArtists)
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinct()
        if (combined.isNotEmpty()) return combined.take(12)

        val local = deps.localLibraryRepository.allSongsSnapshot()
            .sortedByDescending { it.lastPlayedAt ?: 0L }
            .map { it.artist }
            .filter { it.isNotBlank() }
            .distinct()
            .take(8)
        if (local.isNotEmpty()) return local

        return listOf("Taylor Swift", "Drake", "Billie Eilish", "The Weeknd", "SZA")
    }

    private fun scoreReleaseRadar(
        artist: String,
        seedArtists: List<String>,
        releaseYear: Int,
        currentYear: Int
    ): Double {
        val artistLower = artist.lowercase()
        val seedLower = seedArtists.map { it.lowercase() }.toSet()
        var score = 50.0
        val daysOld = ((currentYear - releaseYear).coerceAtLeast(0)) * 365
        score += (100.0 - daysOld * 0.02).coerceAtLeast(0.0) * 0.45
        if (artistLower in seedLower) score += 20.0
        if (releaseYear == currentYear) score += 15.0
        return score
    }

    private fun extractYearFromQuery(query: String): Int? {
        val match = Regex("(20\\d{2})").find(query) ?: return null
        return match.value.toIntOrNull()
    }
}
