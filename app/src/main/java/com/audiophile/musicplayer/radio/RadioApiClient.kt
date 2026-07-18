package com.audiophile.musicplayer.radio

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.POST

data class SimpleTrackRef(
    val title: String,
    val artist: String,
    val album: String? = null
)

data class TasteProfile(
    val likes: List<String>,
    val dislikes: List<String>
)

data class GenerateStationRequestV1(
    val seed: String,
    val seedKind: String? = null,
    val seedEraStart: Int? = null,
    val seedEraEnd: Int? = null,
    val seedArtist: String? = null,
    val seedTitle: String? = null,
    val hintKeywords: List<String>? = null,
    val recentHistory: List<SimpleTrackRef>,
    val tasteProfile: TasteProfile,
    val count: Int = 15
)

data class TrackRecommendationV1(
    val title: String,
    val artist: String,
    val album: String?,
    val reason: String?
)

data class GenerateStationResponseV1(
    val stationName: String,
    val tracks: List<TrackRecommendationV1>
)

object RadioBackendRequestPlanner {
    fun buildGenerateStationRequest(
        seed: StreamingStationSeed,
        request: StreamingStationRequest,
        recentHistory: List<SimpleTrackRef>
    ): GenerateStationRequestV1 {
        val prompt = backendSeedPrompt(seed, request.taste)
        val hints = backendHintKeywords(seed, request.taste)
        return GenerateStationRequestV1(
            seed = prompt,
            seedKind = seed.kind.name,
            seedEraStart = seed.eraStart,
            seedEraEnd = seed.eraEnd,
            seedArtist = seed.seedArtist,
            seedTitle = seed.seedTitle,
            hintKeywords = hints.takeIf { it.isNotEmpty() },
            recentHistory = recentHistory,
            tasteProfile = request.taste.toBackendTasteProfile(),
            count = request.targetCount
        )
    }

    private fun backendSeedPrompt(seed: StreamingStationSeed, taste: StreamingStationTasteSignals): String {
        if (seed.kind != StreamingStationKind.FREE_TEXT) return seed.displayName

        val tasteAnchors = tasteAnchors(taste)
        if (VibeTranslator.isPersonalTastePrompt(seed.primaryQuery)) {
            return if (tasteAnchors.isNotEmpty()) {
                "personal taste mix: ${tasteAnchors.take(6).joinToString(", ")}"
            } else {
                "personal taste mix"
            }
        }

        return VibeTranslator.extractSearchQueries(seed.primaryQuery)
            .filter { it.isNotBlank() }
            .distinct()
            .take(6)
            .joinToString(", ")
            .ifBlank { StationQuerySanitizer.cleanStationMeta(seed.primaryQuery).ifBlank { "personal music mix" } }
    }

    private fun backendHintKeywords(seed: StreamingStationSeed, taste: StreamingStationTasteSignals): List<String> {
        val translated = if (seed.kind == StreamingStationKind.FREE_TEXT) {
            VibeTranslator.extractSearchQueries(seed.primaryQuery)
        } else {
            seed.hintKeywords
        }
        return (translated + tasteAnchors(taste))
            .map { StationQuerySanitizer.cleanStationMeta(it.trim()) }
            .filter { it.length >= 2 && !StationQuerySanitizer.isStationMetaPhrase(it) }
            .distinct()
            .take(12)
    }

    private fun StreamingStationTasteSignals.toBackendTasteProfile(): TasteProfile {
        val likes = tasteAnchors(this).take(20)
        val dislikes = (dislikedArtists + skippedArtists.filterValues { it >= 2 }.keys)
            .map { it.trim() }
            .filter { it.length >= 2 }
            .distinct()
            .take(12)
        return TasteProfile(likes = likes, dislikes = dislikes)
    }

    private fun tasteAnchors(taste: StreamingStationTasteSignals): List<String> {
        val artists = taste.favoriteArtists
            .map { it.trim() }
            .filter { it.length >= 2 }
        val genres = taste.genreAffinities.entries
            .sortedByDescending { it.value }
            .map { it.key.trim() }
            .filter { it.length >= 2 && !StationQuerySanitizer.isStationMetaPhrase(it) }
        return (artists + genres).distinct()
    }
}

data class DjSegmentRequestV1(
    val stationName: String,
    val recentTracks: List<SimpleTrackRef>,
    val nextTrack: SimpleTrackRef,
    val tone: String? = null
)

data class DjSegmentResponseV1(
    val script: String,
    val audioBase64: String?
)

interface RadioApiService {
    @POST("api/v1/radio/generate")
    suspend fun generateStation(@Body request: GenerateStationRequestV1): Response<GenerateStationResponseV1>

    @POST("api/v1/radio/dj")
    suspend fun generateDjSegment(@Body request: DjSegmentRequestV1): Response<DjSegmentResponseV1>
}
