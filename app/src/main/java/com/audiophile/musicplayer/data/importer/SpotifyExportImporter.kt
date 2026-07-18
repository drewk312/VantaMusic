package com.audiophile.musicplayer.data.importer

import org.json.JSONArray
import org.json.JSONObject
import java.io.InputStream
import java.util.zip.ZipInputStream

/** Reads Spotify's user-provided privacy-data ZIP locally. Nothing is uploaded. */
object SpotifyExportImporter {
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

    private fun JSONObject.firstString(vararg names: String): String? = names.firstNotNullOfOrNull { name ->
        optString(name).trim().takeIf { it.isNotBlank() }
    }
}
