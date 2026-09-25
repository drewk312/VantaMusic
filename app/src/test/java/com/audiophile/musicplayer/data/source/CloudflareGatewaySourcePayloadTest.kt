package com.audiophile.musicplayer.data.source

import com.google.gson.JsonParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class CloudflareGatewaySourcePayloadTest {
    @Test
    fun providerSpecificPayloadIsValidJson() {
        val json = JsonParser.parseString(buildGatewayResolvePayload("3602074142", "deezer")).asJsonObject

        assertEquals("3602074142", json.get("id").asString)
        assertEquals("24", json.get("quality").asString)
        assertEquals("deezer", json.get("provider").asString)
    }

    @Test
    fun atmosPayloadUsesAtmosQualityToken() {
        val json = JsonParser.parseString(buildGatewayResolvePayload("123", "tidal", "atmos")).asJsonObject
        assertEquals("123", json.get("id").asString)
        assertEquals("atmos", json.get("quality").asString)
        assertEquals("tidal", json.get("provider").asString)
    }

    @Test
    fun sony360PayloadKeeps360QualityToken() {
        val json = JsonParser.parseString(buildGatewayResolvePayload("B076YT2CBT", "amazon", "360")).asJsonObject
        assertEquals("B076YT2CBT", json.get("id").asString)
        assertEquals("360", json.get("quality").asString)
        assertEquals("amazon", json.get("provider").asString)
    }

    @Test
    fun autoDetectPayloadOmitsProvider() {
        val json = JsonParser.parseString(buildGatewayResolvePayload("track-1", null)).asJsonObject

        assertEquals("track-1", json.get("id").asString)
        assertFalse(json.has("provider"))
    }

    @Test
    fun fulfillmentProviderDoesNotReplaceRegisteredGatewayIdentity() {
        val identity = gatewayStreamProviderIdentity(
            routeProviderId = "cloudflare_gateway",
            providerHint = "deezer",
            responseProviderId = "qobuz"
        )

        assertEquals("cloudflare_gateway", identity.routeProviderId)
        assertEquals("qobuz", identity.fulfillmentProviderId)
    }

    @Test
    fun providerHintIsUsedWhenGatewayOmitsFulfillmentProvider() {
        val identity = gatewayStreamProviderIdentity(
            routeProviderId = "cloudflare_gateway",
            providerHint = "deezer",
            responseProviderId = null
        )

        assertEquals("cloudflare_gateway", identity.routeProviderId)
        assertEquals("deezer", identity.fulfillmentProviderId)
    }

    @Test
    fun unknownGatewayBitrateIsNotDefaultedTo128() {
        assertEquals(0, CloudLibraryHelpers.inferStreamBitrateKbps(null, null, null, null))
        assertEquals(1411, CloudLibraryHelpers.inferStreamBitrateKbps(null, "FLAC", "audio/flac", null))
        assertEquals(320, CloudLibraryHelpers.inferStreamBitrateKbps(320, null, null, null))
        assertEquals(0, CloudLibraryHelpers.inferStreamBitrateKbps(null, null, "audio/mp4", "m4a"))
        assertEquals(0, CloudLibraryHelpers.inferStreamBitrateKbps(null, "atmos", "audio/mp4", "m4a"))
        assertEquals(3284, CloudLibraryHelpers.inferStreamBitrateKbps(3284, "atmos", "audio/mp4", "m4a"))
    }

    @Test
    fun redirectLocationIsUsedAsStreamUrl() {
        assertEquals(
            "https://cdn.example/audio.flac",
            GatewayStreamResponseParser.locationOrBody(302, "https://cdn.example/audio.flac", null)
        )
        assertEquals(
            """{"url":"https://cdn.example/audio.flac"}""",
            GatewayStreamResponseParser.locationOrBody(200, null, """{"url":"https://cdn.example/audio.flac"}""")
        )
        assertEquals(null, GatewayStreamResponseParser.locationOrBody(503, null, """{"error":"SOURCE_OFFLINE"}"""))
    }

    @Test
    fun parsesOnlySafeWidevineConfiguration() {
        val payload = JsonParser.parseString(
            """{
                "drm": {
                    "scheme": "widevine",
                    "licenseUrl": "https://vanta.example/drm/tidal/widevine?token=signed",
                    "forceDefaultLicenseUri": true,
                    "licenseRequestHeaders": {"X-Playback-Session": "abc"}
                }
            }"""
        ).asJsonObject
        val drm = GatewayStreamResponseParser.drmConfiguration(payload)

        assertEquals("widevine", drm?.scheme)
        assertEquals("https://vanta.example/drm/tidal/widevine?token=signed", drm?.licenseUrl)
        assertEquals("abc", drm?.licenseRequestHeaders?.get("X-Playback-Session"))
        assertEquals(true, drm?.forceDefaultLicenseUri)

        val unsafe = JsonParser.parseString(
            """{"drm":{"scheme":"widevine","licenseUrl":"http://127.0.0.1/license"}}"""
        ).asJsonObject
        assertEquals(null, GatewayStreamResponseParser.drmConfiguration(unsafe))
    }

    @Test
    fun searchPlaylistsKeepSourceAndArtwork() {
        val playlists = parseGatewayPlaylistCards(
            JsonParser.parseString(
                """[
                    {"id":"908","name":"This Is Metallica","curator":"Deezer","artworkURL":"https://example.com/p.jpg","source":"deezer"},
                    {"id":"","name":"Missing id"},
                    {"id":"pl.abc","title":"Apple Essentials","curator":"Apple Music"}
                ]"""
            ).asJsonArray
        )
        assertEquals(listOf("908", "pl.abc"), playlists.map { it.id })
        assertEquals("deezer", playlists.first().source)
        assertEquals("apple", playlists.last().source)
        assertEquals("This Is Metallica", playlists.first().toCanonicalPlaylist().title)
    }
}
