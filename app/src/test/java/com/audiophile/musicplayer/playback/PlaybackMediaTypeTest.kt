package com.audiophile.musicplayer.playback

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PlaybackMediaTypeTest {
    @Test
    fun preservesDashMimeWithParameters() {
        assertEquals(
            PlaybackMediaType.DASH,
            PlaybackMediaType.forStream("application/dash+xml; charset=utf-8", "https://example.com/manifest")
        )
    }

    @Test
    fun recognizesExtensionlessGatewayManifestRoute() {
        assertEquals(
            PlaybackMediaType.DASH,
            PlaybackMediaType.forStream(null, "https://gateway.example/manifest/mpd?data=abc")
        )
    }

    @Test
    fun infersFlacForExtensionlessQobuzCdn() {
        assertEquals(
            "audio/flac",
            PlaybackMediaType.forStream(
                null,
                "https://streaming-qobuz-std.akamaized.net/file?eid=381791126&fmt=7&profile=raw"
            )
        )
    }
}
