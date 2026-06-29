package com.audiophile.musicplayer.data.connectors

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SecurityTest {

    @Test
    fun noTokensInConnectorLogsAfterRedaction() {
        val raw = "Authorization: Bearer access.secret refresh_token=refresh.secret developer_token=dev.secret"
        val redacted = ConnectedLibraryLogRedactor.redact(raw)

        assertFalse(redacted.contains("access.secret"))
        assertFalse(redacted.contains("refresh.secret"))
        assertFalse(redacted.contains("dev.secret"))
        assertTrue(redacted.contains("<redacted>"))
    }

    @Test
    fun providerTrackLinkRejectsStreamUrlsAsProviderIds() {
        val failed = runCatching {
            ProviderTrackLink(
                id = "bad",
                provider = ConnectedLibraryProvider.SPOTIFY,
                providerTrackId = "https://spotify.example/stream",
                matchConfidence = 1f,
                linkedAt = 1L
            )
        }.isFailure

        assertTrue(failed)
    }
}
