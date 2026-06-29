package com.audiophile.musicplayer.data.source.external

import com.google.gson.annotations.SerializedName

data class ExternalSourceManifest(
    val id: String,
    val name: String,
    val version: String?,
    val description: String?,
    val resources: List<String>?,
    val types: List<String>?,
    val supportedTypes: List<String>?,
    val icon: String?,
    val canSearch: Boolean?,
    val canStream: Boolean?,
    val canBrowse: Boolean?,
    val capabilities: List<String>?
)

data class ExternalSearchResponse(
    val tracks: List<ExternalTrack>?
)

data class ExternalTrack(
    val id: String,
    val title: String,
    val artist: String,
    val album: String?,
    val durationMs: Long?,
    @SerializedName("duration") val durationSeconds: Int? = null,
    val artworkUrl: String?,
    @SerializedName("artworkURL") val artworkURL: String?,
    val coverUrl: String?,
    val isrc: String?,
    val format: String?,
    val quality: String?,
    val streamUrl: String?,
    val playable: Boolean?
) {
    fun resolveArtwork(): String? {
        return artworkUrl ?: artworkURL ?: coverUrl
    }
    fun resolveDurationMs(): Long? {
        return durationMs ?: durationSeconds?.toLong()?.times(1000L)
    }
}

data class ExternalStreamResponse(
    val url: String?,
    val streamUrl: String?,
    val mimeType: String?,
    val quality: String?,
    val bitrateKbps: Int?,
    val expiresAt: Long?
) {
    fun resolveStreamUrl(): String? {
        return streamUrl ?: url
    }
}
