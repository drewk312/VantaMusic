package com.audiophile.musicplayer.data.dj

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DjStructuredResponseParserTest {

    @Test
    fun parse_validJson_extractsMessageMoodAndActions() {
        val raw = """
            {
              "message": "Late-night mode. I'll keep this smooth and clean.",
              "mood": "late_night",
              "energy": 0.45,
              "actions": [
                {
                  "type": "ADJUST_QUEUE",
                  "direction": "smooth_dark_clean"
                }
              ],
              "display": {
                "cardType": "dj_moment",
                "accent": "calm_green",
                "durationMs": 6000
              }
            }
        """.trimIndent()

        val parsed = DjStructuredResponseParser.parse(raw)

        assertNotNull(parsed)
        assertEquals("Late-night mode. I'll keep this smooth and clean.", parsed!!.message)
        assertEquals("late_night", parsed.mood)
        assertEquals(0.45f, parsed.energy)
        assertEquals(DjActionType.ADJUST_QUEUE, parsed.primaryAction?.type)
        assertEquals("smooth_dark_clean", parsed.primaryAction?.direction)
        assertEquals("dj_moment", parsed.display?.cardType)
    }

    @Test
    fun parse_embeddedJsonInProse_stillParsesObject() {
        val raw = """Sure — {"message":"Good pick.","actions":[{"type":"FIND_SIMILAR"}]}"""

        val parsed = DjStructuredResponseParser.parse(raw)

        assertNotNull(parsed)
        assertEquals("Good pick.", parsed!!.message)
        assertEquals(DjActionType.FIND_SIMILAR, parsed.primaryAction?.type)
    }

    @Test
    fun parse_invalidJson_returnsNull() {
        assertNull(DjStructuredResponseParser.parse("not json at all"))
        assertNull(DjStructuredResponseParser.parse(""))
        assertNull(DjStructuredResponseParser.parse(null))
    }

    @Test
    fun parse_unknownActionType_mapsToUnknown() {
        val raw = """{"message":"ok","actions":[{"type":"PLAY_FAKE_TRACK"}]}"""
        val parsed = DjStructuredResponseParser.parse(raw)

        assertNotNull(parsed)
        assertTrue(parsed!!.actions.any { it.type == DjActionType.UNKNOWN })
    }
}
