package com.audiophile.musicplayer.data.source.external

import org.junit.Assert.assertEquals
import org.junit.Test

class SpotiFlacEndpointsTest {
    @Test
    fun buildCommunityDownloadPayload_includesService() {
        assertEquals(
            """{"id":"42","quality":"24","service":"qobuz"}""",
            SpotiFlacEndpoints.buildCommunityDownloadPayload("42", "27", "qobuz")
        )
    }

    @Test
    fun inferStreamService_mapsPrefixedIds() {
        assertEquals("tidal", SpotiFlacEndpoints.inferStreamService("tidal:12345"))
        assertEquals("qobuz", SpotiFlacEndpoints.inferStreamService("qobuz:67890"))
        assertEquals("deezer", SpotiFlacEndpoints.inferStreamService("deezer:908604612"))
        assertEquals("deezer", SpotiFlacEndpoints.inferStreamService("908604612"))
    }
}
