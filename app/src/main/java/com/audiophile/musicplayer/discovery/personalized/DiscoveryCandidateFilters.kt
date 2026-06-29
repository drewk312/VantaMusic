package com.audiophile.musicplayer.discovery.personalized

/** Pure candidate filtering helpers — unit-testable without I/O. */
object DiscoveryCandidateFilters {
    fun normalize(candidate: MixCandidate): MixCandidate =
        candidate.copy(
            title = candidate.title.trim(),
            artist = candidate.artist.trim(),
            album = candidate.album?.trim(),
            normKey = TrackNormKey.normalize(candidate.title, candidate.artist)
        )

    fun dedupe(candidates: List<MixCandidate>): List<MixCandidate> {
        val seenIsrc = mutableSetOf<String>()
        val seenNorm = mutableSetOf<String>()
        val out = mutableListOf<MixCandidate>()
        for (candidate in candidates) {
            val isrc = candidate.isrc?.trim()?.lowercase()?.takeIf { it.isNotBlank() }
            if (isrc != null) {
                if (!seenIsrc.add(isrc)) continue
            }
            if (!seenNorm.add(candidate.normKey)) continue
            out.add(candidate)
        }
        return out
    }

    fun applyDiversityCaps(
        candidates: List<MixCandidate>,
        maxPerArtist: Int,
        maxPerAlbum: Int
    ): List<MixCandidate> {
        val artistCounts = mutableMapOf<String, Int>()
        val albumCounts = mutableMapOf<String, Int>()
        val out = mutableListOf<MixCandidate>()
        for (candidate in candidates) {
            val artistKey = candidate.artist.lowercase()
            val albumKey = "${candidate.album.orEmpty().lowercase()}|$artistKey"
            val artistCount = artistCounts[artistKey] ?: 0
            val albumCount = albumCounts[albumKey] ?: 0
            if (artistCount >= maxPerArtist) continue
            if (candidate.album != null && albumCount >= maxPerAlbum) continue
            artistCounts[artistKey] = artistCount + 1
            albumCounts[albumKey] = albumCount + 1
            out.add(candidate)
        }
        return out
    }
}
