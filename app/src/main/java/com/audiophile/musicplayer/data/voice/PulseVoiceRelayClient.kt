package com.audiophile.musicplayer.data.voice

import android.util.Log
import com.google.gson.JsonObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

class PulseVoiceRelayClient {
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(90, TimeUnit.SECONDS)
        .build()

    suspend fun synthesize(
        relayBaseUrl: String,
        relayToken: String?,
        text: String,
        engine: String,
        voiceDescription: String,
        openAiInstructions: String
    ): ByteArray? = withContext(Dispatchers.IO) {
        try {
            val base = relayBaseUrl.trim().removeSuffix("/")
            val url = if (base.endsWith("/v1/pulse/speak")) base else "$base/v1/pulse/speak"
            val body = JsonObject().apply {
                addProperty("text", text)
                addProperty("engine", engine)
                addProperty("voice_description", voiceDescription)
                addProperty("openai_instructions", openAiInstructions)
                addProperty("output_format", "mp3")
            }
            val builder = Request.Builder()
                .url(url)
                .post(body.toString().toRequestBody("application/json".toMediaType()))
            if (!relayToken.isNullOrBlank()) {
                builder.addHeader("Authorization", "Bearer ${relayToken.trim()}")
            }
            val bytes = client.newCall(builder.build()).execute().use { response ->
                val bodyBytes = response.body?.bytes()
                if (!response.isSuccessful) {
                    Log.e("VANTA_PULSE_VOICE", "Relay error ${response.code}: ${bodyBytes?.decodeToString()?.take(200)}")
                    return@use null
                }
                if (bodyBytes == null || bodyBytes.isEmpty()) {
                    Log.e("VANTA_PULSE_VOICE", "Relay returned empty audio")
                    return@use null
                }
                bodyBytes
            } ?: return@withContext null
            bytes
        } catch (e: java.io.IOException) {
            Log.e("VANTA_PULSE_VOICE", "Relay request failed", e)
            null
        }
    }
}
