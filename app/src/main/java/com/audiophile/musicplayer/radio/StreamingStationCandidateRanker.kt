package com.audiophile.musicplayer.radio

import com.audiophile.musicplayer.data.source.SourceSearchResult
import com.audiophile.musicplayer.data.source.VariantClassifier
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
        "top electronic", "edm tribe", "electronic songs",
        "artists to listen", "billboard hot", "muchmusic",
        "full body workout", "workout routine", "choreography",
        "prod by", "prod. by", "produced by", "type beat", "beat tape",
        "sample pack", "drum kit", "loop kit", "loop pack", "instrumental kit",
        "party anthem mix", "club mix", "dance mix", "hype mix"
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

        // Tabata / "in N Styles" / fitness packs — never Song Radio material
        if (VariantClassifier.isWorkoutOrStylePackVariant(result.title, result.artist, result.album)) {
            return true
        }

        if (conflictsWithGenre(seed, result)) return true
        if (isTitleOnlyGenreMatch(seed, result)) return true
        if (genreRadioTitlePattern.matches(result.title.trim())) return true
        // Genre/mood branding only — Song Radio displayName is "<seed title> Radio"
        // and must not reject the seed track itself.
        if (!SongRadioRelatedness.isSongRadioKind(seed.kind) &&
            isStationBrandingTitle(result.title, seed)
        ) {
            return true
        }

        if (SongRadioRelatedness.isListicleOrCompilationAlbum(result.title, result.artist, result.album)) {
            return true
        }
        if (SongRadioRelatedness.isSongRadioKind(seed.kind) &&
            SongRadioRelatedness.isCoverActArtist(result.artist)
        ) {
            return true
        }

        if (SongRadioRelatedness.isSongRadioKind(seed.kind)) {
            // Weeknd → Weekend lexical collapse, foreign covers, weak title-token spam
            if (SongRadioRelatedness.isArtistNameCollision(seed.seedArtist, result.title, result.artist, result.album)) {
                return true
            }
            if (SongRadioRelatedness.isSeedArtistMentionedByOtherArtist(
                    seed.seedArtist,
                    result.title,
                    result.artist
                )
            ) {
                return true
            }
            if (SongRadioRelatedness.isWeakTitleTokenSpam(
                    seed.seedTitle,
                    seed.seedArtist,
                    result.title,
                    result.artist
                )
            ) {
                return true
            }
            if (SongRadioRelatedness.isForeignHitCover(
                    seed.seedTitle,
                    result.title,
                    result.artist,
                    seed.seedArtist
                )
            ) {
                return true
            }
            val classification = VariantClassifier.classify(result.title, result.artist, result.album)
            if (classification.variantType == VariantClassifier.VariantType.COVER) {
                return true
            }
        }

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
        taste: StreamingStationTasteSignals,
        steerGenome: com.audiophile.musicplayer.radio.genome.MusicGenomeVector? = null,
        discoveryMode: RadioDiscoveryMode = RadioDiscoveryMode.HYBRID_MIX
    ): Double {
        var score = 40.0
        val title = result.title.lowercase()
        val artist = result.artist.lowercase()
        val album = result.album?.lowercase().orEmpty()
        val haystack = "$title $artist $album"

        // Song radio: never boost raw title/query tokens (Weekend / Lights collapse).
        // Prefer full artist + exact title identity only.
        if (!SongRadioRelatedness.isSongRadioKind(seed.kind)) {
            val scoreBoostWords = buildScoreBoostWords(seed)
            scoreBoostWords.forEach { word ->
                if (haystack.contains(word)) score += 8.0
            }
        }

        seed.seedArtists.forEach { seedArtist ->
            val key = seedArtist.lowercase()
            if (artist == key) score += 28.0
            else if (SongRadioRelatedness.artistsMatch(key, artist)) score += 12.0
        }

        seed.seedArtist?.let { seedArtist ->
            val key = seedArtist.lowercase()
            if (artist == key) score += 35.0
            else if (SongRadioRelatedness.artistsMatch(key, artist)) score += 14.0
            else if (SongRadioRelatedness.isSongRadioKind(seed.kind)) {
                // Non-seed artists are discovery — mild demotion vs seed artist
                score -= 4.0
            }
        }

        seed.seedTitle?.let { seedTitle ->
            val key = seedTitle.lowercase()
            if (title == key) score += 30.0
            else if (title.contains(key) && SongRadioRelatedness.artistsMatch(seed.seedArtist.orEmpty(), artist)) {
                score += 10.0
            }
        }

        if (result.isrc?.isNotBlank() == true) score += 10.0
        if (result.artworkUrl?.startsWith("http") == true) score += 6.0
        if (result.durationMs != null && result.durationMs in 120_000L..420_000L) score += 5.0

        score += taste.artistPenalty(result.artist).toDouble()
        score += StreamingEraFilter.eraFitScore(seed, result.title, result.artist, result.album).toDouble()

        softPenaltyMarkers.forEach { marker ->
            if (title.contains(marker)) score -= 12.0
        }

        // Vibe/word-echo penalty: for non-song stations a title that literally
        // repeats the mood/genre keyword ("Party Time", "Country Hits") is a
        // lexical match, not a musical fit. Real catalogue songs with their own
        // identity must not be beaten by titles that merely echo the seed word.
        score += titleEchoPenalty(seed, result.title)

        if (title.contains("cover")) score -= 18.0

        // RadioDiscoveryMode preference
        when (discoveryMode) {
            RadioDiscoveryMode.MY_FAVORITES -> {
                val isFavorite = taste.favoriteArtists.any { SongRadioRelatedness.artistsMatch(it, result.artist) }
                if (isFavorite) score += 60.0 else score -= 35.0
            }
            RadioDiscoveryMode.HYBRID_MIX -> {
                val isFavorite = taste.favoriteArtists.any { SongRadioRelatedness.artistsMatch(it, result.artist) }
                if (isFavorite) score += 25.0
            }
            RadioDiscoveryMode.DEEP_DISCOVERY -> {
                val isFavorite = taste.favoriteArtists.any { SongRadioRelatedness.artistsMatch(it, result.artist) }
                if (isFavorite) {
                    score -= 150.0
                } else {
                    score += 25.0
                }
            }
        }

        // Musical Genome Similarity & Pandora Station Mode.
        // When the live station genome (thumb-steered by the Genome engine) is
        // supplied it replaces the static seed vector, so thumbs actually move
        // the refill ranking instead of being discarded.
        val seedGenre = seed.hintKeywords.firstOrNull()
            ?: if (seed.kind == StreamingStationKind.GENRE) seed.displayName else null
        val referenceVector = steerGenome ?: com.audiophile.musicplayer.radio.genome.MusicGenomeExtractor.extract(
            title = seed.seedTitle ?: seed.displayName,
            artist = seed.seedArtist ?: seed.seedArtists.firstOrNull().orEmpty(),
            genre = seedGenre
        )
        val candidateVector = com.audiophile.musicplayer.radio.genome.MusicGenomeExtractor.extract(result)
        val genomeSim = referenceVector.cosineSimilarity(candidateVector)
        score += (genomeSim * 25.0)

        // Thumb feedback is sticky through the steered vector itself: the engine
        // has already pushed currentStationGenome toward likes and away from
        // dislikes, so similarity to it *is* the steering signal.

        when (seed.genomeMode) {
            com.audiophile.musicplayer.radio.genome.PandoraStationMode.CHILL -> {
                score += (candidateVector.acousticWeight * 20.0)
                score -= (candidateVector.energyLevel * 25.0)
                if (candidateVector.tempoBpmNorm < 0.45f) score += 15.0
            }
            com.audiophile.musicplayer.radio.genome.PandoraStationMode.UPBEAT -> {
                score += (candidateVector.energyLevel * 25.0)
                score += (candidateVector.danceability * 20.0)
                if (candidateVector.tempoBpmNorm > 0.48f) score += 15.0
            }
            com.audiophile.musicplayer.radio.genome.PandoraStationMode.DISCOVERY -> {
                val isSeedArtist = seed.seedArtists.any { SongRadioRelatedness.artistsMatch(it, result.artist) } ||
                    SongRadioRelatedness.artistsMatch(seed.seedArtist.orEmpty(), result.artist)
                if (isSeedArtist) {
                    score -= 30.0
                } else {
                    score += 15.0
                }
            }
            com.audiophile.musicplayer.radio.genome.PandoraStationMode.CROWD_FAVES -> {
                val isSeedArtist = seed.seedArtists.any { SongRadioRelatedness.artistsMatch(it, result.artist) } ||
                    SongRadioRelatedness.artistsMatch(seed.seedArtist.orEmpty(), result.artist)
                if (isSeedArtist) score += 20.0
                score += (candidateVector.danceability * 12.0)
            }
            com.audiophile.musicplayer.radio.genome.PandoraStationMode.DEEP_CUTS -> {
                if (title.contains("feat.") || title.contains("remix")) score += 8.0
            }
            else -> {}
        }

        return score
    }

    private fun buildScoreBoostWords(seed: StreamingStationSeed): List<String> {
        val fromArtists = seed.seedArtists.map { it.lowercase() }
        // Mood/activity/genre hints are vibe labels ("party", "chill", "workout"),
        // not title terms. Boosting the literal substring picks "Party Kit",
        // "Deep Heat Essentials / Ibiza Dance Party" compilations, and other
        // query-collision junk over real music. Vibe fit comes from genome
        // similarity + the station-mode bonuses instead.
        val excludesHintBoost = seed.kind in setOf(
            StreamingStationKind.MOOD,
            StreamingStationKind.ACTIVITY,
            StreamingStationKind.GENRE,
            StreamingStationKind.JUKEBOX_PRESET
        )
        val fromHints = if (excludesHintBoost) {
            emptyList()
        } else {
            seed.hintKeywords.map { it.lowercase() }.filter { it.length >= 4 }
        }
        // Intentionally omit queryPhrases token splits — they caused lexical collapse.
        return (fromArtists + fromHints)
            .filter { it !in genericGenreTokens }
            .distinct()
    }

    fun rankCandidates(
        results: List<SourceSearchResult>,
        seed: StreamingStationSeed,
        taste: StreamingStationTasteSignals,
        seenNormKeys: Set<String>,
        artistCounts: Map<String, Int>,
        maxPerArtist: Int = 2,
        seedArtistShareCap: Double = 0.25,
        steerGenome: com.audiophile.musicplayer.radio.genome.MusicGenomeVector? = null,
        discoveryMode: RadioDiscoveryMode = RadioDiscoveryMode.HYBRID_MIX
    ): List<SourceSearchResult> {
        val artistTally = artistCounts.toMutableMap()
        val seedArtistTitles = if (SongRadioRelatedness.isSongRadioKind(seed.kind)) {
            results
                .filter { SongRadioRelatedness.artistsMatch(seed.seedArtist.orEmpty(), it.artist) }
                .map { SongRadioRelatedness.normalizePhrase(it.title) }
                .filter { it.isNotBlank() }
                .toMutableSet()
                .also { set ->
                    seed.seedTitle?.let { title ->
                        val norm = SongRadioRelatedness.normalizePhrase(title)
                        if (norm.isNotBlank()) set += norm
                    }
                }
        } else {
            emptySet()
        }

        val ranked = results
            .filter { !isStationJunk(it, seed) }
            .filter {
                val key = normalizeCandidateKey(it.title, it.artist)
                key !in seenNormKeys
            }
            .filter {
                if (!SongRadioRelatedness.isSongRadioKind(seed.kind)) return@filter true
                if (SongRadioRelatedness.artistsMatch(seed.seedArtist.orEmpty(), it.artist)) return@filter true
                // Non-seed artist performing a seed-artist title = cover/reupload.
                SongRadioRelatedness.normalizePhrase(it.title) !in seedArtistTitles
            }
            .filter {
                if (!SongRadioRelatedness.isSongRadioKind(seed.kind)) return@filter true
                !SongRadioRelatedness.isCoverActArtist(it.artist)
            }
            .distinctBy { "${it.providerId}:${it.id}" }
            .distinctBy { normalizeCandidateKey(it.title, it.artist) }
            .sortedByDescending { scoreCandidate(it, seed, taste, steerGenome, discoveryMode) }

        val seedArtistKey = seed.seedArtist?.trim()?.lowercase().orEmpty()
        val accepted = mutableListOf<SourceSearchResult>()
        var seedArtistAccepted = artistTally.entries
            .filter { SongRadioRelatedness.artistsMatch(it.key, seedArtistKey) }
            .sumOf { it.value }

        for (candidate in ranked) {
            val artistKey = candidate.artist.trim().lowercase()
            val currentCount = artistTally[artistKey] ?: 0
            if (artistKey.isNotBlank() && currentCount >= maxPerArtist) continue

            if (SongRadioRelatedness.isSongRadioKind(seed.kind) &&
                seedArtistKey.isNotBlank() &&
                SongRadioRelatedness.artistsMatch(artistKey, seedArtistKey)
            ) {
                val nextTotal = accepted.size + 1
                val nextSeedShare = (seedArtistAccepted + 1).toDouble() / nextTotal.toDouble()
                if (accepted.size >= 3 && nextSeedShare > seedArtistShareCap) continue
            }

            accepted.add(candidate)
            if (artistKey.isNotBlank()) {
                artistTally[artistKey] = currentCount + 1
            }
            if (seedArtistKey.isNotBlank() && SongRadioRelatedness.artistsMatch(artistKey, seedArtistKey)) {
                seedArtistAccepted++
            }
        }
        return accepted
    }

    fun isSongRadioKind(seed: StreamingStationSeed): Boolean =
        SongRadioRelatedness.isSongRadioKind(seed.kind)

    /** Penalty for titles that literally repeat a mood/genre keyword. */
    fun titleEchoPenalty(seed: StreamingStationSeed, title: String): Double {
        if (SongRadioRelatedness.isSongRadioKind(seed.kind)) return 0.0
        val titleTokens = title.lowercase().split(Regex("""\s+""")).map { it.trim() }
        val echoWords = seed.hintKeywords.map { it.lowercase().trim() }
            .filter { it.length >= 3 && it !in genericGenreTokens }
        return if (echoWords.any { word -> titleTokens.any { it == word } }) -18.0 else 0.0
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
