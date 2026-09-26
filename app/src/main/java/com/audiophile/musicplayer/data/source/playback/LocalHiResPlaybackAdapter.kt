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
        val trimmed = uri.trim()
        val targetBytes = maxOf(maxBytes, 65536)
        if (trimmed.startsWith("content:", ignoreCase = true)) {
            val ctx = com.audiophile.musicplayer.debug.VantaDiagnosticLog.getAppContext()
            if (ctx != null) {
                return try {
                    ctx.contentResolver.openInputStream(android.net.Uri.parse(trimmed))?.use { input ->
                        val buffer = ByteArray(targetBytes)
                        var totalRead = 0
                        while (totalRead < buffer.size) {
                            val count = input.read(buffer, totalRead, buffer.size - totalRead)
                            if (count <= 0) break
                            totalRead += count
                        }
                        if (totalRead <= 0) null else buffer.copyOf(totalRead)
                    }
                } catch (_: Exception) {
                    null
                }
            }
            return null
        }
        val file = fileFor(trimmed)
        if (file != null && file.isFile) {
            val fromFile = try {
                file.inputStream().use { input ->
                    val buffer = ByteArray(targetBytes)
                    var totalRead = 0
                    while (totalRead < buffer.size) {
                        val count = input.read(buffer, totalRead, buffer.size - totalRead)
                        if (count <= 0) break
                        totalRead += count
                    }
                    if (totalRead <= 0) null else buffer.copyOf(totalRead)
                }
            } catch (_: Exception) {
                null
            }
            if (fromFile != null) return fromFile
        }
        val ctx = com.audiophile.musicplayer.debug.VantaDiagnosticLog.getAppContext()
        if (ctx != null) {
            try {
                val proj = arrayOf(android.provider.MediaStore.Audio.Media._ID)
                val sel = "${android.provider.MediaStore.Audio.Media.DATA} = ?"
                ctx.contentResolver.query(
                    android.provider.MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                    proj, sel, arrayOf(trimmed), null
                )?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val id = cursor.getLong(0)
                        val contentUri = android.content.ContentUris.withAppendedId(
                            android.provider.MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id
                        )
                        return readHeader(contentUri.toString(), targetBytes)
                    }
                }
            } catch (_: Exception) {}
        }
        return null
    }

    override fun lengthBytes(uri: String): Long? {
        val trimmed = uri.trim()
        if (trimmed.startsWith("content:", ignoreCase = true)) {
            val ctx = com.audiophile.musicplayer.debug.VantaDiagnosticLog.getAppContext()
            if (ctx != null) {
                return try {
                    ctx.contentResolver.openFileDescriptor(android.net.Uri.parse(trimmed), "r")?.use { pfd ->
                        pfd.statSize.takeIf { it > 0L }
                    }
                } catch (_: Exception) {
                    null
                }
            }
            return null
        }
        val file = fileFor(trimmed)
        if (file != null && file.isFile) {
            val len = try {
                file.length().takeIf { it > 0L }
            } catch (_: SecurityException) {
                null
            }
            if (len != null) return len
        }
        val ctx = com.audiophile.musicplayer.debug.VantaDiagnosticLog.getAppContext()
        if (ctx != null) {
            try {
                val proj = arrayOf(android.provider.MediaStore.Audio.Media._ID)
                val sel = "${android.provider.MediaStore.Audio.Media.DATA} = ?"
                ctx.contentResolver.query(
                    android.provider.MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                    proj, sel, arrayOf(trimmed), null
                )?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val id = cursor.getLong(0)
                        val contentUri = android.content.ContentUris.withAppendedId(
                            android.provider.MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id
                        )
                        return lengthBytes(contentUri.toString())
                    }
                }
            } catch (_: Exception) {}
        }
        return null
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
        val bitrate = flac?.let { info ->
            length?.let { FlacStreamInfoParser.averageBitrateKbps(it, info) }
        } ?: request.bitrateKbps.takeIf { it > 0 } ?: 0
        val container = inferLocalContainer(uri, flac != null, header)
        val codec = when {
            flac != null -> "flac"
            container == "flac" -> "flac"
            else -> container
        }
        val lossless = codec == "flac" || container == "flac" || PlaybackStreamNormalizer.inferLossless(codec, container, mimeFor(container), container)
        val isAtmos = container == "eac3" || container == "ac3"
        val mime = if (codec == "flac" || container == "flac") "audio/flac" else mimeFor(container)
        val stream = PlaybackStreamNormalizer.normalize(
            ResolvedStream(
                streamUrl = uri,
                bitrateKbps = bitrate,
                mimeType = mime,
                expiresAt = request.expiresAtMs,
                qualityLabel = null,
                format = codec,
                codec = codec,
                container = container,
                isLossless = lossless,
                isDolbyAtmos = isAtmos,
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

    private fun inferLocalContainer(uri: String, isFlacHeader: Boolean, header: ByteArray? = null): String {
        if (isFlacHeader) return "flac"
        if (header != null) {
            for (i in 0..(header.size - 4)) {
                if (header[i] == 'f'.code.toByte() &&
                    header[i + 1] == 'L'.code.toByte() &&
                    header[i + 2] == 'a'.code.toByte() &&
                    header[i + 3] == 'C'.code.toByte()
                ) {
                    return "flac"
                }
            }
            if (header.size >= 8 &&
                header[4] == 'f'.code.toByte() &&
                header[5] == 't'.code.toByte() &&
                header[6] == 'y'.code.toByte() &&
                header[7] == 'p'.code.toByte()
            ) {
                return "m4a"
            }
            if (header.size >= 2 && header[0] == 0x0B.toByte() && header[1] == 0x77.toByte()) {
                return "eac3"
            }
        }
        if (uri.startsWith("content:", ignoreCase = true)) {
            val ctx = com.audiophile.musicplayer.debug.VantaDiagnosticLog.getAppContext()
            if (ctx != null) {
                try {
                    val parsed = android.net.Uri.parse(uri)
                    val mime = ctx.contentResolver.getType(parsed)?.lowercase()
                    if (mime != null) {
                        when {
                            mime.contains("flac") -> return "flac"
                            mime.contains("eac3") || mime.contains("ac3") -> return "eac3"
                            mime.contains("mp4") || mime.contains("m4a") || mime.contains("aac") -> return "m4a"
                            mime.contains("mpeg") || mime.contains("mp3") -> return "mp3"
                            mime.contains("wav") -> return "wav"
                            mime.contains("ogg") || mime.contains("opus") -> return "ogg"
                        }
                    }
                    ctx.contentResolver.query(
                        parsed,
                        arrayOf(android.provider.MediaStore.MediaColumns.DISPLAY_NAME),
                        null, null, null
                    )?.use { cursor ->
                        if (cursor.moveToFirst()) {
                            val name = cursor.getString(0)?.lowercase().orEmpty()
                            when {
                                name.endsWith(".flac") -> return "flac"
                                name.endsWith(".m4a") -> return "m4a"
                                name.endsWith(".mp4") -> return "mp4"
                                name.endsWith(".eac3") || name.endsWith(".ec3") -> return "eac3"
                                name.endsWith(".ac3") -> return "ac3"
                                name.endsWith(".wav") -> return "wav"
                                name.endsWith(".mp3") -> return "mp3"
                                name.endsWith(".opus") -> return "opus"
                                name.endsWith(".ogg") -> return "ogg"
                            }
                        }
                    }
                } catch (_: Exception) {}
            }
        }
        val path = uri.substringBefore('?').lowercase(Locale.US)
        val decoded = runCatching { android.net.Uri.decode(path) }.getOrDefault(path).lowercase(Locale.US)
        return when {
            decoded.endsWith(".flac") || decoded.contains(".flac") -> "flac"
            decoded.endsWith(".wav") || decoded.contains(".wav") -> "wav"
            decoded.endsWith(".alac") || decoded.contains(".alac") -> "alac"
            decoded.endsWith(".aiff") || decoded.endsWith(".aif") -> "aiff"
            decoded.endsWith(".eac3") || decoded.endsWith(".ec3") || decoded.contains(".eac3") || decoded.contains(".ec3") -> "eac3"
            decoded.endsWith(".ac3") || decoded.contains(".ac3") -> "ac3"
            decoded.endsWith(".m4a") || decoded.contains(".m4a") -> "m4a"
            decoded.endsWith(".mp4") || decoded.contains(".mp4") -> "mp4"
            decoded.endsWith(".mp3") || decoded.contains(".mp3") -> "mp3"
            decoded.endsWith(".ogg") || decoded.contains(".ogg") -> "ogg"
            decoded.endsWith(".opus") || decoded.contains(".opus") -> "opus"
            decoded.endsWith(".aac") || decoded.contains(".aac") -> "aac"
            decoded.endsWith(".iamf") -> "iamf"
            else -> "audio"
        }
    }

    private fun mimeFor(container: String): String = when (container) {
        "flac" -> "audio/flac"
        "wav" -> "audio/wav"
        "alac", "m4a", "mp4" -> "audio/mp4"
        "eac3" -> "audio/eac3"
        "ac3" -> "audio/ac3"
        "iamf" -> "audio/iamf"
        "aiff" -> "audio/aiff"
        "mp3" -> "audio/mpeg"
        "ogg" -> "audio/ogg"
        "opus" -> "audio/opus"
        "aac" -> "audio/aac"
        else -> "application/octet-stream"
    }
}
