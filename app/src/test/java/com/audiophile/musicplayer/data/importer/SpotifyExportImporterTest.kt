package com.audiophile.musicplayer.data.importer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.ByteArrayInputStream
import java.time.Instant
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class SpotifyExportImporterTest {

    private fun zipOf(files: Map<String, String>): ByteArrayInputStream {
        val bytes = java.io.ByteArrayOutputStream()
        ZipOutputStream(bytes).use { zip ->
            files.forEach { (name, content) ->
                zip.putNextEntry(ZipEntry(name))
                zip.write(content.toByteArray(Charsets.UTF_8))
                zip.closeEntry()
            }
        }
        return ByteArrayInputStream(bytes.toByteArray())
    }

    private val sampleRow = """
        {"ts":"2023-05-01T12:34:56Z","username":"u","platform":"iOS",
         "ms_played":184032,"conn_country":"US","ip_addr_decrypted":"0.0.0.0",
         "user_agent_decrypted":"ok","master_metadata_track_name":"Shape of You",
         "master_metadata_album_artist_name":"Ed Sheeran",
         "master_metadata_album_album_name":"divide",
         "spotify_track_uri":"spotify:track:7qiZfU4dY1lWllzX7mPBI3",
         "episode_name":null,"episode_show_name":null,
         "spotify_episode_uri":null,"reason_start":"trackdone","reason_end":"trackdone","shuffle":false,
         "skipped":false,"offline":false,"offline_timestamp":0,"incognito_mode":false}
    """.trimIndent()

    @Test
    fun extractHistoryRows_parsesFullStreamingHistoryFields() {
        val history = "[$sampleRow]"
        val input = zipOf(mapOf("Streaming_History_Audio_2023_0.json" to history))

        val rows = SpotifyExportImporter.extractHistoryRows(input)

        assertEquals(1, rows.size)
        val row = rows.first()
        assertEquals(Instant.parse("2023-05-01T12:34:56Z").toEpochMilli(), row.ts)
        assertEquals("Shape of You", row.trackName)
        assertEquals("Ed Sheeran", row.artistName)
        assertEquals("divide", row.albumName)
        assertEquals("spotify:track:7qiZfU4dY1lWllzX7mPBI3", row.spotifyTrackUri)
        assertEquals(184_032L, row.msPlayed)
        assertEquals(false, row.skipped)
        assertEquals("trackdone", row.reasonStart)
        assertEquals("trackdone", row.reasonEnd)
    }

    @Test
    fun extractHistoryRows_dropsPodcastAndZeroMsRows() {
        val podcast = """
            {"ts":"2023-05-01T12:34:56Z","username":"u","platform":"web player",
             "ms_played":12345,"master_metadata_track_name":null,
             "master_metadata_album_artist_name":null,
             "spotify_track_uri":null,"episode_name":"An Episode",
             "episode_show_name":"A Show","spotify_episode_uri":"spotify:episode:abc",
             "reason_start":"appload","reason_end":"unexpected-exit","skipped":false}
        """.trimIndent()
        val zeroMs = """
            {"ts":"2023-05-01T12:34:57Z","username":"u","platform":"web player",
             "ms_played":0,"master_metadata_track_name":"Ad",
             "master_metadata_album_artist_name":"Spotify","spotify_track_uri":null,
             "reason_start":"appload","reason_end":"unexpected-exit","skipped":false}
        """.trimIndent()
        val history = "[$podcast,$zeroMs]"
        val input = zipOf(mapOf("Streaming_History_Audio_2023_0.json" to history))

        val rows = SpotifyExportImporter.extractHistoryRows(input)

        assertEquals(0, rows.size)
    }

    @Test
    fun parseHistoryFields_marksSkippedRows() {
        val history = """
            {"ts":"2023-05-01T12:34:56Z","username":"u","platform":"android",
             "ms_played":45210,"master_metadata_track_name":"Blinding Lights",
             "master_metadata_album_artist_name":"The Weeknd",
             "master_metadata_album_album_name":"After Hours",
             "spotify_track_uri":"spotify:track:0VjIjW4GlUZAMYd2vXMi3b",
             "reason_start":"clickrow","reason_end":"fwdbtn","skipped":true}
        """.trimIndent()
        val rows = SpotifyExportImporter.parseHistoryText(history)

        assertEquals(1, rows.size)
        val row = rows.first()
        assertEquals(true, row.skipped)
        assertEquals("fwdbtn", row.reasonEnd)
        assertEquals("clickrow", row.reasonStart)
        assertEquals(45_210L, row.msPlayed)
    }

    @Test
    fun parseHistoryFields_rejectsMissingTimestamp() {
        val noTs = """
            {"username":"u","ms_played":9999,"master_metadata_track_name":"X",
             "master_metadata_album_artist_name":"Y","skipped":false}
        """.trimIndent()

        assertNull(SpotifyExportImporter.parseHistoryText(noTs).firstOrNull())
    }

    @Test
    fun parseHistoryText_survivesTrailingNewlineAndWhitespace() {
        val history = "  [\n$sampleRow\n]\n  "

        val rows = SpotifyExportImporter.parseHistoryText(history)

        assertEquals(1, rows.size)
        assertEquals("Shape of You", rows.first().trackName)
    }
}