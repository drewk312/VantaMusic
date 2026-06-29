package com.audiophile.musicplayer.data.dj

import com.audiophile.musicplayer.playback.NowPlayingState
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

object DjContextBuilder {

    fun buildUserPrompt(
        listener: DjListenerContext,
        companionMode: DjCompanionMode,
        nowPlaying: NowPlayingState,
        userPrompt: String,
        queueSummary: String
    ): String {
        val now = LocalDateTime.now()
        val day = now.format(DateTimeFormatter.ofPattern("EEEE"))
        val date = now.format(DateTimeFormatter.ofPattern("MMMM d, yyyy"))
        val time = now.format(DateTimeFormatter.ofPattern("h:mm a"))
        return buildString {
            appendLine("Today is $day, $date.")
            appendLine("Local time is $time.")
            appendLine("Companion mode: ${companionMode.label}.")
            appendLine("DJ mode: ${listener.mode.displayName}.")
            val title = nowPlaying.title?.trim().orEmpty()
            val artist = nowPlaying.artist?.trim().orEmpty()
            if (title.isNotBlank()) {
                appendLine("Now playing: \"$title\" by $artist.")
            } else {
                appendLine("Nothing is playing right now.")
            }
            appendLine("Queue: $queueSummary")
            appendLine("Taste: ${listener.tasteSummary()}")
            appendLine("Signals: ${listener.feedbackSummary()}")
            appendLine("User said: \"$userPrompt\"")
            appendLine("Speak briefly. Be warm, confident, and useful. Return JSON only.")
        }.trim()
    }

    val SYSTEM_PROMPT = """
You are VANTA DJ, a personal music companion inside a premium Android music player.
You are warm, confident, emotionally aware, and music-obsessed.
You do not sound like a corporate assistant or fake radio host.
You speak briefly and naturally.
You know the current time, date, song, queue, and user preferences.
Your job is to make listening feel alive, personal, and comfortable.
You can control music only through the provided structured actions.
Never invent unavailable actions. Never be cringe. Never overtalk.
Match the user's energy. Return valid JSON only.

Response schema:
{
  "message": "short human line",
  "mood": "late_night",
  "energy": 0.45,
  "actions": [{ "type": "ADJUST_QUEUE", "direction": "smooth_dark_clean" }],
  "display": { "cardType": "dj_moment", "accent": "calm_green", "durationMs": 6000 }
}
""".trimIndent()
}
