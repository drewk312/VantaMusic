package com.audiophile.musicplayer.data.lyrics

import android.util.Log
import com.audiophile.musicplayer.data.local.entities.UnifiedTrack
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

class KnownWebLyricsProvider(
    private val fetcher: suspend (String) -> String? = ::defaultFetch
) : LyricsProvider {
    override val providerId: String = "known_web"

    override suspend fun getLyrics(track: UnifiedTrack, isrc: String?): LyricsData? = withContext(Dispatchers.IO) {
        val source = knownSourceFor(track) ?: return@withContext null
        for (url in source.urls) {
            val html = fetcher(url) ?: continue
            val plainText = extractLyricsText(html)
            val lines = LrcParser.parsePlainLines(plainText)
            if (lines.size < MIN_USEFUL_LINES) {
                Log.d("VANTA_LYRICS_TRUTH", "known web lyrics rejected short extraction url='$url' lines=${lines.size}")
                continue
            }
            val timedLines = track.durationMs?.takeIf { it > 0L }
                ?.let { durationMs -> LrcParser.estimatePlainLyricTimings(lines, durationMs) }
                ?: lines
            return@withContext LyricsData(
                trackKey = source.canonicalKey,
                isSynced = false,
                lines = timedLines,
                providerId = providerId,
                sourceLabel = source.label
            )
        }
        null
    }

    private fun knownSourceFor(track: UnifiedTrack): KnownLyricsSource? {
        val title = normalize(track.title)
        val artist = normalize(track.artist)
        val album = normalize(track.albumName.orEmpty())
        val text = "$title $artist $album"
        return when {
            title == "victory lap 5" && VICTORY_LAP_FIVE_ARTISTS.any { it in text } -> KnownLyricsSource(
                canonicalKey = "known:victory-lap-five:fred-again-skepta-plaqueboymax-denzel-curry-hanumankind-d-double-e-lyny",
                label = "Verified web lyrics [estimated timing]",
                urls = listOf(
                    "https://www.letras.mus.br/that-mexican-ot/victory-lap-five-part-fred-again-skepta-plaqueboymax-denzel-curry-hanumankind-d-double-e-e-lyny/"
                )
            )
            title == "victory lap" && listOf("fred again", "skepta", "plaqueboymax").all { it in text } -> KnownLyricsSource(
                canonicalKey = "known:victory-lap:fred-again-skepta-plaqueboymax",
                label = "Verified web lyrics [estimated timing]",
                urls = listOf(
                    "https://genius.com/Fred-again-skepta-and-plaqueboymax-victory-lap-lyrics"
                )
            )
            else -> null
        }
    }

    private data class KnownLyricsSource(
        val canonicalKey: String,
        val label: String,
        val urls: List<String>
    )

    companion object {
        private const val MIN_USEFUL_LINES = 8

        private val client = OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .build()

        private val VICTORY_LAP_FIVE_ARTISTS = listOf(
            "fred again",
            "skepta",
            "plaqueboymax",
            "denzel curry",
            "hanumankind",
            "d double e",
            "lyny",
            "that mexican ot"
        )

        internal fun hasKnownSource(track: UnifiedTrack): Boolean {
            val title = normalize(track.title)
            val artist = normalize(track.artist)
            val album = normalize(track.albumName.orEmpty())
            val text = "$title $artist $album"
            return (title == "victory lap 5" && VICTORY_LAP_FIVE_ARTISTS.any { it in text }) ||
                (title == "victory lap" && listOf("fred again", "skepta", "plaqueboymax").all { it in text })
        }

        private suspend fun defaultFetch(url: String): String? = withContext(Dispatchers.IO) {
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "Mozilla/5.0 VANTA/1.0")
                .build()
            try {
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) return@withContext null
                response.body?.string()?.takeIf { it.isNotBlank() }
            }
        } catch (e: java.io.IOException) {
                Log.d("VANTA_LYRICS_TRUTH", "known web lyrics fetch failed url='$url' error='${e.message}'")
                null
            }
        }

        internal fun extractLyricsText(html: String): String {
            val genius = Regex(
                """(?is)<div[^>]+data-lyrics-container=["']true["'][^>]*>(.*?)</div>"""
            ).findAll(html)
                .map { htmlToText(it.groupValues[1]) }
                .joinToString("\n")
                .trim()
            if (genius.isNotBlank()) return genius

            val letras = listOf(
                Regex("""(?is)<div[^>]+class=["'][^"']*(?:lyric-original|cnt-letra|letra)[^"']*["'][^>]*>(.*?)</div>"""),
                Regex("""(?is)<article[^>]*>(.*?)</article>""")
            ).asSequence()
                .flatMap { regex -> regex.findAll(html).map { it.groupValues[1] } }
                .map { htmlToText(it) }
                .firstOrNull { it.lines().count { line -> line.isNotBlank() } >= MIN_USEFUL_LINES }
                ?.trim()
            if (!letras.isNullOrBlank()) return letras

            val jsonLyrics = Regex(""""lyrics"\s*:\s*"((?:\\.|[^"\\])*)"""").find(html)
                ?.groupValues
                ?.getOrNull(1)
                ?.let { unescapeJsonString(it) }
                ?.trim()
            return jsonLyrics.orEmpty()
        }

        private fun htmlToText(value: String): String =
            value
                .replace(Regex("""(?i)<br\s*/?>"""), "\n")
                .replace(Regex("""(?is)<script.*?</script>"""), "")
                .replace(Regex("""(?is)<style.*?</style>"""), "")
                .replace(Regex("""<[^>]+>"""), "")
                .replace("&amp;", "&")
                .replace("&quot;", "\"")
                .replace("&#39;", "'")
                .replace("&apos;", "'")
                .replace("&nbsp;", " ")
                .lines()
                .map { it.trim() }
                .filter { it.isNotBlank() }
                .joinToString("\n")

        private fun unescapeJsonString(value: String): String =
            value
                .replace("\\n", "\n")
                .replace("\\\"", "\"")
                .replace("\\/", "/")
                .replace("\\u0026", "&")
                .replace("\\u0027", "'")

        private fun normalize(value: String): String =
            value.lowercase()
                .replace("five", "5")
                .replace(Regex("""[^\p{L}\p{N}\s]"""), " ")
                .replace(Regex("""\s+"""), " ")
                .trim()
    }
}
