package com.audiophile.musicplayer.data.local.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "local_songs",
    indices = [
        Index(value = ["artist", "title"]),
        Index(value = ["isrc"]),
        Index(value = ["canonicalTrackId"])
    ]
)
data class LocalSongEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val title: String,
    val artist: String,
    val album: String? = null,
    val durationMs: Long? = null,
    val artworkUrl: String? = null,
    val isrc: String? = null,
    /** VANTA canonical graph track id when known. */
    val canonicalTrackId: Long? = null,
    val explicit: Boolean? = null,
    val genres: List<String> = emptyList(),
    val quality: String? = null,
    val sourceType: SourceType = SourceType.LOCAL,
    val streamUrl: String? = null,
    val isFavorite: Boolean = false,
    val dateAdded: Long = System.currentTimeMillis(),
    val lastPlayedAt: Long? = null,
    val playCount: Int = 0,
    val importSource: String? = null,
    val externalIdsJson: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)
