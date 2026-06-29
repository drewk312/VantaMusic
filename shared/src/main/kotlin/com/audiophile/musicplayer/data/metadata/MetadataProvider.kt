package com.audiophile.musicplayer.data.metadata

interface MetadataProvider {
    suspend fun searchByText(title: String, artist: String, album: String? = null): EnhancedMetadata?
    suspend fun lookupByIsrc(isrc: String): EnhancedMetadata?
    suspend fun lookupAlbum(album: String, artist: String): List<EnhancedMetadata>
    suspend fun lookupArtist(artist: String): List<EnhancedMetadata>
    suspend fun getRelatedTracks(track: EnhancedMetadata): List<EnhancedMetadata>
}
