package com.audiophile.musicplayer.data.display

import android.util.Log
import com.audiophile.musicplayer.data.local.entities.UnifiedTrack

data class DisplayTrack(
    val title: String,
    val artist: String,
    val album: String?,
    val artworkUrl: String?,
    val trackId: Long,
    val durationMs: Long?,
    val genre: String?,
    val isLiked: Boolean = false,
    val explicit: Boolean? = null,
    val lastPlayedAt: Long?,
    val qualityInfo: VantaQualityInfo? = null
)

object TrackDisplayResolver {

    fun resolve(
        track: UnifiedTrack,
        likedTrackIds: Set<Long> = emptySet(),
        preferCleanArtist: Boolean = true,
        qualityInfo: VantaQualityInfo? = null
    ): DisplayTrack {
        // UnifiedTrack doesn't persist explicit — will be null until DB schema updated
        val display = DisplayMetadataCleaner.computeDisplayMetadata(
            rawTitle = track.title,
            rawArtist = track.artist ?: "",
            rawAlbum = track.albumName
        )
        val artworkUrl = track.coverArtUrl?.takeIf { it.startsWith("http") }

        Log.d("VANTA_DISPLAY_TRUTH",
            "trackId=${track.trackId} title='${display.title}' artist='${display.artist}' album='${track.albumName}'" +
            " artwork=${artworkUrl != null} liked=${track.trackId in likedTrackIds}" +
            " lastPlayedAt=${track.lastPlayedAt}")

        return DisplayTrack(
            title = display.title,
            artist = display.artist,
            album = display.album ?: DisplayMetadataCleaner.cleanAlbumName(track.albumName),
            artworkUrl = artworkUrl,
            trackId = track.trackId,
            durationMs = track.durationMs,
            genre = track.genre,
            isLiked = track.trackId in likedTrackIds,
            explicit = display.explicit,
            lastPlayedAt = track.lastPlayedAt,
            qualityInfo = qualityInfo
        )
    }
}
