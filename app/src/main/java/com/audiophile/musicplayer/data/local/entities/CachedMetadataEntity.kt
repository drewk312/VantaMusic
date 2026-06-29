package com.audiophile.musicplayer.data.local.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "metadata_cache")
data class CachedMetadataEntity(
    @PrimaryKey val matchKey: String,
    val metadataJson: String,
    val lastUpdated: Long = System.currentTimeMillis()
)
