@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package com.audiophile.musicplayer.playback

import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MimeTypes
import com.audiophile.musicplayer.data.display.AudioQualityInfo

data class DecoderAudioFormat(
    val codec: String?,
    val container: String?,
    val mimeType: String?,
    val sampleRateHz: Int?,
    val channels: Int?,
    val bitrateKbps: Int?,
    val bitDepth: Int?,
    val pcmEncoding: String?,
    val dolbyAtmos: Boolean,
    val eclipsaAudio: Boolean = false
)

object Media3AudioFormatReader {
    fun read(format: Format): DecoderAudioFormat {
        val mime = format.sampleMimeType
        val container = format.containerMimeType
        val codec = normalizeCodec(format.codecs, mime, container)
        val bitrateKbps = formatBitrateKbps(format)
        val sampleRateHz = format.sampleRate.takeIf { it > 0 }
        val channels = format.channelCount.takeIf { it > 0 }
        val pcmName = pcmEncodingName(format.pcmEncoding)
        val bitDepth = bitDepthFromPcmEncoding(format.pcmEncoding)
        val dolbyAtmos = AudioQualityInfo.hasAtmosCodecEvidence(mime, container, format.codecs, codec)
        val eclipsaAudio = AudioQualityInfo.hasEclipsaCodecEvidence(mime, container, format.codecs, codec)
        return DecoderAudioFormat(
            codec = codec,
            container = containerMimeToContainer(container),
            mimeType = mime,
            sampleRateHz = sampleRateHz,
            channels = channels,
            bitrateKbps = bitrateKbps,
            bitDepth = bitDepth,
            pcmEncoding = pcmName,
            dolbyAtmos = dolbyAtmos,
            eclipsaAudio = eclipsaAudio
        )
    }

    fun bitDepthFromPcmEncoding(encoding: Int): Int? = when (encoding) {
        C.ENCODING_PCM_8BIT -> 8
        C.ENCODING_PCM_16BIT -> 16
        C.ENCODING_PCM_24BIT -> 24
        C.ENCODING_PCM_32BIT -> 32
        C.ENCODING_PCM_FLOAT -> null
        else -> null
    }

    fun pcmEncodingName(encoding: Int): String? = when (encoding) {
        C.ENCODING_PCM_8BIT -> "PCM_8BIT"
        C.ENCODING_PCM_16BIT -> "PCM_16BIT"
        C.ENCODING_PCM_24BIT -> "PCM_24BIT"
        C.ENCODING_PCM_32BIT -> "PCM_32BIT"
        C.ENCODING_PCM_FLOAT -> "PCM_FLOAT"
        C.ENCODING_INVALID -> null
        else -> null
    }

    fun formatBitrateKbps(format: Format): Int? {
        val bps = when {
            format.bitrate != Format.NO_VALUE -> format.bitrate
            format.averageBitrate != Format.NO_VALUE -> format.averageBitrate
            format.peakBitrate != Format.NO_VALUE -> format.peakBitrate
            else -> return null
        }
        return (bps / 1000).takeIf { it in 16..10_000 }
    }

    private fun normalizeCodec(codecs: String?, mime: String?, container: String?): String? {
        val hay = listOfNotNull(codecs, mime, container).joinToString(" ").lowercase()
        return when {
            hay.isBlank() -> null
            "iamf" in hay -> "iamf"
            "mha1" in hay || "mhm1" in hay || "mpegh" in hay -> "mpeg-h"
            "ac-4" in hay || Regex("""(?<![a-z0-9])ac4(?![a-z0-9])""").containsMatchIn(hay) -> "ac4"
            "eac3" in hay || "e-ac-3" in hay || "ec-3" in hay -> "eac3"
            "flac" in hay -> "flac"
            "alac" in hay -> "alac"
            MimeTypes.AUDIO_AAC == mime || "mp4a" in hay -> "aac"
            "mpeg" in hay || "mp3" in hay -> "mp3"
            else -> codecs?.substringBefore('.')?.lowercase()
        }
    }

    private fun containerMimeToContainer(container: String?): String? {
        val value = container?.lowercase() ?: return null
        return when {
            "flac" in value -> "flac"
            "mp4" in value || "m4a" in value -> "m4a"
            "mpeg" in value -> "mp3"
            "matroska" in value || "webm" in value -> "webm"
            else -> null
        }
    }
}
