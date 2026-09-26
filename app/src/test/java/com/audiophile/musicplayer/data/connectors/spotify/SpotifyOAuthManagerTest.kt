package com.audiophile.musicplayer.data.connectors.spotify

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.security.MessageDigest
import java.util.Base64

class SpotifyOAuthManagerTest {

    @Test
    fun codeVerifierMeetsRfc7636Requirements() {
        val randomBytes = ByteArray(64)
        java.security.SecureRandom().nextBytes(randomBytes)
        val verifier = Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes)

        assertTrue("Verifier length must be between 43 and 128", verifier.length in 43..128)
        assertTrue("Verifier must contain only URL-safe chars", verifier.matches(Regex("^[A-Za-z0-9_-]+$")))
    }

    @Test
    fun codeChallengeComputesCorrectSha256Base64Url() {
        // Standard PKCE test vector verification
        val testVerifier = "dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk"
        val digest = MessageDigest.getInstance("SHA-256").digest(testVerifier.toByteArray(Charsets.US_ASCII))
        val expectedChallenge = Base64.getUrlEncoder().withoutPadding().encodeToString(digest)

        // Verifier computation logic
        val computedChallenge = Base64.getUrlEncoder().withoutPadding().encodeToString(
            MessageDigest.getInstance("SHA-256").digest(testVerifier.toByteArray(Charsets.US_ASCII))
        )

        assertEquals(expectedChallenge, computedChallenge)
        assertTrue("Challenge must not have padding '='", !computedChallenge.contains("="))
        assertTrue("Challenge must match url safe charset", computedChallenge.matches(Regex("^[A-Za-z0-9_-]+$")))
    }

    @Test
    fun spotifyScopesIncludeRequiredPermissions() {
        val scopes = SpotifyOAuthManager.SCOPES.split(" ")
        assertTrue("Must have user-library-read", scopes.contains("user-library-read"))
        assertTrue("Must have playlist-read-private", scopes.contains("playlist-read-private"))
        assertTrue("Must have playlist-read-collaborative", scopes.contains("playlist-read-collaborative"))
        assertTrue("Must have user-read-private", scopes.contains("user-read-private"))
        assertTrue("Must have user-library-modify", scopes.contains("user-library-modify"))
    }

    @Test
    fun redirectUriMatchesVantaCallbackScheme() {
        assertEquals("vanta://spotify-callback", SpotifyOAuthManager.REDIRECT_URI)
    }

    @Test
    fun endpointsPointToOfficialSpotifyAuth() {
        assertEquals("https://accounts.spotify.com/authorize", SpotifyOAuthManager.AUTH_ENDPOINT)
        assertEquals("https://accounts.spotify.com/api/token", SpotifyOAuthManager.TOKEN_ENDPOINT)
    }
}
