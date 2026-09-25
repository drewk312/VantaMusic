package com.audiophile.musicplayer.data.source.playback

import com.audiophile.musicplayer.data.audio.FlacStreamInfoParser
import com.audiophile.musicplayer.data.local.entities.SourceType
import com.audiophile.musicplayer.data.local.entities.TrackSource
import com.audiophile.musicplayer.data.local.entities.UnifiedTrack
import com.audiophile.musicplayer.data.local.entities.UnifiedTrackWithSources
import com.audiophile.musicplayer.data.source.ResolvedStream
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackSourceRouterTest {
    @Test
    fun localHiResPlaysWhenCatalogNeedsToken() = runBlocking {
        val flacUri = "/sdcard/Music/jungle-24-48.flac"
        val header = flacHeader(sampleRateHz = 48_000, channels = 2, bitDepth = 24, totalSamples = 1_440_000)
        val router = PlaybackSourceRouter(
            local = LocalHiResPlaybackAdapter(object : MediaProbe {
                override fun readHeader(uri: String, maxBytes: Int): ByteArray? =
                    if (uri == flacUri) header else null

                override fun lengthBytes(uri: String): Long? =
                    if (uri == flacUri) 4_647_396L else null
            }),
            direct = DirectMediaPlaybackAdapter(),
            catalog = object : PlaybackSourceAdapter {
                override val adapterId = "catalog"
                override val sourceLabel = "Tidal"
                override suspend fun resolve(request: PlaybackSourceRequest): PlaybackSourceOutcome =
                    PlaybackSourceOutcome.Failed(
                        PlaybackSourceFailure.of(
                            code = PlaybackSourceErrorCode.AUTH_REQUIRED,
                            sourceLabel = "Tidal",
                            adapterId = "catalog"
                        )
                    )
            }
        )
        val outcome = router.resolvePersisted(
            track = track(
                localUrl = flacUri,
                catalogProvider = "tidal_gateway",
                catalogId = "tidal:123"
            )
        )
        val ready = outcome as PlaybackSourceOutcome.Ready
        assertEquals("local", ready.stream.providerId)
        assertEquals("flac", ready.stream.codec)
        assertEquals(24, ready.stream.bitDepth)
        assertEquals(48_000, ready.stream.sampleRateHz)
        assertTrue(ready.stream.isLossless)
        assertFalse(ready.stream.isDolbyAtmos)
        assertEquals("Local file", ready.stream.sourceLabel)
    }

    @Test
    fun catalogAuthRequiredIsNotCollapsedWhenNoLocalFileExists() = runBlocking {
        val router = PlaybackSourceRouter(
            local = LocalHiResPlaybackAdapter(),
            direct = DirectMediaPlaybackAdapter(),
            catalog = object : PlaybackSourceAdapter {
                override val adapterId = "catalog"
                override val sourceLabel = "Amazon"
                override suspend fun resolve(request: PlaybackSourceRequest): PlaybackSourceOutcome =
                    PlaybackSourceOutcome.Failed(
                        PlaybackSourceFailure.of(
                            code = PlaybackSourceErrorCode.AUTH_REQUIRED,
                            sourceLabel = "Amazon",
                            adapterId = "catalog"
                        )
                    )
            }
        )
        val outcome = router.resolvePersisted(
            track = track(
                localUrl = null,
                catalogProvider = "amazon_gateway",
                catalogId = "amazon:abc"
            )
        )
        val failed = outcome as PlaybackSourceOutcome.Failed
        assertEquals(PlaybackSourceErrorCode.AUTH_REQUIRED, failed.failure.code)
        assertFalse(failed.failure.message.contains("No playable stream found"))
    }

    @Test
    fun missingLocalFileDoesNotSurfaceCatalogTokenAsTheOnlyError() {
        val collapsed = PlaybackSourceRouter.collapseFailures(
            failures = listOf(
                PlaybackSourceFailure.of(
                    code = PlaybackSourceErrorCode.NOT_FOUND,
                    sourceLabel = "Local file",
                    adapterId = "local"
                ),
                PlaybackSourceFailure.of(
                    code = PlaybackSourceErrorCode.AUTH_REQUIRED,
                    sourceLabel = "Tidal",
                    adapterId = "catalog"
                )
            ),
            triedLocalOrDirect = true
        )
        assertEquals(PlaybackSourceErrorCode.NOT_FOUND, collapsed.code)
        assertTrue(collapsed.message.contains("authentication"))
        assertFalse(collapsed.message.contains("TorBox", ignoreCase = true))
        assertFalse(collapsed.message.contains("Real-Debrid", ignoreCase = true))
    }

    @Test
    fun atmosShortfallStillPlaysLosslessFlac() = runBlocking {
        val uri = "/music/stereo.flac"
        val header = flacHeader(sampleRateHz = 48_000, channels = 2, bitDepth = 24, totalSamples = 480_000)
        val adapter = LocalHiResPlaybackAdapter(object : MediaProbe {
            override fun readHeader(uri: String, maxBytes: Int): ByteArray? = header
            override fun lengthBytes(uri: String): Long? = 1_000_000L
        })
        val outcome = adapter.resolve(
            PlaybackSourceRequest(
                title = "Test",
                artist = "Artist",
                localUri = uri,
                requestedQuality = RequestedAudioQuality.ATMOS
            )
        )
        val ready = outcome as PlaybackSourceOutcome.Ready
        assertEquals(PlaybackSourceErrorCode.ATMOS_UNAVAILABLE, ready.qualityShortfall)
        assertFalse(ready.stream.isDolbyAtmos)
        assertEquals(24, ready.stream.bitDepth)
    }

    @Test
    fun directSelfHostedFlacDoesNotRequireCatalogAuth() = runBlocking {
        val router = PlaybackSourceRouter(
            local = LocalHiResPlaybackAdapter(),
            direct = DirectMediaPlaybackAdapter(),
            catalog = object : PlaybackSourceAdapter {
                override val adapterId = "catalog"
                override val sourceLabel = "Qobuz"
                override suspend fun resolve(request: PlaybackSourceRequest): PlaybackSourceOutcome =
                    PlaybackSourceOutcome.Failed(
                        PlaybackSourceFailure.of(
                            code = PlaybackSourceErrorCode.AUTH_REQUIRED,
                            sourceLabel = "Qobuz",
                            adapterId = "catalog"
                        )
                    )
            }
        )
        val outcome = router.resolvePersisted(
            track = UnifiedTrackWithSources(
                track = UnifiedTrack(
                    trackId = 9L,
                    title = "Self hosted",
                    artist = "Artist",
                    albumName = "Album",
                    coverArtUrl = null
                ),
                sources = listOf(
                    TrackSource(
                        sourceId = 1L,
                        parentTrackId = 9L,
                        sourceType = SourceType.ADDON,
                        streamUrl = "https://files.example.net/library/track.flac",
                        bitrate = 2304,
                        externalProviderId = "qobuz",
                        externalTrackId = "qobuz:1"
                    )
                )
            )
        )
        val ready = outcome as PlaybackSourceOutcome.Ready
        assertEquals("direct", ready.stream.providerId)
        assertEquals("flac", ready.stream.container)
        assertTrue(ready.stream.isLossless)
    }

    @Test
    fun staleCdnPersistedUrlIsReResolvedInsteadOfReplayed() = runBlocking {
        val router = PlaybackSourceRouter(
            local = LocalHiResPlaybackAdapter(),
            direct = DirectMediaPlaybackAdapter(),
            catalog = object : PlaybackSourceAdapter {
                override val adapterId = "catalog"
                override val sourceLabel = "Qobuz"
                override suspend fun resolve(request: PlaybackSourceRequest): PlaybackSourceOutcome =
                    PlaybackSourceOutcome.Ready(
                        PlaybackStreamNormalizer.normalize(
                            ResolvedStream(
                                streamUrl = "https://fresh-cdn.example/track.flac",
                                bitrateKbps = 900,
                                mimeType = "audio/flac",
                                format = "flac"
                            ),
                            sourceLabel = "Qobuz fresh",
                            providerId = "catalog_fresh"
                        )
                    )
            }
        )
        val outcome = router.resolvePersisted(
            track = UnifiedTrackWithSources(
                track = UnifiedTrack(
                    trackId = 10L,
                    title = "Stale CDN",
                    artist = "Artist",
                    albumName = "Album",
                    coverArtUrl = null
                ),
                sources = listOf(
                    TrackSource(
                        sourceId = 1L,
                        parentTrackId = 10L,
                        sourceType = SourceType.ADDON,
                        streamUrl = "https://streaming-qobuz-std.akamaized.net/file?eid=10&fmt=7&profile=raw.flac",
                        bitrate = 900,
                        externalProviderId = "cloudflare_gateway",
                        externalTrackId = "qobuz:10",
                        expiresAtMs = null
                    )
                )
            )
        )
        val ready = outcome as PlaybackSourceOutcome.Ready
        assertEquals("catalog_fresh", ready.stream.providerId)
    }

    @Test
    fun selfHostedExpiredPersistedUrlIsReResolvedInsteadOfReplayed() = runBlocking {
        val router = PlaybackSourceRouter(
            local = LocalHiResPlaybackAdapter(),
            direct = DirectMediaPlaybackAdapter(),
            catalog = object : PlaybackSourceAdapter {
                override val adapterId = "catalog"
                override val sourceLabel = "Direct catalog"
                override suspend fun resolve(request: PlaybackSourceRequest): PlaybackSourceOutcome =
                    PlaybackSourceOutcome.Ready(
                        PlaybackStreamNormalizer.normalize(
                            ResolvedStream(
                                streamUrl = "https://fresh.example/track.flac",
                                bitrateKbps = 900,
                                mimeType = "audio/flac",
                                format = "flac"
                            ),
                            sourceLabel = "Fresh",
                            providerId = "catalog_fresh"
                        )
                    )
            }
        )
        val outcome = router.resolvePersisted(
            track = UnifiedTrackWithSources(
                track = UnifiedTrack(
                    trackId = 11L,
                    title = "Expired self host",
                    artist = "Artist",
                    albumName = "Album",
                    coverArtUrl = null
                ),
                sources = listOf(
                    TrackSource(
                        sourceId = 1L,
                        parentTrackId = 11L,
                        sourceType = SourceType.ADDON,
                        streamUrl = "https://files.example.net/library/track.flac",
                        bitrate = 900,
                        externalProviderId = "selfhost",
                        externalTrackId = "selfhost:11",
                        expiresAtMs = System.currentTimeMillis() - 60_000L
                    )
                )
            )
        )
        val ready = outcome as PlaybackSourceOutcome.Ready
        assertEquals("catalog_fresh", ready.stream.providerId)
    }

    @Test
    fun flacHeaderAverageBitrateIsNotHardcoded() {
        val info = FlacStreamInfoParser.parse(
            flacHeader(sampleRateHz = 48_000, channels = 2, bitDepth = 24, totalSamples = 1_440_000)
        )
        assertEquals(1239, FlacStreamInfoParser.averageBitrateKbps(4_647_396L, requireNotNull(info)))
    }

    private fun track(
        localUrl: String?,
        catalogProvider: String,
        catalogId: String
    ): UnifiedTrackWithSources {
        val sources = buildList {
            if (localUrl != null) {
                add(
                    TrackSource(
                        sourceId = 1L,
                        parentTrackId = 1L,
                        sourceType = SourceType.LOCAL,
                        streamUrl = localUrl,
                        bitrate = 0,
                        externalProviderId = "local",
                        externalTrackId = null
                    )
                )
            }
            add(
                TrackSource(
                    sourceId = 2L,
                    parentTrackId = 1L,
                    sourceType = SourceType.ADDON,
                    streamUrl = "",
                    bitrate = 0,
                    externalProviderId = catalogProvider,
                    externalTrackId = catalogId
                )
            )
        }
        return UnifiedTrackWithSources(
            track = UnifiedTrack(
                trackId = 1L,
                title = "Welcome To The Jungle",
                artist = "Guns N' Roses",
                albumName = "Appetite for Destruction",
                coverArtUrl = null
            ),
            sources = sources
        )
    }

    private fun flacHeader(
        sampleRateHz: Int,
        channels: Int,
        bitDepth: Int,
        totalSamples: Long
    ): ByteArray {
        val header = ByteArray(42)
        header[0] = 'f'.code.toByte()
        header[1] = 'L'.code.toByte()
        header[2] = 'a'.code.toByte()
        header[3] = 'C'.code.toByte()
        header[4] = 0
        header[5] = 0
        header[6] = 0
        header[7] = 34
        val packed = (sampleRateHz shl 4) or
            (((channels - 1) and 0x7) shl 1) or
            (((bitDepth - 1) ushr 4) and 0x1)
        header[18] = ((packed ushr 16) and 0xFF).toByte()
        header[19] = ((packed ushr 8) and 0xFF).toByte()
        header[20] = (packed and 0xFF).toByte()
        header[21] = ((((bitDepth - 1) and 0xF) shl 4) or
            ((totalSamples ushr 32).toInt() and 0xF)).toByte()
        header[22] = ((totalSamples ushr 24) and 0xFF).toByte()
        header[23] = ((totalSamples ushr 16) and 0xFF).toByte()
        header[24] = ((totalSamples ushr 8) and 0xFF).toByte()
        header[25] = (totalSamples and 0xFF).toByte()
        return header
    }
}

class GatewayStreamErrorClassifierTest {
    @Test
    fun authRequiredFromGatewayCode() {
        assertEquals(
            PlaybackSourceErrorCode.AUTH_REQUIRED,
            GatewayStreamErrorClassifier.classify(401, "AUTH_REQUIRED", null)
        )
    }

    @Test
    fun legacyNoStreamSourceWithTokenHintIsAuthRequired() {
        assertEquals(
            PlaybackSourceErrorCode.AUTH_REQUIRED,
            GatewayStreamErrorClassifier.classify(
                503,
                "no_stream_source",
                "Set QOBUZ_APP_ID+QOBUZ_AUTH_TOKEN"
            )
        )
    }

    @Test
    fun legacyNoStreamSourceWithoutAuthHintIsOffline() {
        assertEquals(
            PlaybackSourceErrorCode.SOURCE_OFFLINE,
            GatewayStreamErrorClassifier.classify(503, "no_stream_source", "upstream timed out")
        )
    }

    @Test
    fun atmosUnavailableIsDistinct() {
        assertEquals(
            PlaybackSourceErrorCode.ATMOS_UNAVAILABLE,
            GatewayStreamErrorClassifier.classify(404, "ATMOS_UNAVAILABLE", null)
        )
    }
}

class PlaybackStreamNormalizerTest {
    @Test
    fun twentyFourBitFlacIsLosslessNotAtmos() {
        val stream = PlaybackStreamNormalizer.normalize(
            ResolvedStream(
                streamUrl = "file:///music/a.flac",
                bitrateKbps = 3284,
                mimeType = "audio/flac",
                format = "flac",
                bitDepth = 24,
                sampleRateHz = 48_000,
                channelCount = 2,
                isDolbyAtmos = false
            ),
            sourceLabel = "Local file",
            providerId = "local"
        )
        assertEquals("flac", stream.codec)
        assertTrue(stream.isLossless)
        assertFalse(stream.isDolbyAtmos)
        assertEquals(
            PlaybackSourceErrorCode.ATMOS_UNAVAILABLE,
            PlaybackStreamNormalizer.qualityShortfall(stream, RequestedAudioQuality.ATMOS)
        )
        assertEquals(
            null,
            PlaybackStreamNormalizer.qualityShortfall(stream, RequestedAudioQuality.HI_RES_24)
        )
    }

    @Test
    fun eac3IsAtmos() {
        val stream = PlaybackStreamNormalizer.normalize(
            ResolvedStream(
                streamUrl = "https://cdn.example/atmos.mp4",
                bitrateKbps = 768,
                mimeType = "audio/eac3-joc",
                format = "eac3_joc"
            )
        )
        assertTrue(stream.isDolbyAtmos)
        assertEquals("eac3", stream.codec)
    }

    @Test
    fun mpeghIsNotInferredAsMp3() {
        val stream = PlaybackStreamNormalizer.normalize(
            ResolvedStream(
                streamUrl = "https://gateway.example/audio/extension?token=abc",
                bitrateKbps = 768,
                mimeType = "audio/mp4",
                format = "mha1",
                qualityLabel = "360 Reality Audio"
            )
        )
        assertEquals("mpeg-h", stream.codec)
        assertFalse(stream.isDolbyAtmos)
        assertEquals(
            null,
            PlaybackStreamNormalizer.qualityShortfall(stream, RequestedAudioQuality.SONY_360)
        )
    }
}

class PlaybackAccountPolicyTest {
    @Test
    fun vantaGatewayDoesNotRequireUserTidalToken() {
        assertFalse(PlaybackAccountPolicy.requiresLicensedAccount("cloudflare_gateway"))
        assertFalse(PlaybackAccountPolicy.requiresLicensedAccount("qobuz_tidal"))
        assertFalse(PlaybackAccountPolicy.requiresLicensedAccount("tidal_gateway"))
        assertTrue(PlaybackAccountPolicy.requiresLicensedAccount("torbox_library"))
    }
}

class RequestedAudioQualityPreferenceTest {
    @Test
    fun atmosPreferenceRequestsAtmosQuality() {
        assertEquals(RequestedAudioQuality.ATMOS, requestedAudioQualityFromPreference("atmos"))
        assertEquals("atmos", RequestedAudioQuality.ATMOS.toGatewayQuality())
        assertEquals(RequestedAudioQuality.SONY_360, requestedAudioQualityFromPreference("360"))
        assertEquals("360", RequestedAudioQuality.SONY_360.toGatewayQuality())
        assertEquals(RequestedAudioQuality.HI_RES_24, requestedAudioQualityFromPreference("24"))
        assertEquals(RequestedAudioQuality.LOSSLESS_16, requestedAudioQualityFromPreference("16"))
    }

    @Test
    fun atmosUnavailableDoesNotAskForATidalAccount() {
        val message = PlaybackSourceFailure.messageFor(
            PlaybackSourceErrorCode.ATMOS_UNAVAILABLE,
            "Cloudflare Gateway"
        )
        assertTrue(message.contains("configured source"))
        assertFalse(message.contains("HiFi", ignoreCase = true))
        assertFalse(message.contains("token", ignoreCase = true))
        assertFalse(message.contains("account", ignoreCase = true))
    }

    @Test
    fun authRequiredUserMessageIsGeneric() {
        val failure = PlaybackSourceFailure.of(
            PlaybackSourceErrorCode.AUTH_REQUIRED,
            "TorBox Library"
        )
        assertEquals("This source requires authentication.", failure.userMessage())
        assertFalse(failure.userMessage().contains("TorBox"))
        assertEquals("TorBox Library", failure.sourceLabel)
    }
}
