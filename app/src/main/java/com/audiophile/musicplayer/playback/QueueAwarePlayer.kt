package com.audiophile.musicplayer.playback

import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.MediaItem
import androidx.media3.common.Metadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import androidx.media3.common.Tracks
import androidx.media3.common.util.Clock
import androidx.media3.common.util.ListenerSet
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer

/**
 * Wraps [exoPlayer] for actual playback while keeping MediaSession informed.
 *
 * PREVIOUSLY this class tried to expose a fake multi-item timeline so the lock-screen
 * showed a full queue. That caused MediaSession PlayerInfo crashes because ExoPlayer's
 * real timeline and the synthetic timeline disagree during setMediaItems/clearMediaItems.
 * Now we let ExoPlayer's timeline be the single source of truth, and we drive queue
 * next/previous through [hasNextMediaItem] / [seekToNextMediaItem] overrides instead.
 */
@OptIn(UnstableApi::class)
class QueueAwarePlayer(
    private val exoPlayer: ExoPlayer,
    private val queueSnapshotProvider: () -> QueueSnapshot,
    private val onSkipToNext: () -> Unit,
    private val onSkipToPrevious: () -> Unit,
) : ForwardingPlayer(exoPlayer) {

    private val notificationListeners = ListenerSet<Player.Listener>(
        Looper.getMainLooper(),
        Clock.DEFAULT
    ) { _, _ -> }

    // We no longer publish a synthetic timeline, but we still suppress spurious
    // timeline events while the queue is being replaced to avoid MediaSession churn.
    @Volatile private var isReplacingQueue = false

    private val exoEventBridge = object : Player.Listener {
        override fun onTimelineChanged(timeline: Timeline, reason: Int) {
            if (isReplacingQueue) return
            val now = SystemClock.uptimeMillis()
            if (reason == Player.TIMELINE_CHANGE_REASON_SOURCE_UPDATE &&
                now - lastTimelineForwardAtMs > 250L
            ) {
                lastTimelineForwardAtMs = now
                notificationListeners.sendEvent(Player.EVENT_TIMELINE_CHANGED) {
                    it.onTimelineChanged(timeline, reason)
                }
            }
        }

        override fun onAvailableCommandsChanged(availableCommands: Player.Commands) {
            notificationListeners.sendEvent(Player.EVENT_AVAILABLE_COMMANDS_CHANGED) {
                it.onAvailableCommandsChanged(this@QueueAwarePlayer.availableCommands)
            }
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            notificationListeners.sendEvent(Player.EVENT_PLAYBACK_STATE_CHANGED) {
                it.onPlaybackStateChanged(playbackState)
            }
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            notificationListeners.sendEvent(Player.EVENT_IS_PLAYING_CHANGED) {
                it.onIsPlayingChanged(isPlaying)
            }
        }

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            notificationListeners.sendEvent(Player.EVENT_MEDIA_ITEM_TRANSITION) {
                it.onMediaItemTransition(mediaItem, reason)
            }
        }

        override fun onPlayerError(error: PlaybackException) {
            notificationListeners.sendEvent(Player.EVENT_PLAYER_ERROR) {
                it.onPlayerError(error)
            }
        }

        override fun onPositionDiscontinuity(
            oldPosition: Player.PositionInfo,
            newPosition: Player.PositionInfo,
            reason: Int
        ) {
            notificationListeners.sendEvent(Player.EVENT_POSITION_DISCONTINUITY) {
                it.onPositionDiscontinuity(oldPosition, newPosition, reason)
            }
        }

        override fun onTracksChanged(tracks: Tracks) {
            notificationListeners.sendEvent(Player.EVENT_TRACKS_CHANGED) {
                it.onTracksChanged(tracks)
            }
        }

        override fun onIsLoadingChanged(isLoading: Boolean) {
            notificationListeners.sendEvent(Player.EVENT_IS_LOADING_CHANGED) {
                it.onIsLoadingChanged(isLoading)
            }
        }

        override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
            notificationListeners.sendEvent(Player.EVENT_PLAY_WHEN_READY_CHANGED) {
                it.onPlayWhenReadyChanged(playWhenReady, reason)
            }
        }

        override fun onMetadata(metadata: Metadata) {
            notificationListeners.sendEvent(Player.EVENT_METADATA) {
                it.onMetadata(metadata)
            }
        }
    }

    private var lastTimelineForwardAtMs: Long = 0L

    override fun addListener(listener: Player.Listener) {
        notificationListeners.add(listener)
    }

    override fun removeListener(listener: Player.Listener) {
        notificationListeners.remove(listener)
    }

    init {
        exoPlayer.addListener(exoEventBridge)
    }

    fun beginQueueReplacement(mediaItems: List<MediaItem> = emptyList(), startIndex: Int = 0) {
        isReplacingQueue = true
    }

    fun endQueueReplacement() {
        isReplacingQueue = false
        // Forward the final timeline once so MediaSession catches up.
        notificationListeners.sendEvent(Player.EVENT_TIMELINE_CHANGED) {
            it.onTimelineChanged(exoPlayer.currentTimeline, Player.TIMELINE_CHANGE_REASON_PLAYLIST_CHANGED)
        }
        notificationListeners.sendEvent(Player.EVENT_AVAILABLE_COMMANDS_CHANGED) {
            it.onAvailableCommandsChanged(this.availableCommands)
        }
    }

    // Single source of truth: ExoPlayer's actual timeline. Never publish synthetic timelines.
    override fun getCurrentTimeline(): Timeline = exoPlayer.currentTimeline

    override fun getMediaItemCount(): Int = exoPlayer.mediaItemCount

    override fun getCurrentMediaItemIndex(): Int = exoPlayer.currentMediaItemIndex

    override fun getMediaItemAt(index: Int): MediaItem {
        return exoPlayer.getMediaItemAt(index)
    }

    override fun hasNextMediaItem(): Boolean {
        val exoCount = exoPlayer.mediaItemCount
        if (exoCount > 1) {
            return exoPlayer.currentMediaItemIndex < exoCount - 1 || queueSnapshotProvider().canPlayNext
        }
        return queueSnapshotProvider().canPlayNext ||
            queueSnapshotProvider().priorityQueue.isNotEmpty() ||
            queueSnapshotProvider().upNextQueue.isNotEmpty()
    }

    override fun hasPreviousMediaItem(): Boolean {
        val exoCount = exoPlayer.mediaItemCount
        if (exoCount > 1) {
            return exoPlayer.currentMediaItemIndex > 0 || queueSnapshotProvider().canPlayPrevious
        }
        return queueSnapshotProvider().canPlayPrevious
    }

    override fun isCommandAvailable(command: @Player.Command Int): Boolean {
        return when (command) {
            Player.COMMAND_SEEK_TO_NEXT,
            Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM -> hasNextMediaItem()
            Player.COMMAND_SEEK_TO_PREVIOUS,
            Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM -> hasPreviousMediaItem()
            else -> super.isCommandAvailable(command)
        }
    }

    override fun getAvailableCommands(): Player.Commands {
        return super.getAvailableCommands().buildUpon().apply {
            if (hasNextMediaItem()) {
                add(Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM)
                add(Player.COMMAND_SEEK_TO_NEXT)
            }
            if (hasPreviousMediaItem()) {
                add(Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM)
                add(Player.COMMAND_SEEK_TO_PREVIOUS)
            }
        }.build()
    }

    // An idle/failed player is not buffering. Mirroring the real state also
    // lets controllers prepare a stopped stream before seeking.
    override fun getPlaybackState(): Int = super.getPlaybackState()

    override fun seekToNextMediaItem() {
        val exoCount = exoPlayer.mediaItemCount
        if (exoCount > 1 &&
            exoPlayer.currentMediaItemIndex < exoCount - 1 &&
            exoPlayer.getMediaItemAt(exoPlayer.currentMediaItemIndex + 1).localConfiguration?.uri != null
        ) {
            super.seekToNextMediaItem()
            return
        }
        if (queueSnapshotProvider().canPlayNext) {
            onSkipToNext()
        } else {
            super.seekToNextMediaItem()
        }
    }

    override fun seekToPreviousMediaItem() {
        if (queueSnapshotProvider().canPlayPrevious) {
            onSkipToPrevious()
        } else {
            super.seekToPreviousMediaItem()
        }
    }

    override fun seekToNext() {
        if (queueSnapshotProvider().canPlayNext) {
            onSkipToNext()
        } else {
            super.seekToNext()
        }
    }

    override fun seekToPrevious() {
        if (queueSnapshotProvider().canPlayPrevious) {
            onSkipToPrevious()
        } else {
            super.seekToPrevious()
        }
    }

    override fun seekTo(mediaItemIndex: Int, positionMs: Long) {
        super.seekTo(mediaItemIndex, positionMs)
    }

    override fun seekToDefaultPosition(mediaItemIndex: Int) {
        super.seekToDefaultPosition(mediaItemIndex)
    }

    companion object {
        private const val TAG = "QueueAwarePlayer"
    }
}

