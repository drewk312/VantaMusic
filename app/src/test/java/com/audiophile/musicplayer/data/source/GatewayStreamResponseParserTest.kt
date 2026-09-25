package com.audiophile.musicplayer.data.source

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GatewayStreamResponseParserTest {

    @Test
    fun rejectsAmazonCloudFrontWithoutDrm() {
        assertTrue(
            GatewayStreamResponseParser.streamUrlLooksUnplayableWithoutDrm(
                "https://d123.cloudfront.net/file.mp4",
                "flac",
                drmPresent = false
            )
        )
        assertFalse(
            GatewayStreamResponseParser.streamUrlLooksUnplayableWithoutDrm(
                "https://vanta-music-gateway.16drewk.workers.dev/api/decrypt-stream?t=1",
                "flac",
                drmPresent = false
            )
        )
        assertFalse(
            GatewayStreamResponseParser.streamUrlLooksUnplayableWithoutDrm(
                "https://streaming-qobuz-std.akamaized.net/file.flac",
                "flac",
                drmPresent = false
            )
        )
    }

    @Test
    fun rejectsMpeghWithoutDrm() {
        assertTrue(
            GatewayStreamResponseParser.streamUrlLooksUnplayableWithoutDrm(
                "https://cdn.example/audio",
                "mha1",
                drmPresent = false
            )
        )
        assertFalse(
            GatewayStreamResponseParser.streamUrlLooksUnplayableWithoutDrm(
                "https://cdn.example/audio",
                "mha1",
                drmPresent = true
            )
        )
    }
}
