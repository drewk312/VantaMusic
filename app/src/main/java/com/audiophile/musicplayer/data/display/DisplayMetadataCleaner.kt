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


    private fun replaceUnderscoresWithSpaces(text: String): String {
        return text.replace('_', ' ').trim()
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
        """(?i)\s*[\(\[]\s*(?:feat\.?|featuring|ft\.?|with)\s+([^\)\]]+)\s*[\)\]]|\s+\b(?:feat\.?|featuring|ft\.?|with)\s+(.+?)(?:\s*[\(\[]|$)"""
    )

    fun extractFeaturedArtists(rawTitle: String, rawArtist: String = ""): List<String> {
        val candidates = mutableListOf<String>()
        featurePattern.findAll("$rawTitle $rawArtist").forEach { match ->
            val raw = (match.groups[1]?.value ?: match.groups[2]?.value ?: "").trim()
            if (raw.isNotBlank()) {
                // Split on common separators like &, and, vs, comma
                raw.split(Regex("""(?i)\s*(?:,|\&|and|vs\.?|x)\s*"""))
                    .map { it.trim() }
                    .filter { it.isNotBlank() }
                    .forEach { candidates.add(it) }
            }
        }
        return candidates
            .map { cleanArtistName(it) ?: it }
            .filter { it.isNotBlank() }
            .distinct()
    }

    fun cleanAlbumName(album: String?): String? {
        if (album.isNullOrBlank()) return null
        val trimmed = album.trim()
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
        val featuredArtists = extractFeaturedArtists(rawTitle, rawArtist)
        val suffixCleanedTitle = stripSuffixes(rawTitle).let { it.ifBlank { rawTitle } }
        val artistTitle = parseArtistTitle(suffixCleanedTitle)

        val enrichedArtist = enrichArtistWithFeatured(finalArtist = null, featuredArtists = featuredArtists)

        if (artistTitle != null) {
            val (parsedArtist, parsedSong) = artistTitle
            val displayArtistRaw = cleanChannelName(rawArtist, parsedArtist)
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

        val finalTitle = replaceUnderscoresWithSpaces(suffixCleanedTitle)
        val finalArtist = enrichArtistWithFeatured(cleanChannelName(rawArtist, null), featuredArtists)
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

