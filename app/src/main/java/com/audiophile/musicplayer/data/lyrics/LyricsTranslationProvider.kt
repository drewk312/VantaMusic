package com.audiophile.musicplayer.data.lyrics

import android.util.Log
import com.audiophile.musicplayer.data.llm.PulseAiBrain
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

class LyricsTranslationProvider(
    private val pulseAiBrain: PulseAiBrain
) {
    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()
    private val gson = Gson()
    private val cache = mutableMapOf<String, String>()

    suspend fun translate(
        lines: List<String>,
        targetLanguage: String = "en"
    ): List<String?> = withContext(Dispatchers.IO) {
        if (lines.all { it.isBlank() }) return@withContext lines.map { null }

        // Try LLM first
        val llmClient = pulseAiBrain.configuredClient()
        if (llmClient != null) {
            val batchSize = 15
            val results = mutableListOf<String?>()
            lines.chunked(batchSize).forEach { chunk ->
                val cached = chunk.map { cache[it] }
                val uncached = chunk.filterIndexed { i, _ -> cached[i] == null }
                if (uncached.isEmpty()) {
                    results.addAll(cached)
                } else {
                    val prompt = buildString {
                        appendLine("Translate the following song lyric lines to $targetLanguage.")
                        appendLine("Return ONLY the translated lines, one per line, in the same order.")
                        appendLine("If a line is blank or untranslatable, return an empty line.")
                        appendLine("---")
                        uncached.forEach { appendLine(it) }
                    }
                    val response = llmClient.chat(
                        "You are a precise lyrics translator. Never add commentary.",
                        prompt
                    )
                    if (response != null) {
                        val translated = response.lines()
                            .map { it.trim() }
                            .filterNot { it.startsWith("---") }
                        var ti = 0
                        chunk.forEach { original ->
                            if (cache.containsKey(original)) {
                                results.add(cache[original])
                            } else if (ti < translated.size) {
                                val t = translated[ti]
                                cache[original] = t
                                results.add(t)
                                ti++
                            } else {
                                results.add(null)
                            }
                        }
                    } else {
                        results.addAll(chunk.map { null })
                    }
                }
            }
            if (results.any { it != null }) return@withContext results
        }

        // Fallback: LibreTranslate
        val results = lines.map { line ->
            if (line.isBlank() || line.length < 3) return@map null
            cache[line] ?: libreTranslate(line, targetLanguage)?.also { cache[line] = it }
        }
        return@withContext results
    }

    private fun libreTranslate(text: String, target: String): String? {
        val instances = listOf(
            "https://libretranslate.com/translate",
            "https://translate.argosopentech.com/translate",
            "https://libretranslate.de/translate"
        )
        for (instance in instances) {
            try {
                val body = gson.toJson(mapOf(
                    "q" to text,
                    "source" to "auto",
                    "target" to target,
                    "format" to "text"
                ))
                val request = Request.Builder()
                    .url(instance)
                    .post(body.toRequestBody("application/json".toMediaType()))
                    .build()
                val response = httpClient.newCall(request).execute()
                if (response.isSuccessful) {
                    val json = gson.fromJson(response.body?.string(), Map::class.java)
                    return json?.get("translatedText")?.toString()?.trim()
                }
            } catch (e: Exception) {
                Log.w("VANTA_TRANSLATE", "LibreTranslate $instance failed: ${e.message}")
            }
        }
        return null
    }
}
