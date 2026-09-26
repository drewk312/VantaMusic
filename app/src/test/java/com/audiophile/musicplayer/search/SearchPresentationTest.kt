package com.audiophile.musicplayer.search

import com.audiophile.musicplayer.data.canonical.*
import com.audiophile.musicplayer.data.source.SearchItemStatus
import org.junit.Assert.*
import org.junit.Test

class SearchPresentationTest {
    @Test fun artistNameGetsArtistOverviewWithoutASpecialPrefix() {
        val artist = CanonicalArtist(name = "Morgan Wallen")
        assertEquals(artist, SearchPresentation.artistMatch(" morgan wallen ", listOf(artist), emptyList()))
        assertNull(SearchPresentation.artistMatch("Last Night", listOf(artist), emptyList()))
    }
    @Test fun artistCanBeIdentifiedFromSongsWhenArtistEndpointIsEmpty() {
        assertEquals("Morgan Wallen", SearchPresentation.artistMatch("Morgan Wallen", emptyList(),
            listOf(CanonicalTrack(title = "Last Night", artist = "Morgan Wallen")))?.name)
    }
    @Test fun duplicateSourcesCollapseButLiveAndExplicitVersionsRemain() {
        val original = CanonicalTrack(title = "Last Night", artist = "Morgan Wallen", isrc = "US001", sourceStatus = SearchItemStatus.METADATA_ONLY)
        val playable = original.copy(externalTrackId = "deezer:1", sourceProviderId = "cloudflare_gateway", sourceStatus = SearchItemStatus.SOURCE_FOUND)
        val live = original.copy(title = "Last Night (Live)", isrc = "US002")
        val clean = original.copy(isrc = "US003", explicit = false)
        val result = SearchPresentation.songs(listOf(original, playable, live, clean))
        assertEquals(listOf(playable, live, clean), result)
    }
    @Test fun albumsAreDeduplicatedAndScopedToTheSearchedArtist() {
        val tracks = listOf(CanonicalTrack(title = "A", artist = "Morgan Wallen", album = "One Thing at a Time"),
            CanonicalTrack(title = "B", artist = "Morgan Wallen", album = "One Thing at a Time"),
            CanonicalTrack(title = "C", artist = "Someone Else", album = "Other album"))
        val result = SearchPresentation.albums(emptyList(), tracks, CanonicalArtist(name = "Morgan Wallen"))
        assertEquals(listOf("One Thing at a Time"), result.map { it.title })
    }

    @Test fun artistDiscographyExpandsSearchBeyondAlbumsRepresentedInTopSongs() {
        val track = CanonicalTrack(title = "One", artist = "Metallica", album = "Justice")
        val response = UnifiedSearchResponse(track, listOf(track), emptyList(), emptyList(), false)
        val catalog = com.audiophile.musicplayer.data.catalog.ArtistCatalog(
            CanonicalArtist(name = "Metallica"), listOf(track),
            listOf("72 Seasons", "Master of Puppets", "Ride the Lightning").map { CanonicalAlbum(title = it, artist = "Metallica") }
        )
        val merged = SearchPresentation.withArtistCatalog(response, catalog)
        assertEquals(4, merged.albums.size)
        assertEquals(1, merged.songs.size)
        assertEquals("Metallica", merged.artists.first().name)
        assertEquals(response, SearchPresentation.withArtistCatalog(response, null))
        assertEquals(3, SearchPresentation.withArtistCatalog(response.copy(songs = emptyList(), topResult = null),
            catalog.copy(tracks = emptyList())).albums.size)
    }

    @Test fun playlistsKeepCatalogIdentitiesAndDropBlanks() {
        val keep = CanonicalPlaylist(title = "Metallica Essentials", id = "99", curator = "Deezer")
        val dropBlank = CanonicalPlaylist(title = " ", id = "1")
        val dropId = CanonicalPlaylist(title = "No id")
        assertEquals(listOf(keep), SearchPresentation.playlists(listOf(keep, dropBlank, dropId, keep.copy())))
        assertEquals(
            listOf(keep),
            SearchPresentation.playlists(
                listOf(keep, CanonicalPlaylist(title = "Riding music", id = "7")),
                "Metallica"
            )
        )
    }

    @Test fun blankIdentityRowsNeverCrashPresentationGrouping() {
        val bad = CanonicalTrack(title = "", artist = "", album = null, sourceStatus = SearchItemStatus.SOURCE_FOUND)
        val ok = CanonicalTrack(title = "Last Night", artist = "Morgan Wallen", album = "One Thing at a Time", isrc = "US001",
            sourceStatus = SearchItemStatus.SOURCE_FOUND, sourceProviderId = "cloudflare_gateway", externalTrackId = "deezer:1")
        val grouped = SearchPresentation.songs(listOf(bad, ok, ok.copy(sourcePriority = 1)))
        assertNotNull(grouped)
        assertEquals(2, grouped.size)
        val albums = SearchPresentation.albums(emptyList(), listOf(bad, ok), CanonicalArtist(name = "Morgan Wallen"))
        assertEquals(listOf("One Thing at a Time"), albums.map { it.title })
        assertEquals("", SearchPresentation.key(""))
        assertEquals("morgan wallen", SearchPresentation.key("Morgan Wallen"))
    }

    @Test
    fun remastersAndDeluxeDuplicatesCollapseIntoSingleBestTrack() {
        val studio = CanonicalTrack(title = "Whiskey Lullaby", artist = "Brad Paisley", sourcePriority = 10, sourceStatus = SearchItemStatus.SOURCE_FOUND)
        val remaster = CanonicalTrack(title = "Whiskey Lullaby - Remastered 2020", artist = "Brad Paisley", sourcePriority = 10, sourceStatus = SearchItemStatus.SOURCE_FOUND)
        val deluxe = CanonicalTrack(title = "Whiskey Lullaby (Deluxe Edition)", artist = "Brad Paisley", sourcePriority = 10, sourceStatus = SearchItemStatus.SOURCE_FOUND)
        val radioEdit = CanonicalTrack(title = "Whiskey Lullaby (Radio Edit)", artist = "Brad Paisley", sourcePriority = 10, sourceStatus = SearchItemStatus.SOURCE_FOUND)
        val otherSong = CanonicalTrack(title = "Mud on the Tires", artist = "Brad Paisley", sourcePriority = 10, sourceStatus = SearchItemStatus.SOURCE_FOUND)

        val result = SearchPresentation.songs(listOf(studio, remaster, deluxe, radioEdit, otherSong))
        assertEquals("Should collapse duplicate versions into 1 track plus the distinct other song", 2, result.size)
        assertEquals("Whiskey Lullaby", result[0].title)
        assertEquals("Mud on the Tires", result[1].title)
    }

    @Test
    fun searchTypoAndStopwordsSurfacesSong() {
        val track = CanonicalTrack(title = "Whiskey Lullaby", artist = "Brad Paisley feat. Alison Krauss", sourceStatus = SearchItemStatus.SOURCE_FOUND)
        val response = UnifiedSearchEngine.process("whisky lullably by brad paisley", listOf(track))
        assertEquals(1, response.songs.size)
        assertEquals("Whiskey Lullaby", response.songs.first().title)
    }
}

