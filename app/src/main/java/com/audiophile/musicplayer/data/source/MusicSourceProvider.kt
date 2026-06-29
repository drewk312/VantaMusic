package com.audiophile.musicplayer.data.source

interface MusicSourceProvider {
    val providerId: String
    val providerName: String

    suspend fun search(query: String): List<SourceSearchResult>
    suspend fun resolveStream(trackId: String): ResolvedStream?
}
