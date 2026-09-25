package com.audiophile.musicplayer.data.source.external

import java.security.MessageDigest
import java.security.SecureRandom
import java.text.SimpleDateFormat
import java.util.Base64
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

data class CommunityRelaySession(
    val sessionId: String,
    val sessionSecret: String,
    val expiresAt: String,
    val appVersion: String = "unknown",
    val platform: String = "desktop"
)

object CommunityRelaySigner {
    const val USER_AGENT = "SpotiFLAC/1.5.2 (Windows NT 10.0; Win64; x64)"
    private const val MIN_SESSION_TTL_MS = 5 * 60 * 1000L

    @Volatile
    var fallbackSession: CommunityRelaySession? = null

    private val secureRandom = SecureRandom()

    fun isUsable(session: CommunityRelaySession?, nowMs: Long = System.currentTimeMillis()): Boolean {
        if (session == null || session.sessionId.isBlank() || session.sessionSecret.isBlank()) return false
        val expiresAtMs = runCatching { java.time.Instant.parse(session.expiresAt).toEpochMilli() }.getOrNull()
            ?: return false
        return expiresAtMs - nowMs >= MIN_SESSION_TTL_MS
    }

    fun signRequest(
        body: String,
        session: CommunityRelaySession,
        now: Long = System.currentTimeMillis(),
        nonceOverride: String? = null
    ): Map<String, String> {
        require(isUsable(session, now)) { "Community relay session is missing or expired" }
        val unixSeconds = now / 1000
        val window = unixSeconds / 300

        val secretBytes = session.sessionSecret.toByteArray(Charsets.UTF_8)
        val rollingKeyInput = "$window:${session.sessionId}"
        val rollingKey = Base64.getUrlEncoder().withoutPadding().encodeToString(
            hmacSha256(secretBytes, rollingKeyInput.toByteArray(Charsets.UTF_8))
        ).toByteArray(Charsets.UTF_8)

        val bodyBytes = body.toByteArray(Charsets.UTF_8)
        val bodyHash = sha256Hex(bodyBytes)

        val dateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }
        val timestamp = dateFormat.format(Date(now))

        val nonce = nonceOverride ?: ByteArray(12).also(secureRandom::nextBytes)
            .joinToString("") { "%02x".format(it) }

        val signingString = listOf(
            "SPOTIFLAC-HMAC-V1",
            "POST",
            "/api/dl",
            "",
            bodyHash,
            timestamp,
            nonce,
            session.sessionId,
            session.appVersion,
            session.platform
        ).joinToString("\n")

        val signature = Base64.getUrlEncoder().withoutPadding().encodeToString(
            hmacSha256(rollingKey, signingString.toByteArray(Charsets.UTF_8))
        )

        return mapOf(
            "X-Sig-Session" to session.sessionId,
            "X-Sig-Timestamp" to timestamp,
            "X-Sig-Nonce" to nonce,
            "X-Sig-Body-SHA256" to bodyHash,
            "X-Sig-Signature" to signature,
            "X-Sig-App-Version" to session.appVersion,
            "X-Sig-Platform" to session.platform,
            "User-Agent" to USER_AGENT
        )
    }

    private fun hmacSha256(key: ByteArray, data: ByteArray): ByteArray {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(key, "HmacSHA256"))
        return mac.doFinal(data)
    }

    private fun sha256Hex(data: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val hash = digest.digest(data)
        return hash.joinToString("") { "%02x".format(it) }
    }
}
