package com.audiophile.musicplayer.data.remote

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class TorBoxRepository(private val api: TorBoxApi) {

    suspend fun getDirectStreamUrl(
        bearerToken: String,
        infoHash: String,
        fileId: Int
    ): String? = withContext(Dispatchers.IO) {
        try {
            val cacheCheck = api.checkCache("Bearer $bearerToken", infoHash)
            if (!cacheCheck.success) return@withContext null

            val streamRequest = api.getDownloadLink(
                bearerToken = "Bearer $bearerToken",
                infoHash = infoHash,
                fileId = fileId
            )
            if (streamRequest.success) streamRequest.data else null
        } catch (_: Exception) {
            null
        }
    }

    suspend fun getDirectStreamUrlForTorrent(
        bearerToken: String,
        torrentId: Long,
        fileId: Int
    ): String? = withContext(Dispatchers.IO) {
        try {
            val streamRequest = api.getDownloadLink(
                bearerToken = "Bearer $bearerToken",
                torrentId = torrentId,
                fileId = fileId
            )
            if (streamRequest.success) streamRequest.data else null
        } catch (_: Exception) {
            null
        }
    }

    suspend fun getAudioTorrents(
        bearerToken: String,
        limit: Int = 100
    ): List<TorBoxTorrentSummary> = withContext(Dispatchers.IO) {
        try {
            val listResponse = api.getMyList("Bearer $bearerToken", limit = limit)
            if (!listResponse.success) return@withContext emptyList()

            extractTorrentArray(listResponse.data)
                .mapNotNull { parseTorrentSummary(it) }
                .filter { torrent ->
                    torrent.files.any { file -> isAudioFile(file.name, file.mimeType) }
                }
        } catch (_: Exception) {
            emptyList()
        }
    }

    suspend fun importableAudioFiles(
        bearerToken: String,
        limit: Int = 100
    ): List<TorBoxImportCandidate> = withContext(Dispatchers.IO) {
        getAudioTorrents(bearerToken, limit)
            .flatMap { torrent ->
                torrent.files
                    .filter { file -> isAudioFile(file.name, file.mimeType) }
                    .map { file ->
                        TorBoxImportCandidate(
                            torrentId = torrent.torrentId,
                            torrentName = torrent.name,
                            infoHash = torrent.infoHash,
                            status = torrent.status,
                            file = file
                        )
                    }
            }
    }

    private fun extractTorrentArray(data: JsonElement?): List<JsonObject> {
        if (data == null || data.isJsonNull) return emptyList()
        return when {
            data.isJsonArray -> data.asJsonArray.toJsonObjects()
            data.isJsonObject -> {
                val obj = data.asJsonObject
                when {
                    obj.get("torrents")?.isJsonArray == true -> obj.getAsJsonArray("torrents").toJsonObjects()
                    obj.get("data")?.isJsonArray == true -> obj.getAsJsonArray("data").toJsonObjects()
                    else -> emptyList()
                }
            }
            else -> emptyList()
        }
    }

    private fun parseTorrentSummary(obj: JsonObject): TorBoxTorrentSummary? {
        val torrentId = obj.readLong("id")
            ?: obj.readLong("torrent_id")
            ?: return null
        val name = obj.readString("name")
            ?: obj.readString("torrent_name")
            ?: "TorBox Item $torrentId"
        val files = extractFiles(obj)
        return TorBoxTorrentSummary(
            torrentId = torrentId,
            name = name,
            infoHash = obj.readString("hash") ?: obj.readString("info_hash"),
            status = obj.readString("status"),
            files = files
        )
    }

    private fun extractFiles(obj: JsonObject): List<TorBoxFileSummary> {
        val fileArrays = listOfNotNull(
            obj.get("files")?.takeIf { it.isJsonArray }?.asJsonArray,
            obj.get("downloads")?.takeIf { it.isJsonArray }?.asJsonArray
        )
        return fileArrays
            .flatMap { it.toJsonObjects() }
            .mapNotNull { parseFileSummary(it) }
    }

    private fun parseFileSummary(obj: JsonObject): TorBoxFileSummary? {
        val fileId = obj.readInt("id")
            ?: obj.readInt("file_id")
            ?: return null
        val name = obj.readString("name")
            ?: obj.readString("short_name")
            ?: return null
        return TorBoxFileSummary(
            fileId = fileId,
            name = name,
            sizeBytes = obj.readLong("size") ?: obj.readLong("bytes"),
            mimeType = obj.readString("mimetype") ?: obj.readString("mime_type"),
            downloadUrl = obj.readString("download_url") ?: obj.readString("url")
        )
    }

    private fun isAudioFile(name: String, mimeType: String?): Boolean {
        val lowerName = name.lowercase()
        if (mimeType?.startsWith("audio/") == true) return true
        return lowerName.endsWith(".flac") ||
            lowerName.endsWith(".wav") ||
            lowerName.endsWith(".alac") ||
            lowerName.endsWith(".m4a") ||
            lowerName.endsWith(".mp3") ||
            lowerName.endsWith(".ogg") ||
            lowerName.endsWith(".opus")
    }
}

data class TorBoxImportCandidate(
    val torrentId: Long,
    val torrentName: String,
    val infoHash: String?,
    val status: String?,
    val file: TorBoxFileSummary
)

private fun JsonArray.toJsonObjects(): List<JsonObject> {
    return mapNotNull { element -> element.takeIf { it.isJsonObject }?.asJsonObject }
}

private fun JsonObject.readString(key: String): String? {
    return get(key)?.takeIf { !it.isJsonNull }?.asString
}

private fun JsonObject.readLong(key: String): Long? {
    return get(key)?.takeIf { !it.isJsonNull }?.asLong
}

private fun JsonObject.readInt(key: String): Int? {
    return get(key)?.takeIf { !it.isJsonNull }?.asInt
}
