package com.audiophile.musicplayer.data.display

import com.audiophile.musicplayer.data.source.SearchItemStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VantaQualityInfoTest {
    @Test
    fun fromSource_labelsDolbyAtmosOnlyWhenMetadataClaimsIt() {
        val info = VantaQualityInfo.fromSource(
            bitrate = 768,
            quality = "Dolby Atmos",
            mime = "audio/eac3-joc",
            format = "e-ac-3 joc",
            status = SearchItemStatus.VALIDATED_PLAYABLE,
            isValidated = true,
            sourceProviderId = "apple"
        )

        assertTrue(info.isDolbyAtmos)
        assertTrue(info.isSpatialAudio)
        assertTrue(info.bestQualityLabel().orEmpty().contains("Dolby Atmos"))
    }

    @Test
    fun fromSource_doesNotInventDolbyAtmosFromHighBitrate() {
        val info = VantaQualityInfo.fromSource(
            bitrate = 2304,
            quality = "24-bit / 96 kHz FLAC",
            mime = "audio/flac",
            format = "flac",
            status = SearchItemStatus.VALIDATED_PLAYABLE,
            isValidated = true,
            sourceProviderId = "qobuz"
        )

        assertFalse(info.isDolbyAtmos)
        assertFalse(info.isSpatialAudio)
        assertEquals("Hi-Res \u00B7 24-bit/96 kHz", info.bestQualityLabel())
    }
}
