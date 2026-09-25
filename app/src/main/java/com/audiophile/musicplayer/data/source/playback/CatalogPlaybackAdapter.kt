package com.audiophile.musicplayer.data.source.playback

import com.audiophile.musicplayer.data.source.SourceRegistry
import kotlinx.coroutines.TimeoutCancellationException

class CatalogPlaybackAdapter(
    private val sourceRegistry: SourceRegistry
) : PlaybackSourceAdapter {
    override val adapterId: String = "catalog"
    override val sourceLabel: String = "Catalog"

    override suspend fun resolve(request: PlaybackSourceRequest): PlaybackSourceOutcome {
        val providerId = request.catalogProviderId?.trim().orEmpty()
        val trackId = request.catalogExternalId?.trim().orEmpty()
        if (providerId.isEmpty() || trackId.isEmpty()) {
            return PlaybackSourceOutcome.Failed(
                PlaybackSourceFailure.of(
                    code = PlaybackSourceErrorCode.NOT_FOUND,
                    sourceLabel = sourceLabel,
                    adapterId = adapterId
                )
            )
        }
        return try {
            sourceRegistry.resolvePlayback(
                providerId = providerId,
                trackId = trackId,
                requestedQuality = request.requestedQuality
            )
        } catch (e: TimeoutCancellationException) {
            PlaybackSourceOutcome.Failed(
                PlaybackSourceFailure.of(
                    code = PlaybackSourceErrorCode.SOURCE_OFFLINE,
                    sourceLabel = providerId,
                    adapterId = adapterId,
                    detail = "Timed out."
                )
            )
        }
    }
}
