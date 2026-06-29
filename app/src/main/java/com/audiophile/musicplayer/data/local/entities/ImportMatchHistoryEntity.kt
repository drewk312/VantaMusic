package com.audiophile.musicplayer.data.local.entities

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.audiophile.musicplayer.data.importer.ImportMatchStatus

@Entity(
    tableName = "import_match_history",
    foreignKeys = [
        ForeignKey(
            entity = ImportedTrackEntity::class,
            parentColumns = ["id"],
            childColumns = ["importedTrackId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("importedTrackId"), Index("selectedSongId")]
)
data class ImportMatchHistoryEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val importedTrackId: Long,
    val queryUsed: String,
    val selectedSongId: Long? = null,
    val matchStatus: ImportMatchStatus,
    val confidenceScore: Float = 0f,
    val matchedAt: Long = System.currentTimeMillis(),
    val wasUserSelected: Boolean = false
)
