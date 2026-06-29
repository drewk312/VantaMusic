package com.audiophile.musicplayer.data.lastfm

import android.content.Context
import android.util.Log
import com.audiophile.musicplayer.ResolverConfigStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Resolves artist genres from Last.fm top tags, cached in SharedPreferences.
 * Used by jukebox station matching when local genre metadata is missing.
 */
class LastFmGenreResolver(
    context: Context,
    private val configStore: ResolverConfigStore,
    private val apiClient: LastFmApiClient = LastFmApiClient()
) {
    companion object {
        private const val TAG = "LastFmGenreResolver"
        private const val PREFS = "lastfm_genre_cache"
        private const val CACHE_MS = 30L * 86_400_000L
        private const val EMPTY_CACHE_MS = 48L * 3_600_000L
        private const val MIN_TAG_COUNT = 10
        private const val MAX_TAGS = 3

        /** Maps Last.fm tag names to jukebox station keyword fragments. */
        val JUKEBOX_TAG_ALIASES: Map<String, List<String>> = mapOf(
            "classic rock" to listOf("rock", "classic rock"),
            "hard rock" to listOf("rock"),
            "soft rock" to listOf("rock", "soft rock"),
            "rock and roll" to listOf("rock and roll", "rock"),
            "rhythm and blues" to listOf("r&b", "soul"),
            "rnb" to listOf("r&b", "soul"),
            "soul" to listOf("soul", "r&b"),
            "funk" to listOf("funk", "soul"),
            "disco" to listOf("disco", "dance"),
            "doo wop" to listOf("doo-wop", "doo wop", "oldies"),
            "doo-wop" to listOf("doo-wop", "doo wop", "oldies"),
            "motown" to listOf("motown", "soul"),
            "oldies" to listOf("oldies"),
            "blues" to listOf("blues"),
            "jazz" to listOf("jazz"),
            "country" to listOf("country"),
            "folk" to listOf("folk"),
            "punk" to listOf("punk"),
            "metal" to listOf("metal"),
            "hip hop" to listOf("hip-hop", "hip hop"),
            "electronic" to listOf("electronic", "dance"),
            "indie" to listOf("indie"),
            "alternative" to listOf("alternative", "indie"),
            "pop" to listOf("pop"),
            "reggae" to listOf("reggae"),
            "grunge" to listOf("grunge", "rock"),
            "new wave" to listOf("new wave"),
            "synthpop" to listOf("synth", "pop")
        )
    }

    private val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val inFlight = Mutex()
    private val memoryCache = mutableMapOf<String, CachedTags>()

    private data class CachedTags(val tags: List<String>, val fetchedAt: Long)

    suspend fun resolve(artistName: String): List<String> {
        val normalizedArtist = artistName.trim()
        if (normalizedArtist.isBlank()) return emptyList()
        val apiKey = configStore.getLastFmApiKey().orEmpty()
        if (apiKey.isBlank()) return emptyList()

        readCache(normalizedArtist)?.let { return it.tags }

        return inFlight.withLock {
            readCache(normalizedArtist)?.let { return@withLock it.tags }
            val tags = fetchFromApi(apiKey, normalizedArtist)
            writeCache(normalizedArtist, tags)
            tags
        }
    }

    /** Returns a lowercase genre string suitable for jukebox station matching. */
    suspend fun resolveGenreString(artistName: String): String {
        return resolve(artistName).joinToString(" ").lowercase()
    }

    suspend fun validateApiKey(apiKey: String): String? = withContext(Dispatchers.IO) {
        apiClient.validateApiKey(apiKey)
    }

    private suspend fun fetchFromApi(apiKey: String, artistName: String): List<String> =
        withContext(Dispatchers.IO) {
            val rawTags = apiClient.artistTopTags(apiKey, artistName)
            val seen = mutableSetOf<String>()
            rawTags
                .filter { (_, count) -> count >= MIN_TAG_COUNT }
                .mapNotNull { (name, _) ->
                    val normalized = normalizeTag(name)
                    if (!seen.add(normalized)) null else normalized
                }
                .take(MAX_TAGS)
                .also { Log.d(TAG, "Resolved $artistName: $it") }
        }

    private fun normalizeTag(tag: String): String =
        tag.lowercase().trim().replace('-', ' ').replace(Regex("\\s+"), " ")

    private fun cacheKey(artist: String): String =
        "tags_${artist.lowercase()}"

    private fun readCache(artist: String): CachedTags? {
        memoryCache[artist]?.let { cached ->
            val ttl = if (cached.tags.isEmpty()) EMPTY_CACHE_MS else CACHE_MS
            if (System.currentTimeMillis() - cached.fetchedAt < ttl) return cached
        }
        val key = cacheKey(artist)
        val fetchedAt = prefs.getLong("${key}_at", 0L)
        if (fetchedAt <= 0L) return null
        val tags = prefs.getString(key, null)
            ?.split(",")
            ?.filter { it.isNotBlank() }
            .orEmpty()
        val ttl = if (tags.isEmpty()) EMPTY_CACHE_MS else CACHE_MS
        if (System.currentTimeMillis() - fetchedAt >= ttl) return null
        return CachedTags(tags, fetchedAt).also { memoryCache[artist] = it }
    }

    private fun writeCache(artist: String, tags: List<String>) {
        val now = System.currentTimeMillis()
        memoryCache[artist] = CachedTags(tags, now)
        val key = cacheKey(artist)
        prefs.edit()
            .putString(key, tags.joinToString(","))
            .putLong("${key}_at", now)
            .apply()
    }
}
