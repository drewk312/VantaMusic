package com.audiophile.musicplayer.data.local.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.audiophile.musicplayer.radio.sonic.FeatureVector
import com.audiophile.musicplayer.radio.sonic.SonicTrack

@Entity(
    tableName = "track_features",
    indices = [
        Index(value = ["energy"]),
        Index(value = ["danceability"]),
        Index(value = ["energy", "valence"])
    ]
)
data class TrackFeatureEntity(
    @PrimaryKey
    val trackId: String,
    val title: String,
    val artist: String,
    @ColumnInfo(name = "sub_genres")
    val subGenres: String = "",
    val energy: Double,
    val valence: Double,
    val danceability: Double,
    val acousticness: Double,
    val updatedAt: Long = System.currentTimeMillis()
) {
    fun toSonicTrack(): SonicTrack {
        val parsedGenres = subGenres.split(",", ";")
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .toSet()
        return SonicTrack(
            id = trackId,
            title = title,
            artist = artist,
            subGenres = parsedGenres,
            features = FeatureVector(energy, valence, danceability, acousticness)
        )
    }

    companion object {
        fun fromSonicTrack(track: SonicTrack): TrackFeatureEntity {
            return TrackFeatureEntity(
                trackId = track.id,
                title = track.title,
                artist = track.artist,
                subGenres = track.subGenres.joinToString(","),
                energy = track.features.energy,
                valence = track.features.valence,
                danceability = track.features.danceability,
                acousticness = track.features.acousticness
            )
        }
    }
}
