package com.audiophile.musicplayer.ui.livinglyrics

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.withTransform
import com.audiophile.musicplayer.ui.livinglyrics.VisualMotifs.drawSceneBackplate
import com.audiophile.musicplayer.ui.livinglyrics.VisualMotifs.drawAtmosphereHaze
import com.audiophile.musicplayer.ui.livinglyrics.VisualMotifs.drawBloomGlow
import com.audiophile.musicplayer.ui.livinglyrics.VisualMotifs.drawCinematicVignette
import com.audiophile.musicplayer.ui.livinglyrics.VisualMotifs.drawBuildingSilhouette
import com.audiophile.musicplayer.ui.livinglyrics.VisualMotifs.drawCarStreaks
import com.audiophile.musicplayer.ui.livinglyrics.VisualMotifs.drawChapelSilhouette
import com.audiophile.musicplayer.ui.livinglyrics.VisualMotifs.drawClouds
import com.audiophile.musicplayer.ui.livinglyrics.VisualMotifs.drawConcreteWall
import com.audiophile.musicplayer.ui.livinglyrics.VisualMotifs.drawCrowdSilhouette
import com.audiophile.musicplayer.ui.livinglyrics.VisualMotifs.drawDustParticles
import com.audiophile.musicplayer.ui.livinglyrics.VisualMotifs.drawFencePosts
import com.audiophile.musicplayer.ui.livinglyrics.VisualMotifs.drawFieldGround
import com.audiophile.musicplayer.ui.livinglyrics.VisualMotifs.drawFireflies
import com.audiophile.musicplayer.ui.livinglyrics.VisualMotifs.drawGraffitiMarks
import com.audiophile.musicplayer.ui.livinglyrics.VisualMotifs.drawGrassWisps
import com.audiophile.musicplayer.ui.livinglyrics.VisualMotifs.drawHarshLamp
import com.audiophile.musicplayer.ui.livinglyrics.VisualMotifs.drawHeadlights
import com.audiophile.musicplayer.ui.livinglyrics.VisualMotifs.drawHorizonLine
import com.audiophile.musicplayer.ui.livinglyrics.VisualMotifs.drawLightningFlash
import com.audiophile.musicplayer.ui.livinglyrics.VisualMotifs.drawMicrophoneStand
import com.audiophile.musicplayer.ui.livinglyrics.VisualMotifs.drawNeonSign
import com.audiophile.musicplayer.ui.livinglyrics.VisualMotifs.drawRainDrops
import com.audiophile.musicplayer.ui.livinglyrics.VisualMotifs.drawRedStageBeams
import com.audiophile.musicplayer.ui.livinglyrics.VisualMotifs.drawRoadLines
import com.audiophile.musicplayer.ui.livinglyrics.VisualMotifs.drawSkyGradient
import com.audiophile.musicplayer.ui.livinglyrics.VisualMotifs.drawSmoke
import com.audiophile.musicplayer.ui.livinglyrics.VisualMotifs.drawSparks
import com.audiophile.musicplayer.ui.livinglyrics.VisualMotifs.drawSpotlightBeam
import com.audiophile.musicplayer.ui.livinglyrics.VisualMotifs.drawStageFloor
import com.audiophile.musicplayer.ui.livinglyrics.VisualMotifs.drawStageTruss
import com.audiophile.musicplayer.ui.livinglyrics.VisualMotifs.drawStars
import com.audiophile.musicplayer.ui.livinglyrics.VisualMotifs.drawSteelBeams
import com.audiophile.musicplayer.ui.livinglyrics.VisualMotifs.drawStreetGround
import com.audiophile.musicplayer.ui.livinglyrics.VisualMotifs.drawStreetlights
import com.audiophile.musicplayer.ui.livinglyrics.VisualMotifs.drawStrobeFlash
import com.audiophile.musicplayer.ui.livinglyrics.VisualMotifs.drawSunHorizon
import com.audiophile.musicplayer.ui.livinglyrics.VisualMotifs.drawTornPosters
import com.audiophile.musicplayer.ui.livinglyrics.VisualMotifs.drawFilmGrain
import com.audiophile.musicplayer.ui.livinglyrics.VisualMotifs.drawFog
import com.audiophile.musicplayer.ui.livinglyrics.VisualMotifs.drawVignette
import com.audiophile.musicplayer.ui.livinglyrics.VisualMotifs.drawWarmLamp
import com.audiophile.musicplayer.ui.livinglyrics.VisualMotifs.drawWaterReflection
import com.audiophile.musicplayer.ui.livinglyrics.VisualMotifs.drawWetStreetReflection
import com.audiophile.musicplayer.ui.livinglyrics.VisualMotifs.drawBedroomWindow
import kotlin.math.PI
import kotlin.math.sin

/**
 * The generative scene renderer. Takes a [ScenePlan] and the current beat,
 * then renders layered cinematic visuals from reusable motifs.
 *
 * Rendering order (back-to-front, with parallax):
 *   0. Cinematic backplate (scene-specific procedural background)
 *   1. Atmosphere / haze / stars
 *   2. Midground structures and environment
 *   3. Foreground objects, particles and light
 *   4. Beat-reactive color wash
 *   5. Bloom glow, vignette, film grain
 *
 * The lyric layer is handled separately by the parent composable.
 */
@Composable
fun SceneComposer(
    plan: ScenePlan,
    currentBeat: LivingLyricBeat?,
    energy: Float,
    modifier: Modifier = Modifier
) {
    val palette = plan.palette
    val motion = plan.motionProfile
    val motifs = plan.motifs
    val intensity = ((currentBeat?.intensity ?: 0.5f) * (0.6f + energy * 0.4f)).coerceIn(0.2f, 1f)
    val sceneFamily = plan.artDirection.sceneFamily

    // Animation clocks — different speeds for different visual layers
    val transition = rememberInfiniteTransition(label = "sceneComposer")

    val slowDrift by transition.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(
            tween((24000 / motion.driftSpeed).toInt().coerceAtLeast(6000), easing = LinearEasing),
            RepeatMode.Reverse
        ),
        label = "slowDrift"
    )
    val mediumCycle by transition.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(
            tween((14000 / motion.driftSpeed).toInt().coerceAtLeast(3500), easing = LinearEasing),
            RepeatMode.Reverse
        ),
        label = "mediumCycle"
    )
    val fastPulse by transition.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(
            tween(
                if (motion.strobeEnabled) motion.strobeDurationMs else 700,
                easing = LinearEasing
            ),
            RepeatMode.Restart
        ),
        label = "fastPulse"
    )

    // Camera drift for parallax
    val cameraX by transition.animateFloat(
        initialValue = -1f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(17000, easing = LinearEasing), RepeatMode.Reverse),
        label = "camX"
    )
    val cameraY by transition.animateFloat(
        initialValue = -1f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(21000, easing = LinearEasing), RepeatMode.Reverse),
        label = "camY"
    )
    val parallaxBase = motion.parallaxStrength * 10f
    val driftX = sin(cameraX * PI.toFloat()) * parallaxBase
    val driftY = sin(cameraY * PI.toFloat() * 0.7f) * parallaxBase * 0.6f

    // Beat-reactive light wash
    val reactiveTarget = (intensity * energy).coerceIn(0f, 1f)
    val reactiveAlpha by animateFloatAsState(
        targetValue = reactiveTarget,
        animationSpec = tween(350),
        label = "reactiveAlpha"
    )
    val washColor = remember(currentBeat, palette) {
        if ((currentBeat?.intensity ?: 0.5f) > 0.6f) palette.secondary else palette.accent
    }
    val washAlpha by animateFloatAsState(
        targetValue = if (washColor == palette.secondary) 0.06f else 0.04f,
        animationSpec = tween(600),
        label = "washAlpha"
    )

    Box(modifier = modifier.fillMaxSize()) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            val horizonY = h * environmentHorizon(sceneFamily)
            val floorY = h * environmentFloor(sceneFamily)

            // ══ Layer 0: scene-specific cinematic backplate ═══════════
            withTransform({ translate(left = driftX * 0.15f, top = driftY * 0.12f) }) {
                drawSceneBackplate(sceneFamily, mediumCycle, palette, intensity)
            }

            // ══ Layer 1: atmosphere and distant light ═══════════════════
            if (plan.artDirection.atmosphereWeight > 0f) {
                drawAtmosphereHaze(palette, intensity * plan.artDirection.atmosphereWeight)
            }
            if (VisualMotifType.STARS in motifs) {
                drawStars(palette, intensity)
            }

            // ══ Layer 2: midground (structures, sky, environment) ═════
            withTransform({ translate(left = driftX * 0.45f, top = driftY * 0.25f) }) {
                if (VisualMotifType.CLOUDS in motifs) {
                    drawClouds(slowDrift, palette, intensity)
                }
                if (VisualMotifType.SUN_HORIZON in motifs) {
                    drawSunHorizon(mediumCycle, palette, intensity, horizonY)
                }
                if (VisualMotifType.BUILDING_SILHOUETTE in motifs) {
                    drawBuildingSilhouette(palette, mediumCycle, intensity, horizonY)
                }
                if (VisualMotifType.CHAPEL_SILHOUETTE in motifs) {
                    drawChapelSilhouette(palette, intensity)
                }
                if (VisualMotifType.STEEL_BEAMS in motifs) {
                    drawSteelBeams(palette, intensity)
                }
                if (VisualMotifType.STAGE_TRUSS in motifs) {
                    drawStageTruss(palette, intensity)
                }
                if (VisualMotifType.FENCE_POSTS in motifs) {
                    drawFencePosts(palette, intensity, horizonY)
                }
                if (VisualMotifType.BEDROOM_WINDOW in motifs) {
                    drawBedroomWindow(palette, mediumCycle, intensity)
                }

                // Ground / floor / water
                when (sceneFamily) {
                    LivingSceneType.INDUSTRIAL_STAGE,
                    LivingSceneType.NIGHT_HIGHWAY_STAGE,
                    LivingSceneType.STAGE_SPOTLIGHT,
                    LivingSceneType.BASEMENT_STAGE -> drawStageFloor(palette, floorY)
                    LivingSceneType.NIGHT_CITY_PULSE,
                    LivingSceneType.CITY_NIGHT -> drawStreetGround(palette, floorY)
                    LivingSceneType.OPEN_FIELD_ROAD,
                    LivingSceneType.OPEN_ROAD_SKY,
                    LivingSceneType.OPEN_FIELD,
                    LivingSceneType.DESERT_HIGHWAY -> drawFieldGround(palette, horizonY)
                    else -> {}
                }

                if (sceneFamily in listOf(
                        LivingSceneType.OPEN_ROAD_SKY, LivingSceneType.OPEN_FIELD_ROAD,
                        LivingSceneType.CITY_REFLECTION, LivingSceneType.DESERT_HIGHWAY
                    )) {
                    drawHorizonLine(palette, horizonY, intensity)
                }

                if (VisualMotifType.WATER_REFLECTION in motifs) {
                    drawWaterReflection(mediumCycle, palette, intensity, horizonY)
                }
            }

            // ══ Layer 3: foreground (objects, particles, light) ════════
            withTransform({ translate(left = driftX * 0.85f, top = driftY * 0.55f) }) {
                if (VisualMotifType.ROAD_LINES in motifs) {
                    drawRoadLines(slowDrift, palette, intensity, horizonY)
                }
                if (VisualMotifType.MICROPHONE_STAND in motifs) {
                    drawMicrophoneStand(palette, intensity, floorY)
                }
                if (VisualMotifType.TORN_POSTER in motifs) {
                    drawTornPosters(palette, intensity)
                }
                if (VisualMotifType.GRAFFITI_MARKS in motifs) {
                    drawGraffitiMarks(palette, intensity)
                }
                if (VisualMotifType.CROWD_SILHOUETTE in motifs) {
                    drawCrowdSilhouette(palette, intensity, floorY + (h - floorY) * 0.3f)
                }
                if (VisualMotifType.GRASS_WISPS in motifs) {
                    drawGrassWisps(mediumCycle, palette, intensity, horizonY)
                }

                // Atmosphere particles
                if (VisualMotifType.SMOKE in motifs) {
                    drawSmoke(slowDrift, palette, intensity, floorY)
                }
                if (VisualMotifType.DUST_PARTICLES in motifs) {
                    drawDustParticles(slowDrift, palette, intensity)
                }
                if (VisualMotifType.RAIN_DROPS in motifs) {
                    drawRainDrops(fastPulse, palette, intensity)
                }
                if (VisualMotifType.FOG in motifs) {
                    drawFog(palette, intensity)
                }

                // Light
                if (VisualMotifType.STAGE_SPOTLIGHT_BEAM in motifs) {
                    drawSpotlightBeam(mediumCycle, palette, intensity, floorY)
                }
                if (VisualMotifType.HEADLIGHTS in motifs) {
                    drawHeadlights(mediumCycle, palette, intensity, horizonY)
                }
                if (VisualMotifType.NEON_SIGN in motifs) {
                    drawNeonSign(mediumCycle, palette, intensity)
                }
                if (VisualMotifType.WARM_LAMP in motifs) {
                    drawWarmLamp(mediumCycle, palette, intensity)
                }
                if (VisualMotifType.STROBE_FLASH in motifs && motion.strobeEnabled) {
                    drawStrobeFlash(fastPulse, intensity)
                }
                if (VisualMotifType.LIGHTNING_FLASH in motifs) {
                    drawLightningFlash(fastPulse, intensity)
                }

                // Family-specific light accents
                if (sceneFamily == LivingSceneType.INDUSTRIAL_STAGE) {
                    drawRedStageBeams(mediumCycle, palette, intensity, floorY)
                }
                if (sceneFamily in listOf(LivingSceneType.NIGHT_CITY_PULSE, LivingSceneType.CITY_NIGHT)) {
                    drawStreetlights(mediumCycle, palette, intensity, floorY)
                }
                if (sceneFamily == LivingSceneType.BASEMENT_STAGE) {
                    drawHarshLamp(fastPulse, palette, intensity, floorY)
                }

                // Detail particles and reflections
                if (VisualMotifType.SPARKS in motifs) {
                    drawSparks(fastPulse, palette, intensity)
                }
                if (VisualMotifType.FIREFLIES in motifs) {
                    drawFireflies(slowDrift, palette, intensity)
                }
                if (VisualMotifType.WET_STREET_REFLECTION in motifs) {
                    drawWetStreetReflection(mediumCycle, palette, intensity, floorY)
                }
                if (VisualMotifType.CAR_STREAKS in motifs) {
                    drawCarStreaks(slowDrift, palette, intensity, floorY + (h - floorY) * 0.3f)
                }
            }

            // ══ Layer 4: beat-reactive color wash (smooth crossfade feel)
            drawRect(
                brush = androidx.compose.ui.graphics.Brush.radialGradient(
                    colors = listOf(
                        washColor.copy(alpha = washAlpha * reactiveAlpha),
                        Color.Transparent
                    ),
                    center = Offset(w * 0.5f, h * 0.4f),
                    radius = w * 0.7f
                ),
                topLeft = Offset.Zero,
                size = size
            )

            // ══ Layer 5: bloom, vignette, film grain ═══════════════════
            drawBloomGlow(palette, reactiveAlpha, Offset(w * 0.5f, h * 0.38f), w * 0.55f)
            drawCinematicVignette(plan.artDirection.vignetteStrength)
            if (VisualMotifType.FILM_GRAIN in motifs || plan.artDirection.filmGrain) {
                drawFilmGrain(slowDrift, palette, intensity)
            }
        }
    }
}

/** Horizon line position varies by scene family. */
private fun environmentHorizon(family: LivingSceneType): Float = when (family) {
    LivingSceneType.OPEN_ROAD_SKY -> 0.45f
    LivingSceneType.OPEN_FIELD_ROAD -> 0.45f
    LivingSceneType.CITY_REFLECTION -> 0.48f
    LivingSceneType.DESERT_HIGHWAY -> 0.42f
    LivingSceneType.OPEN_FIELD -> 0.4f
    LivingSceneType.NIGHT_HIGHWAY_STAGE -> 0.4f
    else -> 0.5f
}

/** Floor/stage line position varies by scene family. */
private fun environmentFloor(family: LivingSceneType): Float = when (family) {
    LivingSceneType.INDUSTRIAL_STAGE -> 0.78f
    LivingSceneType.BASEMENT_STAGE -> 0.75f
    LivingSceneType.STAGE_SPOTLIGHT -> 0.75f
    LivingSceneType.NIGHT_HIGHWAY_STAGE -> 0.85f
    LivingSceneType.NIGHT_CITY_PULSE -> 0.72f
    LivingSceneType.CITY_NIGHT -> 0.85f
    else -> 0.8f
}

/** Sky gradient bands per scene family (kept for legacy callers). */
private fun skyGradientForFamily(family: LivingSceneType, palette: ScenePalette): List<Color> = when (family) {
    LivingSceneType.NIGHT_HIGHWAY_STAGE,
    LivingSceneType.INDUSTRIAL_STAGE -> listOf(
        Color(0xFF050508), Color(0xFF0A0A14), Color(0xFF0A0A18),
        palette.primary.copy(alpha = 0.6f), palette.secondary.copy(alpha = 0.25f)
    )
    LivingSceneType.OPEN_ROAD_SKY -> listOf(
        Color(0xFF1A2440), Color(0xFF2A3A5A), Color(0xFF4A6A8A),
        Color(0xFF8AA0B8), Color(0xFFC0B890), palette.secondary
    )
    LivingSceneType.CITY_REFLECTION -> listOf(
        Color(0xFF2A4A6A), Color(0xFF3A5A7A), Color(0xFF4A7AA8),
        Color(0xFF8AA0B8), Color(0xFFC0B890), palette.accent
    )
    LivingSceneType.OPEN_FIELD_ROAD -> listOf(
        Color(0xFF3A4060), Color(0xFF4A5070), Color(0xFF6A5A60),
        Color(0xFF8A6850), Color(0xFFA87840), palette.secondary
    )
    LivingSceneType.DESERT_HIGHWAY -> listOf(
        Color(0xFF3A4A5A), Color(0xFF6A7A8A), Color(0xFF8A8A7A),
        Color(0xFFAA9A70), palette.secondary
    )
    LivingSceneType.STORM_WINDOW -> listOf(
        Color(0xFF0A0A14), Color(0xFF1A1A2A), Color(0xFF2A2A3A),
        palette.secondary.copy(alpha = 0.35f)
    )
    LivingSceneType.NIGHT_CITY_PULSE,
    LivingSceneType.CITY_NIGHT -> listOf(
        Color(0xFF080810), Color(0xFF080810), palette.primary.copy(alpha = 0.5f)
    )
    LivingSceneType.BASEMENT_STAGE -> listOf(
        Color(0xFF1A1510), Color(0xFF2A2520), palette.primary.copy(alpha = 0.55f),
        palette.secondary.copy(alpha = 0.15f)
    )
    LivingSceneType.BEDROOM_MEMORY -> listOf(
        Color(0xFF1A1510), Color(0xFF2A2520), palette.secondary.copy(alpha = 0.12f)
    )
    LivingSceneType.CHURCH_LIGHT -> listOf(
        Color(0xFF1A1510), Color(0xFF2A2520), Color(0xFF3A3530),
        palette.secondary.copy(alpha = 0.18f)
    )
    LivingSceneType.STAGE_SPOTLIGHT -> listOf(
        Color(0xFF0A0A0A), Color(0xFF1A1A1A), palette.primary.copy(alpha = 0.45f)
    )
    LivingSceneType.PREMIUM_FALLBACK -> listOf(
        Color(0xFF0A0A0A), Color(0xFF1A1815), palette.secondary.copy(alpha = 0.12f)
    )
    LivingSceneType.MOVING_TRAIN -> listOf(
        Color(0xFF1A2028), Color(0xFF2A3038), Color(0xFF3A4048),
        palette.secondary.copy(alpha = 0.18f)
    )
    else -> listOf(palette.background, palette.primary.copy(alpha = 0.5f))
}