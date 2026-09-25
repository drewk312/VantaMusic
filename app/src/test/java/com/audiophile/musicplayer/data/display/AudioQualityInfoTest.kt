package com.audiophile.musicplayer.data.display

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AudioQualityInfoTest {
    @Test
    fun twentyFourBitFortyEightKhzFlacIsMaxNotAtmos() {
        val info = AudioQualityInfo(
            codec = "flac",
            container = "flac",
            mimeType = "audio/flac",
            bitDepth = 24,
            sampleRateHz = 48_000,
            bitrateKbps = 3284,
            channels = 2,
            lossless = true,
            hiRes = true,
            dolbyAtmos = false,
            measured = true
        )
        assertEquals(QualityTier.MAX, info.tier)
        assertEquals("MAX", info.badgeLabel())
        assertEquals("24-bit \u00B7 48.0 kHz \u00B7 3284 kbps \u00B7 FLAC", info.playbackLabel())
        assertFalse(info.dolbyAtmos)
    }

    @Test
    fun twentyFourBitFortyEightKhzFlacIsMaxEvenWhenBitrateIsBelowPcmFloor() {
        val info = AudioQualityInfo(
            codec = "flac",
            container = "flac",
            mimeType = "audio/flac",
            bitDepth = 24,
            sampleRateHz = 48_000,
            bitrateKbps = 800,
            channels = 2,
            lossless = true,
            hiRes = true,
            dolbyAtmos = false,
            measured = true
        )
        assertEquals(QualityTier.MAX, info.tier)
        assertEquals("MAX", info.badgeLabel())
        assertEquals("24-bit \u00B7 48.0 kHz \u00B7 800 kbps \u00B7 FLAC", info.playbackLabel())
    }

    @Test
    fun sixteenBitCdFlacIsLosslessNotMax() {
        val info = AudioQualityInfo(
            codec = "flac",
            container = "flac",
            mimeType = "audio/flac",
            bitDepth = 16,
            sampleRateHz = 44_100,
            bitrateKbps = 900,
            channels = 2,
            lossless = true,
            hiRes = false,
            dolbyAtmos = false,
            measured = true
        )
        assertEquals(QualityTier.LOSSLESS, info.tier)
        assertEquals("LOSSLESS", info.badgeLabel())
        assertFalse(info.dolbyAtmos)
    }

    @Test
    fun ordinaryAacIsNeverAtmos() {
        val info = AudioQualityInfo(
            codec = "aac",
            container = "m4a",
            mimeType = "audio/mp4",
            bitrateKbps = 256,
            channels = 2,
            lossless = false,
            dolbyAtmos = AudioQualityInfo.hasAtmosCodecEvidence("audio/mp4", "aac", "m4a")
        )
        assertFalse(info.dolbyAtmos)
        assertEquals(QualityTier.HIGH, info.tier)
    }

    @Test
    fun eac3JocIsAtmos() {
        assertTrue(AudioQualityInfo.hasAtmosCodecEvidence("audio/eac3-joc", "EAC3_JOC"))
        val info = AudioQualityInfo(
            codec = "eac3",
            mimeType = "audio/eac3-joc",
            bitrateKbps = 768,
            dolbyAtmos = true
        )
        assertEquals(QualityTier.ATMOS, info.tier)
        assertTrue(info.playbackLabel().orEmpty().contains("Dolby Atmos"))
    }

    @Test
    fun plainEac3IsDolbyDigitalPlusNotAtmos() {
        assertFalse(AudioQualityInfo.hasAtmosCodecEvidence("audio/eac3", "ec-3"))
        assertFalse(AudioQualityInfo.hasAtmosCodecEvidence("audio/eac3", "Dolby Digital Plus"))
    }

    @Test
    fun ac4NeedsAnExplicitImmersiveProfile() {
        assertFalse(AudioQualityInfo.hasAtmosCodecEvidence("audio/ac4", "ac-4"))
        assertTrue(AudioQualityInfo.hasAtmosCodecEvidence("audio/ac4", "AC-4 Dolby Atmos"))
    }

    @Test
    fun twentyFourBitFlacIsNotAtmosEvidence() {
        assertFalse(AudioQualityInfo.hasAtmosCodecEvidence("24-bit / 48 kHz FLAC", "audio/flac"))
    }

    @Test
    fun mpeghIsSony360RealityAudio() {
        assertTrue(AudioQualityInfo.hasSony360RealityAudioEvidence("audio/mpeg-h", "360 Reality Audio"))
        assertTrue(AudioQualityInfo.hasSony360RealityAudioEvidence("360 RA"))
        assertFalse(AudioQualityInfo.hasSony360RealityAudioEvidence("24-bit / 48 kHz FLAC"))
        val info = AudioQualityInfo(
            codec = "mpeg-h",
            mimeType = "audio/mpeg-h",
            bitrateKbps = 768,
            sony360RealityAudio = true
        )
        assertEquals(QualityTier.ATMOS, info.tier)
        assertEquals("360 RA", info.badgeLabel())
        assertTrue(info.playbackLabel().orEmpty().contains("360 Reality Audio"))
    }
}
