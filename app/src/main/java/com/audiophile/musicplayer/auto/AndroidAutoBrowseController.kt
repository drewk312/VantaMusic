@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package com.audiophile.musicplayer.auto

import android.net.Uri
import android.os.Bundle
import android.util.Log
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.LibraryResult
import androidx.media3.session.MediaLibraryService.MediaLibrarySession
import androidx.media3.session.MediaSession
import androidx.media3.session.SessionError
import com.audiophile.musicplayer.data.local.entities.SourceType
import com.audiophile.musicplayer.data.local.entities.UnifiedTrackWithSources
import com.audiophile.musicplayer.data.repository.TrackRepository
import com.audiophile.musicplayer.data.source.SourceRegistry
import com.audiophile.musicplayer.data.source.SourceSearchResult
import com.audiophile.musicplayer.data.source.canResolveStream
import com.audiophile.musicplayer.auto.AutoMainStageLyrics
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.SettableFuture
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.net.URLDecoder
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

class AndroidAutoBrowseController(
    private val trackRepository: TrackRepository,
    private val sourceRegistry: SourceRegistry,
    private val scope: CoroutineScope,
    private val handleAutoRequest: (List<MediaItem>, Int) -> Unit,
    private val orderedQueueTracksProvider: () -> List<UnifiedTrackWithSources>,
    private val allTracksProvider: suspend () -> List<UnifiedTrackWithSources>,
    private val remoteResultCache: AutoLruCache<String, SourceSearchResult>,
    private val favoritesProvider: suspend () -> List<UnifiedTrackWithSources> = { allTracksProvider().filter { false } },
    private val playlistsProvider: suspend () -> List<Pair<Long, String>> = { emptyList() },
    private val playlistTracksProvider: suspend (Long) -> List<UnifiedTrackWithSources> = { emptyList() }
) {
    companion object {
        private const val TAG = "VANTA_AA_BROWSE"
        const val AUTO_ROOT_ID = "vanta:root"
        const val AUTO_MAIN_STAGE_ID = "vanta:main-stage"
        const val AUTO_QUEUE_ID = "vanta:main-stage:queue"
        const val AUTO_RECENT_ID = "vanta:recent"
        const val AUTO_TRACKS_ID = "vanta:tracks"
        const val AUTO_DEVICE_ID = "vanta:device"
        const val AUTO_HIGH_QUALITY_ID = "vanta:hq"
        const val AUTO_SHUFFLE_ID = "vanta:shuffle"
        const val AUTO_TRACK_PREFIX = "vanta:track:"
        const val AUTO_REMOTE_TRACK_PREFIX = "vanta:remote-track:"
        const val AUTO_PAGE_SIZE = 50
        const val AUTO_SEARCH_LIMIT = 50
        const val AUTO_SEARCH_CACHE_SIZE = 8
        const val AUTO_REMOTE_CACHE_SIZE = 200
        const val AUTO_HIGH_QUALITY_KBPS = 900
        const val AUTO_ARTIST_PREFIX = "vanta:artist:"
        const val AUTO_ALBUM_PREFIX = "vanta:album:"
        const val AUTO_LIKED_ID = "vanta:liked"
        const val AUTO_PLAYLISTS_ID = "vanta:playlists"
        const val AUTO_PLAYLIST_PREFIX = "vanta:playlist:"
        const val AUTO_MADE_FOR_YOU_ID = "vanta:made-for-you"
        const val AUTO_RADIO_ID = "vanta:radio"
        const val AUTO_SONG_RADIO_ID = "vanta:radio:song"
        const val AUTO_ARTIST_RADIO_ID = "vanta:radio:artist"
        const val AUTO_DJ_ID = "vanta:ai-dj"
        const val AUTO_LYRICS_ID = "vanta:lyrics"
    }

    private val searchCache = AutoLruCache<String, List<MediaItem>>(AUTO_SEARCH_CACHE_SIZE)

    // remoteResultCache is shared with PlaybackService for search → play resolution.

    fun onGetLibraryRoot(
        session: MediaLibrarySession,
        browser: MediaSession.ControllerInfo,
        params: MediaLibraryService.LibraryParams?
    ): ListenableFuture<LibraryResult<MediaItem>> {
        val root = MediaItem.Builder()
            .setMediaId(AUTO_ROOT_ID)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle("VANTA")
                    .setIsBrowsable(true)
                    .setIsPlayable(false)
                    .setMediaType(MediaMetadata.MEDIA_TYPE_FOLDER_MIXED)
                    .build()
            )
            .build()
        val rootParams = MediaLibraryService.LibraryParams.Builder()
            .setExtras(AutoBrowseExtras.rootExtras())
            .build()
        return Futures.immediateFuture(LibraryResult.ofItem(root, rootParams))
    }

    fun onGetItem(
        session: MediaLibrarySession,
        browser: MediaSession.ControllerInfo,
        mediaId: String
    ): ListenableFuture<LibraryResult<MediaItem>> {
        return autoFuture {
            val item = itemForId(mediaId)
            if (item != null) LibraryResult.ofItem(item, null)
            else LibraryResult.ofError(SessionError.ERROR_BAD_VALUE)
        }
    }

    fun onGetChildren(
        session: MediaLibrarySession,
        browser: MediaSession.ControllerInfo,
        parentId: String,
        page: Int,
        pageSize: Int,
        params: MediaLibraryService.LibraryParams?
    ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> {
        return autoFuture {
            try {
                val children = childrenFor(parentId, page, pageSize)
                LibraryResult.ofItemList(ImmutableList.copyOf(children), params)
            } catch (e: Exception) {
                Log.e(TAG, "onGetChildren failed parentId=$parentId", e)
                LibraryResult.ofItemList(ImmutableList.of(), params)
            }
        }
    }

    fun onSearch(
        session: MediaLibrarySession,
        browser: MediaSession.ControllerInfo,
        query: String,
        params: MediaLibraryService.LibraryParams?
    ): ListenableFuture<LibraryResult<Void>> {
        scope.launch(Dispatchers.IO) {
            try {
                val results = searchResults(query)
                searchCache.put(normalizeQuery(query), results)
                session.notifySearchResultChanged(browser, query, results.size, params)
            } catch (e: Exception) {
                Log.e(TAG, "onSearch failed query=$query", e)
                searchCache.put(normalizeQuery(query), emptyList())
                session.notifySearchResultChanged(browser, query, 0, params)
            }
        }
        return Futures.immediateFuture(LibraryResult.ofVoid())
    }

    fun onGetSearchResult(
        session: MediaLibrarySession,
        browser: MediaSession.ControllerInfo,
        query: String,
        page: Int,
        pageSize: Int,
        params: MediaLibraryService.LibraryParams?
    ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> {
        return autoFuture {
            val cacheKey = normalizeQuery(query)
            val results = searchCache.get(cacheKey)
                ?: searchResults(query).also { searchCache.put(cacheKey, it) }
            LibraryResult.ofItemList(ImmutableList.copyOf(pageItems(results, page, pageSize)), params)
        }
    }

    fun onAddMediaItems(
        session: MediaSession,
        controller: MediaSession.ControllerInfo,
        mediaItems: List<MediaItem>
    ): ListenableFuture<List<MediaItem>> {
        handleAutoRequest(mediaItems, 0)
        return Futures.immediateFuture(emptyList())
    }

    fun onSetMediaItems(
        session: MediaSession,
        controller: MediaSession.ControllerInfo,
        mediaItems: List<MediaItem>,
        startIndex: Int,
        startPositionMs: Long
    ): ListenableFuture<MediaSession.MediaItemsWithStartPosition> {
        handleAutoRequest(mediaItems, startIndex)
        return Futures.immediateFuture(
            MediaSession.MediaItemsWithStartPosition(emptyList(), 0, androidx.media3.common.C.TIME_UNSET)
        )
    }

    // ── Browse Tree Logic ──

    private suspend fun childrenFor(parentId: String, page: Int, pageSize: Int): List<MediaItem> {
        val resolvedPageSize = if (pageSize > 0) pageSize else AUTO_PAGE_SIZE
        return when {
            parentId == AUTO_ROOT_ID -> rootCategories()
            parentId == AUTO_MAIN_STAGE_ID || parentId == AUTO_QUEUE_ID -> orderedQueueTracksProvider().filterAutoBrowse()
                .map { it.toAutoTrackItem() }
            parentId == AUTO_RECENT_ID -> pageItems(
                trackRepository.getRecentlyPlayed(AUTO_PAGE_SIZE).filterAutoBrowse(),
                page, resolvedPageSize
            ).map { it.toAutoTrackItem() }
            parentId == AUTO_TRACKS_ID -> pageItems(
                allTracksProvider().filterAutoBrowse(), page, resolvedPageSize
            ).map { it.toAutoTrackItem() }
            parentId == AUTO_DEVICE_ID -> pageItems(
                allTracksProvider().filter { it.sources.any { s -> s.sourceType == SourceType.LOCAL } }
                    .filterAutoBrowse(), page, resolvedPageSize
            ).map { it.toAutoTrackItem() }
            parentId == AUTO_HIGH_QUALITY_ID -> pageItems(
                allTracksProvider().filter { it.sources.any { s -> s.sourceType == SourceType.LOCAL } }
                    .filterAutoBrowse(), page, resolvedPageSize
            ).map { it.toAutoTrackItem() }
            parentId == AUTO_MADE_FOR_YOU_ID -> pageItems(
                (favoritesProvider() + allTracksProvider().take(20)).distinctBy { it.track.trackId }
                    .filterAutoBrowse(), page, resolvedPageSize
            ).map { it.toAutoTrackItem() }
            parentId == AUTO_LIKED_ID -> pageItems(
                favoritesProvider().filterAutoBrowse(), page, resolvedPageSize
            ).map { it.toAutoTrackItem() }
            parentId == AUTO_PLAYLISTS_ID -> pageItems(
                playlistsProvider().map { (id, name) -> folder("$AUTO_PLAYLIST_PREFIX$id", name, MediaMetadata.MEDIA_TYPE_FOLDER_MIXED) },
                page, resolvedPageSize
            )
            parentId.startsWith(AUTO_PLAYLIST_PREFIX) -> {
                val playlistId = parentId.removePrefix(AUTO_PLAYLIST_PREFIX).toLongOrNull()
                if (playlistId != null) pageItems(
                    playlistTracksProvider(playlistId).filterAutoBrowse(), page, resolvedPageSize
                ).map { it.toAutoTrackItem() } else emptyList()
            }
            parentId.startsWith(AUTO_ARTIST_PREFIX) -> {
                val artist = decodePart(parentId.removePrefix(AUTO_ARTIST_PREFIX))
                pageItems(
                    allTracksProvider().filter { it.track.artist.equals(artist, ignoreCase = true) }
                        .filterAutoBrowse(), page, resolvedPageSize
                ).map { it.toAutoTrackItem() }
            }
            parentId.startsWith(AUTO_ALBUM_PREFIX) -> {
                val key = parseAlbumId(parentId) ?: return emptyList()
                pageItems(
                    allTracksProvider().filter {
                        it.track.artist.equals(key.first, ignoreCase = true) &&
                            it.track.albumName.orEmpty().equals(key.second, ignoreCase = true)
                    }.filterAutoBrowse(), page, resolvedPageSize
                ).map { it.toAutoTrackItem() }
            }
            else -> emptyList()
        }
    }

    private suspend fun itemForId(mediaId: String): MediaItem? {
        return when {
            mediaId == AUTO_ROOT_ID -> folder(AUTO_ROOT_ID, "VANTA", MediaMetadata.MEDIA_TYPE_FOLDER_MIXED)
            mediaId == AUTO_MAIN_STAGE_ID -> folder(AUTO_MAIN_STAGE_ID, "Now Playing", MediaMetadata.MEDIA_TYPE_FOLDER_MIXED)
            mediaId == AUTO_QUEUE_ID -> folder(AUTO_QUEUE_ID, "Up Next", MediaMetadata.MEDIA_TYPE_FOLDER_MIXED)
            mediaId == AUTO_RECENT_ID -> folder(AUTO_RECENT_ID, "Recently Played", MediaMetadata.MEDIA_TYPE_FOLDER_MIXED)
            mediaId == AUTO_TRACKS_ID -> folder(AUTO_TRACKS_ID, "Songs", MediaMetadata.MEDIA_TYPE_FOLDER_MIXED)
            mediaId == AUTO_DEVICE_ID -> folder(AUTO_DEVICE_ID, "On Device", MediaMetadata.MEDIA_TYPE_FOLDER_MIXED)
            mediaId == AUTO_HIGH_QUALITY_ID -> folder(AUTO_HIGH_QUALITY_ID, "High Quality", MediaMetadata.MEDIA_TYPE_FOLDER_MIXED)
            mediaId == AUTO_LIKED_ID -> folder(AUTO_LIKED_ID, "Liked Songs", MediaMetadata.MEDIA_TYPE_FOLDER_MIXED)
            mediaId == AUTO_PLAYLISTS_ID -> folder(AUTO_PLAYLISTS_ID, "Playlists", MediaMetadata.MEDIA_TYPE_FOLDER_MIXED)
            mediaId == AUTO_MADE_FOR_YOU_ID -> folder(AUTO_MADE_FOR_YOU_ID, "Made For You", MediaMetadata.MEDIA_TYPE_FOLDER_MIXED)
            mediaId == AUTO_RADIO_ID -> folder(AUTO_RADIO_ID, "Radio", MediaMetadata.MEDIA_TYPE_FOLDER_MIXED)
            mediaId == AUTO_SONG_RADIO_ID -> actionItem(AUTO_SONG_RADIO_ID, "Song Radio", "Start radio from current song")
            mediaId == AUTO_ARTIST_RADIO_ID -> actionItem(AUTO_ARTIST_RADIO_ID, "Artist Radio", "Start radio from current artist")
            mediaId == AUTO_DJ_ID -> actionItem(AUTO_DJ_ID, "AI DJ", "VANTA's living companion")
            mediaId == AUTO_LYRICS_ID -> actionItem(AUTO_LYRICS_ID, "Lyrics", "Show current lyrics")
            mediaId == AUTO_SHUFFLE_ID -> actionItem(AUTO_SHUFFLE_ID, "Shuffle All", "Surprise me")
            mediaId.startsWith(AUTO_TRACK_PREFIX) -> {
                val trackId = mediaId.removePrefix(AUTO_TRACK_PREFIX).toLongOrNull()
                trackId?.let { trackRepository.getTrackWithSources(it) }
                    ?.takeIf { it.isAutoPlayable() }?.toAutoTrackItem()
            }
            mediaId.startsWith(AUTO_REMOTE_TRACK_PREFIX) -> {
                remoteResultCache.get(mediaId)?.toAutoRemoteTrackItem()
            }
            mediaId.startsWith(AUTO_ARTIST_PREFIX) -> {
                val artist = decodePart(mediaId.removePrefix(AUTO_ARTIST_PREFIX))
                folder(mediaId, artist, MediaMetadata.MEDIA_TYPE_FOLDER_ARTISTS)
            }
            mediaId.startsWith(AUTO_ALBUM_PREFIX) -> {
                val parts = mediaId.removePrefix(AUTO_ALBUM_PREFIX).split("|", limit = 2)
                val albumTitle = parts.getOrNull(1)?.let { decodePart(it) } ?: "Unknown Album"
                folder(mediaId, albumTitle, MediaMetadata.MEDIA_TYPE_FOLDER_ALBUMS)
            }
            else -> null
        }
    }

    private suspend fun searchResults(query: String): List<MediaItem> {
        val cleanQuery = query.trim()
        if (cleanQuery.isBlank()) return emptyList()

        val local = trackRepository.searchLibrary(cleanQuery, AUTO_SEARCH_LIMIT)
            .filterAutoBrowse().map { it.toAutoTrackItem() }

        if (local.size >= AUTO_SEARCH_LIMIT) return local

        val localKeys = local.map { keyOf(it) }.toSet()
        val remote = sourceRegistry.searchAll(cleanQuery, timeoutMs = 5000)
            .asSequence()
            .filter { it.status.canResolveStream() }
            .distinctBy { "${it.title.lowercase()}|${it.artist.lowercase()}" }
            .filterNot { "${it.title}|${it.artist}".lowercase() in localKeys }
            .take(AUTO_SEARCH_LIMIT - local.size)
            .toList()

        val remoteItems = remote.map { it.toAutoRemoteTrackItem() }
        remoteResultCache.putAll(remote.zip(remoteItems).associate { (r, i) -> i.mediaId to r })
        return local + remoteItems
    }

    // ── Browse Helpers ──

    private fun rootCategories(): List<MediaItem> = listOf(
        folder(AUTO_MAIN_STAGE_ID, "Now Playing", MediaMetadata.MEDIA_TYPE_FOLDER_MIXED, "Up next • queue"),
        folder(AUTO_RECENT_ID, "Recently Played", MediaMetadata.MEDIA_TYPE_FOLDER_MIXED),
        folder(AUTO_LIKED_ID, "Liked Songs", MediaMetadata.MEDIA_TYPE_FOLDER_MIXED, "Your favorites"),
        folder(AUTO_TRACKS_ID, "Songs", MediaMetadata.MEDIA_TYPE_FOLDER_MIXED, "Full library"),
        folder(AUTO_PLAYLISTS_ID, "Playlists", MediaMetadata.MEDIA_TYPE_FOLDER_MIXED, "Your playlists"),
        folder(AUTO_MADE_FOR_YOU_ID, "Made For You", MediaMetadata.MEDIA_TYPE_FOLDER_MIXED, "Personalized"),
        folder(AUTO_DEVICE_ID, "On Device", MediaMetadata.MEDIA_TYPE_FOLDER_MIXED, "Offline"),
        folder(AUTO_HIGH_QUALITY_ID, "High Quality", MediaMetadata.MEDIA_TYPE_FOLDER_MIXED, "Lossless"),
        actionItem(AUTO_SHUFFLE_ID, "Shuffle All", "Surprise me"),
        actionItem(AUTO_SONG_RADIO_ID, "Song Radio", "Start radio from current song"),
        actionItem(AUTO_ARTIST_RADIO_ID, "Artist Radio", "Start radio from current artist"),
        actionItem(AUTO_DJ_ID, "AI DJ", "VANTA's living companion"),
        actionItem(AUTO_LYRICS_ID, "Lyrics", "Show current lyrics"),
    )

    private fun folder(id: String, title: String, type: Int, subtitle: String? = null): MediaItem =
        MediaItem.Builder()
            .setMediaId(id)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(title)
                    .apply { subtitle?.let { setArtist(it) } }
                    .setIsBrowsable(true)
                    .setIsPlayable(false)
                    .setMediaType(type)
                    .setExtras(AutoBrowseExtras.listItemExtras())
                    .build()
            )
            .build()

    private fun actionItem(id: String, title: String, subtitle: String): MediaItem =
        MediaItem.Builder()
            .setMediaId(id)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(title)
                    .setArtist(subtitle)
                    .setIsBrowsable(false)
                    .setIsPlayable(true)
                    .setMediaType(MediaMetadata.MEDIA_TYPE_MUSIC)
                    .setExtras(AutoBrowseExtras.listItemExtras())
                    .build()
            )
            .build()

    private fun UnifiedTrackWithSources.toAutoTrackItem(): MediaItem {
        val extras = AutoBrowseExtras.listItemExtras(
            artworkUrl = track.coverArtUrl,
            durationMs = (track.durationMs ?: 0L).coerceAtLeast(0L),
        )
        return MediaItem.Builder()
            .setMediaId("$AUTO_TRACK_PREFIX${track.trackId}")
            .setMediaMetadata(
                AutoMainStageLyrics.buildPlaybackMetadata(
                    title = track.title,
                    artist = track.artist,
                    album = track.albumName,
                    artworkUrl = track.coverArtUrl,
                    durationMs = track.durationMs,
                ).buildUpon()
                    .setIsBrowsable(false)
                    .setIsPlayable(true)
                    .setExtras(extras)
                    .build()
            )
            .setRequestMetadata(
                MediaItem.RequestMetadata.Builder()
                    .setMediaUri(Uri.parse("vanta://track/${track.trackId}"))
                    .build()
            )
            .build()
    }

    private fun SourceSearchResult.toAutoRemoteTrackItem(): MediaItem {
        val mediaId = AutoMediaIdCodec.remoteTrackId(AUTO_REMOTE_TRACK_PREFIX, providerId, id)
        return MediaItem.Builder()
            .setMediaId(mediaId)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(title)
                    .setArtist(artist)
                    .setAlbumTitle(album)
                    .setDurationMs(durationMs ?: 0L)
                    .apply { artworkUrl?.let { setArtworkUri(Uri.parse(it)) } }
                    .setIsPlayable(true)
                    .setMediaType(MediaMetadata.MEDIA_TYPE_MUSIC)
                    .setExtras(AutoBrowseExtras.listItemExtras(artworkUrl, durationMs ?: 0L))
                    .build()
            )
            .build()
    }

    private fun keyOf(item: MediaItem): String =
        "${item.mediaMetadata.title}|${item.mediaMetadata.artist}".lowercase()

    private fun <T> autoFuture(block: suspend () -> T): ListenableFuture<T> {
        val future = SettableFuture.create<T>()
        scope.launch(Dispatchers.IO) {
            try { future.set(block()) } catch (e: Exception) { future.setException(e) }
        }
        return future
    }

    private fun <T> pageItems(items: List<T>, page: Int, pageSize: Int): List<T> =
        items.drop(page * pageSize).take(pageSize)

    private fun encodePart(value: String): String =
        URLEncoder.encode(value, StandardCharsets.UTF_8.toString())

    private fun decodePart(value: String): String =
        URLDecoder.decode(value, StandardCharsets.UTF_8.toString())

    private fun parseAlbumId(mediaId: String): Pair<String, String>? {
        val encoded = mediaId.removePrefix(AUTO_ALBUM_PREFIX)
        val parts = encoded.split("|", limit = 2)
        if (parts.size < 2) return null
        return decodePart(parts[0]) to decodePart(parts[1])
    }

    private fun normalizeQuery(query: String): String =
        query.trim().lowercase()
}

private fun List<UnifiedTrackWithSources>.filterAutoBrowse(): List<UnifiedTrackWithSources> =
    this.filter { it.track.title.isNotBlank() && it.track.artist.isNotBlank() && it.isAutoPlayable() }

private fun UnifiedTrackWithSources.isAutoPlayable(): Boolean =
    sources.isNotEmpty()
