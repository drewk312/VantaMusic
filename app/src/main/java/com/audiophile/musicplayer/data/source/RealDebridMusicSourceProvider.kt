package com.audiophile.musicplayer.data.source

import android.util.Log
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

class RealDebridMusicSourceProvider(
    private val apiToken: String?
) : MusicSourceProvider {
    override val providerId: String = CloudLibraryHelpers.REALDEBRID_PROVIDER_ID
    override val providerName: String = "Real-Debrid"

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    override suspend fun search(query: String): List<SourceSearchResult> = withContext(Dispatchers.IO) {
        val token = apiToken?.trim().orEmpty()
        if (token.isBlank()) return@withContext emptyList()

        try {
            val torrents = fetchTorrentList(token)
            val queryTokens = query.trim().lowercase().split(Regex("\\s+")).filter { it.isNotBlank() }
            val results = mutableListOf<SourceSearchResult>()

            for (torrent in torrents) {
                if (!torrent.isReady()) continue
                val info = fetchTorrentInfo(token, torrent.id) ?: continue
                val audioFiles = enumerateAudioFiles(info)
                for (file in audioFiles) {
                    val searchable = "${torrent.filename} ${file.path}".lowercase()
                    if (queryTokens.isNotEmpty() && !queryTokens.all { searchable.contains(it) }) continue

                    val fileName = file.path.substringAfterLast('/').ifBlank { file.path }
                    val (artist, title) = CloudLibraryHelpers.parseArtistTitle(fileName, torrent.filename)
                    results.add(
                        SourceSearchResult(
                            id = CloudLibraryHelpers.realDebridTrackId(torrent.id, file.id),
                            providerId = providerId,
                            title = title,
                            artist = artist,
                            album = torrent.filename,
                            coverSeed = title,
                            durationMs = null,
                            isrc = null,
                            status = SearchItemStatus.SOURCE_FOUND,
                            qualityLabel = CloudLibraryHelpers.estimateQualityLabel(fileName, "Real-Debrid")
                        )
                    )
                }
            }

            Log.d("RDSource", "search query='$query' torrents=${torrents.size} results=${results.size}")
            results
        } catch (e: Exception) {
            Log.e("RDSource", "Search exception", e)
            emptyList()
        }
    }

    override suspend fun resolveStream(trackId: String): ResolvedStream? = withContext(Dispatchers.IO) {
        val token = apiToken?.trim().orEmpty()
        if (token.isBlank()) return@withContext null

        try {
            val parsed = parseTrackId(trackId) ?: return@withContext null
            val info = fetchTorrentInfo(token, parsed.torrentId) ?: return@withContext null
            val selectedFiles = selectedFilesInOrder(info)
            val fileIndex = selectedFiles.indexOfFirst { it.id == parsed.fileId }
            if (fileIndex < 0) return@withContext null

            val links = info.getAsJsonArray("links") ?: return@withContext null
            if (fileIndex >= links.size()) return@withContext null
            val hostLink = links[fileIndex].asString

            val streamUrl = unrestrictLink(token, hostLink) ?: return@withContext null
            val fileName = selectedFiles[fileIndex].path.substringAfterLast('/')
            ResolvedStream(
                streamUrl = streamUrl,
                bitrateKbps = CloudLibraryHelpers.estimateBitrateKbps(fileName),
                mimeType = CloudLibraryHelpers.mimeTypeForFileName(fileName),
                expiresAt = System.currentTimeMillis() + (4 * 60 * 60 * 1000L),
                qualityLabel = CloudLibraryHelpers.estimateQualityLabel(fileName, "Real-Debrid")
            )
        } catch (e: Exception) {
            Log.e("RDSource", "Resolve exception trackId=$trackId", e)
            null
        }
    }

    private fun fetchTorrentList(token: String): List<RdTorrentSummary> {
        val request = Request.Builder()
            .url("https://api.real-debrid.com/rest/1.0/torrents?limit=100")
            .header("Authorization", "Bearer $token")
            .build()
        val body = client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                Log.e("RDSource", "List failed: ${response.code}")
                return@use null
            }
            response.body?.string() ?: return@use null
        } ?: return emptyList()
        val json = JsonParser.parseString(body)
        if (!json.isJsonArray) return emptyList()
        return json.asJsonArray.mapNotNull { element ->
            val obj = element.asJsonObject
            val id = obj.get("id")?.asString ?: return@mapNotNull null
            val filename = obj.get("filename")?.asString ?: id
            val status = obj.get("status")?.asString.orEmpty()
            RdTorrentSummary(id = id, filename = filename, status = status)
        }
    }

    private fun fetchTorrentInfo(token: String, torrentId: String): JsonObject? {
        val request = Request.Builder()
            .url("https://api.real-debrid.com/rest/1.0/torrents/info/$torrentId")
            .header("Authorization", "Bearer $token")
            .build()
        val body = client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                Log.e("RDSource", "Info failed torrentId=$torrentId code=${response.code}")
                return@use null
            }
            response.body?.string() ?: return@use null
        } ?: return null
        return JsonParser.parseString(body).asJsonObject
    }

    private fun enumerateAudioFiles(info: JsonObject): List<RdFileEntry> {
        return selectedFilesInOrder(info).filter { file ->
            CloudLibraryHelpers.isAudioFile(file.path.substringAfterLast('/'), null)
        }
    }

    private fun selectedFilesInOrder(info: JsonObject): List<RdFileEntry> {
        val files = info.getAsJsonArray("files") ?: return emptyList()
        return files.mapNotNull { element ->
            val obj = element.asJsonObject
            val id = obj.get("id")?.asInt ?: return@mapNotNull null
            val path = obj.get("path")?.asString ?: return@mapNotNull null
            val selected = obj.get("selected")?.asInt ?: 0
            if (selected != 1) return@mapNotNull null
            RdFileEntry(id = id, path = path)
        }
    }

    private fun unrestrictLink(token: String, link: String): String? {
        val request = Request.Builder()
            .url("https://api.real-debrid.com/rest/1.0/unrestrict/link")
            .header("Authorization", "Bearer $token")
            .post(FormBody.Builder().add("link", link).build())
            .build()
        val body = client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                Log.e("RDSource", "Unrestrict failed: ${response.code}")
                return@use null
            }
            response.body?.string() ?: return@use null
        } ?: return null
        val json = JsonParser.parseString(body).asJsonObject
        return json.get("download")?.asString ?: json.get("link")?.asString
    }

    private fun parseTrackId(trackId: String): RdTrackRef? {
        if (!trackId.startsWith("rd_")) return null
        val remainder = trackId.removePrefix("rd_")
        val lastUnderscore = remainder.lastIndexOf('_')
        if (lastUnderscore <= 0) return null
        val torrentId = remainder.substring(0, lastUnderscore)
        val fileId = remainder.substring(lastUnderscore + 1).toIntOrNull() ?: return null
        return RdTrackRef(torrentId = torrentId, fileId = fileId)
    }

    private data class RdTorrentSummary(
        val id: String,
        val filename: String,
        val status: String
    ) {
        fun isReady(): Boolean {
            val lower = status.lowercase()
            return lower == "downloaded" || lower.contains("100")
        }
    }

    private data class RdFileEntry(val id: Int, val path: String)
    private data class RdTrackRef(val torrentId: String, val fileId: Int)
}
