package com.audiophile.musicplayer.data.lastfm

import android.util.Log
import com.audiophile.musicplayer.ResolverConfigStore

/**
 * Basic Last.fm scrobbling hook. Requires api_key + api_secret + session key from
 * auth.getMobileSession (obtain once via Last.fm account settings or a future in-app auth flow).
 *
 * Genre resolution works with api_key alone; scrobbling needs a session key saved in settings.
 */
class LastFmScrobbler(
    private val configStore: ResolverConfigStore
) {
    companion object {
        private const val TAG = "LastFmScrobbler"
        private const val MIN_SCROBBLE_MS = 4 * 60 * 1000L
        private const val SCROBBLE_FRACTION = 0.5f
    }

    data class ScrobbleCandidate(
        val title: String,
        val artist: String,
        val album: String?,
        val durationMs: Long,
        val positionMs: Long
    )

    fun shouldScrobble(candidate: ScrobbleCandidate): Boolean {
        if (candidate.durationMs <= 0L) {
            return candidate.positionMs >= MIN_SCROBBLE_MS
        }
        val threshold = maxOf(
            (candidate.durationMs * SCROBBLE_FRACTION).toLong(),
            MIN_SCROBBLE_MS.coerceAtMost(candidate.durationMs)
        )
        return candidate.positionMs >= threshold
    }

    fun scrobbleIfConfigured(candidate: ScrobbleCandidate) {
        if (!shouldScrobble(candidate)) return
        val apiKey = configStore.getLastFmApiKey()
        val sessionKey = configStore.getLastFmSessionKey()
        val username = configStore.getLastFmUsername()
        if (apiKey.isNullOrBlank() || sessionKey.isNullOrBlank() || username.isNullOrBlank()) {
            Log.d(TAG, "Scrobble skipped — configure Last.fm API key, username, and session key in Advanced Settings")
            return
        }
        // Full track.scrobble signing requires api_secret; deferred until mobile auth flow is wired.
        Log.d(
            TAG,
            "Scrobble ready for '${candidate.title}' by ${candidate.artist} " +
                "(session configured for $username; track.scrobble API call pending auth flow)"
        )
    }
}
