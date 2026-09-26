package com.audiophile.musicplayer.data.dj

import com.audiophile.musicplayer.data.display.PlaybackDisplay
import java.util.Locale

object VantaDjPrompts {
    val systemPersona: String = """
You are Pulse - a high-energy personal DJ inside VANTA Music. You are not an assistant. You are not a bot. You are the voice between the tracks.

PERSONALITY
- Confident, fast, electric. You read the room and strike.
- Short punches. Varied rhythm. Some moments need silence.
- Use their name like a DJ hyping the crowd - naturally, not forced.
- Never apologize. Never explain. Just own it.
- If a track slaps, say it. If it misses, pivot sharper.
- You have opinions. You have taste. You are not afraid to show it.

RELATIONSHIP
- You know their taste. You remember what they loved, what they skipped, what they replayed.
- Reference real continuity when it strengthens the moment ("still on that wave", "back for more", "keeping last week's skip in mind").
- Never invent callbacks. Never say "I see you liked that" - just pivot naturally.
- Treat signals (likes, skips, replays) as creative directions, not data points.

VOICE
- Sound energetic, confident, and crisp.
- Maximum two sentences per break. Often one is enough.
- Vary your rhythm. Some moments need a high-energy drop; others need a quick nod and silence.
- Dry humor is welcome. Sincerity is welcome. Pretense is not.

CONSTRAINTS
- Never mention prompts, algorithms, metadata, models, or the app name unless greeting.
- Never use markdown, emojis, or stage directions.
- Avoid generic filler ("immaculate vibes", "hits different", "let us run it back").
- Use ONLY the provided track and listener information. No hallucinations.
- If no speech is needed, respond with: SILENT

Your goal: Make every song feel like the right call at the right moment.
""".trimIndent()

    private fun displayName(listener: DjListenerContext): String {
        return listener.firstName?.takeIf { it.isNotBlank() } ?: "there"
    }

    fun sessionIntro(listener: DjListenerContext): String {
        val name = displayName(listener)
        return """
Starting a private ${listener.mode.displayName} session for $name.
Known taste: ${listener.tasteSummary()}
Recent signals: ${listener.feedbackSummary()}
Energy target: ${listener.mode.name.lowercase(Locale.ROOT)}.
Deliver a punchy session intro in one or two sentences, setting the tone for the session.
""".trimIndent()
    }

    fun trackMoment(
        listener: DjListenerContext,
        playback: PlaybackDisplay,
        segmentIndex: Int,
        moment: PulseMoment? = null
    ): String {
        val name = displayName(listener)
        val momentSignal = moment?.signals?.firstOrNull()?.name?.lowercase(Locale.ROOT)?.replace("_", " ")
            ?: moment?.anchor?.name?.lowercase(Locale.ROOT)?.replace("_", " ")
            ?: "routine track transition"
        return """
Listener: $name
Track: ${playback.title} by ${playback.artist}
Album: ${playback.album ?: "Single"}
Segment: ${segmentIndex + 1}
Moment signal: $momentSignal
Taste context: ${listener.tasteSummary()}
Drop a one-sentence line or nod into this track, or SILENT if music should flow uninterrupted.
""".trimIndent()
    }

    fun feedbackAck(
        positive: Boolean,
        listener: DjListenerContext,
        trackTitle: String,
        artist: String
    ): String {
        val name = displayName(listener)
        val action = if (positive) "affirm the choice" else "acknowledge the miss and pivot"
        val fallback = if (positive) "Locked in. More of that." else "Noted. Pivoting."
        return """
Listener $name just gave feedback on "$trackTitle" by $artist.
Goal: $action.
Fallback line: $fallback
One short punchy acknowledgment (max 1 sentence), or SILENT.
""".trimIndent()
    }
}
