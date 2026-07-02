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
import android.view.MotionEvent
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.hilt.navigation.compose.hiltViewModel
import com.audiophile.musicplayer.data.dj.AiDjViewModel
import com.audiophile.musicplayer.playback.NowPlayingViewModel
import com.audiophile.musicplayer.data.source.isConfirmedPlayable
import com.audiophile.musicplayer.data.source.isMetadataOnly
import com.audiophile.musicplayer.data.source.isUnavailable
import com.audiophile.musicplayer.auto.AndroidAutoHelper
import com.audiophile.musicplayer.ui.AppMainScreen
import com.audiophile.musicplayer.ui.MainViewModel
import com.audiophile.musicplayer.ui.PersonalizedMixViewModel
import com.audiophile.musicplayer.ui.SharedImportPayload
import com.audiophile.musicplayer.ui.visualizer.VantaVisualizerViewModel
import com.audiophile.musicplayer.data.local.entities.SourceType
import com.audiophile.musicplayer.playback.NowPlayingState
import com.audiophile.musicplayer.search.UnifiedSearchEngine
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
    private var nowPlayingViewModel: NowPlayingViewModel? = null
    private var sharedImportPayload by mutableStateOf<SharedImportPayload?>(null)

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

    private val debugReceiver = if (BuildConfig.DEBUG) object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val action = intent.action ?: return
            val query = intent.getStringExtra("query") ?: "The Weeknd"
            Log.w("VANTA_DEVICE_TEST", "debug_broadcast action=$action query=$query")
            Log.v("VANTA_DEBUG", "Received broadcast action=$action query=$query")
            debugScope.launch {
                // Wait for ViewModels to be initialized (Compose composition may not be complete yet)
                var waitAttempts = 0
                while ((mainViewModel == null || nowPlayingViewModel == null) && waitAttempts < 20) {
                    delay(250)
                    waitAttempts++
                }
                val vm = mainViewModel
                val npm = nowPlayingViewModel
                if (vm == null || npm == null) {
                    Log.v("VANTA_DEBUG", "ViewModels not initialized after ${waitAttempts * 250}ms, aborting")
                    return@launch
                }
                Log.v("VANTA_DEBUG", "ViewModels ready after ${waitAttempts * 250}ms")
                when (action) {
                    "com.audiophile.musicplayer.DEBUG_SEARCH" -> {
                        vm.onQueryChanged(query)
                        vm.search()
                        Log.v("VANTA_DEBUG", "Triggered search for query=$query")
                    }
                    "com.audiophile.musicplayer.DEBUG_PLAY_FIRST" -> {
                        val results = vm.uiState.value.sourceResults
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
                        vm.onQueryChanged(query)
                        vm.search()
                        Log.v("VANTA_DEBUG", "Triggered search for query=$query, waiting for results...")
                        var attempts = 0
                        while (attempts < 70) {
                            delay(500)
                            val state = vm.uiState.value
                            if (!state.isSearching && state.sourceResults.isNotEmpty()) {
                                val searchIntent = UnifiedSearchEngine.parse(query)
                                val top = state.searchTopResult
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
                                Log.d("VANTA_DEBUG", "  sourceId=${s.sourceId} prov=[${s.externalProviderId}] extId=[${s.externalTrackId}] expires=${s.expiresAtMs} url=${s.streamUrl.take(60)}")
                            }
                        }
                    }
                    "com.audiophile.musicplayer.DEBUG_PAUSE" -> {
                        val container = (context.applicationContext as android.app.Application).appContainer
                        container.playerController.pause()
                        Log.w("VANTA_DEBUG", "DEBUG_PAUSE: userPauseRequested should be true")
                    }
                    "com.audiophile.musicplayer.DEBUG_NEXT" -> {
                        val container = (context.applicationContext as android.app.Application).appContainer
                        container.playerController.next()
                        Log.w("VANTA_DEBUG", "DEBUG_NEXT: skip requested")
                    }
                    "com.audiophile.musicplayer.DEBUG_SEARCH_PLAY_PAUSE" -> {
                        val container = (context.applicationContext as android.app.Application).appContainer
                        vm.onQueryChanged(query)
                        vm.search()
                        var attempts = 0
                        while (attempts < 30) {
                            delay(300)
                            val results = vm.uiState.value.sourceResults
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
                    "com.audiophile.musicplayer.DEBUG_RAPID_SKIP" -> {
                        val container = (context.applicationContext as android.app.Application).appContainer
                        val count = intent.getIntExtra("count", 20)
                        repeat(count) {
                            container.playerController.next()
                            delay(80)
                        }
                        Log.w("VANTA_DEBUG", "DEBUG_RAPID_SKIP: sent $count skip requests")
                    }
                }
            }
        }
    } else null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = false
            isAppearanceLightNavigationBars = false
        }
        val container = application.appContainer
        window.decorView.post {
            AndroidAutoHelper.warmUpPlaybackService(this@MainActivity)
        }
        sharedImportPayload = extractSharedImportPayload(intent) ?: savedInstanceState?.let {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                it.getSerializable("shared_import_payload", SharedImportPayload::class.java)
            } else {
                @Suppress("DEPRECATION")
                it.getSerializable("shared_import_payload") as? SharedImportPayload
            }
        }

        setContent {
            val mainVm: MainViewModel = hiltViewModel()
            val nowPlayingVm: NowPlayingViewModel = hiltViewModel()
            val aiDjViewModel: AiDjViewModel = hiltViewModel()
            val personalizedMixViewModel: PersonalizedMixViewModel = hiltViewModel()
            val visualizerViewModel: VantaVisualizerViewModel = viewModel()
            // Store references for debug receiver
            androidx.compose.runtime.LaunchedEffect(mainVm) {
                mainViewModel = mainVm
            }
            androidx.compose.runtime.LaunchedEffect(nowPlayingVm) {
                nowPlayingViewModel = nowPlayingVm
            }
            VantaTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = AppBackground
                ) {
                    AppMainScreen(
                        mainViewModel = mainVm,
                        nowPlayingViewModel = nowPlayingVm,
                        aiDjViewModel = aiDjViewModel,
                        personalizedMixViewModel = personalizedMixViewModel,
                        visualizerViewModel = visualizerViewModel,
                        accountManager = container.accountManager,
                        vantaSocialManager = container.vantaSocialManager,
                        sharedImportPayload = sharedImportPayload,
                        onSharedImportConsumed = { sharedImportPayload = null }
                    )
                }
            }
        }

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
                addAction("com.audiophile.musicplayer.DEBUG_NEXT")
                addAction("com.audiophile.musicplayer.DEBUG_SEARCH_PLAY_PAUSE")
                addAction("com.audiophile.musicplayer.DEBUG_ARTIST_RADIO")
                addAction("com.audiophile.musicplayer.DEBUG_RAPID_SKIP")
            }
            ContextCompat.registerReceiver(
                applicationContext,
                debugReceiver,
                filter,
                ContextCompat.RECEIVER_NOT_EXPORTED
            )
            debugReceiverRegistered = true
            Log.w("VANTA_DEVICE_TEST", "debug_receiver_registered")
        }
    }

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
                ?.use { it.readText() }
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

