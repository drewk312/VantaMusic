package com.audiophile.musicplayer.data.importer

import com.audiophile.musicplayer.data.local.entities.SourceType
import com.audiophile.musicplayer.data.local.entities.UnifiedTrack
import com.audiophile.musicplayer.data.local.entities.UnifiedTrackWithSources
import com.audiophile.musicplayer.data.remote.eclipse.EclipseApiResponse
import com.audiophile.musicplayer.data.remote.eclipse.EclipsePlaylistApi
import com.audiophile.musicplayer.data.remote.eclipse.EclipsePlaylistResponse
import com.audiophile.musicplayer.data.remote.eclipse.EclipseTrack
import com.audiophile.musicplayer.data.repository.LocalLibraryRepository
import com.audiophile.musicplayer.data.repository.TrackRepository
import com.audiophile.musicplayer.data.source.MusicSourceProvider
import com.audiophile.musicplayer.data.source.ResolvedStream
import com.audiophile.musicplayer.data.source.SearchItemStatus
import com.audiophile.musicplayer.data.source.SourceRegistry
import com.audiophile.musicplayer.data.source.SourceSearchResult
import com.audiophile.musicplayer.testutil.InMemoryLibraryDao
import com.audiophile.musicplayer.testutil.InMemoryTrackDao
import com.audiophile.musicplayer.testutil.newMetadataResolver
import com.audiophile.musicplayer.testutil.runSuspendTest
import com.google.gson.Gson
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EclipsePlaylistImporterTest {

    @Test
    fun import_rejectsTitleOnlyCatalogMatchWhenArtistIsMissing() {
        val libraryDao = InMemoryLibraryDao()
        val localLibraryRepository = LocalLibraryRepository(libraryDao, newMetadataResolver(libraryDao))
        val trackRepository = TrackRepository(
            InMemoryTrackDao(
                searchResults = listOf(
                    UnifiedTrackWithSources(
                        track = UnifiedTrack(
                            trackId = 88L,
                            title = "Hello",
                            artist = "Adele",
                            albumName = "25",
                            coverArtUrl = null,
                            localLibraryId = 88L
                        ),
                        sources = emptyList()
                    )
                )
            )
        )
        val importer = EclipsePlaylistImporter(
            eclipseApi = eclipseApiReturning(
                EclipsePlaylistResponse(
                    name = "Eclipse List",
                    tracks = listOf(EclipseTrack(title = "Hello", artist = ""))
                )
            ),
            trackRepository = trackRepository,
            localLibraryRepository = localLibraryRepository
        )

        val result = runSuspendTest { importer.import("share-token").getOrThrow() }

        assertEquals(0, result.matched)
        assertEquals(1, result.unmatched)
        assertEquals(1, libraryDao.songsSnapshot().size)
    }

    @Test
    fun import_registryFallbackDoesNotTakeBadFirstHit() {
        val libraryDao = InMemoryLibraryDao()
        val localLibraryRepository = LocalLibraryRepository(libraryDao, newMetadataResolver(libraryDao))
        val trackRepository = TrackRepository(InMemoryTrackDao())
        val provider = object : MusicSourceProvider {
            override val providerId: String = "fake_provider"
            override val providerName: String = "Fake Provider"

            override suspend fun search(query: String): List<SourceSearchResult> = listOf(
                SourceSearchResult(
                    id = "bad",
                    providerId = providerId,
                    title = "Hello",
                    artist = "Tribute Band",
                    album = "Covers",
                    coverSeed = "bad",
                    durationMs = 180_000L,
                    status = SearchItemStatus.SOURCE_FOUND,
                    qualityLabel = "320 kbps"
                ),
                SourceSearchResult(
                    id = "good",
                    providerId = providerId,
                    title = "Hello",
                    artist = "Adele",
                    album = "25",
                    coverSeed = "good",
                    durationMs = 295_000L,
                    status = SearchItemStatus.SOURCE_FOUND,
                    qualityLabel = "Lossless"
                )
            )

            override suspend fun resolveStream(trackId: String): ResolvedStream? = when (trackId) {
                "good" -> ResolvedStream(
                    streamUrl = "https://streams.example/good.flac",
                    bitrateKbps = 1411,
                    qualityLabel = "Lossless"
                )
                "bad" -> ResolvedStream(
                    streamUrl = "https://streams.example/bad.mp3",
                    bitrateKbps = 320,
                    qualityLabel = "320 kbps"
                )
                else -> null
            }
        }
        val importer = EclipsePlaylistImporter(
            eclipseApi = eclipseApiReturning(
                EclipsePlaylistResponse(
                    name = "Hello",
                    tracks = listOf(EclipseTrack(title = "Hello", artist = "Adele", durationMs = 295_000L))
                )
            ),
            trackRepository = trackRepository,
            localLibraryRepository = localLibraryRepository,
            sourceRegistry = SourceRegistry(listOf(provider))
        )

        val result = runSuspendTest { importer.import("share-token").getOrThrow() }
        val savedSong = libraryDao.songsSnapshot().single()

        assertEquals(1, result.matched)
        assertEquals(0, result.unmatched)
        assertEquals("Hello", savedSong.title)
        assertEquals("Adele", savedSong.artist)
        assertEquals(SourceType.ADDON, savedSong.sourceType)
        assertTrue(savedSong.streamUrl.orEmpty().contains("good.flac"))
    }

    private fun eclipseApiReturning(playlist: EclipsePlaylistResponse): EclipsePlaylistApi {
        val body = Gson().toJson(EclipseApiResponse(success = true, data = playlist))
        val client = OkHttpClient.Builder()
            .addInterceptor { chain ->
                Response.Builder()
                    .request(chain.request())
                    .protocol(Protocol.HTTP_1_1)
                    .code(200)
                    .message("OK")
                    .body(body.toResponseBody("application/json".toMediaType()))
                    .build()
            }
            .build()
        return EclipsePlaylistApi(client)
    }
}
