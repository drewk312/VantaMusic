package com.audiophile.musicplayer.data.local.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "import_batches")
data class ImportBatchEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val sourceName: String,
    val sourceLabel: String = sourceName,
    val importedAt: Long = System.currentTimeMillis(),
    val totalTracks: Int,
    val playableCount: Int = 0,
    val metadataOnlyCount: Int = 0,
    val matchedCount: Int,
    val needsReviewCount: Int,
    val notFoundCount: Int
)
