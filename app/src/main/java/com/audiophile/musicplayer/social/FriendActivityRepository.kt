package com.audiophile.musicplayer.social

import com.audiophile.musicplayer.account.AccountManager
import com.audiophile.musicplayer.playback.NowPlayingState
import kotlinx.coroutines.flow.StateFlow

/**
 * Repository for friend listening activity.
 *
 * Currently backed by a local cache. When a gateway implementation is wired in,
 * this class becomes the single point where remote events are merged with local
 * state. It never blocks playback; it is purely social/optional.
 */
class FriendActivityRepository(
    private val localSource: FriendActivityLocalSource,
    private val accountManager: AccountManager
) {
    val feed: StateFlow<FriendFeed> = localSource.friendFeed

    fun addFriend(friend: VantaFriend) {
        localSource.addOrUpdateFriend(friend)
    }

    fun removeFriend(friendId: String) {
        localSource.removeFriend(friendId)
    }

    fun updateEvents(events: List<FriendListeningEvent>) {
        localSource.updateEvents(events)
    }

    /**
     * Publish the current user's own listening activity.
     * If sharing is disabled in the account profile, the event is cleared.
     */
    fun publishOwnActivity(nowPlaying: NowPlayingState) {
        val profile = accountManager.profile.value
        if (!profile.shareListeningActivity || nowPlaying.trackId == null) {
            localSource.clearOwnActivity()
            return
        }
        val event = FriendListeningEvent(
            friendId = profile.vantaUserId,
            friendDisplayName = profile.displayName,
            trackId = nowPlaying.trackId,
            title = nowPlaying.title ?: "Unknown",
            artist = nowPlaying.artist ?: "Unknown",
            album = nowPlaying.album,
            artworkUrl = nowPlaying.artworkUrl,
            sourceLabel = nowPlaying.qualityInfo?.bestQualityLabel(),
            positionMs = nowPlaying.positionMs.coerceAtLeast(0L),
            durationMs = nowPlaying.durationMs.coerceAtLeast(0L)
        )
        localSource.updateOwnActivity(event)
    }
}
