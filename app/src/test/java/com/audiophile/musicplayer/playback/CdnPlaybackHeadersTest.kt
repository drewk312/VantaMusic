package com.audiophile.musicplayer.playback

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CdnPlaybackHeadersTest {
    @Test
    fun neteaseGetsMusic163Referer() {
        val headers = CdnPlaybackHeaders.forUrl(
            "https://m801.music.126.net/20260912091831/c62bb141.flac"
        )
        assertEquals("https://music.163.com/", headers["Referer"])
        assertEquals("https://music.163.com", headers["Origin"])
        assertTrue(headers["User-Agent"].orEmpty().contains("Chrome"))
    }

    @Test
    fun qobuzAkamaiGetsQobuzReferer() {
        val headers = CdnPlaybackHeaders.forUrl(
            "https://streaming-qobuz-std.akamaized.net/file?eid=1&fmt=7"
        )
        assertEquals("https://www.qobuz.com/", headers["Referer"])
    }

    @Test
    fun googlevideoLeavesHeadersEmpty() {
        assertTrue(
            CdnPlaybackHeaders.forUrl("https://rr1---sn.googlevideo.com/videoplayback").isEmpty()
        )
    }

    @Test
    fun shortLivedCdnDetectsNeteaseAndAkamai() {
        assertTrue(CdnPlaybackHeaders.isShortLivedCdn("https://m801.music.126.net/a.flac"))
        assertTrue(CdnPlaybackHeaders.isShortLivedCdn("https://streaming-qobuz-std.akamaized.net/file"))
        assertFalse(CdnPlaybackHeaders.isShortLivedCdn("https://example.com/track.flac"))
    }
}
