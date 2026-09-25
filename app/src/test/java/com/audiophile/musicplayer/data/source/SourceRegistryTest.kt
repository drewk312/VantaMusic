package com.audiophile.musicplayer.data.source

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import java.time.OffsetDateTime
import java.time.ZoneOffset

class SourceRegistryTest {
    @Test
    fun extractExpiryFromUrl_readsQobuzEtspParam() {
        val expirySeconds = 1_782_543_609L
        val url = "https://streaming-qobuz-std.akamaized.net/file?uid=1&eid=2&etsp=$expirySeconds&hmac=abc"

        assertEquals(expirySeconds, SourceRegistry.extractExpiryFromUrl(url))
    }

    @Test
    fun extractExpiryFromUrl_readsNeteasePathIssuedAtPlusFifteenMinutes() {
        val url = "https://m801.music.126.net/20260912091831/c62bb141.flac"
        val expected = OffsetDateTime.of(2026, 9, 12, 9, 18, 31, 0, ZoneOffset.ofHours(8))
            .toInstant()
            .toEpochMilli() + 15L * 60_000L

        assertEquals(expected, SourceRegistry.extractExpiryFromUrl(url))
    }

    @Test
    fun inferTtlFromHost_treatsNeteaseAsShortLived() {
        val ttl = SourceRegistry.inferTtlFromHost("https://m801.music.126.net/c62bb141.flac")
        assertNotNull(ttl)
        assertEquals(480_000L, ttl)
    }
}
