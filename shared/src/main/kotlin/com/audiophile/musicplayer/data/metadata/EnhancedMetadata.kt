package com.audiophile.musicplayer.data.metadata

data class EnhancedMetadata(
    val title: String,
    val artist: String,
    val album: String? = null,
    val albumArtist: String? = null,
    val durationMs: Long? = null,
    val artworkUrl: String? = null,
    val isrc: String? = null,
    val genres: List<String> = emptyList(),
    val releaseYear: Int? = null,
    val trackNumber: Int? = null,
    val discNumber: Int? = null,
    val explicit: Boolean? = null,
    val lyricsAvailable: Boolean = false,
    val syncedLyricsAvailable: Boolean = false,
    val credits: Map<String, String> = emptyMap(), // Role -> Name (e.g. "Producer" -> "Max Martin")
    val editorialNotes: String? = null,
    val relatedTrackIds: List<String> = emptyList(),
    val externalIds: Map<String, String> = emptyMap(), // Provider -> ID (e.g. "AppleMusic" -> "12345")
    val sourceConfidence: Float = 1.0f,
    val matchReason: String? = null,
    val lastUpdatedAt: Long = System.currentTimeMillis()
)
