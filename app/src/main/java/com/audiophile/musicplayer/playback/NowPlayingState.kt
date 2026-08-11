package com.audiophile.musicplayer.playback

import com.audiophile.musicplayer.data.display.VantaQualityInfo
import com.audiophile.musicplayer.data.local.entities.UnifiedTrackWithSources
import com.audiophile.musicplayer.data.source.sourceValidityStatus

enum class PlaybackPhase {
    IDLE,
    BUFFERING,
    PLAYING,
    PAUSED,
    ERROR
}

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
    val isBuffering: Boolean = false,
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
    val phase: PlaybackPhase
        get() = when {
            !errorMessage.isNullOrBlank() -> PlaybackPhase.ERROR
            isBuffering -> PlaybackPhase.BUFFERING
            isPlaying -> PlaybackPhase.PLAYING
            trackId != null || !title.isNullOrBlank() || queueSize > 0 -> PlaybackPhase.PAUSED
            else -> PlaybackPhase.IDLE
        }

    /**
     * Enforces the user-visible playback contract at the shared state boundary.
     * A stream cannot truthfully be playing while it is buffering or errored.
     */
    fun normalized(): NowPlayingState {
        val cleanError = errorMessage?.trim()?.takeIf { it.isNotBlank() }
        val safeDuration = durationMs.coerceAtLeast(0L)
        val safePosition = positionMs.coerceIn(0L, safeDuration.takeIf { it > 0L } ?: Long.MAX_VALUE)
        val safeBuffered = bufferedMs
            .coerceAtLeast(safePosition)
            .coerceAtMost(safeDuration.takeIf { it > 0L } ?: Long.MAX_VALUE)
        return copy(
            isPlaying = isPlaying && !isBuffering && cleanError == null,
            isBuffering = isBuffering && cleanError == null,
            positionMs = safePosition,
            durationMs = safeDuration,
            bufferedMs = safeBuffered,
            queueSize = queueSize.coerceAtLeast(0),
            queuePosition = if (queueSize > 0) queuePosition.coerceIn(0, queueSize - 1) else 0,
            errorMessage = cleanError
        )
    }

    companion object {
        fun pendingPlayback(
            track: UnifiedTrackWithSources,
            queuePosition: Int,
            queueSize: Int,
            preferredProviderId: String? = null,
            preferredExternalTrackId: String? = null,
            userQuery: String? = null,
            isFavorite: Boolean = false,
            canonicalTrackId: String? = null
        ): NowPlayingState {
            val bestSource = identitySource(track)
                ?: track.sources
                    .filter { it.streamUrl.isNotBlank() }
                    .maxByOrNull { it.bitrate }
                ?: track.sources.maxByOrNull { it.bitrate }

            return NowPlayingState(
                trackId = track.track.trackId.toString(),
                title = track.track.title,
                artist = track.track.artist,
                album = track.track.albumName,
                isrc = track.track.isrc,
                canonicalTrackId = canonicalTrackId,
                artworkUrl = track.track.coverArtUrl,
                isFavorite = isFavorite,
                isPlaying = false,
                isBuffering = true,
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

        /**
         * Builds Now Playing state for a track change without dropping preferred
         * provider/id, ISRC, favorite, or user-query identity from the prior latch.
         */
        fun fromTrackChange(
            track: UnifiedTrackWithSources,
            qualityInfo: VantaQualityInfo?,
            queuePosition: Int,
            queueSize: Int,
            previous: NowPlayingState? = null
        ): NowPlayingState {
            val trackId = track.track.trackId.toString()
            val sameTrack = previous?.trackId == trackId
            val identity = identitySource(track)
            return NowPlayingState(
                trackId = trackId,
                title = track.track.title,
                artist = track.track.artist,
                album = track.track.albumName,
                isrc = track.track.isrc?.takeIf { it.isNotBlank() }
                    ?: previous?.takeIf { sameTrack }?.isrc,
                canonicalTrackId = previous?.takeIf { sameTrack }?.canonicalTrackId,
                artworkUrl = track.track.coverArtUrl,
                isFavorite = previous?.takeIf { sameTrack }?.isFavorite ?: false,
                isPlaying = false,
                isBuffering = true,
                positionMs = 0L,
                durationMs = track.track.durationMs ?: 0L,
                bufferedMs = 0L,
                queuePosition = queuePosition,
                queueSize = queueSize,
                explicit = track.track.explicit,
                qualityInfo = qualityInfo,
                preferredProviderId = identity?.externalProviderId
                    ?: previous?.takeIf { sameTrack }?.preferredProviderId,
                preferredExternalTrackId = identity?.externalTrackId
                    ?: previous?.takeIf { sameTrack }?.preferredExternalTrackId,
                streamUrl = identity?.streamUrl?.takeIf { it.isNotBlank() }
                    ?: previous?.takeIf { sameTrack }?.streamUrl,
                userQuery = previous?.takeIf { sameTrack }?.userQuery
            )
        }

        private fun identitySource(track: UnifiedTrackWithSources) =
            track.sources
                .filter {
                    !it.externalProviderId.isNullOrBlank() &&
                        !it.externalTrackId.isNullOrBlank()
                }
                .maxByOrNull { it.bitrate }
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
