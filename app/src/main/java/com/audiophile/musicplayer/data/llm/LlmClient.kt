package com.audiophile.musicplayer.data.llm

import android.util.Log
import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

interface LlmClient {
    suspend fun chat(systemPrompt: String, userMessage: String): String?
}

suspend fun LlmClient.getTrackSuggestions(userPrompt: String): List<Pair<String, String>> {
    val systemPrompt = """You are VANTA AI DJ, a real music discovery engine. Given a request, suggest 5-10 specific, real songs with their original artists. Only suggest actual commercially released songs — no radio streams, no live recordings, no compilation tracks, no karaoke versions. Return ONLY one line per song in the format: Song Title - Artist Name. No markdown, no numbering, no extra text. Predefine specific, well-known original songs."""
    val response = chat(systemPrompt, userPrompt) ?: return emptyList()
    return response.lines().mapNotNull { line ->
        val cleaned = line.trim().removePrefix("- ").removePrefix("* ")
        val parts = cleaned.split(" - ", limit = 2)
        if (parts.size == 2) parts[0].trim() to parts[1].trim() else null
    }
}

class GeminiLlmClient(
    private val apiKey: String,
    private val model: String = "gemini-2.5-flash"
) : LlmClient {

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()
    private val gson = Gson()

    override suspend fun chat(systemPrompt: String, userMessage: String): String? = withContext(Dispatchers.IO) {
        try {
            val url = "https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent?key=$apiKey"
            val body = JsonObject().apply {
                add("system_instruction", JsonObject().apply {
                    add("parts", JsonArray().apply {
                        add(JsonObject().apply { addProperty("text", systemPrompt) })
                    })
                })
                add("contents", JsonArray().apply {
                    add(JsonObject().apply {
                        addProperty("role", "user")
                        add("parts", JsonArray().apply {
                            add(JsonObject().apply { addProperty("text", userMessage) })
                        })
                    })
                })
                add("generationConfig", gson.toJsonTree(
                    mapOf("temperature" to 0.7, "maxOutputTokens" to 1024)
                ).asJsonObject)
            }
            val request = Request.Builder()
                .url(url)
                .post(body.toString().toRequestBody("application/json".toMediaType()))
                .build()
            val bodyString = client.newCall(request).execute().use { response ->
                val b = response.body?.string() ?: return@use null
                if (!response.isSuccessful) {
                    Log.e("GeminiLlmClient", "API error ${response.code}: $b")
                    return@use null
                }
                b
            } ?: return@withContext null
            val json = gson.fromJson(bodyString, JsonObject::class.java)
            json?.asJsonObject?.get("candidates")?.asJsonArray?.firstOrNull()
                ?.asJsonObject?.get("content")?.asJsonObject?.get("parts")?.asJsonArray?.firstOrNull()
                ?.asJsonObject?.get("text")?.asString
        } catch (e: Exception) {
            Log.e("GeminiLlmClient", "Request failed", e)
            null
        }
    }
}

class OpenaiCompatibleLlmClient(
    private val apiKey: String,
    private val baseUrl: String,
    private val model: String
) : LlmClient {

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()
    private val gson = Gson()

    override suspend fun chat(systemPrompt: String, userMessage: String): String? = withContext(Dispatchers.IO) {
        try {
            val url = "${baseUrl.trimEnd('/')}/chat/completions"
            val body = JsonObject().apply {
                addProperty("model", model)
                add("messages", JsonArray().apply {
                    add(JsonObject().apply {
                        addProperty("role", "system")
                        addProperty("content", systemPrompt)
                    })
                    add(JsonObject().apply {
                        addProperty("role", "user")
                        addProperty("content", userMessage)
                    })
                })
                addProperty("temperature", 0.7)
                addProperty("max_tokens", 1024)
            }
            val request = Request.Builder()
                .url(url)
                .addHeader("Authorization", "Bearer $apiKey")
                .post(body.toString().toRequestBody("application/json".toMediaType()))
                .build()
            val bodyString = client.newCall(request).execute().use { response ->
                val b = response.body?.string() ?: return@use null
                if (!response.isSuccessful) {
                    Log.e("OpenaiLlmClient", "API error ${response.code}: $b")
                    return@use null
                }
                b
            } ?: return@withContext null
            val json = gson.fromJson(bodyString, JsonObject::class.java)
            json?.asJsonObject?.get("choices")?.asJsonArray?.firstOrNull()
                ?.asJsonObject?.get("message")?.asJsonObject?.get("content")?.asString
        } catch (e: Exception) {
            Log.e("OpenaiLlmClient", "Request failed", e)
            null
        }
    }
}

fun createLlmClient(provider: AiProvider, apiKey: String): LlmClient? {
    if (apiKey.isBlank()) return null
    return when (provider) {
        AiProvider.GEMINI -> GeminiLlmClient(apiKey, provider.defaultModel)
        AiProvider.CEREBRAS -> OpenaiCompatibleLlmClient(apiKey, "https://api.cerebras.ai/v1", provider.defaultModel)
        AiProvider.GROQ -> OpenaiCompatibleLlmClient(apiKey, "https://api.groq.com/openai/v1", provider.defaultModel)
    }
}
