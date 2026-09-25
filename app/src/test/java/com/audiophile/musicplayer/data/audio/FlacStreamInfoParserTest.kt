package com.audiophile.musicplayer.data.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class FlacStreamInfoParserTest {
    @Test
    fun parse_reads24bit48khzStereo() {
        val header = flacHeader(
            sampleRateHz = 48_000,
            channels = 2,
            bitDepth = 24,
            totalSamples = 1_440_000
        )
        val info = FlacStreamInfoParser.parse(header)
        assertNotNull(info)
        assertEquals(48_000, info?.sampleRateHz)
        assertEquals(2, info?.channels)
        assertEquals(24, info?.bitDepth)
        assertEquals(1_440_000L, info?.totalSamples)
        assertEquals(1_239, FlacStreamInfoParser.averageBitrateKbps(4_647_396L, info!!))
    }

    @Test
    fun parse_rejectsNonFlac() {
        assertNull(FlacStreamInfoParser.parse(ByteArray(42)))
    }

    private fun flacHeader(
        sampleRateHz: Int,
        channels: Int,
        bitDepth: Int,
        totalSamples: Long
    ): ByteArray {
        val header = ByteArray(42)
        header[0] = 'f'.code.toByte()
        header[1] = 'L'.code.toByte()
        header[2] = 'a'.code.toByte()
        header[3] = 'C'.code.toByte()
        header[4] = 0
        header[5] = 0
        header[6] = 0
        header[7] = 34
        val packed = (sampleRateHz shl 4) or
            (((channels - 1) and 0x7) shl 1) or
            (((bitDepth - 1) ushr 4) and 0x1)
        header[18] = ((packed ushr 16) and 0xFF).toByte()
        header[19] = ((packed ushr 8) and 0xFF).toByte()
        header[20] = (packed and 0xFF).toByte()
        header[21] = ((((bitDepth - 1) and 0xF) shl 4) or
            ((totalSamples ushr 32).toInt() and 0xF)).toByte()
        header[22] = ((totalSamples ushr 24) and 0xFF).toByte()
        header[23] = ((totalSamples ushr 16) and 0xFF).toByte()
        header[24] = ((totalSamples ushr 8) and 0xFF).toByte()
        header[25] = (totalSamples and 0xFF).toByte()
        return header
    }
}
