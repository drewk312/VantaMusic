package com.audiophile.musicplayer.data.dj

import com.audiophile.musicplayer.data.display.DisplayMetadataCleaner
import com.audiophile.musicplayer.data.display.PlaybackDisplay
import com.audiophile.musicplayer.data.local.entities.UnifiedTrackWithSources

data class DjListenerContext(
    val displayName: String? = null,
    val favoriteArtists: List<String> = emptyList(),
    val favoriteGenres: List<String> = emptyList(),
    val recentLikes: List<String> = emptyList(),
    val recentSkips: List<String> = emptyList(),
    val recentReplays: List<String> = emptyList(),
    val sessionTrackCount: Int = 0,
    val totalSessions: Int = 0,
    val mode: AiDjMode = AiDjMode.DAILY_DJ
) {
    val firstName: String? = displayName
        ?.trim()
        ?.split(Regex("\\s+"))
        ?.firstOrNull()
        ?.takeIf { it.isNotBlank() }

    fun tasteSummary(): String {
        val artists = favoriteArtists.take(4).joinToString(", ").ifBlank { "wide open" }
        val genres = favoriteGenres.take(3).joinToString(", ").ifBlank { "all styles" }
        return "artists like $artists; genres like $genres"
    }

    fun feedbackSummary(): String {
        val likes = recentLikes.take(3).joinToString(", ")
        val skips = recentSkips.take(3).joinToString(", ")
        val replays = recentReplays.take(2).joinToString(", ")
        return buildString {
            if (likes.isNotBlank()) append("Recently enjoyed: $likes. ")
            if (replays.isNotBlank()) append("Replayed: $replays. ")
            if (skips.isNotBlank()) append("Recently passed on: $skips. ")
        }.trim().ifBlank { "No strong recent signals yet." }
    }
}

fun UnifiedTrackWithSources.toPlaybackDisplay(): PlaybackDisplay {
    val streamHint = sources.firstOrNull()?.streamUrl
    return DisplayMetadataCleaner.computePlaybackDisplay(
        rawTitle = track.title,
        rawArtist = track.artist.orEmpty(),
        rawAlbum = track.albumName,
        streamHint = streamHint
    )
}
