@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package com.audiophile.musicplayer.radio

import androidx.media3.common.Metadata
import androidx.media3.extractor.metadata.id3.TextInformationFrame
import androidx.media3.extractor.metadata.icy.IcyInfo

data class LiveRadioNowPlaying(
    val title: String,
    val artist: String
)

object LiveRadioMetadataParser {
  private val separators = listOf(" - ", " – ", " — ", " | ", " / ")

    fun parseMetadata(metadata: Metadata): LiveRadioNowPlaying? {
        var icyTitle: String? = null
        var id3Title: String? = null
        var id3Artist: String? = null
        for (i in 0 until metadata.length()) {
            when (val entry = metadata[i]) {
                is IcyInfo -> icyTitle = entry.title?.trim()
                is TextInformationFrame -> when (entry.id) {
                    "TIT2" -> id3Title = entry.value.trim()
                    "TPE1" -> id3Artist = entry.value.trim()
                }
            }
        }
        if (!id3Title.isNullOrBlank() && !id3Artist.isNullOrBlank()) {
            return LiveRadioNowPlaying(title = id3Title, artist = id3Artist)
        }
        return parseCombinedTitle(icyTitle ?: id3Title)
    }

    fun parseCombinedTitle(raw: String?): LiveRadioNowPlaying? {
        val cleaned = raw?.trim().orEmpty()
        if (cleaned.isBlank()) return null
        for (separator in separators) {
            val index = cleaned.indexOf(separator)
            if (index > 0) {
                val artist = cleaned.substring(0, index).trim()
                val title = cleaned.substring(index + separator.length).trim()
                if (artist.isNotBlank() && title.isNotBlank()) {
                    return LiveRadioNowPlaying(title = title, artist = artist)
                }
            }
        }
        return LiveRadioNowPlaying(title = cleaned, artist = "")
    }

    fun isStationPlaceholder(title: String, artist: String, stationName: String): Boolean {
        if (title.equals(stationName, ignoreCase = true)) return true
        if (artist.contains("live", ignoreCase = true) && artist.contains(stationName, ignoreCase = true)) {
            return true
        }
        return false
    }
}
