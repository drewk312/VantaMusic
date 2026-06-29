package com.audiophile.musicplayer.data.lyrics

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "lyrics_cache")
data class LyricsCacheEntity(
    @PrimaryKey
    val lyricsKey: String, // isrc or canonicalId or normalized title/artist
    val trackTitle: String,
    val artist: String,
    val isrc: String?,
    val providerId: String,
    val lyricsJson: String,
    val isSynced: Boolean,
    val lastUpdatedAt: Long = System.currentTimeMillis()
)
