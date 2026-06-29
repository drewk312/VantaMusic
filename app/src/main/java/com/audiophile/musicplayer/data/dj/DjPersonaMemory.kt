package com.audiophile.musicplayer.data.dj

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** Persistent DJ relationship memory — feedback signals that shape future sets and prompts. */
class DjPersonaMemory(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("vanta_dj_persona", Context.MODE_PRIVATE)

    enum class SignalType {
        THUMBS_UP,
        THUMBS_DOWN,
        SKIP,
        REPLAY,
        SAVE,
        FULL_PLAY
    }

    data class FeedbackSignal(
        val type: SignalType,
        val trackId: Long,
        val title: String,
        val artist: String,
        val versionLabel: String?,
        val sessionId: Long,
        val timestamp: Long
    ) {
        fun label(): String = buildString {
            append(title)
            if (!versionLabel.isNullOrBlank()) append(" ($versionLabel)")
            append(" — ").append(artist)
        }
    }

    fun totalSessions(): Int = prefs.getInt("total_sessions", 0)

    fun incrementSession() {
        prefs.edit().putInt("total_sessions", totalSessions() + 1).apply()
    }

    fun recordThumbsUp(
        trackId: Long,
        title: String,
        artist: String,
        versionLabel: String?,
        sessionId: Long
    ) {
        appendSignal(
            FeedbackSignal(
                type = SignalType.THUMBS_UP,
                trackId = trackId,
                title = title,
                artist = artist,
                versionLabel = versionLabel,
                sessionId = sessionId,
                timestamp = System.currentTimeMillis()
            )
        )
    }

    fun recordThumbsDown(
        trackId: Long,
        title: String,
        artist: String,
        versionLabel: String?,
        sessionId: Long
    ) {
        appendSignal(
            FeedbackSignal(
                type = SignalType.THUMBS_DOWN,
                trackId = trackId,
                title = title,
                artist = artist,
                versionLabel = versionLabel,
                sessionId = sessionId,
                timestamp = System.currentTimeMillis()
            )
        )
    }

    fun recordSkip(trackId: Long, title: String, artist: String, versionLabel: String?, sessionId: Long) {
        appendSignal(
            FeedbackSignal(
                type = SignalType.SKIP,
                trackId = trackId,
                title = title,
                artist = artist,
                versionLabel = versionLabel,
                sessionId = sessionId,
                timestamp = System.currentTimeMillis()
            )
        )
    }

    fun recordReplay(trackId: Long, title: String, artist: String, versionLabel: String?, sessionId: Long) {
        appendSignal(
            FeedbackSignal(
                type = SignalType.REPLAY,
                trackId = trackId,
                title = title,
                artist = artist,
                versionLabel = versionLabel,
                sessionId = sessionId,
                timestamp = System.currentTimeMillis()
            )
        )
    }

    fun recordSave(trackId: Long, title: String, artist: String, versionLabel: String?, sessionId: Long) {
        appendSignal(
            FeedbackSignal(
                type = SignalType.SAVE,
                trackId = trackId,
                title = title,
                artist = artist,
                versionLabel = versionLabel,
                sessionId = sessionId,
                timestamp = System.currentTimeMillis()
            )
        )
    }

    fun recordFullPlay(trackId: Long, title: String, artist: String, versionLabel: String?, sessionId: Long) {
        appendSignal(
            FeedbackSignal(
                type = SignalType.FULL_PLAY,
                trackId = trackId,
                title = title,
                artist = artist,
                versionLabel = versionLabel,
                sessionId = sessionId,
                timestamp = System.currentTimeMillis()
            )
        )
    }

    fun recentSignals(limit: Int = 24): List<FeedbackSignal> =
        loadSignals().take(limit)

    fun recentLikeLabels(): List<String> =
        recentSignals()
            .filter { it.type == SignalType.THUMBS_UP || it.type == SignalType.SAVE || it.type == SignalType.REPLAY }
            .map { it.label() }
            .distinct()
            .take(8)

    fun recentSkipLabels(): List<String> =
        recentSignals()
            .filter { it.type == SignalType.THUMBS_DOWN || it.type == SignalType.SKIP }
            .map { it.label() }
            .distinct()
            .take(8)

    fun artistAffinity(artist: String): Float {
        val key = artist.trim().lowercase()
        if (key.isBlank()) return 0f
        var score = 0f
        for (signal in loadSignals()) {
            if (signal.artist.trim().lowercase() != key) continue
            score += when (signal.type) {
                SignalType.THUMBS_UP -> 2.5f
                SignalType.SAVE -> 2f
                SignalType.REPLAY -> 1.8f
                SignalType.FULL_PLAY -> 1.2f
                SignalType.SKIP -> -0.8f
                SignalType.THUMBS_DOWN -> -2.5f
            }
        }
        return score
    }

    fun trackAffinity(trackId: Long): Float {
        var score = 0f
        for (signal in loadSignals()) {
            if (signal.trackId != trackId) continue
            score += when (signal.type) {
                SignalType.THUMBS_UP -> 3f
                SignalType.SAVE -> 2.5f
                SignalType.REPLAY -> 2f
                SignalType.FULL_PLAY -> 1f
                SignalType.SKIP -> -1f
                SignalType.THUMBS_DOWN -> -3f
            }
        }
        return score
    }

    fun dislikedArtists(): Set<String> =
        loadSignals()
            .filter { it.type == SignalType.THUMBS_DOWN }
            .map { it.artist.trim().lowercase() }
            .filter { it.isNotBlank() }
            .toSet()

    fun favoredArtists(): Set<String> =
        loadSignals()
            .filter { it.type == SignalType.THUMBS_UP || it.type == SignalType.SAVE || it.type == SignalType.REPLAY }
            .map { it.artist.trim().lowercase() }
            .filter { it.isNotBlank() }
            .toSet()

    fun blockedTrackIds(): Set<Long> =
        prefs.getString("blocked_tracks", "").orEmpty()
            .split("|")
            .mapNotNull { it.toLongOrNull() }
            .toSet()

    fun blockedArtists(): Set<String> =
        prefs.getString("blocked_artists", "").orEmpty()
            .split("|")
            .filter { it.isNotBlank() }
            .toSet()

    fun blockTrack(trackId: Long, artist: String) {
        val tracks = blockedTrackIds().toMutableSet()
        tracks.add(trackId)
        prefs.edit().putString("blocked_tracks", tracks.joinToString("|")).apply()
        if (artist.isNotBlank()) {
            val artists = blockedArtists().toMutableSet()
            artists.add(artist.trim().lowercase())
            prefs.edit().putString("blocked_artists", artists.joinToString("|")).apply()
        }
    }

    private fun appendSignal(signal: FeedbackSignal) {
        val updated = (listOf(signal) + loadSignals()).take(80)
        prefs.edit().putString("signals_json", encodeSignals(updated)).apply()
    }

    private fun loadSignals(): List<FeedbackSignal> {
        val raw = prefs.getString("signals_json", null)
        if (raw.isNullOrBlank()) return migrateLegacyLabels()
        return try {
            decodeSignals(raw)
        } catch (_: Exception) {
            migrateLegacyLabels()
        }
    }

    private fun migrateLegacyLabels(): List<FeedbackSignal> {
        val likes = prefs.getString("recent_likes", "").orEmpty().split("|").filter { it.isNotBlank() }
        val skips = prefs.getString("recent_skips", "").orEmpty().split("|").filter { it.isNotBlank() }
        return emptyList()
    }

    private fun encodeSignals(signals: List<FeedbackSignal>): String {
        val array = JSONArray()
        signals.forEach { signal ->
            array.put(
                JSONObject()
                    .put("type", signal.type.name)
                    .put("trackId", signal.trackId)
                    .put("title", signal.title)
                    .put("artist", signal.artist)
                    .put("versionLabel", signal.versionLabel)
                    .put("sessionId", signal.sessionId)
                    .put("timestamp", signal.timestamp)
            )
        }
        return array.toString()
    }

    private fun decodeSignals(raw: String): List<FeedbackSignal> {
        val array = JSONArray(raw)
        val out = mutableListOf<FeedbackSignal>()
        for (i in 0 until array.length()) {
            val obj = array.getJSONObject(i)
            out.add(
                FeedbackSignal(
                    type = SignalType.valueOf(obj.getString("type")),
                    trackId = obj.getLong("trackId"),
                    title = obj.getString("title"),
                    artist = obj.getString("artist"),
                    versionLabel = obj.optString("versionLabel").takeIf { it.isNotBlank() },
                    sessionId = obj.optLong("sessionId", 0L),
                    timestamp = obj.optLong("timestamp", 0L)
                )
            )
        }
        return out
    }
}
