package com.audiophile.musicplayer.data.dj

import com.audiophile.musicplayer.data.local.entities.UnifiedTrackWithSources
import com.audiophile.musicplayer.data.llm.PulseAiBrain

class AiDjNarrationGenerator(
    private val pulseAiBrain: PulseAiBrain
) {

    suspend fun generateModeIntro(listener: DjListenerContext): DjCommentary {
        val fallback = generateModeIntroFallback(listener.mode, listener)
        if (!pulseAiBrain.isConfigured()) return DjCommentary(fallback, fromPulseAi = false)
        return finalizeNarration(
            pulseAiBrain.narrate(
                VantaDjPrompts.systemPersona,
                VantaDjPrompts.sessionIntro(listener),
                fallback
            ),
            fallback
        )
    }

    suspend fun generateSegmentIntro(
        segment: AiDjSegment,
        segmentIndex: Int,
        listener: DjListenerContext
    ): DjCommentary {
        if (segmentIndex == 0) return generateModeIntro(listener.copy(mode = listener.mode))
        val fallback = generateSegmentIntroFallback(segment, segmentIndex, listener.mode, listener)
        if (!pulseAiBrain.isConfigured()) return DjCommentary(fallback, fromPulseAi = false)
        val userPrompt = """
Continuing a private ${listener.mode.displayName} set.
Segment ${segmentIndex + 1}. Vibe: ${segment.vibeDescription}.
${listener.firstName?.let { "Display name: $it. Use only if natural." } ?: "No display name."}
Known taste: ${listener.tasteSummary()}
Recent signals: ${listener.feedbackSummary()}
Brief transition into the next stretch. One or two sentences, or SILENT if unnecessary.
""".trimIndent()
        return finalizeNarration(
            pulseAiBrain.narrate(VantaDjPrompts.systemPersona, userPrompt, fallback),
            fallback
        )
    }

    suspend fun generateTrackCommentary(
        track: UnifiedTrackWithSources,
        listener: DjListenerContext,
        segmentIndex: Int
    ): DjCommentary {
        if (!pulseAiBrain.isConfigured()) return DjCommentary.Silent
        val playback = track.toPlaybackDisplay()
        val artist = track.track.artist.orEmpty()
        val title = track.track.title.orEmpty()
        val album = track.track.albumName.orEmpty()
        val fallback = listOfNotNull(
            "Up next — $artist — $title.".takeIf { title.isNotBlank() && artist.isNotBlank() },
            "Up next — $artist.".takeIf { artist.isNotBlank() },
            "Next up."
        ).first()
        val result = pulseAiBrain.narrate(
            VantaDjPrompts.systemPersona,
            VantaDjPrompts.trackMoment(listener, playback, segmentIndex),
            fallback
        )
        val final = finalizeNarration(result, fallback)
        if (final.fromPulseAi) {
            val metadata = setOfNotNull(title, artist, album, listener.displayName, "Vanta").filter { it.isNotBlank() }.toSet()
            if (!DjFactGuard.validate(final.text, metadata)) {
                return DjCommentary(fallback, fromPulseAi = false)
            }
        }
        return final
    }

    suspend fun generateFeedbackAcknowledgment(
        positive: Boolean,
        listener: DjListenerContext,
        trackTitle: String,
        artist: String
    ): DjCommentary {
        val fallback = if (positive) {
            listener.firstName?.let { "Yes, $it — more of that." }
                ?: "More like that."
        } else {
            listener.firstName?.let { "Switching gears, $it." }
                ?: "Changing it up."
        }
        if (!pulseAiBrain.isConfigured()) return DjCommentary(fallback, fromPulseAi = false)
        return finalizeNarration(
            pulseAiBrain.narrate(
                VantaDjPrompts.systemPersona,
                VantaDjPrompts.feedbackAck(positive, listener, trackTitle, artist),
                fallback
            ),
            fallback
        )
    }

    private fun finalizeNarration(
        result: Pair<String, Boolean>,
        fallback: String
    ): DjCommentary {
        val raw = result.first.trim()
        val fromAi = result.second
        if (raw.equals("SILENT", ignoreCase = true)) return DjCommentary.Silent
        val text = raw.ifBlank { fallback.trim() }
        if (text.isBlank() || text.equals("SILENT", ignoreCase = true)) return DjCommentary.Silent
        return DjCommentary(text = text, fromPulseAi = fromAi)
    }

    fun generatePickReason(track: UnifiedTrackWithSources, pickIndex: Int, totalPicks: Int): String {
        val artist = track.track.artist
        val title = track.track.title
        return when (pickIndex) {
            0 -> "Opens the set with $artist."
            totalPicks - 1 -> "Closes this stretch with $artist."
            else -> "$artist — $title"
        }
    }

        fun generateEmptyLibraryMessage(mode: AiDjMode? = null): String {
        return when (mode) {
            AiDjMode.LATE_NIGHT -> "Library is empty. Late night sessions need fuel - search an artist or drop a vibe."
            AiDjMode.WORKOUT -> "Empty library. Workout mode needs heat - give me a seed track."
            AiDjMode.CHILL_VIBES -> "Library is empty. Perfect time to discover something new."
            AiDjMode.DEEP_CUTS -> "Empty library. Deep cuts need depth - search an artist you love."
            else -> "Your library is empty. Add some music or search for songs to get started."
        }
    }


    fun generateNoPlayableTracks(count: Int): String {
        return if (count == 0) {
            "Found some recommendations, but none have playable sources yet."
        } else {
            "Found $count playable tracks for this set."
        }
    }

    private fun generateModeIntroFallback(mode: AiDjMode, listener: DjListenerContext): String {
        val name = listener.firstName
        return when {
            name != null && listener.totalSessions > 1 ->
                "$name, back for another round."
            name != null ->
                "$name, let's get this started."
            mode == AiDjMode.LATE_NIGHT -> "Late night, right energy."
            else -> "Let's go."
        }
    }

    private fun generateSegmentIntroFallback(
        segment: AiDjSegment,
        segmentIndex: Int,
        mode: AiDjMode,
        listener: DjListenerContext
    ): String {
        if (segmentIndex == 0) return segment.narration
        val name = listener.firstName
        return when {
            name != null -> "$name, let's keep it moving."
            mode == AiDjMode.LATE_NIGHT -> "Staying locked in."
            else -> "Next up."
        }
    }
}

