package com.audiophile.musicplayer.ui.livinglyrics

import android.util.Log
import androidx.compose.ui.graphics.Color
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.pow

object ScenePlanQualityGate {
    private val recentSceneBySong = ConcurrentHashMap<String, LivingSceneType>()

    fun clearHistory() = recentSceneBySong.clear()

    fun repairOrFallback(plan: ScenePlan): ScenePlan {
        val issues = evaluate(plan)
        if (issues.isEmpty()) {
            recentSceneBySong[plan.songFingerprint()] = plan.artDirection.sceneFamily
            return plan
        }

        val repaired = repair(plan)
        val remainingIssues = evaluate(repaired, allowCurrentReuse = true)
        if (remainingIssues.isEmpty()) {
            Log.d("VANTA_SCENE_PLAN", "Repaired plan for '${plan.title}': ${issues.joinToString()}")
            recentSceneBySong[repaired.songFingerprint()] = repaired.artDirection.sceneFamily
            return repaired
        }

        Log.w("VANTA_SCENE_PLAN", "Fallback plan for '${plan.title}': ${remainingIssues.joinToString()}")
        return premiumFallback(plan)
    }

    fun evaluate(plan: ScenePlan, allowCurrentReuse: Boolean = false): List<String> {
        val issues = mutableListOf<String>()

        if (plan.motifs.distinct().size < 3) issues += "fewer_than_3_motifs"
        if (plan.title.isBlank() || plan.artist.isBlank()) issues += "missing_metadata"
        if (plan.beats.isEmpty()) issues += "no_beat_changes"
        if (plan.palette.textColor == plan.palette.background) issues += "low_lyric_contrast"
        if (contrastRatio(plan.palette.textColor, plan.palette.background) < 2.5f) issues += "poor_lyric_contrast"
        if (plan.artDirection.vignetteStrength < 0.08f) issues += "weak_vignette"
        if (!plan.artDirection.filmGrain && VisualMotifType.FILM_GRAIN !in plan.motifs) issues += "missing_film_grain"

        val hasDepthOrTexture = plan.motifs.any {
            it in setOf(
                VisualMotifType.DUST_PARTICLES,
                VisualMotifType.SMOKE,
                VisualMotifType.RAIN_DROPS,
                VisualMotifType.CLOUDS,
                VisualMotifType.SPARKS,
                VisualMotifType.FIREFLIES,
                VisualMotifType.STAGE_SPOTLIGHT_BEAM,
                VisualMotifType.STROBE_FLASH,
                VisualMotifType.HEADLIGHTS,
                VisualMotifType.NEON_SIGN,
                VisualMotifType.SUN_HORIZON,
                VisualMotifType.WATER_REFLECTION,
                VisualMotifType.WET_STREET_REFLECTION
            )
        }
        if (!hasDepthOrTexture) issues += "flat_color_slab"

        if (strongVibe(plan.vibeProfile) && plan.artDirection.sceneFamily == LivingSceneType.PREMIUM_FALLBACK) {
            issues += "generic_scene_for_strong_vibe_song"
        }

        val prior = recentSceneBySong[plan.songFingerprint()]
        if (!allowCurrentReuse && prior == plan.artDirection.sceneFamily && strongVibe(plan.vibeProfile)) {
            issues += "same_scene_reused_for_unrelated_songs"
        }

        return issues
    }

    private fun repair(plan: ScenePlan): ScenePlan {
        val family = if (strongVibe(plan.vibeProfile) && plan.artDirection.sceneFamily == LivingSceneType.PREMIUM_FALLBACK) {
            plan.vibeProfile.recommendedScene
        } else {
            plan.artDirection.sceneFamily
        }
        val motifs = (plan.motifs + requiredMotifs(family) + VisualMotifType.FILM_GRAIN).distinct()
        val beats = plan.beats.ifEmpty {
            listOf(
                LivingLyricBeat(
                    startMs = 0L,
                    endMs = 30_000L,
                    sceneType = family,
                    intensity = plan.vibeProfile.energy.coerceIn(0.25f, 1f)
                )
            )
        }.map { it.copy(sceneType = family) }

        return plan.copy(
            palette = ensureReadablePalette(ScenePalette.forScene(family)),
            artDirection = plan.artDirection.copy(
                sceneFamily = family,
                visualTheme = plan.artDirection.visualTheme.ifBlank {
                    "${plan.title} cinematic ${family.name.lowercase().replace("_", " ")}"
                },
                vignetteStrength = plan.artDirection.vignetteStrength.coerceIn(0.18f, 0.40f),
                atmosphereWeight = plan.artDirection.atmosphereWeight.coerceAtLeast(0.55f),
                filmGrain = true
            ),
            motifs = motifs,
            beats = beats
        )
    }

    private fun premiumFallback(plan: ScenePlan): ScenePlan {
        val family = LivingSceneType.PREMIUM_FALLBACK
        return plan.copy(
            palette = ensureReadablePalette(ScenePalette.forScene(family)),
            artDirection = ArtDirection(
                sceneFamily = family,
                lightingStyle = LightingStyle.WARM_LAMP,
                visualTheme = "premium album-art cinematic fallback",
                atmosphereWeight = 0.65f,
                vignetteStrength = 0.35f,
                filmGrain = true
            ),
            motifs = listOf(
                VisualMotifType.WARM_LAMP,
                VisualMotifType.DUST_PARTICLES,
                VisualMotifType.FILM_GRAIN,
                VisualMotifType.SMOKE,
                VisualMotifType.STAGE_SPOTLIGHT_BEAM
            ),
            beats = plan.beats.ifEmpty {
                listOf(LivingLyricBeat(startMs = 0L, endMs = 30_000L, sceneType = family, intensity = 0.5f))
            }.map { it.copy(sceneType = family) }
        )
    }

    private fun requiredMotifs(scene: LivingSceneType): List<VisualMotifType> = when (scene) {
        LivingSceneType.INDUSTRIAL_STAGE -> listOf(
            VisualMotifType.STEEL_BEAMS,
            VisualMotifType.STAGE_TRUSS,
            VisualMotifType.SMOKE,
            VisualMotifType.SPARKS,
            VisualMotifType.STROBE_FLASH
        )
        LivingSceneType.NIGHT_HIGHWAY_STAGE -> listOf(
            VisualMotifType.ROAD_LINES,
            VisualMotifType.HEADLIGHTS,
            VisualMotifType.DUST_PARTICLES,
            VisualMotifType.NEON_SIGN
        )
        LivingSceneType.OPEN_ROAD_SKY -> listOf(
            VisualMotifType.CLOUDS,
            VisualMotifType.SUN_HORIZON,
            VisualMotifType.GRASS_WISPS,
            VisualMotifType.ROAD_LINES
        )
        LivingSceneType.CITY_REFLECTION -> listOf(
            VisualMotifType.BUILDING_SILHOUETTE,
            VisualMotifType.WATER_REFLECTION,
            VisualMotifType.CLOUDS,
            VisualMotifType.CAR_STREAKS
        )
        LivingSceneType.NIGHT_CITY_PULSE,
        LivingSceneType.CITY_NIGHT -> listOf(
            VisualMotifType.BUILDING_SILHOUETTE,
            VisualMotifType.NEON_SIGN,
            VisualMotifType.WET_STREET_REFLECTION,
            VisualMotifType.CAR_STREAKS
        )
        LivingSceneType.BASEMENT_STAGE -> listOf(
            VisualMotifType.CONCRETE_WALL,
            VisualMotifType.TORN_POSTER,
            VisualMotifType.MICROPHONE_STAND
        )
        LivingSceneType.OPEN_FIELD,
        LivingSceneType.OPEN_FIELD_ROAD -> listOf(
            VisualMotifType.GRASS_WISPS,
            VisualMotifType.SUN_HORIZON,
            VisualMotifType.FIREFLIES
        )
        LivingSceneType.CHURCH_LIGHT -> listOf(
            VisualMotifType.CHAPEL_SILHOUETTE,
            VisualMotifType.STAGE_SPOTLIGHT_BEAM,
            VisualMotifType.DUST_PARTICLES
        )
        LivingSceneType.BEDROOM_MEMORY -> listOf(
            VisualMotifType.BEDROOM_WINDOW,
            VisualMotifType.WARM_LAMP,
            VisualMotifType.DUST_PARTICLES
        )
        LivingSceneType.STORM_WINDOW -> listOf(
            VisualMotifType.RAIN_DROPS,
            VisualMotifType.LIGHTNING_FLASH,
            VisualMotifType.FOG
        )
        LivingSceneType.DESERT_HIGHWAY -> listOf(
            VisualMotifType.ROAD_LINES,
            VisualMotifType.SUN_HORIZON,
            VisualMotifType.DUST_PARTICLES,
            VisualMotifType.HEADLIGHTS
        )
        LivingSceneType.MOVING_TRAIN -> listOf(
            VisualMotifType.TRAIN_CARS,
            VisualMotifType.DUST_PARTICLES,
            VisualMotifType.WATER_REFLECTION
        )
        LivingSceneType.STAGE_SPOTLIGHT,
        LivingSceneType.PREMIUM_FALLBACK -> listOf(
            VisualMotifType.STAGE_SPOTLIGHT_BEAM,
            VisualMotifType.DUST_PARTICLES,
            VisualMotifType.SMOKE
        )
    }

    private fun strongVibe(vibe: SongVibeProfile): Boolean =
        vibe.aggression > 0.65f ||
            vibe.industrial > 0.65f ||
            vibe.spiritual > 0.65f ||
            vibe.urban > 0.65f ||
            vibe.nostalgia > 0.65f

    private fun ensureReadablePalette(palette: ScenePalette): ScenePalette =
        palette.copy(textColor = Color(0xFFFFF4E8), textShadow = Color(0xCC000000))

    private fun contrastRatio(a: Color, b: Color): Float {
        val la = luminance(a)
        val lb = luminance(b)
        val lighter = maxOf(la, lb)
        val darker = minOf(la, lb)
        return (lighter + 0.05f) / (darker + 0.05f)
    }

    private fun luminance(c: Color): Float {
        fun channelLum(v: Float): Float =
            if (v <= 0.03928f) v / 12.92f else ((v + 0.055f) / 1.055f).pow(2.4f)
        return 0.2126f * channelLum(c.red) +
            0.7152f * channelLum(c.green) +
            0.0722f * channelLum(c.blue)
    }

    private fun ScenePlan.songFingerprint(): String =
        "${artist.trim().lowercase()}|${title.trim().lowercase()}"
}
