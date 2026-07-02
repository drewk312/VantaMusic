package com.audiophile.musicplayer.social

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

/**
 * Local storage for friend list and cached friend activity feed.
 *
 * Uses plain SharedPreferences for the friend graph (non-sensitive) and keeps a
 * small in-memory snapshot so the HomeScreen feed can render immediately. The
 * actual listening events are fetched/pushed through the gateway.
 */
class FriendActivityLocalSource(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("vanta_social", Context.MODE_PRIVATE)

    private val _friendFeed = MutableStateFlow(loadFeed())
    val friendFeed: StateFlow<FriendFeed> = _friendFeed.asStateFlow()

    fun addOrUpdateFriend(friend: VantaFriend) {
        val current = loadFriends().toMutableList()
        current.removeAll { it.id == friend.id }
        current.add(friend)
        saveFriends(current)
        _friendFeed.value = loadFeed()
    }

    fun removeFriend(friendId: String) {
        val current = loadFriends().filterNot { it.id == friendId }
        saveFriends(current)
        _friendFeed.value = loadFeed()
    }

    fun updateEvents(events: List<FriendListeningEvent>) {
        val friends = loadFriends()
        val feed = FriendFeed(
            friends = friends,
            events = events.sortedByDescending { it.startedAtMs },
            updatedAtMs = System.currentTimeMillis()
        )
        saveFeed(feed)
        _friendFeed.value = feed
    }

    fun updateOwnActivity(event: FriendListeningEvent) {
        // Own activity is stored separately so it can be included in the feed preview.
        prefs.edit {
            putString(KEY_OWN_ACTIVITY, event.toJson().toString())
        }
    }

    fun clearOwnActivity() {
        prefs.edit { remove(KEY_OWN_ACTIVITY) }
    }

    private fun loadFriends(): List<VantaFriend> {
        val raw = prefs.getString(KEY_FRIENDS, null) ?: return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            List(array.length()) { index ->
                val obj = array.getJSONObject(index)
                VantaFriend(
                    id = obj.getString("id"),
                    displayName = obj.getString("displayName"),
                    avatarSeed = obj.optString("avatarSeed", obj.getString("displayName")),
                    isFavorite = obj.optBoolean("isFavorite", false),
                    addedAtMs = obj.optLong("addedAtMs", System.currentTimeMillis())
                )
            }
        }.getOrDefault(emptyList())
    }

    private fun saveFriends(friends: List<VantaFriend>) {
        val array = JSONArray()
        friends.forEach { friend ->
            array.put(
                JSONObject().apply {
                    put("id", friend.id)
                    put("displayName", friend.displayName)
                    put("avatarSeed", friend.avatarSeed)
                    put("isFavorite", friend.isFavorite)
                    put("addedAtMs", friend.addedAtMs)
                }
            )
        }
        prefs.edit { putString(KEY_FRIENDS, array.toString()) }
    }

    private fun loadFeed(): FriendFeed {
        val friends = loadFriends()
        val raw = prefs.getString(KEY_EVENTS, null)
        val events = runCatching {
            if (raw.isNullOrBlank()) return@runCatching emptyList()
            val array = JSONArray(raw)
            List(array.length()) { index ->
                val obj = array.getJSONObject(index)
                FriendListeningEvent(
                    friendId = obj.getString("friendId"),
                    friendDisplayName = obj.getString("friendDisplayName"),
                    trackId = obj.getString("trackId"),
                    title = obj.getString("title"),
                    artist = obj.getString("artist"),
                    album = obj.optString("album").takeIf { it.isNotBlank() },
                    artworkUrl = obj.optString("artworkUrl").takeIf { it.isNotBlank() },
                    sourceLabel = obj.optString("sourceLabel").takeIf { it.isNotBlank() },
                    startedAtMs = obj.optLong("startedAtMs", 0L),
                    positionMs = obj.optLong("positionMs", 0L),
                    durationMs = obj.optLong("durationMs", 0L)
                )
            }
        }.getOrDefault(emptyList())
        return FriendFeed(friends, events, System.currentTimeMillis())
    }

    private fun saveFeed(feed: FriendFeed) {
        val array = JSONArray()
        feed.events.forEach { event ->
            array.put(event.toJson())
        }
        prefs.edit {
            putString(KEY_EVENTS, array.toString())
            putLong(KEY_UPDATED_AT, feed.updatedAtMs)
        }
    }

    private fun FriendListeningEvent.toJson(): JSONObject =
        JSONObject().apply {
            put("friendId", friendId)
            put("friendDisplayName", friendDisplayName)
            put("trackId", trackId)
            put("title", title)
            put("artist", artist)
            put("album", album)
            put("artworkUrl", artworkUrl)
            put("sourceLabel", sourceLabel)
            put("startedAtMs", startedAtMs)
            put("positionMs", positionMs)
            put("durationMs", durationMs)
        }

    companion object {
        private const val KEY_FRIENDS = "friends"
        private const val KEY_EVENTS = "events"
        private const val KEY_UPDATED_AT = "updated_at"
        private const val KEY_OWN_ACTIVITY = "own_activity"
    }
}



