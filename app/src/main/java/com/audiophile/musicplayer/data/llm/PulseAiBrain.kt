package com.audiophile.musicplayer.data.llm

import com.audiophile.musicplayer.ResolverConfigStore

class PulseAiBrain(
    private val resolverConfigStore: ResolverConfigStore
) {
    fun configuredClient(): LlmClient? {
        val provider = resolverConfigStore.getLlmProvider() ?: return null
        val apiKey = resolverConfigStore.getLlmApiKey(provider) ?: return null
        return createLlmClient(provider, apiKey)
    }

    fun isConfigured(): Boolean {
        val provider = resolverConfigStore.getLlmProvider() ?: return false
        if (!resolverConfigStore.isLlmVerified(provider)) return false
        return configuredClient() != null
    }

    suspend fun narrate(systemContext: String, userPrompt: String, fallback: String): Pair<String, Boolean> {
        val client = configuredClient() ?: return fallback to false
        val response = client.chat(systemContext, userPrompt)
            ?.trim()
            ?.takeIf { it.isNotBlank() }
        return if (response != null) {
            response to true
        } else {
            fallback to false
        }
    }

    suspend fun testConnection(): Pair<Boolean, String> {
        val provider = resolverConfigStore.getLlmProvider()
            ?: return false to "Select a Pulse AI provider first."
        val apiKey = resolverConfigStore.getLlmApiKey(provider)
        if (apiKey.isNullOrBlank()) {
            return false to "Enter your API key first."
        }
        val client = createLlmClient(provider, apiKey)
            ?: return false to "Could not create LLM client."
        val response = client.chat("You are a test assistant.", "Reply with exactly: OK")
        return if (response?.trim()?.contains("OK", ignoreCase = true) == true) {
            true to "${provider.displayName} connected successfully."
        } else {
            false to "${provider.displayName} connection failed. Check your API key."
        }
    }
}
