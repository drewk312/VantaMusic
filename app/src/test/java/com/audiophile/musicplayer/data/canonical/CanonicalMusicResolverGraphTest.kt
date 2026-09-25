package com.audiophile.musicplayer.data.canonical

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class CanonicalMusicResolverGraphTest {
    private lateinit var dao: FakeCanonicalGraphDao
    private lateinit var resolver: CanonicalMusicResolver

    @Before
    fun setUp() {
        dao = FakeCanonicalGraphDao()
        resolver = CanonicalMusicResolver(dao)
    }

    @Test
    fun sameProviderArtistIdResolvesSameCanonicalArtist() = runBlocking {
        val first = resolver.resolveArtist(
            name = "The Weeknd",
            providerId = "qobuz",
            externalArtistId = "qw-1"
        ).entity
        val second = resolver.resolveArtist(
            name = "THE WEEKND",
            providerId = "qobuz",
            externalArtistId = "qw-1"
        ).entity
        assertNotNull(first)
        assertEquals(first!!.artistId, second!!.artistId)
        assertEquals(1, dao.artistCount())
    }

    @Test
    fun weekndDoesNotMergeWithWeekend() = runBlocking {
        val weeknd = resolver.resolveArtist(name = "The Weeknd").entity!!
        val weekend = resolver.resolveArtist(name = "Weekend").entity!!
        assertNotEquals(weeknd.artistId, weekend.artistId)
        assertEquals(2, dao.artistCount())
    }

    @Test
    fun sameExternalAlbumIdResolvesSameAlbum() = runBlocking {
        val a = resolver.resolveAlbum(
            title = "After Hours",
            artistName = "The Weeknd",
            providerId = "qobuz",
            externalAlbumId = "alb-91"
        ).entity!!
        val b = resolver.resolveAlbum(
            title = "After Hours (Deluxe)",
            artistName = "The Weeknd",
            providerId = "qobuz",
            externalAlbumId = "alb-91"
        ).entity!!
        // Same provider album ID wins over title variant — still one canonical album.
        assertEquals(a.albumId, b.albumId)
    }

    @Test
    fun deluxeEditionWithoutSharedIdStaysSeparate() = runBlocking {
        val base = resolver.resolveAlbum("After Hours", "The Weeknd").entity!!
        val deluxe = resolver.resolveAlbum("After Hours (Deluxe)", "The Weeknd").entity!!
        assertNotEquals(base.albumId, deluxe.albumId)
    }

    @Test
    fun sameIsrcResolvesSameTrackAcrossProviders() = runBlocking {
        val qobuz = resolver.resolveTrack(
            CanonicalMusicResolver.TrackInput(
                title = "Blinding Lights",
                artist = "The Weeknd",
                album = "After Hours",
                isrc = "USUG11903892",
                durationMs = 200_040L,
                artworkUrl = "https://cdn.example/q.jpg",
                providerId = "qobuz",
                externalTrackId = "q-track-1"
            )
        ).entity!!
        val tidal = resolver.resolveTrack(
            CanonicalMusicResolver.TrackInput(
                title = "Blinding Lights",
                artist = "The Weeknd",
                album = "After Hours",
                isrc = "USUG11903892",
                durationMs = 200_100L,
                providerId = "tidal",
                externalTrackId = "t-track-9"
            )
        ).entity!!
        assertEquals(qobuz.trackId, tidal.trackId)
        assertEquals(1, dao.trackCount())
        assertNotNull(dao.trackExternal("qobuz", "q-track-1"))
        assertNotNull(dao.trackExternal("tidal", "t-track-9"))
    }

    @Test
    fun richCatalogMetadataUpgradesWeakFields() = runBlocking {
        val weak = resolver.resolveTrack(
            CanonicalMusicResolver.TrackInput(
                title = "Blinding Lights",
                artist = "The Weeknd",
                album = null,
                isrc = "USUG11903892",
                artworkUrl = null,
                providerId = "youtube_music",
                externalTrackId = "yt-1"
            )
        ).entity!!
        assertTrue(weak.albumDisplay.isNullOrBlank())
        val upgraded = resolver.resolveTrack(
            CanonicalMusicResolver.TrackInput(
                title = "Blinding Lights",
                artist = "The Weeknd",
                album = "After Hours",
                isrc = "USUG11903892",
                artworkUrl = "https://cdn.example/after-hours.jpg",
                providerId = "qobuz",
                externalTrackId = "q-2"
            )
        ).entity!!
        assertEquals(weak.trackId, upgraded.trackId)
        assertEquals("After Hours", upgraded.albumDisplay)
        assertEquals("https://cdn.example/after-hours.jpg", upgraded.artworkUrl)
    }

    @Test
    fun weakYoutubeMetadataCannotDowngradeCanonical() = runBlocking {
        val strong = resolver.resolveTrack(
            CanonicalMusicResolver.TrackInput(
                title = "Blinding Lights",
                artist = "The Weeknd",
                album = "After Hours",
                isrc = "USUG11903892",
                artworkUrl = "https://cdn.example/good.jpg",
                providerId = "qobuz",
                externalTrackId = "q-3"
            )
        ).entity!!
        val afterWeak = resolver.resolveTrack(
            CanonicalMusicResolver.TrackInput(
                title = "The Weeknd - Blinding Lights (Official Audio)",
                artist = "TheWeekndVEVO",
                album = null,
                isrc = "USUG11903892",
                artworkUrl = null,
                providerId = "youtube_music",
                externalTrackId = "yt-vevo"
            )
        ).entity!!
        assertEquals(strong.trackId, afterWeak.trackId)
        assertEquals("Blinding Lights", afterWeak.title)
        assertEquals("The Weeknd", afterWeak.artistDisplay)
        assertEquals("After Hours", afterWeak.albumDisplay)
        assertEquals("https://cdn.example/good.jpg", afterWeak.artworkUrl)
    }

    @Test
    fun liveVersionDoesNotMergeWithStudio() = runBlocking {
        val studio = resolver.resolveTrack(
            CanonicalMusicResolver.TrackInput(
                title = "Blinding Lights",
                artist = "The Weeknd",
                durationMs = 200_000L,
                providerId = "qobuz",
                externalTrackId = "studio"
            )
        ).entity!!
        val live = resolver.resolveTrack(
            CanonicalMusicResolver.TrackInput(
                title = "Blinding Lights (Live)",
                artist = "The Weeknd",
                durationMs = 210_000L,
                providerId = "qobuz",
                externalTrackId = "live"
            )
        ).entity!!
        assertNotEquals(studio.trackId, live.trackId)
    }
}
