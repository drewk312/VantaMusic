package com.audiophile.musicplayer

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.OpenableColumns
import android.util.Log
import android.view.Gravity
import android.view.MotionEvent
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import com.audiophile.musicplayer.BuildConfig
import androidx.activity.ComponentActivity
import dagger.hilt.android.AndroidEntryPoint
import androidx.activity.compose.setContent
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import com.audiophile.musicplayer.ui.theme.VantaTheme
import com.audiophile.musicplayer.ui.AppBackground
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import com.audiophile.musicplayer.playback.NowPlayingViewModel
import com.audiophile.musicplayer.common.AcceptanceTruth
import com.audiophile.musicplayer.data.source.isConfirmedPlayable
import com.audiophile.musicplayer.data.source.isMetadataOnly
import com.audiophile.musicplayer.data.source.isUnavailable
import com.audiophile.musicplayer.auto.AndroidAutoHelper
import com.audiophile.musicplayer.ui.AppMainScreen
import com.audiophile.musicplayer.ui.MainViewModel
import com.audiophile.musicplayer.ui.SearchViewModel
import com.audiophile.musicplayer.ui.SharedImportPayload
import com.audiophile.musicplayer.data.local.entities.SourceType
import com.audiophile.musicplayer.data.importer.MAX_IMPORT_TEXT_CHARS
import com.audiophile.musicplayer.data.importer.readBoundedText
import com.audiophile.musicplayer.playback.NowPlayingState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val activityScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val debugScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var debugReceiverRegistered = false
    private var mainViewModel: MainViewModel? = null
    private var searchViewModel: SearchViewModel? = null
    private var nowPlayingViewModel: NowPlayingViewModel? = null
    private var sharedImportPayload by mutableStateOf<SharedImportPayload?>(null)
    private var startupStatusView: TextView? = null
    @Volatile private var nativeWindowFocused = false

    override fun dispatchGenericMotionEvent(ev: MotionEvent): Boolean {
        return try {
            super.dispatchGenericMotionEvent(ev)
        } catch (e: IllegalStateException) {
            if (e.message == "The ACTION_HOVER_EXIT event was not cleared.") {
                Log.w("VANTA_INPUT_GUARD", "Suppressed Compose hover-exit platform exception", e)
                true
            } else {
                throw e
            }
        }
    }

    private val debugReceiver: BroadcastReceiver? = if (BuildConfig.DEBUG) object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val action = intent.action ?: return
            val query = intent.getStringExtra("query") ?: "The Weeknd"
            Log.w("VANTA_DEVICE_TEST", "debug_broadcast action=$action query=$query")
            Log.v("VANTA_DEBUG", "Received broadcast action=$action query=$query")
            debugScope.launch {
                // Wait for ViewModels to be initialized (Compose composition may not be complete yet)
                var waitAttempts = 0
                while ((mainViewModel == null || searchViewModel == null || nowPlayingViewModel == null) && waitAttempts < 20) {
                    delay(250)
                    waitAttempts++
                }
                val vm = mainViewModel
                val searchVm = searchViewModel
                val npm = nowPlayingViewModel
                if (vm == null || searchVm == null || npm == null) {
                    Log.v("VANTA_DEBUG", "ViewModels not initialized after ${waitAttempts * 250}ms, aborting")
                    return@launch
                }
                Log.v("VANTA_DEBUG", "ViewModels ready after ${waitAttempts * 250}ms")
                when (action) {
                    "com.audiophile.musicplayer.DEBUG_SEARCH" -> {
                        searchVm.onQueryChanged(query)
                        searchVm.search()
                        Log.v("VANTA_DEBUG", "Triggered search for query=$query")
                    }
                    "com.audiophile.musicplayer.DEBUG_PLAY_FIRST" -> {
                        val results = searchVm.uiState.value.songs
                        if (results.isNotEmpty()) {
                            val first = results.first()
                            vm.playSourceResult(first)
                            npm.restore()
                            Log.v("VANTA_DEBUG", "Triggered play for result=${first.title} by ${first.artist}")
                        } else {
                            Log.v("VANTA_DEBUG", "No source results to play")
                        }
                    }
                    "com.audiophile.musicplayer.DEBUG_SEARCH_AND_PLAY" -> {
                        searchVm.onQueryChanged(query)
                        searchVm.search()
                        Log.v("VANTA_DEBUG", "Triggered search for query=$query, waiting for results...")
                        var attempts = 0
                        while (attempts < 70) {
                            delay(500)
                            val state = searchVm.uiState.value
                            if (!state.isSearching && state.songs.isNotEmpty()) {
                                val top = state.topResult
                                if (top != null) {
                                    Log.v("VANTA_DEBUG", "Auto-playing identity-valid result=${top.title} by ${top.artist}")
                                    vm.playSourceResult(top)
                                    npm.restore()
                                } else {
                                    Log.w("VANTA_DEBUG", "Blocked autoplay — no identity-valid candidate for query=$query")
                                }
                                break
                            }
                            attempts++
                        }
                        if (attempts >= 70) {
                            Log.v("VANTA_DEBUG", "Timeout waiting for search results")
                        }
                    }
                    "com.audiophile.musicplayer.DEBUG_PROVIDER_SEARCH_AND_PLAY" -> {
                        val providerId = intent.getStringExtra("providerId")
                        if (providerId.isNullOrBlank()) {
                            Log.e("VANTA_PROVIDER_TEST", "Missing required extra: providerId")
                            return@launch
                        }
                        Log.v("VANTA_DEBUG", "DEBUG_PROVIDER_SEARCH_AND_PLAY: providerId=$providerId query=$query")
                        val container = (context.applicationContext as android.app.Application).appContainer
                        val sourceRegistry = container.sourceRegistry
                        // Search only the requested provider with 10s timeout
                        val searchStartMs = System.currentTimeMillis()
                        val searchResults = withContext(Dispatchers.IO) {
                            sourceRegistry.searchSingle(providerId, query, timeoutMs = 10000L)
                        }
                        val searchMs = System.currentTimeMillis() - searchStartMs
                        Log.d("VANTA_PROVIDER_TEST", "providerId=$providerId query='$query' searchMs=$searchMs resultCount=${searchResults.size}")
                        if (searchResults.isEmpty()) {
                            Log.e("VANTA_PROVIDER_TEST", "No results from provider '$providerId' for query='$query'")
                            return@launch
                        }
                        // Pick best match: prefer exact title match, then first result
                        val bestMatch = searchResults.firstOrNull {
                            it.title.equals(query, ignoreCase = true)
                        } ?: searchResults.firstOrNull {
                            it.title.contains(query, ignoreCase = true)
                        } ?: searchResults.firstOrNull()

                        if (bestMatch == null) {
                            Log.e("VANTA_PROVIDER_TEST", "No matches found for query: $query")
                            return@launch
                        }

                        Log.d("VANTA_PROVIDER_TEST", "selectedTitle='${bestMatch.title}' selectedArtist='${bestMatch.artist}' selectedAlbum='${bestMatch.album}' sourceTrackId=${bestMatch.id} qualityLabel=${bestMatch.qualityLabel}")
                        // Resolve stream
                        val resolveStartMs = System.currentTimeMillis()
                        val resolvedStream = withContext(Dispatchers.IO) {
                            sourceRegistry.resolveStream(providerId, bestMatch.id, timeoutMs = 10000L)
                        }
                        val resolveMs = System.currentTimeMillis() - resolveStartMs
                        if (resolvedStream == null || resolvedStream.streamUrl.isBlank()) {
                            Log.e("VANTA_PROVIDER_TEST", "resolveStream returned null/blank for $providerId:${bestMatch.id} resolveMs=$resolveMs")
                            return@launch
                        }
                        val streamHost = runCatching { java.net.URI(resolvedStream.streamUrl).host }.getOrDefault("unknown")
                        Log.d("VANTA_PROVIDER_TEST", "streamHost=$streamHost expiresAt=${resolvedStream.expiresAt} resolveMs=$resolveMs bitrate=${resolvedStream.bitrateKbps}kbps")
                        // Validate stream
                        val validateStartMs = System.currentTimeMillis()
                        val validated = withContext(Dispatchers.IO) {
                            try {
                                var conn = java.net.URL(resolvedStream.streamUrl).openConnection() as java.net.HttpURLConnection
                                conn.setRequestProperty("User-Agent", "VANTA/1.0 (Android 14; en-US)")
                                conn.connectTimeout = 5000
                                conn.readTimeout = 5000
                                conn.requestMethod = "HEAD"
                                conn.connect()
                                var code = conn.responseCode
                                if (code == 405 || code == 400) {
                                    conn.disconnect()
                                    conn = java.net.URL(resolvedStream.streamUrl).openConnection() as java.net.HttpURLConnection
                                    conn.setRequestProperty("User-Agent", "VANTA/1.0 (Android 14; en-US)")
                                    conn.setRequestProperty("Range", "bytes=0-1")
                                    conn.connectTimeout = 5000
                                    conn.readTimeout = 5000
                                    conn.connect()
                                    code = conn.responseCode
                                }
                                conn.disconnect()
                                code in 200..299
                            } catch (e: Exception) {
                                Log.e("VANTA_PROVIDER_TEST", "Validation error: ${e.message}")
                                false
                            }
                        }
                        val validateMs = System.currentTimeMillis() - validateStartMs
                        Log.d("VANTA_PROVIDER_TEST", "validationResult=${if (validated) "PASS" else "FAIL"} validateMs=$validateMs")
                        if (!validated) {
                            Log.e("VANTA_PROVIDER_TEST", "Stream validation failed — cannot play")
                            return@launch
                        }
                        // Add track source to DB
                        val trackId = withContext(Dispatchers.IO) {
                            container.trackRepository.addTrackSource(
                                title = bestMatch.title,
                                artist = bestMatch.artist,
                                album = bestMatch.album,
                                coverArtUrl = bestMatch.artworkUrl,
                                sourceType = com.audiophile.musicplayer.data.local.entities.SourceType.ADDON,
                                streamUrl = resolvedStream.streamUrl,
                                bitrate = resolvedStream.bitrateKbps,
                                isrc = bestMatch.isrc,
                                durationMs = bestMatch.durationMs,
                                externalProviderId = providerId,
                                externalTrackId = bestMatch.id,
                                expiresAtMs = resolvedStream.expiresAt
                            )
                        }
                        Log.d("VANTA_PROVIDER_TEST", "addTrackSource returned trackId=$trackId")
                        // Reload and play
                        val track = withContext(Dispatchers.IO) {
                            container.trackRepository.getTrackWithSources(trackId)
                        }
                        if (track != null) {
                            container.playerController.playQueue(listOf(track), 0)
                            container.nowPlayingStateStore.save(
                                com.audiophile.musicplayer.playback.NowPlayingState(
                                    trackId = track.track.trackId.toString(),
                                    title = bestMatch.title,
                                    artist = bestMatch.artist,
                                    album = bestMatch.album,
                                    artworkUrl = bestMatch.artworkUrl,
                                    isrc = bestMatch.isrc,
                                    durationMs = bestMatch.durationMs ?: 0L,
                                    queuePosition = 0,
                                    queueSize = 1
                                )
                            )
                            npm.restore()
                            Log.i("VANTA_PROVIDER_TEST", "PLAYBACK STARTED: title='${bestMatch.title}' artist='${bestMatch.artist}' trackId=$trackId providerId=$providerId streamHost=$streamHost")
                        } else {
                            Log.e("VANTA_PROVIDER_TEST", "getTrackWithSources returned null for trackId=$trackId")
                        }
                    }
                    "com.audiophile.musicplayer.TEST_INSERT_NULL_PROVIDER" -> {
                        val container = (context.applicationContext as android.app.Application).appContainer
                        val repo = container.trackRepository
                        val player = container.playerController
                        val trackId = withContext(Dispatchers.IO) {
                            repo.addTrackSource(
                                title = "TEST_NULL_PROVIDER",
                                artist = "TEST_ARTIST",
                                album = null,
                                coverArtUrl = null,
                                sourceType = SourceType.ADDON,
                                streamUrl = "https://streaming-qobuz-std.akamaized.net/file?uid=0&eid=0&fmt=7&profile=raw&etsp=0&hmac=test",
                                bitrate = 320,
                                externalProviderId = null,
                                externalTrackId = null,
                                expiresAtMs = 0L
                            )
                        }
                        Log.w("VANTA_DEBUG", "INSERT_NULL_PROVIDER: trackId=$trackId externalProviderId=null externalTrackId=null expiresAtMs=0")
                        val track = withContext(Dispatchers.IO) { repo.getTrackWithSources(trackId) }
                        if (track != null) {
                            player.playQueue(listOf(track), 0)
                            Log.w("VANTA_DEBUG", "INSERT_NULL_PROVIDER: auto-playing trackId=$trackId")
                        }
                    }

                    "com.audiophile.musicplayer.TEST_MARK_EXPIRED" -> {
                        val container = (context.applicationContext as android.app.Application).appContainer
                        val targetTrackId = intent.getLongExtra("trackId", 3L)
                        val repo = container.trackRepository
                        val player = container.playerController
                        val track = withContext(Dispatchers.IO) { repo.getTrackWithSources(targetTrackId) }
                        if (track != null && track.sources.isNotEmpty()) {
                            val source = track.sources.first()
                            val expiredSource = source.copy(
                                streamUrl = "https://streaming-qobuz-std.akamaized.net/file?uid=0&eid=0&expired=1&fmt=7&etsp=0&hmac=dead",
                                expiresAtMs = 0L
                            )
                            withContext(Dispatchers.IO) { repo.updateSource(expiredSource) }
                            Log.w("VANTA_DEBUG", "MARK_EXPIRED sourceId=${source.sourceId}: providerId=${source.externalProviderId} externalTrackId=${source.externalTrackId} — URL replaced with expired URL")
                            val updated = withContext(Dispatchers.IO) { repo.getTrackWithSources(targetTrackId) }
                            if (updated != null) {
                                player.playQueue(listOf(updated), 0)
                                Log.w("VANTA_DEBUG", "MARK_EXPIRED: auto-playing trackId=$targetTrackId for recovery test")
                            }
                        } else {
                            Log.e("VANTA_DEBUG", "MARK_EXPIRED: track $targetTrackId not found or has no sources")
                        }
                    }

                    "com.audiophile.musicplayer.TEST_LIST_SOURCES" -> {
                        val container = (context.applicationContext as android.app.Application).appContainer
                        val repo = container.trackRepository
                        val allTracks = withContext(Dispatchers.IO) { repo.getAllTracks() }
                        Log.d("VANTA_DEBUG", "=== Current DB state: ${allTracks.size} tracks ===")
                        for (t in allTracks) {
                            Log.d("VANTA_DEBUG", "trackId=${t.track.trackId} title=[${t.track.title}] artist=[${t.track.artist}] sources=${t.sources.size}")
                            for (s in t.sources) {
                                Log.d("VANTA_DEBUG", "  sourceId=${s.sourceId} prov=[${s.externalProviderId}] extId=[${s.externalTrackId}] expires=${s.expiresAtMs} hasUrl=${s.streamUrl.isNotBlank()}")
                            }
                        }
                    }
                    "com.audiophile.musicplayer.DEBUG_PAUSE" -> {
                        val container = (context.applicationContext as android.app.Application).appContainer
                        container.playerController.pause()
                        Log.w("VANTA_DEBUG", "DEBUG_PAUSE: userPauseRequested should be true")
                    }
                    "com.audiophile.musicplayer.DEBUG_RESUME" -> {
                        val container = (context.applicationContext as android.app.Application).appContainer
                        container.playerController.resume()
                        Log.w("VANTA_DEVICE_TEST", "DEBUG_RESUME")
                    }
                    "com.audiophile.musicplayer.DEBUG_SEEK" -> {
                        val container = (context.applicationContext as android.app.Application).appContainer
                        val positionMs = intent.getLongExtra("positionMs", 2_000L)
                        container.playerController.seekTo(positionMs)
                        Log.w("VANTA_DEVICE_TEST", "DEBUG_SEEK positionMs=$positionMs")
                    }
                    "com.audiophile.musicplayer.DEBUG_PLAY_FILE" -> {
                        val rawPath = intent.getStringExtra("path")
                            ?: intent.getStringExtra("uri")
                            ?: ""
                        if (rawPath.isBlank()) {
                            Log.e("VANTA_DEVICE_TEST", "DEBUG_PLAY_FILE missing path/uri extra")
                            return@launch
                        }
                        val playUri = when {
                            rawPath.startsWith("content:", ignoreCase = true) -> rawPath
                            rawPath.startsWith("file:", ignoreCase = true) -> rawPath
                            else -> "file://${rawPath.replace('\\', '/')}"
                        }
                        val fileName = rawPath.substringAfterLast('/').substringAfterLast('\\')
                        val title = intent.getStringExtra("title")
                            ?: fileName.substringBeforeLast('.').ifBlank { "Hardware test" }
                        val artist = intent.getStringExtra("artist") ?: "VANTA Hardware Test"
                        val container = (context.applicationContext as android.app.Application).appContainer
                        val trackId = withContext(Dispatchers.IO) {
                            container.trackRepository.addTrackSource(
                                title = title,
                                artist = artist,
                                album = "Hardware Acceptance",
                                coverArtUrl = null,
                                sourceType = com.audiophile.musicplayer.data.local.entities.SourceType.LOCAL,
                                streamUrl = playUri,
                                bitrate = 0
                            )
                        }
                        if (trackId <= 0L) {
                            Log.e("VANTA_DEVICE_TEST", "DEBUG_PLAY_FILE addTrackSource failed path=$playUri")
                            return@launch
                        }
                        val track = withContext(Dispatchers.IO) {
                            container.trackRepository.getTrackWithSources(trackId)
                        }
                        if (track == null) {
                            Log.e("VANTA_DEVICE_TEST", "DEBUG_PLAY_FILE track missing trackId=$trackId")
                            return@launch
                        }
                        Log.w("VANTA_DEVICE_TEST", "DEBUG_PLAY_FILE path=$playUri trackId=$trackId title='$title'")
                        vm.playQueue(listOf(track), 0)
                        npm.restore()
                    }
                    "com.audiophile.musicplayer.DEBUG_NEXT" -> {
                        val container = (context.applicationContext as android.app.Application).appContainer
                        container.playerController.next()
                        Log.w("VANTA_DEBUG", "DEBUG_NEXT: skip requested")
                    }
                    "com.audiophile.musicplayer.DEBUG_SEARCH_PLAY_PAUSE" -> {
                        val container = (context.applicationContext as android.app.Application).appContainer
                        searchVm.onQueryChanged(query)
                        searchVm.search()
                        var attempts = 0
                        while (attempts < 30) {
                            delay(300)
                            val results = searchVm.uiState.value.songs
                            if (results.isNotEmpty()) {
                                container.playerController.pause()
                                vm.playSourceResult(results.first())
                                container.playerController.pause()
                                Log.w("VANTA_DEBUG", "SEARCH_PLAY_PAUSE: paused during resolve for '${results.first().title}' by '${results.first().artist}'")
                                break
                            }
                            attempts++
                        }
                    }
                    "com.audiophile.musicplayer.DEBUG_ARTIST_RADIO" -> {
                        val artist = intent.getStringExtra("artist") ?: query
                        vm.playArtistRadio(artist)
                        Log.w("VANTA_DEBUG", "DEBUG_ARTIST_RADIO: started for artist='$artist'")
                    }
                    "com.audiophile.musicplayer.DEBUG_SONG_RADIO" -> {
                        val title = intent.getStringExtra("title") ?: "Blinding Lights"
                        val artist = intent.getStringExtra("artist") ?: "The Weeknd"
                        val album = intent.getStringExtra("album")
                        val genre = intent.getStringExtra("genre")
                        var seedTrack = vm.uiState.value.library.firstOrNull {
                            it.track.title.equals(title, ignoreCase = true) &&
                                it.track.artist.equals(artist, ignoreCase = true) &&
                                it.sources.any { source -> source.streamUrl.isNotBlank() }
                        }
                        if (intent.getBooleanExtra("searchAndPlaySeed", true)) {
                            searchVm.onQueryChanged("$title $artist")
                            searchVm.search()
                            var attempts = 0
                            while (attempts < 70) {
                                delay(500)
                                val state = searchVm.uiState.value
                                if (!state.isSearching && state.songs.isNotEmpty()) {
                                    val top = state.topResult ?: state.songs.firstOrNull()
                                    if (top != null) {
                                        vm.playSourceResult(top)
                                        npm.restore()
                                        delay(3000)
                                        seedTrack = vm.uiState.value.library.firstOrNull {
                                            it.track.title.equals(top.title, ignoreCase = true) &&
                                                it.track.artist.equals(top.artist, ignoreCase = true)
                                        } ?: seedTrack
                                    }
                                    break
                                }
                                attempts++
                            }
                        }
                        val seedTitle = seedTrack?.track?.title ?: title
                        val seedArtist = seedTrack?.track?.artist ?: artist
                        vm.playSongRadio(
                            title = seedTitle,
                            artist = seedArtist,
                            album = seedTrack?.track?.albumName ?: album,
                            genre = seedTrack?.track?.genre ?: genre,
                            seedTrack = seedTrack
                        )
                        Log.w(
                            "VANTA_DEBUG",
                            "DEBUG_SONG_RADIO: started seedTitle='$seedTitle' seedArtist='$seedArtist' " +
                                "seedPresent=${seedTrack != null}"
                        )
                    }
                    "com.audiophile.musicplayer.DEBUG_RAPID_SKIP" -> {
                        val container = (context.applicationContext as android.app.Application).appContainer
                        val count = intent.getIntExtra("count", 20)
                        repeat(count) {
                            container.playerController.next()
                            delay(80)
                        }
                        Log.w("VANTA_DEBUG", "DEBUG_RAPID_SKIP: sent $count skip requests")
                    }
                    "com.audiophile.musicplayer.DEBUG_VIEW_ARTIST" -> {
                        val np = npm.state.value
                        val artist = intent.getStringExtra("artist") ?: np.artist ?: query
                        val id = intent.getStringExtra("canonicalArtistId")?.toLongOrNull()
                            ?: np.canonicalArtistId?.toLongOrNull()
                        vm.loadArtistCatalog(artist, id)
                        var attempts = 0
                        while (attempts < 40) {
                            delay(250)
                            val catalog = vm.uiState.value.artistCatalog
                            if (catalog != null && !vm.uiState.value.artistCatalogLoading) {
                                AcceptanceTruth.artist(
                                    artist = catalog.artist.name,
                                    navigationMode = if (catalog.artist.id != null) "canonical" else "name",
                                    seedTrackId = np.trackId,
                                    activePlaybackTrackId = np.trackId,
                                    artistId = catalog.artist.id
                                )
                                Log.w(
                                    "VANTA_DEBUG",
                                    "DEBUG_VIEW_ARTIST: name='${catalog.artist.name}' id=${catalog.artist.id} " +
                                        "tracks=${catalog.tracks.size}"
                                )
                                break
                            }
                            attempts++
                        }
                    }
                    "com.audiophile.musicplayer.DEBUG_VIEW_ALBUM" -> {
                        val np = npm.state.value
                        val album = intent.getStringExtra("album") ?: np.album ?: query
                        val artist = intent.getStringExtra("artist") ?: np.artist.orEmpty()
                        val id = intent.getStringExtra("canonicalAlbumId")?.toLongOrNull()
                            ?: np.canonicalAlbumId?.toLongOrNull()
                        vm.loadAlbumCatalog(album, artist, id)
                        var attempts = 0
                        while (attempts < 40) {
                            delay(250)
                            val catalog = vm.uiState.value.albumCatalog
                            if (catalog != null && !vm.uiState.value.albumCatalogLoading) {
                                AcceptanceTruth.album(
                                    album = catalog.album.title,
                                    artist = catalog.album.artist,
                                    seedTrackId = np.trackId,
                                    navigationMode = if (catalog.album.id != null) "canonical" else "name",
                                    albumId = catalog.album.id
                                )
                                Log.w(
                                    "VANTA_DEBUG",
                                    "DEBUG_VIEW_ALBUM: title='${catalog.album.title}' id=${catalog.album.id} " +
                                        "artist='${catalog.album.artist}'"
                                )
                                break
                            }
                            attempts++
                        }
                    }
                }
            }
        }
    } else null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // On Android TV, always use the Leanback shell — even if the phone
        // LAUNCHER activity was opened somehow.
        val uiMode = resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_TYPE_MASK
        val isTelevision = uiMode == android.content.res.Configuration.UI_MODE_TYPE_TELEVISION ||
            packageManager.hasSystemFeature(android.content.pm.PackageManager.FEATURE_LEANBACK)
        if (isTelevision) {
            startActivity(android.content.Intent(this, com.audiophile.musicplayer.tv.TvMainActivity::class.java))
            finish()
            return
        }

        WindowCompat.setDecorFitsSystemWindows(window, false)
        @Suppress("DEPRECATION")
        run {
            window.statusBarColor = android.graphics.Color.BLACK
            window.navigationBarColor = android.graphics.Color.BLACK
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                window.isStatusBarContrastEnforced = false
                window.isNavigationBarContrastEnforced = false
            }
        }
        WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = false
            isAppearanceLightNavigationBars = false
        }
        // The native boot surface is display-only. Do not let Android enqueue a
        // focus/input deadline while the process is still cold-starting.
        window.addFlags(android.view.WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE)
        window.addFlags(android.view.WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE)
        showNativeStartupSurface()
        sharedImportPayload = extractSharedImportPayload(intent) ?: savedInstanceState?.let {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                it.getSerializable("shared_import_payload", SharedImportPayload::class.java)
            } else {
                @Suppress("DEPRECATION")
                it.getSerializable("shared_import_payload") as? SharedImportPayload
            }
        }

        activityScope.launch {
            val startedAt = android.os.SystemClock.elapsedRealtime()
            Log.i("VANTA_STARTUP", "container_init_begin")
            runCatching {
                withContext(Dispatchers.IO) {
                    application.appContainer.also {
                        Log.i("VANTA_STARTUP", "container_background_ready")
                    }
                }
            }.onSuccess { container ->
                val elapsedMs = android.os.SystemClock.elapsedRealtime() - startedAt
                Log.i("VANTA_STARTUP", "container_init_complete elapsedMs=$elapsedMs")
                showFullApp(container)
            }.onFailure { error ->
                startupStatusView?.text = getString(R.string.startup_audio_engine_error)
                Log.e("VANTA_STARTUP", "container_init_failed", error)
            }
        }

    }

    private fun registerDebugReceiverIfNeeded() {
        if (BuildConfig.DEBUG && !debugReceiverRegistered) {
            val filter = IntentFilter().apply {
                addAction("com.audiophile.musicplayer.DEBUG_SEARCH")
                addAction("com.audiophile.musicplayer.DEBUG_PLAY_FIRST")
                addAction("com.audiophile.musicplayer.DEBUG_SEARCH_AND_PLAY")
                addAction("com.audiophile.musicplayer.DEBUG_PROVIDER_SEARCH_AND_PLAY")
                addAction("com.audiophile.musicplayer.TEST_INSERT_NULL_PROVIDER")
                addAction("com.audiophile.musicplayer.TEST_MARK_EXPIRED")
                addAction("com.audiophile.musicplayer.TEST_LIST_SOURCES")
                addAction("com.audiophile.musicplayer.DEBUG_PAUSE")
                addAction("com.audiophile.musicplayer.DEBUG_RESUME")
                addAction("com.audiophile.musicplayer.DEBUG_SEEK")
                addAction("com.audiophile.musicplayer.DEBUG_PLAY_FILE")
                addAction("com.audiophile.musicplayer.DEBUG_NEXT")
                addAction("com.audiophile.musicplayer.DEBUG_SEARCH_PLAY_PAUSE")
                addAction("com.audiophile.musicplayer.DEBUG_ARTIST_RADIO")
                addAction("com.audiophile.musicplayer.DEBUG_SONG_RADIO")
                addAction("com.audiophile.musicplayer.DEBUG_RAPID_SKIP")
                addAction("com.audiophile.musicplayer.DEBUG_VIEW_ARTIST")
                addAction("com.audiophile.musicplayer.DEBUG_VIEW_ALBUM")
            }
            ContextCompat.registerReceiver(
                applicationContext,
                debugReceiver,
                filter,
                // Debug-only: allow adb `am broadcast` device gates to reach this receiver.
                ContextCompat.RECEIVER_EXPORTED
            )
            debugReceiverRegistered = true
            Log.w("VANTA_DEVICE_TEST", "debug_receiver_registered")
        }
    }

    private fun showNativeStartupSurface() {
        val root = FrameLayout(this).apply {
            setBackgroundColor(android.graphics.Color.rgb(4, 6, 11))
        }
        val stack = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
        }
        val title = TextView(this).apply {
            text = this@MainActivity.getString(R.string.startup_title)
            setTextColor(android.graphics.Color.WHITE)
            textSize = 28f
            gravity = Gravity.CENTER
            letterSpacing = 0.28f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
        }
        val progress = ProgressBar(this).apply {
            indeterminateTintList = android.content.res.ColorStateList.valueOf(
                android.graphics.Color.rgb(179, 146, 255)
            )
        }
        startupStatusView = TextView(this).apply {
            text = this@MainActivity.getString(R.string.startup_preparing_audio_engine)
            setTextColor(android.graphics.Color.rgb(139, 143, 160))
            textSize = 12f
            gravity = Gravity.CENTER
            letterSpacing = 0.05f
        }
        stack.addView(title)
        stack.addView(progress, LinearLayout.LayoutParams(dp(42), dp(42)).apply {
            topMargin = dp(18)
            bottomMargin = dp(14)
        })
        stack.addView(startupStatusView)
        root.addView(
            stack,
            FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                gravity = Gravity.CENTER
                leftMargin = dp(32)
                rightMargin = dp(32)
            }
        )
        setContentView(root)
    }

    private fun showFullApp(container: AppContainer) {
        startupStatusView = null
        Log.i("VANTA_STARTUP", "compose_set_content_begin")
        setContent {
            // Always call ViewModel factories unconditionally — conditional
            // hiltViewModel() calls break Compose slot identity and can stall
            // the phased startup forever (phone stuck on blank phase=0).
            val mainVm: MainViewModel = hiltViewModel()
            val searchVm: SearchViewModel = hiltViewModel()
            val nowPlayingVm: NowPlayingViewModel = hiltViewModel()
            var uiReady by remember { mutableStateOf(false) }

            LaunchedEffect(Unit) {
                Log.i("VANTA_STARTUP", "compose_ready_begin")
                mainViewModel = mainVm
                searchViewModel = searchVm
                nowPlayingViewModel = nowPlayingVm
                // Drop the native boot touch/focus shields immediately. Waiting
                // for window focus behind a lockscreen left mobile blank forever.
                window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE)
                window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE)
                uiReady = true
                Log.i("VANTA_STARTUP", "compose_ready_complete")
                registerDebugReceiverIfNeeded()
                AndroidAutoHelper.warmUpPlaybackService(this@MainActivity)
            }

            Log.i("VANTA_STARTUP", "compose_enter ready=$uiReady")
            VantaTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = AppBackground) {
                    if (uiReady) {
                        AppMainScreen(
                            mainViewModel = mainVm,
                            searchViewModel = searchVm,
                            nowPlayingViewModel = nowPlayingVm,
                            container = container,
                            sharedImportPayload = sharedImportPayload,
                            onSharedImportConsumed = { sharedImportPayload = null }
                        )
                    }
                }
            }
        }
        Log.i("VANTA_STARTUP", "compose_set_content_returned")
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        nativeWindowFocused = hasFocus
        Log.i("VANTA_STARTUP", "window_focus hasFocus=$hasFocus")
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        sharedImportPayload = extractSharedImportPayload(intent)
    }

    private fun extractSharedImportPayload(intent: Intent?): SharedImportPayload? {
        if (intent == null) return null
        return when (intent.action) {
            Intent.ACTION_SEND -> {
                val sharedText = intent.getCharSequenceExtra(Intent.EXTRA_TEXT)
                    ?.toString()
                    ?.trim()
                    ?.takeIf { it.length <= MAX_IMPORT_TEXT_CHARS }
                    ?.takeIf { it.isNotBlank() }
                if (sharedText != null) {
                    SharedImportPayload(name = "Shared Link", text = sharedText)
                } else {
                    sharedStreamUri(intent)?.let { uri ->
                        val text = readImportText(uri)
                        if (text.isNullOrBlank()) null else SharedImportPayload(
                            name = displayNameForUri(uri) ?: "Shared Tracklist",
                            text = text
                        )
                    }
                }
            }
            Intent.ACTION_VIEW -> {
                val data = intent.data
                val dataText = intent.dataString?.trim()?.takeIf { it.isNotBlank() }
                when {
                    dataText != null && dataText.startsWith("http", ignoreCase = true) ->
                        SharedImportPayload(name = "Shared Link", text = dataText)
                    data != null -> readImportText(data)?.takeIf { it.isNotBlank() }?.let { text ->
                        SharedImportPayload(name = displayNameForUri(data) ?: "Shared Tracklist", text = text)
                    }
                    else -> null
                }
            }
            else -> null
        }
    }

    @Suppress("DEPRECATION")
    private fun sharedStreamUri(intent: Intent): Uri? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
        } else {
            intent.getParcelableExtra(Intent.EXTRA_STREAM) as? Uri
        }
    }

    private fun readImportText(uri: Uri): String? {
        return runCatching {
            contentResolver.openInputStream(uri)
                ?.bufferedReader(Charsets.UTF_8)
                ?.use { it.readBoundedText() }
                ?.takeIf { it.isNotBlank() }
        }.getOrNull()
    }

    private fun displayNameForUri(uri: Uri): String? {
        return runCatching {
            contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (cursor.moveToFirst() && nameIndex >= 0) cursor.getString(nameIndex) else null
            }
        }.getOrNull()
    }

    override fun onDestroy() {
        activityScope.cancel()
        super.onDestroy()
        if (BuildConfig.DEBUG && isFinishing && debugReceiverRegistered) {
            runCatching { applicationContext.unregisterReceiver(debugReceiver) }
            debugReceiverRegistered = false
            debugScope.cancel()
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        sharedImportPayload?.let { outState.putSerializable("shared_import_payload", it) }
    }
}

