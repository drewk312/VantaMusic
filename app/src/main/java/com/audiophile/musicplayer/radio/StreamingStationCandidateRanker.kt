package com.audiophile.musicplayer.radio

import com.audiophile.musicplayer.data.source.SourceSearchResult
import com.audiophile.musicplayer.data.source.isLikelyMusicTrack
import com.audiophile.musicplayer.data.source.isPlaylistCompilationArtifact
import com.audiophile.musicplayer.data.source.isBroadcastLikeMetadata

/**
 * Ranks catalog search hits before expensive stream resolution.
 * Filters station junk (covers, karaoke, tribute acts) and boosts on-theme tracks.
 */
object StreamingStationCandidateRanker {

    private val junkTitleMarkers = listOf(
        "karaoke", "tribute to", "tribute act", "made famous by", "made popular by",
        "originally performed by", "in the style of", "as made famous by",
        "8-bit", "8 bit", "chip tune", "chiptune", "midi version",
        "piano cover", "violin cover", "guitar cover", "string quartet",
        "vitamin string", "kids bop", "kidz bop", "lullaby version",
        "white noise", "brown noise", "study beats", "lofi beats",
        "sped up", "slowed + reverb", "slowed and reverb", "nightcore",
        "tiktok version", "reels version",
        "top electronic", "edm tribe", "electronic songs"
    )

    private val softPenaltyMarkers = listOf(
        "remix", "mix", "edit", "rework", "re-recorded", "re recorded",
        "live at", "live from", "live in", "live version", "(live)",
        "acoustic version", "acoustic cover", "unplugged",
        "demo", "rough mix", "instrumental"
    )

    private val genericGenreTokens = setOf(
        "country", "americana", "rock", "pop", "jazz", "blues", "metal", "punk",
        "electronic", "edm", "house", "techno", "hip", "hop", "rap", "soul", "funk",
        "indie", "alternative", "folk", "disco", "lofi", "chillhop", "radio", "music"
    )

    private val genreRadioTitlePattern = Regex("""(?i)^[\w\s&']+\s+radio$""")

    fun isStationJunk(result: SourceSearchResult, seed: StreamingStationSeed): Boolean {
        if (!result.isLikelyMusicTrack()) return true
        if (isBroadcastLikeMetadata(result.title, result.artist, result.album)) return true
        if (isPlaylistCompilationArtifact(result.title, result.artist, result.album, result.durationMs)) {
            return true
        }

        val title = result.title.lowercase()
        val artist = result.artist.lowercase()
        val combined = "$title $artist"

        if (junkTitleMarkers.any { combined.contains(it) }) return true

        if (conflictsWithGenre(seed, result)) return true
        if (isTitleOnlyGenreMatch(seed, result)) return true
        if (genreRadioTitlePattern.matches(result.title.trim())) return true
        if (isStationBrandingTitle(result.title, seed)) return true

        if (seed.kind != StreamingStationKind.SONG && seed.kind != StreamingStationKind.SONG_SIMILAR) {
            val seedArtist = seed.seedArtist?.lowercase().orEmpty()
            if (seedArtist.isNotBlank() && artist != seedArtist && title.contains(seedArtist) && artist.contains("cover")) {
                return true
            }
        }

        return false
    }

    private fun conflictsWithGenre(seed: StreamingStationSeed, result: SourceSearchResult): Boolean {
        if (seed.kind != StreamingStationKind.GENRE &&
            seed.kind != StreamingStationKind.JUKEBOX_PRESET &&
            seed.kind != StreamingStationKind.MOOD
        ) {
            return false
        }
        val haystack = "${result.title} ${result.artist} ${result.album.orEmpty()}".lowercase()
        return GenreStationSeeds.conflictMarkersFor(seed.hintKeywords).any { haystack.contains(it) }
    }

    private fun isTitleOnlyGenreMatch(seed: StreamingStationSeed, result: SourceSearchResult): Boolean {
        if (seed.kind != StreamingStationKind.GENRE && seed.kind != StreamingStationKind.JUKEBOX_PRESET) {
            return false
        }
        if (seed.seedArtists.isEmpty()) return false

        val title = result.title.lowercase().trim()
        val artist = result.artist.lowercase().trim()
        val matchesSeedArtist = seed.seedArtists.any { seedArtist ->
            val key = seedArtist.lowercase()
            artist == key || artist.contains(key) || key.contains(artist)
        }
        if (matchesSeedArtist) return false

        val genreTerms = (seed.hintKeywords + listOf(seed.primaryQuery))
            .map { it.lowercase().trim() }
            .filter { it.length >= 4 && it in genericGenreTokens }

        return genreTerms.any { genre ->
            title == genre ||
                title == "$genre radio" ||
                title == "$genre music" ||
                (title.contains(genre) && !title.contains("road") && artist !in seed.seedArtists.map { it.lowercase() })
        }
    }

    fun scoreCandidate(
        result: SourceSearchResult,
        seed: StreamingStationSeed,
        taste: StreamingStationTasteSignals
    ): Double {
        var score = 40.0
        val title = result.title.lowercase()
        val artist = result.artist.lowercase()
        val album = result.album?.lowercase().orEmpty()
        val haystack = "$title $artist $album"

        val scoreBoostWords = buildScoreBoostWords(seed)
        scoreBoostWords.forEach { word ->
            if (haystack.contains(word)) score += 8.0
        }

        seed.seedArtists.forEach { seedArtist ->
            val key = seedArtist.lowercase()
            if (artist == key) score += 28.0
            else if (artist.contains(key) || key.contains(artist)) score += 12.0
        }

        seed.seedArtist?.let { seedArtist ->
            val key = seedArtist.lowercase()
            if (artist == key) score += 35.0
            else if (artist.contains(key) || key.contains(artist)) score += 14.0
        }

        seed.seedTitle?.let { seedTitle ->
            val key = seedTitle.lowercase()
            if (title == key) score += 30.0
            else if (title.contains(key)) score += 10.0
        }

        if (result.isrc?.isNotBlank() == true) score += 10.0
        if (result.artworkUrl?.startsWith("http") == true) score += 6.0
        if (result.durationMs != null && result.durationMs in 120_000L..420_000L) score += 5.0

        score += taste.artistPenalty(result.artist).toDouble()
        score += StreamingEraFilter.eraFitScore(seed, result.title, result.artist, result.album).toDouble()

        softPenaltyMarkers.forEach { marker ->
            if (title.contains(marker)) score -= 12.0
        }

        if (title.contains("cover") && seed.kind != StreamingStationKind.SONG) score -= 18.0

        return score
    }

    private fun buildScoreBoostWords(seed: StreamingStationSeed): List<String> {
        val fromPhrases = seed.queryPhrases
            .flatMap { it.lowercase().split(Regex("""\s+""")) }
            .filter { it.length >= 4 }
        val fromArtists = seed.seedArtists.map { it.lowercase() }
        val fromHints = if (seed.kind == StreamingStationKind.GENRE || seed.kind == StreamingStationKind.JUKEBOX_PRESET) {
            emptyList()
        } else {
            seed.hintKeywords.map { it.lowercase() }.filter { it.length >= 4 }
        }
        return (fromPhrases + fromArtists + fromHints)
            .filter { it !in genericGenreTokens }
            .distinct()
    }

    fun rankCandidates(
        results: List<SourceSearchResult>,
        seed: StreamingStationSeed,
        taste: StreamingStationTasteSignals,
        seenNormKeys: Set<String>,
        artistCounts: Map<String, Int>,
        maxPerArtist: Int = 2
    ): List<SourceSearchResult> {
        val artistTally = artistCounts.toMutableMap()
        return results
            .asSequence()
            .filter { !isStationJunk(it, seed) }
            .filter {
                val key = normalizeCandidateKey(it.title, it.artist)
                key !in seenNormKeys
            }
            .filter {
                val artistKey = it.artist.trim().lowercase()
                artistKey.isBlank() || (artistTally[artistKey] ?: 0) < maxPerArtist + 1
            }
            .distinctBy { "${it.providerId}:${it.id}" }
            .distinctBy { normalizeCandidateKey(it.title, it.artist) }
            .sortedByDescending { scoreCandidate(it, seed, taste) }
            .toList()
    }

    fun normalizeCandidateKey(title: String, artist: String): String =
        "${title.lowercase().trim()}|${artist.lowercase().trim()}"
            .replace(Regex("""[^\p{L}\p{N}|]+"""), "")

    private fun isStationBrandingTitle(title: String, seed: StreamingStationSeed): Boolean {
        val normalizedTitle = title.lowercase().trim()
        if (normalizedTitle.isBlank()) return false

        val display = seed.displayName.lowercase().trim()
        if (normalizedTitle == display) return true

        if (display.endsWith(" radio")) {
            val genreLabel = display.removeSuffix(" radio").trim()
            if (normalizedTitle == genreLabel || normalizedTitle == display) return true
            if (normalizedTitle == "$genreLabel radio") return true
        }

        val playlistMarkers = listOf(
            "top electronic", "edm tribe", "playlist", "mix 20", "best of 20",
            "hits 20", "chart hits", "viral hits"
        )
        if (playlistMarkers.any { normalizedTitle.contains(it) }) return true

        return false
    }
}
