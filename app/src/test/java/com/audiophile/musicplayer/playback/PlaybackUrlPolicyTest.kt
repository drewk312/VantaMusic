package com.audiophile.musicplayer.playback

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackUrlPolicyTest {
    @Test
    fun allowsPublicHttpsAudio() {
        assertTrue(PlaybackUrlPolicy.isAllowedRemoteStreamUrl("https://audio.example.com/live.flac?token=secret"))
    }

    @Test
    fun rejectsCleartextCredentialsAndPrivateHosts() {
        listOf(
            "http://audio.example.com/live.mp3",
            "https://user:pass@audio.example.com/live.mp3",
            "https://localhost/live.mp3",
            "https://127.0.0.1/live.mp3",
            "https://10.0.0.4/live.mp3",
            "https://169.254.169.254/latest/meta-data",
            "https://192.168.1.5/live.mp3",
            "not a url"
        ).forEach { assertFalse(it, PlaybackUrlPolicy.isAllowedRemoteStreamUrl(it)) }
    }
}
