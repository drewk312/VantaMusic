package com.audiophile.musicplayer.data.source.playback

import com.audiophile.musicplayer.data.source.ResolvedStream
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class PlaybackQualityFallbackTest {
    private val flac = ResolvedStream(
        streamUrl = "https://media.example/song.flac",
        bitrateKbps = 1411,
        format = "flac",
        isLossless = true,
        bitDepth = 16,
        sampleRateHz = 44_100
    )

    @Test
    fun unavailableAtmosAndHiResStillPlayCdFlacWithoutAnAtmosLabel() = runBlocking {
        val attempts = mutableListOf<RequestedAudioQuality>()
        val result = resolveWithQualityFallback(RequestedAudioQuality.ATMOS) { quality ->
            attempts += quality
            when (quality) {
                RequestedAudioQuality.ATMOS -> failure(PlaybackSourceErrorCode.ATMOS_UNAVAILABLE)
                RequestedAudioQuality.SONY_360 -> failure(PlaybackSourceErrorCode.QUALITY_UNAVAILABLE)
                RequestedAudioQuality.IAMF -> failure(PlaybackSourceErrorCode.QUALITY_UNAVAILABLE)
                RequestedAudioQuality.HI_RES_24 -> failure(PlaybackSourceErrorCode.QUALITY_UNAVAILABLE)
                else -> PlaybackSourceOutcome.Ready(flac)
            }
        } as PlaybackSourceOutcome.Ready
        assertEquals(
            listOf(
                RequestedAudioQuality.ATMOS,
                RequestedAudioQuality.SONY_360,
                RequestedAudioQuality.IAMF,
                RequestedAudioQuality.HI_RES_24,
                RequestedAudioQuality.LOSSLESS_16
            ),
            attempts
        )
        assertEquals(flac.streamUrl, result.stream.streamUrl)
        assertFalse(result.stream.isDolbyAtmos)
        assertEquals(PlaybackSourceErrorCode.ATMOS_UNAVAILABLE, result.qualityShortfall)
    }

    @Test
    fun successfulAtmosDoesNotIssueFallbackRequests() = runBlocking {
        var count = 0
        val result = resolveWithQualityFallback(RequestedAudioQuality.ATMOS) {
            count++
            PlaybackSourceOutcome.Ready(flac.copy(isDolbyAtmos = true, format = "eac3-joc"))
        } as PlaybackSourceOutcome.Ready
        assertEquals(1, count)
        assertEquals(null, result.qualityShortfall)
    }

    @Test
    fun eclipsaIsSelectedWhenAtmosMixIsUnavailable() = runBlocking {
        val attempts = mutableListOf<RequestedAudioQuality>()
        val result = resolveWithQualityFallback(RequestedAudioQuality.ATMOS) { quality ->
            attempts += quality
            if (quality == RequestedAudioQuality.ATMOS) failure(PlaybackSourceErrorCode.ATMOS_UNAVAILABLE)
            else if (quality == RequestedAudioQuality.SONY_360) failure(PlaybackSourceErrorCode.QUALITY_UNAVAILABLE)
            else PlaybackSourceOutcome.Ready(flac.copy(isEclipsaAudio = true, mimeType = "audio/iamf"))
        } as PlaybackSourceOutcome.Ready
        assertEquals(listOf(RequestedAudioQuality.ATMOS, RequestedAudioQuality.SONY_360, RequestedAudioQuality.IAMF), attempts)
        assertEquals(true, result.stream.isEclipsaAudio)
        assertFalse(result.stream.isDolbyAtmos)
    }

    @Test
    fun rejectedSessionDoesNotRepeatRequestsAtEveryQuality() = runBlocking {
        var count = 0
        val result = resolveWithQualityFallback(RequestedAudioQuality.HI_RES_24) {
            count++
            failure(PlaybackSourceErrorCode.AUTH_REQUIRED)
        } as PlaybackSourceOutcome.Failed
        assertEquals(1, count)
        assertEquals(PlaybackSourceErrorCode.AUTH_REQUIRED, result.failure.code)
    }

    @Test
    fun offlineAtmosStillPlaysCdFlac() = runBlocking {
        val attempts = mutableListOf<RequestedAudioQuality>()
        val result = resolveWithQualityFallback(RequestedAudioQuality.AUTO_SPATIAL) { quality ->
            attempts += quality
            when (quality) {
                RequestedAudioQuality.ATMOS,
                RequestedAudioQuality.SONY_360,
                RequestedAudioQuality.IAMF,
                RequestedAudioQuality.HI_RES_24 -> failure(PlaybackSourceErrorCode.SOURCE_OFFLINE)
                else -> PlaybackSourceOutcome.Ready(flac)
            }
        } as PlaybackSourceOutcome.Ready
        assertEquals(
            listOf(
                RequestedAudioQuality.ATMOS,
                RequestedAudioQuality.SONY_360,
                RequestedAudioQuality.IAMF,
                RequestedAudioQuality.HI_RES_24,
                RequestedAudioQuality.LOSSLESS_16
            ),
            attempts
        )
        assertEquals(flac.streamUrl, result.stream.streamUrl)
    }

    private fun failure(code: PlaybackSourceErrorCode) = PlaybackSourceOutcome.Failed(
        PlaybackSourceFailure.of(code = code, sourceLabel = "Test gateway")
    )
}
