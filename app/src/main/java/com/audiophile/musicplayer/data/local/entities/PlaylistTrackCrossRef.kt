package com.audiophile.musicplayer.data.local.entities

import androidx.room.Entity
import androidx.room.Index

// A junction table to allow many-to-many relationship between Playlists and Tracks,
// with an explicit `position` column to allow arbitrary user reordering.
@Entity(
    tableName = "playlist_track_cross_ref",
    primaryKeys = ["playlistId", "trackId"],
    indices = [Index(value = ["trackId"])]
)
data class PlaylistTrackCrossRef(
    val playlistId: Long,
    val trackId: Long,
    val position: Int
)
