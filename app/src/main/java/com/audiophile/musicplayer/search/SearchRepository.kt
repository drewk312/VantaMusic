package com.audiophile.musicplayer.search

import android.util.Log
import com.audiophile.musicplayer.data.canonical.CanonicalMapper
import com.audiophile.musicplayer.data.canonical.CanonicalTrack
import com.audiophile.musicplayer.data.local.entities.UnifiedTrackWithSources
import com.audiophile.musicplayer.data.metadata.MetadataResolver
import com.audiophile.musicplayer.data.repository.TrackRepository
import com.audiophile.musicplayer.data.source.SearchItemStatus
import com.audiophile.musicplayer.data.source.SourceRegistry
import com.audiophile.musicplayer.data.source.ContentPurityFilter
import com.audiophile.musicplayer.playback.NowPlayingStateStore
import com.audiophile.musicplayer.data.display.DisplayMetadataCleaner
import com.audiophile.musicplayer.data.canonical.CanonicalAlbum
import com.audiophile.musicplayer.data.canonical.CanonicalArtist
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
    val allResults: List<CanonicalTrack>
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
    private val metadataResolver: MetadataResolver
) {

    suspend fun search(request: SearchRequest): SearchResultBundle = withContext(Dispatchers.IO) {
        val searchStartMs = System.currentTimeMillis()
        val textQuery = request.queryText.trim()
        Log.d("VANTA_SEARCH", "SearchRepository query='$textQuery'")
        val localMatches = trackRepository.searchLibrary(textQuery)
            .filter { track ->
                ContentPurityFilter.isAllowed(
                    title = track.track.title,
                    artist = track.track.artist,
                    album = track.track.albumName,
                    durationMs = track.track.durationMs,
                    source = track.sources.firstOrNull()?.externalProviderId
                )
            }
        val sourceResults = sourceRegistry.searchAll(textQuery)
            .filterNot { it.status == SearchItemStatus.PREVIEW }
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

        val sourceTracks = sourceResults.map { CanonicalMapper.mapToCanonicalTrack(it) }
        val mappedLocal = localMatches.map { CanonicalMapper.mapToCanonicalTrack(it) }
        // Catalog hits first (gateway order), then library — no local re-ranking.
        val canonicalResults = (sourceTracks + mappedLocal).distinctBy { canonicalTrackKey(it) }

        val response = UnifiedSearchEngine.process(textQuery, canonicalResults)
        
        SearchResultBundle(
            localMatches = localMatches,
            sourceResults = canonicalResults,
            activeNowPlayingTrackId = nowPlayingStateStore.load()?.trackId,
            grouped = GroupedSearchResults(
                topResult = response.topResult,
                songs = response.songs,
                albums = response.albums,
                artists = response.artists,
                allResults = response.songs
            )
        )
    }
}

private fun canonicalTrackKey(track: CanonicalTrack): String {
    val (cleanTitle, cleanArtist) = DisplayMetadataCleaner.computeDisplayTitleArtist(track.title, track.artist)
    val artistBase = cleanArtist
        .replace(Regex("""\s*feat\.?\s+.*$""", RegexOption.IGNORE_CASE), "")
        .replace(Regex("""\s*\(feat\..*$""", RegexOption.IGNORE_CASE), "")
        .trim()
    return "${cleanTitle.lowercase()}|${artistBase.lowercase()}"
}
