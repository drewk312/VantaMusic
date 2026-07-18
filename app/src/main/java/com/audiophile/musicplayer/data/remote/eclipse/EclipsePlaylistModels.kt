package com.audiophile.musicplayer.data.remote.eclipse

import com.google.gson.annotations.SerializedName

data class EclipseApiResponse(
    val success: Boolean = false,
    val data: EclipsePlaylistResponse? = null
)

data class EclipsePlaylistResponse(
    val id: String? = null,
    val name: String? = null,
    val description: String? = null,
    @SerializedName("coverUrl") val artworkUrl: String? = null,
    val trackCount: Int? = null,
    val tracks: List<EclipseTrack> = emptyList()
)

data class EclipseTrack(
    val id: String? = null,
    val title: String? = null,
    val artist: String? = null,
    val album: String? = null,
    val isrc: String? = null,
    @SerializedName("coverUrl") val artworkUrl: String? = null,
    val durationMs: Long? = null
)
