package com.audiophile.musicplayer.data.source.external

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TidalOauthTokensTest {
    @Test
    fun parsesPkceJsonAndBareAccessToken() {
        val nowSeconds = 1_778_000_000L
        val parsed = TidalOauthTokens.parse(
            """{"access_token":"access-1","refresh_token":"refresh-1","expires_at":$nowSeconds,"client_id":"tidal-client"}"""
        )
        assertEquals("access-1", parsed?.accessToken)
        assertEquals("refresh-1", parsed?.refreshToken)
        assertEquals("tidal-client", parsed?.clientId)
        assertEquals(nowSeconds * 1000, parsed?.expiresAtMs)
        assertEquals("bare-access-token-value", TidalOauthTokens.parse("bare-access-token-value")?.accessToken)
    }

    @Test
    fun refreshesWhenExpiryIsUnknownOrInsideSkew() {
        val withRefresh = TidalOauthTokens.Snapshot("access", "refresh-1", expiresAtMs = null)
        assertTrue(TidalOauthTokens.needsRefresh(withRefresh, 0L))
        assertFalse(TidalOauthTokens.needsRefresh(withRefresh.copy(expiresAtMs = 200_000L), 0L))
        assertTrue(TidalOauthTokens.needsRefresh(withRefresh.copy(expiresAtMs = 60_000L), 0L))
        assertFalse(TidalOauthTokens.needsRefresh(withRefresh.copy(refreshToken = null, expiresAtMs = 0L), 0L))
    }

    @Test
    fun mergeKeepsRefreshTokenAndWritesUnixExpiry() {
        val next = TidalOauthTokens.Snapshot(
            accessToken = "access-2",
            refreshToken = "refresh-2",
            expiresAtMs = 1_778_000_000_000L,
            clientId = "tidal-client"
        )
        val merged = TidalOauthTokens.mergeStoredJson(
            """{"access_token":"old","refresh_token":"refresh-1","scope":"r_usr"}""",
            next,
            nowMs = 1_778_000_000_000L - 3_600_000L
        )
        val parsed = TidalOauthTokens.parse(merged)
        assertEquals("access-2", parsed?.accessToken)
        assertEquals("refresh-2", parsed?.refreshToken)
        assertEquals(1_778_000_000_000L, parsed?.expiresAtMs)
        assertTrue(merged.contains("\"scope\":\"r_usr\""))
    }

    @Test
    fun parseRefreshResponseKeepsPreviousRefreshWhenProviderOmitsIt() {
        val previous = TidalOauthTokens.Snapshot("old", "refresh-1", clientId = "tidal-client")
        val next = TidalOauthTokens.parseRefreshResponse(
            """{"access_token":"access-2","expires_in":3600}""",
            previous,
            nowMs = 1_000_000L
        )
        assertEquals("access-2", next?.accessToken)
        assertEquals("refresh-1", next?.refreshToken)
        assertEquals(1_000_000L + 3_600_000L, next?.expiresAtMs)
    }
}
