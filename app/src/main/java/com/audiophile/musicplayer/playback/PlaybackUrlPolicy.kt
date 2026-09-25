package com.audiophile.musicplayer.playback

import java.net.URI

object PlaybackUrlPolicy {
    /** Remote audio must be public HTTPS; local files use the separate content/file path. */
    fun isAllowedRemoteStreamUrl(value: String?): Boolean {
        val uri = runCatching { URI(value?.trim().orEmpty()) }.getOrNull() ?: return false
        if (!uri.scheme.equals("https", ignoreCase = true)) return false
        if (uri.userInfo != null) return false
        val host = uri.host?.trim()?.lowercase()?.removeSuffix(".") ?: return false
        return !isPrivateHost(host)
    }

    private fun isPrivateHost(host: String): Boolean {
        if (host == "localhost" || host.endsWith(".localhost") || host.endsWith(".local") || host.endsWith(".internal")) {
            return true
        }
        if (host.contains(':')) {
            return host == "::" || host == "::1" || host.startsWith("fc") || host.startsWith("fd") ||
                host.startsWith("fe8") || host.startsWith("fe9") || host.startsWith("fea") || host.startsWith("feb")
        }
        val octets = host.split('.').mapNotNull { it.toIntOrNull()?.takeIf { value -> value in 0..255 } }
        if (octets.size != 4) return false
        val a = octets[0]
        val b = octets[1]
        return a == 0 || a == 10 || a == 127 ||
            (a == 100 && b in 64..127) ||
            (a == 169 && b == 254) ||
            (a == 172 && b in 16..31) ||
            (a == 192 && b == 168) ||
            a >= 224
    }
}
