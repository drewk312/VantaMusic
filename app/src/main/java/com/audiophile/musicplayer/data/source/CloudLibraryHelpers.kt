package com.audiophile.musicplayer.data.source

import com.audiophile.musicplayer.data.local.entities.SourceType

object CloudLibraryHelpers {
    const val TORBOX_PROVIDER_ID = "torbox_library"
    const val REALDEBRID_PROVIDER_ID = "realdebrid_library"
    const val CLOUD_SEARCH_TIMEOUT_MS = 15_000L
    const val CLOUD_RESOLVE_TIMEOUT_MS = 60_000L
    const val TORBOX_LINK_TTL_MS = 3 * 60 * 60 * 1000L

    fun sourceTypeForProvider(providerId: String): SourceType = when (providerId) {
        TORBOX_PROVIDER_ID -> SourceType.TORBOX
        "youtube_music" -> SourceType.YOUTUBE_MUSIC
        else -> SourceType.ADDON
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
            lower.endsWith(".wma")
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
            else -> "application/octet-stream"
        }
    }

    fun estimateBitrateKbps(fileName: String): Int = 0

    fun torBoxTrackId(torrentId: Long, fileId: Int): String = "${torrentId}_$fileId"

    fun realDebridTrackId(torrentId: String, fileId: Int): String = "rd_${torrentId}_$fileId"
}
