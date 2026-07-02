package com.audiophile.musicplayer.social

import android.content.Context
import android.util.Log
import com.audiophile.musicplayer.account.AccountManager
import com.audiophile.musicplayer.playback.NowPlayingState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch

/**
 * Social coordinator: friends list, listening feed, and "share what I am listening to".
 *
 * This is the public API used by UI layers. The repository handles storage;
 * this manager adds convenience methods and in-memory events for immediate UI updates.
 */
class VantaSocialManager(
    context: Context,
    private val accountManager: AccountManager,
    private val repository: FriendActivityRepository = FriendActivityRepository(
        FriendActivityLocalSource(context),
        accountManager
    ),
    private val syncManager: com.audiophile.musicplayer.sync.VantaSyncManager? = null
) {
    val feed: Flow<FriendFeed> = repository.feed

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _pendingInvites = MutableSharedFlow<String>()
    val pendingInvites: Flow<String> = _pendingInvites.asSharedFlow()

    /** The stable code friends use to add this user. */
    fun friendCode(): String = accountManager.ensureUserId()

    fun addFriendByHandle(handle: String, displayName: String? = null) {
        val normalized = handle.trim().lowercase()
        if (normalized.isBlank()) return
        val name = displayName?.trim()?.takeIf { it.isNotBlank() } ?: handle.trim()
        repository.addFriend(
            VantaFriend(
                id = normalized,
                displayName = name,
                avatarSeed = name.take(2).uppercase()
            )
        )
        refreshFriendFeed()
    }

    fun addFriend(friend: VantaFriend) {
        repository.addFriend(friend)
    }

    fun removeFriend(friendId: String) {
        repository.removeFriend(friendId)
    }

    fun setShareListeningActivity(enabled: Boolean) {
        accountManager.setShareListeningActivity(enabled)
    }

    /**
     * Publish own listening activity locally and (when sharing is enabled)
     * push it to the gateway so friends see it. Never blocks the caller.
     */
    fun publishOwnActivity(nowPlaying: NowPlayingState) {
        repository.publishOwnActivity(nowPlaying)
        val profile = accountManager.profile.value
        if (!profile.shareListeningActivity || nowPlaying.trackId == null) return
        val sync = syncManager ?: return
        val event = FriendListeningEvent(
            friendId = profile.vantaUserId,
            friendDisplayName = profile.displayName,
            trackId = nowPlaying.trackId ?: return,
            title = nowPlaying.title ?: "Unknown",
            artist = nowPlaying.artist ?: "Unknown",
            album = nowPlaying.album,
            artworkUrl = nowPlaying.artworkUrl,
            sourceLabel = nowPlaying.qualityInfo?.bestQualityLabel(),
            positionMs = nowPlaying.positionMs.coerceAtLeast(0L),
            durationMs = nowPlaying.durationMs.coerceAtLeast(0L)
        )
        scope.launch {
            val ok = sync.postOwnActivity(event)
            Log.d(TAG, "publish_gateway ok=$ok track='${event.title}'")
        }
    }

    /**
     * Pull the latest friend activity from the gateway and merge into the local feed.
     * Safe to call often; failures leave the cached feed untouched.
     */
    fun refreshFriendFeed() {
        val sync = syncManager ?: return
        scope.launch {
            val events = runCatching { sync.fetchFriendActivity() }.getOrDefault(emptyList())
            if (events.isEmpty()) return@launch
            val friendIds = repository.feed.value.friends.map { it.id }.toSet()
            val relevant = if (friendIds.isEmpty()) events
            else events.filter { it.friendId.lowercase() in friendIds }
            if (relevant.isNotEmpty()) {
                repository.updateEvents(relevant)
                Log.d(TAG, "feed_refresh events=${relevant.size}")
            }
        }
    }

    private companion object {
        const val TAG = "VANTA_SOCIAL"
    }
}


