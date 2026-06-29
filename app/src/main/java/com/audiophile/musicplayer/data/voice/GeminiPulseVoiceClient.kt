package com.audiophile.musicplayer.data.voice

import android.util.Base64
import android.util.Log
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.TimeUnit

/** Direct Gemini generated speech. The user's existing Gemini key stays on-device. */
class GeminiPulseVoiceClient {
    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    suspend fun synthesize(apiKey: String, text: String): ByteArray? = withContext(Dispatchers.IO) {
        runCatching {
            val prompt = "${PulseVoiceProfile.GEMINI_STYLE}\n\nSpeak exactly this DJ line:\n$text"
            val body = JsonObject().apply {
                add("contents", JsonArray().apply {
                    add(JsonObject().apply {
                        add("parts", JsonArray().apply {
                            add(JsonObject().apply { addProperty("text", prompt) })
                        })
                    })
                })
                add("generationConfig", JsonObject().apply {
                    add("responseModalities", JsonArray().apply { add("AUDIO") })
                    add("speechConfig", JsonObject().apply {
                        add("voiceConfig", JsonObject().apply {
                            add("prebuiltVoiceConfig", JsonObject().apply {
                                addProperty("voiceName", "Charon")
                            })
                        })
                    })
                })
            }
            val request = Request.Builder()
                .url("https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash-preview-tts:generateContent?key=$apiKey")
                .post(body.toString().toRequestBody("application/json".toMediaType()))
                .build()
            client.newCall(request).execute().use { response ->
                val responseText = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    Log.w("VANTA_PULSE_VOICE", "Gemini speech failed code=${response.code}")
                    return@use null
                }
                val part = JsonParser.parseString(responseText).asJsonObject
                    .getAsJsonArray("candidates")?.firstOrNull()?.asJsonObject
                    ?.getAsJsonObject("content")?.getAsJsonArray("parts")
                    ?.firstOrNull()?.asJsonObject?.getAsJsonObject("inlineData")
                    ?: return@use null
                val mime = part.get("mimeType")?.asString.orEmpty()
                val audio = Base64.decode(part.get("data")?.asString ?: return@use null, Base64.DEFAULT)
                if (mime.contains("wav", ignoreCase = true)) audio
                else pcm16ToWav(audio, Regex("rate=(\\d+)").find(mime)?.groupValues?.getOrNull(1)?.toIntOrNull() ?: 24_000)
            }
        }.onFailure {
            Log.w("VANTA_PULSE_VOICE", "Gemini speech request failed: ${it.message}")
        }.getOrNull()
    }

    private fun pcm16ToWav(pcm: ByteArray, sampleRate: Int): ByteArray {
        val out = ByteArrayOutputStream(pcm.size + 44)
        fun text(value: String) = out.write(value.toByteArray(Charsets.US_ASCII))
        fun int32(value: Int) = out.write(ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(value).array())
        fun int16(value: Int) = out.write(ByteBuffer.allocate(2).order(ByteOrder.LITTLE_ENDIAN).putShort(value.toShort()).array())
        text("RIFF"); int32(36 + pcm.size); text("WAVE")
        text("fmt "); int32(16); int16(1); int16(1); int32(sampleRate)
        int32(sampleRate * 2); int16(2); int16(16)
        text("data"); int32(pcm.size); out.write(pcm)
        return out.toByteArray()
    }
}
