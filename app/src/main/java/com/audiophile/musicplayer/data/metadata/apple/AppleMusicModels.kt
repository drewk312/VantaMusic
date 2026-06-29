package com.audiophile.musicplayer.data.metadata.apple

data class AppleMusicSearchResponse(
    val results: AppleMusicSearchResults? = null
)

data class AppleMusicSearchResults(
    val songs: AppleMusicSongsResponse? = null
)

data class AppleMusicSongsResponse(
    val data: List<AppleMusicSongResource> = emptyList()
)

data class AppleMusicSongResource(
    val id: String,
    val attributes: AppleMusicSongAttributes? = null
)

data class AppleMusicSongAttributes(
    val name: String? = null,
    val artistName: String? = null,
    val albumName: String? = null,
    val albumArtistName: String? = null,
    val durationInMillis: Long? = null,
    val artwork: AppleMusicArtwork? = null,
    val isrc: String? = null,
    val genreNames: List<String> = emptyList(),
    val releaseDate: String? = null,
    val trackNumber: Int? = null,
    val discNumber: Int? = null,
    val contentRating: String? = null,
    val editorialNotes: AppleMusicEditorialNotes? = null
)

data class AppleMusicArtwork(
    val url: String? = null,
    val width: Int? = null,
    val height: Int? = null
) {
    fun sizedUrl(size: Int = 1200): String? {
        return url
            ?.replace("{w}", size.toString())
            ?.replace("{h}", size.toString())
    }
}

data class AppleMusicEditorialNotes(
    val standard: String? = null,
    val short: String? = null,
    val name: String? = null
)

data class AppleMusicPlaylistResponse(val data: List<AppleMusicPlaylistResource> = emptyList())
data class AppleMusicAlbumResponse(val data: List<AppleMusicAlbumResource> = emptyList())
data class AppleMusicPlaylistResource(
    val id: String,
    val attributes: AppleMusicPlaylistAttributes? = null,
    val relationships: AppleMusicRelationships? = null
)
data class AppleMusicAlbumResource(
    val id: String,
    val attributes: AppleMusicAlbumAttributes? = null,
    val relationships: AppleMusicRelationships? = null
)
data class AppleMusicPlaylistAttributes(
    val name: String? = null,
    val curatorName: String? = null,
    val artwork: AppleMusicArtwork? = null,
    val description: AppleMusicEditorialNotes? = null
)
data class AppleMusicAlbumAttributes(
    val name: String? = null,
    val artistName: String? = null,
    val artwork: AppleMusicArtwork? = null,
    val releaseDate: String? = null,
    val genreNames: List<String> = emptyList(),
    val contentRating: String? = null
)
data class AppleMusicRelationships(val tracks: AppleMusicTrackRelationship? = null)
data class AppleMusicTrackRelationship(val data: List<AppleMusicSongResource> = emptyList())

data class AppleMusicCollectionMetadata(
    val id: String,
    val type: String,
    val title: String,
    val artist: String?,
    val artworkUrl: String?,
    val tracks: List<com.audiophile.musicplayer.data.metadata.EnhancedMetadata>
)
