package com.audiophile.musicplayer.search

import android.util.Log
import com.audiophile.musicplayer.data.canonical.CanonicalAlbum
import com.audiophile.musicplayer.data.canonical.CanonicalArtist
import com.audiophile.musicplayer.data.canonical.CanonicalMapper
import com.audiophile.musicplayer.data.canonical.CanonicalPlaylist
import com.audiophile.musicplayer.data.canonical.CanonicalTrack
import com.audiophile.musicplayer.data.display.DisplayMetadataCleaner
import com.audiophile.musicplayer.data.local.entities.UnifiedTrackWithSources
import com.audiophile.musicplayer.data.metadata.MetadataResolver
import com.audiophile.musicplayer.data.repository.TrackRepository
import com.audiophile.musicplayer.data.source.ContentPurityFilter
import com.audiophile.musicplayer.data.source.SourceRegistry
import com.audiophile.musicplayer.playback.NowPlayingStateStore
import kotlinx.coroutines.async
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class SearchRequest(
    val queryText: String
)

data class GroupedSearchResults(
    val topResult: CanonicalTrack?,
    val songs: List<CanonicalTrack>,
    val albums: List<CanonicalAlbum>,
    val artists: List<CanonicalArtist>,
    val allResults: List<CanonicalTrack>,
    val playlists: List<CanonicalPlaylist> = emptyList()
)

data class SearchResultBundle(
    val localMatches: List<UnifiedTrackWithSources>,
    val sourceResults: List<CanonicalTrack>,
    val activeNowPlayingTrackId: String?,
    val grouped: GroupedSearchResults
)

class SearchRepository(
    private val trackRepository: TrackRepository,
    private val sourceRegistry: SourceRegistry,
    private val nowPlayingStateStore: NowPlayingStateStore,
    private val metadataResolver: MetadataResolver,
    private val canonicalMusicResolver: com.audiophile.musicplayer.data.canonical.CanonicalMusicResolver? = null,
    private val catalogBrowseRepository: com.audiophile.musicplayer.data.catalog.CatalogBrowseRepository = com.audiophile.musicplayer.data.catalog.CatalogBrowseRepository(),
    private val gatewaySource: com.audiophile.musicplayer.data.source.CloudflareGatewaySource? = null,
    private val lyricsRepository: com.audiophile.musicplayer.data.lyrics.LyricsRepository? = null
) {

    suspend fun searchLibraryOnly(queryText: String): List<UnifiedTrackWithSources> = withContext(Dispatchers.IO) {
        val textQuery = queryText.trim()
        if (textQuery.isBlank()) return@withContext emptyList()
        trackRepository.searchLibrary(textQuery)
            .filter { track ->
                ContentPurityFilter.isAllowed(
                    title = track.track.title,
                    artist = track.track.artist,
                    album = track.track.albumName,
                    durationMs = track.track.durationMs,
                    source = track.sources.firstOrNull()?.externalProviderId
                )
            }
    }

    suspend fun search(request: SearchRequest): SearchResultBundle = withContext(Dispatchers.IO) {
        val searchStartMs = System.currentTimeMillis()
        val textQuery = request.queryText.trim()
        val providerQuery = UnifiedSearchEngine.providerQuery(textQuery)
        Log.d(
            "VANTA_SEARCH",
            "SearchRepository query='$textQuery' providerQuery='$providerQuery' corrected=${providerQuery != textQuery}"
        )
        val artistCatalog = async {
            // Optional catalog enrichment must finish within the existing search budget.
            kotlinx.coroutines.withTimeoutOrNull(6_000L) { catalogBrowseRepository.findArtistCatalog(providerQuery) }
        }
        val playlistCatalog = async {
            kotlinx.coroutines.withTimeoutOrNull(5_000L) { catalogBrowseRepository.searchPlaylists(providerQuery) }
        }
        val lyricsSearchJob = async {
            if (textQuery.length >= 4 && lyricsRepository != null) {
                kotlinx.coroutines.withTimeoutOrNull(4_000L) {
                    lyricsRepository.searchLyrics(textQuery, limit = 6)
                }.orEmpty()
            } else {
                emptyList()
            }
        }
        val localMatches = searchLibraryOnly(textQuery)
        val sourceResults = sourceRegistry.searchAll(
            providerQuery,
            timeoutMs = 8_000L,
            includeSupplemental = false
        )
            .filter { result ->
                ContentPurityFilter.isAllowed(
                    title = result.title,
                    artist = result.artist,
                    album = result.album,
                    durationMs = result.durationMs,
                    source = result.providerId,
                    userQuery = textQuery
                )
            }
        val searchDurationMs = System.currentTimeMillis() - searchStartMs
        Log.d("VANTA_SEARCH", "SearchRepository local=${localMatches.size} source=${sourceResults.size}")
        Log.d("VANTA_SEARCH_PERF", "query='$textQuery' local=${localMatches.size} source=${sourceResults.size} totalDurationMs=$searchDurationMs")

        val lyricMatchedTracks = lyricsSearchJob.await()
        val lyricSnippetByKey = lyricMatchedTracks.associate {
            canonicalTrackKey(it) to it.matchedLyricSnippet
        }

        val sourceTracks = sourceResults.map { CanonicalMapper.mapToCanonicalTrack(it) }
        val mappedLocal = localMatches.map { CanonicalMapper.mapToCanonicalTrack(it) }

        val enrichedSourceTracks = sourceTracks.map { track ->
            val snippet = lyricSnippetByKey[canonicalTrackKey(track)]
            if (snippet != null) track.copy(matchedLyricSnippet = snippet) else track
        }
        val enrichedLocal = mappedLocal.map { track ->
            val snippet = lyricSnippetByKey[canonicalTrackKey(track)]
            if (snippet != null) track.copy(matchedLyricSnippet = snippet) else track
        }
        val knownKeys = (enrichedSourceTracks + enrichedLocal).map { canonicalTrackKey(it) }.toSet()
        val newFromLyrics = lyricMatchedTracks.filter { canonicalTrackKey(it) !in knownKeys }

        // Preserve catalog ordering as the baseline; the ranking engine may use
        // current/library identity only to break ambiguous or incomplete-query ties.
        val canonicalResults = (enrichedSourceTracks + enrichedLocal + newFromLyrics).distinctBy { canonicalTrackKey(it) }

        val nowPlaying = nowPlayingStateStore.load()
        val personalization = SearchPersonalization(
            activeTitle = nowPlaying?.title,
            activeArtist = nowPlaying?.artist,
            libraryIdentityKeys = localMatches
                .mapTo(linkedSetOf()) { UnifiedSearchEngine.identityKey(it.track.title, it.track.artist) },
            recentIdentityKeys = localMatches
                .asSequence()
                .filter { it.track.lastPlayedAt != null }
                .mapTo(linkedSetOf()) { UnifiedSearchEngine.identityKey(it.track.title, it.track.artist) }
        )

        val response = SearchPresentation.withArtistCatalog(
            UnifiedSearchEngine.process(textQuery, canonicalResults, personalization), artistCatalog.await()
        ).copy(
            playlists = SearchPresentation.playlists(
                (playlistCatalog.await().orEmpty() + gatewaySource?.cachedSearchPlaylists(providerQuery).orEmpty())
                    .distinctBy { "${it.source}:${it.id}" },
                providerQuery
            )
        )
        val resolver = canonicalMusicResolver
        val enrichedArtists = response.artists
        val enrichedAlbums = response.albums
        val enrichedSongs = response.songs
        val enrichedTop = response.topResult?.let { top ->
            if (resolver == null) top else enrichTrackWithGraph(resolver, top)
        }
        Log.d(
            "VANTA_SEARCH_RANK",
            "query='$textQuery' top='${enrichedTop?.title.orEmpty()}' " +
                "artist='${enrichedTop?.artist.orEmpty()}' " +
                "canonicalTrackId=${enrichedTop?.canonicalTrackId ?: -1} " +
                "songs=${enrichedSongs.size} artists=${enrichedArtists.size} " +
                "playlists=${response.playlists.size} " +
                "activeIdentityUsed=" +
                (enrichedTop?.let {
                    UnifiedSearchEngine.identityKey(it.title, it.artist) ==
                        UnifiedSearchEngine.identityKey(nowPlaying?.title.orEmpty(), nowPlaying?.artist.orEmpty())
                } ?: false)
        )

        SearchResultBundle(
            localMatches = localMatches,
            sourceResults = canonicalResults,
            activeNowPlayingTrackId = nowPlaying?.trackId,
            grouped = GroupedSearchResults(
                topResult = enrichedTop,
                songs = enrichedSongs,
                albums = enrichedAlbums,
                artists = enrichedArtists,
                allResults = enrichedSongs,
                playlists = response.playlists
            )
        )
    }
}

private suspend fun enrichTrackWithGraph(
    resolver: com.audiophile.musicplayer.data.canonical.CanonicalMusicResolver,
    track: CanonicalTrack
): CanonicalTrack {
    val resolved = resolver.resolveTrack(
        com.audiophile.musicplayer.data.canonical.CanonicalMusicResolver.TrackInput(
            title = track.title,
            artist = track.artist,
            album = track.album,
            isrc = track.isrc,
            durationMs = track.durationMs,
            artworkUrl = track.artworkUrl,
            genre = track.genre,
            trackNumber = track.trackNumber,
            discNumber = track.discNumber,
            releaseYear = track.releaseYear,
            explicit = track.explicit,
            providerId = track.sourceProviderId,
            externalTrackId = track.externalTrackId
        )
    ).entity ?: return track
    return track.copy(
        canonicalTrackId = resolved.trackId,
        canonicalArtistId = resolved.canonicalArtistId,
        canonicalAlbumId = resolved.canonicalAlbumId,
        album = resolved.albumDisplay ?: track.album,
        artworkUrl = resolved.artworkUrl ?: track.artworkUrl,
        isrc = resolved.isrc ?: track.isrc
    )
}

/** Prefer ISRC / canonicalTrackId merge; keep higher-priority provider row as visible result. */
private fun mergeCanonicalSongs(songs: List<CanonicalTrack>): List<CanonicalTrack> {
    if (songs.size <= 1) return songs
    val merged = LinkedHashMap<String, CanonicalTrack>()
    for (song in songs) {
        val key = when {
            !song.isrc.isNullOrBlank() -> "isrc:${song.isrc.trim().uppercase()}"
            song.canonicalTrackId != null -> "ct:${song.canonicalTrackId}"
            else -> "meta:${canonicalTrackKey(song)}|${song.durationMs?.div(1000L) ?: -1}"
        }
        val existing = merged[key]
        if (existing == null) {
            merged[key] = song
        } else if (song.sourcePriority > existing.sourcePriority) {
            merged[key] = song.copy(
                isrc = song.isrc ?: existing.isrc,
                album = song.album ?: existing.album,
                artworkUrl = song.artworkUrl ?: existing.artworkUrl,
                canonicalTrackId = song.canonicalTrackId ?: existing.canonicalTrackId,
                canonicalArtistId = song.canonicalArtistId ?: existing.canonicalArtistId,
                canonicalAlbumId = song.canonicalAlbumId ?: existing.canonicalAlbumId
            )
        } else {
            merged[key] = existing.copy(
                isrc = existing.isrc ?: song.isrc,
                album = existing.album ?: song.album,
                artworkUrl = existing.artworkUrl ?: song.artworkUrl,
                canonicalTrackId = existing.canonicalTrackId ?: song.canonicalTrackId,
                canonicalArtistId = existing.canonicalArtistId ?: song.canonicalArtistId,
                canonicalAlbumId = existing.canonicalAlbumId ?: song.canonicalAlbumId
            )
        }
    }
    return merged.values.toList()
}

private fun canonicalTrackKey(track: CanonicalTrack): String {
    val (cleanTitle, cleanArtist) = DisplayMetadataCleaner.computeDisplayTitleArtist(track.title, track.artist)
    val artistBase = cleanArtist
        .replace(Regex("""\s*feat\.?\s+.*$""", RegexOption.IGNORE_CASE), "")
        .replace(Regex("""\s*\(feat\..*$""", RegexOption.IGNORE_CASE), "")
        .trim()
    return "${cleanTitle.lowercase()}|${artistBase.lowercase()}"
}
