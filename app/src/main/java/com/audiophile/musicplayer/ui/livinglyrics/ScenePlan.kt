package com.audiophile.musicplayer.ui.livinglyrics

import androidx.compose.ui.graphics.Color

/**
 * A per-song visual plan. This is the output of the director (AI or local fallback).
 * Two songs in the same scene family will still look different because their
 * palette, motifs, intensity curve, and motion profile are unique.
 *
 * ScenePlans are cached by songId and rendered by [SceneComposer].
 */
data class ScenePlan(
    val songId: String,
    val title: String,
    val artist: String,
    val album: String?,
    val vibeProfile: SongVibeProfile,
    val artDirection: ArtDirection,
    val palette: ScenePalette,
    val typography: LyricTypography,
    val motionProfile: MotionProfile,
    val motifs: List<VisualMotifType>,
    val beats: List<LivingLyricBeat>
)

/**
 * Art direction envelope: the scene family, overall atmosphere, lighting style.
 */
data class ArtDirection(
    val sceneFamily: LivingSceneType,
    val lightingStyle: LightingStyle = LightingStyle.NATURAL,
    val atmosphereWeight: Float = 0.5f,
    val vignetteStrength: Float = 0.2f,
    val filmGrain: Boolean = false,
    val visualTheme: String = ""
)

enum class LightingStyle {
    NATURAL,        // sunlight, daylight, soft ambient
    STAGE,          // spotlights, beams, concert lighting
    NEON,           // neon signs, colored glow, wet reflections
    HARSH,          // strobes, industrial, cold white
    WARM_LAMP,      // interior, bedside, warm glow
    MOONLIGHT,      // blue-tinted, night, silvery
    FIRE            // red/orange, flickering, heat
}

/**
 * Per-song color palette. Extracted from vibe profile or AI direction.
 */
data class ScenePalette(
    val background: Color,
    val primary: Color,
    val secondary: Color,
    val accent: Color,
    val highlight: Color,
    val textColor: Color = Color(0xFFFFF4E8),
    val textShadow: Color = Color(0x99000000)
) {
    companion object {
        val INDUSTRIAL = ScenePalette(
            background = Color(0xFF050505),
            primary = Color(0xFF3A3A3A),
            secondary = Color(0xFFCC2020),
            accent = Color(0xFFFFFFFF),
            highlight = Color(0xFFFF4400)
        )
        val NIGHT_HIGHWAY = ScenePalette(
            background = Color(0xFF0A0A18),
            primary = Color(0xFF1A1A1A),
            secondary = Color(0xFFFFAA40),
            accent = Color(0xFFFF3040),
            highlight = Color(0xFFE8C840)
        )
        val OPEN_ROAD_SKY = ScenePalette(
            background = Color(0xFF1A2440),
            primary = Color(0xFF2A3A18),
            secondary = Color(0xFFFFD060),
            accent = Color(0xFFFFF0D0),
            highlight = Color(0xFF80A060)
        )
        val CITY_REFLECTION = ScenePalette(
            background = Color(0xFF2A4A6A),
            primary = Color(0xFF2A3040),
            secondary = Color(0xFF6A9AC0),
            accent = Color(0xFFD4B870),
            highlight = Color(0xFFFFE8C0)
        )
        val NIGHT_CITY_PULSE = ScenePalette(
            background = Color(0xFF080810),
            primary = Color(0xFF0E0E18),
            secondary = Color(0xFF8020E0),
            accent = Color(0xFF20D0E0),
            highlight = Color(0xFFE04080)
        )
        val BASEMENT_STAGE = ScenePalette(
            background = Color(0xFF2A2520),
            primary = Color(0xFF1A1815),
            secondary = Color(0xFFFFE880),
            accent = Color(0xFFE04040),
            highlight = Color(0xFF4060D0)
        )
        val OPEN_FIELD_ROAD = ScenePalette(
            background = Color(0xFF3A4060),
            primary = Color(0xFF2A3A18),
            secondary = Color(0xFFB87840),
            accent = Color(0xFFFFD080),
            highlight = Color(0xFF4A5A30)
        )
        val CHURCH_LIGHT = ScenePalette(
            background = Color(0xFF1A1510),
            primary = Color(0xFF2A2520),
            secondary = Color(0xFFFFDCA0),
            accent = Color(0xFFFFF5E0),
            highlight = Color(0xFFD4A56A)
        )
        val BEDROOM_MEMORY = ScenePalette(
            background = Color(0xFF1A1510),
            primary = Color(0xFF2A2520),
            secondary = Color(0xFFFFD8A0),
            accent = Color(0xFFFFC080),
            highlight = Color(0xFF6A5A4A)
        )
        val STORM_WINDOW = ScenePalette(
            background = Color(0xFF0A0A14),
            primary = Color(0xFF1A1A2A),
            secondary = Color(0xFF6A8AAA),
            accent = Color(0xFFFFFFFF),
            highlight = Color(0xFF4A5A6A)
        )
        val DESERT_HIGHWAY = ScenePalette(
            background = Color(0xFF4A3A2A),
            primary = Color(0xFF8A7A60),
            secondary = Color(0xFFD4A56A),
            accent = Color(0xFFFFF0D0),
            highlight = Color(0xFF6A9AC0)
        )
        val DEFAULT = ScenePalette(
            background = Color(0xFF1A1510),
            primary = Color(0xFF2A2520),
            secondary = Color(0xFFFFE8C0),
            accent = Color(0xFFFFD080),
            highlight = Color(0xFF4A3A2A)
        )

        fun forScene(scene: LivingSceneType): ScenePalette = when (scene) {
            LivingSceneType.INDUSTRIAL_STAGE -> INDUSTRIAL
            LivingSceneType.NIGHT_HIGHWAY_STAGE -> NIGHT_HIGHWAY
            LivingSceneType.OPEN_ROAD_SKY -> OPEN_ROAD_SKY
            LivingSceneType.CITY_REFLECTION -> CITY_REFLECTION
            LivingSceneType.NIGHT_CITY_PULSE -> NIGHT_CITY_PULSE
            LivingSceneType.BASEMENT_STAGE -> BASEMENT_STAGE
            LivingSceneType.OPEN_FIELD_ROAD -> OPEN_FIELD_ROAD
            LivingSceneType.CHURCH_LIGHT -> CHURCH_LIGHT
            LivingSceneType.BEDROOM_MEMORY -> BEDROOM_MEMORY
            LivingSceneType.STORM_WINDOW -> STORM_WINDOW
            LivingSceneType.DESERT_HIGHWAY -> DESERT_HIGHWAY
            else -> DEFAULT
        }
    }
}

/**
 * Per-song typography treatment for lyrics.
 */
data class LyricTypography(
    val fontWeight: Int = 600,
    val fontSize: Float = 26f,
    val letterSpacing: Float = 0f,
    val lineHeight: Float = 34f,
    val textTransform: TextTransform = TextTransform.NONE
)

enum class TextTransform { NONE, UPPERCASE, LOWERCASE }

/**
 * Per-song motion profile: how things move, how fast, how aggressive.
 */
data class MotionProfile(
    val driftSpeed: Float = 1.0f,
    val pulseIntensity: Float = 0.5f,
    val strobeEnabled: Boolean = false,
    val strobeDurationMs: Int = 400,
    val cameraShake: Float = 0.0f,
    val parallaxStrength: Float = 0.5f,
    val transitionStyle: TransitionStyle = TransitionStyle.CROSSFADE
)

enum class TransitionStyle { CROSSFADE, CUT, PUSH, DISSOLVE }

/**
 * Enumeration of all reusable visual motifs (drawing building blocks).
 * These are small, composable Canvas-drawn elements that [SceneComposer]
 * layers together to create a unique scene.
 */
enum class VisualMotifType {
    // Environment / Structure
    ROAD_LINES,
    STEEL_BEAMS,
    STAGE_TRUSS,
    BUILDING_SILHOUETTE,
    CHAPEL_SILHOUETTE,
    FENCE_POSTS,
    POWER_LINES,
    CONCRETE_WALL,
    BEDROOM_WINDOW,

    // Atmosphere
    SMOKE,
    DUST_PARTICLES,
    RAIN_DROPS,
    FILM_GRAIN,
    FOG,
    CLOUDS,

    // Light
    HEADLIGHTS,
    NEON_SIGN,
    STAGE_SPOTLIGHT_BEAM,
    STROBE_FLASH,
    SUN_HORIZON,
    MOONLIGHT,
    WARM_LAMP,
    CANDLE_FLICKER,
    LIGHTNING_FLASH,

    // Objects / Detail
    SPARKS,
    STARS,
    FIREFLIES,
    CROWD_SILHOUETTE,
    GRASS_WISPS,
    WATER_REFLECTION,
    WET_STREET_REFLECTION,
    TRAIN_CARS,
    CAR_STREAKS,
    MICROPHONE_STAND,
    GRAFFITI_MARKS,
    TORN_POSTER
}
