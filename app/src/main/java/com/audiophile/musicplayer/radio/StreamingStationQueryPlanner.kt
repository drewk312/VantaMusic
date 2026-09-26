package com.audiophile.musicplayer.radio

/**
 * Expands a [StreamingStationSeed] into diverse catalog search queries.
 * Prioritizes seed-artist lookups and genre-specific patterns for better station quality.
 */
object StreamingStationQueryPlanner {

    fun planInitialQueries(
        seed: StreamingStationSeed,
        taste: StreamingStationTasteSignals = StreamingStationTasteSignals()
    ): List<String> {
        val base = linkedSetOf<String>()

        seed.seedArtists.take(8).forEach { artist ->
            if (artist.isNotBlank()) {
                base += artist.trim()
            }
        }
        seed.seedArtists.take(8).forEach { artist ->
            if (artist.isNotBlank()) {
                base += "$artist top songs"
                base += "$artist greatest hits"
            }
        }

        when (seed.kind) {
            StreamingStationKind.SONG_SIMILAR -> {
                val title = seed.seedTitle.orEmpty()
                val artist = seed.seedArtist.orEmpty()
                if (artist.isNotBlank()) {
                    // Artist / similarity anchors only — never bare title tokens.
                    base += artist
                    base += "$artist top songs"
                    base += "artists like $artist"
                    base += "bands like $artist"
                    base += "$artist similar artists"
                    base += "$artist deep cuts"
                    base += "$artist essential songs"
                    if (title.isNotBlank()) {
                        base += "$artist $title"
                    }
                }
            }
            StreamingStationKind.SONG -> {
                val title = seed.seedTitle.orEmpty()
                val artist = seed.seedArtist.orEmpty()
                if (artist.isNotBlank()) {
                    base += artist
                    base += "$artist top songs"
                    base += "artists like $artist"
                    base += "$artist similar artists"
                    base += "$artist deep cuts"
                    base += "$artist album tracks"
                    if (title.isNotBlank()) {
                        // Exact identity for seed recall — not "songs like $title" OR-token search.
                        base += "$artist $title"
                    }
                }
            }
            StreamingStationKind.ARTIST -> {
                val artist = seed.seedArtist.orEmpty().ifBlank { seed.primaryQuery }
                base += artist
                base += "$artist essential songs"
                base += "$artist deep cuts"
                base += "artists like $artist"
                base += "bands similar to $artist"
            }
            StreamingStationKind.ERA -> {
                appendEraQueries(base, seed)
            }
            StreamingStationKind.GENRE, StreamingStationKind.MOOD, StreamingStationKind.ACTIVITY -> {
                appendGenreMoodQueries(base, seed)
            }
            else -> {
                if (VibeTranslator.isPersonalTastePrompt(seed.primaryQuery)) {
                    appendTasteAnchorQueries(base, taste)
                }
                val vibeSeeds = VibeTranslator.extractSearchQueries(seed.primaryQuery)
                vibeSeeds.forEach { vibeSeed ->
                    expandFreeTextQueries(vibeSeed, seed.hintKeywords).forEach { base += it }
                }
            }
        }

        seed.queryPhrases.filter { it.isNotBlank() }.forEach { phrase ->
            if (seed.kind == StreamingStationKind.FREE_TEXT) {
                return@forEach
            }
            // Song radio: never expand title phrases into free-text token searches.
            if (SongRadioRelatedness.isSongRadioKind(seed.kind)) {
                val artist = seed.seedArtist.orEmpty()
                val lower = phrase.lowercase()
                if (lower.startsWith("songs like") || lower.contains(" similar ")) {
                    return@forEach
                }
                if (artist.isNotBlank() && lower.contains(artist.lowercase())) {
                    base += phrase.trim()
                }
                return@forEach
            }
            if (seed.seedArtists.isNotEmpty() &&
                seed.hintKeywords.any { phrase.equals(it, ignoreCase = true) }
            ) {
                return@forEach
            }
            expandFreeTextQueries(phrase, seed.hintKeywords).forEach { base += it }
        }

        if (seed.eraStart != null && seed.eraEnd != null && seed.kind != StreamingStationKind.ERA) {
            val short = (seed.eraStart % 100).toString().padStart(2, '0')
            val eraTail = seed.hintKeywords.firstOrNull().orEmpty()
            if (eraTail.isNotBlank()) {
                base += "${short}s $eraTail essentials"
                base += "${short}s $eraTail hits"
                base += "best ${short}s $eraTail"
            }
        }

        return StationQuerySanitizer.filterSearchQueries(
            prioritizeQueries(base.toList(), seed)
        ).take(28)
    }

    fun planExpansionQueries(
        seed: StreamingStationSeed,
        discoveredArtists: Collection<String>,
        pass: Int,
        taste: StreamingStationTasteSignals = StreamingStationTasteSignals()
    ): List<String> {
        val queries = linkedSetOf<String>()
        val genreUsesArtistSeeds = seed.kind == StreamingStationKind.GENRE &&
            seed.seedArtists.isNotEmpty()

        when (pass) {
            0 -> {
                if (genreUsesArtistSeeds) {
                    seed.seedArtists.take(8).forEach { artist ->
                        queries += artist
                        queries += "$artist top songs"
                        queries += "$artist greatest hits"
                    }
                } else {
                    appendPrimaryExpansionQueries(queries, seed, pass, taste)
                }
            }
            1 -> {
                discoveredArtists.distinct().take(12).forEach { artist ->
                    queries += artist
                    queries += "$artist top songs"
                    queries += "$artist best album tracks"
                }
                seed.seedArtists.take(6).forEach { artist ->
                    if (artist.isNotBlank()) queries += "$artist fan favorites"
                }
            }
            2 -> {
                if (genreUsesArtistSeeds) {
                    seed.seedArtists.take(8).forEach { artist ->
                        queries += artist
                        queries += "$artist top songs"
                        queries += "$artist greatest hits"
                    }
                }
                discoveredArtists.distinct().take(8).forEach { artist ->
                    queries += "artists like $artist"
                }
                appendPrimaryExpansionQueries(queries, seed, pass, taste)
            }
            else -> {
                if (genreUsesArtistSeeds) {
                    seed.seedArtists.take(8).forEach { artist ->
                        queries += artist
                        queries += "$artist deep cuts"
                    }
                }
                discoveredArtists.distinct().take(6).forEach { artist ->
                    queries += "$artist deep cuts"
                }
                appendPrimaryExpansionQueries(queries, seed, pass, taste)
            }
        }

        return StationQuerySanitizer.filterSearchQueries(queries).take(20)
    }

    private fun appendPrimaryExpansionQueries(
        queries: LinkedHashSet<String>,
        seed: StreamingStationSeed,
        pass: Int,
        taste: StreamingStationTasteSignals
    ) {
        val anchors = expansionAnchors(seed, taste)
        if (anchors.isEmpty()) return

        if (seed.kind == StreamingStationKind.FREE_TEXT) {
            anchors.take(5).forEach { anchor ->
                when (pass) {
                    0 -> expandFreeTextQueries(anchor, seed.hintKeywords).take(3).forEach { queries += it }
                    2 -> {
                        queries += "$anchor hidden gems"
                        queries += "$anchor album tracks"
                        queries += "underrated $anchor songs"
                    }
                    else -> {
                        queries += "$anchor deep cuts"
                        queries += "$anchor album tracks"
                        queries += "underrated $anchor"
                    }
                }
            }
            return
        }

        val primary = anchors.first()
        when (pass) {
            0 -> {
                queries += "$primary essentials"
                queries += "best $primary songs"
                queries += "$primary deep cuts"
                queries += "discover $primary"
            }
            2 -> {
                queries += "$primary hidden gems"
                queries += "$primary album tracks"
            }
            else -> {
                queries += "$primary deep album cuts"
            }
        }
    }

    private fun expansionAnchors(seed: StreamingStationSeed, taste: StreamingStationTasteSignals): List<String> {
        if (seed.kind == StreamingStationKind.FREE_TEXT) {
            if (VibeTranslator.isPersonalTastePrompt(seed.primaryQuery)) {
                val tasteAnchors = tasteAnchorTerms(taste)
                if (tasteAnchors.isNotEmpty()) return tasteAnchors
            }
            return VibeTranslator.extractSearchQueries(seed.primaryQuery)
                .map { StationQuerySanitizer.cleanStationMeta(it) }
                .filter { it.isNotBlank() && !StationQuerySanitizer.isStationMetaPhrase(it) }
                .distinct()
        }

        return listOf(StationQuerySanitizer.cleanStationMeta(seed.primaryQuery))
            .filter { it.isNotBlank() && !StationQuerySanitizer.isStationMetaPhrase(it) }
    }

    private fun appendEraQueries(base: LinkedHashSet<String>, seed: StreamingStationSeed) {
        val label = seed.displayName
        base += label
        base += "$label hits"
        base += "$label essentials"
        seed.hintKeywords.filter { it.isNotBlank() && !StationQuerySanitizer.isStationMetaPhrase(it) }.forEach {
            base += it
            base += "$it classics"
        }
        seed.eraStart?.let { start ->
            val end = seed.eraEnd ?: start + 9
            val short = (start % 100).toString().padStart(2, '0')
            base += "${short}s greatest hits"
            base += "${short}s rock hits"
            if (start == 1950) {
                base += "${short}s rock and roll"
                base += "${short}s rockabilly"
                base += "${short}s doo wop"
                base += "${short}s rhythm and blues"
                base += "${short}s classics"
            } else {
                base += "${short}s pop hits"
                base += "${short}s r&b hits"
                if (start >= 1980) {
                    base += "${short}s alternative hits"
                }
            }
            var year = start
            while (year <= end) {
                base += "$year hit songs"
                year += 2
            }
        }
    }

    private fun appendGenreMoodQueries(base: LinkedHashSet<String>, seed: StreamingStationSeed) {
        val hints = seed.hintKeywords.filter { it.isNotBlank() }
        val hasArtistSeeds = seed.seedArtists.isNotEmpty()

        if (hasArtistSeeds) {
            seed.seedArtists.take(8).forEach { artist ->
                base += artist
                base += "$artist top songs"
                base += "$artist greatest hits"
                base += "$artist deep cuts"
            }
        }

        if (!hasArtistSeeds) {
            val primary = StationQuerySanitizer.cleanStationMeta(seed.primaryQuery)
            if (primary.isNotBlank()) {
                base += primary
                base += "$primary essentials"
                base += "$primary classics"
                base += "official $primary tracks"
                base += "$primary studio recordings"
            }
            hints.take(4).forEach { hint ->
                val cleanHint = StationQuerySanitizer.cleanStationMeta(hint)
                if (cleanHint.isBlank()) return@forEach
                base += cleanHint
                base += "$cleanHint essentials"
                base += "$cleanHint official"
            }
        }

        if (seed.eraStart != null && seed.eraEnd != null) {
            appendEraQueries(base, seed)
        }
    }

    private fun appendTasteAnchorQueries(base: LinkedHashSet<String>, taste: StreamingStationTasteSignals) {
        taste.favoriteArtists
            .map { it.trim() }
            .filter { it.length >= 2 }
            .take(8)
            .forEach { artist ->
                base += artist
                base += "$artist top songs"
                base += "$artist essential songs"
                base += "artists like $artist"
            }

        taste.genreAffinities.entries
            .sortedByDescending { it.value }
            .map { it.key.trim() }
            .filter { it.length >= 2 && !StationQuerySanitizer.isStationMetaPhrase(it) }
            .take(5)
            .forEach { genre ->
                base += genre
                base += "$genre essentials"
                base += "best $genre songs"
            }
    }

    private fun tasteAnchorTerms(taste: StreamingStationTasteSignals): List<String> {
        val artists = taste.favoriteArtists
            .map { it.trim() }
            .filter { it.length >= 2 }
        val genres = taste.genreAffinities.entries
            .sortedByDescending { it.value }
            .map { it.key.trim() }
            .filter { it.length >= 2 && !StationQuerySanitizer.isStationMetaPhrase(it) }
        return (artists + genres).distinct()
    }

    private fun prioritizeQueries(queries: List<String>, seed: StreamingStationSeed): List<String> {
        val seedArtists = seed.seedArtists.map { it.lowercase() }.toSet()
        val seedArtist = seed.seedArtist?.lowercase()
        return queries
            .map { it.trim() }
            .filter { it.length >= 2 }
            .distinct()
            .sortedByDescending { query ->
                val lower = query.lowercase()
                when {
                    seedArtist != null && lower == seedArtist -> 100
                    seedArtists.contains(lower) -> 90
                    seedArtists.any { lower.startsWith(it) || lower.contains(" $it") } -> 80
                    lower.contains("essential") || lower.contains("greatest") || lower.contains("top songs") -> 60
                    lower.contains("deep cut") || lower.contains("similar") -> 50
                    lower.contains("radio") || lower.contains("playlist") -> 20
                    else -> 40
                }
            }
    }

    private fun expandFreeTextQueries(phrase: String, hints: List<String>): List<String> {
        val clean = StationQuerySanitizer.cleanStationMeta(phrase.trim())
        if (clean.isBlank()) return emptyList()
        
        // 1. Map known multi-word vibes first
        val lowerPhrase = clean.lowercase()
        for ((vibe, genres) in MULTI_WORD_VIBE_MAPPING) {
            if (lowerPhrase.contains(vibe)) {
                val out = linkedSetOf<String>()
                genres.forEach { genre ->
                    out += genre
                    out += "best $genre"
                    out += "$genre essentials"
                }
                // Also include hints if they don't overlap too much
                hints.filter { it.isNotBlank() && !StationQuerySanitizer.isStationMetaPhrase(it) }.take(2).forEach { out += "$it $phrase" }
                return out.toList()
            }
        }

        val out = linkedSetOf(
            clean,
            "$clean essentials",
            "$clean classics",
            "best $clean songs",
            "$clean hits"
        )
        
        val vibeWords = clean.lowercase().split("\\s+".toRegex())
        val mappedGenres = vibeWords.flatMap { VIBE_MAPPING[it] ?: emptyList() }.distinct()
        
        mappedGenres.forEach { genre ->
            out += genre
            out += "best $genre"
            out += "$genre essentials"
        }

        hints.filter { it.isNotBlank() && !clean.contains(it, ignoreCase = true) && !StationQuerySanitizer.isStationMetaPhrase(it) }
            .take(4)
            .forEach { hint ->
                out += "$clean $hint"
                out += "$hint $clean"
                out += "$hint essentials"
            }
        return out.toList()
    }

    private val MULTI_WORD_VIBE_MAPPING = mapOf(
        "quiet car ride" to listOf("ambient", "indie folk", "acoustic", "lo-fi", "chillout"),
        "night drive" to listOf("synthwave", "dark r&b", "deep house", "vaporwave"),
        "road trip" to listOf("classic rock", "indie pop", "90s hits", "driving rock"),
        "study session" to listOf("lo-fi beats", "ambient", "classical piano", "focus"),
        "workout pump" to listOf("trap", "heavy metal", "hardstyle", "high energy edm"),
        "dinner party" to listOf("jazz", "bossa nova", "soul classics", "lounge")
    )

    private val VIBE_MAPPING = mapOf(
        "quiet" to listOf("acoustic", "ambient", "indie folk", "chill"),
        "ride" to listOf("driving", "road trip", "synthwave", "classic rock", "cruising"),
        "drive" to listOf("driving", "road trip", "synthwave", "classic rock"),
        "driving" to listOf("driving", "road trip", "synthwave", "classic rock"),
        "workout" to listOf("gym", "high energy", "EDM", "hip hop", "hard rock"),
        "gym" to listOf("workout", "high energy", "EDM", "hip hop", "metal"),
        "study" to listOf("lo-fi", "instrumental", "classical", "focus", "ambient"),
        "party" to listOf("dance", "pop hits", "upbeat", "club", "party"),
        "chill" to listOf("lo-fi", "ambient", "indie", "r&b", "laid back"),
        "relax" to listOf("acoustic", "ambient", "lo-fi", "soft pop"),
        "sad" to listOf("heartbreak", "acoustic", "melancholy", "sad indie", "ballads"),
        "happy" to listOf("feel good", "upbeat", "pop", "summer", "happy"),
        "sleep" to listOf("ambient", "drone", "meditation", "sleep", "lullaby"),
        "focus" to listOf("lo-fi beats", "instrumental", "ambient", "classical"),
        "morning" to listOf("coffee", "acoustic", "morning", "upbeat indie"),
        "night" to listOf("late night", "r&b", "synthwave", "midnight", "dark"),
        "summer" to listOf("summer hits", "tropical house", "upbeat pop", "reggae"),
        "winter" to listOf("acoustic", "cozy", "holiday", "folk"),
        "rain" to listOf("lo-fi", "acoustic", "jazz", "rainy day")
    )
}
