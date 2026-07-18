package com.audiophile.musicplayer.ui.livinglyrics

enum class LivingSceneType {
    DESERT_HIGHWAY,
    CHURCH_LIGHT,
    OPEN_FIELD,
    MOVING_TRAIN,
    BEDROOM_MEMORY,
    CITY_NIGHT,
    STORM_WINDOW,
    STAGE_SPOTLIGHT,
    PREMIUM_FALLBACK,
    // ---- New genre-aware scenes ----
    OPEN_ROAD_SKY,
    INDUSTRIAL_STAGE,
    NIGHT_HIGHWAY_STAGE,
    CITY_REFLECTION,
    NIGHT_CITY_PULSE,
    BASEMENT_STAGE,
    OPEN_FIELD_ROAD
}

/**
 * Multi-dimensional vibe profile for a song. Drives scene routing.
 * All float values are 0.0–1.0.
 */
data class SongVibeProfile(
    val genre: String? = null,
    val energy: Float = 0.5f,
    val darkness: Float = 0.3f,
    val aggression: Float = 0.0f,
    val warmth: Float = 0.5f,
    val nostalgia: Float = 0.0f,
    val spiritual: Float = 0.0f,
    val romance: Float = 0.0f,
    val urban: Float = 0.0f,
    val industrial: Float = 0.0f,
    val cinematicKeywords: List<String> = emptyList(),
    val recommendedScene: LivingSceneType = LivingSceneType.PREMIUM_FALLBACK
)

data class LivingLyricBeat(
    val startMs: Long,
    val endMs: Long,
    val lyricLine: String = "",
    val sceneType: LivingSceneType,
    val subject: String = "",
    val setting: String = "",
    val cameraMotion: String = "",
    val emotion: String = "",
    val colorMood: String = "",
    val visualMotif: String = "",
    val intensity: Float = 0.5f
)

data class StoryScene(
    val songTitle: String = "",
    val artist: String = "",
    val album: String = "",
    val visualTheme: String = "",
    val vibeProfile: SongVibeProfile? = null,
    val beats: List<LivingLyricBeat> = emptyList()
)

data class LivingLyricsConfig(
    val title: String,
    val artist: String,
    val album: String?,
    val qualityLabel: String?,
    val isFavorite: Boolean,
    val onToggleFavorite: () -> Unit,
    val durationMs: Long,
    val positionMs: Long,
    val isPlaying: Boolean,
    val onSeekTo: (Long) -> Unit,
    val onPrevious: () -> Unit,
    val onNext: () -> Unit,
    val onTogglePlayPause: () -> Unit,
    val lyricsData: com.audiophile.musicplayer.data.lyrics.LyricsData?,
    val accentColor: androidx.compose.ui.graphics.Color,
    val artworkColors: com.audiophile.musicplayer.ui.ArtworkGradientColors?
)

