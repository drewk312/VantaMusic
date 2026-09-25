package com.audiophile.musicplayer.data.source.playback

/** Quality is a preference: an unavailable mix must not prevent ordinary playback. */
internal suspend fun resolveWithQualityFallback(
    requested: RequestedAudioQuality,
    resolve: suspend (RequestedAudioQuality) -> PlaybackSourceOutcome
): PlaybackSourceOutcome {
    val qualities = when (requested) {
        RequestedAudioQuality.AUTO_SPATIAL, RequestedAudioQuality.ATMOS -> listOf(
            RequestedAudioQuality.ATMOS,
            RequestedAudioQuality.SONY_360,
            RequestedAudioQuality.IAMF,
            RequestedAudioQuality.HI_RES_24,
            RequestedAudioQuality.LOSSLESS_16
        )
        RequestedAudioQuality.SONY_360 -> listOf(
            requested,
            RequestedAudioQuality.ATMOS,
            RequestedAudioQuality.HI_RES_24,
            RequestedAudioQuality.LOSSLESS_16
        )
        RequestedAudioQuality.IAMF -> listOf(requested, RequestedAudioQuality.HI_RES_24, RequestedAudioQuality.LOSSLESS_16)
        RequestedAudioQuality.HI_RES_24 -> listOf(requested, RequestedAudioQuality.LOSSLESS_16)
        else -> listOf(requested)
    }
    var lastFailure: PlaybackSourceOutcome.Failed? = null
    for (quality in qualities) {
        when (val outcome = resolve(quality)) {
            is PlaybackSourceOutcome.Ready -> {
                return outcome.copy(qualityShortfall = PlaybackStreamNormalizer.qualityShortfall(outcome.stream, requested))
            }
            is PlaybackSourceOutcome.Failed -> {
                if (outcome.failure.code == PlaybackSourceErrorCode.AUTH_REQUIRED ||
                    outcome.failure.code == PlaybackSourceErrorCode.UNSUPPORTED
                ) return outcome
                lastFailure = outcome
            }
        }
    }
    return checkNotNull(lastFailure)
}
