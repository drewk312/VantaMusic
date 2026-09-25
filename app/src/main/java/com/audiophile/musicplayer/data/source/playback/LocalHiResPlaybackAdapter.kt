package com.audiophile.musicplayer.data.source.playback

import com.audiophile.musicplayer.data.audio.FlacStreamInfoParser
import com.audiophile.musicplayer.data.source.ResolvedStream
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException
import java.net.URI
import java.net.URISyntaxException
import java.util.Locale

interface MediaProbe {
    fun readHeader(uri: String, maxBytes: Int = 64): ByteArray?

    fun lengthBytes(uri: String): Long? = null
}

object FileMediaProbe : MediaProbe {
    override fun readHeader(uri: String, maxBytes: Int): ByteArray? {
        val file = fileFor(uri) ?: return null
        if (!file.isFile) return null
        return try {
            file.inputStream().use { input ->
                val buffer = ByteArray(maxBytes.coerceAtLeast(1))
                val read = input.read(buffer)
                if (read <= 0) null else buffer.copyOf(read)
            }
        } catch (_: FileNotFoundException) {
            null
        } catch (_: SecurityException) {
            null
        } catch (_: IOException) {
            null
        }
    }

    override fun lengthBytes(uri: String): Long? {
        val file = fileFor(uri) ?: return null
        return try {
            file.length().takeIf { it > 0L }
        } catch (_: SecurityException) {
            null
        }
    }

    private fun fileFor(uri: String): File? {
        val trimmed = uri.trim()
        if (trimmed.isEmpty()) return null
        val lower = trimmed.lowercase(Locale.US)
        if (lower.startsWith("content:")) return null
        if (lower.startsWith("http://") || lower.startsWith("https://")) return null
        return try {
            if (lower.startsWith("file:")) {
                val path = URI(trimmed).path ?: trimmed.removePrefix("file://")
                File(path)
            } else {
                File(trimmed)
            }
        } catch (_: URISyntaxException) {
            File(trimmed.removePrefix("file://"))
        } catch (_: IllegalArgumentException) {
            File(trimmed.removePrefix("file://"))
        }
    }
}

class LocalHiResPlaybackAdapter(
    private val probe: MediaProbe = FileMediaProbe
) : PlaybackSourceAdapter {
    override val adapterId: String = "local"
    override val sourceLabel: String = "Local file"

    override suspend fun resolve(request: PlaybackSourceRequest): PlaybackSourceOutcome {
        val uri = request.localUri?.trim().orEmpty()
        if (uri.isEmpty() || !PlaybackStreamNormalizer.looksLikeLocalUri(uri)) {
            return PlaybackSourceOutcome.Failed(
                PlaybackSourceFailure.of(
                    code = PlaybackSourceErrorCode.NOT_FOUND,
                    sourceLabel = sourceLabel,
                    adapterId = adapterId
                )
            )
        }
        val header = probe.readHeader(uri)
        val flac = header?.let { FlacStreamInfoParser.parse(it) }
        val length = probe.lengthBytes(uri)
        if (header == null && length == null && !uri.startsWith("content:", ignoreCase = true)) {
            return PlaybackSourceOutcome.Failed(
                PlaybackSourceFailure.of(
                    code = PlaybackSourceErrorCode.NOT_FOUND,
                    sourceLabel = sourceLabel,
                    adapterId = adapterId,
                    detail = "The local file could not be opened."
                )
            )
        }
        val bitrate = flac?.let { info ->
            length?.let { FlacStreamInfoParser.averageBitrateKbps(it, info) }
        } ?: request.bitrateKbps.takeIf { it > 0 } ?: 0
        val container = inferLocalContainer(uri, flac != null)
        val codec = flac?.let { "flac" } ?: container
        val lossless = PlaybackStreamNormalizer.inferLossless(codec, container, mimeFor(container), container)
        val stream = PlaybackStreamNormalizer.normalize(
            ResolvedStream(
                streamUrl = uri,
                bitrateKbps = bitrate,
                mimeType = mimeFor(container),
                expiresAt = request.expiresAtMs,
                qualityLabel = null,
                format = codec,
                codec = codec,
                container = container,
                isLossless = lossless,
                isDolbyAtmos = false,
                sourceLabel = sourceLabel,
                providerId = "local",
                bitDepth = flac?.bitDepth,
                sampleRateHz = flac?.sampleRateHz,
                channelCount = flac?.channels
            ),
            sourceLabel = sourceLabel,
            providerId = "local"
        )
        return PlaybackSourceOutcome.Ready(
            stream = stream,
            qualityShortfall = PlaybackStreamNormalizer.qualityShortfall(stream, request.requestedQuality)
        )
    }

    private fun inferLocalContainer(uri: String, isFlacHeader: Boolean): String {
        if (isFlacHeader) return "flac"
        val path = uri.substringBefore('?').lowercase(Locale.US)
        return when {
            path.endsWith(".flac") -> "flac"
            path.endsWith(".wav") -> "wav"
            path.endsWith(".alac") -> "alac"
            path.endsWith(".aiff") || path.endsWith(".aif") -> "aiff"
            path.endsWith(".m4a") -> "m4a"
            path.endsWith(".mp4") -> "mp4"
            path.endsWith(".mp3") -> "mp3"
            path.endsWith(".ogg") -> "ogg"
            path.endsWith(".opus") -> "opus"
            path.endsWith(".aac") -> "aac"
            path.endsWith(".iamf") -> "iamf"
            else -> "audio"
        }
    }

    private fun mimeFor(container: String): String = when (container) {
        "flac" -> "audio/flac"
        "wav" -> "audio/wav"
        "alac", "m4a", "mp4" -> "audio/mp4"
        "iamf" -> "audio/iamf"
        "aiff" -> "audio/aiff"
        "mp3" -> "audio/mpeg"
        "ogg" -> "audio/ogg"
        "opus" -> "audio/opus"
        "aac" -> "audio/aac"
        else -> "application/octet-stream"
    }
}
