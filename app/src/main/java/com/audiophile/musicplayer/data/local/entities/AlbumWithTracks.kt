package com.audiophile.musicplayer.data.local.entities

import androidx.room.Embedded
import androidx.room.Relation

data class AlbumWithTracks(
    @Embedded val album: Album,
    
    @Relation(
        parentColumn = "albumId",
        entityColumn = "albumId"
    )
    val tracks: List<UnifiedTrack>
)
