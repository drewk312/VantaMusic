package com.audiophile.musicplayer.discovery.personalized

import android.util.Log
import com.audiophile.musicplayer.ResolverConfigStore
import com.audiophile.musicplayer.data.lastfm.LastFmApiClient
import com.audiophile.musicplayer.data.lastfm.LastFmGenreResolver
import com.audiophile.musicplayer.data.local.PersonalizedMixDao
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

/** Last.fm related-artist + genre cache backed by Room `discovery_artist_cache`. */
class DiscoveryArtistResolver(
    private val dao: PersonalizedMixDao,
    private val configStore: ResolverConfigStore,
    private val genreResolver: LastFmGenreResolver,
    private val apiClient: LastFmApiClient = LastFmApiClient()
) {
    companion object {
        private const val TAG = "DiscoveryArtistResolver"
        private val CACHE_MS = TimeUnit.DAYS.toMillis(30)
        private val gson = Gson()
    }

    private val inFlight = Mutex()

    suspend fun relatedArtists(artistName: String, limit: Int = 8): List<String> =
        withContext(Dispatchers.IO) {
            val key = artistName.trim().lowercase()
            if (key.isBlank()) return@withContext emptyList()
            readCache(key)?.relatedArtists?.take(limit)?.let { return@withContext it }

            inFlight.withLock {
                readCache(key)?.relatedArtists?.take(limit)?.let { return@withLock it }
                val genres = genreResolver.resolve(artistName)
                val related = fetchRelatedFromApi(artistName, limit)
                val entity = com.audiophile.musicplayer.data.local.entities.DiscoveryArtistCacheEntity(
                    artistKey = key,
                    relatedArtistsJson = gson.toJson(related),
                    genresJson = gson.toJson(genres)
                )
                dao.upsertArtistCache(entity)
                related
            }
        }

    suspend fun artistGenres(artistName: String): List<String> {
        val key = artistName.trim().lowercase()
        readCache(key)?.genres?.let { if (it.isNotEmpty()) return it }
        return genreResolver.resolve(artistName)
    }

    private suspend fun readCache(artistKey: String): CachedArtist? {
        val row = dao.getArtistCache(artistKey) ?: return null
        if (System.currentTimeMillis() - row.fetchedAt > CACHE_MS) return null
        return CachedArtist(
            relatedArtists = parseStringList(row.relatedArtistsJson),
            genres = parseStringList(row.genresJson)
        )
    }

    private fun fetchRelatedFromApi(artistName: String, limit: Int): List<String> {
        val apiKey = configStore.getLastFmApiKey().orEmpty()
        if (apiKey.isBlank()) return emptyList()
        return apiClient.artistSimilar(apiKey, artistName, limit)
            .also { Log.d(TAG, "related artist='$artistName' count=${it.size}") }
    }

    private fun parseStringList(json: String): List<String> =
        runCatching {
            gson.fromJson(json, Array<String>::class.java)?.toList().orEmpty()
        }.getOrDefault(emptyList())

    private data class CachedArtist(val relatedArtists: List<String>, val genres: List<String>)
}
