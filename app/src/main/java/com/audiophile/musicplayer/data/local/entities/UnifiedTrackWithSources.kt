package com.audiophile.musicplayer.data.local.entities

import androidx.room.Embedded
import androidx.room.Relation

data class UnifiedTrackWithSources(
    @Embedded 
    val track: UnifiedTrack,
    
    @Relation(
        parentColumn = "trackId",
        entityColumn = "parentTrackId"
    )
    val sources: List<TrackSource>
)
