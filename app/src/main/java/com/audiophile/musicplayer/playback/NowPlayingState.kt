package com.audiophile.musicplayer.playback

import com.audiophile.musicplayer.data.display.VantaQualityInfo
import com.audiophile.musicplayer.data.local.entities.UnifiedTrackWithSources
import com.audiophile.musicplayer.data.source.sourceValidityStatus

data class NowPlayingState(
    val trackId: String? = null,
    val title: String? = null,
    val artist: String? = null,
    val versionLabel: String? = null,
    val album: String? = null,
    val isrc: String? = null,
    val canonicalTrackId: String? = null,
    val artworkUrl: String? = null,
    val isFavorite: Boolean = false,
    val isPlaying: Boolean = false,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val bufferedMs: Long = 0L,
    val shuffleEnabled: Boolean = false,
    val repeatMode: Int = 0,
    val queueSize: Int = 0,
    val queuePosition: Int = 0,
    val playabilityStatus: Int = 0,
    val errorMessage: String? = null,
    val explicit: Boolean? = null,
    val sleepTimerRemainingMs: Long? = null,
    val qualityInfo: VantaQualityInfo? = null,
    val isLiveRadio: Boolean = false,
    val liveStationName: String? = null,
    val preferredProviderId: String? = null,
    val preferredExternalTrackId: String? = null,
    val streamUrl: String? = null,
    val userQuery: String? = null,
    val featuredArtists: List<String> = emptyList()
) {
    companion object {
        fun pendingPlayback(
            track: UnifiedTrackWithSources,
            queuePosition: Int,
            queueSize: Int,
            preferredProviderId: String? = null,
            preferredExternalTrackId: String? = null,
            userQuery: String? = null
        ): NowPlayingState {
            val bestSource = track.sources
                .filter { it.streamUrl.isNotBlank() }
                .maxByOrNull { it.bitrate }
                ?: track.sources.maxByOrNull { it.bitrate }

            return NowPlayingState(
                trackId = track.track.trackId.toString(),
                title = track.track.title,
                artist = track.track.artist,
                album = track.track.albumName,
                isrc = track.track.isrc,
                artworkUrl = track.track.coverArtUrl,
                isPlaying = true,
                positionMs = 0L,
                durationMs = track.track.durationMs ?: 0L,
                bufferedMs = 0L,
                queueSize = queueSize.coerceAtLeast(1),
                queuePosition = queuePosition.coerceAtLeast(0),
                errorMessage = null,
                explicit = track.track.explicit,
                qualityInfo = VantaQualityInfo.fromTrackSource(
                    source = bestSource,
                    status = track.sourceValidityStatus()
                ),
                isLiveRadio = false,
                preferredProviderId = preferredProviderId ?: bestSource?.externalProviderId,
                preferredExternalTrackId = preferredExternalTrackId ?: bestSource?.externalTrackId,
                streamUrl = bestSource?.streamUrl?.takeIf { it.isNotBlank() },
                userQuery = userQuery
            )
        }
    }

    fun isConfirmedPlayable(): Boolean = trackId != null && errorMessage == null

    /** Mini-player stays visible while a queue session exists, even when buffering or errored. */
    fun shouldShowMiniPlayer(queueSnapshot: QueueSnapshot? = null): Boolean {
        if (isLiveRadio) return true
        if (trackId != null) return true
        if (queueSize > 0) return true
        if (!title.isNullOrBlank() && !title.equals("Unknown Title", ignoreCase = true)) return true
        if (isPlaying || bufferedMs > 0L || positionMs > 0L) return true
        queueSnapshot?.let { snap ->
            if (snap.currentTrack != null) return true
            if (snap.queueSize > 0) return true
            if (snap.originalQueue.isNotEmpty()) return true
        }
        return false
    }
}
