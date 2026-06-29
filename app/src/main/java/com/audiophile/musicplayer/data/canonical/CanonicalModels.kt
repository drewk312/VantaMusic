package com.audiophile.musicplayer.data.canonical

import com.audiophile.musicplayer.data.display.VantaQualityInfo
import com.audiophile.musicplayer.data.source.SearchItemStatus

data class CanonicalTrack(
    val title: String = "",
    val artist: String = "",
    val album: String? = null,
    val isrc: String? = null,
    val durationMs: Long? = null,
    val genre: String? = null,
    val trackNumber: Int? = null,
    val discNumber: Int? = null,
    val releaseYear: Int? = null,
    val artworkUrl: String? = null,
    val explicit: Boolean? = null,
    val sourcePriority: Int = 0,
    val sourceStatus: SearchItemStatus? = null,
    val sourceProviderId: String? = null,
    val externalTrackId: String? = null,
    val qualityInfo: VantaQualityInfo? = null,
    val featuredArtists: List<String> = emptyList()
) {
    val displayTitle: String get() = title.ifBlank { "Unknown Track" }
    val displayArtist: String get() {
        val primary = artist.ifBlank { "Unknown Artist" }
        return if (featuredArtists.isNotEmpty()) "$primary feat. ${featuredArtists.joinToString(", ")}" else primary
    }
}

data class CanonicalAlbum(
    val title: String = "",
    val artist: String = "",
    val id: String? = null,
    val artworkUrl: String? = null,
    val releaseYear: Int? = null,
    val genre: String? = null,
    val trackCount: Int? = null,
    val explicit: Boolean? = null,
    val sourcePriority: Int = 0
) {
    val displayTitle: String get() = title.ifBlank { "Unknown Album" }
    val displayArtist: String get() = artist.ifBlank { "Unknown Artist" }
}

data class CanonicalArtist(
    val name: String = "",
    val id: String? = null,
    val genre: String? = null,
    val artworkUrl: String? = null,
    val sourcePriority: Int = 0
) {
    val displayName: String get() = name.ifBlank { "Unknown Artist" }
}
