package com.audiophile.musicplayer.data.importer

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.net.URI
import java.util.Locale

data class ParsedPlaylistLine(
    val rawLine: String,
    val title: String? = null,
    val artist: String? = null,
    val album: String? = null,
    val sourceUrl: String? = null,
    val sourcePlatform: String? = null,
    val sourceId: String? = null,
    val preserved: Boolean = false
)

object SoundiizTextParser {
    private val byPattern = Regex("""^(.+?)\s+by\s+(.+)$""", RegexOption.IGNORE_CASE)
    private val listPrefixPattern = Regex("""^\s*(?:[-*•]\s+|\d+[\.)]\s+)(.+)$""")
    private val urlPattern = Regex("""https?://[^\s"'<>]+""", RegexOption.IGNORE_CASE)
    private val knownArtistHints = setOf(
        "a-ha",
        "artist",
        "artist name",
        "dua lipa",
        "olivia rodrigo",
        "taylor swift",
        "the weeknd"
    )

    fun parseBlock(block: String): List<ParsedPlaylistLine> {
        parseM3uBlock(block)?.let { return it }
        parseJsonBlock(block)?.let { return it }
        parseXmlBlock(block)?.let { return it }
        parseCsvBlock(block)?.let { return it }

        return block
            .lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .map { parseLine(it) }
            .toList()
    }

    fun parseLine(line: String): ParsedPlaylistLine {
        val normalizedLine = stripListPrefix(line.trim())
        parseUrlLine(normalizedLine, line)?.let { return it }
        parseDashLine(normalizedLine, line)?.let { return it }
        parseCommaLine(normalizedLine, line)?.let { return it }
        parseByLine(normalizedLine, line)?.let { return it }
        return ParsedPlaylistLine(rawLine = line, preserved = true)
    }

    fun detectFormat(block: String): String {
        val parsed = parseBlock(block)
        if (parsed.isEmpty()) return "Empty"
        val rawLines = block.lineSequence().map { it.trim() }.filter { it.isNotBlank() }.toList()
        return when {
            rawLines.any { it.startsWith("#EXTM3U", ignoreCase = true) || it.startsWith("#EXTINF", ignoreCase = true) } -> "M3U playlist"
            rawLines.any { listPrefixPattern.matches(it) } -> "Numbered or bulleted text"
            rawLines.any { urlPattern.containsMatchIn(it) } -> "Platform links"
            rawLines.firstOrNull()?.startsWith("{") == true || rawLines.firstOrNull()?.startsWith("[") == true -> "JSON export"
            rawLines.firstOrNull()?.startsWith("<") == true -> "XML export"
            rawLines.any { it.count { ch -> ch == ',' } >= 2 } -> "CSV-like text"
            rawLines.any { " by " in it.lowercase() } -> "Plain text with by"
            rawLines.any { " - " in it } -> "Dash-separated text"
            rawLines.any { "," in it } -> "Comma-separated text"
            else -> "Plain text"
        }
    }

    private fun parseUrlLine(line: String, rawLine: String): ParsedPlaylistLine? {
        val url = urlPattern.find(line)?.value?.trimEnd('.', ',', ')', ']', '}') ?: return null
        val platform = platformName(url)
        val linkMetadata = parsePlatformUrl(url)
        val withoutUrl = line.replace(url, " ").trim()
        val parsedText = if (withoutUrl.length >= 3) {
            parseDashLine(withoutUrl, rawLine)
                ?: parseCommaLine(withoutUrl, rawLine)
                ?: parseByLine(withoutUrl, rawLine)
        } else {
            null
        }
        return ParsedPlaylistLine(
            rawLine = rawLine,
            title = parsedText?.title ?: linkMetadata?.title,
            artist = parsedText?.artist ?: linkMetadata?.artist,
            album = parsedText?.album ?: linkMetadata?.album,
            sourceUrl = url,
            sourcePlatform = platform,
            sourceId = linkMetadata?.sourceId,
            preserved = parsedText == null && linkMetadata?.title == null
        )
    }

    private fun parseDashLine(line: String, rawLine: String): ParsedPlaylistLine? {
        val parts = line.split(" - ", limit = 2)
        if (parts.size != 2) return null

        val left = parts[0].trim()
        val right = parts[1].trim()
        if (left.isEmpty() || right.isEmpty()) return null

        val artistIsLikelyOnRight = isLikelyArtist(right)
        val artistIsLikelyOnLeft = isLikelyArtist(left)
        val artist = if (artistIsLikelyOnRight) right else left
        val title = if (artistIsLikelyOnRight) left else right
        if (artistIsLikelyOnLeft && !artistIsLikelyOnRight) {
            return ParsedPlaylistLine(rawLine = rawLine, title = right, artist = left)
        }

        return ParsedPlaylistLine(rawLine = rawLine, title = title, artist = artist)
    }

    private fun parseCommaLine(line: String, rawLine: String): ParsedPlaylistLine? {
        val parts = line.split(",").map { it.trim() }.filter { it.isNotEmpty() }
        if (parts.size < 2) return null

        val left = parts[0]
        val right = parts[1]
        val third = parts.getOrNull(2)
        if (left.isEmpty() || right.isEmpty()) return null

        val artistIsLikelyOnLeft = isLikelyArtist(left)
        val artistIsLikelyOnRight = isLikelyArtist(right)
        val title = if (artistIsLikelyOnLeft && !artistIsLikelyOnRight) right else left
        val artist = if (artistIsLikelyOnLeft && !artistIsLikelyOnRight) left else right
        val album = third

        return ParsedPlaylistLine(rawLine = rawLine, title = title, artist = artist, album = album)
    }

    private fun parseByLine(line: String, rawLine: String): ParsedPlaylistLine? {
        val match = byPattern.matchEntire(line) ?: return null
        val title = match.groupValues[1].trim()
        val artist = match.groupValues[2].trim()
        if (artist.isEmpty() || title.isEmpty()) return null

        return ParsedPlaylistLine(rawLine = rawLine, title = title, artist = artist)
    }

    private fun isLikelyArtist(value: String): Boolean {
        val normalized = value.trim().lowercase()
        return normalized in knownArtistHints ||
            normalized.startsWith("the ") ||
            normalized.endsWith(" artist") ||
            normalized.endsWith(" artist name")
    }

    private fun stripListPrefix(line: String): String {
        return listPrefixPattern.matchEntire(line)?.groupValues?.getOrNull(1)?.trim() ?: line
    }

    private fun parseM3uBlock(block: String): List<ParsedPlaylistLine>? {
        val lines = block.lineSequence().map { it.trim() }.filter { it.isNotBlank() }.toList()
        if (lines.none { it.startsWith("#EXTM3U", ignoreCase = true) || it.startsWith("#EXTINF", ignoreCase = true) }) {
            return null
        }

        val rows = mutableListOf<ParsedPlaylistLine>()
        var pendingMetadata: ParsedPlaylistLine? = null

        lines.forEach { line ->
            when {
                line.startsWith("#EXTINF", ignoreCase = true) -> {
                    val label = line.substringAfter(',', "").trim()
                    pendingMetadata = label.takeIf { it.isNotBlank() }?.let {
                        parseDashLine(it, it)
                            ?: parseCommaLine(it, it)
                            ?: parseByLine(it, it)
                            ?: ParsedPlaylistLine(rawLine = it, title = it)
                    }
                }
                line.startsWith("#") -> Unit
                else -> {
                    val url = urlPattern.find(line)?.value?.trimEnd('.', ',', ')', ']', '}')
                    if (url != null) {
                        val parsedUrl = parsePlatformUrl(url)
                        rows += ParsedPlaylistLine(
                            rawLine = listOfNotNull(pendingMetadata?.rawLine, line).joinToString(" "),
                            title = pendingMetadata?.title ?: parsedUrl?.title,
                            artist = pendingMetadata?.artist ?: parsedUrl?.artist,
                            album = pendingMetadata?.album ?: parsedUrl?.album,
                            sourceUrl = url,
                            sourcePlatform = platformName(url),
                            sourceId = parsedUrl?.sourceId,
                            preserved = pendingMetadata == null && parsedUrl?.title == null
                        )
                    } else {
                        val fileLabel = line.substringAfterLast('/').substringAfterLast('\\').substringBeforeLast('.')
                        rows += parseDashLine(fileLabel, line)
                            ?: parseCommaLine(fileLabel, line)
                            ?: parseByLine(fileLabel, line)
                            ?: ParsedPlaylistLine(rawLine = line, title = slugToTitle(fileLabel), preserved = false)
                    }
                    pendingMetadata = null
                }
            }
        }

        pendingMetadata?.let { rows += it }
        return rows.takeIf { it.isNotEmpty() }
    }

    private fun parseJsonBlock(block: String): List<ParsedPlaylistLine>? {
        val trimmed = block.trim()
        if (!trimmed.startsWith("{") && !trimmed.startsWith("[")) return null
        val root = runCatching { JsonParser.parseString(trimmed) }.getOrNull() ?: return null
        val rows = mutableListOf<ParsedPlaylistLine>()
        collectJsonRows(root, rows)
        return rows.takeIf { it.isNotEmpty() }
    }

    private fun collectJsonRows(element: JsonElement, rows: MutableList<ParsedPlaylistLine>) {
        when {
            element.isJsonArray -> element.asJsonArray.forEach { collectJsonRows(it, rows) }
            element.isJsonObject -> {
                val obj = element.asJsonObject
                jsonObjectToRow(obj)?.let { rows += it }
                obj.entrySet().forEach { (_, value) ->
                    if (value.isJsonArray || value.isJsonObject) collectJsonRows(value, rows)
                }
            }
        }
    }

    private fun jsonObjectToRow(obj: JsonObject): ParsedPlaylistLine? {
        val title = obj.stringValue("title", "track", "trackName", "name", "song", "songName")
        val artist = obj.stringValue("artist", "artistName", "artists", "creator", "primaryArtist")
        val album = obj.stringValue("album", "albumName", "collection", "collectionName")
        val url = obj.stringValue("url", "link", "trackUrl", "externalUrl", "appleMusicUrl", "spotifyUrl")
            ?.takeIf { urlPattern.matches(it) || urlPattern.containsMatchIn(it) }
            ?.let { urlPattern.find(it)?.value ?: it }
        if (title.isNullOrBlank() && url.isNullOrBlank()) return null
        val parsedUrl = url?.let { parsePlatformUrl(it) }
        val finalTitle = title ?: parsedUrl?.title
        val finalArtist = artist ?: parsedUrl?.artist
        return ParsedPlaylistLine(
            rawLine = obj.toString(),
            title = finalTitle,
            artist = finalArtist,
            album = album ?: parsedUrl?.album,
            sourceUrl = url,
            sourcePlatform = url?.let { platformName(it) },
            sourceId = parsedUrl?.sourceId,
            preserved = finalTitle.isNullOrBlank()
        )
    }

    private fun parseXmlBlock(block: String): List<ParsedPlaylistLine>? {
        val trimmed = block.trim()
        if (!trimmed.startsWith("<")) return null
        val itemPattern = Regex("""<(?:(?:track|song|item|entry)\b)[^>]*>(.*?)</(?:track|song|item|entry)>""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
        val rows = itemPattern.findAll(trimmed)
            .mapNotNull { match ->
                val body = match.groupValues[1]
                val title = tagValue(body, "title", "name", "trackName")
                val artist = tagValue(body, "artist", "artistName", "creator")
                val album = tagValue(body, "album", "albumName", "collectionName")
                val url = tagValue(body, "url", "link", "trackUrl")?.let { urlPattern.find(it)?.value ?: it }
                if (title.isNullOrBlank() && url.isNullOrBlank()) null else {
                    val parsedUrl = url?.let { parsePlatformUrl(it) }
                    ParsedPlaylistLine(
                        rawLine = match.value.replace(Regex("""\s+"""), " ").trim(),
                        title = title ?: parsedUrl?.title,
                        artist = artist ?: parsedUrl?.artist,
                        album = album ?: parsedUrl?.album,
                        sourceUrl = url,
                        sourcePlatform = url?.let { platformName(it) },
                        sourceId = parsedUrl?.sourceId,
                        preserved = title.isNullOrBlank() && parsedUrl?.title == null
                    )
                }
            }
            .toList()
        return rows.takeIf { it.isNotEmpty() }
    }

    private fun parseCsvBlock(block: String): List<ParsedPlaylistLine>? {
        val lines = block.lineSequence().map { it.trim() }.filter { it.isNotBlank() }.toList()
        if (lines.size < 2 || lines.none { "," in it }) return null
        val header = splitCsvLine(lines.first()).map { it.lowercase(Locale.US).trim() }
        val hasHeader = header.any { it in setOf("title", "track", "track name", "song", "song title") } &&
            header.any { it in setOf("artist", "artist name", "artists") || it.contains("artist") }
        if (!hasHeader) return null
        fun indexOf(vararg keys: String): Int = header.indexOfFirst { column -> keys.any { key -> column == key || column.contains(key) } }
        val titleIndex = indexOf("title", "track", "song")
        val artistIndex = indexOf("artist")
        val albumIndex = indexOf("album", "collection")
        val urlIndex = indexOf("url", "link")
        val rows = lines.drop(1).mapNotNull { line ->
            val fields = splitCsvLine(line)
            val title = fields.getOrNull(titleIndex)?.trim()?.takeIf { it.isNotBlank() }
            val artist = fields.getOrNull(artistIndex)?.trim()?.takeIf { it.isNotBlank() }
            val album = fields.getOrNull(albumIndex)?.trim()?.takeIf { it.isNotBlank() }
            val url = fields.getOrNull(urlIndex)?.let { urlPattern.find(it)?.value }
            if (title == null && url == null) null else {
                val parsedUrl = url?.let { parsePlatformUrl(it) }
                ParsedPlaylistLine(
                    rawLine = line,
                    title = title ?: parsedUrl?.title,
                    artist = artist ?: parsedUrl?.artist,
                    album = album ?: parsedUrl?.album,
                    sourceUrl = url,
                    sourcePlatform = url?.let { platformName(it) },
                    sourceId = parsedUrl?.sourceId,
                    preserved = title == null && parsedUrl?.title == null
                )
            }
        }
        return rows.takeIf { it.isNotEmpty() }
    }

    private data class UrlParts(
        val title: String? = null,
        val artist: String? = null,
        val album: String? = null,
        val sourceId: String? = null
    )

    private fun parsePlatformUrl(url: String): UrlParts? {
        val uri = runCatching { URI(url) }.getOrNull() ?: return null
        val host = uri.host.orEmpty().removePrefix("www.").lowercase(Locale.US)
        val pathSegments = uri.path.orEmpty().split('/').filter { it.isNotBlank() }
        val numericId = queryParam(uri, "i")
            ?.takeIf { value -> value.all { it.isDigit() } }
            ?: pathSegments.lastOrNull { segment -> segment.all { it.isDigit() } }
        val slug = when {
            "music.apple.com" in host -> pathSegments.getOrNull(pathSegments.indexOfLast { segment -> segment.all { it.isDigit() } } - 1)
            else -> pathSegments.lastOrNull { segment ->
                !segment.all { it.isDigit() } &&
                    segment !in setOf("track", "song", "album", "artist", "playlist", "watch", "music")
            }
        }
        return UrlParts(
            title = slug?.let { slugToTitle(it) },
            sourceId = numericId ?: pathSegments.lastOrNull()
        )
    }

    private fun platformName(url: String): String? {
        val host = runCatching { URI(url).host.orEmpty().removePrefix("www.").lowercase(Locale.US) }.getOrNull() ?: return null
        return when {
            "music.apple.com" in host || "itunes.apple.com" in host -> "Apple Music"
            "open.spotify.com" in host || "spotify.link" in host -> "Spotify"
            "tidal.com" in host -> "Tidal"
            "qobuz.com" in host -> "Qobuz"
            "deezer.com" in host -> "Deezer"
            "music.amazon." in host || host == "amazon.com" || host == "www.amazon.com" -> "Amazon Music"
            "youtube.com" in host || "youtu.be" in host || "music.youtube.com" in host -> "YouTube"
            "soundcloud.com" in host -> "SoundCloud"
            "song.link" in host || "album.link" in host || "odesli.co" in host -> "Songlink"
            else -> host
        }
    }

    private fun queryParam(uri: URI, name: String): String? {
        return uri.rawQuery
            ?.split('&')
            ?.firstNotNullOfOrNull { pair ->
                val key = pair.substringBefore('=')
                val value = pair.substringAfter('=', "")
                value.takeIf { key == name && it.isNotBlank() }
            }
    }

    private fun JsonObject.stringValue(vararg names: String): String? {
        for (name in names) {
            val value = get(name) ?: continue
            if (value.isJsonPrimitive) return value.asString.trim().takeIf { it.isNotBlank() }
            if (value.isJsonArray) {
                val joined = value.asJsonArray.mapNotNull {
                    if (it.isJsonPrimitive) it.asString.trim().takeIf { s -> s.isNotBlank() } else null
                }.joinToString(", ")
                if (joined.isNotBlank()) return joined
            }
        }
        return null
    }

    private fun tagValue(body: String, vararg names: String): String? {
        for (name in names) {
            val pattern = Regex("""<$name\b[^>]*>(.*?)</$name>""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
            val value = pattern.find(body)?.groupValues?.getOrNull(1)
                ?.replace(Regex("""<!\[CDATA\[(.*?)]]>""", RegexOption.DOT_MATCHES_ALL), "$1")
                ?.replace(Regex("""<[^>]+>"""), "")
                ?.trim()
            if (!value.isNullOrBlank()) return value
        }
        return null
    }

    private fun splitCsvLine(line: String): List<String> {
        val result = mutableListOf<String>()
        val current = StringBuilder()
        var inQuotes = false
        var i = 0
        while (i < line.length) {
            val ch = line[i]
            when {
                ch == '"' && inQuotes && i + 1 < line.length && line[i + 1] == '"' -> {
                    current.append('"')
                    i++
                }
                ch == '"' -> inQuotes = !inQuotes
                ch == ',' && !inQuotes -> {
                    result += current.toString()
                    current.clear()
                }
                else -> current.append(ch)
            }
            i++
        }
        result += current.toString()
        return result
    }

    private fun slugToTitle(slug: String): String {
        return slug
            .substringBefore('?')
            .replace('-', ' ')
            .replace('_', ' ')
            .trim()
            .split(Regex("""\s+"""))
            .joinToString(" ") { word ->
                word.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.US) else it.toString() }
            }
    }
}
