package com.audiophile.musicplayer.search

import com.audiophile.musicplayer.data.canonical.CanonicalAlbum
import com.audiophile.musicplayer.data.canonical.CanonicalArtist
import com.audiophile.musicplayer.data.canonical.CanonicalPlaylist
import com.audiophile.musicplayer.data.canonical.CanonicalTrack
import com.audiophile.musicplayer.data.display.DisplayMetadataCleaner
import com.audiophile.musicplayer.data.source.SearchItemStatus
import com.audiophile.musicplayer.data.source.VariantClassifier
import com.audiophile.musicplayer.data.source.isLikelyMusicTrack

/**
 * Unified response from the search engine.
 */
data class UnifiedSearchResponse(
    val topResult: CanonicalTrack?,
    val songs: List<CanonicalTrack>,
    val albums: List<CanonicalAlbum>,
    val artists: List<CanonicalArtist>,
    val identityMatch: Boolean,
    val playlists: List<CanonicalPlaylist> = emptyList()
)

/**
 * Personal evidence that may break ties for an incomplete or ambiguous query.
 * These identities only boost real provider/library results; they never create
 * a metadata-only result or bypass source validation.
 */
data class SearchPersonalization(
    val activeTitle: String? = null,
    val activeArtist: String? = null,
    val libraryIdentityKeys: Set<String> = emptySet(),
    val recentIdentityKeys: Set<String> = emptySet()
)

/**
 * A single, cohesive, and intelligent search engine that replaces fragmented logic.
 * Consolidates intent parsing, identity scoring, heuristic ranking, and categorization.
 */
object UnifiedSearchEngine {

    /** Provider lookups normalize phrasing and common typos. */
    fun providerQuery(rawQuery: String): String {
        val trimmed = rawQuery.trim()
        val withoutBy = if (trimmed.contains(" by ", ignoreCase = true)) {
            trimmed.replace(Regex("(?i)\\s+by\\s+"), " ").trim()
        } else {
            trimmed
        }
        return normalizeTyposForProvider(withoutBy)
    }

    fun normalizeTyposForProvider(query: String): String {
        var result = query
        val replacements = listOf(
            Regex("""(?i)\blullably\b""") to "lullaby",
            Regex("""(?i)\blullabye\b""") to "lullaby",
            Regex("""(?i)\bwhisky\b""") to "whiskey",
            Regex("""(?i)\bacapella\b""") to "a cappella",
            Regex("""(?i)\bacappella\b""") to "a cappella"
        )
        for ((pattern, replacement) in replacements) {
            result = result.replace(pattern, replacement)
        }
        return result
    }

    fun fallbackProviderQueries(rawQuery: String): List<String> {
        val trimmed = rawQuery.trim()
        val list = mutableListOf<String>()
        val withoutBy = if (trimmed.contains(" by ", ignoreCase = true)) {
            trimmed.replace(Regex("(?i)\\s+by\\s+"), " ").trim()
        } else {
            trimmed
        }

        val normalized = normalizeTyposForProvider(withoutBy)
        if (!normalized.equals(withoutBy, ignoreCase = true)) {
            list.add(normalized)
        }

        if (trimmed.contains(" by ", ignoreCase = true)) {
            val parts = trimmed.split(Regex("(?i)\\s+by\\s+"), limit = 2)
            if (parts.size == 2) {
                val titlePart = normalizeTyposForProvider(parts[0].trim())
                val artistPart = parts[1].trim()
                if (titlePart.isNotBlank() && artistPart.isNotBlank()) {
                    list.add("$titlePart $artistPart")
                    list.add(titlePart)
                    list.add(artistPart)
                }
            }
        }
        return list.distinct()
    }

    /**
     * Publicly exposes scoring for a single candidate.
     */
    fun score(intent: SearchQueryIntent, track: CanonicalTrack): Evaluation {
        return scoreCandidate(intent, track)
    }

    /**
     * Publicly exposes intent parsing.
     */
    fun parse(query: String): SearchQueryIntent {
        return parseIntent(query)
    }

    /**
     * Ranks a list of tracks and returns them with their evaluations.
     */
    fun rank(intent: SearchQueryIntent, tracks: List<CanonicalTrack>): List<Pair<CanonicalTrack, Evaluation>> {
        return tracks.map { it to scoreCandidate(intent, it) }
            .sortedByDescending { it.second.finalScore }
    }

    private const val IDENTITY_THRESHOLD = 85
    private val FEAT_MARKERS = listOf(" feat ", " feat. ", " ft ", " ft. ", " featuring ", " with ")
    private val DIGIT_WORD_MAP = mapOf(
        "zero" to "0", "one" to "1", "two" to "2", "three" to "3", "four" to "4",
        "five" to "5", "six" to "6", "seven" to "7", "eight" to "8", "nine" to "9",
        "ten" to "10", "eleven" to "11", "twelve" to "12", "thirteen" to "13",
        "fourteen" to "14", "fifteen" to "15", "sixteen" to "16", "seventeen" to "17",
        "eighteen" to "18", "nineteen" to "19", "twenty" to "20", "thirty" to "30",
        "forty" to "40", "fifty" to "50", "sixty" to "60", "seventy" to "70",
        "eighty" to "80", "ninety" to "90", "hundred" to "00", "thousand" to "000"
    )

    /**
     * Primary entry point for search processing.
     */
    fun process(
        query: String,
        results: List<CanonicalTrack>,
        personalization: SearchPersonalization = SearchPersonalization()
    ): UnifiedSearchResponse {
        val normalizedQuery = normalize(query)
        val intent = resolveIntent(query, results, personalization)

        // Rank only real catalog/library hits. Search never fabricates a metadata-only
        // song that cannot subsequently resolve to audio.
        val scoredTracks = results
            .map { track ->
                val evaluation = scoreCandidate(intent, track)
                track to evaluation.copy(
                    finalScore = addScoreSafely(
                        evaluation.finalScore,
                        personalizationBonus(query, track, personalization)
                    )
                )
            }
            .sortedByDescending { it.second.finalScore }
        val bestScore = scoredTracks.firstOrNull()?.second?.finalScore ?: Int.MIN_VALUE

        // 3. Separate into categories using Surgical Strict Search (SSS)
        val songs = mutableListOf<CanonicalTrack>()
        val artists = mutableMapOf<String, CanonicalArtist>()
        val albums = mutableMapOf<String, CanonicalAlbum>()

        val seenSongKeys = mutableSetOf<String>()

        for ((track, evaluation) in scoredTracks) {
            if (!isRelevantResult(intent, track, evaluation, bestScore)) continue

            // Artist/album rails follow the resolved identity, so a song search
            // does not surface karaoke channels and unrelated cover artists.
            val artistName = track.artist.ifBlank { track.title }.trim()
            val artistKey = normalize(artistName)
            val expectedArtist = intent.primaryArtist?.let(::normalize)
            val artistRelevant = expectedArtist.isNullOrBlank() ||
                if (intent.songTitle.isNullOrBlank()) artistKey == expectedArtist
                else artistIdentityMatches(expectedArtist, artistKey)
            if (artistRelevant && artistKey.isNotBlank() && !artists.containsKey(artistKey)) {
                // Never promote a track/external recording id into an artist catalog id.
                artists[artistKey] = CanonicalArtist(
                    name = artistName,
                    id = null,
                    genre = track.genre,
                    artworkUrl = track.artworkUrl
                )
            }
            val albumTitle = track.album?.trim().orEmpty().ifBlank { track.title.trim() }
            val albumArtistName = track.artist.trim()
            val albumKey = normalize("$albumTitle|$albumArtistName")
            if (artistRelevant && albumTitle.isNotBlank() && !albums.containsKey(albumKey)) {
                // Name+artist browse until a real catalog album id exists.
                albums[albumKey] = CanonicalAlbum(
                    title = albumTitle,
                    artist = albumArtistName,
                    id = null,
                    artworkUrl = track.artworkUrl,
                    releaseYear = track.releaseYear,
                    genre = track.genre,
                    trackCount = null
                )
            }

            // Then categorize for song inclusion
            val category = detectCategory(track, normalizedQuery)
            if (category == Category.SONG) {
                if (track.isLikelyMusicTrack() || track.sourceStatus == SearchItemStatus.METADATA_ONLY) {
                    val cleanTitle = normalize(track.title)
                        .replace(Regex("""\b(remaster|remastered|deluxe|radio edit|single edit|album version|clean version|explicit version|mono|stereo)\b.*"""), "")
                        .trim()
                    val canonicalArtist = normalize(primaryArtist(track.artist))
                    val key = if (cleanTitle.isNotBlank()) "$cleanTitle|$canonicalArtist" else "${normalize(track.title)}|${normalize(track.artist)}"
                    if (seenSongKeys.add(key)) {
                        songs.add(track)
                    }
                }
            }
        }

        // Select Top Result
        val topCandidate = scoredTracks
            .filter { it.second.eligibleForTop }
            .map { it.first }
            .firstOrNull() ?: songs.firstOrNull()

        // Enrich with featured artists from the catalog row and the query.
        val topResult = topCandidate?.copy(featuredArtists = mergeFeaturedArtists(topCandidate, intent.featuredArtists))
        val enrichedSongs = songs.map { it.copy(featuredArtists = mergeFeaturedArtists(it, intent.featuredArtists)) }
        
        val topScore = scoredTracks.firstOrNull { it.first == topCandidate }?.second?.finalScore ?: 0
        val identityMatch = topScore >= IDENTITY_THRESHOLD

        return UnifiedSearchResponse(
            topResult = topResult,
            songs = enrichedSongs,
            albums = albums.values.toList().sortedByDescending { scoreText(normalizedQuery, it.title) },
            artists = artists.values.toList().sortedByDescending { scoreText(normalizedQuery, it.name) },
            identityMatch = identityMatch
        )
    }

    // --- Intent Parsing ---

    private fun parseIntent(query: String): SearchQueryIntent {
        val raw = query.trim()
        val working = normalize(raw)
        var featured = emptyList<String>()

        var queryForParsing = working
        for (marker in FEAT_MARKERS) {
            val idx = working.indexOf(marker)
            if (idx > 0) {
                val before = working.substring(0, idx).trim()
                val after = working.substring(idx + marker.length).trim()
                featured = after.split(" and ", " & ", ",").map { it.trim() }.filter { it.isNotBlank() }
                queryForParsing = before
                break
            }
        }

        // Explicit syntax is the only place parsing assigns an artist without
        // catalog evidence. Free-form text is resolved against live results in
        // [inferCatalogIntent], not a title/artist registry.
        if (raw.contains(" by ", ignoreCase = true)) {
            val parts = queryForParsing.split(Regex(" by ", RegexOption.IGNORE_CASE), limit = 2)
            return SearchQueryIntent(raw, parts[0].trim(), parts[1].trim(), featured)
        }

        val explicitBase = queryWithoutFeatureClause(raw)
        if (explicitBase.contains(" - ")) {
            val parts = explicitBase.split(" - ", limit = 2).map { it.trim() }
            if (parts.size == 2 && parts[0].isNotBlank() && parts[1].isNotBlank()) {
                return SearchQueryIntent(raw, parts[0], parts[1], featured)
            }
        }

        return SearchQueryIntent(raw, queryForParsing, null, featured)
    }

    // --- Scoring Logic ---

    private fun scoreCandidate(intent: SearchQueryIntent, track: CanonicalTrack): Evaluation {
        val title = track.title.trim()
        val artist = primaryArtist(track.artist)
        if (title.isBlank()) return Evaluation(Int.MIN_VALUE, false)

        val cleanTitle = identityTitle(title, intent.rawQuery)
        val normTitle = normalize(title)
        val normCleanTitle = normalize(cleanTitle)
        val normArtist = normalize(artist)
        val expectedTitle = intent.songTitle?.let { normalize(it) }
        val expectedArtist = intent.primaryArtist?.let { normalize(primaryArtist(it)) }

        val rawNorm = normalize(intent.rawQuery)
        val queryTokens = rawNorm.split(" ").filter { it.isNotBlank() && it !in setOf("by", "feat", "ft", "featuring", "with") }
        val titleTokens = (normTitle.split(" ") + normCleanTitle.split(" ")).filter { it.isNotBlank() }.toSet()
        val artistTokens = normArtist.split(" ").filter { it.isNotBlank() }.toSet()
        val combinedTokens = titleTokens + artistTokens
        val allQueryTokensInTrack = queryTokens.isNotEmpty() && queryTokens.all { it in combinedTokens }
        val fuzzyQueryTokensMatch = queryTokens.isNotEmpty() && queryTokens.all { qTok ->
            combinedTokens.any { cTok ->
                cTok == qTok || (qTok.length >= 4 && cTok.length >= 4 && levenshteinDistance(qTok, cTok) <= 1)
            }
        }
        val titleSim = expectedTitle?.let { maxOf(textSimilarity(it, normTitle), textSimilarity(it, normCleanTitle)) } ?: 0.0
        val fuzzyTitleMatch = expectedTitle != null && (
            titleSim >= 0.70 ||
            expectedTitle.split(" ").filter { it.length >= 3 }.all { eTok ->
                titleTokens.any { tTok ->
                    tTok == eTok || (eTok.length >= 4 && tTok.length >= 4 && levenshteinDistance(eTok, tTok) <= 1)
                }
            }
        )
        val combinedArtistTitle = "$normArtist $normTitle"
        val combinedTitleArtist = "$normTitle $normArtist"
        val strictCombinedMatch = rawNorm == combinedArtistTitle || rawNorm == combinedTitleArtist
        val cleanCombinedMatch = !strictCombinedMatch && (rawNorm == "$normArtist $normCleanTitle" || rawNorm == "$normCleanTitle $normArtist")

        var score = 0

        // Title Score
        val strictTitleMatch = expectedTitle != null && normTitle == expectedTitle
        val cleanTitleMatch = !strictTitleMatch && expectedTitle != null && normCleanTitle == expectedTitle
        score += when {
            strictCombinedMatch -> 650
            cleanCombinedMatch -> 560
            strictTitleMatch -> 500
            cleanTitleMatch -> 440
            expectedTitle != null && (normTitle.contains(expectedTitle) || normCleanTitle.contains(expectedTitle) || expectedTitle.contains(normTitle) || expectedTitle.contains(normCleanTitle)) -> 250
            fuzzyTitleMatch -> 390
            rawNorm.contains(normTitle) && normTitle.length >= 3 -> 200
            allQueryTokensInTrack || fuzzyQueryTokensMatch -> 180
            else -> scoreText(intent.rawQuery, title) / 2
        }
        if ((allQueryTokensInTrack || fuzzyQueryTokensMatch) && !strictCombinedMatch && !cleanCombinedMatch && !strictTitleMatch && !cleanTitleMatch && !fuzzyTitleMatch) {
            score += 120
        }

        // Resolve requested variant early so title/penalty logic can use it
        val requestedVariant = resolveRequestedVariant(intent.rawQuery)
        val requestedAnyVariant = requestedVariant != VariantClassifier.VariantType.UNKNOWN
        // If a requested variant word appears in the title, give a strong boost even before exact title matching
        if (requestedAnyVariant) {
            val requestedMarker = when (requestedVariant) {
                VariantClassifier.VariantType.PIANO -> "piano"
                VariantClassifier.VariantType.LIVE -> "live"
                VariantClassifier.VariantType.ACOUSTIC -> "acoustic"
                VariantClassifier.VariantType.REMIX -> "remix"
                VariantClassifier.VariantType.COVER -> "cover"
                VariantClassifier.VariantType.INSTRUMENTAL -> "instrumental"
                VariantClassifier.VariantType.KARAOKE -> "karaoke"
                else -> null
            }
            if (requestedMarker != null && requestedMarker in normTitle) score += 400
        }

        // Artist Score — artist identity is as important as title identity.
        val artistMatch = when {
            expectedArtist != null && (normArtist == expectedArtist || normArtist.contains(expectedArtist)) -> true
            expectedArtist != null && expectedArtist.contains(normArtist) && normArtist.length > 3 -> true
            else -> false
        }
        val artistOverlap = artistTokenOverlap(expectedArtist, normArtist)
        val exactArtistMatch = expectedArtist != null && normArtist == expectedArtist
        score += when {
            exactArtistMatch -> 420
            artistMatch -> 300
            expectedArtist != null && artistOverlap >= 0.5 -> 150
            expectedArtist != null && expectedArtist.contains(normArtist) && normArtist.length > 3 -> 40
            expectedArtist == null && normArtist.length >= 3 && rawNorm.contains(normArtist) -> 300
            else -> 0
        }

        // Wrong-artist penalty — when we know the artist and the candidate has no real overlap,
        // crush uploaders / tribute / cover channels that happen to match the title.
        if (expectedArtist != null && !artistMatch && artistOverlap < 0.25 && normArtist.isNotBlank()) {
            score -= 500
        }

        // Variant handling: if the user explicitly asked for a variant, boost tracks that ARE
        // that variant so the right recording can beat the canonical studio version.
        val trackVariant = VariantClassifier.classify(title, artist, track.album, intent.rawQuery).variantType
        if (requestedVariant != VariantClassifier.VariantType.STUDIO_VOCAL &&
            requestedVariant != VariantClassifier.VariantType.UNKNOWN &&
            trackVariant == requestedVariant
        ) {
            score += 700
        }

        // Variant Penalties
        val (rejected, _) = VariantClassifier.isRejectedForStudioIntent(title, artist, track.album, intent.rawQuery)
        if (rejected) score -= 400

        // Quality penalties — penalize SEO/uploader garbage in title
        val titleGarbageMarkers = listOf(
            "official video", "official audio", "official music video", "lyric video",
            "audio", "4k", "hd", "visualizer", "lyrics", "explicit", "clean"
        )
        if (titleGarbageMarkers.any { it in normTitle }) score -= 260
        if (normTitle.startsWith("the ") || normTitle.startsWith("a ")) score -= 5
        val titleNegMarkers = listOf(
            "tribute", "cover", "karaoke", "instrumental", "remix", "8d", "nightcore",
            "speed up", "slowed", "reverb", "tiktok", "ringtone", "dj mix",
            "acoustic version", "live at", "live from", "live in", "live version", "originally performed",
            "backing track", "type beat"
        )
        val hasLiveMarker = (normTitle.endsWith(" live") || normTitle.contains(" live ")) && requestedVariant != VariantClassifier.VariantType.LIVE
        if (titleNegMarkers.any { it in normTitle } || hasLiveMarker) {
            score += if (requestedAnyVariant && trackVariant == requestedVariant) 1000 else -260
        }

        // Professionalism Bonus (penalize uploaders masquerading as artists)
        val uploaderMarkers = listOf(
            "7clouds", "topic", "lyrics", "lyric", "uploader", "channel", "archive",
            "hq", "hd", "videos", "audio library", "no copyright", "ncs", "covers",
            "instrumental", "karafun", "sing king", "8d tunes"
        )
        val uploaderLikeArtist = uploaderMarkers.any { it in normArtist }
        if (uploaderLikeArtist) score -= 400
        if (normArtist.endsWith("vevo") || normArtist.endsWith("topic")) score -= 80
        if (normArtist.isBlank()) score -= 120

        // Catalog authority comes from the provider result itself: source
        // priority and complete recording metadata. It never depends on a
        // hand-maintained title, artist, or duration table.
        score += track.sourcePriority.coerceIn(0, 20)
        if (!track.externalTrackId.isNullOrBlank()) score += 8
        if (!track.album.isNullOrBlank()) score += 5
        if (!track.isrc.isNullOrBlank()) score += 12
        if ((track.durationMs ?: 0L) > 0L) score += 4

        // Eligibility for "Top Result" requires:
        //  - not a rejected variant
        //  - title matches the expected title (or no expected title)
        //  - artist matches the expected artist when one is known
        val titleMatches = expectedTitle == null ||
            normTitle == expectedTitle ||
            normCleanTitle == expectedTitle ||
            normTitle.contains(expectedTitle) ||
            normCleanTitle.contains(expectedTitle) ||
            expectedTitle.contains(normTitle) ||
            expectedTitle.contains(normCleanTitle) ||
            fuzzyTitleMatch ||
            allQueryTokensInTrack ||
            fuzzyQueryTokensMatch ||
            (rawNorm.contains(normTitle) && normTitle.length >= 3)
        val artistMatchesCandidate = expectedArtist == null || artistMatch || artistOverlap >= 0.5

        val eligible = !rejected &&
            !uploaderLikeArtist &&
            !isLowAuthorityArtist(artist) &&
            normArtist.isNotBlank() &&
            titleMatches &&
            artistMatchesCandidate

        return Evaluation(score, eligible)
    }

    private fun artistTokenOverlap(expectedArtist: String?, candidateArtist: String): Float {
        if (expectedArtist.isNullOrBlank() || candidateArtist.isBlank()) return 0f
        val expectedTokens = expectedArtist.split(" ").filter { it.length > 1 }.toSet()
        val candidateTokens = candidateArtist.split(" ").filter { it.length > 1 }.toSet()
        if (expectedTokens.isEmpty() || candidateTokens.isEmpty()) return 0f
        val intersection = expectedTokens.intersect(candidateTokens)
        return intersection.size.toFloat() / expectedTokens.size.toFloat()
    }

    private fun scoreText(query: String, text: String): Int {
        val nq = normalize(query)
        val nt = normalize(text)
        return when {
            nt == nq -> 100
            nt.startsWith(nq) -> 80
            nt.contains(nq) -> 60
            else -> 0
        }
    }

    private fun inferPersonalizedIntent(
        query: String,
        results: List<CanonicalTrack>,
        personalization: SearchPersonalization
    ): SearchQueryIntent? {
        val normalizedQuery = normalize(query)
        if (normalizedQuery.replace(" ", "").length < 3) return null

        val preferred = results
            .asSequence()
            .filter { queryCanDescribe(normalizedQuery, it) }
            .map { track -> track to personalizationBonus(query, track, personalization) }
            .filter { (_, bonus) -> bonus >= PERSONALIZED_INTENT_THRESHOLD }
            .maxByOrNull { (_, bonus) -> bonus }
            ?.first
            ?: return null

        return SearchQueryIntent(
            rawQuery = query.trim(),
            songTitle = identityTitle(preferred.title, query),
            primaryArtist = preferred.artist.trim(),
            featuredArtists = parseIntent(query).featuredArtists
        )
    }

    /** Resolve free-form text only from supplied catalog and personal evidence. */
    fun resolveIntent(
        query: String,
        results: List<CanonicalTrack>,
        personalization: SearchPersonalization = SearchPersonalization()
    ): SearchQueryIntent = inferPersonalizedIntent(query, results, personalization)
        ?: inferCatalogIntent(query, results)
        ?: parseIntent(query)

    private fun personalizationBonus(
        query: String,
        track: CanonicalTrack,
        personalization: SearchPersonalization
    ): Int {
        val normalizedQuery = normalize(query)
        if (!queryCanDescribe(normalizedQuery, track)) return 0

        val key = identityKey(track.title, track.artist)
        var bonus = 0
        if (key in personalization.libraryIdentityKeys) bonus += LIBRARY_IDENTITY_BONUS
        if (key in personalization.recentIdentityKeys) bonus += RECENT_IDENTITY_BONUS

        val activeTitle = personalization.activeTitle?.let(::normalize)
        val activeArtist = personalization.activeArtist?.let(::normalize)
        if (!activeTitle.isNullOrBlank() && !activeArtist.isNullOrBlank() &&
            normalize(track.title) == activeTitle && normalize(track.artist) == activeArtist
        ) {
            bonus += ACTIVE_IDENTITY_BONUS
        }
        return bonus
    }

    private fun queryCanDescribe(normalizedQuery: String, track: CanonicalTrack): Boolean {
        if (normalizedQuery.isBlank()) return false
        val title = normalize(identityTitle(track.title, normalizedQuery))
        val artist = normalize(track.artist)
        return title.startsWith(normalizedQuery) ||
            artist.startsWith(normalizedQuery) ||
            "$title $artist".startsWith(normalizedQuery) ||
            "$artist $title".startsWith(normalizedQuery)
    }

    private fun addScoreSafely(score: Int, bonus: Int): Int =
        if (score == Int.MIN_VALUE) Int.MIN_VALUE else score + bonus

    /**
     * Derive track/artist intent from relevance-ordered catalog results. The
     * gateway's discovery catalog supplies spelling tolerance and popularity;
     * this layer independently verifies the character similarity before using
     * the identity. No song names are embedded here.
     */
    private fun inferCatalogIntent(query: String, results: List<CanonicalTrack>): SearchQueryIntent? {
        val raw = query.trim()
        val catalogQuery = queryWithoutFeatureClause(raw)
        val normalizedQuery = normalize(catalogQuery)
        val compactQuery = normalizedQuery.replace(" ", "")
        if (compactQuery.length < 3 || results.isEmpty()) return null

        // Explicit title/artist syntax is already authoritative and must not be
        // overwritten by a catalog's popularity ordering.
        if (catalogQuery.contains(" by ", ignoreCase = true) || catalogQuery.contains(" - ")) {
            return parseIntent(raw)
        }

        data class ArtistIntentCandidate(
            val displayName: String,
            val normalizedName: String,
            val occurrenceCount: Int,
            val firstIndex: Int
        )

        val artistMatch = results
            .take(25)
            .mapIndexedNotNull { index, track ->
                val displayName = primaryArtist(track.artist)
                val normalizedName = normalize(displayName)
                if (displayName.isBlank() ||
                    (!normalizedName.startsWith(normalizedQuery) && !normalizedQuery.startsWith(normalizedName) && !normalizedQuery.contains(normalizedName)) ||
                    isLowAuthorityArtist(displayName)
                ) {
                    null
                } else {
                    Triple(normalizedName, displayName, index)
                }
            }
            .groupBy { it.first }
            .map { (normalizedName, matches) ->
                ArtistIntentCandidate(
                    displayName = matches.first().second,
                    normalizedName = normalizedName,
                    occurrenceCount = matches.size,
                    firstIndex = matches.minOf { it.third }
                )
            }
            .filter { it.occurrenceCount >= 2 }
            .sortedWith(
                compareByDescending<ArtistIntentCandidate> { it.normalizedName == normalizedQuery }
                    .thenByDescending { it.occurrenceCount }
                    .thenBy { it.firstIndex }
            )
            .firstOrNull()

        val titleMatchCount = results.take(25).count { track ->
            normalize(identityTitle(track.title, catalogQuery)) == normalizedQuery
        }
        if (artistMatch != null && titleMatchCount < 2) {
            val remainingTitle = if (artistMatch.normalizedName != normalizedQuery) {
                normalizedQuery.replace(artistMatch.normalizedName, "").trim()
            } else null
            return SearchQueryIntent(
                rawQuery = raw,
                songTitle = if (remainingTitle.isNullOrBlank()) null else remainingTitle,
                primaryArtist = artistMatch.displayName,
                featuredArtists = parseIntent(raw).featuredArtists
            )
        }

        data class CatalogCandidate(
            val track: CanonicalTrack,
            val title: String,
            val confidence: Double,
            val score: Double
        )

        val candidates = results.take(25).mapIndexed { index, track ->
            val title = identityTitle(track.title, catalogQuery)
            val artist = track.artist.trim()
            val similarity = maxOf(
                textSimilarity(catalogQuery, title),
                textSimilarity(catalogQuery, "$title $artist"),
                textSimilarity(catalogQuery, "$artist $title")
            )
            val confidence = maxOf(
                similarity,
                identityTokenCoverage(catalogQuery, title, artist)
            )
            val orderBonus = maxOf(0, 36 - index * 28)
            val metadataBonus =
                (if (track.album.isNullOrBlank()) 0 else 4) +
                    (if (track.externalTrackId.isNullOrBlank()) 0 else 8) +
                    (if (track.isrc.isNullOrBlank()) 0 else 30) +
                    track.sourcePriority.coerceIn(0, 10)
            val (rejectedVariant, _) = VariantClassifier.isRejectedForStudioIntent(
                track.title,
                track.artist,
                track.album,
                catalogQuery
            )
            val authorityPenalty =
                (if (isLowAuthorityArtist(artist)) 20 else 0) +
                    (if (rejectedVariant) 100 else 0)
            val soundtrackPenalty =
                if (looksLikeSoundtrackPackaging(track.title, track.album)) 18 else 0
            val swappedPenalty =
                if (normalize(artist) == normalizedQuery && normalize(title) != normalizedQuery) 35 else 0
            CatalogCandidate(
                track = track,
                title = title,
                confidence = confidence,
                score = confidence * 100.0 + orderBonus + metadataBonus - authorityPenalty - soundtrackPenalty - swappedPenalty
            )
        }.sortedByDescending { it.score }

        val best = candidates.firstOrNull() ?: return null
        val minimumConfidence = if (compactQuery.length <= 5) 0.88 else 0.72
        if (best.confidence < minimumConfidence || best.title.isBlank() || best.track.artist.isBlank()) return null

        return SearchQueryIntent(
            rawQuery = raw,
            songTitle = best.title,
            primaryArtist = best.track.artist.trim(),
            featuredArtists = parseIntent(raw).featuredArtists
        )
    }

    private fun queryWithoutFeatureClause(query: String): String {
        val normalized = normalize(query)
        val markerIndex = FEAT_MARKERS
            .map(normalized::indexOf)
            .filter { it > 0 }
            .minOrNull()
            ?: return query.trim()
        return normalized.substring(0, markerIndex).trim()
    }

    /** Stable identity key shared by repository personalization and ranking. */
    fun identityKey(title: String, artist: String): String =
        "${normalize(title)}|${normalize(artist)}"

    private fun textSimilarity(left: String, right: String): Double {
        val a = normalize(left).replace(" ", "")
        val b = normalize(right).replace(" ", "")
        if (a.isBlank() || b.isBlank()) return 0.0
        if (a == b) return 1.0
        val longest = maxOf(a.length, b.length)
        val editSimilarity = 1.0 - levenshteinDistance(a, b).toDouble() / longest.toDouble()
        val containmentSimilarity = if (a.contains(b) || b.contains(a)) {
            minOf(a.length, b.length).toDouble() / longest.toDouble()
        } else {
            0.0
        }
        return maxOf(editSimilarity, containmentSimilarity)
    }

    private fun identityTokenCoverage(query: String, title: String, artist: String): Double {
        val queryTokens = normalize(query).split(" ").filter { it.isNotBlank() }.toSet()
        val titleTokens = normalize(title).split(" ").filter { it.isNotBlank() }.toSet()
        val artistTokens = normalize(artist).split(" ").filter { it.isNotBlank() }.toSet()
        if (queryTokens.isEmpty() || titleTokens.isEmpty()) return 0.0
        val titleCoverage = titleTokens.intersect(queryTokens).size.toDouble() / titleTokens.size.toDouble()
        if (titleCoverage < 0.5) return 0.0
        val artistCoverage = if (artistTokens.isEmpty()) {
            0.0
        } else {
            artistTokens.intersect(queryTokens).size.toDouble() / artistTokens.size.toDouble()
        }
        return titleCoverage * 0.7 + artistCoverage * 0.3
    }

    private fun levenshteinDistance(left: String, right: String): Int {
        if (left == right) return 0
        if (left.isEmpty()) return right.length
        if (right.isEmpty()) return left.length
        var previous = IntArray(right.length + 1) { it }
        for (row in 1..left.length) {
            val current = IntArray(right.length + 1)
            current[0] = row
            for (column in 1..right.length) {
                val substitution = previous[column - 1] +
                    if (left[row - 1] == right[column - 1]) 0 else 1
                current[column] = minOf(previous[column] + 1, current[column - 1] + 1, substitution)
            }
            previous = current
        }
        return previous[right.length]
    }

    private fun identityTitle(title: String, query: String): String {
        val requestedVariant = resolveRequestedVariant(query)
        val preserveVariant = requestedVariant != VariantClassifier.VariantType.UNKNOWN
        val withoutSubtitles = if (preserveVariant) {
            title
        } else {
            title
                .replace(Regex("""\s*[\[(][^\])]+[\])]"""), " ")
                .replace(Regex("""(?i)\s*-\s*(music from|from|original motion picture|soundtrack|inspired by).*$"""), " ")
        }
        val releaseWords = setOf(
            "remaster", "remastered", "deluxe", "edition", "version", "edit",
            "mono", "stereo", "explicit", "clean", "audio", "video", "official"
        )
        return normalize(withoutSubtitles)
            .split(" ")
            .filterNot { it in releaseWords }
            .joinToString(" ")
            .replace(Regex("""\b(feat|featuring|ft)\b.*$"""), "")
            .trim()
    }

    private fun looksLikeSoundtrackPackaging(title: String, album: String?): Boolean {
        val blob = "${title.lowercase()} ${album.orEmpty().lowercase()}"
        return listOf("from ", "soundtrack", "motion picture", "original score", " ost").any { it in blob }
    }

    private fun mergeFeaturedArtists(track: CanonicalTrack, intentFeatured: List<String>): List<String> {
        return (track.featuredArtists + DisplayMetadataCleaner.extractFeaturedArtists(track.title, track.artist) + intentFeatured)
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinctBy { it.lowercase() }
    }

    private fun isLowAuthorityArtist(artist: String): Boolean {
        val normalized = normalize(artist)
        return listOf(
            "karaoke", "tribute", "cover band", "covers", "kids bop", "kidz bop",
            "instrumental", "rain sounds", "rainforest sounds", "music box", "lyrics",
            "backing track", "the backing tracks", "originally performed",
            "tabata", "workout hits", "fitness beats", "style pack", "party tyme",
            "sound a like", "sing along", "composerlyricist", "musicpublisher"
        ).any(normalized::contains)
    }

    private fun primaryArtist(artist: String): String {
        val split = DisplayMetadataCleaner.splitArtistCredits(artist)
        if (split.primary.isNotBlank()) return split.primary
        val first = artist.split(",").firstOrNull()?.trim().orEmpty()
        return first.replace(Regex("""(?i)\s+(feat\.?|featuring|ft\.?)\b.*$"""), "").trim()
    }

    private fun artistIdentityMatches(expected: String, actual: String): Boolean {
        val left = normalize(primaryArtist(expected))
        val right = normalize(primaryArtist(actual))
        if (left == right) return true
        if (left.length > 3 && right.contains(left)) return true
        if (right.length > 3 && left.contains(right)) return true
        return textSimilarity(left, right) >= 0.9
    }

    private fun isRelevantResult(
        intent: SearchQueryIntent,
        track: CanonicalTrack,
        evaluation: Evaluation,
        bestScore: Int
    ): Boolean {
        if (evaluation.finalScore == Int.MIN_VALUE) return false
        if (bestScore != Int.MIN_VALUE && evaluation.finalScore < bestScore - 700) return false

        val rawNorm = normalize(intent.rawQuery)
        val titleNorm = normalize(identityTitle(track.title, intent.rawQuery))
        val actualArtist = normalize(track.artist)

        // Query token coverage check: if track matches all query words across title + artist, it is ALWAYS relevant
        val stopWords = setOf("by", "feat", "ft", "featuring", "with", "the", "a", "an", "and", "&")
        val queryTokens = rawNorm.split(" ").filter { it.isNotBlank() && it !in stopWords }
        val trackTokens = (titleNorm.split(" ") + actualArtist.split(" ")).filter { it.isNotBlank() }.toSet()
        val fuzzyCoverage = queryTokens.isNotEmpty() && queryTokens.all { qTok ->
            trackTokens.any { tTok ->
                tTok == qTok || (qTok.length >= 4 && tTok.length >= 4 && levenshteinDistance(qTok, tTok) <= 1)
            }
        }
        if (fuzzyCoverage) {
            return true
        }

        val expectedArtist = intent.primaryArtist?.let(::normalize)
        if (!expectedArtist.isNullOrBlank() && !artistIdentityMatches(expectedArtist, actualArtist) && !rawNorm.contains(actualArtist)) return false

        val expectedTitle = intent.songTitle
        if (!expectedTitle.isNullOrBlank()) {
            val similarity = textSimilarity(expectedTitle, titleNorm)
            val combinedArtistTitle = "$actualArtist $titleNorm"
            val combinedTitleArtist = "$titleNorm $actualArtist"
            val combinedSim = maxOf(textSimilarity(rawNorm, combinedArtistTitle), textSimilarity(rawNorm, combinedTitleArtist))
            val fuzzyTokensMatch = expectedTitle.split(" ").filter { it.length >= 3 && it !in stopWords }.all { eTok ->
                trackTokens.any { tTok ->
                    tTok == eTok || (eTok.length >= 4 && tTok.length >= 4 && levenshteinDistance(eTok, tTok) <= 1)
                }
            }
            return evaluation.eligibleForTop || similarity >= 0.50 || combinedSim >= 0.55 || rawNorm.contains(titleNorm) || fuzzyTokensMatch
        }

        return expectedArtist.isNullOrBlank() || artistIdentityMatches(expectedArtist, actualArtist)
    }

    // --- Categorization ---

    private enum class Category { SONG, ARTIST, ALBUM }

    private fun detectCategory(track: CanonicalTrack, query: String): Category {
        val extId = track.externalTrackId ?: ""
        if (extId.contains(":artist:")) return Category.ARTIST
        if (extId.contains(":album:")) return Category.ALBUM

        val title = track.title.trim()
        val artist = track.artist.trim()
        val album = track.album?.trim()

        // Surgical Strict Search rules
        if (track.durationMs == null || track.durationMs == 0L) {
            if (title.isBlank() || title.equals(artist, ignoreCase = true)) return Category.ARTIST
            if (title.equals(album, ignoreCase = true)) return Category.ALBUM
            // SSS Rule: Don't guess artists for query matches unless metadata is missing.
        }

        return Category.SONG
    }

    // --- Utils ---

    private fun normalize(value: String): String {
        val folded = java.text.Normalizer.normalize(value, java.text.Normalizer.Form.NFD)
            .replace(Regex("\\p{M}+"), "")
        return folded.lowercase()
            .replace("&", " and ")
            .replace(Regex("""[^\p{L}\p{N}\s]+"""), " ")
            .replace(Regex("""\s+"""), " ")
            .normalizeDigitWords()
            .trim()
    }

    private fun String.normalizeDigitWords(): String {
        var result = this
        DIGIT_WORD_MAP.forEach { (word, digit) ->
            result = result.replace(word, digit)
        }
        return result
    }

    /**
     * Normalized query for categorization check.
     */
    private fun normalizeForCat(value: String): String {
        return value.lowercase()
            .replace(Regex("""[^a-z0-9\s]"""), "") // More aggressive
            .replace(Regex("""\s+"""), " ")
            .trim()
    }

    data class Evaluation(val finalScore: Int, val eligibleForTop: Boolean)

    data class SearchQueryIntent(
        val rawQuery: String,
        val songTitle: String?,
        val primaryArtist: String?,
        val featuredArtists: List<String>,
        val hasExpectedTitle: Boolean = !songTitle.isNullOrBlank()
    )

    private const val LIBRARY_IDENTITY_BONUS = 120
    private const val RECENT_IDENTITY_BONUS = 180
    private const val ACTIVE_IDENTITY_BONUS = 420
    private const val PERSONALIZED_INTENT_THRESHOLD = LIBRARY_IDENTITY_BONUS + RECENT_IDENTITY_BONUS

    private fun resolveRequestedVariant(query: String?): VariantClassifier.VariantType {
        if (query.isNullOrBlank()) return VariantClassifier.VariantType.UNKNOWN
        val q = query.lowercase()
        return when {
            "instrumental" in q || "inst " in q -> VariantClassifier.VariantType.INSTRUMENTAL
            "karaoke" in q -> VariantClassifier.VariantType.KARAOKE
            "piano" in q -> VariantClassifier.VariantType.PIANO
            "lullaby version" in q || "lullaby rendition" in q || "baby lullaby" in q -> VariantClassifier.VariantType.COVER
            "orchestra" in q || "symphony" in q -> VariantClassifier.VariantType.INSTRUMENTAL
            "tribute" in q -> VariantClassifier.VariantType.COVER
            "cover" in q -> VariantClassifier.VariantType.COVER
            "live" in q -> VariantClassifier.VariantType.LIVE
            "remix" in q || "slowed" in q || "sped" in q || "nightcore" in q -> VariantClassifier.VariantType.REMIX
            "acoustic" in q || "unplugged" in q -> VariantClassifier.VariantType.ACOUSTIC
            else -> VariantClassifier.VariantType.UNKNOWN
        }
    }
}
