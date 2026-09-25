package com.audiophile.musicplayer.data.importer

import com.audiophile.musicplayer.data.local.entities.ListeningHistoryEntity
import java.time.Instant

/**
 * Parses exported Apple Music / iTunes library files purely from text so it can be
 * unit-tested without Android stubs (org.json is stubbed in this repo's unit tests).
 *
 * Supported sources:
 *  - iTunes XML plist export (File -> Library -> Export Library), `<plist>` with `<key>Tracks</key>`.
 *  - Music.app JSON database (library.json), `"Library": { "Tracks": { "<id>": {...} } }`
 *    or a top-level `"Tracks": [ ... ]` array.
 *
 * Emits one [ListeningHistoryEntity] per track that has ever been played, rolling Play Counts
 * into [ListeningHistoryEntity.playCount] and scaling `ms_played` by that count so Wrapped
 * aggregates (which rank by SUM(ms_played)) reflect real minutes listened.
 */
object AppleLibraryImporter {

    const val APPLE_MUSIC = "APPLE_MUSIC"
    const val PLATFORM = APPLE_MUSIC

    /** Seconds between the Unix epoch and the Apple 1904 epoch. */
    private const val APPLE_EPOCH_OFFSET_SECONDS = 2_082_844_800L

    data class AppleTrack(
        val trackId: String,
        val name: String,
        val artist: String,
        val album: String? = null,
        val albumArtist: String? = null,
        val genre: String? = null,
        val totalTimeMs: Long? = null,
        val playCount: Int = 0,
        val lastPlayedAtMs: Long? = null,
        val dateAddedMs: Long? = null,
        val loved: Boolean = false
    )

    data class AppleLibrary(
        val tracks: List<AppleTrack>,
        val source: String
    )

    fun parseLibrary(text: String?): AppleLibrary? {
        if (text.isNullOrBlank()) return null
        val trimmed = text.trimStart()
        return when {
            trimmed.startsWith("<") -> parsePlistLibrary(text)
            trimmed.startsWith("{") -> parseJsonLibrary(text)
            else -> null
        }
    }

    fun toHistoryRows(
        library: AppleLibrary?,
        recordedAt: Long = System.currentTimeMillis()
    ): List<ListeningHistoryEntity> {
        if (library == null) return emptyList()
        return library.tracks.mapNotNull { track ->
            val name = track.name.trim()
            val artist = track.artist.trim()
            val plays = track.playCount.coerceAtLeast(0)
            if (name.isBlank() || artist.isBlank() || plays < 1) {
                null
            } else {
                val durationMs = track.totalTimeMs?.takeIf { it > 0 }
                ListeningHistoryEntity(
                    startedAt = track.lastPlayedAtMs ?: track.dateAddedMs ?: recordedAt,
                    title = name,
                    artist = artist,
                    album = track.album?.trim()?.takeIf { it.isNotEmpty() },
                    platform = PLATFORM,
                    providerId = PLATFORM,
                    sourceTrackId = track.trackId.takeIf { it.isNotBlank() },
                    msPlayed = (durationMs ?: 0L) * plays.toLong(),
                    durationMs = durationMs,
                    playCount = plays,
                    skipped = false,
                    reasonStart = "apple:import",
                    reasonEnd = if (track.loved) "loved" else null,
                    recordedAt = recordedAt
                )
            }
        }
    }

    // --- XML plist ---

    private fun parsePlistLibrary(text: String): AppleLibrary? {
        if (!text.contains("<plist", ignoreCase = true) || !text.contains("<key>Tracks</key>", ignoreCase = true)) {
            return null
        }
        val trackPattern = Regex(
            """<key>([0-9a-zA-Z-]+)</key>\s*<dict>(.*?)</dict>""",
            setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
        )
        val tracks = trackPattern.findAll(text).mapNotNull { match ->
            val body = match.groupValues[2]
            val trackId = plistInteger(body, "Track ID")?.toString()
                ?: match.groupValues[1].takeIf { it != "Tracks" }
            val name = plistString(body, "Name")
            val artist = plistString(body, "Artist")
            if (trackId == null || name.isNullOrBlank() || artist.isNullOrBlank()) {
                null
            } else {
                AppleTrack(
                    trackId = trackId,
                    name = name,
                    artist = artist,
                    album = plistString(body, "Album"),
                    albumArtist = plistString(body, "Album Artist"),
                    genre = plistString(body, "Genre"),
                    totalTimeMs = plistInteger(body, "Total Time"),
                    playCount = plistInteger(body, "Play Count")?.toInt() ?: 0,
                    lastPlayedAtMs = playDateToEpochMillis(plistInteger(body, "Play Date")),
                    dateAddedMs = plistDate(body, "Date Added"),
                    loved = plistBool(body, "Loved")
                )
            }
        }.toList()
        return tracks.takeIf { it.isNotEmpty() }?.let { AppleLibrary(it, "xml") }
    }

    private fun plistString(body: String, key: String): String? {
        val m = Regex(
            "<key>" + Regex.escape(key) + "</key>\\s*<string>(.*?)</string>",
            setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
        ).find(body) ?: return null
        return decodePlistEntities(m.groupValues[1]).trim().takeIf { it.isNotEmpty() }
    }

    private fun plistInteger(body: String, key: String): Long? {
        val m = Regex(
            "<key>" + Regex.escape(key) + "</key>\\s*<integer>([-0-9]+)</integer>",
            setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
        ).find(body) ?: return null
        return m.groupValues[1].toLongOrNull()
    }

    private fun plistDate(body: String, key: String): Long? {
        val m = Regex(
            "<key>" + Regex.escape(key) + "</key>\\s*<date>(.*?)</date>",
            setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
        ).find(body) ?: return null
        val value = decodePlistEntities(m.groupValues[1]).trim()
        if (value.isEmpty()) return null
        return runCatching { Instant.parse(value).toEpochMilli() }.getOrNull()
    }

    private fun plistBool(body: String, key: String): Boolean {
        return Regex(
            "<key>" + Regex.escape(key) + "</key>\\s*<true\\s*/?>",
            setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
        ).containsMatchIn(body)
    }

    private fun decodePlistEntities(value: String): String {
        return value
            .replace(Regex("""<!\[CDATA\[(.*?)]]>""", RegexOption.DOT_MATCHES_ALL), "$1")
            .replace("&#x0A;", "\n")
            .replace("&#xA;", "\n")
            .replace("&#10;", "\n")
            .replace("&amp;", "&")
            .replace("&apos;", "'")
            .replace("&quot;", "\"")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
    }

    // --- JSON (Music.app library.json and similar exporters) ---

    private fun parseJsonLibrary(text: String): AppleLibrary? {
        val chunks = jsonTracksChunks(text) ?: return null
        val tracks = chunks.mapNotNull { chunk ->
            val fields = jsonFields(chunk)
            val trackId = fields.token("Track ID")?.let { unquote(it) }
            val name = fields.str("Name")
            val artist = fields.str("Artist")
            if (trackId == null || name == null || artist == null) {
                null
            } else {
                AppleTrack(
                    trackId = trackId,
                    name = name,
                    artist = artist,
                    album = fields.str("Album"),
                    albumArtist = fields.str("Album Artist"),
                    genre = fields.str("Genre"),
                    totalTimeMs = fields.token("Total Time")?.let { unquote(it) }?.toLongOrNull(),
                    playCount = fields.token("Play Count")?.let { unquote(it) }?.toIntOrNull() ?: 0,
                    lastPlayedAtMs = fields.token("Play Date")?.let { unquote(it) }?.toLongOrNull()
                        ?.let { playDateToEpochMillis(it) },
                    dateAddedMs = fields.str("Date Added")?.let { runCatching { Instant.parse(it).toEpochMilli() }.getOrNull() },
                    loved = fields.token("Loved") == "true"
                )
            }
        }.toList()
        return tracks.takeIf { it.isNotEmpty() }?.let { AppleLibrary(it, "json") }
    }

    /** Returns the brace-balanced object chunks (tracks) under the first usable `"Tracks"` key. */
    private fun jsonTracksChunks(text: String): List<String>? {
        var searchFrom = 0
        while (true) {
            val keyAt = indexOfJsonKey(text, "Tracks", searchFrom) ?: return null
            var i = skipWhitespace(text, keyAt + "\"Tracks\"".length)
            if (i >= text.length || text[i] != ':') {
                searchFrom = keyAt + 1
                continue
            }
            i = skipWhitespace(text, i + 1)
            if (i >= text.length) return null
            val startPad = if (text[i] == '[' || text[i] == '{') i else null
            if (startPad == null) {
                searchFrom = keyAt + 1
                continue
            }
            val close = matchClose(text, startPad) ?: return null
            val innerStart = if (text[startPad] == '[') startPad + 1 else startPad + 1
            val chunks = splitJsonObjects(text, innerStart, close)
            if (chunks.isNotEmpty()) return chunks
            searchFrom = keyAt + 1
        }
    }

    private fun splitJsonObjects(text: String, from: Int, until: Int): List<String> {
        val chunks = mutableListOf<String>()
        var i = from
        while (i < until) {
            when {
                text[i] == '{' -> {
                    val close = matchClose(text, i) ?: break
                    if (close <= until) chunks.add(text.substring(i, close + 1))
                    i = close + 1
                }
                text[i] == '"' -> i = skipJsonString(text, i) ?: break
                else -> i++
            }
        }
        return chunks
    }

    private fun jsonFields(chunk: String): Map<String, String> {
        val map = LinkedHashMap<String, String>()
        var i = 0
        while (i < chunk.length) {
            if (chunk[i] == '"') {
                val keyEnd = skipJsonString(chunk, i) ?: break
                val key = decodeJsonString(chunk.substring(i + 1, keyEnd - 1))
                var j = skipWhitespace(chunk, keyEnd)
                if (j < chunk.length && chunk[j] == ':') {
                    j = skipWhitespace(chunk, j + 1)
                    if (j < chunk.length) {
                        val (valueText, next) = jsonValue(chunk, j) ?: jsonValueFallback(chunk, j) ?: (null to j)
                        if (valueText != null && key.isNotBlank() && !map.containsKey(key)) {
                            map[key] = valueText
                        }
                        i = next
                        continue
                    }
                }
                i = keyEnd
            } else {
                i++
            }
        }
        return map
    }

    /** Reads a string value if chunk[j] == '"'. Returns decoded string and index after it. */
    private fun jsonValue(chunk: String, j: Int): Pair<String, Int>? {
        if (chunk[j] != '"') return null
        val end = skipJsonString(chunk, j) ?: return null
        return decodeJsonString(chunk.substring(j + 1, end - 1)) to end
    }

    /** Reads a bare number/token literal up to the next delimiter. */
    private fun jsonValueFallback(chunk: String, j: Int): Pair<String?, Int>? {
        if (chunk[j] == '{' || chunk[j] == '[') {
            val close = matchClose(chunk, j) ?: return null
            return (null as String?) to close
        }
        var k = j
        val sb = StringBuilder()
        while (k < chunk.length && chunk[k] != ',' && chunk[k] != '}') {
            sb.append(chunk[k])
            k++
        }
        return sb.toString().trim().takeIf { it.isNotEmpty() }?.let { it to k }
    }

    private fun indexOfJsonKey(text: String, key: String, from: Int): Int? {
        var searchFrom = from
        while (true) {
            val idx = text.indexOf("\"$key\"", searchFrom)
            if (idx < 0) return null
            if (idx == 0 || text[idx - 1] != '\\') return idx
            searchFrom = idx + 1
        }
    }

    private fun skipWhitespace(text: String, from: Int): Int {
        var i = from
        while (i < text.length && (text[i] == ' ' || text[i] == '\t' || text[i] == '\n' || text[i] == '\r')) i++
        return i
    }

    /** Index after the closing quote of a quoted string starting at `at` (text[at] == '"'). */
    private fun skipJsonString(text: String, at: Int): Int? {
        if (at >= text.length || text[at] != '"') return null
        var i = at + 1
        while (i < text.length) {
            when (text[i]) {
                '\\' -> i += 2
                '"' -> return i + 1
                else -> i++
            }
        }
        return null
    }

    /** Index of the matching close for a `{`/`[` at `at`, honoring quoted strings. */
    private fun matchClose(text: String, at: Int): Int? {
        if (at >= text.length || (text[at] != '{' && text[at] != '[')) return null
        val open = text[at]
        val close = if (open == '{') '}' else ']'
        var depth = 0
        var i = at
        while (i < text.length) {
            when (text[i]) {
                '"' -> {
                    val end = skipJsonString(text, i) ?: return null
                    i = end
                }
                open -> {
                    depth++
                    i++
                }
                close -> {
                    depth--
                    if (depth == 0) return i
                    i++
                }
                else -> i++
            }
        }
        return null
    }

    private fun decodeJsonString(raw: String): String {
        val sb = StringBuilder()
        var i = 0
        while (i < raw.length) {
            val c = raw[i]
            if (c != '\\' || i + 1 >= raw.length) {
                sb.append(c)
                i++
                continue
            }
            val esc = raw[i + 1]
            when (esc) {
                '"' -> { sb.append('"'); i += 2 }
                '\\' -> { sb.append('\\'); i += 2 }
                '/' -> { sb.append('/'); i += 2 }
                'n' -> { sb.append('\n'); i += 2 }
                't' -> { sb.append('\t'); i += 2 }
                'r' -> { sb.append('\r'); i += 2 }
                'b' -> { sb.append('\b'); i += 2 }
                'f' -> { sb.append('\u000C'); i += 2 }
                'u' -> {
                    val hex = raw.substring(i + 2, (i + 6).coerceAtMost(raw.length))
                    sb.append(hex.toIntOrNull(16)?.let(::codepointToChar) ?: raw[i + 2])
                    i += 2 + 4
                }
                else -> { sb.append(esc); i += 2 }
            }
        }
        return sb.toString()
    }

    private fun codepointToChar(cp: Int): String = String(Character.toChars(cp))

    private fun Map<String, String>.str(vararg keys: String): String? {
        for (key in keys) {
            this[key]?.takeIf { it.isNotBlank() }?.let { return it.trim() }
        }
        return null
    }

    private fun Map<String, String>.token(vararg keys: String): String? {
        for (key in keys) {
            this[key]?.let { return it }
        }
        return null
    }

    private fun unquote(value: String): String {
        if (value.length >= 2 && value.startsWith("\"") && value.endsWith("\"")) {
            return decodeJsonString(value.substring(1, value.length - 1))
        }
        return value
    }

    private fun playDateToEpochMillis(secondsRaw: Long?): Long? {
        if (secondsRaw == null || secondsRaw <= 0) return null
        val nowYear = runCatching { java.time.ZonedDateTime.now(java.time.ZoneOffset.UTC).year }.getOrDefault(2026)
        val candidates = listOf(
            secondsRaw - APPLE_EPOCH_OFFSET_SECONDS,
            secondsRaw
        )
        for (unixSeconds in candidates) {
            if (unixSeconds <= 0 || unixSeconds > 150_000_000_000L) continue
            val millis = unixSeconds * 1000L
            runCatching {
                val year = Instant.ofEpochMilli(millis).atZone(java.time.ZoneOffset.UTC).year
                if (year in 1980..(nowYear + 1)) return millis
            }
        }
        return null
    }
}