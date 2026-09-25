package com.audiophile.musicplayer.data.source.external

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class CommunityRelaySignerTest {
    private val now = Instant.parse("2026-09-02T00:00:00Z").toEpochMilli()
    private val session = CommunityRelaySession(
        sessionId = "session-id",
        sessionSecret = "session-secret",
        expiresAt = "2026-09-02T01:00:00Z",
        appVersion = "unknown",
        platform = "desktop"
    )

    @Test
    fun rejectsExpiredOrMalformedSessions() {
        assertTrue(CommunityRelaySigner.isUsable(session, now))
        assertFalse(CommunityRelaySigner.isUsable(session.copy(expiresAt = "2026-09-01T23:00:00Z"), now))
        assertFalse(CommunityRelaySigner.isUsable(session.copy(expiresAt = "not-a-date"), now))
    }

    @Test
    fun signatureIsDeterministicForFixedTimeAndNonceButChangesWithBody() {
        val first = CommunityRelaySigner.signRequest("{\"id\":\"1234\",\"quality\":\"24\"}", session, now, "00112233445566778899aabb")
        val second = CommunityRelaySigner.signRequest("{\"id\":\"1234\",\"quality\":\"24\"}", session, now, "00112233445566778899aabb")
        val changed = CommunityRelaySigner.signRequest("{\"id\":\"1234\",\"quality\":\"16\"}", session, now, "00112233445566778899aabb")

        assertEquals(first["X-Sig-Signature"], second["X-Sig-Signature"])
        assertNotEquals(first["X-Sig-Signature"], changed["X-Sig-Signature"])
        assertEquals("wyFhdDHDRrewLZy7FI4o7kym6DpXF28sjZk4UG5q8Ps", first["X-Sig-Signature"])
        assertEquals("session-id", first["X-Sig-Session"])
        assertEquals("00112233445566778899aabb", first["X-Sig-Nonce"])
    }
}
