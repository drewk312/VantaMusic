package com.audiophile.musicplayer.data.lyrics

import com.audiophile.musicplayer.data.local.entities.UnifiedTrack
import com.audiophile.musicplayer.data.canonical.CanonicalIdentityResolver
import com.audiophile.musicplayer.data.source.VocalRecordingClassifier
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class LyricsRepository(
    private val providers: List<LyricsProvider>,
    private val lyricsCacheDao: LyricsCacheDao,
    private val gson: Gson = Gson()
) {
    suspend fun getLyrics(track: UnifiedTrack, isrc: String? = null): LyricsData? = withContext(Dispatchers.IO) {
        if (!VocalRecordingClassifier.lyricsExpected(track.title, track.artist, track.albumName)) {
            return@withContext null
        }

        val cacheKey = CanonicalIdentityResolver.generateCanonicalId(isrc, null, track.title, track.artist, track.albumName, track.durationMs)
        
        // 1. Check cache by ISRC — only trust synced rows; upgrade plain cache later
        if (!isrc.isNullOrBlank()) {
            val cachedByIsrc = lyricsCacheDao.getLyricsByIsrc(isrc.trim().uppercase())
            if (cachedByIsrc != null) {
                val cached = deserialize(cachedByIsrc, track.durationMs)
                if (cached.isSynced && cached.lines.any { it.startTimeMs != null }) {
                    return@withContext cached
                }
            }
        }
        
        // 2. Check cache by strict cacheKey — upgrade plain cache when synced lyrics may exist
        val cachedByKey = lyricsCacheDao.getLyrics(cacheKey)
        if (cachedByKey != null) {
            val cached = deserialize(cachedByKey, track.durationMs)
            if (cached.isSynced && cached.lines.any { it.startTimeMs != null }) {
                return@withContext cached
            }
        }
        
        // 3. Fetch from providers in priority order
        for (provider in providers) {
            var lyrics = provider.getLyrics(track, isrc)
            
            // Fallback: title + artist + album (strip duration)
            if (lyrics == null && track.durationMs != null) {
                val trackWithoutDuration = track.copy(durationMs = null)
                lyrics = provider.getLyrics(trackWithoutDuration, isrc)
            }
            
            // Fallback: title + artist (strip album and duration)
            if (lyrics == null && (!track.albumName.isNullOrBlank() || track.durationMs != null)) {
                val simplifiedTrack = track.copy(albumName = null, durationMs = null)
                lyrics = provider.getLyrics(simplifiedTrack, isrc)
            }

            if (lyrics != null) {
                val timedLines = when {
                    lyrics.isSynced -> lyrics.lines
                    track.durationMs != null && track.durationMs > 0L ->
                        LrcParser.estimatePlainLyricTimings(lyrics.lines, track.durationMs)
                    else -> lyrics.lines
                }
                val hasTimings = timedLines.any { it.startTimeMs != null }
                val keyedLyrics = lyrics.copy(
                    trackKey = cacheKey,
                    lines = timedLines,
                    isSynced = lyrics.isSynced || hasTimings
                )
                // Save to cache
                val entity = LyricsCacheEntity(
                    lyricsKey = cacheKey,
                    trackTitle = track.title,
                    artist = track.artist,
                    isrc = isrc,
                    providerId = provider.providerId,
                    lyricsJson = gson.toJson(keyedLyrics.lines),
                    isSynced = keyedLyrics.isSynced
                )
                lyricsCacheDao.insertLyrics(entity)
                return@withContext keyedLyrics
            }
        }
        
        return@withContext null
    }
    
    private fun deserialize(entity: LyricsCacheEntity, durationMs: Long? = null): LyricsData {
        val type = object : TypeToken<List<LyricsLine>>() {}.type
        val lines: List<LyricsLine> = gson.fromJson(entity.lyricsJson, type)
        val withEnds = if (entity.isSynced) applyLineEndTimes(lines) else lines
        val normalizedLines = when {
            withEnds.any { it.startTimeMs != null } -> withEnds
            durationMs != null && durationMs > 0L -> LrcParser.estimatePlainLyricTimings(withEnds, durationMs)
            else -> withEnds
        }
        val hasTimings = normalizedLines.any { it.startTimeMs != null }
        return LyricsData(
            trackKey = entity.lyricsKey,
            isSynced = entity.isSynced && hasTimings,
            lines = normalizedLines,
            providerId = entity.providerId,
            sourceLabel = "Cached (${entity.providerId})"
        )
    }

    suspend fun invalidateCache(cacheKey: String, isrc: String? = null) = withContext(Dispatchers.IO) {
        lyricsCacheDao.deleteLyrics(cacheKey)
        if (!isrc.isNullOrBlank()) {
            lyricsCacheDao.deleteLyricsByIsrc(isrc.trim().uppercase())
        }
    }
}
