package com.audiophile.musicplayer.data.source.playback

import com.audiophile.musicplayer.data.display.AudioQualityInfo
import com.audiophile.musicplayer.data.source.ResolvedStream
import java.util.Locale

object PlaybackStreamNormalizer {
    fun normalize(
        stream: ResolvedStream,
        sourceLabel: String? = stream.sourceLabel,
        providerId: String? = stream.providerId
    ): ResolvedStream {
        val codec = stream.codec ?: inferCodec(stream)
        val container = stream.container ?: inferContainer(stream)
        val lossless = stream.isLossless || inferLossless(codec, container, stream.mimeType, stream.format)
        val atmos = AudioQualityInfo.hasAtmosCodecEvidence(
            stream.mimeType,
            stream.format,
            codec,
            container,
            stream.qualityLabel
        )
        val eclipsa = AudioQualityInfo.hasEclipsaCodecEvidence(
            stream.mimeType,
            stream.format,
            codec,
            container,
            stream.qualityLabel
        )
        val sony360 = stream.isSony360RealityAudio || AudioQualityInfo.hasSony360RealityAudioEvidence(
            stream.mimeType,
            stream.format,
            codec,
            container,
            stream.qualityLabel
        )
        return stream.copy(
            codec = codec,
            container = container,
            isLossless = lossless,
            isDolbyAtmos = atmos,
            isEclipsaAudio = eclipsa,
            isSony360RealityAudio = sony360,
            isSpatialAudio = atmos || eclipsa || sony360 || stream.isSpatialAudio,
            isSurround = atmos || sony360 || stream.isSurround,
            sourceLabel = sourceLabel?.takeIf { it.isNotBlank() } ?: stream.sourceLabel,
            providerId = providerId ?: stream.providerId
        )
    }

    fun qualityShortfall(
        stream: ResolvedStream,
        requested: RequestedAudioQuality
    ): PlaybackSourceErrorCode? = when (requested) {
        RequestedAudioQuality.ANY -> null
        RequestedAudioQuality.AUTO_SPATIAL -> null
        RequestedAudioQuality.IAMF ->
            if (stream.isEclipsaAudio) null else PlaybackSourceErrorCode.QUALITY_UNAVAILABLE
        RequestedAudioQuality.ATMOS ->
            if (stream.isDolbyAtmos) null else PlaybackSourceErrorCode.ATMOS_UNAVAILABLE
        RequestedAudioQuality.SONY_360 ->
            if (stream.isSony360RealityAudio || AudioQualityInfo.hasSony360RealityAudioEvidence(
                    stream.mimeType,
                    stream.format,
                    stream.codec,
                    stream.qualityLabel
                )
            ) null else PlaybackSourceErrorCode.QUALITY_UNAVAILABLE
        RequestedAudioQuality.HI_RES_24 -> {
            val hiRes = (stream.bitDepth != null && stream.bitDepth > 16) ||
                (stream.sampleRateHz != null && stream.sampleRateHz > 44_100)
            if (stream.isLossless && hiRes) null else PlaybackSourceErrorCode.QUALITY_UNAVAILABLE
        }
        RequestedAudioQuality.LOSSLESS_16 ->
            if (stream.isLossless) null else PlaybackSourceErrorCode.QUALITY_UNAVAILABLE
    }

    fun inferCodec(stream: ResolvedStream): String? {
        val hay = listOf(stream.codec, stream.format, stream.mimeType, stream.qualityLabel, stream.streamUrl)
            .joinToString(" ")
            .lowercase(Locale.US)
        return when {
            AudioQualityInfo.hasAtmosCodecEvidence(stream.mimeType, stream.format, stream.qualityLabel) ->
                atmosCodecLabel(hay)
            "flac" in hay -> "flac"
            "alac" in hay -> "alac"
            "aiff" in hay || "aif" in hay -> "aiff"
            "wav" in hay -> "wav"
            "eac3" in hay || "e-ac-3" in hay || "ec-3" in hay -> "eac3"
            "ac-4" in hay || Regex("""(?<![a-z0-9])ac4(?![a-z0-9])""").containsMatchIn(hay) -> "ac-4"
            "mha1" in hay || "mhm1" in hay || "mpeg-h" in hay || "mpegh" in hay -> "mpeg-h"
            "opus" in hay -> "opus"
            "vorbis" in hay || hay.contains("audio/ogg") -> "vorbis"
            "aac" in hay -> "aac"
            "mp3" in hay || Regex("""(?<![a-z0-9])mpeg(?![a-z0-9-])""").containsMatchIn(hay) -> "mp3"
            "m4a" in hay || "mp4" in hay -> "aac"
            else -> stream.format?.trim()?.takeIf { it.isNotEmpty() }
        }
    }

    fun inferContainer(stream: ResolvedStream): String? {
        val url = stream.streamUrl.substringBefore('?').lowercase(Locale.US)
        val hay = listOf(stream.container, stream.mimeType, stream.format, url).joinToString(" ").lowercase(Locale.US)
        return when {
            "flac" in hay -> "flac"
            "alac" in hay -> "alac"
            "aiff" in hay || ".aif" in hay -> "aiff"
            "wav" in hay -> "wav"
            "ogg" in hay -> "ogg"
            "opus" in hay -> "opus"
            "eac3" in hay || "e-ac-3" in hay || "ec-3" in hay || "ec3" in hay -> "eac3"
            "ac3" in hay -> "ac3"
            "mp3" in hay -> "mp3"
            "m4a" in hay -> "m4a"
            "mp4" in hay -> "mp4"
            else -> stream.container
        }
    }

    fun inferLossless(codec: String?, container: String?, mimeType: String?, format: String?): Boolean {
        val hay = listOf(codec, container, mimeType, format).joinToString(" ").lowercase(Locale.US)
        return "flac" in hay || "alac" in hay || "wav" in hay || "aiff" in hay || "aif" in hay || "lossless" in hay
    }

    fun looksLikeLocalUri(uri: String): Boolean {
        val value = uri.trim()
        if (value.isEmpty()) return false
        val lower = value.lowercase(Locale.US)
        return lower.startsWith("file:") ||
            lower.startsWith("content:") ||
            lower.startsWith("/") ||
            lower.startsWith("file:///") ||
            (lower.length >= 3 && lower[1] == ':' && (lower[2] == '\\' || lower[2] == '/'))
    }

    fun looksLikeDirectMediaUrl(url: String): Boolean {
        val path = url.substringBefore('?').lowercase(Locale.US)
        if (looksLikeLocalUri(path)) return true
        return path.endsWith(".flac") ||
            path.endsWith(".wav") ||
            path.endsWith(".alac") ||
            path.endsWith(".aiff") ||
            path.endsWith(".aif") ||
            path.endsWith(".eac3") ||
            path.endsWith(".ec3") ||
            path.endsWith(".ac3") ||
            path.endsWith(".m4a") ||
            path.endsWith(".mp4") ||
            path.endsWith(".mp3") ||
            path.endsWith(".ogg") ||
            path.endsWith(".opus") ||
            path.endsWith(".aac") ||
            path.endsWith(".iamf")
    }

    private fun atmosCodecLabel(hay: String): String = when {
        "ac-4" in hay || Regex("""(?<![a-z0-9])ac4(?![a-z0-9])""").containsMatchIn(hay) -> "ac-4"
        else -> "eac3"
    }
}
