package com.audiophile.musicplayer.data.voice

object PulseVoiceProfile {
    const val GEMINI_ENGINE = "gemini_direct_tts_party_v1"
    const val ELEVENLABS_MODEL = "eleven_multilingual_v2"
    const val OPENAI_MODEL = "gpt-4o-mini-tts"
    const val OPENAI_VOICE = "nova"

    val VOICE_DESCRIPTION = """
A high-energy American club DJ in their early thirties. Confident, upbeat, and crisp — the voice of someone who reads the room and keeps the party moving. Bright, forward delivery with faster pacing, natural breaths, and genuine crowd energy. Never intimate, sultry, whispery, romantic, or late-night bedroom. Never theatrical, cheesy radio hype, or screaming. It should feel like a trusted personal DJ hyping one listener at peak set energy — tight, natural, and ready to drop the next track.
""".trimIndent()

    val OPENAI_INSTRUCTIONS = """
High-energy club DJ. Confident upbeat crisp delivery. Faster pacing, bright tone, crowd energy. Never intimate, sultry, whispery, or late-night. Never theatrical or cheesy radio hype. Natural party DJ — not screaming.
    """.trimIndent()

    val GEMINI_STYLE = """
Speak like a high-energy club DJ. Confident, upbeat, crisp, and bright.
Use faster pacing and genuine crowd energy. Never sound intimate, sultry, whispery, romantic, or late-night.
Never sound theatrical, promotional, or like cheesy radio hype.
Do not add, remove, or rewrite any words from the supplied line.
    """.trimIndent()
}
