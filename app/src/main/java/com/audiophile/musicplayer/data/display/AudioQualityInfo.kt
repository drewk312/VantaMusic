package com.audiophile.musicplayer.data.display

import java.util.Locale

enum class QualityTier {
    ATMOS,
    MAX,
    LOSSLESS,
    HIGH,
    STANDARD,
    PREVIEW,
    UNKNOWN
}

/**
 * Single canonical description of the audio that is actually selected for
 * playback. Screens must not invent a second quality model.
 *
 * [dolbyAtmos] is always a boolean. It is true only with an immersive profile
 * signal (for example E-AC-3 JOC), not merely an E-AC-3 or AC-4 codec name.
 * Plain Dolby Digital Plus is not necessarily Atmos, and 24-bit FLAC is never
 * Atmos.
 */
data class AudioQualityInfo(
    val codec: String? = null,
    val container: String? = null,
    val mimeType: String? = null,
    val bitDepth: Int? = null,
    val sampleRateHz: Int? = null,
    val bitrateKbps: Int? = null,
    val channels: Int? = null,
    val lossless: Boolean = false,
    val hiRes: Boolean = false,
    val dolbyAtmos: Boolean = false,
    val spatialAudio: Boolean = false,
    val eclipsaAudio: Boolean = false,
    val sony360RealityAudio: Boolean = false,
    val surround: Boolean = false,
    val pcmEncoding: String? = null,
    val measured: Boolean = false,
    val transcodingOccurred: Boolean = false
) {
    val tier: QualityTier
        get() = when {
            dolbyAtmos -> QualityTier.ATMOS
            sony360RealityAudio -> QualityTier.ATMOS
            eclipsaAudio -> QualityTier.ATMOS
            lossless && isMaxLossless() -> QualityTier.MAX
            lossless -> QualityTier.LOSSLESS
            (bitrateKbps ?: 0) >= 256 -> QualityTier.HIGH
            else -> QualityTier.STANDARD
        }

    /**
     * Single canonical spatial format, derived from proven signals on this
     * model. Never elevates to an explicit format on labels alone.
     */
    val spatialFormat: SpatialFormat
        get() = when {
            dolbyAtmos -> SpatialFormat.DOLBY_ATMOS
            eclipsaAudio -> SpatialFormat.ECLIPSA_AUDIO
            sony360RealityAudio -> SpatialFormat.SONY_360_REALITY_AUDIO
            spatialAudio || surround -> SpatialFormat.UNKNOWN_SPATIAL
            else -> SpatialFormat.NONE
        }

    /**
     * Compact quality line for badges/chips on list rows, e.g.
     * `FLAC 24/96`, `ALAC 16/44`, `AAC 320`, `MP3 128`, `ATMOS`.
     * Falls back to [badgeLabel] tier text when fine detail is unknown.
     */
    fun compactLabel(): String {
        if (dolbyAtmos) return "ATMOS"
        if (sony360RealityAudio) return "360 RA"
        if (eclipsaAudio) return "ECLIPSA"
        val codecTag = codecDisplay()
        val detail = if (lossless || hiRes) {
            when {
                bitDepth != null && bitDepth > 0 && sampleRateHz != null && sampleRateHz > 0 ->
                    "$bitDepth/${formatCompactKhz(sampleRateHz)}"
                bitDepth != null && bitDepth > 0 -> "$bitDepth-bit"
                sampleRateHz != null && sampleRateHz > 0 -> "${formatCompactKhz(sampleRateHz)}kHz"
                else -> null
            }
        } else {
            bitrateKbps?.takeIf { it > 0 }?.toString()
        }
        return when {
            codecTag != null && detail != null -> "$codecTag $detail"
            codecTag != null -> codecTag
            detail != null -> detail
            else -> badgeLabel()
        }
    }

    fun badgeLabel(): String = when {
        dolbyAtmos -> "ATMOS"
        sony360RealityAudio -> "360 RA"
        eclipsaAudio -> "ECLIPSA"
        else -> when (tier) {
            QualityTier.ATMOS -> "ATMOS"
            QualityTier.MAX -> "MAX"
            QualityTier.LOSSLESS -> "LOSSLESS"
            QualityTier.HIGH -> "HIGH"
            QualityTier.STANDARD -> "STANDARD"
            QualityTier.PREVIEW -> "PREVIEW"
            QualityTier.UNKNOWN -> "AUDIO"
        }
    }

    /**
     * Truthful playback line, e.g. `24-bit · 48.0 kHz · 1411 kbps · FLAC`.
     * Values are omitted when unknown. Bitrate is displayed as measured and
     * never used to decide MAX vs LOSSLESS.
     */
    fun playbackLabel(): String? {
        if (dolbyAtmos) {
            val parts = listOfNotNull("Dolby Atmos", codecDisplay(), bitratePart())
            return parts.joinToString(" \u00B7 ")
        }
        if (sony360RealityAudio) {
            val parts = listOfNotNull("360 Reality Audio", codecDisplay(), bitratePart())
            return parts.joinToString(" \u00B7 ")
        }
        val parts = buildList {
            bitDepth?.takeIf { it > 0 }?.let { add("$it-bit") }
            sampleRateHz?.takeIf { it > 0 }?.let { add(formatKhz(it)) }
            bitratePart()?.let { add(it) }
            codecDisplay()?.let { add(it) }
        }
        if (parts.isNotEmpty()) return parts.joinToString(" \u00B7 ")
        return if (lossless) "Lossless" else null
    }

    /** MAX is codec + source bit depth + sample rate. Never a kbps threshold. */
    private fun isMaxLossless(): Boolean =
        hiRes ||
            (bitDepth != null && bitDepth > 16) ||
            (sampleRateHz != null && sampleRateHz > 44_100)

    private fun bitratePart(): String? =
        bitrateKbps?.takeIf { it > 0 }?.let { "$it kbps" }

    private fun codecDisplay(): String? {
        val raw = codec?.trim()?.takeIf { it.isNotEmpty() } ?: container
        raw ?: return null
        return when (raw.lowercase(Locale.US)) {
            "flac" -> "FLAC"
            "alac" -> "ALAC"
            "wav", "aiff" -> "WAV"
            "eac3", "e-ac-3", "ec-3" -> "E-AC-3"
            "ac4", "ac-4" -> "AC-4"
            "m4a", "mp4" -> "M4A"
            "aac" -> "AAC"
            "mp3" -> "MP3"
            "opus" -> "OPUS"
            "ogg" -> "OGG"
            else -> raw.uppercase(Locale.US)
        }
    }

    companion object {
        fun hasAtmosCodecEvidence(vararg values: String?): Boolean {
            val text = values.filterNotNull().joinToString(" ").lowercase(Locale.US)
            if (text.isBlank()) return false
            val hasJoc = "eac3_joc" in text ||
                "eac3-joc" in text ||
                "eac3+joc" in text ||
                "ec-3 joc" in text ||
                "joc" in text
            if (hasJoc) return true

            // `ec-3`/E-AC-3 on its own is ordinary Dolby Digital Plus. AC-4
            // likewise supports more than Atmos. Require the codec and an
            // explicit immersive/Atmos profile signal when JOC is not named.
            val hasEac3 = "eac3" in text || "e-ac-3" in text || "ec-3" in text
            val hasAc4 = "ac-4" in text ||
                Regex("""(?<![a-z0-9])ac4(?![a-z0-9])""").containsMatchIn(text)
            val hasImmersiveProfile = "dolby atmos" in text ||
                Regex("""(?<![a-z0-9])atmos(?![a-z0-9])""").containsMatchIn(text) ||
                "immersive" in text
            return (hasEac3 || hasAc4) && hasImmersiveProfile
        }

        /**
         * Eclipsa Audio is the open (royalty-free) immersive format built on
         * IAMF (Immersive Audio Model and Formats, AOMedia). Unlike Dolby Atmos
         * it has open source decoders/renderers, so we can actually render it
         * to binaural PCM on any device instead of only passthrough.
         */
        fun hasEclipsaCodecEvidence(vararg values: String?): Boolean {
            val text = values.filterNotNull().joinToString(" ").lowercase(Locale.US)
            if (text.isBlank()) return false
            // Marketing names and generic spatial flags do not prove an IAMF stream.
            return Regex("""(?<![a-z0-9])iamf(?![a-z0-9])""").containsMatchIn(text)
        }

        /**
         * Sony 360 Reality Audio evidence (MPEG-H). Marketing "360" claims are
         * not proof; requires "mpeg-h", "360 reality audio", "sony 360", or
         * "360ra". An "mha1" sample entry alone is shared with IAMF and is not
         * vendor-attributable.
         */
        fun hasSony360RealityAudioEvidence(vararg values: String?): Boolean {
            val text = values.filterNotNull().joinToString(" ").lowercase(Locale.US)
            if (text.isBlank()) return false
            return Regex("""(?<![a-z0-9])mpeg-?h(?![a-z0-9])""").containsMatchIn(text) ||
                "360 reality audio" in text ||
                "sony 360" in text ||
                Regex("""(?<![a-z0-9])360\s*ra(?![a-z0-9])""").containsMatchIn(text) ||
                Regex("""(?<![a-z0-9])360ra(?![a-z0-9])""").containsMatchIn(text)
        }

        fun formatKhz(sampleRateHz: Int): String {
            val khz = sampleRateHz / 1000.0
            return String.format(Locale.US, "%.1f kHz", khz)
        }

        fun formatCompactKhz(sampleRateHz: Int): Int = when (sampleRateHz) {
            44_100 -> 44
            48_000 -> 48
            88_200 -> 88
            96_000 -> 96
            176_400 -> 176
            192_000 -> 192
            else -> (sampleRateHz / 1000.0).let { Math.round(it) }.toInt()
        }
    }
}
