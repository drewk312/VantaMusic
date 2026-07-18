package com.audiophile.musicplayer.data.lyrics

import com.audiophile.musicplayer.data.canonical.CanonicalIdentityResolver
import com.audiophile.musicplayer.data.local.entities.UnifiedTrack
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LyricsIdentityGateTest {

    @Test
    fun plainLyricsWithEstimatedTimingsAreNotPromotedToExactSync() = runBlocking {
        val repository = LyricsRepository(
            providers = listOf(PlainLyricsProvider()),
            lyricsCacheDao = InMemoryLyricsCacheDao()
        )
        val gate = LyricsIdentityGate(repository)

        val identity = gate.resolveIdentity(
            track = UnifiedTrack(
                title = "Spirit In The Sky",
                artist = "Norman Greenbaum",
                albumName = "Spirit In The Sky",
                coverArtUrl = null,
                durationMs = 240_000L
            ),
            isrc = null
        )

        assertTrue(identity is LyricsIdentity.EstimatedTiming)
        assertFalse(identity.lyricsData?.isSynced == true)
        assertTrue(identity.lyricsData?.lines.orEmpty().any { it.startTimeMs != null })
    }

    @Test
    fun victoryLapFiveDoesNotAcceptLyricsThatOnlyMatchAfterDroppingDuration() = runBlocking {
        val repository = LyricsRepository(
            providers = listOf(DurationStrippedWrongLyricsProvider()),
            lyricsCacheDao = InMemoryLyricsCacheDao()
        )
        val gate = LyricsIdentityGate(repository)

        val identity = gate.resolveIdentity(
            track = UnifiedTrack(
                title = "Victory Lap Five",
                artist = "Fred again..",
                albumName = "USB",
                coverArtUrl = null,
                durationMs = 213_000L
            ),
            isrc = null
        )

        assertTrue(identity is LyricsIdentity.Unavailable)
    }

    @Test
    fun victoryLapFiveFredAgainKnownWebLyricsAreEstimatedInsteadOfBlocked() = runBlocking {
        val repository = LyricsRepository(
            providers = listOf(
                KnownWebLyricsProvider { url -> victoryLapFiveHtml().takeIf { "victory-lap-five" in url } }
            ),
            lyricsCacheDao = InMemoryLyricsCacheDao()
        )
        val gate = LyricsIdentityGate(repository)

        val identity = gate.resolveIdentity(
            track = UnifiedTrack(
                title = "Victory Lap Five",
                artist = "Fred again..",
                albumName = "Victory Lap Five",
                coverArtUrl = null,
                durationMs = 344_000L
            ),
            isrc = null
        )

        assertTrue(identity is LyricsIdentity.EstimatedTiming)
        assertFalse(identity.lyricsData?.isSynced == true)
        assertTrue(identity.lyricsData?.sourceLabel?.contains("Verified web lyrics") == true)
    }

    @Test
    fun victoryLapFiveKnownWebLyricsBypassOldLrclibCache() = runBlocking {
        val cacheDao = InMemoryLyricsCacheDao()
        val track = UnifiedTrack(
            title = "Victory Lap Five",
            artist = "Fred again..",
            albumName = "Victory Lap Five",
            coverArtUrl = null,
            durationMs = 344_000L
        )
        val cacheKey = CanonicalIdentityResolver.generateCanonicalId(
            isrc = null,
            _unused = null,
            title = track.title,
            artist = track.artist,
            album = track.albumName,
            durationMs = track.durationMs
        )
        cacheDao.insertLyrics(
            LyricsCacheEntity(
                lyricsKey = cacheKey,
                trackTitle = track.title,
                artist = track.artist,
                isrc = null,
                providerId = "lrclib",
                lyricsJson = """[{"startTimeMs":0,"endTimeMs":4000,"text":"wrong cached row"}]""",
                isSynced = true,
                lastUpdatedAt = System.currentTimeMillis()
            )
        )
        val repository = LyricsRepository(
            providers = listOf(
                KnownWebLyricsProvider { url -> victoryLapFiveHtml().takeIf { "victory-lap-five" in url } },
                DurationStrippedWrongLyricsProvider()
            ),
            lyricsCacheDao = cacheDao
        )
        val gate = LyricsIdentityGate(repository)

        val identity = gate.resolveIdentity(track = track, isrc = null)

        assertTrue(identity is LyricsIdentity.EstimatedTiming)
        assertTrue(identity.lyricsData?.providerId == "known_web")
        assertFalse(identity.lyricsData?.lines.orEmpty().any { it.text == "wrong cached row" })
    }

    @Test
    fun victoryLapFiveKnownWebFailureDoesNotFallThroughToOldLrclibCache() = runBlocking {
        val cacheDao = InMemoryLyricsCacheDao()
        val track = UnifiedTrack(
            title = "Victory Lap Five",
            artist = "Fred again..",
            albumName = "Victory Lap Five",
            coverArtUrl = null,
            durationMs = 344_000L
        )
        val cacheKey = CanonicalIdentityResolver.generateCanonicalId(
            isrc = null,
            _unused = null,
            title = track.title,
            artist = track.artist,
            album = track.albumName,
            durationMs = track.durationMs
        )
        cacheDao.insertLyrics(
            LyricsCacheEntity(
                lyricsKey = cacheKey,
                trackTitle = track.title,
                artist = track.artist,
                isrc = null,
                providerId = "lrclib",
                lyricsJson = """[{"startTimeMs":0,"endTimeMs":4000,"text":"wrong cached row"}]""",
                isSynced = true,
                lastUpdatedAt = System.currentTimeMillis()
            )
        )
        val repository = LyricsRepository(
            providers = listOf(
                KnownWebLyricsProvider { null },
                DurationStrippedWrongLyricsProvider()
            ),
            lyricsCacheDao = cacheDao
        )
        val gate = LyricsIdentityGate(repository)

        val identity = gate.resolveIdentity(track = track, isrc = null)

        assertTrue(identity is LyricsIdentity.Unavailable)
    }

    @Test
    fun staleLrclibCacheIsIgnoredAfterStrictDurationMatchingFix() = runBlocking {
        val cacheDao = InMemoryLyricsCacheDao()
        val track = UnifiedTrack(
            title = "Victory Lap Five",
            artist = "Fred again..",
            albumName = "USB",
            coverArtUrl = null,
            durationMs = 213_000L
        )
        val cacheKey = CanonicalIdentityResolver.generateCanonicalId(
            isrc = null,
            _unused = null,
            title = track.title,
            artist = track.artist,
            album = track.albumName,
            durationMs = track.durationMs
        )
        cacheDao.insertLyrics(
            LyricsCacheEntity(
                lyricsKey = cacheKey,
                trackTitle = track.title,
                artist = track.artist,
                isrc = null,
                providerId = "lrclib",
                lyricsJson = """[{"startTimeMs":0,"endTimeMs":4000,"text":"stale wrong line"}]""",
                isSynced = true,
                lastUpdatedAt = 1L
            )
        )
        val repository = LyricsRepository(
            providers = emptyList(),
            lyricsCacheDao = cacheDao
        )
        val gate = LyricsIdentityGate(repository)

        val identity = gate.resolveIdentity(track = track, isrc = null)

        assertTrue(identity is LyricsIdentity.Unavailable)
    }

    private class PlainLyricsProvider : LyricsProvider {
        override val providerId: String = "plain_test"

        override suspend fun getLyrics(track: UnifiedTrack, isrc: String?): LyricsData =
            LyricsData(
                trackKey = "plain",
                isSynced = false,
                lines = listOf(
                    LyricsLine(startTimeMs = null, text = "Opening line"),
                    LyricsLine(startTimeMs = null, text = "Second line")
                ),
                providerId = providerId,
                sourceLabel = "Plain"
            )
    }

    private class DurationStrippedWrongLyricsProvider : LyricsProvider {
        override val providerId: String = "duration_stripped_wrong_test"

        override suspend fun getLyrics(track: UnifiedTrack, isrc: String?): LyricsData? {
            if (track.durationMs != null) return null
            return LyricsData(
                trackKey = "wrong-victory-lap",
                isSynced = true,
                lines = listOf(
                    LyricsLine(startTimeMs = 0L, endTimeMs = 4_000L, text = "Wrong opening line"),
                    LyricsLine(startTimeMs = 4_000L, endTimeMs = 8_000L, text = "Wrong second line")
                ),
                providerId = providerId,
                sourceLabel = "Wrong synced lyrics"
            )
        }
    }

    private fun victoryLapFiveHtml(): String =
        """
        <html><body><div class="lyric-original">
        Line one<br>Line two<br>Line three<br>Line four<br>
        Line five<br>Line six<br>Line seven<br>Line eight<br>Line nine
        </div></body></html>
        """.trimIndent()

    private class InMemoryLyricsCacheDao : LyricsCacheDao {
        private val byKey = mutableMapOf<String, LyricsCacheEntity>()
        private val byIsrc = mutableMapOf<String, LyricsCacheEntity>()

        override suspend fun getLyrics(key: String): LyricsCacheEntity? = byKey[key]

        override suspend fun getLyricsByIsrc(isrc: String): LyricsCacheEntity? = byIsrc[isrc]

        override suspend fun insertLyrics(lyrics: LyricsCacheEntity) {
            byKey[lyrics.lyricsKey] = lyrics
            lyrics.isrc?.let { byIsrc[it] = lyrics }
        }

        override suspend fun deleteLyrics(key: String) {
            byKey.remove(key)
        }

        override suspend fun deleteLyricsByIsrc(isrc: String) {
            byIsrc.remove(isrc)
        }

        override suspend fun clearAll() {
            byKey.clear()
            byIsrc.clear()
        }
    }
}
