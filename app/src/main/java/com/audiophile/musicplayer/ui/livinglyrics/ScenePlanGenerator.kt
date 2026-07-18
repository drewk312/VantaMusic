package com.audiophile.musicplayer.ui.livinglyrics

import android.util.Log
import com.audiophile.musicplayer.data.lyrics.LyricsData
import java.util.concurrent.ConcurrentHashMap

/**
 * Generates per-song [ScenePlan]s from metadata signals. This is the local
 * fallback when Pulse AI is unavailable. Even without AI, every song gets
 * a unique plan because the generator combines:
 *   - VibeClassifier profile → scene family
 *   - Scene family → base motif set
 *   - Vibe dimensions → motif additions/removals + palette tweaks
 *   - Lyrics → beat generation with intensity curve
 *
 * Plans are cached by songId.
 */
object ScenePlanGenerator {

    private val planCache = ConcurrentHashMap<String, ScenePlan>()

    fun clearCache() {
        planCache.clear()
        ScenePlanQualityGate.clearHistory()
    }
    fun invalidate(songId: String) = planCache.remove(songId)

    fun generate(
        songId: String,
        title: String,
        artist: String,
        album: String?,
        genre: String? = null,
        lyricsData: LyricsData? = null,
        audioEnergy: Float? = null
    ): ScenePlan {
        planCache[songId]?.let { return it }

        val lyricText = lyricsData?.lines?.joinToString(" ") { it.text }

        // 1. Classify vibe
        val vibe = VibeClassifier.classify(title, artist, album, genre, lyricText, audioEnergy)

        // 2. Select motifs for this specific song
        val motifs = selectMotifs(vibe)

        // 3. Build palette (may adjust from vibe profile)
        val palette = buildPalette(vibe)

        // 4. Build motion profile
        val motion = buildMotionProfile(vibe)

        // 5. Build typography
        val typography = buildTypography(vibe)

        // 6. Build art direction
        val artDirection = ArtDirection(
            sceneFamily = vibe.recommendedScene,
            lightingStyle = inferLighting(vibe),
            atmosphereWeight = if (vibe.darkness > 0.5f) 0.75f else 0.55f,
            vignetteStrength = if (vibe.darkness > 0.7f) 0.35f else 0.28f,
            filmGrain = true,
            visualTheme = buildThemeLine(title, artist, vibe)
        )

        // 7. Generate beats from lyrics
        val beats = generateBeats(lyricsData, vibe)

        val plan = ScenePlan(
            songId = songId,
            title = title,
            artist = artist,
            album = album,
            vibeProfile = vibe,
            artDirection = artDirection,
            palette = palette,
            typography = typography,
            motionProfile = motion,
            motifs = motifs,
            beats = beats
        )

        val gatedPlan = ScenePlanQualityGate.repairOrFallback(plan)
        planCache[songId] = gatedPlan
        Log.d("VANTA_SCENE_PLAN", "Generated plan for '$title' by '$artist': " +
            "family=${gatedPlan.artDirection.sceneFamily} motifs=${gatedPlan.motifs.size} beats=${gatedPlan.beats.size}")
        return gatedPlan
    }

    /**
     * Select motifs based on the vibe profile. Each scene family starts with
     * a base motif set, then the vibe dimensions add or remove motifs.
     */
    private fun selectMotifs(vibe: SongVibeProfile): List<VisualMotifType> {
        val motifs = mutableListOf<VisualMotifType>()

        // Base motifs per scene family
        when (vibe.recommendedScene) {
            LivingSceneType.INDUSTRIAL_STAGE -> motifs.addAll(listOf(
                VisualMotifType.STEEL_BEAMS, VisualMotifType.STAGE_TRUSS,
                VisualMotifType.SMOKE, VisualMotifType.SPARKS,
                VisualMotifType.STROBE_FLASH, VisualMotifType.CROWD_SILHOUETTE
            ))
            LivingSceneType.NIGHT_HIGHWAY_STAGE -> motifs.addAll(listOf(
                VisualMotifType.STARS, VisualMotifType.ROAD_LINES,
                VisualMotifType.HEADLIGHTS, VisualMotifType.NEON_SIGN,
                VisualMotifType.DUST_PARTICLES, VisualMotifType.SMOKE
            ))
            LivingSceneType.OPEN_ROAD_SKY -> motifs.addAll(listOf(
                VisualMotifType.CLOUDS, VisualMotifType.SUN_HORIZON,
                VisualMotifType.ROAD_LINES, VisualMotifType.GRASS_WISPS,
                VisualMotifType.DUST_PARTICLES
            ))
            LivingSceneType.CITY_REFLECTION -> motifs.addAll(listOf(
                VisualMotifType.CLOUDS, VisualMotifType.BUILDING_SILHOUETTE,
                VisualMotifType.WATER_REFLECTION
            ))
            LivingSceneType.NIGHT_CITY_PULSE -> motifs.addAll(listOf(
                VisualMotifType.BUILDING_SILHOUETTE, VisualMotifType.NEON_SIGN,
                VisualMotifType.CAR_STREAKS, VisualMotifType.WET_STREET_REFLECTION
            ))
            LivingSceneType.BASEMENT_STAGE -> motifs.addAll(listOf(
                VisualMotifType.CONCRETE_WALL, VisualMotifType.TORN_POSTER,
                VisualMotifType.GRAFFITI_MARKS, VisualMotifType.MICROPHONE_STAND
            ))
            LivingSceneType.OPEN_FIELD_ROAD -> motifs.addAll(listOf(
                VisualMotifType.SUN_HORIZON, VisualMotifType.GRASS_WISPS,
                VisualMotifType.FENCE_POSTS, VisualMotifType.ROAD_LINES,
                VisualMotifType.FIREFLIES
            ))
            LivingSceneType.OPEN_FIELD -> motifs.addAll(listOf(
                VisualMotifType.CLOUDS, VisualMotifType.GRASS_WISPS,
                VisualMotifType.SUN_HORIZON, VisualMotifType.FIREFLIES
            ))
            LivingSceneType.CHURCH_LIGHT -> motifs.addAll(listOf(
                VisualMotifType.STAGE_SPOTLIGHT_BEAM, VisualMotifType.DUST_PARTICLES,
                VisualMotifType.CHAPEL_SILHOUETTE
            ))
            LivingSceneType.BEDROOM_MEMORY -> motifs.addAll(listOf(
                VisualMotifType.BEDROOM_WINDOW, VisualMotifType.WARM_LAMP,
                VisualMotifType.DUST_PARTICLES
            ))
            LivingSceneType.STORM_WINDOW -> motifs.addAll(listOf(
                VisualMotifType.RAIN_DROPS, VisualMotifType.LIGHTNING_FLASH,
                VisualMotifType.FOG
            ))
            LivingSceneType.CITY_NIGHT -> motifs.addAll(listOf(
                VisualMotifType.BUILDING_SILHOUETTE, VisualMotifType.RAIN_DROPS,
                VisualMotifType.NEON_SIGN, VisualMotifType.WET_STREET_REFLECTION
            ))
            LivingSceneType.DESERT_HIGHWAY -> motifs.addAll(listOf(
                VisualMotifType.ROAD_LINES, VisualMotifType.SUN_HORIZON,
                VisualMotifType.DUST_PARTICLES, VisualMotifType.HEADLIGHTS
            ))
            LivingSceneType.MOVING_TRAIN -> motifs.addAll(listOf(
                VisualMotifType.TRAIN_CARS, VisualMotifType.DUST_PARTICLES
            ))
            LivingSceneType.STAGE_SPOTLIGHT -> motifs.addAll(listOf(
                VisualMotifType.STAGE_SPOTLIGHT_BEAM, VisualMotifType.DUST_PARTICLES,
                VisualMotifType.SMOKE
            ))
            LivingSceneType.PREMIUM_FALLBACK -> motifs.addAll(listOf(
                VisualMotifType.DUST_PARTICLES, VisualMotifType.WARM_LAMP
            ))
        }

        // Vibe-driven additions
        if (vibe.spiritual > 0.5f && VisualMotifType.CHAPEL_SILHOUETTE !in motifs) {
            motifs.add(VisualMotifType.CHAPEL_SILHOUETTE)
        }
        if (vibe.aggression > 0.6f && VisualMotifType.STROBE_FLASH !in motifs) {
            motifs.add(VisualMotifType.STROBE_FLASH)
        }
        if (vibe.aggression > 0.7f && VisualMotifType.SPARKS !in motifs) {
            motifs.add(VisualMotifType.SPARKS)
        }
        if (vibe.urban > 0.5f && VisualMotifType.CAR_STREAKS !in motifs) {
            motifs.add(VisualMotifType.CAR_STREAKS)
        }
        if (vibe.warmth > 0.6f && vibe.energy < 0.5f && VisualMotifType.FIREFLIES !in motifs) {
            motifs.add(VisualMotifType.FIREFLIES)
        }
        if (vibe.nostalgia > 0.5f && VisualMotifType.FILM_GRAIN !in motifs) {
            motifs.add(VisualMotifType.FILM_GRAIN)
        }
        if (vibe.darkness > 0.5f && VisualMotifType.SMOKE !in motifs) {
            motifs.add(VisualMotifType.SMOKE)
        }

        return motifs
    }

    private fun buildPalette(vibe: SongVibeProfile): ScenePalette {
        val base = ScenePalette.forScene(vibe.recommendedScene)
        // Future: modulate base palette with vibe dimensions (e.g., warmer highlight for high warmth)
        return base
    }

    private fun buildMotionProfile(vibe: SongVibeProfile): MotionProfile {
        return MotionProfile(
            driftSpeed = when {
                vibe.energy > 0.8f -> 1.5f
                vibe.energy > 0.5f -> 1.0f
                else -> 0.6f
            },
            pulseIntensity = vibe.energy,
            strobeEnabled = vibe.aggression > 0.6f || vibe.industrial > 0.5f,
            strobeDurationMs = if (vibe.aggression > 0.8f) 300 else 500,
            cameraShake = if (vibe.aggression > 0.7f) vibe.aggression * 0.3f else 0f,
            parallaxStrength = if (vibe.energy > 0.5f) 0.6f else 0.3f,
            transitionStyle = if (vibe.aggression > 0.5f) TransitionStyle.CUT else TransitionStyle.CROSSFADE
        )
    }

    private fun buildTypography(vibe: SongVibeProfile): LyricTypography {
        val size = when {
            vibe.aggression > 0.7f -> 28f
            vibe.energy < 0.3f -> 24f
            else -> 26f
        }
        return LyricTypography(
            fontWeight = when {
                vibe.aggression > 0.7f -> 800
                vibe.energy > 0.7f -> 700
                vibe.warmth > 0.6f -> 500
                else -> 600
            },
            fontSize = size,
            lineHeight = size * 1.18f,
            letterSpacing = if (vibe.aggression > 0.6f) 1.5f else 0f,
            textTransform = if (vibe.aggression > 0.8f) TextTransform.UPPERCASE else TextTransform.NONE
        )
    }

    private fun inferLighting(vibe: SongVibeProfile): LightingStyle = when {
        vibe.industrial > 0.6f || vibe.aggression > 0.7f -> LightingStyle.HARSH
        vibe.urban > 0.6f -> LightingStyle.NEON
        vibe.spiritual > 0.6f -> LightingStyle.NATURAL
        vibe.warmth > 0.6f && vibe.energy < 0.5f -> LightingStyle.WARM_LAMP
        vibe.darkness > 0.6f && vibe.energy < 0.4f -> LightingStyle.MOONLIGHT
        vibe.energy > 0.7f -> LightingStyle.STAGE
        else -> LightingStyle.NATURAL
    }

    private fun buildThemeLine(title: String, artist: String, vibe: SongVibeProfile): String {
        val family = vibe.recommendedScene.name.lowercase().replace("_", " ")
        val mood = when {
            vibe.aggression > 0.7f -> "aggressive, confrontational"
            vibe.spiritual > 0.6f -> "spiritual, transcendent"
            vibe.warmth > 0.6f -> "warm, nostalgic"
            vibe.darkness > 0.6f -> "dark, brooding"
            vibe.urban > 0.6f -> "urban, electric"
            else -> "cinematic, atmospheric"
        }
        return "$family — $mood"
    }

    /**
     * Generate beats from the real synced lyric timeline. Each timed row becomes
     * one beat so the rendered lyric matches playback instead of a coarse chunk.
     */
    private fun generateBeats(lyricsData: LyricsData?, vibe: SongVibeProfile): List<LivingLyricBeat> {
        val lines = lyricsData?.lines
            ?.filter { it.startTimeMs != null && it.text.isNotBlank() }
            ?.sortedBy { it.startTimeMs }
            ?: emptyList()

        if (lines.isEmpty()) {
            val endMs = lyricsData?.lines?.lastOrNull()?.let {
                it.endTimeMs ?: (it.startTimeMs ?: 0) + 30000
            } ?: 30000L
            return listOf(
                LivingLyricBeat(
                    startMs = 0, endMs = endMs,
                    sceneType = vibe.recommendedScene,
                    intensity = vibe.energy
                )
            )
        }

        val lineCounts = mutableMapOf<String, Int>()
        lines.forEach { line ->
            val normalized = line.text.trim().lowercase()
            if (normalized.isNotBlank()) {
                lineCounts[normalized] = (lineCounts[normalized] ?: 0) + 1
            }
        }

        return lines.mapIndexed { index, line ->
            val startMs = line.startTimeMs ?: 0L
            val nextStartMs = lines.getOrNull(index + 1)?.startTimeMs
            val explicitEndMs = line.endTimeMs?.takeIf { it > startMs }
            val endMs = explicitEndMs
                ?: nextStartMs?.takeIf { it > startMs }
                ?: startMs + 4_000L

            val normalized = line.text.trim().lowercase()
            val repeatedLineBoost = if ((lineCounts[normalized] ?: 0) > 1) 0.28f else 0f
            val lyricCueBoost = lyricSceneCueStrength(normalized) * 0.08f
            val position = index.toFloat() / (lines.size - 1).coerceAtLeast(1)
            val positionCurve = when {
                position < 0.12f -> 0.35f
                position > 0.88f -> 0.45f
                position in 0.42f..0.62f -> 0.7f
                else -> 0.5f
            }
            val intensity = ((positionCurve + repeatedLineBoost + lyricCueBoost)
                .coerceIn(0.22f, 1.0f) * vibe.energy.coerceAtLeast(0.35f))
                .coerceIn(0.18f, 1.0f)

            LivingLyricBeat(
                startMs = startMs,
                endMs = endMs,
                lyricLine = line.text,
                sceneType = vibe.recommendedScene,
                subject = inferLineSubject(normalized, vibe),
                setting = inferLineSetting(normalized, vibe),
                cameraMotion = inferLineCameraMotion(normalized, vibe),
                emotion = inferLineEmotion(normalized, vibe),
                colorMood = inferLineColorMood(normalized, vibe),
                visualMotif = inferLineVisualMotif(normalized, vibe),
                intensity = intensity
            )
        }
    }

    private fun lyricSceneCueStrength(line: String): Float {
        val cues = listOf(
            "sky", "heaven", "spirit", "angel", "god", "holy",
            "road", "highway", "drive", "headlight", "car", "engine",
            "city", "world", "power", "rule", "mirror", "glass",
            "fire", "smoke", "steel", "machine", "iron",
            "rain", "storm", "light", "church", "field"
        )
        return cues.count { line.contains(it) }.toFloat().coerceAtMost(4f)
    }

    private fun inferLineSubject(line: String, vibe: SongVibeProfile): String = when {
        line.hasAny("sky", "heaven", "spirit", "angel", "god", "holy") -> "spiritual horizon"
        line.hasAny("road", "highway", "drive", "ride", "car", "engine") -> "moving road"
        line.hasAny("city", "world", "power", "rule", "mirror", "glass") -> "reflected skyline"
        line.hasAny("fire", "smoke", "steel", "machine", "iron") -> "industrial stage"
        line.hasAny("rain", "storm", "tear", "cold") -> "storm glass"
        else -> vibe.cinematicKeywords.firstOrNull().orEmpty()
    }

    private fun inferLineSetting(line: String, vibe: SongVibeProfile): String = when {
        line.hasAny("church", "chapel", "pray", "altar") -> "chapel light"
        line.hasAny("field", "grass", "river", "country") -> "open field"
        line.hasAny("street", "city", "block", "club") -> "city street"
        line.hasAny("road", "highway", "drive") -> "highway"
        line.hasAny("stage", "guitar", "amp", "crowd") -> "stage"
        else -> vibe.recommendedScene.name.lowercase().replace("_", " ")
    }

    private fun inferLineCameraMotion(line: String, vibe: SongVibeProfile): String = when {
        line.hasAny("run", "drive", "ride", "move", "go") -> "forward push"
        line.hasAny("fall", "down", "cry", "tear") -> "slow drop"
        line.hasAny("sky", "heaven", "rise", "light") -> "slow lift"
        vibe.energy > 0.75f -> "handheld pulse"
        else -> "slow drift"
    }

    private fun inferLineEmotion(line: String, vibe: SongVibeProfile): String = when {
        line.hasAny("love", "baby", "heart") -> "romantic"
        line.hasAny("rule", "power", "world") -> "commanding"
        line.hasAny("pray", "spirit", "heaven", "soul") -> "transcendent"
        line.hasAny("fire", "burn", "hate", "fight") -> "confrontational"
        line.hasAny("cry", "pain", "cold", "alone") -> "melancholic"
        vibe.aggression > 0.65f -> "aggressive"
        vibe.nostalgia > 0.55f -> "nostalgic"
        else -> "cinematic"
    }

    private fun inferLineColorMood(line: String, vibe: SongVibeProfile): String = when {
        line.hasAny("fire", "burn", "smoke", "steel") -> "black red steel"
        line.hasAny("sky", "heaven", "light", "sun") -> "gold blue white"
        line.hasAny("road", "headlight", "drive") -> "amber asphalt"
        line.hasAny("city", "world", "glass", "mirror") -> "blue gold glass"
        line.hasAny("rain", "storm", "cold") -> "blue gray"
        vibe.darkness > 0.65f -> "dark high contrast"
        else -> "cinematic contrast"
    }

    private fun inferLineVisualMotif(line: String, vibe: SongVibeProfile): String = when {
        line.hasAny("sky", "heaven", "spirit", "angel", "god") -> "light beams and horizon"
        line.hasAny("road", "highway", "drive", "ride") -> "road lines and dust"
        line.hasAny("headlight", "car", "engine") -> "headlights and chrome"
        line.hasAny("city", "world", "power", "rule") -> "skyline reflections"
        line.hasAny("fire", "smoke", "steel", "machine") -> "smoke sparks and steel"
        line.hasAny("stage", "guitar", "amp") -> "stage lights"
        line.hasAny("rain", "storm") -> "rain on glass"
        else -> vibe.cinematicKeywords.take(2).joinToString(", ")
    }

    private fun String.hasAny(vararg keywords: String): Boolean =
        keywords.any { contains(it) }
}
