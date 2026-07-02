package com.audiophile.musicplayer.data.remote.eclipse

/**
 * Models for the Eclipse Music share-playlist API.
 *
 * Example URL: https://api.eclipsemusic.app/api/share/playlist/{shareToken}
 */
data class EclipsePlaylistResponse(
    val id: String? = null,
    val name: String? = null,
    val description: String? = null,
    val artworkUrl: String? = null,
    val tracks: List<EclipseTrack> = emptyList()
)

data class EclipseTrack(
    val id: String? = null,
    val title: String? = null,
    val artist: String? = null,
    val album: String? = null,
    val isrc: String? = null,
    val artworkUrl: String? = null,
    val durationMs: Long? = null
)
