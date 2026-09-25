package com.audiophile.musicplayer.data.importer

import com.audiophile.musicplayer.data.importer.AppleLibraryImporter.APPLE_MUSIC
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AppleLibraryImporterTest {

    private val plistLibrary = """
        <?xml version="1.0" encoding="UTF-8"?>
        <!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
        <plist version="1.0">
        <dict>
        	<key>Tracks</key>
        	<dict>
        		<key>1001</key>
        		<dict>
        			<key>Track ID</key><integer>1001</integer>
        			<key>Name</key><string>Songs From The Second Floor</string>
        			<key>Artist</key><string>Samurai &amp; The Bandits</string>
        			<key>Album</key><string>Midnight Mechanical</string>
        			<key>Genre</key><string>Electronic</string>
        			<key>Total Time</key><integer>210000</integer>
        			<key>Play Count</key><integer>12</integer>
        			<key>Play Date</key><integer>3660681600</integer>
        			<key>Date Added</key><date>2020-01-01T00:00:00Z</date>
        		</dict>
        		<key>1002</key>
        		<dict>
        			<key>Track ID</key><integer>1002</integer>
        			<key>Name</key><string>Heavy Rotation</string>
        			<key>Artist</key><string>Loose Change</string>
        			<key>Album</key><string>Pocket</string>
        			<key>Total Time</key><integer>177003</integer>
        			<key>Play Count</key><integer>1</integer>
        			<key>Play Date</key><integer>1577836800</integer>
        			<key>Loved</key><true/>
        		</dict>
        		<key>1003</key>
        		<dict>
        			<key>Track ID</key><integer>1003</integer>
        			<key>Name</key><string>Never Played</string>
        			<key>Artist</key><string>Ghost Respekt</string>
        			<key>Total Time</key><integer>200000</integer>
        		</dict>
        		<key>1004</key>
        		<dict>
        			<key>Track ID</key><integer>1004</integer>
        			<key>Name</key><string>On Hold</string>
        			<key>Artist</key><string>Ocean Blue</string>
        			<key>Total Time</key><integer>180000</integer>
        			<key>Play Count</key><integer>2</integer>
        			<key>Date Added</key><date>2019-06-01T08:00:00Z</date>
        		</dict>
        	</dict>
        </dict>
        </plist>
    """.trimIndent()

    private val jsonLibrary = """
        {
          "Library": {
            "Application Version": "12.9.0.167",
            "Tracks": {
              "1001": {
                "Track ID": 1001,
                "Name": "Mint Condition",
                "Artist": "Bad Habit",
                "Album": "The Long Wait",
                "Genre": "Indie",
                "Total Time": 251320,
                "Play Count": 4,
                "Play Date": 3660681600,
                "Loved": true
              },
              "1002": {
                "Track ID": "1002",
                "Name": "Blue \"Sky\" Tattoo",
                "Artist": "Night Drifters",
                "Album": "Ocean Park",
                "Total Time": 189000,
                "Play Count": 0,
                "Date Added": "2021-03-15T10:30:00Z"
              }
            }
          }
        }
    """.trimIndent()

    @Test
    fun plistLibrary_parsesTracksAndDecodesEntities() {
        val library = AppleLibraryImporter.parseLibrary(plistLibrary)

        assertNotNull(library)
        assertEquals("xml", library?.source)
        assertEquals(4, library?.tracks?.size)
        val first = library!!.tracks.first()
        assertEquals("1001", first.trackId)
        assertEquals("Songs From The Second Floor", first.name)
        assertEquals("Samurai & The Bandits", first.artist)
        assertEquals("Midnight Mechanical", first.album)
        assertEquals("Electronic", first.genre)
        assertEquals(210_000L, first.totalTimeMs)
        assertEquals(12, first.playCount)
    }

    @Test
    fun plistLibrary_rollsPlayCountsIntoHistoryRows() {
        val rows = AppleLibraryImporter.toHistoryRows(AppleLibraryImporter.parseLibrary(plistLibrary))

        assertEquals(3, rows.size)
        assertTrue(rows.none { it.title == "Never Played" })

        val secondFloor = rows.first { it.sourceTrackId == "1001" }
        assertEquals(12, secondFloor.playCount)
        assertEquals(210_000L * 12, secondFloor.msPlayed)
        assertEquals(210_000L, secondFloor.durationMs!!)
        assertEquals(1_577_836_800_000L, secondFloor.startedAt)
        assertNull(secondFloor.reasonEnd)

        val heavy = rows.first { it.sourceTrackId == "1002" }
        assertEquals(1, heavy.playCount)
        assertEquals(177_003L, heavy.msPlayed)
        assertEquals("loved", heavy.reasonEnd)
        assertEquals(1_577_836_800_000L, heavy.startedAt)

        val onHold = rows.first { it.sourceTrackId == "1004" }
        assertEquals(2, onHold.playCount)
        assertEquals(360_000L, onHold.msPlayed)
        assertEquals(1_559_376_000_000L, onHold.startedAt)
    }

    @Test
    fun plistLibrary_marksPlatformAndSource() {
        val row = AppleLibraryImporter.toHistoryRows(AppleLibraryImporter.parseLibrary(plistLibrary)).first()

        assertEquals(APPLE_MUSIC, row.platform)
        assertEquals(APPLE_MUSIC, row.providerId)
        assertEquals("1001", row.sourceTrackId)
        assertEquals("apple:import", row.reasonStart)
        assertEquals(false, row.skipped)
    }

    @Test
    fun jsonLibrary_objectKeyedTracks_parsesPlayedAndDropsUnplayed() {
        val library = AppleLibraryImporter.parseLibrary(jsonLibrary)

        assertNotNull(library)
        assertEquals("json", library?.source)
        assertEquals(2, library?.tracks?.size)
        val rows = AppleLibraryImporter.toHistoryRows(library)
        assertEquals(1, rows.size)
        val row = rows.first()
        assertEquals("Mint Condition", row.title)
        assertEquals("Bad Habit", row.artist)
        assertEquals(4, row.playCount)
        assertEquals(251_320L * 4, row.msPlayed)
        assertEquals("loved", row.reasonEnd)
        assertEquals("1001", row.sourceTrackId)
        assertEquals(1_577_836_800_000L, row.startedAt)
    }

    @Test
    fun jsonLibrary_arrayVariant_parsesAllTracks() {
        val json = """
            {
              "Tracks": [
                {"Track ID": 5, "Name": "Kept", "Artist": "The Keeper", "Total Time": 120000, "Play Count": 2},
                {"Track ID": 6, "Name": "Midnight", "Artist": "The Keeper", "Album": "Nightwork", "Total Time": 90000, "Play Count": 3}
              ]
            }
        """.trimIndent()

        val library = AppleLibraryImporter.parseLibrary(json)

        assertNotNull(library)
        assertEquals(2, library?.tracks?.size)
        val rows = AppleLibraryImporter.toHistoryRows(library)
        assertEquals(2, rows.size)
        assertEquals(240_000L, rows.first { it.sourceTrackId == "5" }.msPlayed)
        assertEquals(270_000L, rows.first { it.sourceTrackId == "6" }.msPlayed)
    }

    @Test
    fun parseLibrary_rejectsNonAppleInput() {
        assertNull(AppleLibraryImporter.parseLibrary(null))
        assertNull(AppleLibraryImporter.parseLibrary(""))
        assertNull(AppleLibraryImporter.parseLibrary("<xml><playlist><song/></playlist></xml>"))
        assertNull(AppleLibraryImporter.parseLibrary("""{"songs": []}"""))
        assertTrue(AppleLibraryImporter.toHistoryRows(null).isEmpty())
        assertTrue(AppleLibraryImporter.toHistoryRows(AppleLibraryImporter.parseLibrary("<plist><key>Tracks</key><dict/></plist>")).isEmpty())
    }
}