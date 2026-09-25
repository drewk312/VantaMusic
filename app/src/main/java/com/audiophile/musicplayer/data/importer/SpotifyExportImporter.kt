package com.audiophile.musicplayer.data.importer

import org.json.JSONArray
import org.json.JSONObject
import java.io.InputStream
import java.time.Instant
import java.util.zip.ZipInputStream

/** One row from Spotify's Extended Streaming History export (Streaming_History_Audio_*.json). */
data class SpotifyHistoryRow(
    val ts: Long, // UTC epoch millis
    val trackName: String,
    val artistName: String,
    val albumName: String? = null,
    val spotifyTrackUri: String? = null,
    val msPlayed: Long,
    val skipped: Boolean = false,
    val reasonStart: String? = null,
    val reasonEnd: String? = null
)

/**
 * Reads Spotify's user-provided privacy-data ZIP locally. Nothing is uploaded.
 *
 * The same zip feeds two consumers:
 *  - [extractTracklist]: legacy "artist - title" lines for library/playlist import screens (org.json).
 *  - [extractHistoryRows]: full Extended Streaming History ([SpotifyHistoryRow]) so taste
 *    profiles and Wrapped stats can derive from real listening data.
 *
 * History rows are parsed with a lightweight text scanner (not org.json) so the parsing is
 * deterministic and unit-testable even where the Android org.json stubs return defaults.
 */
object SpotifyExportImporter {

    private const val HISTORY_FILE = "Streaming_History_Audio_"

    // --- Legacy tracklist import ------------------------------------------------

    fun extractTracklist(input: InputStream): String {
        val rows = linkedSetOf<String>()
        ZipInputStream(input.buffered()).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                if (!entry.isDirectory && entry.name.endsWith(".json", ignoreCase = true)) {
                    val json = zip.readBytes().toString(Charsets.UTF_8)
                    runCatching { collectJson(json, rows) }
                }
                zip.closeEntry()
            }
        }
        return rows.joinToString("\n")
    }

    private fun collectJson(json: String, rows: MutableSet<String>) {
        val trimmed = json.trim()
        when {
            trimmed.startsWith("[") -> collectArray(JSONArray(trimmed), rows)
            trimmed.startsWith("{") -> collectObject(JSONObject(trimmed), rows)
        }
    }

    private fun collectArray(array: JSONArray, rows: MutableSet<String>) {
        for (index in 0 until array.length()) when (val value = array.opt(index)) {
            is JSONObject -> collectObject(value, rows)
            is JSONArray -> collectArray(value, rows)
        }
    }

    private fun collectObject(objectValue: JSONObject, rows: MutableSet<String>) {
        val title = objectValue.firstString("trackName", "track_name", "name", "title", "entityName")
        val artist = objectValue.firstString("artistName", "artist_name", "artist", "creatorName", "creator")
        val uri = objectValue.firstString("spotifyTrackUri", "spotify_track_uri", "uri", "spotifyUri")
        // Spotify exports include podcasts and shows; only keep recordings.
        if (!title.isNullOrBlank() && !artist.isNullOrBlank() &&
            (uri.isNullOrBlank() || uri.startsWith("spotify:track:") || objectValue.has("trackName") || objectValue.has("artistName"))
        ) rows += "$artist - $title"

        objectValue.keys().forEach { key ->
            when (val child = objectValue.opt(key)) {
                is JSONObject -> collectObject(child, rows)
                is JSONArray -> collectArray(child, rows)
            }
        }
    }

    private fun JSONObject.firstString(vararg names: String): String? =
        nullSafeString(*names).takeIf { it.isNotBlank() }

    private fun JSONObject.nullSafeString(vararg names: String): String {
        for (name in names) {
            val value = opt(name)
            if (value != null && value != JSONObject.NULL) {
                val text = value.toString().trim()
                if (text.isNotBlank() && text != "null") return text
            }
        }
        return ""
    }

    // --- Extended Streaming History import -------------------------------------

    fun extractHistoryRows(input: InputStream): List<SpotifyHistoryRow> {
        val rows = linkedSetOf<SpotifyHistoryRow>()
        ZipInputStream(input.buffered()).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                if (!entry.isDirectory && entry.name.endsWith(".json", ignoreCase = true)) {
                    if (entry.name.contains(HISTORY_FILE)) {
                        try {
                            val json = zip.readBytes().toString(Charsets.UTF_8)
                            rows += parseHistoryText(json)
                        } catch (_: Exception) {
                            // Ignore malformed entries; keep the rest of the export.
                        }
                    }
                }
                zip.closeEntry()
            }
        }
        return rows.toList()
    }

    /** Parses one flat Streaming_History JSON document body (an array of flat objects). */
    internal fun parseHistoryText(json: String): List<SpotifyHistoryRow> {
        if (json.isBlank()) return emptyList()
        return objectSubstrings(json).mapNotNull { parseHistoryFields(fieldMap(it)) }
    }

    /** Convenience for on-device callers that already hold a JSONObject row. */
    fun parseHistoryObject(obj: JSONObject): SpotifyHistoryRow? {
        val fields = LinkedHashMap<String, String>()
        obj.keys().forEach { key ->
            val value = obj.opt(key)
            if (value != null && value != JSONObject.NULL) fields[key.trim()] = value.toString()
        }
        return parseHistoryFields(fields)
    }

    /** Builds a row from raw JSON fields; null for podcast episodes, blanks, or zero ms. */
    internal fun parseHistoryFields(fields: Map<String, String>): SpotifyHistoryRow? {
        val trackName = fields.str("master_metadata_track_name", "trackName")
            .takeIf { it.isNotBlank() } ?: return null
        val artistName = fields.str("master_metadata_album_artist_name", "artistName")
            .takeIf { it.isNotBlank() } ?: return null
        val albumName = fields.str("master_metadata_album_album_name", "albumName")
            .takeIf { it.isNotBlank() }
        val uri = fields.str("spotify_track_uri", "spotifyTrackUri")
            .takeIf { it.isNotBlank() }
        if (uri != null && !uri.startsWith("spotify:track:")) return null // podcasts/shows
        val tsText = fields.str("ts")
        if (tsText.isBlank()) return null
        val ts = runCatching { Instant.parse(tsText).toEpochMilli() }.getOrNull() ?: return null
        val msPlayed = fields["ms_played"]?.trim()?.toLongOrNull()?.coerceAtLeast(0L) ?: return null
        if (msPlayed <= 0L) return null // Nothing meaningful was listened to.
        return SpotifyHistoryRow(
            ts = ts,
            trackName = trackName,
            artistName = artistName,
            albumName = albumName,
            spotifyTrackUri = uri,
            msPlayed = msPlayed,
            skipped = fields.str("skipped").equals("true", ignoreCase = true),
            reasonStart = fields.str("reason_start").takeIf { it.isNotBlank() },
            reasonEnd = fields.str("reason_end").takeIf { it.isNotBlank() }
        )
    }

    private fun Map<String, String>.str(vararg names: String): String {
        for (name in names) return get(name)?.trim().orEmpty()
        return ""
    }

    /** Top-level object substrings (balanced braces, quote-aware). */
    private fun objectSubstrings(text: String): List<String> {
        val objects = mutableListOf<String>()
        var i = 0
        val limit = text.length
        while (i < limit) {
            if (text[i] != '{') { i++; continue }
            var depth = 1
            var j = i + 1
            var inString = false
            while (j < limit && depth > 0) {
                val ch = text[j]
                if (ch == '"' && (j == 0 || text[j - 1] != '\\')) inString = !inString
                if (!inString) {
                    when (ch) {
                        '{' -> depth++
                        '}' -> depth--
                    }
                }
                j++
            }
            if (depth == 0) objects += text.substring(i, j)
            i = j
        }
        return objects
    }

    /** Parses `{ "key": value, ... }` into a raw field map (quotes stripped, escapes unescaped). */
    private fun fieldMap(objectText: String): Map<String, String> {
        val fields = LinkedHashMap<String, String>()
        val cursor = TextCursor(objectText)
        while (!cursor.atEnd()) {
            cursor.skipWhiteSpace()
            if (cursor.peek() == ',' || cursor.peek() == '}') { cursor.advance(); continue }
            if (cursor.peek() != '"' || cursor.peekQuoted() == null) { cursor.advance(); continue }
            val key = cursor.peekQuoted() ?: break
            cursor.advanceQuoted()
            cursor.skipWhiteSpace()
            if (cursor.peek() != ':') { cursor.advance(); continue }
            cursor.advance()
            cursor.skipWhiteSpace()
            if (cursor.atEnd()) break
            val value = if (cursor.peek() == '"') {
                cursor.peekQuoted() ?: break
            } else {
                val start = cursor.index
                while (!cursor.atEnd() && cursor.peek() != ',' && cursor.peek() != '}') cursor.advance()
                objectText.substring(start, cursor.index).trim()
            }
            fields[key] = value
        }
        return fields
    }

    /** Lightweight, quote-aware cursor over the raw JSON text. */
    private class TextCursor(private val text: String) {
        var index = 0
            private set

        fun atEnd(): Boolean = index >= text.length

        fun peek(): Char = if (atEnd()) Char.MIN_VALUE else text[index]

        fun advance() {
            if (!atEnd()) index++
        }

        fun skipWhiteSpace() {
            while (!atEnd() && text[index].isWhitespace()) index++
        }

        /** Content of the double-quoted string starting at [index], without the quotes; null if not a string. */
        fun peekQuoted(): String? {
            if (atEnd() || text[index] != '"') return null
            val sb = StringBuilder()
            var i = index + 1
            var escaped = false
            while (i < text.length) {
                val ch = text[i]
                when {
                    escaped -> {
                        sb.append(
                            when (ch) {
                                'n' -> '\n'
                                't' -> '\t'
                                'r' -> '\r'
                                else -> ch
                            }
                        )
                        escaped = false
                    }
                    ch == '\\' -> escaped = true
                    ch == '"' -> return sb.toString()
                    else -> sb.append(ch)
                }
                i++
            }
            return null // unterminated string
        }

        fun advanceQuoted() {
            if (atEnd() || text[index] != '"') return
            var i = index + 1
            var escaped = false
            while (i < text.length) {
                val ch = text[i]
                when {
                    escaped -> escaped = false
                    ch == '\\' -> escaped = true
                    ch == '"' -> { index = i + 1; return }
                    else -> Unit
                }
                i++
            }
            index = text.length
        }
    }
}