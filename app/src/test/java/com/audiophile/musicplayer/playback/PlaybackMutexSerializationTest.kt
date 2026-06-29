package com.audiophile.musicplayer.playback

import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.junit.Assert.assertEquals
import org.junit.Test

/** Mirrors PlayerController.playbackMutex — concurrent playQueue must not overlap setMediaItems work. */
class PlaybackMutexSerializationTest {

    @Test
    fun mutex_serializesConcurrentAcquires() = runBlocking {
        val playbackMutex = Mutex()
        var concurrent = 0
        var maxConcurrent = 0
        val jobs = (1..20).map {
            async {
                playbackMutex.withLock {
                    concurrent++
                    maxConcurrent = maxOf(maxConcurrent, concurrent)
                    delay(15)
                    concurrent--
                }
            }
        }
        jobs.forEach { it.await() }
        assertEquals(1, maxConcurrent)
    }
}
