package com.audiophile.musicplayer.data.importer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.StringReader

class BoundedTextReaderTest {
    @Test
    fun `returns content at the configured limit`() {
        assertEquals("12345", StringReader("12345").readBoundedText(maxChars = 5))
    }

    @Test
    fun `rejects content over the configured limit`() {
        assertNull(StringReader("123456").readBoundedText(maxChars = 5))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `rejects non-positive limits`() {
        StringReader("value").readBoundedText(maxChars = 0)
    }
}
