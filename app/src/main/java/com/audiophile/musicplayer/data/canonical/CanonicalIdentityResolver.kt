package com.audiophile.musicplayer.data.canonical

object CanonicalIdentityResolver {

    fun resolve(track: CanonicalTrack): CanonicalTrack {
        val normalizedTitle = normalizeQuery(track.title)
        val normalizedArtist = normalizeQuery(track.artist)
        return track.copy(
            title = normalizedTitle,
            artist = normalizedArtist
        )
    }

    fun areSameTrack(a: CanonicalTrack, b: CanonicalTrack): Boolean {
        if (a.isrc != null && b.isrc != null && a.isrc.equals(b.isrc, ignoreCase = true)) return true
        return normalizeQuery(a.title) == normalizeQuery(b.title) &&
               normalizeQuery(a.artist) == normalizeQuery(b.artist)
    }

    fun generateCanonicalId(isrc: String?, _unused: Any?, title: String, artist: String, album: String?, durationMs: Long?): String {
        if (!isrc.isNullOrBlank()) return "isrc:${isrc.trim().uppercase()}"
        val parts = listOfNotNull(
            normalizeQuery(title).takeIf { it.isNotBlank() },
            normalizeQuery(artist).takeIf { it.isNotBlank() },
            normalizeQuery(album.orEmpty()).takeIf { it.isNotBlank() },
            durationMs?.toString()
        )
        return "text:${parts.joinToString(":")}"
    }

    private fun normalizeQuery(value: String): String {
        return value.trim().lowercase()
            .replace(Regex("[^a-z0-9\\s]"), "")
            .replace(Regex("\\s+"), " ")
            .trim()
    }
}
