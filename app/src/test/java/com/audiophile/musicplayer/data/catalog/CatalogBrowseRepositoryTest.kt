package com.audiophile.musicplayer.data.catalog

import com.audiophile.musicplayer.data.metadata.deezer.DeezerApiClient
import com.audiophile.musicplayer.data.source.SearchItemStatus
import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class CatalogBrowseRepositoryTest {
    @Test fun albumTypographyDoesNotCauseAnEmptyListing() {
        assertTrue(catalogNameMatches("I'm The Problem", "I\u2019m The Problem"))
        assertFalse(catalogNameMatches("I'm The Problem (Live)", "I'm The Problem"))
    }

    @Test fun albumLoadsFullListingAndCarriesResolvableCatalogIdentity() = runBlocking {
        val paths = mutableListOf<String>()
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/") { exchange ->
            paths += exchange.requestURI.path
            val body = when (exchange.requestURI.path) {
                "/search/album" -> """{"data":[{"id":9,"title":"Album","artist":{"name":"Wrong artist"}},{"id":10,"title":"Album","cover_xl":"https://example.com/cover.jpg","artist":{"name":"Artist"}}]}"""
                "/album/10/tracks" -> """{"data":[{"id":101,"title":"First song","duration":180,"artist":{"name":"Artist"}},{"id":102,"title":"Second song","duration":220,"artist":{"name":"Artist"}}]}"""
                else -> """{"data":[]}"""
            }.toByteArray()
            exchange.responseHeaders.add("Content-Type", "application/json")
            exchange.sendResponseHeaders(200, body.size.toLong())
            exchange.responseBody.use { it.write(body) }
        }
        server.start()
        try {
            val result = CatalogBrowseRepository(DeezerApiClient("http://127.0.0.1:${server.address.port}/"))
                .browseAlbum("Album", "Artist")
            assertEquals(listOf("First song", "Second song"), result.tracks.map { it.title })
            assertEquals(listOf(1, 2), result.tracks.map { it.trackNumber })
            assertEquals("deezer:101", result.tracks.first().externalTrackId)
            assertEquals("cloudflare_gateway", result.tracks.first().sourceProviderId)
            assertEquals(SearchItemStatus.SOURCE_FOUND, result.tracks.first().sourceStatus)
            assertEquals("Album", result.tracks.first().album)
            assertEquals(listOf("/search/album", "/album/10/tracks"), paths)
        } finally { server.stop(0) }
    }

    @Test fun artistReleasesArePagedIndependentlyOfSongSearchAndMissingArtistFields() = runBlocking {
        val requests = mutableListOf<String>()
        withCatalogFixture({ path, query ->
            requests += "$path?$query"
            when (path) {
                "/search/artist" -> """{"data":[{"id":99,"name":"Metallica tribute"},{"id":119,"name":"Metallica"}]}"""
                "/artist/119/top" -> """{"data":[{"id":1,"title":"One","artist":{"id":119,"name":"Metallica"},"album":{"title":"Justice"}},{"id":2,"title":"One (Live)","title_short":"One","artist":{"id":119,"name":"Metallica"},"album":{"title":"Live"}}]}"""
                "/artist/119/albums" -> if (query.contains("index=0"))
                    """{"data":[{"id":10,"title":"72 Seasons","release_date":"2023-04-14"},{"id":11,"title":"Master of Puppets"}],"total":4,"next":"https://api.deezer.com/artist/119/albums?index=2"}"""
                    else """{"data":[{"id":12,"title":"Ride the Lightning"},{"id":13,"title":"Kill 'Em All"}],"total":4}"""
                else -> error("Unexpected request $path")
            }
        }) { repo ->
            val artist = repo.browseArtist("Metallica")
            assertEquals(4, artist.albums.size)
            assertTrue(artist.albums.all { it.artist == "Metallica" })
            assertEquals(2023, artist.albums.first().releaseYear)
            assertEquals(listOf("One", "One (Live)"), artist.tracks.map { it.title })
            assertTrue(requests.any { it.contains("index=2") })
            assertFalse(requests.any { it.startsWith("/search/track") })
        }
    }

    @Test fun repeatedReleasePageTerminatesAndKeepsAlreadyLoadedAlbums() = runBlocking {
        var pages = 0
        withCatalogFixture({ path, _ ->
            when (path) {
                "/search/artist" -> """{"data":[{"id":119,"name":"Metallica"}]}"""
                "/artist/119/albums" -> {
                    pages++
                    """{"data":[{"id":10,"title":"72 Seasons"}],"total":100,"next":"more"}"""
                }
                else -> """{"data":[]}"""
            }
        }) { repo ->
            assertEquals(1, repo.browseArtist("Metallica").albums.size)
            assertEquals(2, pages)
        }
    }

    @Test fun nonArtistQueryDoesNotFetchUnrelatedArtistsDiscography() = runBlocking {
        withCatalogFixture({ path, _ ->
            assertEquals("/search/artist", path)
            """{"data":[{"id":119,"name":"Metallica"}]}"""
        }) { repo -> assertNull(repo.findArtistCatalog("Metallica One")) }
    }

    @Test fun categoryArtistLookupDoesNotLoadDiscography() = runBlocking {
        withCatalogFixture({ path, _ ->
            when (path) {
                "/search/artist" -> """{"data":[{"id":119,"name":"Metallica"}]}"""
                "/artist/119/top" -> """{"data":[{"id":1,"title":"One","artist":{"id":119,"name":"Metallica"}}]}"""
                else -> error("Unexpected request $path")
            }
        }) { repo ->
            val catalog = repo.browseArtist("Metallica", 10, includeAlbums = false)
            assertEquals(1, catalog.tracks.size)
            assertTrue(catalog.albums.isEmpty())
        }
    }

    @Test fun playlistsAreSearchedAndPagedIndependentlyOfSongs() = runBlocking {
        val requests = mutableListOf<String>()
        withCatalogFixture({ path, query ->
            requests += "$path?$query"
            when (path) {
                "/search/playlist" -> """{"data":[{"id":5,"title":"Wrong"},{"id":908,"title":"This Is Metallica","nb_tracks":3,"picture_xl":"https://example.com/p.jpg","user":{"name":"Deezer"}}]}"""
                "/playlist/908/tracks" -> if (query.contains("index=0"))
                    """{"data":[{"id":1,"title":"One","artist":{"name":"Metallica"}}],"total":2,"next":"more"}"""
                    else """{"data":[{"id":2,"title":"Enter Sandman","artist":{"name":"Metallica"}}],"total":2}"""
                else -> error("Unexpected request $path")
            }
        }) { repo ->
            val found = repo.searchPlaylists("Metallica")
            assertEquals(listOf("908"), found.map { it.id })
            assertEquals("This Is Metallica", found.first().title)
            assertEquals("Deezer", found.first().curator)
            val catalog = repo.browsePlaylist(908)
            assertEquals(listOf("One", "Enter Sandman"), catalog.tracks.map { it.title })
            assertEquals("deezer:1", catalog.tracks.first().externalTrackId)
            assertTrue(requests.any { it.contains("/playlist/908/tracks") && it.contains("index=1") })
        }
    }

    private suspend fun withCatalogFixture(
        body: (String, String) -> String,
        check: suspend (CatalogBrowseRepository) -> Unit
    ) {
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/") { exchange ->
            val bytes = body(exchange.requestURI.path, exchange.requestURI.query.orEmpty()).toByteArray()
            exchange.responseHeaders.add("Content-Type", "application/json")
            exchange.sendResponseHeaders(200, bytes.size.toLong())
            exchange.responseBody.use { it.write(bytes) }
        }
        server.start()
        try { check(CatalogBrowseRepository(DeezerApiClient("http://127.0.0.1:${server.address.port}/"))) }
        finally { server.stop(0) }
    }
}
