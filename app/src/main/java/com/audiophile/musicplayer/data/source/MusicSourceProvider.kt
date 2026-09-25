package com.audiophile.musicplayer.data.source

import com.audiophile.musicplayer.data.source.playback.PlaybackAccountPolicy
import com.audiophile.musicplayer.data.source.playback.PlaybackSourceErrorCode
import com.audiophile.musicplayer.data.source.playback.PlaybackSourceFailure
import com.audiophile.musicplayer.data.source.playback.PlaybackSourceOutcome
import com.audiophile.musicplayer.data.source.playback.PlaybackStreamNormalizer
import com.audiophile.musicplayer.data.source.playback.RequestedAudioQuality

interface MusicSourceProvider {
    val providerId: String
    val providerName: String

    fun requiresLicensedAccount(): Boolean = PlaybackAccountPolicy.requiresLicensedAccount(providerId)

    suspend fun search(query: String): List<SourceSearchResult>
    suspend fun resolveStream(trackId: String): ResolvedStream?

    /**
     * Optional music-video / audiovisual stream (e.g. YouTube progressive MP4).
     * Default is unavailable — audio [resolveStream] remains the phone path.
     */
    suspend fun resolveVideoStream(trackId: String): ResolvedStream? = null

    /**
     * Source-agnostic acquisition. Default wraps [resolveStream] so existing
     * providers keep working while catalog adapters can return structured
     * errors instead of a bare null.
     */
    suspend fun resolvePlayback(
        trackId: String,
        requestedQuality: RequestedAudioQuality = RequestedAudioQuality.HI_RES_24
    ): PlaybackSourceOutcome {
        val stream = resolveStream(trackId)
        if (stream != null && stream.streamUrl.isNotBlank()) {
            val normalized = PlaybackStreamNormalizer.normalize(
                stream = stream,
                sourceLabel = stream.sourceLabel ?: providerName,
                providerId = stream.providerId ?: providerId
            )
            return PlaybackSourceOutcome.Ready(
                stream = normalized,
                qualityShortfall = PlaybackStreamNormalizer.qualityShortfall(normalized, requestedQuality)
            )
        }
        val code = when {
            requestedQuality == RequestedAudioQuality.ATMOS -> PlaybackSourceErrorCode.ATMOS_UNAVAILABLE
            requestedQuality == RequestedAudioQuality.SONY_360 -> PlaybackSourceErrorCode.QUALITY_UNAVAILABLE
            requiresLicensedAccount() -> PlaybackSourceErrorCode.AUTH_REQUIRED
            else -> PlaybackSourceErrorCode.SOURCE_OFFLINE
        }
        return PlaybackSourceOutcome.Failed(
            PlaybackSourceFailure.of(
                code = code,
                sourceLabel = providerName,
                adapterId = "catalog"
            )
        )
    }
}
