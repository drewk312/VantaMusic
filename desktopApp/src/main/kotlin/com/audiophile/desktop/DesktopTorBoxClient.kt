package com.audiophile.desktop

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.audiophile.musicplayer.data.resolution.MetadataNormalizer
import java.net.URI
import java.net.URLEncoder
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.charset.StandardCharsets

class DesktopTorBoxClient(
    private val baseUrl: String = "https://api.torbox.app/v1/"
) {
    private val httpClient = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NORMAL).build()

    fun fetchAudioCandidates(token: String, limit: Int = 100): List<TorBoxCandidate> {
        val payload = getJson(
            path = "api/torrents/mylist?bypass_cache=false&offset=0&limit=$limit",
            token = token
        ) ?: return emptyList()

        if (!payload.readBoolean("success")) return emptyList()

        val torrents = extractTorrentArray(payload.get("data"))
        val candidates = mutableListOf<TorBoxCandidate>()

        torrents.forEach { torrent ->
            val torrentId = torrent.readLong("id") ?: torrent.readLong("torrent_id") ?: return@forEach
            val torrentName = torrent.readString("name")
                ?: torrent.readString("torrent_name")
                ?: "TorBox Item $torrentId"
            val infoHash = torrent.readString("hash") ?: torrent.readString("info_hash")
            extractFiles(torrent)
                .filter { file -> isAudioFile(file.name, file.mimeType) }
                .forEach { file ->
                    val directUrl = file.downloadUrl ?: getDownloadUrl(token, torrentId, infoHash, file.fileId)
                    candidates += TorBoxCandidate(
                        fileName = file.name,
                        torrentName = torrentName,
                        quality = guessQuality(file.name),
                        bitrateKbps = estimateBitrateFromName(file.name),
                        streamHint = directUrl.orEmpty(),
                        torrentId = torrentId,
                        fileId = file.fileId,
                        infoHash = infoHash
                    )
                }
        }

        return candidates
    }

    private fun getDownloadUrl(token: String, torrentId: Long, infoHash: String?, fileId: Int): String? {
        val queryParts = mutableListOf("file_id=$fileId")
        if (torrentId > 0) {
            queryParts += "torrent_id=$torrentId"
        } else if (!infoHash.isNullOrBlank()) {
            queryParts += "hash=${infoHash.urlEncode()}"
        }

        val payload = getJson(
            path = "api/torrents/requestdl?${queryParts.joinToString("&")}",
            token = token
        ) ?: return null

        if (!payload.readBoolean("success")) return null
        return payload.readString("data")
    }

    private fun getJson(path: String, token: String): JsonObject? {
        val request = HttpRequest.newBuilder()
            .uri(URI.create(resolveUrl(path)))
            .header("Authorization", "Bearer $token")
            .header("Accept", "application/json")
            .GET()
            .build()

        val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())
        if (response.statusCode() !in 200..299) return null
        return JsonParser.parseString(response.body()).takeIf { it.isJsonObject }?.asJsonObject
    }

    private fun resolveUrl(path: String): String {
        val normalizedBase = if (baseUrl.endsWith("/")) baseUrl else "$baseUrl/"
        return if (path.startsWith("/")) normalizedBase.dropLast(1) + path else normalizedBase + path
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

    private fun extractFiles(obj: JsonObject): List<TorBoxFileSummaryLite> {
        val fileArrays = listOfNotNull(
            obj.get("files")?.takeIf { it.isJsonArray }?.asJsonArray,
            obj.get("downloads")?.takeIf { it.isJsonArray }?.asJsonArray
        )
        return fileArrays
            .flatMap { it.toJsonObjects() }
            .mapNotNull { parseFileSummary(it) }
    }

    private fun parseFileSummary(obj: JsonObject): TorBoxFileSummaryLite? {
        val fileId = obj.readInt("id")
            ?: obj.readInt("file_id")
            ?: return null
        val name = obj.readString("name")
            ?: obj.readString("short_name")
            ?: return null
        return TorBoxFileSummaryLite(
            fileId = fileId,
            name = name,
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

    private fun guessQuality(name: String): String = when (name.substringAfterLast('.', "").lowercase()) {
        "flac", "wav", "alac" -> "LOSSLESS"
        "m4a", "aac" -> "AAC"
        "mp3", "ogg", "opus" -> "LOSSY"
        else -> "AUDIO"
    }

    private fun estimateBitrateFromName(name: String): Int = when (name.substringAfterLast('.', "").lowercase()) {
        "flac", "wav", "alac" -> 1411
        "m4a", "aac" -> 256
        "mp3", "ogg", "opus" -> 320
        else -> 256
    }
}

private data class TorBoxFileSummaryLite(
    val fileId: Int,
    val name: String,
    val mimeType: String?,
    val downloadUrl: String?
)

private fun JsonArray.toJsonObjects(): List<JsonObject> = mapNotNull { element ->
    element.takeIf { it.isJsonObject }?.asJsonObject
}

private fun JsonObject.readString(key: String): String? = get(key)?.takeIf { !it.isJsonNull }?.asString

private fun JsonObject.readInt(key: String): Int? = get(key)?.takeIf { !it.isJsonNull }?.asInt

private fun JsonObject.readLong(key: String): Long? = get(key)?.takeIf { !it.isJsonNull }?.asLong

private fun JsonObject.readBoolean(key: String): Boolean = get(key)?.takeIf { !it.isJsonNull }?.asBoolean ?: false

private fun String.urlEncode(): String = URLEncoder.encode(this, StandardCharsets.UTF_8)

