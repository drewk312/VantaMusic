package com.audiophile.musicplayer.tv

import android.os.Bundle
import android.util.Log
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.audiophile.musicplayer.AppContainer
import com.audiophile.musicplayer.AudiophileApp
import com.audiophile.musicplayer.auto.AndroidAutoHelper
import com.audiophile.musicplayer.playback.NowPlayingViewModel
import com.audiophile.musicplayer.ui.MainViewModel
import com.audiophile.musicplayer.ui.SearchViewModel
import com.audiophile.musicplayer.ui.theme.VantaTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Leanback (Android TV) entry point. Reuses the same AppContainer, playback
 * service, and ViewModels as the phone app, but renders a 10-foot, D-pad
 * driven UI instead of the touch shell.
 */
@AndroidEntryPoint
class TvMainActivity : ComponentActivity() {

    private val activityScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var containerReady by mutableStateOf<AppContainer?>(null)
    private var containerError by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        activityScope.launch {
            runCatching {
                withContext(Dispatchers.IO) { (application as AudiophileApp).appContainer }
            }.onSuccess { container ->
                Log.i("VANTA_TV", "container_ready")
                containerReady = container
                AndroidAutoHelper.warmUpPlaybackService(this@TvMainActivity)
            }.onFailure { error ->
                Log.e("VANTA_TV", "container_init_failed", error)
                containerError = error.message ?: "Startup failed"
            }
        }

        setContent {
            VantaTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = TvTheme.Bg) {
                    val error = containerError
                    val container = containerReady
                    when {
                        error != null -> TvStartupError(message = error)
                        container == null -> TvLoadingScreen()
                        else -> {
                            val mainViewModel: MainViewModel = hiltViewModel()
                            val searchViewModel: SearchViewModel = hiltViewModel()
                            val nowPlayingViewModel: NowPlayingViewModel = hiltViewModel()
                            val personalizedMixViewModel: com.audiophile.musicplayer.ui.PersonalizedMixViewModel =
                                hiltViewModel()
                            LaunchedEffect(nowPlayingViewModel) {
                                nowPlayingViewModel.restore()
                            }
                            TvApp(
                                container = container,
                                mainViewModel = mainViewModel,
                                searchViewModel = searchViewModel,
                                nowPlayingViewModel = nowPlayingViewModel,
                                personalizedMixViewModel = personalizedMixViewModel
                            )
                        }
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        activityScope.cancel()
        super.onDestroy()
    }
}

@Composable
private fun TvStartupError(message: String) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            "VANTA",
            color = TvTheme.Text,
            fontSize = 42.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 8.sp
        )
        Spacer(Modifier.height(16.dp))
        Text("Couldn’t start", color = TvTheme.HiResGold, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(8.dp))
        Text(message, color = TvTheme.TextSecondary, fontSize = 14.sp)
    }
}
