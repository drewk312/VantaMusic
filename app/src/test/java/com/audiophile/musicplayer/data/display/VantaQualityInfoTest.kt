package com.audiophile.musicplayer.data.display

import com.audiophile.musicplayer.data.source.SearchItemStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VantaQualityInfoTest {
    @Test
    fun fromSource_labelsDolbyAtmosOnlyWithCodecEvidence() {
        val info = VantaQualityInfo.fromSource(
            bitrate = 768,
            quality = "Dolby Atmos",
            mime = "audio/eac3-joc",
            format = "e-ac-3 joc",
            status = SearchItemStatus.VALIDATED_PLAYABLE,
            isValidated = true,
            sourceProviderId = "tidal"
        )

        assertTrue(info.isDolbyAtmos)
        assertEquals(false, info.isDolbyAtmos && info.format == "flac")
        assertTrue(info.bestQualityLabel().orEmpty().contains("Dolby Atmos"))
    }

    @Test
    fun fromSource_showsMeasuredHiResM4aBitrate() {
        val info = VantaQualityInfo.fromSource(
            bitrate = 3284,
            quality = null,
            mime = "audio/mp4",
            format = "m4a",
            status = SearchItemStatus.VALIDATED_PLAYABLE,
            isValidated = true,
            sourceProviderId = "tidal",
            bitrateIsMeasured = true
        )

        assertEquals(3284, info.bitrateKbps)
        assertEquals("3284 kbps \u00B7 M4A", info.bestQualityLabel())
        assertFalse(info.isDolbyAtmos)
    }

    @Test
    fun fromSource_ignoresCatalogAtmosFlagWithoutCodec() {
        val info = VantaQualityInfo.fromSource(
            bitrate = 3284,
            quality = "atmos",
            mime = "audio/mp4",
            format = "m4a",
            status = SearchItemStatus.VALIDATED_PLAYABLE,
            isValidated = true,
            sourceProviderId = "tidal",
            isDolbyAtmos = true,
            bitrateIsMeasured = true
        )

        assertFalse(info.isDolbyAtmos)
        assertEquals(3284, info.bitrateKbps)
    }

    @Test
    fun fromSource_displaysMaxLineFor24bit48khzFlac() {
        val info = VantaQualityInfo.fromSource(
            bitrate = 3284,
            quality = "24-bit / 48 kHz FLAC",
            mime = "audio/flac",
            format = "flac",
            sampleRateHz = 48_000,
            bitDepth = 24,
            status = SearchItemStatus.VALIDATED_PLAYABLE,
            isValidated = true,
            sourceProviderId = "qobuz",
            bitrateIsMeasured = true
        )

        assertFalse(info.isDolbyAtmos)
        assertEquals(true, info.isLossless)
        assertEquals(QualityTier.MAX, info.toAudioQualityInfo().tier)
        assertEquals("MAX", info.toAudioQualityInfo().badgeLabel())
        assertEquals("24-bit \u00B7 48.0 kHz \u00B7 3284 kbps \u00B7 FLAC", info.bestQualityLabel())
    }

    @Test
    fun fromSource_keepsMeasuredFlacBitrateBelowPcmFloorAndStaysMax() {
        val info = VantaQualityInfo.fromSource(
            bitrate = 800,
            quality = null,
            mime = "audio/flac",
            format = "flac",
            sampleRateHz = 48_000,
            bitDepth = 24,
            isLossless = true,
            isHiRes = true,
            status = SearchItemStatus.LOCAL_PLAYABLE,
            isValidated = true,
            sourceProviderId = "local",
            bitrateIsMeasured = true
        )

        assertEquals(QualityTier.MAX, info.toAudioQualityInfo().tier)
        assertEquals(800, info.bitrateKbps)
        assertEquals("24-bit \u00B7 48.0 kHz \u00B7 800 kbps \u00B7 FLAC", info.bestQualityLabel())
        assertFalse(info.isDolbyAtmos)
    }

    @Test
    fun fromSource_sixteenBitCdFlacIsLosslessNotMax() {
        val info = VantaQualityInfo.fromSource(
            bitrate = 900,
            quality = null,
            mime = "audio/flac",
            format = "flac",
            sampleRateHz = 44_100,
            bitDepth = 16,
            isLossless = true,
            isHiRes = false,
            status = SearchItemStatus.LOCAL_PLAYABLE,
            isValidated = true,
            sourceProviderId = "local",
            bitrateIsMeasured = true
        )

        assertEquals(QualityTier.LOSSLESS, info.toAudioQualityInfo().tier)
        assertEquals("LOSSLESS", info.toAudioQualityInfo().badgeLabel())
        assertFalse(info.isDolbyAtmos)
    }

    @Test
    fun fromSource_ordinaryM4aAacIsNeverAtmos() {
        val info = VantaQualityInfo.fromSource(
            bitrate = 256,
            quality = null,
            mime = "audio/mp4",
            format = "m4a",
            status = SearchItemStatus.VALIDATED_PLAYABLE,
            isValidated = true,
            sourceProviderId = "local",
            bitrateIsMeasured = true
        )

        assertFalse(info.isDolbyAtmos)
        assertFalse(AudioQualityInfo.hasAtmosCodecEvidence(info.mimeType, info.format, info.label))
    }

    @Test
    fun fromSource_doesNotInventDolbyAtmosFromHighBitrate() {
        val info = VantaQualityInfo.fromSource(
            bitrate = 2304,
            quality = "24-bit / 96 kHz FLAC",
            mime = "audio/flac",
            format = "flac",
            sampleRateHz = 96_000,
            bitDepth = 24,
            status = SearchItemStatus.VALIDATED_PLAYABLE,
            isValidated = true,
            sourceProviderId = "qobuz",
            bitrateIsMeasured = true
        )

        assertFalse(info.isDolbyAtmos)
        assertFalse(info.isSpatialAudio)
        assertEquals("24-bit \u00B7 96.0 kHz \u00B7 2304 kbps \u00B7 FLAC", info.bestQualityLabel())
    }

    @Test
    fun fromSource_acceptsVerifiedSony360Stamp() {
        val info = VantaQualityInfo.fromSource(
            bitrate = null,
            quality = "Stream",
            mime = null,
            format = null,
            status = SearchItemStatus.SOURCE_FOUND,
            isValidated = true,
            sourceProviderId = "amazon",
            isSony360RealityAudio = true,
            spatialEvidence = "verified"
        )
        assertTrue(info.isSony360RealityAudio)
        assertEquals("360 RA", info.compactQualityLabel())
    }

    @Test
    fun fromSource_ignoresBareSony360FlagWithoutEvidence() {
        val info = VantaQualityInfo.fromSource(
            bitrate = 1411,
            quality = "FLAC",
            mime = "audio/flac",
            format = "flac",
            status = SearchItemStatus.VALIDATED_PLAYABLE,
            isValidated = true,
            sourceProviderId = "amazon",
            isSony360RealityAudio = true
        )
        assertFalse(info.isSony360RealityAudio)
    }
}
