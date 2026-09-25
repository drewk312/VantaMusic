package com.audiophile.musicplayer.data.canonical

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "canonical_artists",
    indices = [
        Index(value = ["normalized_name"], unique = true),
        Index(value = ["canonicalName"])
    ]
)
data class CanonicalArtistEntity(
    @PrimaryKey(autoGenerate = true)
    val artistId: Long = 0,
    val canonicalName: String,
    @ColumnInfo(name = "normalized_name")
    val normalizedName: String,
    val sortName: String? = null,
    val artworkUrl: String? = null,
    val heroArtworkUrl: String? = null,
    val biography: String? = null,
    val genresJson: String? = null,
    val country: String? = null,
    val formedYear: Int? = null,
    val musicBrainzId: String? = null,
    val metadataQuality: Int = 0,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "canonical_artist_external_ids",
    foreignKeys = [
        ForeignKey(
            entity = CanonicalArtistEntity::class,
            parentColumns = ["artistId"],
            childColumns = ["canonicalArtistId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["providerId", "externalArtistId"], unique = true),
        Index(value = ["canonicalArtistId"])
    ]
)
data class ArtistExternalIdentityEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val canonicalArtistId: Long,
    val providerId: String,
    val externalArtistId: String,
    val externalName: String? = null,
    val confidence: Float = 1f,
    val lastVerifiedAt: Long = System.currentTimeMillis()
)

enum class CanonicalAlbumType {
    ALBUM,
    SINGLE,
    EP,
    COMPILATION,
    LIVE,
    SOUNDTRACK,
    UNKNOWN
}

@Entity(
    tableName = "canonical_albums",
    foreignKeys = [
        ForeignKey(
            entity = CanonicalArtistEntity::class,
            parentColumns = ["artistId"],
            childColumns = ["canonicalArtistId"],
            onDelete = ForeignKey.SET_NULL
        )
    ],
    indices = [
        Index(value = ["normalized_title", "canonicalArtistId"], unique = true),
        Index(value = ["canonicalArtistId"]),
        Index(value = ["upc"])
    ]
)
data class CanonicalAlbumEntity(
    @PrimaryKey(autoGenerate = true)
    val albumId: Long = 0,
    val title: String,
    @ColumnInfo(name = "normalized_title")
    val normalizedTitle: String,
    val canonicalArtistId: Long? = null,
    val albumArtistName: String? = null,
    val artworkUrl: String? = null,
    val releaseDate: String? = null,
    val releaseYear: Int? = null,
    val albumType: String = CanonicalAlbumType.UNKNOWN.name,
    val genre: String? = null,
    val label: String? = null,
    val copyright: String? = null,
    val trackCount: Int? = null,
    val discCount: Int? = null,
    val upc: String? = null,
    val metadataQuality: Int = 0,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "canonical_album_external_ids",
    foreignKeys = [
        ForeignKey(
            entity = CanonicalAlbumEntity::class,
            parentColumns = ["albumId"],
            childColumns = ["canonicalAlbumId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["providerId", "externalAlbumId"], unique = true),
        Index(value = ["canonicalAlbumId"])
    ]
)
data class AlbumExternalIdentityEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val canonicalAlbumId: Long,
    val providerId: String,
    val externalAlbumId: String,
    val externalTitle: String? = null,
    val confidence: Float = 1f,
    val lastVerifiedAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "canonical_tracks",
    foreignKeys = [
        ForeignKey(
            entity = CanonicalArtistEntity::class,
            parentColumns = ["artistId"],
            childColumns = ["canonicalArtistId"],
            onDelete = ForeignKey.SET_NULL
        ),
        ForeignKey(
            entity = CanonicalAlbumEntity::class,
            parentColumns = ["albumId"],
            childColumns = ["canonicalAlbumId"],
            onDelete = ForeignKey.SET_NULL
        )
    ],
    indices = [
        Index(value = ["isrc"], unique = true),
        Index(value = ["normalized_title", "canonicalArtistId"]),
        Index(value = ["canonicalArtistId"]),
        Index(value = ["canonicalAlbumId"]),
        Index(value = ["unifiedTrackId"])
    ]
)
data class CanonicalTrackEntity(
    @PrimaryKey(autoGenerate = true)
    val trackId: Long = 0,
    val title: String,
    @ColumnInfo(name = "normalized_title")
    val normalizedTitle: String,
    val artistDisplay: String,
    val albumDisplay: String? = null,
    val canonicalArtistId: Long? = null,
    val canonicalAlbumId: Long? = null,
    val isrc: String? = null,
    val durationMs: Long? = null,
    val discNumber: Int? = null,
    val trackNumber: Int? = null,
    val explicit: Boolean? = null,
    val releaseDate: String? = null,
    val genre: String? = null,
    val artworkUrl: String? = null,
    val unifiedTrackId: Long? = null,
    val localSongId: Long? = null,
    val metadataQuality: Int = 0,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "canonical_track_external_ids",
    foreignKeys = [
        ForeignKey(
            entity = CanonicalTrackEntity::class,
            parentColumns = ["trackId"],
            childColumns = ["canonicalTrackId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["providerId", "externalTrackId"], unique = true),
        Index(value = ["canonicalTrackId"])
    ]
)
data class TrackExternalIdentityEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val canonicalTrackId: Long,
    val providerId: String,
    val externalTrackId: String,
    val externalArtistId: String? = null,
    val externalAlbumId: String? = null,
    val isrc: String? = null,
    val confidence: Float = 1f,
    val lastVerifiedAt: Long = System.currentTimeMillis()
)
