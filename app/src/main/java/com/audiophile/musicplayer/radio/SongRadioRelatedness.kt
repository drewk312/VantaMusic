package com.audiophile.musicplayer.radio

/**
 * Song Radio relatedness gates.
 *
 * Prevents lexical collapse where shared title/artist tokens (Weeknd→Weekend,
 * Lights→Neon Lights) dominate over artist/genre/similarity anchors.
 */
object SongRadioRelatedness {

    private val stopTokens = setOf(
        "the", "a", "an", "and", "or", "of", "to", "for", "on", "in", "at", "by",
        "with", "from", "my", "your", "our", "me", "you", "we", "it", "is", "are",
        "be", "as", "vs", "feat", "ft", "featuring", "remix", "mix", "edit", "version",
        "radio", "official", "audio", "video", "lyrics", "song", "songs", "music"
    )

    /** High-frequency title tokens that alone never prove musical relatedness. */
    private val weakTitleTokens = setOf(
        "love", "life", "night", "day", "time", "heart", "baby", "girl", "boy",
        "world", "dream", "dreams", "light", "lights", "dark", "fire", "rain",
        "sun", "moon", "star", "stars", "home", "away", "back", "again", "one",
        "two", "good", "bad", "best", "new", "old", "real", "true", "free",
        "weekend", "monday", "friday", "saturday", "sunday", "tonight", "today",
        "forever", "never", "always", "everything", "nothing", "somebody", "anyone"
    )

    private val listicleAlbumMarkers = listOf(
        "now that's what i call", "now thats what i call",
        "greatest hits of the", "best of the 20", "hits of the 20",
        "top 40", "top40", "billboard", "chart toppers", "chart hits",
        "songs you need", "tracks you need", "songs to listen",
        "workout hits", "party hits", "driving songs", "road trip hits",
        "various artists", "va -", "compiled by", "listicle",
        "brunch playlist", "cafe playlist", "café playlist", "mixtape",
        "hits weekend", "weekend playlist", "weekend brunch",
        "party mixtape", "summer hits", "hits playlist"
    )

    fun isSongRadioKind(kind: StreamingStationKind): Boolean =
        kind == StreamingStationKind.SONG || kind == StreamingStationKind.SONG_SIMILAR

    /**
     * Rejects artist-name collisions (The Weekend / Weeknd) and near-homophone
     * title/album spam derived from the seed artist name.
     */
    fun isArtistNameCollision(
        seedArtist: String?,
        candidateTitle: String,
        candidateArtist: String,
        candidateAlbum: String? = null
    ): Boolean {
        val seed = seedArtist?.trim().orEmpty()
        if (seed.isBlank()) return false
        if (artistsMatch(seed, candidateArtist)) return false

        val seedTokens = distinctiveTokens(seed).filter { it.length >= 5 }
        if (seedTokens.isEmpty()) return false

        val titleTokens = distinctiveTokens(candidateTitle)
        val artistTokens = distinctiveTokens(candidateArtist)
        val albumTokens = distinctiveTokens(candidateAlbum.orEmpty())

        // Near-homophone only (dist ≥ 1): Weeknd ↔ Weekend.
        // Exact token equals are ignored here to avoid Imagine Dragons → "Imagine".
        for (seedToken in seedTokens) {
            if (titleTokens.any { nearHomophone(it, seedToken, allowExact = false) }) return true
            if (artistTokens.any { nearHomophone(it, seedToken, allowExact = false) }) return true
            // Album "Weekend Brunch Playlist" / "Hits Weekend Playlist" collapse.
            if (albumTokens.any { nearHomophone(it, seedToken, allowExact = false) }) return true
        }

        // Whole-name collision: "The Weekend" ≈ "The Weeknd" (exact or near).
        val seedNorm = normalizePhrase(seed)
        if (nearHomophone(normalizePhrase(candidateTitle), seedNorm, allowExact = true)) return true
        if (nearHomophone(normalizePhrase(candidateArtist), seedNorm, allowExact = true)) return true

        return false
    }

    /**
     * Rejects candidates whose only link to the seed is a weak shared title token
     * (e.g. Lights, Love, Weekend) and who are not the seed artist.
     */
    fun isWeakTitleTokenSpam(
        seedTitle: String?,
        seedArtist: String?,
        candidateTitle: String,
        candidateArtist: String
    ): Boolean {
        val seed = seedTitle?.trim().orEmpty()
        if (seed.isBlank()) return false
        if (artistsMatch(seedArtist.orEmpty(), candidateArtist)) return false

        val seedTokens = distinctiveTokens(seed)
        val candTokens = distinctiveTokens(candidateTitle)
        if (seedTokens.isEmpty() || candTokens.isEmpty()) return false

        val shared = seedTokens.intersect(candTokens)
        if (shared.isEmpty()) return false

        // Exact / near-exact title match is a version of the seed, not spam.
        if (nearHomophone(normalizePhrase(seed), normalizePhrase(candidateTitle), allowExact = true)) {
            return false
        }

        val strongShared = shared.filter { token ->
            token.length >= 5 && token !in weakTitleTokens
        }
        // Only weak/common tokens shared → lexical spam, not musical relatedness.
        return strongShared.isEmpty()
    }

    /**
     * Covers of *other* catalog hits (not the seed): e.g. "Hymn for the Weekend"
     * cover while seed is Blinding Lights.
     */
    fun isForeignHitCover(
        seedTitle: String?,
        candidateTitle: String,
        candidateArtist: String,
        seedArtist: String?
    ): Boolean {
        if (artistsMatch(seedArtist.orEmpty(), candidateArtist)) return false
        val seed = seedTitle?.trim().orEmpty()
        val title = candidateTitle.lowercase()
        val coverCue = listOf(
            "cover", "tribute", "made famous", "in the style of",
            "originally performed", "karaoke", "rendition"
        ).any { title.contains(it) }
        if (!coverCue) {
            // Soft cover cue: different artist performing a clearly different known title
            // while also colliding on seed-artist homophone (handled elsewhere).
            return false
        }
        // Cover of something other than the seed title.
        if (seed.isBlank()) return true
        return !normalizePhrase(candidateTitle).contains(normalizePhrase(seed)) &&
            !normalizePhrase(seed).contains(normalizePhrase(candidateTitle).take(12))
    }

    fun isListicleOrCompilationAlbum(title: String, artist: String, album: String?): Boolean {
        val haystack = "$title $artist ${album.orEmpty()}".lowercase()
        if (listicleAlbumMarkers.any { haystack.contains(it) }) return true
        val albumLower = album.orEmpty().lowercase()
        if (albumLower.contains("various artists") || albumLower.contains("greatest hits compilation")) {
            return true
        }
        if (artist.lowercase().trim() in setOf("various artists", "various", "va", "compilation")) {
            return true
        }
        return false
    }

    /**
     * "The Weeknd Reveals How to Write a Hit Song…" / type-beat spam that names the
     * seed artist in the title while credited to a different channel.
     */
    fun isSeedArtistMentionedByOtherArtist(
        seedArtist: String?,
        candidateTitle: String,
        candidateArtist: String
    ): Boolean {
        val seed = seedArtist?.trim().orEmpty()
        if (seed.isBlank()) return false
        if (artistsMatch(seed, candidateArtist)) return false
        val seedNorm = normalizePhrase(seed)
        val titleNorm = normalizePhrase(candidateTitle)
        if (seedNorm.length >= 5 && titleNorm.contains(seedNorm)) return true
        val seedTokens = distinctiveTokens(seed).filter { it.length >= 5 }
        val titleTokens = distinctiveTokens(candidateTitle)
        return seedTokens.any { it in titleTokens }
    }

    /** Mix/cafe/tribute acts that impersonate catalog hits for Song Radio. */
    fun isCoverActArtist(artist: String): Boolean {
        val a = artist.lowercase().trim()
        if (a.isBlank()) return false
        val markers = listOf(
            "grandmix", "grand mix", "tribute", "karaoke", "cover band", "cover crew",
            "playlist", "cafe jazz", "café jazz", "jazz cafe", "jazz café",
            "lounge orchestra", "string quartet", "vitamin string",
            "workout hits", "fitness beats", "style pack", "sound-a-like", "sound alike",
            "songaholic", "type beat", "kar play"
        )
        if (markers.any { a.contains(it) }) return true
        if (a.endsWith(" mix") || a.endsWith(" mixes")) return true
        return false
    }

    fun artistsMatch(a: String, b: String): Boolean {
        val left = normalizePhrase(a)
        val right = normalizePhrase(b)
        if (left.isBlank() || right.isBlank()) return false
        if (left == right) return true
        if (left.contains(right) || right.contains(left)) {
            // Avoid "the" / tiny substring false positives.
            val shorter = if (left.length <= right.length) left else right
            return shorter.length >= 5
        }
        return false
    }

    fun distinctiveTokens(text: String): Set<String> =
        text.lowercase()
            .replace(Regex("""[^\p{L}\p{N}\s]+"""), " ")
            .split(Regex("""\s+"""))
            .map { it.trim() }
            .filter { it.length >= 3 && it !in stopTokens }
            .toSet()

    fun normalizePhrase(text: String): String =
        text.lowercase()
            .replace(Regex("""[^\p{L}\p{N}\s]+"""), " ")
            .replace(Regex("""\s+"""), " ")
            .trim()

    /**
     * Near-homophone / edit-distance gate for artist-token collisions.
     * @param allowExact when true, identical strings count as a match (full-name checks).
     *                   when false, only misspellings/homophones (Weeknd↔Weekend).
     */
    fun nearHomophone(a: String, b: String, allowExact: Boolean = true): Boolean {
        if (a.isBlank() || b.isBlank()) return false
        if (a == b) return allowExact
        val minLen = minOf(a.length, b.length)
        if (minLen < 5) return false
        val dist = levenshtein(a, b)
        // weeknd↔weekend (dist 1), colour↔color (dist 1), etc.
        val maxDist = if (minLen >= 7) 2 else 1
        return dist in 1..maxDist
    }

    private fun levenshtein(left: String, right: String): Int {
        if (left == right) return 0
        if (left.isEmpty()) return right.length
        if (right.isEmpty()) return left.length
        val prev = IntArray(right.length + 1) { it }
        val curr = IntArray(right.length + 1)
        for (i in 1..left.length) {
            curr[0] = i
            for (j in 1..right.length) {
                val cost = if (left[i - 1] == right[j - 1]) 0 else 1
                curr[j] = minOf(
                    curr[j - 1] + 1,
                    prev[j] + 1,
                    prev[j - 1] + cost
                )
            }
            for (j in prev.indices) prev[j] = curr[j]
        }
        return prev[right.length]
    }
}
