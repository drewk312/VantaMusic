package com.audiophile.musicplayer.data.display

import android.util.Log

data class DisplayMetadata(
    val title: String,
    val artist: String,
    val album: String?,
    val explicit: Boolean? = null,
    val reason: String,
    val qualityInfo: VantaQualityInfo? = null,
    val featuredArtists: List<String> = emptyList()
)

data class PlaybackDisplay(
    val title: String,
    val artist: String,
    val album: String? = null,
    val versionLabel: String? = null,
    val isAlternateVersion: Boolean = false,
    val featuredArtists: List<String> = emptyList()
)

object DisplayMetadataCleaner {

    fun cleanTitle(rawTitle: String): String {
        val suffixCleanedTitle = stripSuffixes(replaceUnderscoresWithSpaces(rawTitle)).let { it.ifBlank { rawTitle } }
        val artistTitle = parseArtistTitle(suffixCleanedTitle)
        if (artistTitle != null) {
            return replaceUnderscoresWithSpaces(stripSuffixes(artistTitle.second)).let { it.ifBlank { suffixCleanedTitle } }
        }
        return suffixCleanedTitle
    }

    fun computeDisplayTitleArtist(rawTitle: String, rawArtist: String, explicit: Boolean? = null): Pair<String, String> {
        val result = computeDisplayMetadata(rawTitle, rawArtist, null, explicit)
        return result.title to result.artist
    }

    fun cleanArtistName(artist: String?): String? {
        if (artist == null || artist.isBlank()) return artist
        val trimmed = replaceUnderscoresWithSpaces(artist)
        if (!trimmed.any { it.isLetter() }) return trimmed

        if (trimmed.any { it.isLowerCase() }) {
            var cleaned = trimmed
            for (suffix in listOf(" - Topic", " - VEVO", "VEVO", "- Topic", " Topic", "Official", "Music")) {
                if (cleaned.endsWith(suffix, ignoreCase = true)) {
                    cleaned = cleaned.substring(0, cleaned.length - suffix.length).trim()
                }
            }
            cleaned = cleaned.trimEnd('-', ' ', '.').trim()
            return cleaned.ifBlank { trimmed }
        }
        val words = trimmed.split(Regex("\\s+")).filter { it.isNotBlank() }
        val result = words.joinToString(" ") { normalizeWord(it) }
        return result
    }


    private val whitespaceRegex = Regex("\\s+")

    /**
     * Premium underscore → space conversion for display strings.
     *
     * - Replaces filename/URL-style underscore separators with spaces
     *   ("Song_Title_Here" → "Song Title Here").
     * - Collapses runs of underscores/whitespace into a single space
     *   ("Song__Title" → "Song Title").
     * - Preserves an underscore that sits directly between two digits
     *   (e.g. "8_000"), where it is usually intentional rather than a separator.
     * - Trims leftover leading/trailing whitespace.
     */
    private fun replaceUnderscoresWithSpaces(text: String): String {
        if (text.isEmpty()) return text
        val builder = StringBuilder(text.length)
        for (i in text.indices) {
            val current = text[i]
            if (current == '_') {
                val prev = text.getOrNull(i - 1)
                val next = text.getOrNull(i + 1)
                if (prev != null && next != null && prev.isDigit() && next.isDigit()) {
                    builder.append('_')
                } else {
                    builder.append(' ')
                }
            } else {
                builder.append(current)
            }
        }
        return builder.toString().replace(whitespaceRegex, " ").trim()
    }

    /**
     * Lightweight cleaner for album / playlist / artist display names that should
     * NOT go through full title parsing or suffix stripping. Only normalizes
     * underscores → spaces, collapses whitespace, and trims. Returns "" for
     * null/blank input so callers can `.ifBlank { fallback }`.
     */
    fun cleanDisplayName(raw: String?): String {
        if (raw.isNullOrBlank()) return ""
        return replaceUnderscoresWithSpaces(raw)
    }
    private fun normalizeWord(word: String): String {
        val lower = word.lowercase().removeSurrounding("\"", "\"").removeSurrounding("'", "'")
        if (lower in setOf("mc", "dj", "dj'")) {
            if (lower == "mc") return "MC"
            if (lower == "dj" || lower == "dj'") return "DJ"
        }
        if (lower == "lil" || lower == "lil'") return "Lil'"
        if (lower in setOf("ii", "iii", "iv", "v", "vi", "vii", "viii", "ix", "x")) return lower.uppercase()
        if (lower.startsWith("mc") && lower.length > 2) {
            return "MC${lower.substring(2).replaceFirstChar { it.uppercase() }}"
        }
        if (lower.startsWith("mac") && lower.length > 3 && lower[3].isLetter()) {
            return "Mac${lower.substring(3).replaceFirstChar { it.uppercase() }}"
        }
        if (lower.startsWith("o'") && lower.length > 2) {
            return "O'${lower.substring(2).replaceFirstChar { it.uppercase() }}"
        }
        if (lower in setOf("van", "von", "de", "del", "la", "le", "di", "da", "el")) return lower
        return lower.replaceFirstChar { it.uppercase() }
    }

    private val titleSuffixes = listOf(
        "(Official Audio)", "(Official Music Video)", "(Official Video)",
        "(Audio)", "(Lyric Video)", "(Lyrics)",
        "[Official Video]", "[Official Music Video]",
        "HD", "HQ", "Visualizer",
        "VEVO"
    )

    private val channelSuffixes = listOf("VEVO", "Official", "- Topic", " Topic", "Music", "YouTube", "YouTube Music")

    private val platformNames = listOf(
        "YouTube", "YouTube Music", "YTMusic", "Amazon Music", "Amazon",
        "Tidal", "Qobuz", "Deezer", "Spotify", "Apple Music", "Pandora",
        "SoundCloud", "Bandcamp", "TorBox", "Real-Debrid", "RealDebrid"
    )

    private val featurePattern = Regex(
        """(?i)\s*[\(\[]\s*(?:feat\.?|featuring|ft\.?|with)\s+([^\)\]]+)\s*[\)\]]|\s+\b(?:feat\.?|featuring|ft\.?)\s+(.+?)(?:\s*[\(\[]|$)"""
    )

    private val creditSplitPattern = Regex("""(?i)\s*(?:,|&|\band\b|\bvs\.?\b|\s+x\s+|\+)\s*""")

    private val commaBandNames = setOf(
        "earth, wind & fire",
        "earth, wind, and fire",
        "blood, sweat & tears",
        "crosby, stills & nash",
        "crosby, stills, nash & young",
        "emerson, lake & palmer"
    )

    private val duoBandNames = setOf(
        "simon & garfunkel",
        "hall & oates",
        "brooks & dunn",
        "brooks and dunn",
        "sam & dave",
        "sam and dave",
        "ike & tina turner",
        "ike and tina turner",
        "captain & tennille",
        "captain and tennille",
        "sonny & cher",
        "sonny and cher",
        "peaches & herb",
        "peaches and herb",
        "ashford & simpson",
        "ashford and simpson"
    )

    data class ArtistCredits(
        val primary: String,
        val featured: List<String>
    )

    /**
     * Apple-style collab parse: "Ella Langley & Morgan Wallen" → primary + featured.
     * Permanent duo/band names stay intact.
     */
    fun splitArtistCredits(rawArtist: String): ArtistCredits {
        val artist = replaceUnderscoresWithSpaces(rawArtist).trim()
        if (artist.isBlank()) return ArtistCredits("", emptyList())
        val normalized = artist.lowercase()
        if (duoBandNames.contains(normalized) || commaBandNames.any { normalized == it || normalized.startsWith("$it,") }) {
            return ArtistCredits(artist, emptyList())
        }

        val featMatch = featurePattern.find(artist)
        if (featMatch != null && featMatch.range.first > 0) {
            val primary = artist.substring(0, featMatch.range.first).trim()
            val raw = (featMatch.groups[1]?.value ?: featMatch.groups[2]?.value ?: "").trim()
            val featured = raw.split(creditSplitPattern)
                .map { cleanArtistName(it.trim()) ?: it.trim() }
                .filter { it.isNotBlank() }
                .distinct()
            return ArtistCredits(primary.ifBlank { artist }, featured)
        }

        val parts = artist.split(creditSplitPattern).map { it.trim() }.filter { it.isNotBlank() }
        if (parts.size < 2) return ArtistCredits(artist, emptyList())
        val allSingleWord = parts.all { it.split(Regex("\\s+")).size == 1 }
        if (allSingleWord && !artist.contains('&') && !artist.contains('+') &&
            !Regex("""(?i)\band\b|\bx\b""").containsMatchIn(artist)
        ) {
            return ArtistCredits(artist, emptyList())
        }
        val primary = parts.first()
        val featuredRaw = parts.drop(1)
            .map { cleanArtistName(it) ?: it }
            .filter { it.isNotBlank() && !it.equals(primary, ignoreCase = true) && looksLikePersonCredit(it) }
            .distinct()
        // Long comma lists are almost always liner-note dumps (Qobuz performers).
        val featured = if (parts.size > 3) featuredRaw.take(1) else featuredRaw.take(2)
        return ArtistCredits(primary, featured)
    }

    fun extractFeaturedArtists(rawTitle: String, rawArtist: String = ""): List<String> {
        val candidates = mutableListOf<String>()
        featurePattern.findAll(rawTitle).forEach { match ->
            val raw = (match.groups[1]?.value ?: match.groups[2]?.value ?: "").trim()
            if (raw.isNotBlank()) {
                raw.split(creditSplitPattern)
                    .map { it.trim() }
                    .filter { it.isNotBlank() }
                    .forEach { candidates.add(it) }
            }
        }
        splitArtistCredits(rawArtist).featured.forEach { candidates.add(it) }
        return candidates
            .map { cleanArtistName(it) ?: it }
            .filter { it.isNotBlank() && looksLikePersonCredit(it) }
            .distinct()
            .take(2)
    }

    private fun looksLikePersonCredit(name: String): Boolean {
        val cleaned = name.trim()
        if (cleaned.isBlank()) return false
        val lower = cleaned.lowercase()
        if (lower in setOf(
                "electric guitar", "acoustic guitar", "bass guitar", "pedal steel guitar",
                "drums", "percussion", "mandolin", "piano", "vocals", "bass", "guitar",
                "project coordinator", "producer", "engineer", "mixer"
            )
        ) return false
        if (Regex("""(?i)^(electric|acoustic|bass|pedal\s*steel)?\s*(guitar|drums?|percussion|mandolin|piano|coordinator|engineer|producer|mixer)$""")
                .matches(cleaned)
        ) return false
        return true
    }

    fun cleanAlbumName(album: String?): String? {
        if (album.isNullOrBlank()) return null
        val trimmed = replaceUnderscoresWithSpaces(album)
        if (trimmed.isBlank()) return null
        for (platform in platformNames) {
            if (trimmed.equals(platform, ignoreCase = true)) return null
        }
        return trimmed
    }

    fun computeDisplayMetadata(
        rawTitle: String,
        rawArtist: String,
        rawAlbum: String?,
        explicit: Boolean? = null,
        providerId: String? = null
    ): DisplayMetadata {
        val artistCredits = splitArtistCredits(rawArtist)
        val featuredArtists = (
            extractFeaturedArtists(rawTitle, rawArtist) + artistCredits.featured
            )
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinctBy { it.lowercase() }
        val suffixCleanedTitle = stripSuffixes(rawTitle).let { it.ifBlank { rawTitle } }
        val artistTitle = parseArtistTitle(suffixCleanedTitle)

        if (artistTitle != null) {
            val (parsedArtist, parsedSong) = artistTitle
            val displayArtistRaw = cleanChannelName(
                artistCredits.primary.ifBlank { rawArtist },
                splitArtistCredits(parsedArtist).primary.ifBlank { parsedArtist }
            )
            val displayArtist = enrichArtistWithFeatured(displayArtistRaw, featuredArtists)
            val displayTitle = replaceUnderscoresWithSpaces(stripSuffixes(parsedSong))
            if (displayTitle.isNotBlank()) {
                Log.d("VANTA_METADATA_CLEAN",
                    "rawTitle='${rawTitle}' rawArtist='${rawArtist}' displayTitle='${displayTitle}' displayArtist='${displayArtist}' surface='DisplayMetadataCleaner' reason='youtube_title_artist_parse'")
                return DisplayMetadata(
                    title = displayTitle,
                    artist = displayArtist,
                    album = cleanAlbumName(rawAlbum),
                    explicit = explicit,
                    reason = "youtube_title_artist_parse",
                    featuredArtists = featuredArtists
                )
            }
        }

        val primaryBase = artistCredits.primary.ifBlank { cleanChannelName(rawArtist, null) }
        val finalTitle = replaceUnderscoresWithSpaces(suffixCleanedTitle)
        val finalArtist = enrichArtistWithFeatured(primaryBase, featuredArtists)
        val reason = if (finalTitle != rawTitle) "youtube_strip_suffixes" else "raw_provider"

        Log.d("VANTA_METADATA_CLEAN",
            "rawTitle='${rawTitle}' rawArtist='${rawArtist}' displayTitle='${finalTitle}' displayArtist='${finalArtist}' surface='DisplayMetadataCleaner' reason='${reason}'")

        return DisplayMetadata(
            title = finalTitle,
            artist = finalArtist,
            album = cleanAlbumName(rawAlbum),
            explicit = explicit,
            reason = reason,
            featuredArtists = featuredArtists
        )
    }

    private fun enrichArtistWithFeatured(finalArtist: String?, featuredArtists: List<String>): String {
        val base = finalArtist?.ifBlank { null } ?: return featuredArtists.joinToString(", ")
        val distinctFeatured = featuredArtists
            .map { replaceUnderscoresWithSpaces(it).trim() }
            .filter { it.isNotBlank() }
            .distinct()
            .filter { featured ->
                !base.split(",").any { segment ->
                    segment.trim().equals(featured, ignoreCase = true)
                }
            }
        if (distinctFeatured.isEmpty()) return base
        return "$base feat. ${distinctFeatured.joinToString(", ")}"
    }

    fun parseArtistTitle(title: String): Pair<String, String>? {
        for (sep in listOf(" - ", " ~ ", " ~")) {
            val idx = title.indexOf(sep)
            if (idx > 0 && idx < title.length - sep.length) {
                val candidateArtist = title.substring(0, idx).trim()
                val candidateTitle = title.substring(idx + sep.length).trim()
                if (candidateArtist.isNotBlank() && candidateTitle.isNotBlank() && !looksLikeChannelName(candidateArtist)) {
                    return candidateArtist to candidateTitle
                }
            }
        }
        return null
    }

    private fun looksLikeChannelName(artist: String): Boolean {
        val lower = artist.lowercase()
        if (platformNames.any { lower == it.lowercase() }) return true
        if (channelSuffixes.any { lower.endsWith(it.lowercase()) }) return true
        // reject generic aggregator names like "various artists" parsed from title
        if (lower in setOf("various artists", "unknown artist", "music", "audio")) return true
        return false
    }

    fun cleanMiniBarTitle(rawTitle: String): String {
        return rawTitle
            .replace(Regex("""\s*\[.*?\]\s*"""), "")
            .replace(Regex("""\s*\((?:Alternate|Radio|Album|Single|Extended|Remix|Live|Demo|Acoustic|Instrumental|Edit|Mix|Version|Bonus|Deluxe|Remaster)[^)]*\)\s*""", RegexOption.IGNORE_CASE), "")
            .trim()
            .ifBlank { rawTitle }
    }

    fun stripSuffixes(text: String): String {
        var result = text.trim()
        // Video catalog titles sometimes append a quoted lyric teaser after the
        // presentation label, for example: Song (Lyrics) "first lyric line".
        // Everything from that bracketed label onward is packaging, not title.
        result = result.replace(
            Regex(
                """(?i)\s*[\(\[]\s*(?:lyrics?|lyric\s+video|official\s+(?:audio|music\s+video|video)|audio|visualizer)\s*[\)\]].*$"""
            ),
            ""
        ).trim()
        // Strip known platform/suffix labels
        for (suffix in titleSuffixes) {
            while (result.endsWith(suffix, ignoreCase = true)) {
                result = result.substring(0, result.length - suffix.length).trim()
            }
        }
        // Strip featured-artist suffixes after we have extracted them separately
        result = result.replace(
            Regex(
                """(?i)\s*[\(\[]\s*(?:feat\.?|featuring|ft\.?|with)\s+[^\)\]]+\s*[\)\]]"""
            ),
            ""
        ).trim()
        result = result.replace(
            Regex(
                pattern = """(?i)\s*[-–—]\s*(with\s+)?(lyrics?|lyric\s+video|official\s+(audio|music\s+video|video)|audio|visualizer|hq|hd)[!?\.\s]*$"""
            ),
            ""
        ).trim()
        val providedIdx = result.indexOf("Provided to YouTube by", ignoreCase = true)
        if (providedIdx >= 0) {
            result = result.substring(0, providedIdx).trim()
        }
        return result
    }

    private val versionKeywordPattern =
        Regex("(?i)(remix|rework|edit|bootleg|flip|vip|extended|slowed|sped\\s*up|acoustic|live)")

    fun extractVersionLabel(rawTitle: String, extraHint: String? = null): String? {
        val sources = listOfNotNull(rawTitle, extraHint)
        for (source in sources) {
            val bracketMatch = Regex("""[\(\[]([^\)\]]+)[\)\]]""").findAll(source)
            for (match in bracketMatch) {
                val inner = match.groupValues[1].trim()
                if (inner.isNotBlank() && versionKeywordPattern.containsMatchIn(inner)) {
                    return inner
                }
            }
            val dashMatch = Regex("""(?i)\s[-–—]\s+(.+)$""").find(source.trim())
            if (dashMatch != null) {
                val tail = dashMatch.groupValues[1].trim()
                if (versionKeywordPattern.containsMatchIn(tail)) {
                    return tail
                }
            }
        }
        return null
    }

    fun computePlaybackDisplay(
        rawTitle: String,
        rawArtist: String,
        rawAlbum: String?,
        streamHint: String? = null
    ): PlaybackDisplay {
        val display = computeDisplayMetadata(rawTitle, rawArtist, rawAlbum)
        val versionLabel = extractVersionLabel(rawTitle, streamHint)
        val title = if (versionLabel != null) {
            val stripped = rawTitle
                .replace(Regex("""[\(\[]\s*${Regex.escape(versionLabel)}\s*[\)\]]""", RegexOption.IGNORE_CASE), "")
                .replace(Regex("""\s[-–—]\s*${Regex.escape(versionLabel)}\s*$""", RegexOption.IGNORE_CASE), "")
                .trim()
            val cleaned = computeDisplayMetadata(stripped, rawArtist, rawAlbum)
            cleaned.title
        } else {
            display.title
        }
        return PlaybackDisplay(
            title = title,
            artist = display.artist,
            album = display.album,
            versionLabel = versionLabel,
            isAlternateVersion = versionLabel != null,
            featuredArtists = display.featuredArtists
        )
    }

    fun cleanChannelName(rawArtist: String, parsedArtist: String?): String {
        if (parsedArtist != null && parsedArtist.isNotBlank()) {
            return replaceUnderscoresWithSpaces(parsedArtist)
        }
        var artist = replaceUnderscoresWithSpaces(rawArtist.trim())
        for (suffix in channelSuffixes) {
            if (artist.endsWith(suffix, ignoreCase = true)) {
                artist = artist.substring(0, artist.length - suffix.length).trim()
            }
        }
        return artist.ifBlank { replaceUnderscoresWithSpaces(rawArtist.trim()) }
    }
}

