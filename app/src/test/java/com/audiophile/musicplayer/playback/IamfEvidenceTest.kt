package com.audiophile.musicplayer.playback

import com.audiophile.musicplayer.data.display.AudioQualityInfo
import com.audiophile.musicplayer.data.display.VantaQualityInfo
import com.audiophile.musicplayer.data.source.ResolvedStream
import com.audiophile.musicplayer.data.source.playback.PlaybackStreamNormalizer
import org.junit.Assert.*
import org.junit.Test

class IamfEvidenceTest {
    @Test fun marketingLabelsCannotPromoteStereoToEclipsa() {
        assertFalse(AudioQualityInfo.hasEclipsaCodecEvidence("Eclipsa spatial open audio", "audio/flac"))
        val stream = PlaybackStreamNormalizer.normalize(ResolvedStream(
            streamUrl = "https://example.com/music.flac", bitrateKbps = 1411,
            mimeType = "audio/flac", isEclipsaAudio = true))
        assertFalse(stream.isEclipsaAudio)
    }
    @Test fun actualIamfGetsEclipsaBadgeWithoutAtmosClaim() {
        val quality = VantaQualityInfo.fromSource(bitrate = null, quality = null, status = null, mime = "audio/iamf", format = "iamf")
        assertTrue(quality.isEclipsaAudio)
        assertFalse(quality.isDolbyAtmos)
        assertEquals("ECLIPSA", quality.compactQualityLabel())
    }
}
