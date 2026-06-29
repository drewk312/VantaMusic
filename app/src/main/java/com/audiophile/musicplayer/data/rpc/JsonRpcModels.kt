package com.audiophile.musicplayer.data.rpc

import com.google.gson.annotations.SerializedName

data class JsonRpcRequest(
    @SerializedName("jsonrpc") val jsonrpc: String = "2.0",
    val id: Int,
    val method: String,
    val params: Any? = null
)

data class JsonRpcResponse<T>(
    @SerializedName("jsonrpc") val jsonrpc: String? = null,
    val id: Int? = null,
    val result: T? = null,
    val error: JsonRpcError? = null
)

data class JsonRpcError(
    val code: Int,
    val message: String,
    val data: Any? = null
)

// ── RPC Methods ──

object RpcMethods {
    const val GENERATE_STATION = "generateStation"
    const val GENERATE_DJ_SEGMENT = "generateDjSegment"
    const val PING = "ping"
}

// ── Station Generation ──

data class GenerateStationParams(
    val seed: String,
    val seedKind: String? = null,
    val seedEraStart: Int? = null,
    val seedEraEnd: Int? = null,
    val seedArtist: String? = null,
    val seedTitle: String? = null,
    val hintKeywords: List<String>? = null,
    val recentTracks: List<TrackRef>? = null,
    val tasteLikes: List<String>? = null,
    val tasteDislikes: List<String>? = null,
    val count: Int = 15
)

data class TrackRef(
    val title: String,
    val artist: String,
    val album: String? = null
)

data class GenerateStationResult(
    val stationName: String,
    val tracks: List<TrackRecommendation>
)

data class TrackRecommendation(
    val title: String,
    val artist: String,
    val album: String? = null,
    val reason: String? = null
)

// ── DJ Segment ──

data class DjSegmentParams(
    val stationName: String,
    val recentTracks: List<TrackRef>,
    val nextTrack: TrackRef,
    val tone: String? = null
)

data class DjSegmentResult(
    val script: String,
    @SerializedName("audioBase64") val audioBase64: String? = null
)
