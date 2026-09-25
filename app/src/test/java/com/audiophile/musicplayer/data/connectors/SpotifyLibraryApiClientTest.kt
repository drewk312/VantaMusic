package com.audiophile.musicplayer.data.connectors

import com.audiophile.musicplayer.data.connectors.spotify.SpotifyLibraryApiClient
import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class SpotifyLibraryApiClientTest {
    private val account = ConnectedLibraryAccount("test", ConnectedLibraryProvider.SPOTIFY, "Test", "me", 0)
    private fun withServer(respond: (String) -> Pair<Int, String>, check: (SpotifyLibraryApiClient) -> Unit) {
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/") { exchange ->
            val (status, text) = respond(exchange.requestURI.path)
            val bytes = text.toByteArray()
            exchange.responseHeaders.add("Content-Type", "application/json")
            exchange.sendResponseHeaders(status, bytes.size.toLong())
            exchange.responseBody.use { it.write(bytes) }
        }
        server.start()
        try { check(SpotifyLibraryApiClient("test-token", "http://127.0.0.1:${server.address.port}/")) }
        finally { server.stop(0) }
    }

    @Test fun expiredTokenIsAnErrorInsteadOfSuccessfulEmptyImport() {
        withServer({ 401 to "{}" }) { client -> runBlocking {
            try {
                client.fetchLibraryPage(account, ConnectedLibraryImportRequest(account.provider), null)
                fail("Expired credentials must fail the sync")
            } catch (error: retrofit2.HttpException) { assertEquals(401, error.code()) }
        } }
    }

    @Test fun nextPageComesFromProviderEvenWhenPageIsShort() {
        withServer({ 200 to """{"items":[],"next":"https://api.spotify.com/v1/me/tracks?offset=50"}""" }) { client -> runBlocking {
            val page = client.fetchLibraryPage(account, ConnectedLibraryImportRequest(account.provider, importPlaylists = false), null)
            assertEquals("50", page.nextCursor)
        } }
    }

    @Test fun playlistMembersDoNotCreatePhantomLibraryPages() {
        val items = (1..60).joinToString(",") { """{"item":{"id":"$it","name":"Song $it","artists":[{"name":"Artist"}]}}""" }
        withServer({ path -> 200 to when(path) {
            "/v1/me/tracks" -> """{"items":[],"next":null}"""
            "/v1/me/playlists" -> """{"items":[{"id":"p1","name":"Favorites"}],"next":null}"""
            else -> """{"items":[$items],"next":null}"""
        } }) { client -> runBlocking {
            val page = client.fetchLibraryPage(account, ConnectedLibraryImportRequest(account.provider), null)
            assertEquals(60, page.tracks.size)
            assertNull(page.nextCursor)
            assertEquals(listOf("p1"), page.tracks.first().playlistIds)
        } }
    }

    @Test fun playlistPermissionFailureCannotEraseAnExistingPlaylist() {
        withServer({ path -> when(path) {
            "/v1/me/tracks" -> 200 to """{"items":[],"next":null}"""
            "/v1/me/playlists" -> 200 to """{"items":[{"id":"p1","name":"Favorites"}],"next":null}"""
            else -> 403 to "{}"
        } }) { client -> runBlocking {
            try {
                client.fetchLibraryPage(account, ConnectedLibraryImportRequest(account.provider), null)
                fail("A partial fetch must not be committed")
            } catch (error: retrofit2.HttpException) { assertEquals(403, error.code()) }
        } }
    }
}
