package com.audiophile.musicplayer.search

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MusicTypeaheadTest {
    @Test
    fun `short prefix is completed from real music catalog by relevance`() {
        val response = """
            {
              "data": [
                {"title":"Soleil Bleu","rank":993960,"artist":{"name":"Bleu Soleil"}},
                {"title":"BLOQUÉ","rank":996189,"artist":{"name":"GIMS"}},
                {"title":"Blinding Lights","rank":984833,"artist":{"name":"The Weeknd"}},
                {"title":"bloodstream","rank":914020,"artist":{"name":"Alyssa Grace"}}
              ]
            }
        """.trimIndent()

        val suggestions = MusicTypeahead.suggestions("bl", response)

        assertEquals("BLOQUÉ", suggestions.first())
        assertTrue("Blinding Lights" in suggestions)
        assertFalse("bluey" in suggestions)
    }

    @Test
    fun `artist prefixes and library history are supported without fabricated tracks`() {
        val response = """
            {"data":[{"title":"The Less I Know The Better","rank":900000,"artist":{"name":"Tame Impala"}}]}
        """.trimIndent()

        assertEquals(
            listOf("Tame Impala"),
            MusicTypeahead.suggestions("tame i", response)
        )
        assertEquals(
            "Blackbird",
            MusicTypeahead.suggestions("bl", response, libraryLabels = listOf("Blackbird")).first()
        )
    }

    @Test
    fun `malformed catalog responses fail empty instead of leaking generic web suggestions`() {
        assertTrue(MusicTypeahead.suggestions("bl", "not-json").isEmpty())
        assertTrue(MusicTypeahead.suggestions("b", "{\"data\":[]}").isEmpty())
    }

    @Test
    fun `inline prediction remainder continues the typed prefix`() {
        assertEquals("ing Lights", MusicTypeahead.completionRemainder("Blind", "Blinding Lights"))
        assertTrue(MusicTypeahead.matchesPrefix("Beyoncé", "beyon"))
        assertEquals("cé", MusicTypeahead.completionRemainder("Beyon", "Beyoncé"))
    }
}
