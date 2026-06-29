package com.audiophile.musicplayer.data.connectors

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.core.content.edit
import com.audiophile.musicplayer.data.connectors.apple.AppleMusicLibraryConnector
import com.audiophile.musicplayer.data.connectors.matching.ConnectedLibraryMatcher
import com.audiophile.musicplayer.data.connectors.spotify.SpotifyLibraryApiClient
import com.audiophile.musicplayer.data.connectors.sync.ConnectedLibraryLikeSyncManager
import com.audiophile.musicplayer.data.connectors.sync.VantaLikedTrackIdentity
import com.audiophile.musicplayer.data.local.entities.LocalSongEntity
import com.audiophile.musicplayer.data.local.entities.SourceType
import com.audiophile.musicplayer.data.metadata.apple.AppleMusicConfig
import com.audiophile.musicplayer.data.metadata.apple.AppleMusicMetadataProvider
import com.audiophile.musicplayer.data.repository.LocalLibraryRepository
import com.audiophile.musicplayer.data.repository.TrackRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/**
 * Orchestrates connected-library import for Apple Music and Spotify.
 *
 * Reads encrypted tokens from [ConnectedLibraryTokenStore], fetches metadata-only
 * library pages, matches tracks against the local VANTA catalog, and persists
 * imported tracks to the local library.
 */
class ConnectedLibraryManager(
    private val context: Context,
    private val tokenStore: ConnectedLibraryTokenStore,
    private val trackRepository: TrackRepository,
    private val localLibraryRepository: LocalLibraryRepository,
    private val matcher: ConnectedLibraryMatcher = ConnectedLibraryMatcher(),
    private val prefs: SharedPreferences = context.getSharedPreferences("vanta_connected_libraries", Context.MODE_PRIVATE)
) {

    /** Import the connected library for the given provider and persist tracks. */
    suspend fun importLibrary(provider: ConnectedLibraryProvider): ConnectedLibraryImportResult =
        withContext(Dispatchers.IO) {
            val account = buildAccount(provider)
            val localCatalog = trackRepository.getAllTracks()
            val importManager = createImportManager(provider)
            val result = importManager.importLibrary(
                account = account,
                request = ConnectedLibraryImportRequest(provider = provider),
                localCatalog = localCatalog,
                gatewayResults = { emptyList() }
            )
            if (result.summary.errors.isEmpty()) {
                persistImport(provider)
                val saved = persistImportedTracks(provider, result.importedTracks, result.providerLinks)
                Log.i(
                    "VANTA_CONNECTOR_PERSIST",
                    "provider=$provider saved=${saved.size} tracks"
                )
            }
            result
        }

    /** Sync a VANTA like to all connected providers that have like-sync enabled. */
    suspend fun syncLike(localTrackId: Long, title: String, artist: String, isrc: String?) {
        withContext(Dispatchers.IO) {
            val accounts = ConnectedLibraryProvider.entries
                .filter { isConnected(it) && isSyncLikesEnabled(it) }
                .map { buildAccount(it) }
            if (accounts.isEmpty()) return@withContext

            val links = loadProviderLinks()
            val clients = accounts.associate { it.provider to createClient(it.provider) }
            val syncManager = ConnectedLibraryLikeSyncManager(clients)
            val identity = VantaLikedTrackIdentity(
                vantaTrackId = localTrackId,
                title = title,
                artist = artist,
                isrc = isrc
            )
            val actions = syncManager.enqueueLikeSync(identity, accounts, links)
            actions.forEach { action ->
                val account = accounts.first { it.provider == action.provider }
                val result = syncManager.runQueuedAction(account, action)
                Log.i(
                    "VANTA_LIKE_SYNC",
                    "provider=${result.provider} track=${result.vantaTrackId} status=${result.status}"
                )
            }
        }
    }

    /** Toggle like sync for a provider. */
    fun setSyncLikes(provider: ConnectedLibraryProvider, enabled: Boolean) {
        prefs.edit { putBoolean("${provider.name.lowercase()}_sync_likes", enabled) }
    }

    /** Whether like sync is enabled for a provider. */
    fun isSyncLikesEnabled(provider: ConnectedLibraryProvider): Boolean =
        prefs.getBoolean("${provider.name.lowercase()}_sync_likes", false)

    /** Clear all imported data and tokens for a provider. */
    fun disconnect(provider: ConnectedLibraryProvider) {
        tokenStore.clear(provider)
        prefs.edit {
            remove("${provider.name.lowercase()}_connected")
            remove("${provider.name.lowercase()}_last_import")
            remove("${provider.name.lowercase()}_sync_likes")
            remove(providerLinksKey(provider))
        }
    }

    /** True if the provider has stored tokens. */
    fun isConnected(provider: ConnectedLibraryProvider): Boolean {
        return when (provider) {
            ConnectedLibraryProvider.APPLE_MUSIC -> {
                tokenStore.accessToken(provider)?.isNotBlank() == true &&
                    tokenStore.musicUserToken(provider)?.isNotBlank() == true
            }
            ConnectedLibraryProvider.SPOTIFY -> {
                tokenStore.accessToken(provider)?.isNotBlank() == true
            }
        }
    }

    private fun buildAccount(provider: ConnectedLibraryProvider): ConnectedLibraryAccount {
        val now = System.currentTimeMillis()
        val scopes = when (provider) {
            ConnectedLibraryProvider.SPOTIFY -> setOf("user-library-read", "playlist-read-private", "playlist-read-collaborative")
            ConnectedLibraryProvider.APPLE_MUSIC -> {
                val dev = tokenStore.accessToken(provider) ?: ""
                val user = tokenStore.musicUserToken(provider) ?: ""
                setOf("dev:$dev", "user:$user")
            }
        }
        return ConnectedLibraryAccount(
            id = "${provider.name.lowercase()}_local_account",
            provider = provider,
            displayName = provider.displayName(),
            accountId = "me",
            connectedAt = now,
            tokenStatus = ConnectedLibraryTokenStatus.VALID,
            scopesGranted = scopes,
            importEnabled = true,
            syncLikesEnabled = isSyncLikesEnabled(provider)
        )
    }

    private fun createClient(provider: ConnectedLibraryProvider): com.audiophile.musicplayer.data.connectors.ConnectedLibraryClient {
        return when (provider) {
            ConnectedLibraryProvider.SPOTIFY -> {
                val accessToken = tokenStore.accessToken(provider)
                    ?: throw IllegalStateException("Missing Spotify access token")
                com.audiophile.musicplayer.data.connectors.spotify.SpotifyLibraryConnector(
                    api = SpotifyLibraryApiClient(accessToken)
                )
            }
            ConnectedLibraryProvider.APPLE_MUSIC -> {
                val devToken = tokenStore.accessToken(provider)
                    ?: throw IllegalStateException("Missing Apple Music developer token")
                AppleMusicLibraryConnector(
                    metadataProvider = AppleMusicMetadataProvider(
                        configProvider = { AppleMusicConfig(developerToken = devToken) }
                    )
                )
            }
        }
    }

    private fun createImportManager(provider: ConnectedLibraryProvider): ConnectedLibraryImportManager {
        return ConnectedLibraryImportManager(
            clients = mapOf(provider to createClient(provider)),
            matcher = matcher
        )
    }

    private suspend fun persistImportedTracks(
        provider: ConnectedLibraryProvider,
        importedTracks: List<ImportedLibraryTrack>,
        providerLinks: List<ProviderTrackLink>
    ): List<Long> {
        val sourceType = when (provider) {
            ConnectedLibraryProvider.SPOTIFY -> SourceType.SPOTIFY
            ConnectedLibraryProvider.APPLE_MUSIC -> SourceType.APPLE_MUSIC
        }
        val songs = mutableListOf<LocalSongEntity>()
        val now = System.currentTimeMillis()

        importedTracks.forEach { track ->
            val existing = localLibraryRepository.findSongByTitleArtist(track.title, track.artist)
            if (existing != null) {
                return@forEach
            }
            val externalIds = mutableMapOf<String, String>()
            track.isrc?.let { externalIds["isrc"] = it }
            externalIds["${provider.name.lowercase()}Id"] = track.providerTrackId
            val song = LocalSongEntity(
                title = track.title,
                artist = track.artist,
                album = track.album,
                durationMs = track.durationMs,
                artworkUrl = track.artworkUrl,
                isrc = track.isrc,
                explicit = track.explicit,
                sourceType = sourceType,
                importSource = provider.displayName(),
                externalIdsJson = JSONObject(externalIds).toString(),
                dateAdded = track.addedAt ?: now,
                createdAt = now,
                updatedAt = now
            )
            songs += song
        }

        val savedIds = if (songs.isNotEmpty()) {
            localLibraryRepository.saveSongs(songs)
        } else {
            emptyList()
        }

        persistProviderLinks(providerLinks + buildFallbackLinks(provider, savedIds, importedTracks))
        return savedIds
    }

    private fun buildFallbackLinks(
        provider: ConnectedLibraryProvider,
        savedIds: List<Long>,
        importedTracks: List<ImportedLibraryTrack>
    ): List<ProviderTrackLink> {
        return savedIds.mapNotNull { id ->
            val track = importedTracks.getOrNull(savedIds.indexOf(id)) ?: return@mapNotNull null
            ProviderTrackLink(
                id = "${provider.name}:$id:${System.currentTimeMillis()}",
                provider = provider,
                providerTrackId = track.providerTrackId,
                isrc = track.isrc,
                vantaLocalTrackId = id,
                matchConfidence = 1.0f,
                linkedAt = System.currentTimeMillis()
            )
        }
    }

    private fun persistImport(provider: ConnectedLibraryProvider) {
        val now = android.text.format.DateFormat.format("MMM d, h:mm a", System.currentTimeMillis()).toString()
        prefs.edit {
            putString("${provider.name.lowercase()}_last_import", now)
            putBoolean("${provider.name.lowercase()}_connected", true)
        }
    }

    private fun persistProviderLinks(links: List<ProviderTrackLink>) {
        if (links.isEmpty()) return
        val json = JSONArray()
        links.forEach { link ->
            json.put(
                JSONObject().apply {
                    put("provider", link.provider.name)
                    put("providerTrackId", link.providerTrackId)
                    put("vantaLocalTrackId", link.vantaLocalTrackId ?: -1L)
                    put("isrc", link.isrc ?: JSONObject.NULL)
                    put("matchConfidence", link.matchConfidence.toDouble())
                    put("linkedAt", link.linkedAt)
                }
            )
        }
        prefs.edit { putString(PROVIDER_LINKS_KEY, json.toString()) }
    }

    private fun loadProviderLinks(): List<ProviderTrackLink> {
        val raw = prefs.getString(PROVIDER_LINKS_KEY, null) ?: return emptyList()
        return runCatching {
            val json = JSONArray(raw)
            List(json.length()) { index ->
                val obj = json.getJSONObject(index)
                ProviderTrackLink(
                    id = "${obj.getString("provider")}:${obj.getString("providerTrackId")}:${obj.getLong("linkedAt")}",
                    provider = ConnectedLibraryProvider.valueOf(obj.getString("provider")),
                    providerTrackId = obj.getString("providerTrackId"),
                    isrc = obj.takeIf { it.has("isrc") && !it.isNull("isrc") }?.getString("isrc"),
                    vantaLocalTrackId = obj.getLong("vantaLocalTrackId").takeIf { it >= 0 },
                    matchConfidence = obj.getDouble("matchConfidence").toFloat(),
                    linkedAt = obj.getLong("linkedAt")
                )
            }
        }.getOrDefault(emptyList())
    }

    private fun providerLinksKey(provider: ConnectedLibraryProvider): String =
        "${provider.name.lowercase()}_provider_links"

    companion object {
        private const val PROVIDER_LINKS_KEY = "vanta_provider_links"

        private fun ConnectedLibraryProvider.displayName(): String = when (this) {
            ConnectedLibraryProvider.APPLE_MUSIC -> "Apple Music"
            ConnectedLibraryProvider.SPOTIFY -> "Spotify"
        }
    }
}
