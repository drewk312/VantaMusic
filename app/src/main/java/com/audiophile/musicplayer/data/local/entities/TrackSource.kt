package com.audiophile.musicplayer.data.local.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

// The ForeignKey ensures that if a UnifiedTrack is deleted, all its sources are wiped too (CASCADE).
@Entity(
    tableName = "track_sources",
    foreignKeys = [
        ForeignKey(
            entity = UnifiedTrack::class,
            parentColumns = ["trackId"],
            childColumns = ["parentTrackId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["parentTrackId"])]
)
data class TrackSource(
    @PrimaryKey(autoGenerate = true)
    val sourceId: Long = 0,

    val parentTrackId: Long, // Links back to UnifiedTrack

    @ColumnInfo(name = "source_type")
    val sourceType: SourceType, // Enum: TORBOX, SPOTIFY, APPLE, LOCAL

    @ColumnInfo(name = "stream_url")
    val streamUrl: String, // The direct link, file path, or TorBox API hash

    @ColumnInfo(name = "bitrate_kbps")
    val bitrate: Int, // e.g., 320 for Spotify, 1411 for TorBox FLAC

    @ColumnInfo(name = "external_provider_id")
    val externalProviderId: String? = null, // Provider ID for ADDON sources (e.g., "monochrome.tidal")

    @ColumnInfo(name = "external_track_id")
    val externalTrackId: String? = null, // Provider's track ID for ADDON sources

    @ColumnInfo(name = "expires_at_ms")
    val expiresAtMs: Long? = null // Stream URL expiry timestamp (epoch ms); null = unknown
)

enum class SourceType {
    TORBOX, SPOTIFY, APPLE_MUSIC, LOCAL, YOUTUBE_MUSIC, ADDON
}
