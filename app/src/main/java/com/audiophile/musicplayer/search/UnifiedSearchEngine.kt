package com.audiophile.musicplayer.search

import com.audiophile.musicplayer.data.canonical.CanonicalAlbum
import com.audiophile.musicplayer.data.canonical.CanonicalArtist
import com.audiophile.musicplayer.data.canonical.CanonicalTrack
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
    val identityMatch: Boolean
)

/**
 * A single, cohesive, and intelligent search engine that replaces fragmented logic.
 * Consolidates intent parsing, identity scoring, heuristic ranking, and categorization.
 */
object UnifiedSearchEngine {

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

    /**
     * Primary entry point for search processing.
     */
    fun process(query: String, results: List<CanonicalTrack>): UnifiedSearchResponse {
        val normalizedQuery = normalize(query)
        val intent = parseIntent(query)

        // 1. Enrich results with an "Identity Fallback" if a famous song is recognized but missing from provider hits
        val searchResults = applyFamousSongFallback(intent, results)

        // 2. Score and Filter candidates
        val scoredTracks = searchResults
            .map { it to scoreCandidate(intent, it) }
            .sortedByDescending { it.second.finalScore }

        // 3. Separate into categories using Surgical Strict Search (SSS)
        val songs = mutableListOf<CanonicalTrack>()
        val artists = mutableMapOf<String, CanonicalArtist>()
        val albums = mutableMapOf<String, CanonicalAlbum>()

        val seenSongKeys = mutableSetOf<String>()

        for ((track, evaluation) in scoredTracks) {
            // Always collect unique artists and albums from every track
            val artistName = track.artist.ifBlank { track.title }.trim()
            val artistKey = normalize(artistName)
            if (artistKey.isNotBlank() && !artists.containsKey(artistKey)) {
                artists[artistKey] = CanonicalArtist(
                    name = artistName,
                    id = track.externalTrackId ?: "resolved:artist:$artistKey",
                    genre = track.genre,
                    artworkUrl = track.artworkUrl
                )
            }
            val albumTitle = track.album?.trim().orEmpty().ifBlank { track.title.trim() }
            val albumArtistName = track.artist.trim()
            val albumKey = normalize("$albumTitle|$albumArtistName")
            if (albumTitle.isNotBlank() && !albums.containsKey(albumKey)) {
                albums[albumKey] = CanonicalAlbum(
                    title = albumTitle,
                    artist = albumArtistName,
                    id = track.externalTrackId ?: "resolved:album:$albumKey",
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
                    val key = "${normalize(track.title)}|${normalize(track.artist)}"
                    if (seenSongKeys.add(key)) {
                        songs.add(track)
                    }
                }
            }
        }

        // 4. Select Top Result
        val topCandidate = scoredTracks
            .filter { it.second.eligibleForTop }
            .map { it.first }
            .firstOrNull() ?: songs.firstOrNull()

        // Enrich with featured artists if parsed from intent
        val topResult = topCandidate?.copy(featuredArtists = intent.featuredArtists)
        val enrichedSongs = songs.map { it.copy(featuredArtists = intent.featuredArtists) }
        
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

        // Check registry for famous song patterns first — registry knows canonical artist.
        FamousSongRegistry.resolve(queryForParsing)?.let { resolved ->
            // The registry gives us the canonical title. Anything left in the query after the title
            // (minus the featured artists already split out) is likely the primary artist the user typed.
            val primaryArtist = extractTypedArtist(queryForParsing, resolved.title)
            val intent = SearchQueryIntent(raw, resolved.title, primaryArtist ?: resolved.artist, featured)
            return intent
        }

        // Heuristic: "Song by Artist" or "Artist - Song"
        if (raw.contains(" by ", ignoreCase = true)) {
            val parts = raw.split(Regex(" by ", RegexOption.IGNORE_CASE), limit = 2)
            return SearchQueryIntent(raw, parts[0].trim(), parts[1].trim(), featured)
        }

        if (raw.contains(" - ")) {
            val parts = raw.split(" - ", limit = 2)
            // Artist - Title is very common
            return SearchQueryIntent(raw, parts[1].trim(), parts[0].trim(), featured)
        }

        // Heuristic: "Title Artist" where the last 1-2 words look like an artist name.
        // For 3-word queries like "sting desert rose" (Artist + 2-word title), try both
        // [artist=1, title=2] and [artist=last-1, title=first-2], and prefer the split whose
        // title matches a famous song or whose artist is the canonical artist for that title.
        val rawWords = raw.split(Regex("""\s+""")).filter { it.isNotBlank() }
        if (rawWords.size >= 3) {
            data class Split(val artist: String, val title: String, val score: Int)
            val candidates = mutableListOf<Split>()
            val attemptNs = if (rawWords.size == 3) listOf(1, 2) else listOf(1, 2)
            for (n in attemptNs) {
                if (n >= rawWords.size) continue
                val lastArtist = rawWords.takeLast(n).joinToString(" ")
                val lastTitle = rawWords.dropLast(n).joinToString(" ")
                if (lastArtist.length > 1 && lastTitle.length > 1) {
                    candidates.add(Split(lastArtist, lastTitle, splitQualityScore(lastArtist, lastTitle)))
                }
                val firstArtist = rawWords.take(n).joinToString(" ")
                val firstTitle = rawWords.drop(n).joinToString(" ")
                if (firstArtist.length > 1 && firstTitle.length > 1) {
                    candidates.add(Split(firstArtist, firstTitle, splitQualityScore(firstArtist, firstTitle)))
                }
            }
            val best = candidates.maxByOrNull { it.score }
            if (best != null && best.score >= 0) {
                return SearchQueryIntent(raw, best.title, best.artist, featured)
            }
        }

        return SearchQueryIntent(raw, queryForParsing, null, featured)
    }

    private fun splitQualityScore(artist: String, title: String): Int {
        var score = 0
        val normTitle = normalize(title)
        val normArtist = normalize(artist)
        // Prefer titles that match a famous song
        FamousSongRegistry.getArtist(normTitle)?.let { canonicalArtist ->
            score += 100
            if (normalize(canonicalArtist) == normArtist || normArtist.contains(normalize(canonicalArtist))) {
                score += 200
            }
        }
        // Prefer artist on either end; slight preference for short artist names
        score += 5 - (normArtist.length / 8).coerceAtMost(5)
        // Penalize artist words that look like title words (numbers, common articles)
        if (normArtist in setOf("the", "a", "an", "and")) score -= 50
        return score
    }

    /**
     * If the user typed "Down Jay Sean feat Lil Wayne" and the registry says the title is "down",
     * then "jay sean" is what the user typed as the artist. Returns null if nothing extra remains.
     */
    private fun extractTypedArtist(normalizedQuery: String, normalizedTitle: String): String? {
        val titleWords = normalizedTitle.split(" ").filter { it.isNotBlank() }
        val queryWords = normalizedQuery.split(" ").filter { it.isNotBlank() }
        // Find where the title ends inside the query; anything after is typed artist.
        var titleEnd = -1
        var titleIdx = 0
        for ((idx, word) in queryWords.withIndex()) {
            if (titleIdx < titleWords.size && word == titleWords[titleIdx]) {
                titleIdx++
                titleEnd = idx
                if (titleIdx == titleWords.size) break
            }
        }
        if (titleIdx != titleWords.size) return null
        val artistWords = queryWords.drop(titleEnd + 1)
        return artistWords.joinToString(" ").takeIf { it.isNotBlank() }
    }

    // --- Scoring Logic ---

    private fun scoreCandidate(intent: SearchQueryIntent, track: CanonicalTrack): Evaluation {
        val title = track.title.trim()
        val artist = track.artist.trim()
        if (title.isBlank()) return Evaluation(Int.MIN_VALUE, false)

        val normTitle = normalize(title)
        val normArtist = normalize(artist)
        val expectedTitle = intent.songTitle?.let { normalize(it) }
        val expectedArtist = intent.primaryArtist?.let { normalize(it) }

        var score = 0

        // Title Score
        score += when {
            expectedTitle != null && normTitle == expectedTitle -> 200
            expectedTitle != null && normTitle.contains(expectedTitle) -> 60
            else -> scoreText(intent.rawQuery, title) / 2
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
        score += when {
            artistMatch -> 300
            expectedArtist != null && artistOverlap >= 0.5 -> 150
            expectedArtist != null && expectedArtist.contains(normArtist) && normArtist.length > 3 -> 40
            else -> 0
        }

        // Wrong-artist penalty — when we know the artist and the candidate has no real overlap,
        // crush uploaders / tribute / cover channels that happen to match the title.
        if (expectedArtist != null && !artistMatch && artistOverlap < 0.25 && normArtist.isNotBlank()) {
            score -= 500
        }

        // Famous Registry Bonus — only when the candidate artist is the canonical artist.
        // This stops SEO titles like "Bad Guy Billie Eilish" by uploader channels from winning.
        if (expectedTitle != null) {
            val famousArtist = FamousSongRegistry.getArtist(expectedTitle)
            if (famousArtist != null) {
                val normFamousArtist = normalize(famousArtist)
                if (normArtist.isNotBlank() &&
                    (normFamousArtist == normArtist || normFamousArtist.contains(normArtist) || normArtist.contains(normFamousArtist))
                ) {
                    score += 1000
                }
            }
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
            "acoustic version", "live at", "live from"
        )
        if (titleNegMarkers.any { it in normTitle }) {
            score += if (requestedAnyVariant && trackVariant == requestedVariant) 1000 else -260
        }

        // Professionalism Bonus (penalize uploaders masquerading as artists)
        val uploaderMarkers = listOf(
            "7clouds", "topic", "lyrics", "lyric", "uploader", "channel", "archive",
            "hq", "hd", "videos", "audio library", "no copyright", "ncs", "covers",
            "instrumental", "karafun", "sing king", "8d tunes"
        )
        if (uploaderMarkers.any { it in normArtist }) score -= 400
        if (normArtist.endsWith("vevo") || normArtist.endsWith("topic")) score -= 80

        // Duration Bonus
        expectedTitle?.let { FamousSongRegistry.getDuration(it) }?.let { expectedMs ->
            track.durationMs?.let { actualMs ->
                if (actualMs > 0) {
                    val delta = Math.abs(actualMs - expectedMs)
                    if (delta < 15_000L) score += 30
                    else if (delta > 60_000L) score -= 40
                }
            }
        }

        // Provider Priority
        score += track.sourcePriority.coerceIn(0, 20)

        // Eligibility for "Top Result" requires:
        //  - not a rejected variant
        //  - title matches the expected title (or no expected title)
        //  - artist matches the expected artist when one is known
        val eligible = !rejected &&
            (expectedTitle == null || normTitle.contains(expectedTitle)) &&
            (expectedArtist == null || artistMatch || artistOverlap >= 0.5 || isFamousArtistMatch(expectedTitle, normArtist))

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

    private fun isFamousArtistMatch(expectedTitle: String?, normArtist: String): Boolean {
        if (expectedTitle == null) return false
        val famousArtist = FamousSongRegistry.getArtist(expectedTitle) ?: return false
        val normFamousArtist = normalize(famousArtist)
        return normFamousArtist.isNotBlank() &&
            (normFamousArtist == normArtist || normFamousArtist.contains(normArtist) || normArtist.contains(normFamousArtist))
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

    private fun normalize(value: String): String =
        value.lowercase()
            .replace(Regex("""[^\p{L}\p{N}\s]+"""), "")
            .replace(Regex("""\s+"""), " ")
            .trim()

    /**
     * Normalized query for categorization check.
     */
    private fun normalizeForCat(value: String): String {
        return value.lowercase()
            .replace(Regex("""[^a-z0-9\s]"""), "") // More aggressive
            .replace(Regex("""\s+"""), " ")
            .trim()
    }

    private fun applyFamousSongFallback(intent: SearchQueryIntent, results: List<CanonicalTrack>): List<CanonicalTrack> {
        val title = intent.songTitle ?: return results
        val artist = intent.primaryArtist ?: FamousSongRegistry.getArtist(normalize(title)) ?: return results
        
        val hasExact = results.any { normalize(it.title) == normalize(title) && normalize(it.artist).contains(normalize(artist)) }
        if (hasExact) return results

        val fallback = CanonicalTrack(
            title = title.split(" ").joinToString(" ") { if (it.isNotEmpty()) it.replaceFirstChar { c -> if (c.isLowerCase()) c.titlecase() else c.toString() } else it },
            artist = artist.split(" ").joinToString(" ") { if (it.isNotEmpty()) it.replaceFirstChar { c -> if (c.isLowerCase()) c.titlecase() else c.toString() } else it },
            album = title,
            durationMs = FamousSongRegistry.getDuration(normalize(title)),
            sourcePriority = 10,
            sourceStatus = SearchItemStatus.METADATA_ONLY
        )
        return listOf(fallback) + results
    }

    data class Evaluation(val finalScore: Int, val eligibleForTop: Boolean)

    data class SearchQueryIntent(
        val rawQuery: String,
        val songTitle: String?,
        val primaryArtist: String?,
        val featuredArtists: List<String>,
        val hasExpectedTitle: Boolean = !songTitle.isNullOrBlank()
    )

    /**
     * Replaces FamousSongHints with a more flexible internal registry.
     */
    private object FamousSongRegistry {
        private val data = mapOf(
            "blinding lights" to ("The Weeknd" to 200_000L),
            "bad guy" to ("Billie Eilish" to 194_000L),
            "stayin alive" to ("Bee Gees" to 285_000L),
            "shape of you" to ("Ed Sheeran" to 233_000L),
            "flowers" to ("Miley Cyrus" to 200_000L),
            "anti hero" to ("Taylor Swift" to 200_000L),
            "bohemian rhapsody" to ("Queen" to 354_000L),
            "hotel california" to ("Eagles" to 390_000L),
            "billie jean" to ("Michael Jackson" to 294_000L),
            "piano man" to ("Billy Joel" to 336_000L),
            "down" to ("Jay Sean" to 212_000L),
            "victory lap five" to ("Fred Again" to 237_000L),
            "victory lap 5" to ("Fred Again" to 237_000L),
            "desert rose" to ("Sting" to 287_000L),
            "all of the lights" to ("Kanye West" to 310_000L),
            "stronger" to ("Kanye West" to 311_000L),
            "gold digger" to ("Kanye West" to 208_000L),
            "heartless" to ("Kanye West" to 211_000L),
            "runaway" to ("Kanye West" to 453_000L),
            "power" to ("Kanye West" to 292_000L),
            "jesus walks" to ("Kanye West" to 203_000L),
            "touch the sky" to ("Kanye West" to 236_000L),
            "ultralight beam" to ("Kanye West" to 320_000L),
            "famous" to ("Kanye West" to 196_000L),
            "no church in the wild" to ("Jay-Z" to 276_000L),
            "ni**as in paris" to ("Jay-Z" to 215_000L),
            "empire state of mind" to ("Jay-Z" to 276_000L),
            "99 problems" to ("Jay-Z" to 213_000L),
            "big pimpin" to ("Jay-Z" to 284_000L),
            "dead presidents ii" to ("Jay-Z" to 262_000L),
            "sicko mode" to ("Travis Scott" to 313_000L),
            "goosebumps" to ("Travis Scott" to 243_000L),
            "highest in the room" to ("Travis Scott" to 175_000L),
            "antidote" to ("Travis Scott" to 266_000L),
            "buttefly effect" to ("Travis Scott" to 199_000L),
            "stargazing" to ("Travis Scott" to 270_000L),
            "m.a.a.d city" to ("Kendrick Lamar" to 352_000L),
            "humble" to ("Kendrick Lamar" to 177_000L),
            "dna" to ("Kendrick Lamar" to 185_000L),
            "alright" to ("Kendrick Lamar" to 219_000L),
            "swimming pools" to ("Kendrick Lamar" to 259_000L),
            "money trees" to ("Kendrick Lamar" to 421_000L),
            "king kunta" to ("Kendrick Lamar" to 234_000L),
            "LOYALTY." to ("Kendrick Lamar" to 206_000L),
            "element" to ("Kendrick Lamar" to 207_000L),
            "rude boy" to ("Rihanna" to 222_000L),
            "umbrella" to ("Rihanna" to 276_000L),
            "diamonds" to ("Rihanna" to 225_000L),
            "work" to ("Rihanna" to 219_000L),
            "needed me" to ("Rihanna" to 191_000L),
            "love on the brain" to ("Rihanna" to 224_000L),
            "stay" to ("Rihanna" to 247_000L),
            "we found love" to ("Rihanna" to 235_000L),
            "fourfiveseconds" to ("Rihanna" to 187_000L),
            "single ladies" to ("Beyonce" to 199_000L),
            "crazy in love" to ("Beyonce" to 235_000L),
            "halo" to ("Beyonce" to 261_000L),
            "irreplaceable" to ("Beyonce" to 274_000L),
            "love on top" to ("Beyonce" to 267_000L),
            "formation" to ("Beyonce" to 219_000L),
            "drunk in love" to ("Beyonce" to 233_000L),
            "sorry" to ("Beyonce" to 232_000L),
            "if i were a boy" to ("Beyonce" to 250_000L),
            "hello" to ("Adele" to 295_000L),
            "someone like you" to ("Adele" to 285_000L),
            "rolling in the deep" to ("Adele" to 228_000L),
            "set fire to the rain" to ("Adele" to 242_000L),
            "easy on me" to ("Adele" to 224_000L),
            "send my love" to ("Adele" to 224_000L),
            "royals" to ("Lorde" to 190_000L),
            "team" to ("Lorde" to 196_000L),
            "green light" to ("Lorde" to 234_000L),
            "yellow flicker beat" to ("Lorde" to 232_000L),
            "riders on the storm" to ("The Doors" to 266_000L),
            "light my fire" to ("The Doors" to 428_000L),
            "break on through" to ("The Doors" to 146_000L),
            "people are strange" to ("The Doors" to 130_000L),
            "california love" to ("2Pac" to 285_000L),
            "dear mama" to ("2Pac" to 280_000L),
            "changes" to ("2Pac" to 270_000L),
            "hit em up" to ("2Pac" to 272_000L),
            "juicy" to ("The Notorious B.I.G." to 300_000L),
            "big poppa" to ("The Notorious B.I.G." to 247_000L),
            "mo money mo problems" to ("The Notorious B.I.G." to 271_000L),
            "hypnotize" to ("The Notorious B.I.G." to 230_000L),
            "nuthin but a g thang" to ("Dr. Dre" to 249_000L),
            "still dre" to ("Dr. Dre" to 270_000L),
            "the next episode" to ("Dr. Dre" to 161_000L),
            "forget about dre" to ("Dr. Dre" to 222_000L),
            "in da club" to ("50 Cent" to 233_000L),
            "candy shop" to ("50 Cent" to 209_000L),
            "many men" to ("50 Cent" to 256_000L),
            "window shopper" to ("50 Cent" to 180_000L),
            "lose yourself" to ("Eminem" to 326_000L),
            "stan" to ("Eminem" to 404_000L),
            "without me" to ("Eminem" to 290_000L),
            "the real slim shady" to ("Eminem" to 284_000L),
            "not afraid" to ("Eminem" to 248_000L),
            "rap god" to ("Eminem" to 363_000L),
            "till i collapse" to ("Eminem" to 298_000L),
            "mockingbird" to ("Eminem" to 251_000L),
            "love the way you lie" to ("Eminem" to 263_000L),
            "godzilla" to ("Eminem" to 210_000L),
            "the box" to ("Roddy Ricch" to 198_000L),
            "rockstar" to ("Post Malone" to 218_000L),
            "circles" to ("Post Malone" to 215_000L),
            "sunflower" to ("Post Malone" to 158_000L),
            "congratulations" to ("Post Malone" to 224_000L),
            "better now" to ("Post Malone" to 223_000L),
            "psycho" to ("Post Malone" to 221_000L),
            "wow" to ("Post Malone" to 165_000L),
            "white iverson" to ("Post Malone" to 249_000L),
            "as it was" to ("Harry Styles" to 167_000L),
            "watermelon sugar" to ("Harry Styles" to 174_000L),
            "sign of the times" to ("Harry Styles" to 340_000L),
            "adore you" to ("Harry Styles" to 207_000L),
            "golden" to ("Harry Styles" to 209_000L),
            "levitating" to ("Dua Lipa" to 203_000L),
            "dont start now" to ("Dua Lipa" to 183_000L),
            "new rules" to ("Dua Lipa" to 209_000L),
            "one kiss" to ("Dua Lipa" to 196_000L),
            "physical" to ("Dua Lipa" to 193_000L),
            "break my heart" to ("Dua Lipa" to 221_000L),
            "savage love" to ("Jason Derulo" to 171_000L),
            "wap" to ("Cardi B" to 187_000L),
            "bodak yellow" to ("Cardi B" to 224_000L),
            "i like it" to ("Cardi B" to 253_000L),
            "money" to ("Cardi B" to 183_000L),
            "up" to ("Cardi B" to 166_000L),
            "truth hurts" to ("Lizzo" to 173_000L),
            "good as hell" to ("Lizzo" to 159_000L),
            "about damn time" to ("Lizzo" to 191_000L),
            "say my name" to ("Destiny's Child" to 257_000L),
            "survivor" to ("Destiny's Child" to 242_000L),
            "bootylicious" to ("Destiny's Child" to 216_000L),
            "cater 2 u" to ("Destiny's Child" to 260_000L),
            "independent women" to ("Destiny's Child" to 222_000L),
            "no scrubs" to ("TLC" to 206_000L),
            "waterfalls" to ("TLC" to 268_000L),
            "creep" to ("TLC" to 223_000L),
            "unpretty" to ("TLC" to 257_000L),
            "lemme borrow that top" to ("Kesha" to 183_000L),
            "tik tok" to ("Kesha" to 200_000L),
            "we r who we r" to ("Kesha" to 215_000L),
            "take it off" to ("Kesha" to 216_000L),
            "your love is my drug" to ("Kesha" to 189_000L),
            "party in the usa" to ("Miley Cyrus" to 202_000L),
            "wrecking ball" to ("Miley Cyrus" to 221_000L),
            "we cant stop" to ("Miley Cyrus" to 231_000L),
            "the climb" to ("Miley Cyrus" to 234_000L),
            "seven rings" to ("Ariana Grande" to 179_000L),
            "thank u next" to ("Ariana Grande" to 207_000L),
            "positions" to ("Ariana Grande" to 172_000L),
            "34+35" to ("Ariana Grande" to 173_000L),
            "into you" to ("Ariana Grande" to 244_000L),
            "dangerous woman" to ("Ariana Grande" to 235_000L),
            "god is a woman" to ("Ariana Grande" to 196_000L),
            "no tears left to cry" to ("Ariana Grande" to 205_000L),
            "breathin" to ("Ariana Grande" to 198_000L),
            "side to side" to ("Ariana Grande" to 226_000L),
            "problem" to ("Ariana Grande" to 193_000L),
            "bang bang" to ("Ariana Grande" to 199_000L),
            "toxic" to ("Britney Spears" to 199_000L),
            "oops i did it again" to ("Britney Spears" to 211_000L),
            "...baby one more time" to ("Britney Spears" to 211_000L),
            "gimme more" to ("Britney Spears" to 250_000L),
            "womanizer" to ("Britney Spears" to 224_000L),
            "circus" to ("Britney Spears" to 192_000L),
            "slave 4 u" to ("Britney Spears" to 206_000L),
            "till the world ends" to ("Britney Spears" to 237_000L),
            "poker face" to ("Lady Gaga" to 237_000L),
            "bad romance" to ("Lady Gaga" to 294_000L),
            "just dance" to ("Lady Gaga" to 242_000L),
            "born this way" to ("Lady Gaga" to 260_000L),
            "shallow" to ("Lady Gaga" to 215_000L),
            "telephone" to ("Lady Gaga" to 218_000L),
            "paparazzi" to ("Lady Gaga" to 207_000L),
            "applause" to ("Lady Gaga" to 212_000L),
            "million reasons" to ("Lady Gaga" to 205_000L),
            "hotline bling" to ("Drake" to 267_000L),
            "gods plan" to ("Drake" to 198_000L),
            "one dance" to ("Drake" to 174_000L),
            "passionfruit" to ("Drake" to 269_000L),
            "started from the bottom" to ("Drake" to 280_000L),
            "take care" to ("Drake" to 276_000L),
            "hold on were going home" to ("Drake" to 227_000L),
            "too good" to ("Drake" to 263_000L),
            "nice for what" to ("Drake" to 210_000L),
            "controlla" to ("Drake" to 245_000L),
            "life is good" to ("Drake" to 238_000L),
            "laugh now cry later" to ("Drake" to 261_000L),
            "back to back" to ("Drake" to 198_000L),
            "the motto" to ("Drake" to 186_000L),
            "despacito" to ("Luis Fonsi" to 229_000L),
            "havana" to ("Camila Cabello" to 217_000L),
            "señorita" to ("Shawn Mendes" to 191_000L),
            "treat you better" to ("Shawn Mendes" to 187_000L),
            "stitches" to ("Shawn Mendes" to 207_000L),
            "theres nothing holdin me back" to ("Shawn Mendes" to 202_000L),
            "in my blood" to ("Shawn Mendes" to 211_000L),
            "wonder" to ("Shawn Mendes" to 171_000L)
        )

        fun resolve(normalizedQuery: String): ResolvedSong? {
            val nq = normalizedQuery.lowercase().trim()
            data[nq]?.let { return ResolvedSong(nq, it.first) }
            // Partial match: query starts with a known title, e.g. "down jay sean".
            data.keys.forEach { title ->
                if (nq.startsWith("$title ") || nq == title) {
                    return ResolvedSong(title, data.getValue(title).first)
                }
            }
            return null
        }

        fun getArtist(title: String): String? = data[normalize(title)]?.first
        fun getDuration(title: String): Long? = data[normalize(title)]?.second

        data class ResolvedSong(val title: String, val artist: String)

        private fun normalize(v: String) = v.lowercase().replace(Regex("[^a-z0-9\\s]"), "").trim()
    }
    private fun resolveRequestedVariant(query: String?): VariantClassifier.VariantType {
        if (query.isNullOrBlank()) return VariantClassifier.VariantType.UNKNOWN
        val q = query.lowercase()
        return when {
            "instrumental" in q || "inst " in q -> VariantClassifier.VariantType.INSTRUMENTAL
            "karaoke" in q -> VariantClassifier.VariantType.KARAOKE
            "piano" in q -> VariantClassifier.VariantType.PIANO
            "lullaby" in q -> VariantClassifier.VariantType.INSTRUMENTAL
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
