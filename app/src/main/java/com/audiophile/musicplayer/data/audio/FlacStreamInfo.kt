package com.audiophile.musicplayer.data.audio

import kotlin.math.roundToInt

/**
 * FLAC STREAMINFO (first metadata block). Used to read the real bit depth
 * and sample rate from a local file instead of guessing from bitrate.
 */
data class FlacStreamInfo(
    val sampleRateHz: Int,
    val channels: Int,
    val bitDepth: Int,
    val totalSamples: Long
)

object FlacStreamInfoParser {
    fun parse(header: ByteArray): FlacStreamInfo? {
        if (header.size < 42) return null

        var offset = 0
        if (header.size >= 10 &&
            header[0] == 'I'.code.toByte() &&
            header[1] == 'D'.code.toByte() &&
            header[2] == '3'.code.toByte()
        ) {
            val s0 = header[6].toInt() and 0x7F
            val s1 = header[7].toInt() and 0x7F
            val s2 = header[8].toInt() and 0x7F
            val s3 = header[9].toInt() and 0x7F
            val tagSize = (s0 shl 21) or (s1 shl 14) or (s2 shl 7) or s3
            val candidateOffset = 10 + tagSize
            if (candidateOffset in 10..(header.size - 42) &&
                header[candidateOffset] == 'f'.code.toByte() &&
                header[candidateOffset + 1] == 'L'.code.toByte() &&
                header[candidateOffset + 2] == 'a'.code.toByte() &&
                header[candidateOffset + 3] == 'C'.code.toByte()
            ) {
                offset = candidateOffset
            }
        }

        if (offset == 0 && (header[0] != 'f'.code.toByte() ||
            header[1] != 'L'.code.toByte() ||
            header[2] != 'a'.code.toByte() ||
            header[3] != 'C'.code.toByte())
        ) {
            for (i in 0..(header.size - 42)) {
                if (header[i] == 'f'.code.toByte() &&
                    header[i + 1] == 'L'.code.toByte() &&
                    header[i + 2] == 'a'.code.toByte() &&
                    header[i + 3] == 'C'.code.toByte()
                ) {
                    offset = i
                    break
                }
            }
            if (offset == 0 && (header[0] != 'f'.code.toByte())) return null
        }

        if (header.size < offset + 42) return null
        val blockType = header[offset + 4].toInt() and 0x7F
        if (blockType != 0) return null
        val length = ((header[offset + 5].toInt() and 0xFF) shl 16) or
            ((header[offset + 6].toInt() and 0xFF) shl 8) or
            (header[offset + 7].toInt() and 0xFF)
        if (length < 18) return null
        // STREAMINFO starts at offset + 8.
        val b0 = header[offset + 18].toInt() and 0xFF
        val b1 = header[offset + 19].toInt() and 0xFF
        val b2 = header[offset + 20].toInt() and 0xFF
        val b3 = header[offset + 21].toInt() and 0xFF
        val sampleRateHz = (b0 shl 12) or (b1 shl 4) or (b2 ushr 4)
        val channels = ((b2 ushr 1) and 0x7) + 1
        val bitDepth = (((b2 and 0x1) shl 4) or (b3 ushr 4)) + 1
        val totalSamples = ((b3 and 0x0F).toLong() shl 32) or
            ((header[offset + 22].toLong() and 0xFF) shl 24) or
            ((header[offset + 23].toLong() and 0xFF) shl 16) or
            ((header[offset + 24].toLong() and 0xFF) shl 8) or
            (header[offset + 25].toLong() and 0xFF)
        if (sampleRateHz !in 1..768_000) return null
        if (channels !in 1..8) return null
        if (bitDepth !in 4..32) return null
        if (totalSamples <= 0L) return null
        return FlacStreamInfo(
            sampleRateHz = sampleRateHz,
            channels = channels,
            bitDepth = bitDepth,
            totalSamples = totalSamples
        )
    }

    fun averageBitrateKbps(fileSizeBytes: Long, info: FlacStreamInfo): Int? {
        if (fileSizeBytes <= 0L || info.sampleRateHz <= 0 || info.totalSamples <= 0L) return null
        val durationSeconds = info.totalSamples.toDouble() / info.sampleRateHz.toDouble()
        if (!durationSeconds.isFinite() || durationSeconds <= 0.0) return null
        return ((fileSizeBytes.toDouble() * 8.0) / durationSeconds / 1_000.0)
            .roundToInt()
            .takeIf { it > 0 }
    }
}
