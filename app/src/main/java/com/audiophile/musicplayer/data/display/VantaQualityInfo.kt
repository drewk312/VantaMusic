package com.audiophile.musicplayer.data.display

import android.util.Log
import com.audiophile.musicplayer.data.local.entities.SourceType
import com.audiophile.musicplayer.data.local.entities.TrackSource
import com.audiophile.musicplayer.data.source.SearchItemStatus
import com.audiophile.musicplayer.data.source.isConfirmedSuccess
import java.util.Locale

data class VantaQualityInfo(
    val format: String?,
    val bitrateKbps: Int?,
    val sampleRateHz: Int?,
    val bitDepth: Int?,
    val isLossless: Boolean?,
    val isHiRes: Boolean?,
    val isSpatialAudio: Boolean = false,
    val isDolbyAtmos: Boolean = false,
    val isSurround: Boolean = false,
    /** "verified" requires delivered-stream codec/channel metadata; other values are discovery-only. */
    val spatialEvidence: String? = null,
    val isPreview: Boolean,
    val isValidated: Boolean,
    val sourceProviderId: String?,
    val reason: String,
    val label: String?
) {
    fun bestLabel(): String? = label?.trim()?.takeIf { it.isNotEmpty() }

    fun bestQualityLabel(): String? = bestLabel()

    fun accessibilityLabel(): String? =
        bestLabel()?.replace(" \u00B7 ", " ")?.let { "Audio quality: $it" }

    companion object {
        private const val TAG = "VANTA_QUALITY_TRUTH"
        private const val MIN_PLAUSIBLE_LOSSLESS_BITRATE_KBPS = 700

        fun fromBitrate(
            bitrateKbps: Int,
            preview: Boolean = false,
            isValidated: Boolean = true,
            sourceProviderId: String? = null,
            reason: String = if (preview) "preview_source" else "known_bitrate"
        ): VantaQualityInfo = fromSource(
            bitrate = bitrateKbps,
            quality = null,
            mime = null,
            format = null,
            status = if (preview) SearchItemStatus.PREVIEW else null,
            isValidated = isValidated || preview,
            sourceProviderId = sourceProviderId,
            reason = reason
        )

        fun fromSource(
            bitrate: Int?,
            quality: String?,
            mime: String?,
            status: SearchItemStatus?,
            format: String? = null,
            sampleRateHz: Int? = null,
            bitDepth: Int? = null,
            isLossless: Boolean? = null,
            isHiRes: Boolean? = null,
            isSpatialAudio: Boolean = false,
            isDolbyAtmos: Boolean = false,
            isSurround: Boolean = false,
            spatialEvidence: String? = null,
            isValidated: Boolean = false,
            sourceProviderId: String? = null,
            reason: String? = null,
            bitrateIsMeasured: Boolean = false
        ): VantaQualityInfo {
            val preview = status == SearchItemStatus.PREVIEW
            val validated = isValidated ||
                status?.isConfirmedSuccess() == true ||
                status == SearchItemStatus.PREVIEW ||
                status == SearchItemStatus.DEMO_ONLY
            val normalizedFormat = normalizeFormat(format, quality, mime)
            val rawBitrate = bitrate?.takeIf { it > 0 }
            val normalizedBitDepth = bitDepth?.takeIf { it > 0 } ?: inferBitDepth(quality)
            val normalizedSampleRate = sampleRateHz?.takeIf { it > 0 }
                ?: inferSampleRateFromQuality(quality)
                ?: inferSampleRateFromBitrateField(
                    bitrateKbps = rawBitrate,
                    format = normalizedFormat,
                    quality = quality,
                    isHiRes = isHiRes
                )
            val normalizedBitrate = sanitizeBitrate(
                bitrateKbps = rawBitrate,
                format = normalizedFormat,
                quality = quality,
                sampleRateHz = normalizedSampleRate,
                bitrateIsMeasured = bitrateIsMeasured
            )
            val detectedAtmos = isDolbyAtmos || qualityClaimsAtmos(quality, mime, format)
            val detectedSpatial = isSpatialAudio || detectedAtmos || qualityClaimsSpatial(quality)
            val detectedSurround = isSurround || detectedAtmos || qualityClaimsSurround(quality, mime, format)
            val inferredLossless = isLossless ?: inferLossless(normalizedFormat, quality)
            val inferredHiRes = isHiRes ?: inferHiRes(quality, normalizedSampleRate, normalizedBitDepth)
            if (rawBitrate != null && normalizedBitrate == null) {
                Log.d(
                    TAG,
                    "step='sanitized' providerId='${sourceProviderId ?: "unknown"}' " +
                        "format='${normalizedFormat ?: "unknown"}' rawBitrateKbps=$rawBitrate " +
                        "sampleRateHz=${normalizedSampleRate ?: "null"} reason='implausible_lossless_bitrate'"
                )
            }
            val label = buildLabel(
                format = normalizedFormat,
                bitrateKbps = normalizedBitrate,
                sampleRateHz = normalizedSampleRate,
                bitDepth = normalizedBitDepth,
                isLossless = inferredLossless,
                isHiRes = inferredHiRes,
                isSpatialAudio = detectedSpatial,
                isDolbyAtmos = detectedAtmos,
                isSurround = detectedSurround,
                isPreview = preview,
                isValidated = validated,
                rawQuality = quality
            )
            val resolvedReason = reason ?: when {
                preview -> "preview_source"
                !validated -> "unvalidated_source"
                label == null -> "unknown_quality"
                else -> "validated_source"
            }
            val info = VantaQualityInfo(
                format = normalizedFormat,
                bitrateKbps = normalizedBitrate,
                sampleRateHz = normalizedSampleRate,
                bitDepth = normalizedBitDepth,
                isLossless = inferredLossless,
                isHiRes = inferredHiRes,
                isSpatialAudio = detectedSpatial,
                isDolbyAtmos = detectedAtmos,
                isSurround = detectedSurround,
                spatialEvidence = spatialEvidence,
                isPreview = preview,
                isValidated = validated,
                sourceProviderId = sourceProviderId,
                reason = resolvedReason,
                label = label
            )
            logInfo(info, step = if (preview) "preview" else if (label == null) "hidden" else "resolved")
            return info
        }

        fun fromTrackSource(
            source: TrackSource?,
            status: SearchItemStatus?,
            isValidated: Boolean = status?.isConfirmedSuccess() == true
        ): VantaQualityInfo? {
            source ?: return null
            val format = inferFormatFromUrl(source.streamUrl)
            val providerId = source.externalProviderId ?: source.sourceType.name.lowercase()
            val resolvedStatus = status ?: when (source.sourceType) {
                SourceType.LOCAL -> SearchItemStatus.LOCAL_PLAYABLE
                else -> SearchItemStatus.SOURCE_FOUND
            }
            return fromSource(
                bitrate = source.bitrate,
                quality = null,
                mime = null,
                format = format,
                status = resolvedStatus,
                isValidated = isValidated || resolvedStatus.isConfirmedSuccess(),
                sourceProviderId = providerId,
                reason = when {
                    resolvedStatus == SearchItemStatus.LOCAL_PLAYABLE -> "local_playable"
                    resolvedStatus.isConfirmedSuccess() -> "validated_source"
                    else -> "unvalidated_source"
                }
            )
        }

        fun logValidated(trackId: String?, info: VantaQualityInfo?) {
            val label = info?.bestLabel()
            if (label == null) {
                Log.d(TAG, "step='hidden' reason='unknown_quality' trackId=${trackId ?: "null"}")
            } else {
                Log.d(TAG, "step='validated' trackId=${trackId ?: "null"} label='$label'")
            }
        }

        private fun logInfo(info: VantaQualityInfo, step: String) {
            val label = info.bestLabel()
            if (label == null) {
                Log.d(TAG, "step='hidden' reason='${info.reason}'")
                return
            }
            Log.d(
                TAG,
                    "step='$step' providerId='${info.sourceProviderId ?: "unknown"}' " +
                        "format='${info.format ?: "unknown"}' " +
                    "bitrateKbps=${info.bitrateKbps ?: "null"} " +
                    "sampleRateHz=${info.sampleRateHz ?: "null"} " +
                    "bitDepth=${info.bitDepth ?: "null"} label='$label'"
            )
        }

        private fun buildLabel(
            format: String?,
            bitrateKbps: Int?,
            sampleRateHz: Int?,
            bitDepth: Int?,
            isLossless: Boolean?,
            isHiRes: Boolean?,
            isSpatialAudio: Boolean,
            isDolbyAtmos: Boolean,
            isSurround: Boolean,
            isPreview: Boolean,
            isValidated: Boolean,
            rawQuality: String?
        ): String? {
            if (isPreview) {
                return if (format in setOf("aac", "m4a", "mp4")) "AAC Preview" else "Preview"
            }

            if (!isValidated) return null

            val immersivePrefix = when {
                isDolbyAtmos -> "Dolby Atmos"
                isSpatialAudio -> "Spatial Audio"
                isSurround -> "Surround"
                else -> null
            }

            val hiRes = isHiRes == true || (bitDepth != null && bitDepth > 16) || (sampleRateHz != null && sampleRateHz > 44_100)
            if (hiRes) {
                val depthRate = formatDepthRate(bitDepth, sampleRateHz)
                val base = when {
                    depthRate != null -> "Hi-Res \u00B7 $depthRate"
                    format != null -> "Hi-Res \u00B7 ${formatDisplay(format)}"
                    else -> "Hi-Res"
                }
                return withImmersivePrefix(immersivePrefix, base)
            }

            if (format == "flac") {
                val base = when {
                    bitrateKbps != null && bitrateKbps >= MIN_PLAUSIBLE_LOSSLESS_BITRATE_KBPS ->
                        "FLAC \u00B7 ${bitrateKbps} kbps"
                    else -> "FLAC"
                }
                return withImmersivePrefix(immersivePrefix, base)
            }

            if (isLossless == true && bitrateKbps != null && bitrateKbps >= 1411) {
                val base = if (format != null) {
                    "${formatDisplay(format)} \u00B7 ${bitrateKbps} kbps"
                } else {
                    "CD Quality"
                }
                return withImmersivePrefix(immersivePrefix, base)
            }

            if (format != null) {
                val base = if (bitrateKbps != null) {
                    "${formatDisplay(format)} \u00B7 ${bitrateKbps} kbps"
                } else {
                    formatDisplay(format)
                }
                return withImmersivePrefix(immersivePrefix, base)
            }

            if (bitrateKbps != null) return withImmersivePrefix(immersivePrefix, "${bitrateKbps} kbps")

            return withImmersivePrefix(immersivePrefix, cleanQualityLabel(rawQuality))
        }

        private fun withImmersivePrefix(prefix: String?, base: String?): String? = when {
            prefix == null -> base
            base.isNullOrBlank() -> prefix
            base.contains(prefix, ignoreCase = true) -> base
            else -> "$prefix \u00B7 $base"
        }

        private fun normalizeFormat(format: String?, quality: String?, mime: String?): String? {
            val haystack = listOfNotNull(format, quality, mime)
                .joinToString(" ")
                .lowercase()
            return when {
                haystack.isBlank() -> null
                "flac" in haystack || "audio/x-flac" in haystack -> "flac"
                "alac" in haystack -> "alac"
                "wav" in haystack || "aiff" in haystack -> "wav"
                "mpeg" in haystack || "mp3" in haystack -> "mp3"
                "aac" in haystack || "m4a" in haystack || "mp4a" in haystack || "audio/mp4" in haystack -> "aac"
                "opus" in haystack -> "opus"
                "ogg" in haystack || "vorbis" in haystack -> "ogg"
                else -> null
            }
        }

        private fun inferFormatFromUrl(url: String): String? {
            val cleanPath = url.substringBefore('?').substringBefore('#').lowercase()
            return when {
                cleanPath.endsWith(".flac") -> "flac"
                cleanPath.endsWith(".alac") -> "alac"
                cleanPath.endsWith(".wav") -> "wav"
                cleanPath.endsWith(".aiff") || cleanPath.endsWith(".aif") -> "wav"
                cleanPath.endsWith(".mp3") -> "mp3"
                cleanPath.endsWith(".aac") || cleanPath.endsWith(".m4a") -> "aac"
                cleanPath.endsWith(".ogg") || cleanPath.endsWith(".oga") -> "ogg"
                cleanPath.endsWith(".opus") -> "opus"
                else -> null
            }
        }

        private fun inferLossless(format: String?, quality: String?): Boolean? {
            if (isLosslessFormat(format)) return true
            return when {
                qualityClaimsLossless(quality) -> true
                format in setOf("mp3", "aac", "ogg", "opus") -> false
                else -> null
            }
        }

        private fun inferHiRes(quality: String?, sampleRateHz: Int?, bitDepth: Int?): Boolean? {
            if (bitDepth != null && bitDepth > 16) return true
            if (sampleRateHz != null && sampleRateHz > 44_100) return true
            val q = quality?.lowercase().orEmpty()
            return when {
                "hi-res" in q || "hires" in q || "24-bit" in q || "24 bit" in q -> true
                q.isNotBlank() -> false
                else -> null
            }
        }

        private fun inferBitDepth(quality: String?): Int? {
            val q = quality?.lowercase().orEmpty()
            return when {
                "24-bit" in q || "24 bit" in q || "24/" in q -> 24
                "16-bit" in q || "16 bit" in q || "16/" in q -> 16
                else -> null
            }
        }

        private fun inferSampleRateFromQuality(quality: String?): Int? {
            val q = quality?.lowercase().orEmpty()
            val match = Regex("""(\d{2,3}(?:\.\d)?)\s*k\s*hz""").find(q)
                ?: Regex("""(\d{2,3}(?:\.\d)?)\s*khz""").find(q)
                ?: return null
            val khz = match.groupValues.getOrNull(1)?.toDoubleOrNull() ?: return null
            return (khz * 1000).toInt()
        }

        private fun sanitizeBitrate(
            bitrateKbps: Int?,
            format: String?,
            quality: String?,
            sampleRateHz: Int?,
            bitrateIsMeasured: Boolean
        ): Int? {
            bitrateKbps ?: return null
            if (sampleRateHz != null && isSampleRateLikeValue(bitrateKbps)) return null
            // Older builds stored PCM-derived constants as if they were measured
            // FLAC bitrates. Treat those legacy guesses as unknown.
            if (!bitrateIsMeasured && (isLosslessFormat(format) || qualityClaimsLossless(quality)) &&
                bitrateKbps in setOf(1411, 2304)
            ) return null
            if ((isLosslessFormat(format) || qualityClaimsLossless(quality)) &&
                bitrateKbps < MIN_PLAUSIBLE_LOSSLESS_BITRATE_KBPS
            ) {
                return null
            }
            return bitrateKbps
        }

        private fun inferSampleRateFromBitrateField(
            bitrateKbps: Int?,
            format: String?,
            quality: String?,
            isHiRes: Boolean?
        ): Int? {
            bitrateKbps ?: return null
            val hasLosslessOrHiResSignal = isLosslessFormat(format) ||
                qualityClaimsLossless(quality) ||
                qualityClaimsHiRes(quality) ||
                isHiRes == true
            if (!hasLosslessOrHiResSignal) return null
            return sampleRateHzFromLooseNumber(bitrateKbps)
        }

        private fun sampleRateHzFromLooseNumber(value: Int): Int? = when (value) {
            44 -> 44_100
            48 -> 48_000
            88 -> 88_200
            96 -> 96_000
            176 -> 176_400
            192 -> 192_000
            44_100, 48_000, 88_200, 96_000, 176_400, 192_000 -> value
            else -> null
        }

        private fun isSampleRateLikeValue(value: Int): Boolean =
            sampleRateHzFromLooseNumber(value) != null

        private fun isLosslessFormat(format: String?): Boolean =
            format == "flac" || format == "alac" || format == "wav"

        private fun qualityClaimsLossless(quality: String?): Boolean {
            val q = quality?.lowercase().orEmpty()
            return "lossless" in q ||
                "flac" in q ||
                "alac" in q ||
                "wav" in q ||
                "cd quality" in q ||
                "hifi" in q ||
                "hi-fi" in q
        }

        private fun qualityClaimsHiRes(quality: String?): Boolean {
            val q = quality?.lowercase().orEmpty()
            return "hi-res" in q ||
                "hires" in q ||
                "24-bit" in q ||
                "24 bit" in q ||
                "24/" in q
        }

        private fun qualityClaimsAtmos(vararg values: String?): Boolean {
            val q = values.filterNotNull().joinToString(" ").lowercase()
            return "dolby atmos" in q ||
                " atmos" in q ||
                "e-ac-3 joc" in q ||
                "eac3-joc" in q ||
                "ec-3 joc" in q
        }

        private fun qualityClaimsSpatial(quality: String?): Boolean {
            val q = quality?.lowercase().orEmpty()
            return "spatial audio" in q || "spatial" in q || qualityClaimsAtmos(quality)
        }

        private fun qualityClaimsSurround(vararg values: String?): Boolean {
            val q = values.filterNotNull().joinToString(" ").lowercase()
            return "surround" in q || "5.1" in q || "7.1" in q || qualityClaimsAtmos(*values)
        }

        private fun cleanQualityLabel(rawQuality: String?): String? {
            val cleanQuality = rawQuality?.trim()?.takeIf { it.isNotBlank() } ?: return null
            return when {
                cleanQuality.equals("stream", ignoreCase = true) -> null
                cleanQuality.equals("audio stream", ignoreCase = true) -> null
                cleanQuality.equals("unknown", ignoreCase = true) -> null
                else -> cleanQuality
            }
        }

        private fun formatDisplay(format: String): String = when (format.lowercase()) {
            "mp3" -> "MP3"
            "aac", "m4a", "mp4" -> "AAC"
            "ogg" -> "OGG"
            "opus" -> "OPUS"
            "alac" -> "ALAC"
            "wav" -> "WAV"
            "flac" -> "FLAC"
            else -> format.uppercase()
        }

        private fun formatDepthRate(bitDepth: Int?, sampleRateHz: Int?): String? {
            if (bitDepth == null && sampleRateHz == null) return null
            val depth = bitDepth?.let { "${it}-bit" }
            val rate = sampleRateHz?.let { "${formatKhz(it)} kHz" }
            return listOfNotNull(depth, rate).joinToString("/")
        }

        private fun formatKhz(sampleRateHz: Int): String {
            if (sampleRateHz % 1000 == 0) return (sampleRateHz / 1000).toString()
            return String.format(Locale.US, "%.1f", sampleRateHz / 1000.0)
        }
    }
}
