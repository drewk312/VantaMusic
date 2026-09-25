package com.audiophile.musicplayer.data.source

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SourceIdentityGateTest {

    private val weekndIdentity = SelectedRecordingIdentity(
        title = "Blinding Lights",
        artist = "The Weeknd",
        durationMs = 200_000L
    )

    @Test
    fun vocalBeatsInstrumental() {
        val vocal = SourceIdentityGate.evaluateSearchResult(
            selected = weekndIdentity,
            candidate = searchResult("Blinding Lights", "The Weeknd")
        )
        val instrumental = SourceIdentityGate.evaluateSearchResult(
            selected = weekndIdentity,
            candidate = searchResult("Blinding Lights (Instrumental)", "The Weeknd")
        )

        assertTrue(vocal.accepted)
        assertFalse(instrumental.accepted)
        assertTrue(vocal.score > instrumental.score)
    }

    @Test
    fun instrumentalRejectedForNormalQuery() {
        val evaluation = SourceIdentityGate.evaluateSearchResult(
            selected = weekndIdentity.copy(userQuery = "Blinding Lights"),
            candidate = searchResult("Blinding Lights", "The Weeknd", album = "Instrumental Version")
        )
        assertFalse(evaluation.accepted)
    }

    @Test
    fun instrumentalAllowedWhenExplicitInQuery() {
        val evaluation = SourceIdentityGate.evaluateSearchResult(
            selected = weekndIdentity.copy(userQuery = "Blinding Lights instrumental"),
            candidate = searchResult("Blinding Lights (Instrumental)", "The Weeknd")
        )
        assertTrue(evaluation.accepted)
    }

    @Test
    fun karaokeRejectedForStayinAlive() {
        val evaluation = SourceIdentityGate.evaluateSearchResult(
            selected = SelectedRecordingIdentity("Stayin' Alive", "Bee Gees"),
            candidate = searchResult("Stayin Alive Karaoke", "Karaoke Artist")
        )
        assertFalse(evaluation.accepted)
    }

    @Test
    fun coverFlacLosesToStudioMp3() {
        val studio = SourceIdentityGate.evaluate(
            selected = weekndIdentity,
            candidateTitle = "Blinding Lights",
            candidateArtist = "The Weeknd",
            bitrateKbps = 320
        )
        val cover = SourceIdentityGate.evaluate(
            selected = weekndIdentity,
            candidateTitle = "Blinding Lights",
            candidateArtist = "Cover Band",
            candidateAlbum = "Cover Versions",
            bitrateKbps = 1411
        )
        assertTrue(studio.accepted)
        assertFalse(cover.accepted)
        assertTrue(studio.score > cover.score)
    }

    @Test
    fun liveRejectedForNormalSong() {
        val evaluation = SourceIdentityGate.evaluateSearchResult(
            selected = SelectedRecordingIdentity("Stayin' Alive", "Bee Gees"),
            candidate = searchResult("Stayin Alive", "Bee Gees", album = "Live at Wembley")
        )
        assertFalse(evaluation.accepted)
    }

    @Test
    fun exactProviderMatchWins() {
        val evaluation = SourceIdentityGate.evaluateSearchResult(
            selected = weekndIdentity.copy(
                preferredProviderId = "qobuz_gateway",
                preferredExternalTrackId = "12345"
            ),
            candidate = searchResult("Blinding Lights", "The Weeknd", providerId = "qobuz_gateway", id = "12345")
        )
        assertTrue(evaluation.accepted)
        assertTrue(evaluation.score >= 500)
    }

    @Test
    fun officialVideoTitleMatchesSelectedRecording() {
        val evaluation = SourceIdentityGate.evaluateSearchResult(
            selected = SelectedRecordingIdentity("Piano Man", "Billy Joel", durationMs = 336_000L),
            candidate = searchResult(
                title = "Billy Joel - Piano Man (Official HD Video)",
                artist = "Billy Joel",
                durationMs = 342_000L,
                providerId = "youtube_music"
            )
        )

        assertTrue(evaluation.accepted)
    }

    @Test
    fun pianoInSongTitleIsNotRejectedAsPianoVariant() {
        val evaluation = SourceIdentityGate.evaluateSearchResult(
            selected = SelectedRecordingIdentity("Piano Man", "Billy Joel"),
            candidate = searchResult("Piano Man", "Billy Joel")
        )

        assertTrue(evaluation.accepted)
    }

    @Test
    fun wrongSongIsRejectedEvenWhenProviderHasStream() {
        val evaluation = SourceIdentityGate.evaluateSearchResult(
            selected = SelectedRecordingIdentity("Piano Man", "Billy Joel"),
            candidate = searchResult("Uptown Girl", "Billy Joel", providerId = "youtube_music")
        )

        assertFalse(evaluation.accepted)
    }

    @Test
    fun wrongArtistIsRejectedEvenWithSameTitle() {
        val evaluation = SourceIdentityGate.evaluateSearchResult(
            selected = SelectedRecordingIdentity("Piano Man", "Billy Joel"),
            candidate = searchResult("Piano Man", "Piano Tribute Players", providerId = "youtube_music")
        )

        assertFalse(evaluation.accepted)
    }

    @Test
    fun radioVariantFilterRejectsInstrumental() {
        val rejected = VariantClassifier.isRejectedForStudioIntent(
            title = "Blinding Lights",
            artist = "The Weeknd",
            album = "Instrumental",
            userQuery = null
        )
        assertTrue(rejected.first)
    }

    @Test
    fun catalogProviderOutranksYouTube() {
        assertTrue(SourceIdentityGate.playbackProviderRank("qobuz_tidal") > SourceIdentityGate.playbackProviderRank("youtube_music"))
        assertTrue(SourceIdentityGate.isCatalogPlaybackProvider("qobuz_tidal"))
        assertTrue(SourceIdentityGate.isSupplementalPlaybackProvider("youtube_music"))
        assertFalse(SourceIdentityGate.isCatalogPlaybackProvider("youtube_music"))
    }

    @Test
    fun measuredAtmosM4aOutranksYouTubeAndCdFlac() {
        val atmos = ResolvedStream(
            streamUrl = "https://example.com/jungle.m4a",
            bitrateKbps = 3284,
            mimeType = "audio/mp4",
            format = "m4a",
            qualityLabel = "atmos",
            isDolbyAtmos = true
        )
        val flac = ResolvedStream(
            streamUrl = "https://example.com/jungle.flac",
            bitrateKbps = 1411,
            mimeType = "audio/flac",
            format = "flac"
        )
        val youtube = ResolvedStream(
            streamUrl = "https://example.com/jungle.webm",
            bitrateKbps = 128,
            mimeType = "audio/webm",
            format = "webm"
        )
        assertTrue(SourceIdentityGate.streamPlaybackScore("tidal", atmos) > SourceIdentityGate.streamPlaybackScore("qobuz", flac))
        assertTrue(SourceIdentityGate.streamPlaybackScore("qobuz", flac) > SourceIdentityGate.streamPlaybackScore("youtube_music", youtube))
    }

    private fun searchResult(
        title: String,
        artist: String,
        album: String? = null,
        providerId: String = "test_provider",
        id: String = "1",
        durationMs: Long = 200_000L
    ) = SourceSearchResult(
        id = id,
        providerId = providerId,
        title = title,
        artist = artist,
        album = album,
        coverSeed = title,
        durationMs = durationMs,
        status = SearchItemStatus.SOURCE_FOUND,
        qualityLabel = null
    )
}
