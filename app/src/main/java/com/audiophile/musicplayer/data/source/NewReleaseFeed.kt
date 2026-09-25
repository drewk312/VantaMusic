package com.audiophile.musicplayer.data.source

import android.content.Context
import androidx.core.content.edit
import com.google.gson.Gson
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import kotlin.random.Random

enum class NewReleaseFeedStatus {
    IDLE,
    LOADING,
    CONTENT,
    STALE,
    ERROR
}

data class NewReleaseFeedSnapshot(
    val tracks: List<SourceSearchResult> = emptyList(),
    val status: NewReleaseFeedStatus = NewReleaseFeedStatus.IDLE,
    val lastUpdatedAtMs: Long? = null,
    val errorMessage: String? = null
)

data class NewReleaseCacheEntry(
    val updatedAtMs: Long,
    val tracks: List<SourceSearchResult>
) {
    fun isFresh(nowMs: Long): Boolean =
        tracks.isNotEmpty() && nowMs - updatedAtMs in 0..NewReleaseFeedCache.FRESH_FOR_MS
}

object NewReleaseFeedPolicy {
    /**
     * Stable for one local calendar day, but materially reshuffled the next.
     * This is an editorial rotation, not a claim that every track released today.
     */
    fun dailyEdit(
        tracks: List<SourceSearchResult>,
        nowMs: Long,
        limit: Int = 5,
        zoneId: ZoneId = ZoneId.systemDefault()
    ): List<SourceSearchResult> {
        if (limit <= 0) return emptyList()
        val valid = tracks
            .filter { it.id.isNotBlank() && it.title.isNotBlank() && it.artist.isNotBlank() }
            .distinctBy { "${it.title.trim()}|${it.artist.trim()}".lowercase() }
        if (valid.size <= 1) return valid.take(limit)
        val epochDay = Instant.ofEpochMilli(nowMs).atZone(zoneId).toLocalDate().toEpochDay()
        val catalogSignature = valid.fold(17) { acc, track -> 31 * acc + track.id.hashCode() }
        val seed = epochDay.toInt() xor catalogSignature
        return valid.shuffled(Random(seed)).take(limit.coerceAtMost(valid.size))
    }

    fun isVerifiedRecentRelease(
        track: SourceSearchResult,
        nowMs: Long,
        maxAgeDays: Long = 60,
        zoneId: ZoneId = ZoneId.systemDefault()
    ): Boolean {
        if (track.discoveryKind != null && track.discoveryKind != "new_release") return false
        val dateStr = track.releaseDate ?: return false
        val releaseDate = runCatching { LocalDate.parse(dateStr) }.getOrNull()
            ?: runCatching {
                val parts = dateStr.split("-")
                when (parts.size) {
                    2 -> LocalDate.of(parts[0].toInt(), parts[1].toInt(), 1)
                    1 -> LocalDate.of(parts[0].toInt(), 1, 1)
                    else -> null
                }
            }.getOrNull() ?: return false
        val today = Instant.ofEpochMilli(nowMs).atZone(zoneId).toLocalDate()
        val ageDays = ChronoUnit.DAYS.between(releaseDate, today)
        return ageDays in -7..maxAgeDays
    }

    fun fromCache(entry: NewReleaseCacheEntry?, nowMs: Long): NewReleaseFeedSnapshot {
        if (entry == null || entry.tracks.isEmpty()) return NewReleaseFeedSnapshot()
        return NewReleaseFeedSnapshot(
            tracks = entry.tracks,
            status = if (entry.isFresh(nowMs)) NewReleaseFeedStatus.CONTENT else NewReleaseFeedStatus.STALE,
            lastUpdatedAtMs = entry.updatedAtMs,
            errorMessage = if (entry.isFresh(nowMs)) null else "Showing the last available release feed."
        )
    }

    fun loading(current: NewReleaseFeedSnapshot): NewReleaseFeedSnapshot =
        current.copy(status = NewReleaseFeedStatus.LOADING, errorMessage = null)

    fun completed(
        current: NewReleaseFeedSnapshot,
        incoming: List<SourceSearchResult>,
        nowMs: Long
    ): NewReleaseFeedSnapshot {
        val valid = incoming
            .filter { it.id.isNotBlank() && it.title.isNotBlank() && it.artist.isNotBlank() }
            .distinctBy { "${it.title.trim()}|${it.artist.trim()}".lowercase() }
            .take(30)
        if (valid.isNotEmpty()) {
            return NewReleaseFeedSnapshot(
                tracks = valid,
                status = NewReleaseFeedStatus.CONTENT,
                lastUpdatedAtMs = nowMs,
                errorMessage = null
            )
        }
        if (current.tracks.isNotEmpty()) {
            return current.copy(
                status = NewReleaseFeedStatus.STALE,
                errorMessage = "The live feed is unavailable. Showing your last successful update."
            )
        }
        return NewReleaseFeedSnapshot(
            status = NewReleaseFeedStatus.ERROR,
            errorMessage = "The live release feed is temporarily unavailable."
        )
    }
}

class NewReleaseFeedCache(
    context: Context,
    private val gson: Gson = Gson()
) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun load(): NewReleaseCacheEntry? {
        val raw = prefs.getString(KEY_PAYLOAD, null) ?: return null
        return runCatching { gson.fromJson(raw, NewReleaseCacheEntry::class.java) }
            .getOrNull()
            ?.takeIf { entry ->
                entry.updatedAtMs > 0L && entry.tracks.isNotEmpty() &&
                    entry.tracks.all { it.id.isNotBlank() && it.title.isNotBlank() && it.artist.isNotBlank() }
            }
    }

    fun save(snapshot: NewReleaseFeedSnapshot) {
        val updatedAt = snapshot.lastUpdatedAtMs ?: return
        if (snapshot.tracks.isEmpty() || snapshot.status != NewReleaseFeedStatus.CONTENT) return
        val entry = NewReleaseCacheEntry(updatedAtMs = updatedAt, tracks = snapshot.tracks.take(30))
        prefs.edit { putString(KEY_PAYLOAD, gson.toJson(entry)) }
    }

    companion object {
        const val FRESH_FOR_MS = 6L * 60L * 60L * 1_000L
        private const val PREFS_NAME = "vanta_new_release_feed"
        private const val KEY_PAYLOAD = "latest_success"
    }
}
