package com.audiophile.musicplayer.ui

import com.audiophile.musicplayer.data.display.VantaQualityInfo
import com.audiophile.musicplayer.data.local.entities.UnifiedTrack
import com.audiophile.musicplayer.data.local.entities.UnifiedTrackWithSources
import com.audiophile.musicplayer.data.source.SearchItemStatus
import com.audiophile.musicplayer.playback.NowPlayingState
import com.audiophile.musicplayer.radio.sonic.AudioFeatureExtractor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VisualFormatBadgeTest {

    @Test
    fun dolbyAtmosEvidence_triggersAtmosBadgeCriteria() {
        val info = VantaQualityInfo.fromSource(
            bitrate = 768,
            quality = "Dolby Atmos",
            mime = "audio/eac3-joc",
            format = "eac3",
            status = SearchItemStatus.VALIDATED_PLAYABLE,
            isValidated = true,
            isDolbyAtmos = true,
            sourceProviderId = "tidal"
        )
        assertTrue(info.isDolbyAtmos)
        assertTrue(isDolbyAtmosLabel(info.bestQualityLabel()))
    }

    @Test
    fun hiResAudio_triggersHiResBadgeCriteria() {
        val info = VantaQualityInfo.fromSource(
            bitrate = 2800,
            quality = "24-bit / 96 kHz",
            mime = "audio/flac",
            format = "flac",
            sampleRateHz = 96000,
            bitDepth = 24,
            status = SearchItemStatus.VALIDATED_PLAYABLE,
            isValidated = true,
            sourceProviderId = "local"
        )
        assertTrue(info.isHiRes == true)
        assertEquals(24, info.bitDepth)
        assertEquals(96000, info.sampleRateHz)
    }

    @Test
    fun losslessFlac_triggersLosslessBadgeCriteria() {
        val info = VantaQualityInfo.fromSource(
            bitrate = 920,
            quality = "16-bit / 44.1 kHz",
            mime = "audio/flac",
            format = "flac",
            sampleRateHz = 44100,
            bitDepth = 16,
            status = SearchItemStatus.VALIDATED_PLAYABLE,
            isValidated = true,
            sourceProviderId = "local"
        )
        assertTrue(info.isLossless == true)
        assertFalse(info.isHiRes == true)
        assertEquals("flac", info.format)
    }

    @Test
    fun trueHdSurround_triggersMultiChannelCriteria() {
        val info = VantaQualityInfo.fromSource(
            bitrate = 4500,
            quality = "5.1 Surround TrueHD",
            mime = "audio/vnd.dolby.mlp",
            format = "truehd",
            sampleRateHz = 96000,
            bitDepth = 24,
            channels = 6,
            status = SearchItemStatus.VALIDATED_PLAYABLE,
            isValidated = true,
            isSurround = true,
            sourceProviderId = "local"
        )
        assertTrue(info.isSurround)
        assertEquals(6, info.channels)
        assertTrue(info.channels != null && info.channels > 2)
    }

    @Test
    fun acousticFeatureExtraction_triggersPureAcousticBadgeThreshold() {
        val folkVector = AudioFeatureExtractor.estimateFromMetadata(
            title = "Acoustic Unplugged Session",
            artist = "Folk String Quartet",
            genres = listOf("acoustic", "folk", "classical")
        )
        // Acousticness should be >= 0.65 threshold
        assertTrue(
            "Expected acousticness >= 0.65 for folk acoustic track, got ${folkVector.acousticness}",
            folkVector.acousticness >= 0.65
        )

        val edmVector = AudioFeatureExtractor.estimateFromMetadata(
            title = "Midnight Club Beat",
            artist = "Electronic DJ",
            genres = listOf("edm", "techno")
        )
        // EDM should NOT trigger acousticness
        assertFalse(
            "Expected acousticness < 0.65 for EDM track, got ${edmVector.acousticness}",
            edmVector.acousticness >= 0.65
        )
    }

    @Test
    fun nowPlayingState_populatesAndPreservesAcousticness() {
        val track = UnifiedTrackWithSources(
            track = UnifiedTrack(
                trackId = 999L,
                title = "Acoustic Reverie",
                artist = "Acoustic Duo",
                albumName = "Strings Unplugged",
                coverArtUrl = null,
                durationMs = 180000L
            ),
            sources = emptyList()
        )

        val state = NowPlayingState.fromTrackChange(
            track = track,
            qualityInfo = null,
            queuePosition = 0,
            queueSize = 1
        )

        assertNotNull(state.acousticness)
        assertTrue(state.acousticness!! >= 0.65)

        // Preserves across track updates if same track
        val updated = NowPlayingState.fromTrackChange(
            track = track,
            qualityInfo = null,
            queuePosition = 0,
            queueSize = 1,
            previous = state
        )
        assertEquals(state.acousticness, updated.acousticness)
    }
}
