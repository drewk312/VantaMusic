package com.audiophile.musicplayer.data.local.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "unified_tracks",
    indices = [Index(value = ["artist_name", "track_title"], unique = true)]
)
data class UnifiedTrack(
    @PrimaryKey(autoGenerate = true) 
    val trackId: Long = 0,
    
    @ColumnInfo(name = "track_title") 
    val title: String,
    
    @ColumnInfo(name = "artist_name") 
    val artist: String,
    
    @ColumnInfo(name = "album_name") 
    val albumName: String?,
    
    @ColumnInfo(name = "cover_art_url") 
    val coverArtUrl: String?,
    
    @ColumnInfo(name = "skip_on_shuffle")
    val skipOnShuffle: Boolean = false,
    
    @ColumnInfo(name = "albumId")
    val albumId: Long? = null, // Optional link to an Album entity if we added the whole album
    
    @ColumnInfo(name = "genre")
    val genre: String? = null, // Used for AutoPlay seeding

    @ColumnInfo(name = "local_library_id")
    val localLibraryId: Long? = null,

    @ColumnInfo(name = "isrc")
    val isrc: String? = null,

    @ColumnInfo(name = "duration_ms")
    val durationMs: Long? = null,

    @ColumnInfo(name = "last_played_at")
    val lastPlayedAt: Long? = null,

    @ColumnInfo(name = "explicit")
    val explicit: Boolean? = null
)
