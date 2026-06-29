package com.audiophile.musicplayer.data.metadata

class CompositeMetadataProvider(
    private val providers: List<MetadataProvider>
) : MetadataProvider {
    override suspend fun searchByText(title: String, artist: String, album: String?): EnhancedMetadata? {
        for (provider in providers) {
            val metadata = provider.searchByText(title, artist, album)
            if (metadata != null) return metadata
        }
        return null
    }

    override suspend fun lookupByIsrc(isrc: String): EnhancedMetadata? {
        for (provider in providers) {
            val metadata = provider.lookupByIsrc(isrc)
            if (metadata != null) return metadata
        }
        return null
    }

    override suspend fun lookupAlbum(album: String, artist: String): List<EnhancedMetadata> {
        return providers
            .flatMap { it.lookupAlbum(album, artist) }
            .distinctBy { MetadataMatchKey.generateKey(it.isrc, it.title, it.artist, it.album) }
    }

    override suspend fun lookupArtist(artist: String): List<EnhancedMetadata> {
        return providers
            .flatMap { it.lookupArtist(artist) }
            .distinctBy { MetadataMatchKey.generateKey(it.isrc, it.title, it.artist, it.album) }
    }

    override suspend fun getRelatedTracks(track: EnhancedMetadata): List<EnhancedMetadata> {
        return providers
            .flatMap { it.getRelatedTracks(track) }
            .distinctBy { MetadataMatchKey.generateKey(it.isrc, it.title, it.artist, it.album) }
    }
}
