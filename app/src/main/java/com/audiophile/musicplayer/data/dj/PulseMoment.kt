package com.audiophile.musicplayer.data.dj

import com.audiophile.musicplayer.data.local.entities.UnifiedTrackWithSources

/**
 * A structured explanation of why a specific track was chosen for the current
 * AI DJ segment. This is the source of truth for both fallback reasons and LLM
 * prompts, so the DJ never has to invent a story.
 */
data class PulseMoment(
    val trackId: Long,
    val title: String,
    val artist: String,
    val album: String?,
    val releaseYear: Int?,
    /** Primary creative reason this pick was surfaced. */
    val anchor: Anchor,
    /** Sub-signals that influenced the score. */
    val signals: List<Signal> = emptyList(),
    /** Position in the segment — used for opener/closer language. */
    val position: Position,
    /** How strongly this pick matched the requested mode/station. */
    val confidence: Float,
    /** True if this track was explicitly favored or replayed by the listener. */
    val isReturningFavorite: Boolean = false,
    /** True if this pick was chosen to break up repetition. */
    val isVarietyPick: Boolean = false
) {
    enum class Anchor {
        TASTE_MATCH,
        FAVORITE_ARTIST,
        GENRE_MATCH,
        ERA_MATCH,
        MOOD_MATCH,
        ENERGY_MATCH,
        BRIDGE,
        DISCOVERY,
        DEEP_CUT,
        COMEBACK,
        CLOSER,
        OPENER,
        RECENT_LIKE_ECHO,
        STATION_SEED,
        VARIETY
    }

    enum class Signal {
        HIGH_BITRATE,
        RECENTLY_LIKED,
        RECENTLY_SKIPPED,
        SAVED,
        FULL_PLAY,
        REPLAYED,
        SAME_ALBUM_AS_PRIOR,
        SAME_ARTIST_AS_PRIOR,
        YEAR_IN_RANGE,
        MOOD_KEYWORD_HIT,
        SEED_ARTIST_MATCH,
        SEED_GENRE_MATCH,
        DIAMOND_IN_LIBRARY
    }

    enum class Position { OPENER, BODY, CLOSER, SOLO }

    /** One-sentence reason suitable for UI and fallback narration. */
    fun humanReason(): String = buildReasonSentence()

    /** Richer context for the LLM prompt. */
    fun promptContext(): String = buildString {
        append("Picked for ")
        append(anchor.name.lowercase().replace("_", " "))
        if (signals.isNotEmpty()) {
            append("; signals: ")
            append(signals.take(4).joinToString(", ") { it.name.lowercase().replace("_", " ") })
        }
        releaseYear?.let { append("; released $it") }
        if (isReturningFavorite) append("; a returning favorite")
        if (isVarietyPick) append("; added for variety")
    }

    private fun buildReasonSentence(): String {
        val verb = when (position) {
            Position.OPENER -> "kicks off"
            Position.CLOSER -> "closes"
            Position.SOLO -> "fills"
            Position.BODY -> "fits"
        }
        val what = when (anchor) {
            Anchor.TASTE_MATCH -> "your taste"
            Anchor.FAVORITE_ARTIST -> "a favorite artist"
            Anchor.GENRE_MATCH -> "the ${primaryGenreOr("genre")} thread"
            Anchor.ERA_MATCH -> releaseYear?.let { "the $it era" } ?: "the era"
            Anchor.MOOD_MATCH -> "the mood"
            Anchor.ENERGY_MATCH -> "the energy"
            Anchor.BRIDGE -> "the transition"
            Anchor.DISCOVERY -> "something new worth your ears"
            Anchor.DEEP_CUT -> "a deep cut"
            Anchor.COMEBACK -> "a comeback"
            Anchor.CLOSER -> "the close"
            Anchor.OPENER -> "the open"
            Anchor.RECENT_LIKE_ECHO -> "a recent like"
            Anchor.STATION_SEED -> "the station seed"
            Anchor.VARIETY -> "variety"
        }
        val suffix = when {
            isReturningFavorite -> " — welcome back."
            isVarietyPick -> " — keeping it fresh."
            position == Position.CLOSER -> "."
            else -> "."
        }
        return "$title by $artist $verb $what$suffix"
    }

    private fun primaryGenreOr(fallback: String): String {
        return signals.firstOrNull { it == Signal.SEED_GENRE_MATCH }?.name?.lowercase()?.replace("_", " ") ?: fallback
    }
}

/** Wraps a chosen track plus the moment that explains it. */
data class PulsePick(
    val track: UnifiedTrackWithSources,
    val moment: PulseMoment,
    val score: Float
)

fun UnifiedTrackWithSources.toPulseMomentSkeleton(position: PulseMoment.Position): PulseMoment = PulseMoment(
    trackId = track.trackId,
    title = track.title.orEmpty(),
    artist = track.artist.orEmpty(),
    album = track.albumName,
    releaseYear = null,
    anchor = PulseMoment.Anchor.TASTE_MATCH,
    signals = emptyList(),
    position = position,
    confidence = 0.5f
)
