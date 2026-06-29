package com.audiophile.musicplayer.data.metadata

import com.audiophile.musicplayer.data.importer.PlayabilityResolver
import com.audiophile.musicplayer.data.importer.PlayabilityStatus
import com.audiophile.musicplayer.data.local.entities.LocalSongEntity

class MetadataResolver(
    private val provider: MetadataProvider,
    private val cache: MetadataCache,
    private val playabilityResolver: PlayabilityResolver = PlayabilityResolver()
) {

    suspend fun resolveRawMetadata(
        title: String,
        artist: String,
        album: String? = null,
        isrc: String? = null
    ): EnhancedMetadata? {
        val key = MetadataMatchKey.generateKey(isrc, title, artist, album)
        var metadata = cache.getCachedMetadata(key)

        if (metadata == null) {
            if (!isrc.isNullOrBlank()) {
                metadata = provider.lookupByIsrc(isrc)
            }
            if (metadata == null) {
                metadata = provider.searchByText(title, artist, album)
            }
            if (metadata != null) {
                cache.saveCachedMetadata(key, metadata)
            }
        }
        return metadata
    }

    suspend fun resolveAndEnrich(
        song: LocalSongEntity, 
        policy: MetadataMergePolicy = MetadataMergePolicy.FILL_EMPTY_ONLY
    ): LocalSongEntity {
        
        val metadata = resolveRawMetadata(song.title, song.artist, song.album, song.isrc)

        if (metadata != null) {
            return merge(song, metadata, policy)
        }

        return song
    }

    private fun merge(song: LocalSongEntity, enhanced: EnhancedMetadata, policy: MetadataMergePolicy): LocalSongEntity {
        // Build the external IDs JSON properly (simplified for milestone)
        val newExternalIdsJson = if (enhanced.externalIds.isNotEmpty()) {
            "{\"provider_ids\": true}" // Placeholder for real JSON merging
        } else song.externalIdsJson

        return song.copy(
            title = applyPolicy(song.title, enhanced.title, policy) ?: song.title,
            artist = applyPolicy(song.artist, enhanced.artist, policy) ?: song.artist,
            album = applyPolicy(song.album, enhanced.album, policy),
            durationMs = applyPolicy(song.durationMs, enhanced.durationMs, policy),
            artworkUrl = applyPolicy(song.artworkUrl, enhanced.artworkUrl, policy),
            isrc = applyPolicy(song.isrc, enhanced.isrc, policy),
            genres = if (policy == MetadataMergePolicy.OVERWRITE_LOCAL || song.genres.isEmpty()) enhanced.genres else song.genres,
            externalIdsJson = newExternalIdsJson,
            updatedAt = System.currentTimeMillis()
        )
    }

    private fun <T> applyPolicy(local: T?, enhanced: T?, policy: MetadataMergePolicy): T? {
        if (enhanced == null) return local
        if (local == null) return enhanced
        
        return when (policy) {
            MetadataMergePolicy.OVERWRITE_LOCAL -> enhanced
            MetadataMergePolicy.FILL_EMPTY_ONLY -> {
                if (local is String && local.isBlank()) enhanced else local
            }
            MetadataMergePolicy.SMART_MERGE -> enhanced // Simplified smart merge logic
        }
    }
}
