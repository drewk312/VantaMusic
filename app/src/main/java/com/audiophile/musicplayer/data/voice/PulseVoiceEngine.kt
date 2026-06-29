package com.audiophile.musicplayer.data.voice

import android.content.Context
import android.util.Log
import com.audiophile.musicplayer.ResolverConfigStore
import com.audiophile.musicplayer.data.llm.AiProvider

class PulseVoiceEngine(
    context: Context,
    private val configStore: ResolverConfigStore,
    private val relayClient: PulseVoiceRelayClient = PulseVoiceRelayClient()
) {
    private val cache = PulseVoiceCache(context.applicationContext)
    private val geminiClient = GeminiPulseVoiceClient()

    fun isPremiumConfigured(): Boolean {
        if (configStore.getPulseVoiceEngine() == DEVICE_ENGINE) return false
        if (!configStore.getPulseVoiceRelayUrl().isNullOrBlank()) return true
        return configStore.getLlmProvider() == AiProvider.GEMINI &&
            !configStore.getLlmApiKey(AiProvider.GEMINI).isNullOrBlank()
    }

    suspend fun synthesize(text: String): SynthesisResult? {
        val trimmed = text.trim()
        if (trimmed.isBlank()) return null

        if (configStore.getPulseVoiceEngine() == DEVICE_ENGINE) return null

        val engine = PulseVoiceProfile.GEMINI_ENGINE
        val cacheKey = cache.keyFor(trimmed, engine)
        val cachedPath = cache.pathFor(cacheKey)
        if (cachedPath != null) {
            return SynthesisResult(filePath = cachedPath, fromCache = true, fromPremium = true)
        }

        val geminiKey = if (configStore.getLlmProvider() == AiProvider.GEMINI) {
            configStore.getLlmApiKey(AiProvider.GEMINI)
        } else null
        if (!geminiKey.isNullOrBlank()) {
            val bytes = geminiClient.synthesize(geminiKey, trimmed)
            if (bytes != null) {
                cache.write(cacheKey, bytes, extension = "wav")
                val path = cache.pathFor(cacheKey)
                if (path != null) {
                    return SynthesisResult(filePath = path, fromCache = false, fromPremium = true)
                }
            }
            Log.w("VANTA_PULSE_VOICE", "Gemini voice failed; trying relay")
        }

        val relayUrl = configStore.getPulseVoiceRelayUrl()
        if (!relayUrl.isNullOrBlank()) {
            val relayBytes = relayClient.synthesize(
                relayBaseUrl = relayUrl,
                relayToken = configStore.getPulseVoiceRelayToken(),
                text = trimmed,
                engine = configStore.getPulseVoiceEngine(),
                voiceDescription = PulseVoiceProfile.VOICE_DESCRIPTION,
                openAiInstructions = PulseVoiceProfile.OPENAI_INSTRUCTIONS
            )
            if (relayBytes != null) {
                cache.write(cacheKey, relayBytes, extension = "mp3")
                val path = cache.pathFor(cacheKey)
                if (path != null) {
                    return SynthesisResult(filePath = path, fromCache = false, fromPremium = true)
                }
            }
            Log.w("VANTA_PULSE_VOICE", "Relay voice failed; using tuned device voice")
        }

        return null
    }

    data class SynthesisResult(
        val filePath: String,
        val fromCache: Boolean,
        val fromPremium: Boolean
    )

    companion object {
        const val DEVICE_ENGINE = "device_voice"
    }
}
