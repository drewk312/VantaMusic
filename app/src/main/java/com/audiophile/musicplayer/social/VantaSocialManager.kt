package com.audiophile.musicplayer.social

import android.content.Context
import com.audiophile.musicplayer.account.AccountManager
import com.audiophile.musicplayer.playback.NowPlayingState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.StateFlow

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
    )
) {
    val feed: Flow<FriendFeed> = repository.feed

    private val _pendingInvites = MutableSharedFlow<String>()
    val pendingInvites: Flow<String> = _pendingInvites.asSharedFlow()

    fun addFriendByHandle(handle: String) {
        val normalized = handle.trim().lowercase()
        if (normalized.isBlank()) return
        repository.addFriend(
            VantaFriend(
                id = normalized,
                displayName = handle.trim(),
                avatarSeed = handle.trim().take(2).uppercase()
            )
        )
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

    fun publishOwnActivity(nowPlaying: NowPlayingState) {
        repository.publishOwnActivity(nowPlaying)
    }
}


