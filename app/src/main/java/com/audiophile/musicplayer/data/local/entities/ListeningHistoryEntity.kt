package com.audiophile.musicplayer.data.local.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * One row per completed/partial play or skip, written by PlaybackService and by
 * importers (Spotify Extended Streaming History, Apple Music export) so taste
 * profiles and Wrapped-style stats are derived from REAL listening data.
 */
@Entity(
    tableName = "listening_history",
    indices = [
        Index(value = ["started_at"]),
        Index(value = ["artist"]),
        Index(value = ["title", "artist"]),
        Index(value = ["provider_id"]),
        Index(value = ["source_track_id"]),
        Index(value = ["platform", "started_at"])
    ]
)
data class ListeningHistoryEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    /** UTC epoch millis when playback/streaming started. */
    @ColumnInfo(name = "started_at")
    val startedAt: Long,

    @ColumnInfo(name = "title")
    val title: String,

    @ColumnInfo(name = "artist")
    val artist: String,

    @ColumnInfo(name = "album")
    val album: String? = null,

    /** Human/source label, e.g. SPOTIFY, APPLE_MUSIC, QOBUZ, TIDAL, AMAZON, LOCAL. */
    @ColumnInfo(name = "platform")
    val platform: String? = null,

    /** External provider id (e.g. "monochrome.tidal") for Addon sources; may be null. */
    @ColumnInfo(name = "provider_id")
    val providerId: String? = null,

    /** External track id / URI (e.g. spotify:track:...); may be null for local tracks. */
    @ColumnInfo(name = "source_track_id")
    val sourceTrackId: String? = null,

    @ColumnInfo(name = "ms_played")
    val msPlayed: Long,

    @ColumnInfo(name = "duration_ms")
    val durationMs: Long? = null,

    @ColumnInfo(name = "play_count")
    val playCount: Int = 1,

    @ColumnInfo(name = "skipped")
    val skipped: Boolean = false,

    /** Spotify reason_start (e.g. "clickrow", "trackdone", "appload"). */
    @ColumnInfo(name = "reason_start")
    val reasonStart: String? = null,

    /** Spotify reason_end (e.g. "trackdone", "backbtn", "fwdbtn", "remote"). */
    @ColumnInfo(name = "reason_end")
    val reasonEnd: String? = null,

    @ColumnInfo(name = "recorded_at")
    val recordedAt: Long = System.currentTimeMillis()
)