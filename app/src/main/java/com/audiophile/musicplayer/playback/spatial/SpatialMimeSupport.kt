package com.audiophile.musicplayer.playback.spatial

import androidx.media3.common.Format
import androidx.media3.common.MimeTypes
import java.util.Locale

/**
 * OpenJOC admits JOC only. Ordinary E-AC-3 must stay on the platform decoder;
 * OpenJOC returns OPENJOC_STATUS_NOT_JOC for non-JOC E-AC-3.
 */
object SpatialMimeSupport {
    @JvmStatic
    fun isOpenJocInput(format: Format): Boolean {
        val mime = format.sampleMimeType
        if (mime == MimeTypes.AUDIO_E_AC3_JOC) return true
        if (mime != MimeTypes.AUDIO_E_AC3) return false
        val codecs = format.codecs?.lowercase(Locale.ROOT).orEmpty()
        return "joc" in codecs || "ec+3" in codecs
    }

    @JvmStatic
    fun isIttiamMpeghInput(format: Format): Boolean {
        val mime = format.sampleMimeType
        if (mime != MimeTypes.AUDIO_MPEGH_MHA1 && mime != MimeTypes.AUDIO_MPEGH_MHM1) return false
        val codecs = format.codecs?.lowercase(Locale.ROOT) ?: return false
        return codecs.matches(Regex("mh[am]1\\.0[b-e]"))
    }
}
