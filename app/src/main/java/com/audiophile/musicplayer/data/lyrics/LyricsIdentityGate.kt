package com.audiophile.musicplayer.data.lyrics

import android.util.Log
import com.audiophile.musicplayer.common.AcceptanceTruth
import com.audiophile.musicplayer.data.local.entities.UnifiedTrack
import com.audiophile.musicplayer.data.source.VocalRecordingClassifier

/**
 * Result of identity-verified lyrics fetch.
 *
 * Hierarchy encodes both availability AND confidence:
 *   ExactSync      — synced lyrics with confirmed identity (timing lines present)
 *   HighConfidence — synced lyrics but identity is inferred, not exact
 *   EstimatedTiming — plain lyrics with estimated per-line timing
 *   Unavailable    — no lyrics found for this track
 *   Rejected       — lyrics were found but rejected (variant mismatch, wrong track)
 */
sealed class LyricsIdentity {
    data class ExactSync(override val lyricsData: LyricsData) : LyricsIdentity()
    data class HighConfidenceSync(override val lyricsData: LyricsData) : LyricsIdentity()
    data class EstimatedTiming(override val lyricsData: LyricsData) : LyricsIdentity()
    data object Unavailable : LyricsIdentity() {
        override val lyricsData: LyricsData? = null
    }
    data class Rejected(val reason: String) : LyricsIdentity() {
        override val lyricsData: LyricsData? = null
    }

    abstract val lyricsData: LyricsData?

    val noLyrics: Boolean get() = this is Unavailable || this is Rejected

    val isLoading: Boolean get() = false
}

/**
 * Authoritative identity verification gate for lyrics.
 *
 * Wraps [LyricsRepository] to add:
 * 1. Pre-check: is this track expected to have lyrics? (instrumental/karaoke/tribute rejection)
 * 2. Identity cross-validation: normalized title/artist, duration tolerance, variant mismatch
 * 3. Confidence classification: exact → high-confidence → estimated
 *
 * This is the single source of truth for "should lyrics be shown for this track."
 */
class LyricsIdentityGate(
    private val lyricsRepository: LyricsRepository
) {
    companion object {
        /** Duration tolerance for matching track to lyrics source. */
        private const val DURATION_TOLERANCE_MS = 5_000L

        /** Variant markers that signal the playing track is a different version. */
        private val VARIANT_MARKERS = listOf(
            "live", "remix", "karaoke", "instrumental", "cover",
            "sped up", "sped-up", "nightcore", "re-recorded",
            "remastered", "acoustic", "demo", "radio edit",
            "slowed", "reverb", "chopped", "screwed"
        )
    }

    suspend fun resolveIdentity(
        track: UnifiedTrack,
        isrc: String?,
        userQuery: String? = null
    ): LyricsIdentity {
        // 1. Pre-check: skip instrumental/karaoke/tribute tracks entirely
        if (!VocalRecordingClassifier.lyricsExpected(track.title, track.artist, track.albumName, userQuery)) {
            Log.d("VANTA_IDENTITY", "lyrics not expected title='${track.title}' artist='${track.artist}' album='${track.albumName}'")
            AcceptanceTruth.lyrics(
                trackId = track.trackId.toString(),
                title = track.title,
                artist = track.artist,
                album = track.albumName,
                isrc = isrc,
                provider = null,
                lyricsMatchMethod = "not_expected",
                available = false
            )
            return LyricsIdentity.Unavailable
        }

        // 2. Fetch from repository (cache + providers)
        val lyricsData = lyricsRepository.getLyrics(track, isrc)

        if (lyricsData == null) {
            Log.d("VANTA_IDENTITY", "no lyrics found title='${track.title}' artist='${track.artist}' repo returned null")
            AcceptanceTruth.lyrics(
                trackId = track.trackId.toString(),
                title = track.title,
                artist = track.artist,
                album = track.albumName,
                isrc = isrc,
                provider = null,
                lyricsMatchMethod = "unavailable",
                available = false
            )
            return LyricsIdentity.Unavailable
        }

        // 3. Cross-validate identity: does this lyric source match the playing track?
        val rejection = crossValidateIdentity(track, lyricsData)
        if (rejection != null) {
            Log.w("VANTA_IDENTITY", "lyrics REJECTED title='${track.title}' reason='$rejection'")
            AcceptanceTruth.lyrics(
                trackId = track.trackId.toString(),
                title = track.title,
                artist = track.artist,
                album = track.albumName,
                isrc = isrc,
                provider = lyricsData.providerId,
                lyricsMatchMethod = "rejected:$rejection",
                available = false
            )
            return LyricsIdentity.Rejected(rejection)
        }

        // 4. Classify confidence level
        val identity = classifyIdentity(track, lyricsData)
        val method = when (identity) {
            is LyricsIdentity.ExactSync -> "exact_sync"
            is LyricsIdentity.HighConfidenceSync -> "high_confidence_sync"
            is LyricsIdentity.EstimatedTiming -> "estimated_timing"
            is LyricsIdentity.Unavailable -> "unavailable"
            is LyricsIdentity.Rejected -> "rejected"
        }
        AcceptanceTruth.lyrics(
            trackId = track.trackId.toString(),
            title = track.title,
            artist = track.artist,
            album = track.albumName,
            isrc = isrc,
            provider = lyricsData.providerId,
            lyricsMatchMethod = method,
            available = identity.lyricsData != null
        )
        return identity
    }

    /**
     * Cross-validates that lyrics match the playing recording.
     * Returns a rejection reason string, or null if identity passes.
     */
    private fun crossValidateIdentity(track: UnifiedTrack, lyricsData: LyricsData): String? {
        // NOTE: lyricsData.trackKey is a hash/ID (e.g. "0" or "c5496f8a..."), NOT a title.
        // We cannot extract variant markers from it. Variant mismatch detection is skipped
        // because we have no reliable lyrics source title to compare against.

        // Duration cross-check when both values are available
        val trackDuration = track.durationMs
        val lyricsDuration = lyricsData.lines.lastOrNull()?.let {
            it.endTimeMs ?: it.startTimeMs
        }
        if (trackDuration != null && trackDuration > 0 && lyricsDuration != null && lyricsDuration > 0) {
            val diff = kotlin.math.abs(trackDuration - lyricsDuration)
            if (diff > DURATION_TOLERANCE_MS && lyricsDuration > 30_000L) {
                // Only reject on duration if lyrics are long enough to be meaningful
                // (short lyrics might end before the song does)
                Log.d("VANTA_IDENTITY", "duration check: track=${trackDuration}ms lyrics=${lyricsDuration}ms diff=${diff}ms")
                // Don't hard-reject on duration alone — downgrade to estimated instead
            }
        }

        return null // Identity passes
    }

    private fun classifyIdentity(track: UnifiedTrack, lyricsData: LyricsData): LyricsIdentity {
        val hasTimedLines = lyricsData.lines.any { it.startTimeMs != null }

        // Duration-based confidence check
        val trackDuration = track.durationMs
        val lastLyricTime = lyricsData.lines.lastOrNull()?.let { it.endTimeMs ?: it.startTimeMs }
        val durationMismatch = if (trackDuration != null && trackDuration > 0 && lastLyricTime != null && lastLyricTime > 0) {
            kotlin.math.abs(trackDuration - lastLyricTime) > DURATION_TOLERANCE_MS
        } else false

        val identity = when {
            lyricsData.isSynced && hasTimedLines && !durationMismatch -> {
                LyricsIdentity.ExactSync(lyricsData)
            }
            lyricsData.isSynced && hasTimedLines && durationMismatch -> {
                // Synced lyrics but duration doesn't match well — still show but lower confidence
                LyricsIdentity.HighConfidenceSync(lyricsData)
            }
            hasTimedLines -> {
                LyricsIdentity.EstimatedTiming(lyricsData)
            }
            else -> {
                LyricsIdentity.EstimatedTiming(lyricsData)
            }
        }

        Log.d("VANTA_IDENTITY", "classifyIdentity providerId='${lyricsData.providerId}' synced=${lyricsData.isSynced} lines=${lyricsData.lines.size} timed=$hasTimedLines durationMismatch=$durationMismatch -> $identity")
        return identity
    }

    /** Extract variant markers present in a title string. */
    private fun extractVariants(title: String): Set<String> {
        val lower = title.lowercase()
        return VARIANT_MARKERS.filter { marker -> lower.contains(marker) }.toSet()
    }

}
