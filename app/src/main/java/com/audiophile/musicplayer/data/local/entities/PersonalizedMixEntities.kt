package com.audiophile.musicplayer.data.local.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "personalized_mixes",
    indices = [Index(value = ["kind", "variant"], unique = true)]
)
data class PersonalizedMixEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val kind: String,
    val variant: String,
    val name: String,
    val configJson: String,
    val trackCount: Int = 0,
    val lastGeneratedAt: Long? = null,
    val lastGenerationError: String? = null,
    val isStale: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "personalized_mix_tracks",
    primaryKeys = ["mixId", "position"],
    foreignKeys = [
        androidx.room.ForeignKey(
            entity = PersonalizedMixEntity::class,
            parentColumns = ["id"],
            childColumns = ["mixId"],
            onDelete = androidx.room.ForeignKey.CASCADE
        )
    ],
    indices = [Index("track_id")]
)
data class PersonalizedMixTrackEntity(
    val mixId: Long,
    val position: Int,
    @androidx.room.ColumnInfo(name = "track_id") val trackId: Long?,
    val title: String,
    val artist: String,
    val album: String?,
    val artworkUrl: String?,
    val providerId: String?,
    val externalTrackId: String?,
    val normKey: String
)

@Entity(
    tableName = "personalized_mix_history",
    primaryKeys = ["kind", "normKey", "servedAt"]
)
data class PersonalizedMixHistoryEntity(
    val kind: String,
    val normKey: String,
    val servedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "discovery_artist_cache")
data class DiscoveryArtistCacheEntity(
    @PrimaryKey val artistKey: String,
    val relatedArtistsJson: String,
    val genresJson: String,
    val fetchedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "discovery_track_cache")
data class DiscoveryTrackCacheEntity(
    @PrimaryKey val normKey: String,
    val title: String,
    val artist: String,
    val releaseYear: Int?,
    val providerId: String?,
    val externalId: String?,
    val unifiedTrackId: Long?,
    val fetchedAt: Long = System.currentTimeMillis()
)
