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
            val category = detectCategory(track, normalizedQuery)
            
            when (category) {
                Category.ARTIST -> {
                    val artistName = track.artist.ifBlank { track.title }.trim()
                    val key = normalize(artistName)
                    if (key.isNotBlank() && !artists.containsKey(key)) {
                        artists[key] = CanonicalArtist(
                            name = artistName,
                            id = track.externalTrackId ?: "resolved:artist:$key",
                            genre = track.genre,
                            artworkUrl = track.artworkUrl
                        )
                    }
                }
                Category.ALBUM -> {
                    val albumTitle = track.album?.trim().orEmpty().ifBlank { track.title.trim() }
                    val artistName = track.artist.trim()
                    val key = normalize("$albumTitle|$artistName")
                    if (albumTitle.isNotBlank() && !albums.containsKey(key)) {
                        albums[key] = CanonicalAlbum(
                            title = albumTitle,
                            artist = artistName,
                            id = track.externalTrackId ?: "resolved:album:$key",
                            artworkUrl = track.artworkUrl,
                            releaseYear = track.releaseYear,
                            genre = track.genre,
                            trackCount = null
                        )
                    }
                }
                Category.SONG -> {
                    if (track.isLikelyMusicTrack() || track.sourceStatus == SearchItemStatus.METADATA_ONLY) {
                        val key = "${normalize(track.title)}|${normalize(track.artist)}"
                        if (seenSongKeys.add(key)) {
                            songs.add(track)
                        }
                    }
                }
            }
        }

        // 4. Select Top Result
        val topCandidate = scoredTracks
            .filter { it.second.eligibleForTop }
            .map { it.first }
            .firstOrNull() ?: songs.firstOrNull()

        // SSS Rule: If we have a famous song fallback, it SHOULD be the top result if it scored well
        val topResult = topCandidate
        
        val topScore = scoredTracks.firstOrNull { it.first == topResult }?.second?.finalScore ?: 0
        val identityMatch = topScore >= IDENTITY_THRESHOLD

        return UnifiedSearchResponse(
            topResult = topResult,
            songs = songs,
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

        return SearchQueryIntent(raw, queryForParsing, null, featured)
    }

    /**
     * If the user typed "Down Jay Sean feat Lil Wayne" and the registry says the title is "down",
     * then "jay sean" is what the user typed as the artist. Returns null if nothing extra remains.
     */
    private fun extractTypedArtist(normalizedQuery: String, normalizedTitle: String): String? {
        val titleWords = normalizedTitle.split(" ").filter { it.isNotBlank() }
        val queryWords = normalizedQuery.split(" ").filter { it.isNotBlank() }
        // Query must start with the title words and have at least one extra word for the artist.
        if (queryWords.size <= titleWords.size) return null
        if (!queryWords.take(titleWords.size).equals(titleWords)) return null
        val artistWords = queryWords.drop(titleWords.size)
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

        // Artist Score
        val artistMatch = when {
            expectedArtist != null && (normArtist == expectedArtist || normArtist.contains(expectedArtist)) -> true
            expectedArtist != null && expectedArtist.contains(normArtist) && normArtist.length > 3 -> true
            else -> false
        }
        score += when {
            artistMatch -> 100
            expectedArtist != null && expectedArtist.contains(normArtist) && normArtist.length > 3 -> 40
            else -> 0
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
                    score += 500
                }
            }
        }

        // Variant handling: if the user explicitly asked for a variant, boost tracks that ARE
        // that variant so the right recording can beat the canonical studio version.
        val trackVariant = VariantClassifier.classify(title, artist, track.album, intent.rawQuery).variantType
        val requestedVariant = resolveRequestedVariant(intent.rawQuery)
        if (requestedVariant != VariantClassifier.VariantType.STUDIO_VOCAL &&
            requestedVariant != VariantClassifier.VariantType.UNKNOWN &&
            trackVariant == requestedVariant
        ) {
            score += 700
        }

        // Variant Penalties
        val (rejected, _) = VariantClassifier.isRejectedForStudioIntent(title, artist, track.album, intent.rawQuery)
        if (rejected) score -= 200

        // Professionalism Bonus (penalize uploaders masquerading as artists)
        val uploaderMarkers = listOf("7clouds", "vevo", "topic", "records", "official audio", "lyrics")
        if (uploaderMarkers.any { it in normArtist }) score -= 40
        
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
            (expectedArtist == null || artistMatch || isFamousArtistMatch(expectedTitle, normArtist))

        return Evaluation(score, eligible)
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
            "down" to ("Jay Sean" to 212_000L)
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
