package com.audiophile.musicplayer.playback

import androidx.media3.common.Format
import androidx.media3.common.MimeTypes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class Media3AudioFormatReaderTest {
    @Test fun mpeghIsNotMisidentifiedAsMp3OrAtmos() {
        val decoded = Media3AudioFormatReader.read(Format.Builder()
            .setSampleMimeType(MimeTypes.AUDIO_MPEGH_MHA1).setCodecs("mha1.0D").build())
        assertEquals("mpeg-h", decoded.codec)
        assertFalse(decoded.dolbyAtmos)
    }

    @Test
    fun plainEac3KeepsCodecButDoesNotClaimAtmos() {
        val decoded = Media3AudioFormatReader.read(
            Format.Builder()
                .setSampleMimeType(MimeTypes.AUDIO_E_AC3)
                .setCodecs("ec-3")
                .setSampleRate(48_000)
                .setChannelCount(6)
                .build()
        )

        assertEquals("eac3", decoded.codec)
        assertFalse(decoded.dolbyAtmos)
    }

    @Test
    fun eac3JocClaimsAtmosOnlyFromJocMime() {
        val decoded = Media3AudioFormatReader.read(
            Format.Builder()
                .setSampleMimeType(MimeTypes.AUDIO_E_AC3_JOC)
                .setCodecs("ec-3")
                .setSampleRate(48_000)
                .setChannelCount(6)
                .build()
        )

        assertEquals("eac3", decoded.codec)
        assertTrue(decoded.dolbyAtmos)
    }
}
