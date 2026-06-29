package com.audiophile.musicplayer.data.local.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "albums",
    indices = [Index(value = ["album_name", "artist_name"], unique = true)]
)
data class Album(
    @PrimaryKey(autoGenerate = true)
    val albumId: Long = 0,
    val album_name: String,
    val artist_name: String,
    val cover_art_url: String?,
    val release_year: Int? = null
)
