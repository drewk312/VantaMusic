package com.audiophile.musicplayer.data.source.playback

import com.audiophile.musicplayer.data.source.ResolvedStream
import java.util.Locale

/**
 * User-owned / self-hosted / authorized direct media URLs. Does not call
 * catalog APIs and does not require a Tidal/Amazon token.
 */
class DirectMediaPlaybackAdapter : PlaybackSourceAdapter {
    override val adapterId: String = "direct"
    override val sourceLabel: String = "Direct media"

    override suspend fun resolve(request: PlaybackSourceRequest): PlaybackSourceOutcome {
        val url = request.directUrl?.trim().orEmpty()
        if (url.isEmpty()) {
            return PlaybackSourceOutcome.Failed(
                PlaybackSourceFailure.of(
                    code = PlaybackSourceErrorCode.NOT_FOUND,
                    sourceLabel = sourceLabel,
                    adapterId = adapterId
                )
            )
        }
        if (PlaybackStreamNormalizer.looksLikeLocalUri(url)) {
            return PlaybackSourceOutcome.Failed(
                PlaybackSourceFailure.of(
                    code = PlaybackSourceErrorCode.UNSUPPORTED,
                    sourceLabel = sourceLabel,
                    adapterId = adapterId,
                    detail = "Local URIs are handled by the local adapter."
                )
            )
        }
        val isHttp = url.startsWith("http://", ignoreCase = true) ||
            url.startsWith("https://", ignoreCase = true)
        if (!isHttp && !PlaybackStreamNormalizer.looksLikeDirectMediaUrl(url)) {
            return PlaybackSourceOutcome.Failed(
                PlaybackSourceFailure.of(
                    code = PlaybackSourceErrorCode.UNSUPPORTED,
                    sourceLabel = sourceLabel,
                    adapterId = adapterId
                )
            )
        }
        val container = url.substringBefore('?').substringAfterLast('.').lowercase(Locale.US)
        val lossless = PlaybackStreamNormalizer.inferLossless(container, container, null, container)
        val stream = PlaybackStreamNormalizer.normalize(
            ResolvedStream(
                streamUrl = url,
                bitrateKbps = request.bitrateKbps.takeIf { it > 0 } ?: 0,
                mimeType = mimeFor(container),
                expiresAt = request.expiresAtMs,
                format = container,
                codec = container,
                container = container,
                isLossless = lossless,
                isDolbyAtmos = false,
                sourceLabel = sourceLabel,
                providerId = "direct"
            ),
            sourceLabel = sourceLabel,
            providerId = "direct"
        )
        return PlaybackSourceOutcome.Ready(
            stream = stream,
            qualityShortfall = PlaybackStreamNormalizer.qualityShortfall(stream, request.requestedQuality)
        )
    }

    private fun mimeFor(container: String): String = when (container) {
        "flac" -> "audio/flac"
        "wav" -> "audio/wav"
        "alac", "m4a", "mp4" -> "audio/mp4"
        "iamf" -> "audio/iamf"
        "aiff", "aif" -> "audio/aiff"
        "mp3" -> "audio/mpeg"
        "ogg" -> "audio/ogg"
        "opus" -> "audio/opus"
        "aac" -> "audio/aac"
        else -> "application/octet-stream"
    }
}
