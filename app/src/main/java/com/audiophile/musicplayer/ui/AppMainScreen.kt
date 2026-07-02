package com.audiophile.musicplayer.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.audiophile.musicplayer.account.AccountManagerimport com.audiophile.musicplayer.social.VantaSocialManager

import com.audiophile.musicplayer.data.dj.AiDjViewModel
import com.audiophile.musicplayer.playback.NowPlayingViewModel
import com.audiophile.musicplayer.ui.visualizer.VantaVisualizerViewModel

data class SharedImportPayload(
    val name: String,
    val text: String
) : java.io.Serializable

@Composable
fun AppMainScreen(
    mainViewModel: MainViewModel,
    nowPlayingViewModel: NowPlayingViewModel,
    aiDjViewModel: AiDjViewModel,
    personalizedMixViewModel: PersonalizedMixViewModel,
    visualizerViewModel: VantaVisualizerViewModel,
    accountManager: AccountManager,    vantaSocialManager: VantaSocialManager,

    sharedImportPayload: SharedImportPayload? = null,
    onSharedImportConsumed: () -> Unit = {}
) {
    var showSplash by remember { mutableStateOf(true) }

    Box(modifier = Modifier.fillMaxSize()) {
        // EMERGENCY: Bypassing splash screen for recovery
        val bypassSplash = true
        if (showSplash && !bypassSplash) {
            VantaSplashScreen(onFinished = { showSplash = false })
        } else {
            AppNavGraph(
                mainViewModel = mainViewModel,
                nowPlayingViewModel = nowPlayingViewModel,
                aiDjViewModel = aiDjViewModel,
                personalizedMixViewModel = personalizedMixViewModel,
                visualizerViewModel = visualizerViewModel,
                accountManager = accountManager,                vantaSocialManager = vantaSocialManager,

                sharedImportPayload = sharedImportPayload,
                onSharedImportConsumed = onSharedImportConsumed
            )
        }
    }
}

