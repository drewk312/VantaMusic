package com.audiophile.musicplayer.data.local

import androidx.room.Dao
import androidx.room.Embedded
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.audiophile.musicplayer.data.local.entities.TrackFeatureEntity

data class TrackWithDistance(
    @Embedded val track: TrackFeatureEntity,
    val distanceSquared: Double
)

@Dao
interface TrackFeatureDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: TrackFeatureEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(entities: List<TrackFeatureEntity>)

    @Query("SELECT * FROM track_features WHERE trackId = :trackId LIMIT 1")
    suspend fun getByTrackId(trackId: String): TrackFeatureEntity?

    @Query("SELECT * FROM track_features WHERE trackId IN (:trackIds)")
    suspend fun getByTrackIds(trackIds: List<String>): List<TrackFeatureEntity>

    /**
     * Vector bounding-box pruning query:
     * Restricts search space using SQLite B-Tree indices on energy/valence,
     * then computes 4-D Euclidean distance squared.
     */
    @Query("""
        SELECT *, 
        ((energy - :targetEnergy) * (energy - :targetEnergy) +
         (valence - :targetValence) * (valence - :targetValence) +
         (danceability - :targetDanceability) * (danceability - :targetDanceability) +
         (acousticness - :targetAcousticness) * (acousticness - :targetAcousticness)) AS distanceSquared
        FROM track_features
        WHERE trackId != :excludeId
          AND energy BETWEEN (:targetEnergy - :window) AND (:targetEnergy + :window)
          AND valence BETWEEN (:targetValence - :window) AND (:targetValence + :window)
        ORDER BY distanceSquared ASC
        LIMIT :limit
    """)
    suspend fun getNearbyCandidateTracks(
        excludeId: String,
        targetEnergy: Double,
        targetValence: Double,
        targetDanceability: Double,
        targetAcousticness: Double,
        window: Double = 0.35,
        limit: Int = 50
    ): List<TrackWithDistance>

    @Query("SELECT * FROM track_features LIMIT :limit")
    suspend fun getAllTracks(limit: Int = 500): List<TrackFeatureEntity>

    @Query("SELECT COUNT(*) FROM track_features")
    suspend fun getCount(): Int
}
