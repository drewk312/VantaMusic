package com.audiophile.musicplayer.playback

/** Media3 content-type hints for resolved URLs, including extensionless gateway routes. */
object PlaybackMediaType {
    const val DASH = "application/dash+xml"
    const val HLS = "application/x-mpegURL"

    fun forStream(reportedMime: String?, url: String?): String? =
        normalize(reportedMime) ?: inferFromUrl(url)

    fun normalize(mime: String?): String? {
        val clean = mime
            ?.substringBefore(';')
            ?.trim()
            ?.lowercase()
            ?.takeIf { it.isNotBlank() }
            ?: return null
        return when (clean) {
            "application/dash+xml" -> DASH
            "application/vnd.apple.mpegurl", "application/x-mpegurl" -> HLS
            "audio/flac", "audio/x-flac" -> "audio/flac"
            "audio/mpeg", "audio/mp3", "audio/x-mpeg", "audio/x-mp3" -> "audio/mpeg"
            "audio/mp4", "audio/aac", "audio/x-m4a", "audio/mp4a-latm" -> "audio/mp4"
            "audio/ogg", "application/ogg" -> "audio/ogg"
            "audio/opus" -> "audio/opus"
            "audio/wav", "audio/wave", "audio/x-wav" -> "audio/wav"
            "audio/x-ms-wma" -> "audio/x-ms-wma"
            "application/octet-stream", "binary/octet-stream" -> null
            else -> clean.takeIf { it.startsWith("audio/") }
        }
    }

    fun inferFromUrl(url: String?): String? {
        val clean = url?.trim().orEmpty()
        if (clean.isBlank()) return null
        val path = runCatching { java.net.URI(clean).path.orEmpty().lowercase() }
            .getOrDefault(clean.substringBefore('?').substringBefore('#').lowercase())
        return when {
            path.endsWith(".mpd") || path.endsWith("/manifest/mpd") || path.endsWith("/api/manifest/mpd") -> DASH
            path.endsWith(".m3u8") -> HLS
            path.endsWith(".flac") -> "audio/flac"
            path.endsWith(".mp3") -> "audio/mpeg"
            path.endsWith(".m4a") || path.endsWith(".aac") -> "audio/mp4"
            path.endsWith(".ogg") || path.endsWith(".oga") -> "audio/ogg"
            path.endsWith(".opus") -> "audio/opus"
            path.endsWith(".wav") -> "audio/wav"
            path.endsWith(".wma") -> "audio/x-ms-wma"
            path.endsWith(".iamf") -> "audio/iamf"
            hostLooksLikeQobuzCdn(clean) -> "audio/flac"
            else -> null
        }
    }

    private fun hostLooksLikeQobuzCdn(url: String): Boolean {
        val host = runCatching { java.net.URI(url).host.orEmpty().lowercase() }.getOrDefault("")
        return host.contains("akamaized.net") ||
            host.contains("qobuz") ||
            host.contains("dzcdn.net")
    }
}
