package com.audiophile.musicplayer.data.dj

import com.audiophile.musicplayer.data.local.entities.UnifiedTrackWithSources

data class AiDjSession(
    val id: Long,
    val mode: AiDjMode,
    val title: String,
    val currentSegmentIndex: Int = 0,
    val segments: List<AiDjSegment> = emptyList(),
    val feedbackHistory: List<AiDjFeedback> = emptyList(),
    val createdAt: Long = System.currentTimeMillis(),
    val listeningStyle: PulseListeningStyle = PulseListeningStyle.PULSE_LIVE,
    val stationId: String? = null,
    val chapterGenre: String? = null,
    val chapterLikes: Int = 0,
    val chapterSkips: Int = 0,
    val chapterTrackTarget: Int = 6,
    val chapterTracksPlayed: Int = 0
)

data class AiDjSegment(
    val id: Long,
    val title: String,
    val vibeDescription: String,
    val tracks: List<UnifiedTrackWithSources>,
    val picks: List<AiDjPick>,
    val narration: String
)

data class AiDjPick(
    val track: UnifiedTrackWithSources,
    val reason: String,
    val confidence: Float
)

sealed class AiDjFeedback {
    abstract val trackId: Long
    data class Liked(override val trackId: Long) : AiDjFeedback()
    data class Skipped(override val trackId: Long) : AiDjFeedback()
    data class MoreLikeThis(override val trackId: Long) : AiDjFeedback()
    data class LessLikeThis(override val trackId: Long) : AiDjFeedback()
    data class Blocked(override val trackId: Long, val artist: String) : AiDjFeedback()
}

data class AiDjTasteProfile(
    val favoriteArtists: List<String> = emptyList(),
    val favoriteGenres: List<String> = emptyList(),
    val recentlyPlayedArtists: List<String> = emptyList(),
    val topArtistsByPlayCount: List<String> = emptyList(),
    val genreCounts: Map<String, Int> = emptyMap(),
    val totalTracks: Int = 0,
    val hasImportedTracks: Boolean = false
)

data class AiDjSessionUiState(
    val isStarted: Boolean = false,
    val currentSession: AiDjSession? = null,
    val currentSegment: AiDjSegment? = null,
    val isLoading: Boolean = false,
    val availableModes: List<AiDjMode> = AiDjMode.entries,
    val tasteProfile: AiDjTasteProfile = AiDjTasteProfile(),
    val statusMessage: String? = null
)
