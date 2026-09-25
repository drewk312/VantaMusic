package com.audiophile.musicplayer.data.rpc

import com.audiophile.musicplayer.radio.GenerateStationRequestV1
import com.audiophile.musicplayer.radio.GenerateStationResponseV1
import com.audiophile.musicplayer.radio.DjSegmentRequestV1
import com.audiophile.musicplayer.radio.DjSegmentResponseV1
import com.audiophile.musicplayer.radio.SimpleTrackRef
import com.audiophile.musicplayer.radio.RadioApiService
import okhttp3.ResponseBody.Companion.toResponseBody

class RadioRpcService(
    private val client: RadioRpcClient
) : RadioApiService {

    override suspend fun generateStation(
        request: GenerateStationRequestV1
    ): retrofit2.Response<GenerateStationResponseV1> {
        return try {
            val params = GenerateStationParams(
                seed = request.seed,
                seedKind = request.seedKind,
                seedEraStart = request.seedEraStart,
                seedEraEnd = request.seedEraEnd,
                seedArtist = request.seedArtist,
                seedTitle = request.seedTitle,
                hintKeywords = request.hintKeywords,
                recentTracks = request.recentHistory.map { TrackRef(it.title, it.artist, it.album) },
                tasteLikes = request.tasteProfile.likes,
                tasteDislikes = request.tasteProfile.dislikes,
                count = request.count
            )
            val result: GenerateStationResult = client.call(RpcMethods.GENERATE_STATION, params)
            val response = GenerateStationResponseV1(
                stationName = result.stationName,
                tracks = result.tracks.map { rec ->
                    com.audiophile.musicplayer.radio.TrackRecommendationV1(
                        title = rec.title,
                        artist = rec.artist,
                        album = rec.album,
                        reason = rec.reason
                    )
                }
            )
            retrofit2.Response.success(response)
        } catch (e: Exception) {
            retrofit2.Response.error(500, (e.message ?: "RPC error").toResponseBody())
        }
    }

    override suspend fun generateDjSegment(
        request: DjSegmentRequestV1
    ): retrofit2.Response<DjSegmentResponseV1> {
        return try {
            val params = DjSegmentParams(
                stationName = request.stationName,
                recentTracks = request.recentTracks.map { TrackRef(it.title, it.artist, it.album) },
                nextTrack = TrackRef(request.nextTrack.title, request.nextTrack.artist, request.nextTrack.album),
                tone = request.tone
            )
            val result: DjSegmentResult = client.call(RpcMethods.GENERATE_DJ_SEGMENT, params)
            val response = DjSegmentResponseV1(
                script = result.script,
                audioBase64 = result.audioBase64
            )
            retrofit2.Response.success(response)
        } catch (e: Exception) {
            retrofit2.Response.error(500, (e.message ?: "RPC error").toResponseBody())
        }
    }
}
