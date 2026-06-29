package com.audiophile.musicplayer.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.audiophile.musicplayer.data.local.entities.DiscoveryArtistCacheEntity
import com.audiophile.musicplayer.data.local.entities.DiscoveryTrackCacheEntity
import com.audiophile.musicplayer.data.local.entities.PersonalizedMixEntity
import com.audiophile.musicplayer.data.local.entities.PersonalizedMixHistoryEntity
import com.audiophile.musicplayer.data.local.entities.PersonalizedMixTrackEntity

@Dao
interface PersonalizedMixDao {
    @Query("SELECT * FROM personalized_mixes WHERE kind = :kind AND variant = :variant LIMIT 1")
    suspend fun getMix(kind: String, variant: String): PersonalizedMixEntity?

    @Query("SELECT * FROM personalized_mixes ORDER BY COALESCE(lastGeneratedAt, createdAt) DESC")
    suspend fun listMixes(): List<PersonalizedMixEntity>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertMix(entity: PersonalizedMixEntity): Long

    @Update
    suspend fun updateMix(entity: PersonalizedMixEntity)

    @Query("UPDATE personalized_mixes SET isStale = 1, updatedAt = :updatedAt WHERE kind IN (:kinds)")
    suspend fun markKindsStale(kinds: List<String>, updatedAt: Long = System.currentTimeMillis())

    @Query(
        "UPDATE personalized_mixes SET lastGenerationError = :error, updatedAt = :updatedAt " +
            "WHERE id = :mixId"
    )
    suspend fun stampGenerationError(mixId: Long, error: String, updatedAt: Long = System.currentTimeMillis())

    @Query(
        "UPDATE personalized_mixes SET trackCount = :trackCount, lastGeneratedAt = :generatedAt, " +
            "lastGenerationError = NULL, isStale = 0, updatedAt = :updatedAt WHERE id = :mixId"
    )
    suspend fun stampGenerationSuccess(
        mixId: Long,
        trackCount: Int,
        generatedAt: Long,
        updatedAt: Long = System.currentTimeMillis()
    )

    @Query("DELETE FROM personalized_mix_tracks WHERE mixId = :mixId")
    suspend fun deleteTracksForMix(mixId: Long)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTracks(tracks: List<PersonalizedMixTrackEntity>)

    @Query("SELECT * FROM personalized_mix_tracks WHERE mixId = :mixId ORDER BY position ASC")
    suspend fun getTracksForMix(mixId: Long): List<PersonalizedMixTrackEntity>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertHistoryRows(rows: List<PersonalizedMixHistoryEntity>)

    @Query(
        "SELECT normKey FROM personalized_mix_history WHERE kind = :kind " +
            "AND servedAt >= :sinceMs"
    )
    suspend fun recentNormKeys(kind: String, sinceMs: Long): List<String>

    @Query("SELECT * FROM discovery_artist_cache WHERE artistKey = :artistKey LIMIT 1")
    suspend fun getArtistCache(artistKey: String): DiscoveryArtistCacheEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertArtistCache(entity: DiscoveryArtistCacheEntity)

    @Query("SELECT * FROM discovery_track_cache WHERE normKey = :normKey LIMIT 1")
    suspend fun getTrackCache(normKey: String): DiscoveryTrackCacheEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertTrackCache(entity: DiscoveryTrackCacheEntity)

    @Transaction
    suspend fun replaceSnapshot(
        mixId: Long,
        tracks: List<PersonalizedMixTrackEntity>,
        trackCount: Int,
        generatedAt: Long,
        historyRows: List<PersonalizedMixHistoryEntity>
    ) {
        deleteTracksForMix(mixId)
        if (tracks.isNotEmpty()) insertTracks(tracks)
        stampGenerationSuccess(mixId, trackCount, generatedAt)
        if (historyRows.isNotEmpty()) insertHistoryRows(historyRows)
    }
}
