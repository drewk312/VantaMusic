package com.audiophile.musicplayer.data.source.playback

/**
 * Paid-catalog auth stays inside those adapters. Local/direct/self-hosted
 * playback must not consult this list.
 */
object PlaybackAccountPolicy {
    fun requiresLicensedAccount(providerId: String?): Boolean {
        val id = providerId?.lowercase().orEmpty()
        if (id.isBlank()) return false
        if (id == "local" || id == "direct" || id == "file") return false
        // VANTA gateway adapters do not take an end-user Tidal/Qobuz token.
        if ("gateway" in id || id == "qobuz_tidal") return false
        return "torbox" in id ||
            "realdebrid" in id ||
            "real-debrid" in id
    }
}
