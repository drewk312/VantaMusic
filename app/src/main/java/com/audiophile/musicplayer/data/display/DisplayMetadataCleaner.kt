package com.audiophile.musicplayer.data.display

import android.util.Log

data class DisplayMetadata(
    val title: String,
    val artist: String,
    val album: String?,
    val explicit: Boolean? = null,
    val reason: String,
    val qualityInfo: VantaQualityInfo? = null
)

data class PlaybackDisplay(
    val title: String,
    val artist: String,
    val versionLabel: String? = null,
    val isAlternateVersion: Boolean = false
)

object DisplayMetadataCleaner {

    fun cleanTitle(rawTitle: String): String {
        val suffixCleanedTitle = stripSuffixes(rawTitle).let { it.ifBlank { rawTitle } }
        val artistTitle = parseArtistTitle(suffixCleanedTitle)
        if (artistTitle != null) {
            return stripSuffixes(artistTitle.second).let { it.ifBlank { suffixCleanedTitle } }
        }
        return suffixCleanedTitle
    }

    fun computeDisplayTitleArtist(rawTitle: String, rawArtist: String, explicit: Boolean? = null): Pair<String, String> {
        val result = computeDisplayMetadata(rawTitle, rawArtist, null, explicit)
        return result.title to result.artist
    }

    fun cleanArtistName(artist: String?): String? {
        if (artist == null || artist.isBlank()) return artist
        if (!artist.any { it.isLetter() }) return artist
        val trimmed = artist.trim()
        if (trimmed.any { it.isLowerCase() }) {
            var cleaned = trimmed
            for (suffix in listOf(" - Topic", " - VEVO", "VEVO", "- Topic", " Topic", "Official", "Music")) {
                if (cleaned.endsWith(suffix, ignoreCase = true)) {
                    cleaned = cleaned.substring(0, cleaned.length - suffix.length).trim()
                }
            }
            cleaned = cleaned.trimEnd('-', ' ').trim()
            return cleaned.ifBlank { trimmed }
        }
        val words = trimmed.split(Regex("\\s+")).filter { it.isNotBlank() }
        val result = words.joinToString(" ") { normalizeWord(it) }
        return result
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
        val suffixCleanedTitle = stripSuffixes(rawTitle).let { it.ifBlank { rawTitle } }
        val artistTitle = parseArtistTitle(suffixCleanedTitle)

        if (artistTitle != null) {
            val (parsedArtist, parsedSong) = artistTitle
            val displayArtist = cleanChannelName(rawArtist, parsedArtist)
            val displayTitle = stripSuffixes(parsedSong)
            if (displayTitle.isNotBlank()) {
                Log.d("VANTA_METADATA_CLEAN",
                    "rawTitle='${rawTitle}' rawArtist='${rawArtist}' displayTitle='${displayTitle}' displayArtist='${displayArtist}' surface='DisplayMetadataCleaner' reason='youtube_title_artist_parse'")
                return DisplayMetadata(title = displayTitle, artist = displayArtist, album = cleanAlbumName(rawAlbum), explicit = explicit, reason = "youtube_title_artist_parse")
            }
        }

        val finalTitle = suffixCleanedTitle
        val finalArtist = cleanChannelName(rawArtist, null)
        val reason = if (finalTitle != rawTitle) "youtube_strip_suffixes" else "raw_provider"

        Log.d("VANTA_METADATA_CLEAN",
            "rawTitle='${rawTitle}' rawArtist='${rawArtist}' displayTitle='${finalTitle}' displayArtist='${finalArtist}' surface='DisplayMetadataCleaner' reason='${reason}'")

        return DisplayMetadata(title = finalTitle, artist = finalArtist, album = cleanAlbumName(rawAlbum), explicit = explicit, reason = reason)
    }

    fun parseArtistTitle(title: String): Pair<String, String>? {
        for (sep in listOf(" - ", " ~ ", " ~")) {
            val idx = title.indexOf(sep)
            if (idx > 0 && idx < title.length - sep.length) {
                val candidateArtist = title.substring(0, idx).trim()
                val candidateTitle = title.substring(idx + sep.length).trim()
                if (candidateArtist.isNotBlank() && candidateTitle.isNotBlank()) {
                    return candidateArtist to candidateTitle
                }
            }
        }
        return null
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
        for (suffix in titleSuffixes) {
            while (result.endsWith(suffix, ignoreCase = true)) {
                result = result.substring(0, result.length - suffix.length).trim()
            }
        }
        result = result.replace(
            Regex(
                pattern = """(?i)\s*[-–—]\s*(with\s+)?(lyrics?|lyric\s+video|official\s+(audio|music\s+video|video)|audio|visualizer|hq|hd)[!?.\s]*$"""
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
            isAlternateVersion = versionLabel != null
        )
    }

    fun cleanChannelName(rawArtist: String, parsedArtist: String?): String {
        if (parsedArtist != null && parsedArtist.isNotBlank()) {
            return parsedArtist
        }
        var artist = rawArtist.trim()
        for (suffix in channelSuffixes) {
            if (artist.endsWith(suffix, ignoreCase = true)) {
                artist = artist.substring(0, artist.length - suffix.length).trim()
            }
        }
        return artist.ifBlank { rawArtist.trim() }
    }
}
