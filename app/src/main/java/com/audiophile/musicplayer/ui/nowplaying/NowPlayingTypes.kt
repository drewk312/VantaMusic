package com.audiophile.musicplayer.ui.nowplaying

import com.audiophile.musicplayer.data.display.DisplayMetadataCleaner
import com.audiophile.musicplayer.data.display.VantaQualityInfo
import com.audiophile.musicplayer.data.metadata.EnhancedMetadata
import com.audiophile.musicplayer.playback.NowPlayingState

enum class NowPlayingMode {
    ARTWORK,
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
    val canDisplayLyrics: Boolean
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
    val artist = cleaned.artist
        .ifBlank { nowPlayingState.artist.orEmpty() }
        .ifBlank { enhancedMetadata?.artist.orEmpty() }
        .ifBlank { "Unknown Artist" }
    val album = nowPlayingState.album?.takeIf { it.isNotBlank() }
        ?: enhancedMetadata?.album?.takeIf { it.isNotBlank() }
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
        canDisplayLyrics = lyricsTrackId != null && lyricsTrackId == nowPlayingState.trackId
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
