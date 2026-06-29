package com.audiophile.musicplayer.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.audiophile.musicplayer.data.local.entities.AddonProvider
import com.audiophile.musicplayer.data.local.entities.ResolutionCacheEntry

@Dao
interface ResolutionDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertProvider(provider: AddonProvider)

    @Query("SELECT * FROM addon_providers WHERE isEnabled = 1 ORDER BY displayName ASC")
    suspend fun getEnabledProviders(): List<AddonProvider>

    @Query("SELECT * FROM addon_providers WHERE providerId = :providerId LIMIT 1")
    suspend fun getProvider(providerId: String): AddonProvider?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertCacheEntry(entry: ResolutionCacheEntry): Long

    @Query(
        """
        SELECT * FROM resolution_cache_entries
        WHERE queryKey = :queryKey
          AND expiresAtEpochMs > :nowEpochMs
        ORDER BY confidenceScore DESC, hitCount DESC, resolvedAtEpochMs DESC
        """
    )
    suspend fun getFreshEntriesForQueryKey(
        queryKey: String,
        nowEpochMs: Long
    ): List<ResolutionCacheEntry>

    @Query(
        """
        SELECT * FROM resolution_cache_entries
        WHERE isrc = :isrc
          AND expiresAtEpochMs > :nowEpochMs
        ORDER BY confidenceScore DESC, hitCount DESC, resolvedAtEpochMs DESC
        """
    )
    suspend fun getFreshEntriesForIsrc(
        isrc: String,
        nowEpochMs: Long
    ): List<ResolutionCacheEntry>

    @Query(
        """
        UPDATE resolution_cache_entries
        SET hitCount = hitCount + 1,
            lastHitAtEpochMs = :nowEpochMs
        WHERE cacheEntryId = :cacheEntryId
        """
    )
    suspend fun markCacheHit(cacheEntryId: Long, nowEpochMs: Long)

    @Query("DELETE FROM resolution_cache_entries WHERE expiresAtEpochMs <= :nowEpochMs")
    suspend fun purgeExpired(nowEpochMs: Long): Int
}
