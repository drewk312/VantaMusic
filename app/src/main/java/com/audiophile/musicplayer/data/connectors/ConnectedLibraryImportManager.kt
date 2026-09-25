package com.audiophile.musicplayer.data.connectors

import android.util.Log
import com.audiophile.musicplayer.data.connectors.matching.ConnectedLibraryMatcher
import com.audiophile.musicplayer.data.local.entities.UnifiedTrackWithSources
import com.audiophile.musicplayer.data.source.SourceSearchResult

class ConnectedLibraryImportManager(
    private val clients: Map<ConnectedLibraryProvider, ConnectedLibraryClient>,
    private val matcher: ConnectedLibraryMatcher = ConnectedLibraryMatcher()
) {
    suspend fun importLibrary(
        account: ConnectedLibraryAccount,
        request: ConnectedLibraryImportRequest,
        localCatalog: List<UnifiedTrackWithSources>,
        gatewayResults: suspend (ImportedLibraryTrack) -> List<SourceSearchResult> = { emptyList() },
        existingLinks: List<ProviderTrackLink> = emptyList(),
        nowMs: Long = System.currentTimeMillis()
    ): ConnectedLibraryImportResult {
        val client = clients[account.provider]
            ?: return ConnectedLibraryImportResult(
                importedTracks = emptyList(),
                importedPlaylists = emptyList(),
                providerLinks = emptyList(),
                summary = summary(account.provider, emptyList(), emptyList(), emptyList(), listOf("missing_connector_client"), nowMs)
            )
        val readinessError = importReadinessError(account, client)
        if (readinessError != null) {
            Log.w("VANTA_CONNECTOR_IMPORT_ERROR", "provider=${account.provider} reason=$readinessError")
            return ConnectedLibraryImportResult(
                importedTracks = emptyList(),
                importedPlaylists = emptyList(),
                providerLinks = emptyList(),
                summary = summary(account.provider, emptyList(), emptyList(), emptyList(), listOf(readinessError), nowMs)
            )
        }

        val importedTracks = mutableListOf<ImportedLibraryTrack>()
        val importedPlaylists = mutableListOf<ImportedPlaylist>()
        val links = mutableListOf<ProviderTrackLink>()
        val errors = mutableListOf<String>()
        var cursor: String? = null
        var guard = 0

        do {
            val page = runCatching { client.fetchLibraryPage(account, request, cursor) }
                .onFailure {
                    if (it is kotlinx.coroutines.CancellationException) throw it
                    val reason = it.message ?: it.javaClass.simpleName
                    errors += reason
                    Log.w("VANTA_CONNECTOR_IMPORT_ERROR", "provider=${account.provider} reason=${ConnectedLibraryLogRedactor.redact(reason)}")
                }
                .getOrNull() ?: break

            importedPlaylists += page.playlists
            page.tracks.forEach { imported ->
                val match = matcher.match(imported, localCatalog, gatewayResults(imported), existingLinks + links)
                val matched = imported.copy(
                    matchStatus = match.status,
                    vantaCanonicalTrackId = match.vantaCanonicalTrackId,
                    vantaLocalTrackId = match.vantaLocalTrackId,
                    matchConfidence = match.confidence
                )
                importedTracks += matched
                matcher.linkFor(matched, match, nowMs)?.let { links += it }
                Log.d(
                    "VANTA_CONNECTOR_MATCH",
                    "provider=${matched.provider} title=${matched.title} artist=${matched.artist} " +
                        "isrc=${matched.isrc ?: "null"} result=${match.status} confidence=${match.confidence} reason=${match.reason}"
                )
            }
            cursor = page.nextCursor
            guard += 1
        } while (cursor != null && guard < MAX_IMPORT_PAGES)

        if (cursor != null && guard >= MAX_IMPORT_PAGES) errors += "Library exceeded the import page limit"
        val summary = summary(account.provider, importedTracks, importedPlaylists, links, errors, nowMs)
        Log.i(
            "VANTA_CONNECTOR_IMPORT",
            "provider=${summary.provider} tracks=${summary.tracksImported} playlists=${summary.playlistsImported} " +
                "matched=${summary.matchedToVanta} unmatched=${summary.unmatchedMetadataOnly}"
        )
        return ConnectedLibraryImportResult(importedTracks, importedPlaylists, links, summary)
    }

    private fun summary(
        provider: ConnectedLibraryProvider,
        tracks: List<ImportedLibraryTrack>,
        playlists: List<ImportedPlaylist>,
        links: List<ProviderTrackLink>,
        errors: List<String>,
        nowMs: Long
    ): ConnectedLibraryImportSummary =
        ConnectedLibraryImportSummary(
            provider = provider,
            tracksImported = tracks.size,
            playlistsImported = playlists.size,
            matchedToVanta = links.size,
            unmatchedMetadataOnly = tracks.count { it.vantaCanonicalTrackId == null && it.vantaLocalTrackId == null },
            artworkFound = tracks.count { !it.artworkUrl.isNullOrBlank() } + playlists.count { !it.artworkUrl.isNullOrBlank() },
            errors = errors,
            lastImportAt = nowMs
        )

    private fun importReadinessError(
        account: ConnectedLibraryAccount,
        client: ConnectedLibraryClient
    ): String? {
        if (!account.importEnabled) return "import_disabled"
        if (account.tokenStatus != ConnectedLibraryTokenStatus.VALID) {
            return "token_${account.tokenStatus.name.lowercase()}"
        }
        val missingScopes = client.minimumImportScopes - account.scopesGranted
        if (missingScopes.isNotEmpty()) {
            return "missing_scopes_${missingScopes.sorted().joinToString("_")}"
        }
        return null
    }

    private companion object {
        const val MAX_IMPORT_PAGES = 500
    }
}

data class ConnectedLibraryImportResult(
    val importedTracks: List<ImportedLibraryTrack>,
    val importedPlaylists: List<ImportedPlaylist>,
    val providerLinks: List<ProviderTrackLink>,
    val summary: ConnectedLibraryImportSummary
)
