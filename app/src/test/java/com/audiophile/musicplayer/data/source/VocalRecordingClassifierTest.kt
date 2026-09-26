package com.audiophile.musicplayer.data.source

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VocalRecordingClassifierTest {

    @Test
    fun rejectsRockabyeLullabyAlbum() {
        assertFalse(
            VocalRecordingClassifier.shouldAllowInCatalog(
                title = "Blinding Lights",
                artist = "Rockabye Baby!",
                album = "Lullaby Renditions of the Weeknd"
            )
        )
        assertFalse(
            VocalRecordingClassifier.lyricsExpected(
                title = "Blinding Lights",
                artist = "Rockabye Baby!",
                album = "Lullaby Renditions of the Weeknd"
            )
        )
    }

    @Test
    fun rejectsPianoTitleVariant() {
        assertFalse(
            VocalRecordingClassifier.shouldAllowInCatalog(
                title = "Blinding lights The weeknd piano",
                artist = "Fredy'Sam",
                album = "Piano Music"
            )
        )
    }

    @Test
    fun rejectsLiveAlbumUnlessRequested() {
        assertFalse(
            VocalRecordingClassifier.shouldAllowInCatalog(
                title = "Blinding Lights",
                artist = "The Weeknd",
                album = "Live At SoFi Stadium"
            )
        )
        assertTrue(
            VocalRecordingClassifier.shouldAllowInCatalog(
                title = "Blinding Lights",
                artist = "The Weeknd",
                album = "Live At SoFi Stadium",
                userQuery = "blinding lights live"
            )
        )
    }

    @Test
    fun rejectsSymphonyOrchestraPerformer() {
        assertFalse(
            VocalRecordingClassifier.shouldAllowInCatalog(
                title = "Blinding Lights",
                artist = "Roma Symphony Orchestra",
                album = "RSO Performs The Weeknd"
            )
        )
    }

    @Test
    fun allowsStudioVocalMaster() {
        assertTrue(
            VocalRecordingClassifier.shouldAllowInCatalog(
                title = "Blinding Lights",
                artist = "The Weeknd",
                album = "After Hours"
            )
        )
        assertTrue(
            VocalRecordingClassifier.lyricsExpected(
                title = "Blinding Lights",
                artist = "The Weeknd",
                album = "After Hours"
            )
        )
    }

    @Test
    fun allowsInstrumentalWhenUserAsked() {
        assertTrue(
            VocalRecordingClassifier.shouldAllowInCatalog(
                title = "Blinding Lights (Instrumental)",
                artist = "The Weeknd",
                album = "Instrumental Version",
                userQuery = "blinding lights instrumental"
            )
        )
    }
    @Test
    fun allowsPianoManByDefault() {
        assertTrue(
            VocalRecordingClassifier.shouldAllowInCatalog(
                title = "Piano Man",
                artist = "Billy Joel",
                album = "Piano Man"
            )
        )
    }

    @Test
    fun rejectsOriginallyPerformedByAndBackingTracks() {
        assertFalse(
            VocalRecordingClassifier.shouldAllowInCatalog(
                title = "Halo Originally Performed By Beyoncé",
                artist = "The Backing Tracks",
                album = "Karaoke Versions"
            )
        )
        assertTrue(
            VocalRecordingClassifier.shouldAllowInCatalog(
                title = "Texas Hold 'Em",
                artist = "Beyoncé",
                album = "Cowboy Carter"
            )
        )
    }

    @Test
    fun allowsWhiskeyLullabyByDefault() {
        assertTrue(
            VocalRecordingClassifier.shouldAllowInCatalog(
                title = "Whiskey Lullaby (feat. Alison Krauss)",
                artist = "Brad Paisley",
                album = "Mud on the Tires"
            )
        )
        assertTrue(
            VocalRecordingClassifier.lyricsExpected(
                title = "Whiskey Lullaby (feat. Alison Krauss)",
                artist = "Brad Paisley",
                album = "Mud on the Tires"
            )
        )
    }
}
