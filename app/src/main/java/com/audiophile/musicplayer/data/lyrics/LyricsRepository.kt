package com.audiophile.musicplayer.data.lyrics

import com.audiophile.musicplayer.data.local.entities.UnifiedTrack
import com.audiophile.musicplayer.data.canonical.CanonicalIdentityResolver
import com.audiophile.musicplayer.data.display.DisplayMetadataCleaner
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
    companion object {
        private const val STRICT_LRCLIB_CACHE_CUTOFF_MS = 1_783_468_800_000L // 2026-07-08T00:00:00Z
    }

    suspend fun getLyrics(track: UnifiedTrack, isrc: String? = null): LyricsData? = withContext(Dispatchers.IO) {
        if (!VocalRecordingClassifier.lyricsExpected(track.title, track.artist, track.albumName)) {
            return@withContext null
        }

        val cacheKey = CanonicalIdentityResolver.generateCanonicalId(isrc, null, track.title, track.artist, track.albumName, track.durationMs)
        val titleVariants = buildTitleVariants(track.title)

        // Known exact web fallbacks are intentionally checked before cache so an older
        // LRCLib row for a nearby title cannot resurrect the wrong lyrics.
        for (provider in providers.filter { it.providerId == "known_web" }) {
            val lyrics = tryTitleVariants(provider, track, isrc, titleVariants)
            if (lyrics != null) return@withContext lyrics
        }
        if (KnownWebLyricsProvider.hasKnownSource(track)) {
            return@withContext null
        }
        
        // 1. Check cache by ISRC — only trust synced rows; upgrade plain cache later
        if (!isrc.isNullOrBlank()) {
            val cachedByIsrc = lyricsCacheDao.getLyricsByIsrc(isrc.trim().uppercase())
            if (cachedByIsrc != null) {
                if (isStrictCacheUsable(cachedByIsrc)) {
                    val cached = deserialize(cachedByIsrc, track.durationMs)
                    if (cached.isSynced && cached.lines.any { it.startTimeMs != null }) {
                        return@withContext cached
                    }
                } else {
                    lyricsCacheDao.deleteLyricsByIsrc(isrc.trim().uppercase())
                }
            }
        }
        
        // 2. Check cache by strict cacheKey — upgrade plain cache when synced lyrics may exist
        val cachedByKey = lyricsCacheDao.getLyrics(cacheKey)
        if (cachedByKey != null) {
            if (isStrictCacheUsable(cachedByKey)) {
                val cached = deserialize(cachedByKey, track.durationMs)
                if (cached.isSynced && cached.lines.any { it.startTimeMs != null }) {
                    return@withContext cached
                }
            } else {
                lyricsCacheDao.deleteLyrics(cacheKey)
            }
        }
        
        // 4. Fetch from providers in priority order, trying title variants
        for (provider in providers.filterNot { it.providerId == "known_web" }) {
            val lyrics = tryTitleVariants(provider, track, isrc, titleVariants)
            if (lyrics != null) return@withContext lyrics
        }
        
        return@withContext null
    }

    private suspend fun tryTitleVariants(
        provider: LyricsProvider,
        track: UnifiedTrack,
        isrc: String?,
        titleVariants: List<String>
    ): LyricsData? {
        val cacheKey = CanonicalIdentityResolver.generateCanonicalId(isrc, null, track.title, track.artist, track.albumName, track.durationMs)
        val allTitles = (listOf(track.title) + titleVariants).distinct()
        for (variantTitle in allTitles) {
            val variantTrack = if (variantTitle == track.title) track else track.copy(title = variantTitle)
            
            // Try exact match
            var lyrics = provider.getLyrics(variantTrack, isrc)
            
            // Fallback strip album metadata, but keep duration as a hard sync guard.
            // Dropping duration lets nearby LRCLib rows pass for songs with similar titles,
            // which is exactly how wrong/off-sync lyrics become trusted.
            if (lyrics == null && !variantTrack.albumName.isNullOrBlank()) {
                lyrics = provider.getLyrics(variantTrack.copy(albumName = null), isrc)
            }

            if (lyrics != null) {
                val timedLines = when {
                    lyrics.isSynced -> lyrics.lines
                    track.durationMs != null && track.durationMs > 0L ->
                        LrcParser.estimatePlainLyricTimings(lyrics.lines, track.durationMs)
                    else -> lyrics.lines
                }
                val keyedLyrics = lyrics.copy(
                    trackKey = cacheKey,
                    lines = timedLines,
                    isSynced = lyrics.isSynced
                )
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
                return keyedLyrics
            }
        }
        return null
    }

    private fun isStrictCacheUsable(entity: LyricsCacheEntity): Boolean {
        if (entity.providerId != "lrclib") return true
        return entity.lastUpdatedAt >= STRICT_LRCLIB_CACHE_CUTOFF_MS
    }

    private fun buildTitleVariants(title: String): List<String> {
        val variants = mutableListOf<String>()
        val t = title.trim()
        val displayCleaned = DisplayMetadataCleaner.cleanTitle(t)
        if (displayCleaned.isNotBlank() && displayCleaned != t) {
            variants.add(displayCleaned)
        }
        // Feat-stripped variant first — the most common cause of missed matches.
        val featStripped = t
            .replace(Regex("""\s*[\[(]\s*(?:feat\.?|featuring|ft\.?|with)\s+[^\])]*[\])]""", RegexOption.IGNORE_CASE), "")
            .replace(Regex("""\s+\b(?:feat\.?|featuring|ft\.?)\s+.*$""", RegexOption.IGNORE_CASE), "")
            .replace(Regex("""\s+"""), " ")
            .trim()
        if (featStripped.isNotBlank() && featStripped != t) {
            variants.add(featStripped)
        }
        // Number-to-word: "5" -> "Five", "5" -> "V"
        val numberMappings = mapOf(
            "5" to listOf("Five", "V"),
            "4" to listOf("Four", "IV"),
            "3" to listOf("Three", "III"),
            "2" to listOf("Two", "II"),
            "1" to listOf("One", "I"),
            "0" to listOf("Zero"),
            "6" to listOf("Six"),
            "7" to listOf("Seven"),
            "8" to listOf("Eight"),
            "9" to listOf("Nine"),
            "10" to listOf("Ten", "X")
        )
        // Word-to-number: "Five" -> "5", "Five" -> "5"
        val wordMappings = mapOf(
            "five" to listOf("5"),
            "four" to listOf("4"),
            "three" to listOf("3"),
            "two" to listOf("2"),
            "one" to listOf("1"),
            "zero" to listOf("0"),
            "six" to listOf("6"),
            "seven" to listOf("7"),
            "eight" to listOf("8"),
            "nine" to listOf("9"),
            "ten" to listOf("10")
        )
        // Try replacing number words with digits
        val lower = t.lowercase()
        for ((word, replacements) in wordMappings) {
            if (lower.contains(word)) {
                val regex = Regex(word, RegexOption.IGNORE_CASE)
                for (repl in replacements) {
                    variants.add(t.replace(regex, repl))
                }
            }
        }
        // Try replacing digits with number words
        val digitPattern = Regex("""\b\d+""")
        digitPattern.findAll(t).forEach { match ->
            val digit = match.value
            for (word in (numberMappings[digit] ?: emptyList())) {
                variants.add(t.replaceRange(match.range, word))
            }
        }
        return variants.distinct()
    }
    
    private fun deserialize(entity: LyricsCacheEntity, durationMs: Long? = null): LyricsData {
        val type = TypeToken.getParameterized(List::class.java, LyricsLine::class.java).type
        val lines: List<LyricsLine> = runCatching {
            gson.fromJson<List<LyricsLine>>(entity.lyricsJson, type)
        }.getOrNull() ?: emptyList()
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

    suspend fun searchLyrics(query: String, limit: Int = 8): List<com.audiophile.musicplayer.data.canonical.CanonicalTrack> = withContext(Dispatchers.IO) {
        val q = query.trim()
        if (q.length < 4) return@withContext emptyList()
        val lrclib = providers.filterIsInstance<LRCLibLyricsProvider>().firstOrNull() ?: LRCLibLyricsProvider()
        lrclib.searchByLyrics(q, limit)
    }
}
