package com.audiophile.musicplayer.data.source

import android.util.Log
import com.audiophile.musicplayer.data.remote.TorBoxRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class TorBoxMusicSourceProvider(
    private val apiToken: String?,
    private val torBoxRepository: TorBoxRepository
) : MusicSourceProvider {
    override val providerId: String = CloudLibraryHelpers.TORBOX_PROVIDER_ID
    override val providerName: String = "TorBox Library"

    override suspend fun search(query: String): List<SourceSearchResult> = withContext(Dispatchers.IO) {
        val token = apiToken?.trim().orEmpty()
        if (token.isBlank()) return@withContext emptyList()

        try {
            val files = torBoxRepository.importableAudioFiles(token, limit = 250)
            val queryLower = query.trim().lowercase()
            val queryTokens = queryLower.split(Regex("\\s+")).filter { it.isNotBlank() }

            val results = files.mapNotNull { candidate ->
                val searchable = "${candidate.torrentName} ${candidate.file.name}".lowercase()
                if (queryTokens.isNotEmpty() && !queryTokens.all { searchable.contains(it) }) {
                    return@mapNotNull null
                }

                val (artist, title) = CloudLibraryHelpers.parseArtistTitle(
                    candidate.file.name,
                    candidate.torrentName
                )
                SourceSearchResult(
                    id = CloudLibraryHelpers.torBoxTrackId(candidate.torrentId, candidate.file.fileId),
                    providerId = providerId,
                    title = title,
                    artist = artist,
                    album = candidate.torrentName,
                    coverSeed = title,
                    durationMs = null,
                    isrc = null,
                    status = SearchItemStatus.SOURCE_FOUND,
                    qualityLabel = CloudLibraryHelpers.estimateQualityLabel(candidate.file.name, "TorBox")
                )
            }

            Log.d("TorBoxSource", "search query='$query' files=${files.size} results=${results.size}")
            results
        } catch (e: Exception) {
            Log.e("TorBoxSource", "Search exception", e)
            emptyList()
        }
    }

    override suspend fun resolveStream(trackId: String): ResolvedStream? = withContext(Dispatchers.IO) {
        val token = apiToken?.trim().orEmpty()
        if (token.isBlank()) return@withContext null

        try {
            val parts = trackId.split("_", limit = 2)
            if (parts.size < 2) return@withContext null
            val torrentId = parts[0].toLongOrNull() ?: return@withContext null
            val fileId = parts[1].toIntOrNull() ?: return@withContext null

            val streamUrl = torBoxRepository.getDirectStreamUrlForTorrent(token, torrentId, fileId)
                ?: return@withContext null

            val fileName = torBoxRepository.importableAudioFiles(token, limit = 250)
                .firstOrNull { it.torrentId == torrentId && it.file.fileId == fileId }
                ?.file
                ?.name
                .orEmpty()

            ResolvedStream(
                streamUrl = streamUrl,
                bitrateKbps = CloudLibraryHelpers.estimateBitrateKbps(fileName),
                mimeType = CloudLibraryHelpers.mimeTypeForFileName(fileName),
                expiresAt = System.currentTimeMillis() + CloudLibraryHelpers.TORBOX_LINK_TTL_MS,
                qualityLabel = "TorBox"
            )
        } catch (e: Exception) {
            Log.e("TorBoxSource", "Resolve exception trackId=$trackId", e)
            null
        }
    }
}
