package com.audiophile.musicplayer.data.resolution

import java.util.Locale

/**
 * Keeps matching deterministic across providers and cache layers.
 */
object MetadataNormalizer {

    private val removableTags = listOf(
        "remaster",
        "remastered",
        "radio edit",
        "deluxe",
        "explicit",
        "clean",
        "mono",
        "stereo",
        "version",
        "edit"
    )

    fun normalizeTitle(value: String?): String = normalizeText(value)

    fun normalizeArtist(value: String?): String = normalizeText(value)

    fun normalizeAlbum(value: String?): String = normalizeText(value)

    fun normalizeIsrc(value: String?): String? {
        val compact = value
            ?.uppercase(Locale.US)
            ?.replace(Regex("[^A-Z0-9]"), "")
            ?.trim()
            .orEmpty()
        return compact.ifBlank { null }
    }

    fun buildQueryKey(
        title: String?,
        artist: String?,
        album: String?,
        isrc: String?
    ): String {
        val normalizedIsrc = normalizeIsrc(isrc)
        if (normalizedIsrc != null) {
            return "isrc:$normalizedIsrc"
        }

        val normalizedTitle = normalizeTitle(title)
        val normalizedArtist = normalizeArtist(artist)
        val normalizedAlbum = normalizeAlbum(album)
        return listOf(normalizedArtist, normalizedTitle, normalizedAlbum)
            .filter { it.isNotBlank() }
            .joinToString(separator = "|")
    }

    private fun normalizeText(value: String?): String {
        if (value.isNullOrBlank()) return ""

        var normalized = value.lowercase(Locale.US)
        normalized = normalized.replace(Regex("\\((.*?)\\)"), " ")
        normalized = normalized.replace(Regex("\\[(.*?)]"), " ")
        removableTags.forEach { tag ->
            normalized = normalized.replace(tag, " ")
        }
        normalized = normalized.replace("&", " and ")
        normalized = normalized.replace(Regex("[^a-z0-9]+"), " ")
        normalized = normalized.replace(Regex("\\s+"), " ")
        return normalized.trim()
    }
}
