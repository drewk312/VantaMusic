package com.audiophile.musicplayer.data.llm

enum class AiProvider(
    val rawValue: String,
    val displayName: String,
    val subtitle: String,
    val defaultModel: String,
    val keyConsoleUrl: String,
    val keyConsoleLabel: String,
    val keyHelpHint: String
) {
    GEMINI(
        "gemini",
        "Gemini",
        "Google",
        "gemini-2.5-flash",
        "https://aistudio.google.com/app/apikey",
        "Open Google AI Studio",
        "Sign in with Google → Create API key → copy the key"
    ),
    CEREBRAS(
        "cerebras",
        "Cerebras",
        "Qwen 235B",
        "qwen-3-235b-a22b-instruct-2507",
        "https://cloud.cerebras.ai/platform/apis",
        "Open Cerebras Cloud",
        "Sign up → Platform → API keys → create and copy"
    ),
    GROQ(
        "groq",
        "Groq",
        "Llama 3.3 70B",
        "llama-3.3-70b-versatile",
        "https://console.groq.com/keys",
        "Open Groq Console",
        "Sign in → API Keys → Create API key → copy"
    )
}
