package com.audiophile.musicplayer.playback

import com.audiophile.musicplayer.data.local.entities.UnifiedTrackWithSources

object PlaybackQueueBuilder {
    fun build(
        startTrack: UnifiedTrackWithSources,
        library: List<UnifiedTrackWithSources>,
    ): List<UnifiedTrackWithSources> {
        if (library.isEmpty()) return listOf(startTrack)

        val album = startTrack.track.albumName?.trim().orEmpty()
        if (album.isNotBlank()) {
            val albumTracks = library.filter { track ->
                track.track.albumName?.equals(album, ignoreCase = true) == true
            }
            if (albumTracks.size > 1) {
                return ensureStartTrackPresent(albumTracks, startTrack)
            }
        }

        val queueEligible = library.filter { track ->
            track.track.localLibraryId != null ||
                track.sources.isNotEmpty() ||
                track.track.trackId == startTrack.track.trackId
        }
        if (queueEligible.isEmpty()) return listOf(startTrack)

        return if (queueEligible.size > 1) {
            ensureStartTrackPresent(queueEligible, startTrack)
        } else {
            listOf(startTrack)
        }
    }

    private fun ensureStartTrackPresent(
        tracks: List<UnifiedTrackWithSources>,
        startTrack: UnifiedTrackWithSources,
    ): List<UnifiedTrackWithSources> {
        if (tracks.any { it.track.trackId == startTrack.track.trackId }) {
            return tracks
        }
        return listOf(startTrack) + tracks
    }
}
