package com.audiophile.musicplayer.continuity

import com.google.gson.annotations.SerializedName

enum class ContinuityMode {
    @SerializedName("independent") INDEPENDENT,
    @SerializedName("cast") CAST,
    @SerializedName("follow") FOLLOW
}

enum class ContinuityRole {
    @SerializedName("phone") PHONE,
    @SerializedName("tv") TV,
    @SerializedName("other") OTHER
}

data class ContinuityDeviceDto(
    @SerializedName("deviceId") val deviceId: String = "",
    @SerializedName("role") val role: String = "other",
    @SerializedName("name") val name: String = "VANTA",
    @SerializedName("lastSeenAtMs") val lastSeenAtMs: Long = 0L
)

data class ContinuityTrackRefDto(
    @SerializedName("trackId") val trackId: String? = null,
    @SerializedName("title") val title: String? = null,
    @SerializedName("artist") val artist: String? = null,
    @SerializedName("album") val album: String? = null,
    @SerializedName("artworkUrl") val artworkUrl: String? = null,
    @SerializedName("providerId") val providerId: String? = null,
    @SerializedName("externalTrackId") val externalTrackId: String? = null,
    @SerializedName("isrc") val isrc: String? = null
)

data class ContinuitySessionDto(
    @SerializedName("mode") val mode: String = "independent",
    @SerializedName("leaderDeviceId") val leaderDeviceId: String? = null,
    @SerializedName("followerDeviceIds") val followerDeviceIds: List<String> = emptyList(),
    @SerializedName("track") val track: ContinuityTrackRefDto? = null,
    @SerializedName("positionMs") val positionMs: Long = 0L,
    @SerializedName("isPlaying") val isPlaying: Boolean = false,
    @SerializedName("updatedAtMs") val updatedAtMs: Long = 0L,
    @SerializedName("seq") val seq: Long = 0L
)

data class ContinuitySnapshotDto(
    @SerializedName("devices") val devices: List<ContinuityDeviceDto> = emptyList(),
    @SerializedName("session") val session: ContinuitySessionDto = ContinuitySessionDto()
)

data class ContinuityPostBody(
    @SerializedName("action") val action: String,
    @SerializedName("deviceId") val deviceId: String,
    @SerializedName("role") val role: String? = null,
    @SerializedName("name") val name: String? = null,
    @SerializedName("mode") val mode: String? = null,
    @SerializedName("leaderDeviceId") val leaderDeviceId: String? = null,
    @SerializedName("followerDeviceIds") val followerDeviceIds: List<String>? = null,
    @SerializedName("track") val track: ContinuityTrackRefDto? = null,
    @SerializedName("positionMs") val positionMs: Long? = null,
    @SerializedName("isPlaying") val isPlaying: Boolean? = null
)

fun ContinuitySessionDto.parsedMode(): ContinuityMode = when (mode.lowercase()) {
    "cast" -> ContinuityMode.CAST
    "follow" -> ContinuityMode.FOLLOW
    else -> ContinuityMode.INDEPENDENT
}
