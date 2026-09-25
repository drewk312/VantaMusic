package com.audiophile.musicplayer.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AppUpdateRepositoryTest {
    @Test
    fun `parses a public update manifest`() {
        val manifest = AppUpdateRepository.parseManifest(
            """{"versionCode":3,"versionName":"1.2","apkUrl":"https://example.com/vanta.apk","donateUrl":"https://ko-fi.com/vanta","changelog":"Fixes"}"""
        )
        requireNotNull(manifest)
        assertEquals(3, manifest.versionCode)
        assertEquals("1.2", manifest.versionName)
        assertEquals("https://example.com/vanta.apk", manifest.apkUrl)
        assertEquals("https://ko-fi.com/vanta", manifest.donateUrl)
    }

    @Test
    fun `rejects a manifest without a version`() {
        assertNull(AppUpdateRepository.parseManifest("""{"apkUrl":"https://example.com/vanta.apk"}"""))
    }

    @Test
    fun `treats a higher versionCode as an update`() {
        val repository = AppUpdateRepository(currentVersionCode = 2)
        val newer = AppReleaseManifest(3, "1.2", "https://example.com/vanta.apk", "", "")
        val same = AppReleaseManifest(2, "1.1", "https://example.com/vanta.apk", "", "")
        assertTrue(repository.isNewer(newer))
        assertFalse(repository.isNewer(same))
    }
}
