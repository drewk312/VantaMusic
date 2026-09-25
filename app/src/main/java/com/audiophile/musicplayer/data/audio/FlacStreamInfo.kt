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
        if (header[0] != 'f'.code.toByte() ||
            header[1] != 'L'.code.toByte() ||
            header[2] != 'a'.code.toByte() ||
            header[3] != 'C'.code.toByte()
        ) {
            return null
        }
        val blockType = header[4].toInt() and 0x7F
        if (blockType != 0) return null
        val length = ((header[5].toInt() and 0xFF) shl 16) or
            ((header[6].toInt() and 0xFF) shl 8) or
            (header[7].toInt() and 0xFF)
        if (length < 18) return null
        // STREAMINFO starts at offset 8. Sample rate (20) + channels (3) +
        // bits-per-sample (5) begin at STREAMINFO byte 10 (file offset 18).
        val b0 = header[18].toInt() and 0xFF
        val b1 = header[19].toInt() and 0xFF
        val b2 = header[20].toInt() and 0xFF
        val b3 = header[21].toInt() and 0xFF
        val sampleRateHz = (b0 shl 12) or (b1 shl 4) or (b2 ushr 4)
        val channels = ((b2 ushr 1) and 0x7) + 1
        val bitDepth = (((b2 and 0x1) shl 4) or (b3 ushr 4)) + 1
        val totalSamples = ((b3 and 0x0F).toLong() shl 32) or
            ((header[22].toLong() and 0xFF) shl 24) or
            ((header[23].toLong() and 0xFF) shl 16) or
            ((header[24].toLong() and 0xFF) shl 8) or
            (header[25].toLong() and 0xFF)
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
