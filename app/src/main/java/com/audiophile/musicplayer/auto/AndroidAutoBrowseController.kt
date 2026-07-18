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
import com.audiophile.musicplayer.data.display.DisplayMetadataCleaner
import com.audiophile.musicplayer.data.local.entities.SourceType
import com.audiophile.musicplayer.data.local.entities.UnifiedTrackWithSources
import com.audiophile.musicplayer.data.repository.TrackRepository
import com.audiophile.musicplayer.data.source.SourceRegistry
import com.audiophile.musicplayer.data.source.SourceSearchResult
import com.audiophile.musicplayer.data.source.canResolveStream
import com.audiophile.musicplayer.data.source.canEnterPlaybackFlow
import com.audiophile.musicplayer.data.source.sourceValidityStatus
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
import androidx.core.net.toUri

class AndroidAutoBrowseController(
    private val trackRepository: TrackRepository,
    private val sourceRegistry: SourceRegistry,
    private val scope: CoroutineScope,
    private val handleAutoRequest: (List<MediaItem>, Int) -> Unit,
    private val orderedQueueTracksProvider: () -> List<UnifiedTrackWithSources>,
    private val allTracksProvider: suspend () -> List<UnifiedTrackWithSources>,
    private val remoteResultCache: AutoLruCache<String, SourceSearchResult>,
    private val currentTrackProvider: () -> UnifiedTrackWithSources? = { null },
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
        const val AUTO_RADIO_ID = "vanta:radio"
        const val AUTO_SONG_RADIO_ID = "vanta:radio:song"
        const val AUTO_ARTIST_RADIO_ID = "vanta:radio:artist"
        const val AUTO_DJ_ID = "vanta:ai-dj"
        const val AUTO_LYRICS_ID = "vanta:lyrics"
        const val AUTO_GENRES_ID = "vanta:genres"
        const val AUTO_GENRE_PREFIX = "vanta:genre:"
        const val AUTO_ARTISTS_ID = "vanta:artists"
        const val AUTO_ALBUMS_ID = "vanta:albums"
        const val AUTO_CONTINUE_ID = "vanta:continue"
        const val AUTO_CONTINUE_TRACK_PREFIX = "vanta:continue-track:"
        const val AUTO_TIME_PREFIX = "vanta:time:"
        const val AUTO_MOOD_PREFIX = "vanta:mood:"
        const val AUTO_VIBE_MIXES_ID = "vanta:vibe-mixes"
        const val AUTO_VIBE_MOODS_ID = "vanta:vibe-moods"
        const val AUTO_VIBE_TIME_ID = "vanta:vibe-time"
        private val AUTO_MOODS = listOf("Happy", "Sad", "Focus", "Party", "Chill", "Energetic", "Romantic", "Sleep")
        private val AUTO_TIME_MIXES = listOf("Morning", "Workout", "Evening", "Late Night")
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
        return Futures.immediateFuture(mediaItems)
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
            MediaSession.MediaItemsWithStartPosition(mediaItems, startIndex, startPositionMs)
        )
    }

    // ── Browse Tree Logic ──

    private suspend fun childrenFor(parentId: String, page: Int, pageSize: Int): List<MediaItem> {
        val resolvedPageSize = if (pageSize > 0) pageSize else AUTO_PAGE_SIZE
        return when {
            parentId == AUTO_ROOT_ID -> rootCategories()
            parentId == AUTO_MAIN_STAGE_ID || parentId == AUTO_QUEUE_ID -> orderedQueueTracksProvider().filterAutoBrowse()
                .map { it.toAutoTrackItem() }
            parentId == AUTO_CONTINUE_ID -> pageItems(
                trackRepository.getRecentlyPlayed(20).filterAutoBrowse(),
                page, resolvedPageSize
            ).map { it.toAutoContinueTrackItem() }
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
                allTracksProvider().filter { it.sources.any { s -> s.bitrate >= AUTO_HIGH_QUALITY_KBPS } }
                    .filterAutoBrowse(), page, resolvedPageSize
            ).map { it.toAutoTrackItem() }
            parentId == AUTO_LIKED_ID -> pageItems(
                favoritesProvider().filterAutoBrowse(), page, resolvedPageSize
            ).map { it.toAutoTrackItem() }
            parentId == AUTO_PLAYLISTS_ID -> {
                val playablePlaylists = playlistsProvider().mapNotNull { (id, name) ->
                    val playableTracks = playlistTracksProvider(id).filterAutoBrowse()
                    if (playableTracks.isEmpty()) {
                        null
                    } else {
                        folder(
                            "$AUTO_PLAYLIST_PREFIX$id",
                            DisplayMetadataCleaner.cleanDisplayName(name).ifBlank { name },
                            MediaMetadata.MEDIA_TYPE_FOLDER_MIXED,
                            "${playableTracks.size} songs"
                        )
                    }
                }
                pageItems(playablePlaylists, page, resolvedPageSize)
            }
            parentId == AUTO_ARTISTS_ID -> pageItems(
                allTracksProvider().filterAutoBrowse()
                    .groupBy { it.track.artist }
                    .map { (artist, tracks) ->
                        val artwork = tracks.firstOrNull()?.track?.coverArtUrl
                        artistFolder("$AUTO_ARTIST_PREFIX${encodePart(artist)}", artist, artwork)
                    }
                    .sortedBy { it.mediaMetadata.title.toString() },
                page, resolvedPageSize
            )
            parentId == AUTO_ALBUMS_ID -> pageItems(
                allTracksProvider().filterAutoBrowse()
                    .groupBy { "${it.track.artist}|${it.track.albumName}" }
                    .map { (_, tracks) ->
                        val first = tracks.first()
                        val albumTitle = first.track.albumName ?: "Unknown Album"
                        val artist = first.track.artist
                        val artwork = first.track.coverArtUrl
                        albumFolder(
                            "$AUTO_ALBUM_PREFIX${encodePart(artist)}|${encodePart(albumTitle)}",
                            albumTitle, artist, artwork
                        )
                    }
                    .sortedBy { it.mediaMetadata.title.toString() },
                page, resolvedPageSize
            )
            parentId == AUTO_VIBE_MIXES_ID -> listOf(
                folder(AUTO_VIBE_MOODS_ID, "Moods", MediaMetadata.MEDIA_TYPE_FOLDER_MIXED, "By feeling"),
                folder(AUTO_VIBE_TIME_ID, "Time Mixes", MediaMetadata.MEDIA_TYPE_FOLDER_MIXED, "By time of day")
            )
            parentId == AUTO_VIBE_MOODS_ID -> AUTO_MOODS.map { mood ->
                folder("$AUTO_MOOD_PREFIX${encodePart(mood)}", "$mood Vibes", MediaMetadata.MEDIA_TYPE_FOLDER_MIXED, "Music for your $mood mood")
            }
            parentId == AUTO_VIBE_TIME_ID -> AUTO_TIME_MIXES.map { timeMix ->
                folder("$AUTO_TIME_PREFIX${encodePart(timeMix)}", "$timeMix Mix", MediaMetadata.MEDIA_TYPE_FOLDER_MIXED, "Music for your $timeMix")
            }
            parentId == AUTO_GENRES_ID -> pageItems(
                availableGenres().map { genre ->
                    folder("$AUTO_GENRE_PREFIX${encodePart(genre)}", genre, MediaMetadata.MEDIA_TYPE_FOLDER_GENRES, "${countTracksInGenre(genre)} tracks")
                },
                page, resolvedPageSize
            )
            parentId.startsWith(AUTO_GENRE_PREFIX) -> {
                val genre = decodePart(parentId.removePrefix(AUTO_GENRE_PREFIX))
                pageItems(
                    allTracksProvider().filter { trackBelongsToGenre(it, genre) }
                        .filterAutoBrowse(), page, resolvedPageSize
                ).map { it.toAutoTrackItem() }
            }
            parentId.startsWith(AUTO_TIME_PREFIX) -> {
                val timeMix = decodePart(parentId.removePrefix(AUTO_TIME_PREFIX))
                pageItems(
                    getTimeMixTracks(timeMix).filterAutoBrowse(), page, resolvedPageSize
                ).map { it.toAutoTrackItem() }
            }
            parentId.startsWith(AUTO_MOOD_PREFIX) -> {
                val mood = decodePart(parentId.removePrefix(AUTO_MOOD_PREFIX))
                pageItems(
                    getMoodTracks(mood).filterAutoBrowse(), page, resolvedPageSize
                ).map { it.toAutoTrackItem() }
            }
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
            mediaId == AUTO_CONTINUE_ID -> folder(AUTO_CONTINUE_ID, "Continue Listening", MediaMetadata.MEDIA_TYPE_FOLDER_MIXED, "Resume where you left off")
            mediaId == AUTO_RECENT_ID -> folder(AUTO_RECENT_ID, "Recently Played", MediaMetadata.MEDIA_TYPE_FOLDER_MIXED)
            mediaId == AUTO_TRACKS_ID -> folder(AUTO_TRACKS_ID, "Songs", MediaMetadata.MEDIA_TYPE_FOLDER_MIXED)
            mediaId == AUTO_DEVICE_ID -> folder(AUTO_DEVICE_ID, "On Device", MediaMetadata.MEDIA_TYPE_FOLDER_MIXED)
            mediaId == AUTO_HIGH_QUALITY_ID -> folder(AUTO_HIGH_QUALITY_ID, "High Quality", MediaMetadata.MEDIA_TYPE_FOLDER_MIXED)
            mediaId == AUTO_LIKED_ID -> folder(AUTO_LIKED_ID, "Liked Songs", MediaMetadata.MEDIA_TYPE_FOLDER_MIXED)
            mediaId == AUTO_PLAYLISTS_ID -> folder(AUTO_PLAYLISTS_ID, "Playlists", MediaMetadata.MEDIA_TYPE_FOLDER_MIXED)
            mediaId == AUTO_GENRES_ID && availableGenres().isNotEmpty() ->
                folder(AUTO_GENRES_ID, "Genres", MediaMetadata.MEDIA_TYPE_FOLDER_GENRES, "Browse by genre")
            mediaId == AUTO_ARTISTS_ID -> folder(AUTO_ARTISTS_ID, "Artists", MediaMetadata.MEDIA_TYPE_FOLDER_ARTISTS, "Browse by artist")
            mediaId == AUTO_ALBUMS_ID -> folder(AUTO_ALBUMS_ID, "Albums", MediaMetadata.MEDIA_TYPE_FOLDER_ALBUMS, "Browse by album")
            mediaId == AUTO_VIBE_MIXES_ID ->
                folder(AUTO_VIBE_MIXES_ID, "Vibe Mixes", MediaMetadata.MEDIA_TYPE_FOLDER_MIXED, "Moods & time mixes")
            mediaId == AUTO_VIBE_MOODS_ID ->
                folder(AUTO_VIBE_MOODS_ID, "Moods", MediaMetadata.MEDIA_TYPE_FOLDER_MIXED, "By feeling")
            mediaId == AUTO_VIBE_TIME_ID ->
                folder(AUTO_VIBE_TIME_ID, "Time Mixes", MediaMetadata.MEDIA_TYPE_FOLDER_MIXED, "By time of day")
            mediaId == AUTO_SONG_RADIO_ID && currentTrackProvider() != null ->
                actionItem(AUTO_SONG_RADIO_ID, "Song Radio", "Start radio from current song")
            mediaId == AUTO_ARTIST_RADIO_ID && currentTrackProvider() != null ->
                actionItem(AUTO_ARTIST_RADIO_ID, "Artist Radio", "Start radio from current artist")
            mediaId == AUTO_LYRICS_ID && currentTrackProvider() != null ->
                actionItem(AUTO_LYRICS_ID, "Lyrics", "Show current lyrics")
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
                val artwork = allTracksProvider().firstOrNull { it.track.artist.equals(artist, ignoreCase = true) }?.track?.coverArtUrl
                artistFolder(mediaId, artist, artwork)
            }
            mediaId.startsWith(AUTO_ALBUM_PREFIX) -> {
                val parts = mediaId.removePrefix(AUTO_ALBUM_PREFIX).split("|", limit = 2)
                val artist = parts.getOrNull(0)?.let { decodePart(it) } ?: "Unknown Artist"
                val albumTitle = parts.getOrNull(1)?.let { decodePart(it) } ?: "Unknown Album"
                val artwork = allTracksProvider().firstOrNull {
                    it.track.artist.equals(artist, ignoreCase = true) &&
                        it.track.albumName.orEmpty().equals(albumTitle, ignoreCase = true)
                }?.track?.coverArtUrl
                albumFolder(mediaId, albumTitle, artist, artwork)
            }
            mediaId.startsWith(AUTO_GENRE_PREFIX) -> {
                val genre = decodePart(mediaId.removePrefix(AUTO_GENRE_PREFIX))
                val count = countTracksInGenre(genre)
                folder(mediaId, genre, MediaMetadata.MEDIA_TYPE_FOLDER_GENRES, "$count tracks")
            }
            mediaId.startsWith(AUTO_TIME_PREFIX) -> {
                val timeMix = decodePart(mediaId.removePrefix(AUTO_TIME_PREFIX))
                folder(mediaId, "$timeMix Mix", MediaMetadata.MEDIA_TYPE_FOLDER_MIXED, "Music for your $timeMix")
            }
            mediaId.startsWith(AUTO_MOOD_PREFIX) -> {
                val mood = decodePart(mediaId.removePrefix(AUTO_MOOD_PREFIX))
                folder(mediaId, "$mood Vibes", MediaMetadata.MEDIA_TYPE_FOLDER_MIXED, "Music for your $mood mood")
            }
            else -> null
        }
    }

    private suspend fun searchResults(query: String): List<MediaItem> {
        val cleanQuery = query.trim()
        if (cleanQuery.isBlank()) return emptyList()
        val normalized = cleanQuery.lowercase()

        // Voice shortcuts: map spoken mood/time requests directly into scored mixes.
        val moodMatch = AUTO_MOODS.firstOrNull { normalized.contains(it.lowercase()) }
        if (moodMatch != null) {
            return getMoodTracks(moodMatch).filterAutoBrowse().map { it.toAutoTrackItem() }
        }
        val timeMatch = AUTO_TIME_MIXES.firstOrNull { normalized.contains(it.lowercase()) }
        if (timeMatch != null) {
            return getTimeMixTracks(timeMatch).filterAutoBrowse().map { it.toAutoTrackItem() }
        }

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

    private suspend fun rootCategories(): List<MediaItem> {
        val items = mutableListOf(
            folder(AUTO_MAIN_STAGE_ID, "Now Playing", MediaMetadata.MEDIA_TYPE_FOLDER_MIXED, "Up next • queue"),
            folder(AUTO_CONTINUE_ID, "Continue Listening", MediaMetadata.MEDIA_TYPE_FOLDER_MIXED, "Resume where you left off"),
            folder(AUTO_RECENT_ID, "Recently Played", MediaMetadata.MEDIA_TYPE_FOLDER_MIXED),
            folder(AUTO_LIKED_ID, "Liked Songs", MediaMetadata.MEDIA_TYPE_FOLDER_MIXED, "Your favorites"),
            folder(AUTO_TRACKS_ID, "Songs", MediaMetadata.MEDIA_TYPE_FOLDER_MIXED, "Full library"),
            folder(AUTO_PLAYLISTS_ID, "Playlists", MediaMetadata.MEDIA_TYPE_FOLDER_MIXED, "Your playlists"),
            folder(AUTO_ARTISTS_ID, "Artists", MediaMetadata.MEDIA_TYPE_FOLDER_ARTISTS, "Browse by artist"),
            folder(AUTO_ALBUMS_ID, "Albums", MediaMetadata.MEDIA_TYPE_FOLDER_ALBUMS, "Browse by album"),
            folder(AUTO_DEVICE_ID, "On Device", MediaMetadata.MEDIA_TYPE_FOLDER_MIXED, "Offline"),
            folder(AUTO_HIGH_QUALITY_ID, "High Quality", MediaMetadata.MEDIA_TYPE_FOLDER_MIXED, "Lossless & Hi-Res"),
            actionItem(AUTO_SHUFFLE_ID, "Shuffle All", "Surprise me"),
        )
        if (availableGenres().isNotEmpty()) {
            items += folder(AUTO_GENRES_ID, "Genres", MediaMetadata.MEDIA_TYPE_FOLDER_GENRES, "Browse by genre")
        }
        items += folder(AUTO_VIBE_MIXES_ID, "Vibe Mixes", MediaMetadata.MEDIA_TYPE_FOLDER_MIXED, "Moods & time mixes")
        if (currentTrackProvider() != null) {
            items += actionItem(AUTO_SONG_RADIO_ID, "Song Radio", "Start radio from current song")
            items += actionItem(AUTO_ARTIST_RADIO_ID, "Artist Radio", "Start radio from current artist")
        }
        return items
    }

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
        val qualityLabel = getQualityLabel()
        val description = listOfNotNull(track.albumName, qualityLabel).joinToString(" • ").ifBlank { null }
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
                    description = description,
                ).buildUpon()
                    .setIsBrowsable(false)
                    .setIsPlayable(true)
                    .setExtras(extras)
                    .build()
            )
            .setRequestMetadata(
                MediaItem.RequestMetadata.Builder()
                    .setMediaUri("vanta://track/${track.trackId}".toUri())
                    .build()
            )
            .build()
    }

    private fun SourceSearchResult.toAutoRemoteTrackItem(): MediaItem {
        val mediaId = AutoMediaIdCodec.remoteTrackId(AUTO_REMOTE_TRACK_PREFIX, providerId, id)
        val (cleanTitle, cleanArtist) = DisplayMetadataCleaner.computeDisplayTitleArtist(title, artist)
        return MediaItem.Builder()
            .setMediaId(mediaId)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(cleanTitle.ifBlank { title })
                    .setArtist(cleanArtist.ifBlank { artist })
                    .setAlbumTitle(DisplayMetadataCleaner.cleanAlbumName(album))
                    .setDurationMs(durationMs ?: 0L)
                    .apply { artworkUrl?.let { setArtworkUri(it.toUri()) } }
                    .setIsPlayable(true)
                    .setMediaType(MediaMetadata.MEDIA_TYPE_MUSIC)
                    .setExtras(AutoBrowseExtras.listItemExtras(artworkUrl, durationMs ?: 0L))
                    .build()
            )
            .build()
    }

    private fun artistFolder(id: String, artist: String, artworkUrl: String?): MediaItem =
        MediaItem.Builder()
            .setMediaId(id)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(DisplayMetadataCleaner.cleanDisplayName(artist).ifBlank { artist })
                    .setIsBrowsable(true)
                    .setIsPlayable(false)
                    .setMediaType(MediaMetadata.MEDIA_TYPE_FOLDER_ARTISTS)
                    .apply { artworkUrl?.let { setArtworkUri(it.toUri()) } }
                    .setExtras(AutoBrowseExtras.listItemExtras(artworkUrl, 0L))
                    .build()
            )
            .build()

    private fun albumFolder(id: String, album: String, artist: String, artworkUrl: String?): MediaItem =
        MediaItem.Builder()
            .setMediaId(id)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(DisplayMetadataCleaner.cleanDisplayName(album).ifBlank { album })
                    .setArtist(DisplayMetadataCleaner.cleanDisplayName(artist).ifBlank { artist })
                    .setIsBrowsable(true)
                    .setIsPlayable(false)
                    .setMediaType(MediaMetadata.MEDIA_TYPE_FOLDER_ALBUMS)
                    .apply { artworkUrl?.let { setArtworkUri(it.toUri()) } }
                    .setExtras(AutoBrowseExtras.listItemExtras(artworkUrl, 0L))
                    .build()
            )
            .build()

    private fun UnifiedTrackWithSources.toAutoContinueTrackItem(): MediaItem {
        val qualityLabel = getQualityLabel()
        val description = listOfNotNull(track.albumName, qualityLabel).joinToString(" • ")
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
                    description = description,
                ).buildUpon()
                    .setIsBrowsable(false)
                    .setIsPlayable(true)
                    .setExtras(extras)
                    .build()
            )
            .setRequestMetadata(
                MediaItem.RequestMetadata.Builder()
                    .setMediaUri("vanta://track/${track.trackId}".toUri())
                    .build()
            )
            .build()
    }

    private fun UnifiedTrackWithSources.getQualityLabel(): String? {
        val bestBitrate = sources.maxOfOrNull { it.bitrate } ?: return null
        return when {
            bestBitrate >= 2000 -> "Hi-Res"
            bestBitrate >= 900 -> "Lossless"
            bestBitrate >= 320 -> "320k"
            bestBitrate >= 256 -> "256k"
            else -> null
        }
    }

    private suspend fun countTracksInGenre(genre: String): Int {
        return allTracksProvider().count { trackBelongsToGenre(it, genre) }
    }

    private suspend fun availableGenres(): List<String> {
        return allTracksProvider()
            .filterAutoBrowse()
            .flatMap { trackGenres(it) }
            .distinct()
            .sortedBy { it.lowercase() }
    }

    private fun trackBelongsToGenre(track: UnifiedTrackWithSources, genre: String): Boolean {
        val match = genre.trim().lowercase()
        return trackGenres(track).any { it.lowercase() == match }
    }

    private fun trackGenres(track: UnifiedTrackWithSources): List<String> =
        track.track.genre
            .orEmpty()
            .split(';', ',', '/', '|')
            .map { DisplayMetadataCleaner.cleanDisplayName(it).trim() }
            .filter { it.isNotBlank() }
            .distinctBy { it.lowercase() }

    private suspend fun getTimeMixTracks(timeMix: String): List<UnifiedTrackWithSources> {
        val allTracks = allTracksProvider()
        val profile = TIME_PROFILES[timeMix.lowercase()] ?: emptyMap()
        return scoreAndRank(allTracks, profile)
    }

    private suspend fun getMoodTracks(mood: String): List<UnifiedTrackWithSources> {
        val allTracks = allTracksProvider()
        val profile = MOOD_PROFILES[mood.lowercase()] ?: emptyMap()
        return scoreAndRank(allTracks, profile)
    }

    private fun scoreAndRank(
        tracks: List<UnifiedTrackWithSources>,
        profile: Map<String, Float>
    ): List<UnifiedTrackWithSources> {
        if (profile.isEmpty()) return tracks.shuffled().take(20)
        val scored = tracks.mapNotNull { track ->
            val score = scoreTrackForProfile(track, profile)
            if (score > 0f) track to score else null
        }
        return if (scored.isEmpty()) {
            tracks.shuffled().take(20)
        } else {
            scored.sortedByDescending { it.second }.map { it.first }.take(20)
        }
    }

    private fun scoreTrackForProfile(
        track: UnifiedTrackWithSources,
        profile: Map<String, Float>
    ): Float {
        val title = track.track.title.lowercase()
        val artist = track.track.artist.lowercase()
        val genre = track.track.genre.orEmpty().lowercase()
        var score = 0f
        for ((token, weight) in profile) {
            if (title.contains(token)) score += weight
            if (artist.contains(token)) score += weight * 0.6f
            if (genre.contains(token)) score += weight * 1.4f
        }
        return score
    }

    private val MOOD_PROFILES = mapOf(
        "happy" to mapOf("happy" to 3f, "joy" to 3f, "smile" to 2f, "good" to 2f, "sun" to 1f, "sunshine" to 2f, "pop" to 1f, "upbeat" to 2f),
        "sad" to mapOf("sad" to 3f, "cry" to 3f, "lonely" to 2f, "heartbreak" to 2f, "hurt" to 2f, "blue" to 2f, "melancholy" to 2f, "acoustic" to 1f),
        "focus" to mapOf("focus" to 3f, "concentrate" to 2f, "study" to 2f, "deep" to 1f, "ambient" to 2f, "classical" to 2f, "instrumental" to 2f, "piano" to 1f),
        "party" to mapOf("party" to 3f, "dance" to 3f, "club" to 2f, "fire" to 1f, "turn up" to 2f, "pop" to 1f, "hip-hop" to 1f, "electronic" to 1f),
        "chill" to mapOf("chill" to 3f, "relax" to 2f, "calm" to 2f, "easy" to 1f, "unwind" to 2f, "lo-fi" to 2f, "r&b" to 1f, "soul" to 1f),
        "energetic" to mapOf("energy" to 3f, "power" to 2f, "strong" to 2f, "hype" to 2f, "fast" to 1f, "workout" to 2f, "rock" to 1f, "edm" to 1f),
        "romantic" to mapOf("love" to 3f, "kiss" to 2f, "heart" to 2f, "forever" to 2f, "slow" to 1f, "r&b" to 1f, "soul" to 1f, "ballad" to 2f),
        "sleep" to mapOf("sleep" to 3f, "dream" to 2f, "night" to 1f, "peace" to 2f, "calm" to 1f, "ambient" to 2f, "classical" to 1f, "lo-fi" to 1f)
    )

    private val TIME_PROFILES = mapOf(
        "morning" to mapOf("morning" to 3f, "sunrise" to 3f, "wake" to 2f, "good day" to 2f, "bright" to 1f, "fresh" to 1f, "upbeat" to 1f, "indie" to 1f, "folk" to 1f),
        "workout" to mapOf("stronger" to 2f, "power" to 2f, "run" to 2f, "pump" to 2f, "workout" to 2f, "gym" to 2f, "energy" to 1f, "hip-hop" to 1f, "edm" to 1f, "rock" to 1f),
        "evening" to mapOf("evening" to 3f, "sunset" to 2f, "chill" to 2f, "unwind" to 2f, "night" to 1f, "r&b" to 1f, "soul" to 1f, "lo-fi" to 1f),
        "late night" to mapOf("midnight" to 3f, "late" to 2f, "night" to 1f, "dark" to 2f, "slow" to 1f, "r&b" to 1f, "ambient" to 1f, "electronic" to 1f)
    )

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
    sourceValidityStatus().canEnterPlaybackFlow()

