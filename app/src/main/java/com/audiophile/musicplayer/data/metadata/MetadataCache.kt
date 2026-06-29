package com.audiophile.musicplayer.data.metadata

import com.audiophile.musicplayer.data.local.LibraryDao
import com.audiophile.musicplayer.data.local.entities.CachedMetadataEntity
import com.google.gson.Gson

class MetadataCache(
    private val libraryDao: LibraryDao
) {
    private val gson = Gson()

    suspend fun getCachedMetadata(matchKey: String): EnhancedMetadata? {
        val entity = libraryDao.getCachedMetadata(matchKey) ?: return null
        
        // TTL Check: Cache expires after 30 days
        val ageMs = System.currentTimeMillis() - entity.lastUpdated
        if (ageMs > 30L * 24 * 60 * 60 * 1000) {
            return null 
        }

        return try {
            gson.fromJson(entity.metadataJson, EnhancedMetadata::class.java)
        } catch (e: Exception) {
            null
        }
    }

    suspend fun saveCachedMetadata(matchKey: String, metadata: EnhancedMetadata) {
        val json = gson.toJson(metadata)
        val entity = CachedMetadataEntity(
            matchKey = matchKey,
            metadataJson = json,
            lastUpdated = System.currentTimeMillis()
        )
        libraryDao.insertCachedMetadata(entity)
    }
}
