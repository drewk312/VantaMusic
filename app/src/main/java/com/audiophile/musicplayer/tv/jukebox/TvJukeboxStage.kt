package com.audiophile.musicplayer.tv.jukebox

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.audiophile.musicplayer.data.display.VantaQualityInfo
import com.audiophile.musicplayer.playback.QueueSnapshot
import com.audiophile.musicplayer.tv.TvMetrics

/**
 * Enter the physical jukebox machine.
 *
 * The stage is retained as the TV navigation entry point so [TvNowPlayingViewMode.Jukebox]
 * callers stay stable; all rendering and mechanical choreography now live in
 * [TvJukeboxScreen]. The old card dashboard this used to render is gone.
 */
@Composable
fun TvJukeboxStage(
    title: String?,
    artist: String?,
    album: String?,
    artworkUrl: String?,
    isPlaying: Boolean,
    qualityInfo: VantaQualityInfo?,
    positionMs: Long,
    durationMs: Long,
    queueSnapshot: QueueSnapshot?,
    metrics: TvMetrics,
    onToggle: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onThumbsUp: () -> Unit,
    onThumbsDown: () -> Unit,
    onPlayQueueIndex: (Int) -> Unit,
    modifier: Modifier = Modifier,
    isLiked: Boolean = false,
    onSeek: ((Long) -> Unit)? = null,
    acousticness: Double? = null
) {
    TvJukeboxScreen(
        queueSnapshot = queueSnapshot,
        title = title,
        artist = artist,
        album = album,
        artworkUrl = artworkUrl,
        isPlaying = isPlaying,
        qualityInfo = qualityInfo,
        positionMs = positionMs,
        durationMs = durationMs,
        isLiked = isLiked,
        metrics = metrics,
        acousticness = acousticness,
        onPrevious = onPrevious,
        onToggle = onToggle,
        onNext = onNext,
        onThumbsUp = onThumbsUp,
        onThumbsDown = onThumbsDown,
        onPlayQueueIndex = onPlayQueueIndex,
        onSeek = onSeek,
        modifier = modifier
    )
}
