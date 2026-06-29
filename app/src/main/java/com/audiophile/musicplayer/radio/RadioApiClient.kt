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
