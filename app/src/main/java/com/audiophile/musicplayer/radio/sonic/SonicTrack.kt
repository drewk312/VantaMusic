package com.audiophile.musicplayer.radio.sonic

import com.audiophile.musicplayer.data.local.entities.LocalSongEntity
import com.audiophile.musicplayer.data.local.entities.UnifiedTrackWithSources

/**
 * Isolated song model containing strictly sonic DNA and relational tags,
 * devoid of album wrappers or compilation clutter.
 */
data class SonicTrack(
    val id: String,
    val title: String,
    val artist: String,
    val subGenres: Set<String> = emptySet(),
    val features: FeatureVector,
    val rawUnifiedTrack: UnifiedTrackWithSources? = null,
    val rawLocalSong: LocalSongEntity? = null
) {
    companion object {
        fun fromUnifiedTrack(
            unified: UnifiedTrackWithSources,
            features: FeatureVector,
            subGenres: Set<String> = emptySet()
        ): SonicTrack {
            val genres = if (subGenres.isNotEmpty()) {
                subGenres
            } else {
                unified.track.genre?.split(",", ";", "/")?.map { it.trim() }?.filter { it.isNotEmpty() }?.toSet() ?: emptySet()
            }
            return SonicTrack(
                id = unified.track.trackId.toString(),
                title = unified.track.title,
                artist = unified.track.artist,
                subGenres = genres,
                features = features,
                rawUnifiedTrack = unified
            )
        }

        fun fromLocalSong(
            song: LocalSongEntity,
            features: FeatureVector,
            subGenres: Set<String> = emptySet()
        ): SonicTrack {
            val genres = if (subGenres.isNotEmpty()) {
                subGenres
            } else {
                song.genres.toSet()
            }
            return SonicTrack(
                id = song.id.toString(),
                title = song.title,
                artist = song.artist,
                subGenres = genres,
                features = features,
                rawLocalSong = song
            )
        }
    }
}
