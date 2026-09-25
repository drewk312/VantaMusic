package com.audiophile.musicplayer.playback

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertEquals
import org.junit.Test

class ListeningHistoryRecorderTest {

    private data class Written(
        val title: String,
        val msPlayed: Long,
        val skipped: Boolean,
        val reasonEnd: String?
    )

    private fun sampleRecord(durationMs: Long? = 180_000L) = ListeningPlayRecord(
        trackId = 1L,
        title = "Shape of You",
        artist = "Ed Sheeran",
        album = "divide",
        platform = "SPOTIFY",
        providerId = "monochrome.spotify",
        sourceTrackId = "spotify:track:7qiZfU4dY1lWllzX7mPBI3",
        durationMs = durationMs
    )

    private class RecordingSink : ListeningHistorySink {
        val writes = ConcurrentLinkedQueue<Written>()
        val latch = CountDownLatch(1)
        override suspend fun write(
            record: ListeningPlayRecord,
            startedAt: Long,
            msPlayed: Long,
            skipped: Boolean,
            reasonEnd: String?
        ) {
            writes += Written(record.title, msPlayed, skipped, reasonEnd)
            latch.countDown()
        }
    }

    private fun awaitSink(sink: RecordingSink) {
        sink.latch.await(5, TimeUnit.SECONDS)
    }

    @Test
    fun naturalEnd_writesCompletedPlayWithDuration() {
        val sink = RecordingSink()
        val recorder = ListeningHistoryRecorder(sink, CoroutineScope(SupervisorJob() + Dispatchers.Default))

        recorder.onPlaybackStarted(sampleRecord())
        recorder.onTrackEndedNaturally(180_000L)
        awaitSink(sink)

        assertEquals(1, sink.writes.size)
        val write = sink.writes.first()
        assertEquals(false, write.skipped)
        assertEquals(180_000L, write.msPlayed)
        assertEquals("trackdone", write.reasonEnd)
    }

    @Test
    fun manualSkip_recordsPositionAsSkipped() {
        val sink = RecordingSink()
        val recorder = ListeningHistoryRecorder(sink, CoroutineScope(SupervisorJob() + Dispatchers.Default))

        recorder.onPlaybackStarted(sampleRecord())
        recorder.onTrackSkipped(42_000L)
        awaitSink(sink)

        val write = sink.writes.first()
        assertEquals(true, write.skipped)
        assertEquals(42_000L, write.msPlayed)
    }

    @Test
    fun completedThenAutoAdvance_doesNotDoubleWrite() {
        val sink = RecordingSink()
        val recorder = ListeningHistoryRecorder(sink, CoroutineScope(SupervisorJob() + Dispatchers.Default))

        recorder.onPlaybackStarted(sampleRecord())
        recorder.onTrackEndedNaturally(180_000L)
        awaitSink(sink)
        // QueueAwarePlayer now triggers onSkipToNext -> playNextFromQueue -> onTrackSkipped.
        recorder.onTrackSkipped(0L)

        assertEquals(1, sink.writes.size)
    }

    @Test
    fun stop_afterShortPlay_writesPartial() {
        val sink = RecordingSink()
        val recorder = ListeningHistoryRecorder(sink, CoroutineScope(SupervisorJob() + Dispatchers.Default))

        recorder.onPlaybackStarted(sampleRecord())
        recorder.onPlaybackStopped(15_000L)
        awaitSink(sink)

        val write = sink.writes.first()
        assertEquals(false, write.skipped)
        assertEquals(15_000L, write.msPlayed)
        assertEquals("stop", write.reasonEnd)
    }

    @Test
    fun clampsMsPlayedToDuration() {
        val sink = RecordingSink()
        val recorder = ListeningHistoryRecorder(sink, CoroutineScope(SupervisorJob() + Dispatchers.Default))

        recorder.onPlaybackStarted(sampleRecord(durationMs = 120_000L))
        recorder.onTrackSkipped(999_999L)
        awaitSink(sink)

        assertEquals(120_000L, sink.writes.first().msPlayed)
    }
}