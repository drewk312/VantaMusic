package com.audiophile.musicplayer.data.dj

import android.util.Log
import com.audiophile.musicplayer.data.llm.PulseAiBrain
import com.audiophile.musicplayer.playback.NowPlayingState

class DjCompanionBrain(
    private val pulseAiBrain: PulseAiBrain
) {
    suspend fun respond(
        listener: DjListenerContext,
        companionMode: DjCompanionMode,
        nowPlaying: NowPlayingState,
        userPrompt: String,
        queueSummary: String
    ): DjStructuredResponse {
        val fallback = ruleBasedFallback(userPrompt, companionMode, nowPlaying)
        if (!pulseAiBrain.isConfigured()) {
            Log.d("VANTA_DJ_COMPANION", "llm_unavailable using_rule_fallback")
            return fallback
        }
        val userMessage = DjContextBuilder.buildUserPrompt(
            listener = listener,
            companionMode = companionMode,
            nowPlaying = nowPlaying,
            userPrompt = userPrompt,
            queueSummary = queueSummary
        )
        val (raw, fromLlm) = pulseAiBrain.narrate(
            systemContext = DjContextBuilder.SYSTEM_PROMPT,
            userPrompt = userMessage,
            fallback = fallback.message
        )
        if (!fromLlm) return fallback
        val parsed = DjStructuredResponseParser.parse(raw)
        if (parsed == null || parsed.message.isBlank()) {
            Log.w("VANTA_DJ_COMPANION", "json_parse_failed using_fallback")
            return fallback.copy(message = raw?.take(160).orEmpty().ifBlank { fallback.message })
        }
        return parsed
    }

    private fun ruleBasedFallback(
        prompt: String,
        mode: DjCompanionMode,
        nowPlaying: NowPlayingState
    ): DjStructuredResponse {
        val lower = prompt.lowercase()
        val message = when {
            "less talk" in lower -> "Got it. I'll stay quiet and let the music lead."
            "more like" in lower -> "Good pick. I'll build around this sound."
            "gym" in lower || "workout" in lower -> "Workout brain activated. Less talking, more momentum."
            "late night" in lower || "sleep" in lower -> "Late-night mode. I'll keep this smooth and not too bright."
            "darker" in lower -> "You’ve been leaning darker. I’ll keep it moody but not depressing."
            "happier" in lower || "happy" in lower -> "Lifting the energy a notch — still tasteful."
            "explain" in lower && nowPlaying.title != null ->
                "This is ${nowPlaying.title} — ${nowPlaying.artist.orEmpty()}. I’ll keep the set in the same lane."
            mode == DjCompanionMode.SILENT -> ""
            mode == DjCompanionMode.FOCUS -> "Focus mode. Stable flow, minimal chatter."
            else -> "I got you. Let me shape the queue around that."
        }
        val action = when {
            "less talk" in lower -> DjStructuredAction(type = DjActionType.REDUCE_TALKING)
            "gym" in lower || "workout" in lower || "hype" in lower ->
                DjStructuredAction(type = DjActionType.CHANGE_MOOD, direction = "hype")
            "late night" in lower -> DjStructuredAction(type = DjActionType.CHANGE_MOOD, direction = "late_night")
            "darker" in lower -> DjStructuredAction(type = DjActionType.ADJUST_QUEUE, direction = "smooth_dark_clean")
            "happier" in lower -> DjStructuredAction(type = DjActionType.ADJUST_QUEUE, direction = "bright_uplift")
            "more like" in lower -> DjStructuredAction(type = DjActionType.FIND_SIMILAR)
            "explain" in lower -> DjStructuredAction(type = DjActionType.EXPLAIN_CURRENT_SONG)
            "radio" in lower && nowPlaying.artist != null ->
                DjStructuredAction(type = DjActionType.START_RADIO, artist = nowPlaying.artist)
            else -> DjStructuredAction(type = DjActionType.ADJUST_QUEUE, direction = "stay_in_lane")
        }
        return DjStructuredResponse(
            message = message,
            mood = mode.name.lowercase(),
            energy = mode.talkLevel,
            actions = listOf(action),
            display = DjDisplayHint(cardType = "dj_moment", accent = "calm_green", durationMs = 6000L)
        )
    }
}
