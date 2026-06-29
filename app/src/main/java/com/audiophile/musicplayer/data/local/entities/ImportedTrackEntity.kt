package com.audiophile.musicplayer.data.local.entities

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.audiophile.musicplayer.data.importer.ImportMatchStatus
import com.audiophile.musicplayer.data.importer.MatchConfidence
import com.audiophile.musicplayer.data.importer.PlayabilityStatus

@Entity(
    tableName = "imported_tracks",
    foreignKeys = [
        ForeignKey(
            entity = ImportBatchEntity::class,
            parentColumns = ["id"],
            childColumns = ["batchId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("batchId"), Index("matchedSongId")]
)
data class ImportedTrackEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val batchId: Long,
    val rawText: String,
    val parsedTitle: String? = null,
    val parsedArtist: String? = null,
    val parsedAlbum: String? = null,
    val sourceUrl: String? = null,
    val matchStatus: ImportMatchStatus,
    val playabilityStatus: PlayabilityStatus = PlayabilityStatus.NOT_FOUND,
    val matchConfidence: MatchConfidence = MatchConfidence.NONE,
    val matchReason: String? = null,
    val friendlySourceLabel: String? = null,
    val matchedSongId: Long? = null,
    val confidenceScore: Float = 0f,
    val userEditedTitle: String? = null,
    val userEditedArtist: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)
