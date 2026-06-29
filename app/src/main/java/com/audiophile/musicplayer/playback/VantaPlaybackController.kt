package com.audiophile.musicplayer.playback

import com.audiophile.musicplayer.data.display.VantaQualityInfo

enum class VantaArtworkSource {
    LOCAL_FILE,
    PROVIDER,
    ALBUM_METADATA,
    ARTIST_METADATA,
    GENERATED,
    UNKNOWN
}

data class VantaArtworkInfo(
    val artworkId: String? = null,
    val artworkUrl: String? = null,
    val dominantColor: Long? = null,
    val backgroundColor: Long? = null,
    val accentColor: Long? = null,
    val source: VantaArtworkSource = VantaArtworkSource.UNKNOWN
)

data class VantaPlaybackCapabilities(
    val canSeek: Boolean,
    val canSkipNext: Boolean,
    val canSkipPrevious: Boolean,
    val canAppendToQueue: Boolean,
    val canPlayNext: Boolean,
    val canEditQueue: Boolean,
    val canMoveQueueItems: Boolean,
    val canRemoveQueueItems: Boolean,
    val canClearQueue: Boolean,
    val canSetShuffle: Boolean,
    val canSetRepeat: Boolean,
    val canStartRadio: Boolean,
    val canSetCrossfade: Boolean,
    val canUseLyrics: Boolean,
    val canSelectAudioTrack: Boolean
)

data class VantaPlaybackSnapshot(
    val trackId: String? = null,
    val title: String? = null,
    val artist: String? = null,
    val album: String? = null,
    val isrc: String? = null,
    val canonicalTrackId: String? = null,
    val artworkUrl: String? = null,
    val isFavorite: Boolean = false,
    val isPlaying: Boolean = false,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val bufferedMs: Long = 0L,
    val shuffleEnabled: Boolean = false,
    val repeatMode: Int = 0,
    val queueSize: Int = 0,
    val queuePosition: Int = 0,
    val playabilityStatus: Int = 0,
    val errorMessage: String? = null,
    val explicit: Boolean? = null,
    val sleepTimerRemainingMs: Long? = null,
    val qualityInfo: VantaQualityInfo? = null,
    val artworkInfo: VantaArtworkInfo? = null,
    val capabilities: VantaPlaybackCapabilities? = null
) {
    fun isConfirmedPlayable(): Boolean = trackId != null && errorMessage == null
}

sealed class VantaPlaybackEvent {
    data class CurrentItemChanged(val oldTrackId: String?, val newTrackId: String?) : VantaPlaybackEvent()
    data class PlaybackStateChanged(val isPlaying: Boolean, val state: Int) : VantaPlaybackEvent()
    data class QueueChanged(val count: Int, val currentIndex: Int) : VantaPlaybackEvent()
    data class QueueItemsAdded(val count: Int) : VantaPlaybackEvent()
    data class QueueItemRemoved(val index: Int) : VantaPlaybackEvent()
    data class QueueItemMoved(val from: Int, val to: Int) : VantaPlaybackEvent()
    object MetadataUpdated : VantaPlaybackEvent()
    data class SourceChanged(val providerId: String?, val trackId: String?) : VantaPlaybackEvent()
    object StreamRenewed : VantaPlaybackEvent()
    data class AudioQualityChanged(val label: String?, val bitrateKbps: Int?, val providerId: String?) : VantaPlaybackEvent()
    data class BufferingChanged(val isBuffering: Boolean) : VantaPlaybackEvent()
    data class PlaybackError(val reason: String, val message: String?) : VantaPlaybackEvent()
    data class ShuffleModeChanged(val enabled: Boolean) : VantaPlaybackEvent()
    data class RepeatModeChanged(val mode: Int) : VantaPlaybackEvent()
    object LyricsStateChanged : VantaPlaybackEvent()
    object RadioQueueReady : VantaPlaybackEvent()
}

interface VantaPlaybackController {
    val snapshot: VantaPlaybackSnapshot
    val events: kotlinx.coroutines.flow.SharedFlow<VantaPlaybackEvent>

    fun play()
    fun pause()
    fun togglePlayPause()
    fun stop()
    fun seekTo(positionMs: Long)
    fun skipNext()
    fun skipPrevious()

    fun prepareQueue(tracks: List<com.audiophile.musicplayer.data.local.entities.UnifiedTrackWithSources>, startIndex: Int = 0)
    fun replaceQueue(tracks: List<com.audiophile.musicplayer.data.local.entities.UnifiedTrackWithSources>, startIndex: Int = 0)
    fun addToQueue(track: com.audiophile.musicplayer.data.local.entities.UnifiedTrackWithSources)
    fun playNext(track: com.audiophile.musicplayer.data.local.entities.UnifiedTrackWithSources)
    fun removeQueueItem(index: Int)
    fun moveQueueItem(fromIndex: Int, toIndex: Int)
    fun clearQueue()
    fun skipToQueueItem(index: Int)
}
