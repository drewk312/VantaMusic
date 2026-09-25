package com.audiophile.musicplayer.data.dj

/** A search hit is not evidence that it belongs to the requested artist or song. */
object RadioIdentityPolicy {
    fun acceptsTaste(profile: AiDjTasteProfile, artist: String, genre: String?): Boolean = acceptsStation(
        JukeboxStation("taste", profile.favoriteGenres.firstOrNull().orEmpty(), "", seedArtists = profile.favoriteArtists,
            genreKeywords = listOf(profile.favoriteGenres.firstOrNull().orEmpty())), artist, genre
    )

    fun normalize(value: String): String = java.text.Normalizer.normalize(value, java.text.Normalizer.Form.NFKC)
        .lowercase(java.util.Locale.ROOT).replace(Regex("[^\\p{L}\\p{N}]+"), " ").trim()

    fun matches(title: String, artist: String, requestedTitle: String?, requestedArtist: String?): Boolean {
        if (!requestedArtist.isNullOrBlank() && normalize(artist) != normalize(requestedArtist)) return false
        if (!requestedTitle.isNullOrBlank() && !requestedArtist.isNullOrBlank() &&
            normalize(title) != normalize(requestedTitle)) return false
        return true
    }

    /** Explicit regional intent survives refill and empty-pool fallback. Unknown tags do not qualify. */
    fun acceptsStation(station: JukeboxStation, artist: String, genre: String?): Boolean {
        val intent = normalize((listOf(station.name) + station.genreKeywords).joinToString(" "))
        val regionalTags = listOf("hindi", "bollywood", "punjabi", "tamil", "telugu", "bengali", "kannada", "malayalam")
            .filter { Regex("\\b$it\\b").containsMatchIn(intent) }
        val seeded = station.seedArtists.any { normalize(it) == normalize(artist) }
        if (regionalTags.isNotEmpty()) {
            val tags = normalize(genre.orEmpty())
            return seeded || regionalTags.any { Regex("\\b$it\\b").containsMatchIn(tags) }
        }
        if (station.seedArtists.isNotEmpty() && station.genreKeywords.isEmpty()) return seeded
        return true
    }
}
