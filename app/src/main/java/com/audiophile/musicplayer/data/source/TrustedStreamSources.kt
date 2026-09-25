package com.audiophile.musicplayer.data.source

import android.util.Log
import java.net.URI

/**
 * Positive-allowlist gate for stream URLs produced by [SourceRegistry.resolveStream].
 *
 * The radio pipeline already validates *metadata* (PlaybackIdentityGate,
 * isLikelyMusicTrack, ContentPurityFilter), but resolved stream URLs from
 * fallback providers can point to junk hosts (live broadcast relays,
 * podcast feeds, GDSudio, SoundCloud free-tier mirrors, etc.).
 *
 * This gate rejects any stream URL whose host does not match a known-
 * good CDN or proxy domain, ensuring radio only plays real music from
 * premium or community-verified sources.
 */
object TrustedStreamSources {

    private const val TAG = "VANTA_STREAM_TRUST"

    /**
     * Substrings matched against the lowercased host of a stream URL.
     * Any match → trusted. Keep this list tight — a false negative
     * means a good stream is dropped; a false positive means junk gets
     * through to playback.
     */
    private val TRUSTED_HOST_SUBSTRINGS = listOf(
        // Qobuz CDN (Akamai)
        "akamaized.net",
        "akamaihd.net",
        // Tidal CDN
        "tidal.com",
        "nocookie.net",         // Tidal + Spotify media CDN
        // Deezer CDN
        "dzcdn.net",
        "deezer.com",
        // Gateway public fulfills (Deezer/Qobuz) via NetEase music CDN
        "126.net",
        "163.com",
        // Amazon CloudFront
        "cloudfront.net",
        // General cloud / S3
        "amazonaws.com",
        // Our own Cloudflare gateway proxy (serves /stream, /manifest)
        "workers.dev",
        // Spotify CDN (for artwork/thumbnails served by providers)
        "scdn.co",
        "spotify.com",
        // SoundCloud CDN (exact track matches / fallbacks)
        "sndcdn.com",
        "soundcloud.com",
        // CDN subdomains used by some providers
        "cdn.",
        "media.",
    )

    /** Host substrings that are always rejected even if they partially
     *  overlap with a trusted pattern. Keeps the denylist compact. */
    private val DENIED_HOST_SUBSTRINGS = listOf(
        // Live broadcast / internet radio relays
        "streamtheworld",
        "shoutcast",
        "icecast",
        "radio-browser",
        "radiostream",
        "zeno.fm",
        "live365",
        "radionomy",
        "streema",
        "tunein",
        // Podcast hosting
        "anchor.fm",
        "podcast",
        "audioboom",
        "spreaker",
        "buzzsprout",
        "megaphone",
        "simplecast",
        "transistor",
        // Video platforms (streams scraped from video)
        "youtube.com",
        "youtu.be",
        "ytimg.com",
        "googlevideo.com",
        "vimeo.com",
        "dailymotion.com",
        // Known junk / placeholder audio mirrors
        "gdstudio",
        "soundhelix",
    )

    /**
     * Returns `true` when the URL points to a host we trust for real music
     * streams.  Returns `false` for anything else (broadcast relays,
     * podcast feeds, video rips, placeholder audio, etc.).
     */
    fun isTrustedStreamUrl(url: String): Boolean {
        if (url.isBlank()) return false

        val lower = url.trim().lowercase()
        if (!lower.startsWith("https://") && !lower.startsWith("http://")) return false

        val host = runCatching { URI(url).host?.lowercase() }.getOrNull()
        if (host.isNullOrBlank()) return false

        // Explicit deny first
        if (DENIED_HOST_SUBSTRINGS.any { host.contains(it) }) {
            Log.d(TAG, "denied_host url=${url.take(120)} host=$host")
            return false
        }

        // Allowlist
        if (TRUSTED_HOST_SUBSTRINGS.any { host.contains(it) }) {
            return true
        }

        Log.d(TAG, "untrusted_host url=${url.take(120)} host=$host")
        return false
    }
}
