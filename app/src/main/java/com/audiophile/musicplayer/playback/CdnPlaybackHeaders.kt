package com.audiophile.musicplayer.playback

import java.net.URI

/**
 * Hotlink CDNs (NetEase, Qobuz Akamai, Deezer) reject ExoPlayer when Referer is
 * stripped. Attach the origin each host actually checks.
 */
object CdnPlaybackHeaders {
    const val CHROME_UA =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"

    fun forUrl(url: String): Map<String, String> {
        val host = runCatching { URI(url).host.orEmpty().lowercase() }.getOrDefault("")
        if (host.isBlank()) return emptyMap()
        return when {
            host.contains("googlevideo.com") -> emptyMap()
            host.endsWith("126.net") || host.endsWith("163.com") || host.contains("music.126") -> mapOf(
                "Referer" to "https://music.163.com/",
                "Origin" to "https://music.163.com",
                "User-Agent" to CHROME_UA
            )
            host.contains("akamaized.net") || host.contains("qobuz") -> mapOf(
                "Referer" to "https://www.qobuz.com/",
                "Origin" to "https://www.qobuz.com",
                "User-Agent" to CHROME_UA
            )
            host.contains("dzcdn.net") || host.contains("deezer.com") -> mapOf(
                "Referer" to "https://www.deezer.com/",
                "Origin" to "https://www.deezer.com",
                "User-Agent" to CHROME_UA
            )
            host.contains("workers.dev") || host.contains("vanta-music-gateway") -> mapOf(
                "Referer" to "https://vanta-music-gateway.16drewk.workers.dev/"
            )
            else -> emptyMap()
        }
    }

    fun isShortLivedCdn(url: String): Boolean {
        val host = runCatching { URI(url).host.orEmpty().lowercase() }.getOrDefault("")
        return host.endsWith("126.net") ||
            host.endsWith("163.com") ||
            host.contains("akamaized.net") ||
            host.contains("qobuz") ||
            host.contains("cloudfront.net")
    }
}
