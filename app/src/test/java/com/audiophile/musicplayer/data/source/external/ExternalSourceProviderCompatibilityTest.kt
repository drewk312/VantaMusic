package com.audiophile.musicplayer.data.source.external

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ExternalSourceProviderCompatibilityTest {
    @Test
    fun providerSpecificResolversRejectOtherCatalogIds() {
        assertTrue(providerAcceptsCatalogTrackId("qobuz_gateway", "qobuz:123"))
        assertFalse(providerAcceptsCatalogTrackId("qobuz_gateway", "deezer:123"))
        assertTrue(providerAcceptsCatalogTrackId("deezer_gateway", "deezer:123"))
        assertFalse(providerAcceptsCatalogTrackId("deezer_gateway", "qobuz:123"))
    }

    @Test
    fun catalogAgnosticGatewayAcceptsPrefixedIds() {
        assertTrue(providerAcceptsCatalogTrackId("cloudflare_gateway", "deezer:123"))
        assertTrue(providerAcceptsCatalogTrackId("qobuz_tidal", "tidal:456"))
        assertTrue(providerAcceptsCatalogTrackId("youtube_music", "video-id"))
    }

    @Test
    fun vantaGatewayUsesSingleSearchAndStreamUrl() {
        val base = "https://vanta-music-gateway.16drewk.workers.dev"
        val search = ExternalSourceProvider.compactSearchUrls(base, "Welcome%20To%20The%20Jungle")
        val stream = ExternalSourceProvider.compactStreamUrls(base, "tidal%3A123", "24", "qobuz_tidal")
        assertEquals(1, search.size)
        assertEquals("$base/search?q=Welcome%20To%20The%20Jungle", search.first())
        assertEquals(1, stream.size)
        assertEquals("$base/stream/tidal%3A123?quality=24", stream.first())
    }

    @Test
    fun thirdPartyGatewayKeepsAShortFallbackList() {
        val stream = ExternalSourceProvider.compactStreamUrls(
            "https://example.invalid",
            "abc",
            "24",
            "addon"
        )
        assertEquals(3, stream.size)
        assertTrue(stream.first().contains("/stream/abc?quality=24"))
    }

    @Test
    fun vantaGatewayAndCloudflareShareOneSearchGroup() {
        val gateway = com.audiophile.musicplayer.data.source.CloudflareGatewaySource()
        val addon = ExternalSourceProvider(
            ExternalSourceConfig(
                id = "qobuz_tidal",
                displayName = "VANTA Gateway",
                baseUrl = SpotiFlacEndpoints.DEFAULT_GATEWAY_BASE_URL,
                enabled = true,
                providerKind = PlaybackProviderKind.ADDON
            )
        )
        assertEquals(gateway.searchGroupKey, addon.searchGroupKey)
    }

    @Test
    fun qobuzAttemptsStayOnQobuzBeforeAnyMappedTidalId() {
        val attempts = catalogAttemptsExactFirst(
            trackId = "qobuz:12345",
            mappedTidalId = "99999",
            mappedQobuzId = "12345"
        )
        assertEquals("qobuz", attempts.first().service)
        assertEquals("12345", attempts.first().trackId)
        assertEquals("tidal", attempts[1].service)
        assertEquals("99999", attempts[1].trackId)
    }

    @Test
    fun deezerAttemptsDoNotPutMappedTidalFirst() {
        val attempts = catalogAttemptsExactFirst(
            trackId = "deezer:4092566321",
            mappedTidalId = "111",
            mappedQobuzId = "222"
        )
        assertEquals("deezer", attempts.first().service)
        assertEquals(listOf("deezer", "tidal", "qobuz"), attempts.map { it.service })
    }

    @Test
    fun unprefixedNumericIdsAreQobuzNotDeezer() {
        val exact = exactCatalogAttempt("12345678")
        assertEquals("qobuz", exact?.service)
        assertEquals("12345678", exact?.trackId)
        assertTrue(catalogAttemptsExactFirst("12345678", mappedTidalId = "1").none { it.service == "deezer" })
    }

    @Test
    fun tidalProviderNeverUsesAQobuzNumericId() {
        assertEquals("888", tidalIdForTidalProvider("tidal:888", mappedTidalId = "1"))
        assertEquals("mapped", tidalIdForTidalProvider("qobuz:12345", mappedTidalId = "mapped"))
        assertNull(tidalIdForTidalProvider("qobuz:12345", mappedTidalId = null))
        assertNull(tidalIdForTidalProvider("12345", mappedTidalId = ""))
    }

    @Test
    fun qobuzCommunityQualityDoesNotDowngrade24BitPreference() {
        assertEquals("24", SpotiFlacEndpoints.mapQobuzQualityToCommunity("24"))
        assertEquals("24", SpotiFlacEndpoints.mapQobuzQualityToCommunity("HI_RES_LOSSLESS"))
        assertEquals("16", SpotiFlacEndpoints.mapQobuzQualityToCommunity("16"))
    }
}
