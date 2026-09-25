package com.audiophile.musicplayer.data.source

import com.audiophile.musicplayer.data.local.entities.SourceType

object CloudLibraryHelpers {
    const val TORBOX_PROVIDER_ID = "torbox_library"
    const val REALDEBRID_PROVIDER_ID = "realdebrid_library"
    /** Catalog search should return like Apple Music, not wait on every provider. */
    const val CLOUD_SEARCH_TIMEOUT_MS = 8_000L
    /** End-to-end tap-to-play budget. Direct resolve, search fallback, and HTTP must fit inside this. */
    const val TAP_PLAY_TOTAL_BUDGET_MS = 14_000L
    const val CLOUD_RESOLVE_TIMEOUT_MS = TAP_PLAY_TOTAL_BUDGET_MS
    const val TAP_PLAY_RESOLVE_TIMEOUT_MS = TAP_PLAY_TOTAL_BUDGET_MS
    const val TAP_PLAY_SEARCH_TIMEOUT_MS = 4_000L
    const val TORBOX_LINK_TTL_MS = 3 * 60 * 60 * 1000L

    fun remainingTapBudgetMs(
        startedAtMs: Long,
        budgetMs: Long = TAP_PLAY_TOTAL_BUDGET_MS,
        nowMs: Long = System.currentTimeMillis()
    ): Long = (budgetMs - (nowMs - startedAtMs)).coerceAtLeast(0L)

    fun sourceTypeForProvider(providerId: String): SourceType = when (providerId) {
        TORBOX_PROVIDER_ID -> SourceType.TORBOX
        "youtube_music" -> SourceType.YOUTUBE_MUSIC
        else -> SourceType.ADDON
    }

    fun isSampleOrPreviewUrl(url: String): Boolean {
        if (url.isBlank()) return false
        val lower = url.lowercase()
        if (lower.contains("audio-ssl.itunes.apple.com") || lower.contains("itunes.apple.com")) return true
        if (lower.contains("cdns-preview") || lower.contains(".dzcdn.net/stream/")) return true
        if (lower.contains("/preview/") || lower.contains("preview.mpd") || lower.contains("preview.m4a")) return true
        if (lower.contains("range=0-") || lower.contains("range=0%2d")) return true
        return false
    }

    fun isAudioFile(name: String, mimeType: String?): Boolean {
        val lower = name.lowercase()
        if (mimeType?.startsWith("audio/") == true) return true
        return lower.endsWith(".flac") ||
            lower.endsWith(".wav") ||
            lower.endsWith(".alac") ||
            lower.endsWith(".m4a") ||
            lower.endsWith(".aac") ||
            lower.endsWith(".mp3") ||
            lower.endsWith(".ogg") ||
            lower.endsWith(".opus") ||
            lower.endsWith(".wma") ||
            lower.endsWith(".iamf")
    }

    fun parseArtistTitle(fileName: String, fallbackAlbum: String): Pair<String, String> {
        val withoutExt = fileName.substringBeforeLast('.').trim()
        val dashParts = withoutExt.split(" - ", limit = 2)
        if (dashParts.size == 2) {
            return dashParts[0].trim().ifBlank { "Unknown Artist" } to
                dashParts[1].trim().ifBlank { withoutExt }
        }
        return "Unknown Artist" to withoutExt.ifBlank { fallbackAlbum }
    }

    fun estimateQualityLabel(fileName: String, fallback: String = "Stream"): String {
        val lower = fileName.lowercase()
        return when {
            lower.endsWith(".flac") -> "FLAC"
            lower.endsWith(".wav") -> "WAV"
            lower.endsWith(".alac") -> "ALAC"
            lower.endsWith(".m4a") || lower.endsWith(".aac") -> "AAC"
            lower.endsWith(".mp3") -> "MP3"
            lower.endsWith(".ogg") || lower.endsWith(".opus") -> "Opus"
            lower.endsWith(".iamf") -> "IAMF"
            else -> fallback
        }
    }

    fun mimeTypeForFileName(fileName: String): String {
        val lower = fileName.lowercase()
        return when {
            lower.endsWith(".flac") -> "audio/flac"
            lower.endsWith(".wav") -> "audio/wav"
            lower.endsWith(".alac") -> "audio/alac"
            lower.endsWith(".m4a") -> "audio/mp4"
            lower.endsWith(".aac") -> "audio/aac"
            lower.endsWith(".mp3") -> "audio/mpeg"
            lower.endsWith(".ogg") -> "audio/ogg"
            lower.endsWith(".opus") -> "audio/opus"
            lower.endsWith(".iamf") -> "audio/iamf"
            else -> "application/octet-stream"
        }
    }

    fun estimateBitrateKbps(fileName: String): Int = 0

    /**
     * Use a reported bitrate when present. Otherwise infer from codec/quality
     * labels. Never invent 128 kbps for an unknown catalog stream.
     */
    fun inferStreamBitrateKbps(
        reportedKbps: Int?,
        quality: String? = null,
        mimeType: String? = null,
        format: String? = null
    ): Int {
        if (reportedKbps != null && reportedKbps > 0) return reportedKbps
        val q = listOf(quality, mimeType, format)
            .mapNotNull { it?.lowercase()?.trim()?.takeIf { value -> value.isNotEmpty() } }
            .joinToString(" ")
        if (q.isBlank()) return 0
        return when {
            "dsd" in q || "192" in q && ("khz" in q || "hi-res" in q || "hires" in q) -> 9216
            "atmos" in q || "eac3" in q || "e-ac-3" in q || "ac-4" in q || "ac4" in q || "joc" in q -> 0
            "96" in q || "88.2" in q || "hi-res" in q || "hires" in q || "24-bit" in q || "24bit" in q -> 2304
            q == "27" || q == "7" -> 2304
            q == "6" || q == "24" || q == "16" -> 1411
            "lossless" in q || "flac" in q || "alac" in q || "wav" in q -> 1411
            q == "5" || "320" in q -> 320
            "256" in q -> 256
            "mp3" in q -> 320
            "opus" in q || "ogg" in q -> 160
            else -> 0
        }
    }

    fun torBoxTrackId(torrentId: Long, fileId: Int): String = "${torrentId}_$fileId"

    fun realDebridTrackId(torrentId: String, fileId: Int): String = "rd_${torrentId}_$fileId"
}
