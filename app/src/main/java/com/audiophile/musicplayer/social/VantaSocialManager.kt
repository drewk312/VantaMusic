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
    private val localSource: FriendActivityLocalSource = FriendActivityLocalSource(context),
    private val repository: FriendActivityRepository = FriendActivityRepository(
        localSource,
        accountManager
    ),
    private val syncManager: com.audiophile.musicplayer.sync.VantaSyncManager? = null
) {
    val feed: Flow<FriendFeed> = repository.feed

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _pendingInvites = MutableSharedFlow<String>()
    val pendingInvites: Flow<String> = _pendingInvites.asSharedFlow()

    private var lastActivityPublishMs = 0L

    /** The stable code friends use to add this user. Opaque and rotatable. */
    fun friendCode(): String = localSource.getOwnFriendCode(accountManager.ensureUserId())

    fun rotateFriendCode(): String {
        // Rotates the local opaque code. Gateway friend-code mapping is a future enhancement.
        return localSource.rotateOwnFriendCode(accountManager.ensureUserId())
    }

    fun addFriendByHandle(handle: String, displayName: String? = null) {
        val normalized = handle.trim().lowercase()
        if (normalized.isBlank()) return
        if (normalized == friendCode().trim().lowercase()) {
            Log.d(TAG, "ignored_self_friend handle=$normalized")
            return
        }
        val name = displayName?.trim()?.takeIf { it.isNotBlank() } ?: handle.trim()
        repository.addFriend(
            VantaFriend(
                id = normalized,
                displayName = name,
                avatarSeed = name.take(2).uppercase()
            )
        )
        val sync = syncManager
        if (sync != null) {
            scope.launch {
                runCatching { sync.addFriendOnGateway(normalized) }
            }
        }
        refreshFriendFeed()
    }

    fun addFriend(friend: VantaFriend) {
        repository.addFriend(friend)
    }

    fun removeFriend(friendId: String) {
        repository.removeFriend(friendId)
    }

    fun likeTrack(friendId: String, track: FriendLikedTrack) {
        repository.addLikedTrack(friendId, track)
    }

    fun unlikeTrack(friendId: String, trackId: String) {
        repository.removeLikedTrack(friendId, trackId)
    }

    fun getFriendById(friendId: String): VantaFriend? {
        return repository.getFriendById(friendId)
    }

    fun getFriendEvents(friendId: String): List<FriendListeningEvent> {
        return repository.getFriendEvents(friendId)
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
        val nowMs = System.currentTimeMillis()
        if (nowMs - lastActivityPublishMs < ACTIVITY_PUBLISH_MIN_INTERVAL_MS) return
        lastActivityPublishMs = nowMs
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
            val remoteFriendIds = runCatching { sync.fetchServerFriendIds() }
                .getOrDefault(emptyList())
                .map(::normalizeFriendId)
                .filter { it.isNotBlank() }
                .toSet()
            if (remoteFriendIds.isNotEmpty()) {
                syncServerFriends(remoteFriendIds)
            }
            val events = runCatching { sync.fetchFriendActivity() }.getOrDefault(emptyList())
            if (events.isEmpty()) return@launch
            val friendIds = (repository.feed.value.friends.map { normalizeFriendId(it.id) } + remoteFriendIds)
                .filter { it.isNotBlank() }
                .toSet()
            val relevant = if (friendIds.isEmpty()) {
                events
            } else {
                events.filter { normalizeFriendId(it.friendId) in friendIds }
            }
            if (relevant.isNotEmpty()) {
                repository.updateEvents(relevant)
                hydrateFriendMetadataFromEvents(relevant)
                Log.d(TAG, "feed_refresh events=${relevant.size}")
            }
        }
    }

    private fun syncServerFriends(friendIds: Set<String>) {
        val existing = repository.feed.value.friends.associateBy { normalizeFriendId(it.id) }
        friendIds.forEach { friendId ->
            if (friendId.isBlank() || friendId == normalizeFriendId(friendCode())) return@forEach
            if (existing.containsKey(friendId)) return@forEach
            repository.addFriend(
                VantaFriend(
                    id = friendId,
                    displayName = friendId,
                    avatarSeed = friendId.take(2).uppercase()
                )
            )
        }
    }

    private fun hydrateFriendMetadataFromEvents(events: List<FriendListeningEvent>) {
        val latestByFriend = events
            .sortedByDescending { it.startedAtMs }
            .distinctBy { normalizeFriendId(it.friendId) }
            .associateBy { normalizeFriendId(it.friendId) }
        if (latestByFriend.isEmpty()) return
        val nowMs = System.currentTimeMillis()
        repository.feed.value.friends.forEach { friend ->
            val normalizedId = normalizeFriendId(friend.id)
            val latest = latestByFriend[normalizedId] ?: return@forEach
            val eventName = latest.friendDisplayName.trim().ifBlank { friend.displayName }
            val shouldUpgradeName = friend.displayName.isBlank() || normalizeFriendId(friend.displayName) == normalizedId
            val updated = friend.copy(
                displayName = if (shouldUpgradeName) eventName else friend.displayName,
                avatarSeed = if (shouldUpgradeName) eventName else friend.avatarSeed,
                isOnline = isLikelyOnline(latest.startedAtMs, nowMs),
                lastSeenAtMs = maxOf(friend.lastSeenAtMs, latest.startedAtMs)
            )
            if (updated != friend) {
                repository.addFriend(updated)
            }
        }
    }


    /**
     * Resolve a friend listening event to a stable canonical identity.
     * The UI can use this to find a playable local/source match via identity search.
     */
    fun resolveFriendEventIdentity(event: FriendListeningEvent): String {
        return com.audiophile.musicplayer.data.canonical.CanonicalIdentityResolver.generateCanonicalId(
            isrc = null,
            _unused = null,
            title = event.title,
            artist = event.artist,
            album = event.album,
            durationMs = event.durationMs
        )
    }
    private fun normalizeFriendId(value: String): String = value.trim().lowercase()

    private fun isLikelyOnline(startedAtMs: Long, nowMs: Long): Boolean {
        if (startedAtMs <= 0L) return false
        return nowMs - startedAtMs <= ONLINE_WINDOW_MS
    }

    private companion object {
        const val TAG = "VANTA_SOCIAL"
        const val ONLINE_WINDOW_MS = 15 * 60 * 1000L
        const val ACTIVITY_PUBLISH_MIN_INTERVAL_MS = 30_000L
    }
}


