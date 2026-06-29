package com.audiophile.musicplayer.data.remote

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class RealDebridRepository(private val api: RealDebridApi) {

    suspend fun isHashCached(
        bearerToken: String,
        infoHash: String
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            val response = api.checkInstantAvailability("Bearer $bearerToken", infoHash)
            response.isJsonObject && response.asJsonObject.entrySet().isNotEmpty()
        } catch (_: Exception) {
            false
        }
    }

    suspend fun addMagnet(
        bearerToken: String,
        magnet: String
    ): RealDebridAddMagnetResponse? = withContext(Dispatchers.IO) {
        try {
            api.addMagnet("Bearer $bearerToken", magnet)
        } catch (_: Exception) {
            null
        }
    }

    suspend fun getStreamUrl(
        bearerToken: String,
        link: String
    ): String? = withContext(Dispatchers.IO) {
        try {
            val response = api.unrestrictLink("Bearer $bearerToken", link)
            response.download ?: response.link
        } catch (_: Exception) {
            null
        }
    }
}
