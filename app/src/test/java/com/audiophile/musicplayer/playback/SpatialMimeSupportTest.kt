package com.audiophile.musicplayer.playback

import androidx.media3.common.Format
import androidx.media3.common.MimeTypes
import com.audiophile.musicplayer.playback.spatial.SpatialMimeSupport
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SpatialMimeSupportTest {
    @Test fun openJocAcceptsJocMime() {
        assertTrue(SpatialMimeSupport.isOpenJocInput(Format.Builder()
            .setSampleMimeType(MimeTypes.AUDIO_E_AC3_JOC).setCodecs("ec-3").build()))
    }

    @Test fun openJocAcceptsEcPlus3OnEac3() {
        assertTrue(SpatialMimeSupport.isOpenJocInput(Format.Builder()
            .setSampleMimeType(MimeTypes.AUDIO_E_AC3).setCodecs("ec+3").build()))
    }

    @Test fun openJocRejectsOrdinaryEac3() {
        assertFalse(SpatialMimeSupport.isOpenJocInput(Format.Builder()
            .setSampleMimeType(MimeTypes.AUDIO_E_AC3).setCodecs("ec-3").build()))
    }

    @Test fun ittiamAcceptsLcProfileOnly() {
        assertTrue(SpatialMimeSupport.isIttiamMpeghInput(Format.Builder()
            .setSampleMimeType(MimeTypes.AUDIO_MPEGH_MHM1).setCodecs("mhm1.0d").build()))
        assertFalse(SpatialMimeSupport.isIttiamMpeghInput(Format.Builder()
            .setSampleMimeType(MimeTypes.AUDIO_MPEGH_MHA1).setCodecs("mha1.0.3").build()))
    }
}
