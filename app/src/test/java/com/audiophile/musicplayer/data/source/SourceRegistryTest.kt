package com.audiophile.musicplayer.data.source

import org.junit.Assert.assertEquals
import org.junit.Test

class SourceRegistryTest {
    @Test
    fun extractExpiryFromUrl_readsQobuzEtspParam() {
        val expirySeconds = 1_782_543_609L
        val url = "https://streaming-qobuz-std.akamaized.net/file?uid=1&eid=2&etsp=$expirySeconds&hmac=abc"

        assertEquals(expirySeconds, SourceRegistry.extractExpiryFromUrl(url))
    }
}
