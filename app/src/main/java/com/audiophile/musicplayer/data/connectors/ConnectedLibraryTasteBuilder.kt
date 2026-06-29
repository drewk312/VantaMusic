package com.audiophile.musicplayer.data.connectors

import android.util.Log
import com.audiophile.musicplayer.data.taste.TasteEvent
import com.audiophile.musicplayer.data.taste.TasteEventType
import java.util.Locale

data class ConnectedTasteProfileSummary(
    val signals: List<ConnectedTasteSignal>,
    val llmSafeSummary: String,
    val providerCoverage: Map<ConnectedLibraryProvider, Int>
)

class ConnectedLibraryTasteBuilder {
    fun build(
        importedTracks: List<ImportedLibraryTrack>,
        playlists: List<ImportedPlaylist>,
        vantaEvents: List<TasteEvent>,
        maxSummaryChars: Int = 420
    ): ConnectedTasteProfileSummary {
        val skipArtists = vantaEvents
            .filter { it.event == TasteEventType.SKIPPED }
            .groupingBy { normalize(it.artist) }
            .eachCount()

        val artistCounts = importedTracks
            .groupingBy { normalize(it.artist) }
            .eachCount()
            .filterKeys { it.isNotBlank() }

        val signals = mutableListOf<ConnectedTasteSignal>()
        artistCounts.entries
            .sortedByDescending { it.value }
            .take(20)
            .forEach { (artist, count) ->
                val skipPenalty = (skipArtists[artist] ?: 0) * 0.35f
                val strength = (count / 5f).coerceAtMost(2f) - skipPenalty
                if (strength > 0f) {
                    signals += ConnectedTasteSignal(
                        provider = providerForArtist(importedTracks, artist),
                        signalType = ConnectedTasteSignalType.ARTIST_AFFINITY,
                        artist = artist,
                        strength = strength,
                        importedAt = importedTracks.maxOfOrNull { it.importedAt } ?: System.currentTimeMillis()
                    )
                }
            }

        playlists.take(12).forEach { playlist ->
            signals += ConnectedTasteSignal(
                provider = playlist.provider,
                signalType = ConnectedTasteSignalType.PLAYLIST_THEME,
                sourcePlaylistName = playlist.name,
                moodTag = inferMoodTag(playlist.name),
                strength = (playlist.trackCount / 25f).coerceIn(0.4f, 2f),
                importedAt = playlist.importedAt
            )
        }

        val anchors = signals
            .filter { it.signalType == ConnectedTasteSignalType.ARTIST_AFFINITY }
            .sortedByDescending { it.strength }
            .take(8)
            .mapNotNull { it.artist }

        val moods = signals
            .mapNotNull { it.moodTag }
            .distinct()
            .take(5)

        val summary = buildString {
            append("Connected library anchors: ")
            append(if (anchors.isEmpty()) "none yet" else anchors.joinToString(", "))
            if (moods.isNotEmpty()) {
                append(". Playlist mood hints: ")
                append(moods.joinToString(", "))
            }
            if (skipArtists.isNotEmpty()) {
                append(". Recent VANTA skips reduce matching artist affinity.")
            }
        }.take(maxSummaryChars)

        val providerCoverage = importedTracks.groupingBy { it.provider }.eachCount()
        Log.i(
            "VANTA_CONNECTED_TASTE_BUILD",
            "topArtists=${anchors.size} clusters=${moods.size} providerCoverage=$providerCoverage summarySize=${summary.length}"
        )
        return ConnectedTasteProfileSummary(signals, summary, providerCoverage)
    }

    private fun providerForArtist(
        tracks: List<ImportedLibraryTrack>,
        normalizedArtist: String
    ): ConnectedLibraryProvider =
        tracks.firstOrNull { normalize(it.artist) == normalizedArtist }?.provider
            ?: ConnectedLibraryProvider.SPOTIFY

    private fun inferMoodTag(name: String): String? {
        val lower = name.lowercase(Locale.US)
        return when {
            "night" in lower || "drive" in lower -> "night-drive"
            "cozy" in lower || "cabin" in lower -> "cozy"
            "road" in lower || "trip" in lower -> "road-trip"
            "chill" in lower || "soft" in lower -> "smooth"
            "workout" in lower || "run" in lower -> "high-energy"
            else -> null
        }
    }

    private fun normalize(value: String): String =
        value.trim().lowercase(Locale.US).replace(Regex("""\s+"""), " ")
}
