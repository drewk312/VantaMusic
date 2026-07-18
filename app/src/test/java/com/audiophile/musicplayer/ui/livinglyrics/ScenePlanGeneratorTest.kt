package com.audiophile.musicplayer.ui.livinglyrics

import com.audiophile.musicplayer.data.lyrics.LyricsData
import com.audiophile.musicplayer.data.lyrics.LyricsLine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ScenePlanGeneratorTest {
    @Before
    fun setUp() {
        ScenePlanGenerator.clearCache()
    }

    @Test
    fun `named songs generate distinct scene plans`() {
        val duHast = plan("du-hast", "Du hast", "Rammstein", "Du hast mich")
        val gimme = plan("gimme", "Gimme All Your Lovin'", "ZZ Top", "headlights on the road")
        val spirit = plan("spirit", "Spirit In The Sky", "Norman Greenbaum", "spirit in the sky")
        val ruleWorld = plan("rule-world", "Everybody Wants To Rule The World", "Tears For Fears", "rule the world")
        val victoryLap = plan("victory-lap-five", "Victory Lap Five", "Fred again..", "club lights on the block")

        assertEquals(LivingSceneType.INDUSTRIAL_STAGE, duHast.artDirection.sceneFamily)
        assertTrue(duHast.motifs.containsAll(listOf(
            VisualMotifType.STEEL_BEAMS,
            VisualMotifType.SMOKE,
            VisualMotifType.SPARKS,
            VisualMotifType.STROBE_FLASH,
            VisualMotifType.STAGE_TRUSS
        )))

        assertEquals(LivingSceneType.NIGHT_HIGHWAY_STAGE, gimme.artDirection.sceneFamily)
        assertTrue(gimme.motifs.containsAll(listOf(
            VisualMotifType.ROAD_LINES,
            VisualMotifType.HEADLIGHTS,
            VisualMotifType.DUST_PARTICLES
        )))

        assertEquals(LivingSceneType.OPEN_ROAD_SKY, spirit.artDirection.sceneFamily)
        assertTrue(spirit.motifs.containsAll(listOf(
            VisualMotifType.CLOUDS,
            VisualMotifType.SUN_HORIZON,
            VisualMotifType.GRASS_WISPS
        )))

        assertEquals(LivingSceneType.CITY_REFLECTION, ruleWorld.artDirection.sceneFamily)
        assertTrue(ruleWorld.motifs.containsAll(listOf(
            VisualMotifType.BUILDING_SILHOUETTE,
            VisualMotifType.WATER_REFLECTION,
            VisualMotifType.CLOUDS
        )))

        assertEquals(LivingSceneType.NIGHT_CITY_PULSE, victoryLap.artDirection.sceneFamily)
        assertTrue(victoryLap.motifs.containsAll(listOf(
            VisualMotifType.BUILDING_SILHOUETTE,
            VisualMotifType.NEON_SIGN,
            VisualMotifType.CAR_STREAKS
        )))

        assertNotEquals(duHast.artDirection.sceneFamily, gimme.artDirection.sceneFamily)
        assertNotEquals(gimme.artDirection.sceneFamily, spirit.artDirection.sceneFamily)
        assertNotEquals(spirit.artDirection.sceneFamily, ruleWorld.artDirection.sceneFamily)
    }

    @Test
    fun `synced lyric lines become matching scene beats`() {
        val lyrics = LyricsData(
            trackKey = "line-sync",
            isSynced = true,
            providerId = "test",
            lines = listOf(
                LyricsLine(startTimeMs = 0L, endTimeMs = null, text = "first line on the road"),
                LyricsLine(startTimeMs = 1_500L, endTimeMs = null, text = "second line under the sky"),
                LyricsLine(startTimeMs = 3_000L, endTimeMs = 4_500L, text = "third line in the light")
            )
        )

        val generated = ScenePlanGenerator.generate(
            songId = "line-sync",
            title = "Line Sync",
            artist = "Unknown",
            album = null,
            lyricsData = lyrics
        )

        assertEquals(listOf(
            "first line on the road",
            "second line under the sky",
            "third line in the light"
        ), generated.beats.map { it.lyricLine })
        assertEquals(0L, generated.beats[0].startMs)
        assertEquals(1_500L, generated.beats[0].endMs)
        assertEquals(3_000L, generated.beats[2].startMs)
        assertEquals(4_500L, generated.beats[2].endMs)
    }

    @Test
    fun `strong lyric imagery can override artist fingerprint scene`() {
        val generated = plan(
            trackKey = "lyric-override",
            title = "Gentle Words",
            artist = "ZZ Top",
            lyricText = "heaven spirit sky angel holy light"
        )

        assertEquals(LivingSceneType.OPEN_ROAD_SKY, generated.artDirection.sceneFamily)
        assertTrue(generated.motifs.contains(VisualMotifType.SUN_HORIZON))
        assertTrue(generated.beats.first().visualMotif.contains("horizon"))
    }

    private fun plan(trackKey: String, title: String, artist: String, lyricText: String): ScenePlan =
        ScenePlanGenerator.generate(
            songId = trackKey,
            title = title,
            artist = artist,
            album = null,
            lyricsData = LyricsData(
                trackKey = trackKey,
                isSynced = true,
                providerId = "test",
                lines = listOf(
                    LyricsLine(startTimeMs = 0L, endTimeMs = 10_000L, text = lyricText),
                    LyricsLine(startTimeMs = 10_000L, endTimeMs = 20_000L, text = lyricText)
                )
            )
        )
}
