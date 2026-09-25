package com.audiophile.musicplayer.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.audiophile.musicplayer.AppContainer
import com.audiophile.musicplayer.playback.NowPlayingViewModel

data class SharedImportPayload(
    val name: String,
    val text: String
) : java.io.Serializable

@Composable
fun AppMainScreen(
    mainViewModel: MainViewModel,
    searchViewModel: SearchViewModel,
    nowPlayingViewModel: NowPlayingViewModel,
    container: AppContainer,
    sharedImportPayload: SharedImportPayload? = null,
    onSharedImportConsumed: () -> Unit = {}
) {
    var showSplash by remember { mutableStateOf(true) }

    Box(modifier = Modifier.fillMaxSize()) {
        // The native startup surface already covers cold initialization.
        val bypassSplash = true
        if (showSplash && !bypassSplash) {
            VantaSplashScreen(onFinished = { showSplash = false })
        } else {
            AppNavGraph(
                mainViewModel = mainViewModel,
                searchViewModel = searchViewModel,
                nowPlayingViewModel = nowPlayingViewModel,
                container = container,
                sharedImportPayload = sharedImportPayload,
                onSharedImportConsumed = onSharedImportConsumed
            )
        }
    }
}
