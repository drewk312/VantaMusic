package com.audiophile.musicplayer.ui.nowplaying

import com.audiophile.musicplayer.data.display.DisplayMetadataCleaner
import com.audiophile.musicplayer.data.display.VantaQualityInfo
import com.audiophile.musicplayer.data.metadata.EnhancedMetadata
import com.audiophile.musicplayer.playback.NowPlayingState

enum class NowPlayingMode {
    ARTWORK,
    CANVAS,
    LYRICS,
    QUEUE
}

data class NowPlayingDisplaySnapshot(
    val trackId: String?,
    val mediaId: String?,
    val title: String,
    val artist: String,
    val album: String?,
    val versionLabel: String?,
    val artworkUrl: String?,
    val qualityInfo: VantaQualityInfo?,
    val queueIndex: Int,
    val source: String,
    val lyricsTrackId: String?,
    val canDisplayLyrics: Boolean,
    val featuredArtists: List<String> = emptyList()
)

fun resolveNowPlayingDisplaySnapshot(
    nowPlayingState: NowPlayingState,
    enhancedMetadata: EnhancedMetadata?,
    lyricsTrackId: String?
): NowPlayingDisplaySnapshot {
    val cleaned = DisplayMetadataCleaner.computeDisplayMetadata(
        rawTitle = nowPlayingState.title.orEmpty().ifBlank { enhancedMetadata?.title.orEmpty() },
        rawArtist = nowPlayingState.artist.orEmpty().ifBlank { enhancedMetadata?.artist.orEmpty() },
        rawAlbum = nowPlayingState.album ?: enhancedMetadata?.album
    )

    val title = cleaned.title
        .ifBlank { nowPlayingState.title.orEmpty() }
        .ifBlank { enhancedMetadata?.title.orEmpty() }
        .ifBlank { "Unknown Track" }
    // Now Playing renders featured artists as their own "feat." line, so the main
    // artist line must be primary-only — otherwise "feat. X" shows twice.
    val featured = (
        cleaned.featuredArtists + nowPlayingState.featuredArtists
        )
        .map { it.trim() }
        .filter { it.isNotBlank() }
        .distinctBy { it.lowercase() }
    val artist = cleaned.artist
        .ifBlank { nowPlayingState.artist.orEmpty() }
        .ifBlank { enhancedMetadata?.artist.orEmpty() }
        .ifBlank { "Unknown Artist" }
        .let { full ->
            if (featured.isEmpty()) full
            else {
                var primary = full.replace(Regex("""\s+(?:feat\.?|featuring|ft\.?)\s+.*$""", RegexOption.IGNORE_CASE), "").trim()
                for (name in featured) {
                    primary = primary.replace(
                        Regex(""",\s*${Regex.escape(name)}\s*$""", RegexOption.IGNORE_CASE),
                        ""
                    ).trim()
                }
                primary.ifBlank { full }
            }
        }
    val album = cleaned.album?.takeIf { it.isNotBlank() }
    val source = when {
        !nowPlayingState.title.isNullOrBlank() || !nowPlayingState.artist.isNullOrBlank() -> "now_playing"
        enhancedMetadata != null -> "enhanced_metadata_fallback"
        else -> "unknown_fallback"
    }

    return NowPlayingDisplaySnapshot(
        trackId = nowPlayingState.trackId,
        mediaId = nowPlayingState.canonicalTrackId
            ?: nowPlayingState.preferredExternalTrackId
            ?: nowPlayingState.trackId,
        title = title,
        artist = artist,
        album = album,
        versionLabel = nowPlayingState.versionLabel?.trim()?.takeIf { it.isNotBlank() },
        artworkUrl = nowPlayingState.artworkUrl ?: enhancedMetadata?.artworkUrl,
        qualityInfo = nowPlayingState.qualityInfo,
        queueIndex = nowPlayingState.queuePosition,
        source = source,
        lyricsTrackId = lyricsTrackId,
        canDisplayLyrics = lyricsTrackId != null && lyricsTrackId == nowPlayingState.trackId,
        featuredArtists = featured
    )
}

fun shouldShowQualityChip(qualityInfo: VantaQualityInfo?): Boolean =
    !qualityInfo?.bestQualityLabel().isNullOrBlank()

fun titleMaxLinesForNowPlaying(): Int = 2

fun formatDuration(valueMs: Long): String {
    val totalSeconds = (valueMs / 1_000L).coerceAtLeast(0L)
    val minutes = totalSeconds / 60L
    val seconds = totalSeconds % 60L
    return "$minutes:${seconds.toString().padStart(2, '0')}"
}

fun qualityLabelFromState(state: NowPlayingState): String? {
    return state.qualityInfo?.bestQualityLabel()
}

