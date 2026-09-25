package com.audiophile.musicplayer.ui

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.audiophile.musicplayer.BuildConfig
import com.audiophile.musicplayer.update.AppReleaseManifest
import com.audiophile.musicplayer.update.AppUpdateInstaller
import com.audiophile.musicplayer.update.AppUpdateRepository
import kotlinx.coroutines.launch

private enum class UpdateUiState { Idle, Checking, Downloading, Ready, Failed }

@Composable
fun AboutVantaSection(
    labsUnlocked: Boolean,
    onLabsUnlocked: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val repository = remember { AppUpdateRepository() }
    var versionTaps by remember { mutableIntStateOf(0) }
    var showAbout by remember { mutableStateOf(false) }
    var manifest by remember { mutableStateOf<AppReleaseManifest?>(null) }
    var updateState by remember { mutableStateOf(UpdateUiState.Idle) }
    var donateUrl by remember {
        mutableStateOf(BuildConfig.DONATE_URL.trim())
    }

    LaunchedEffect(Unit) {
        val latest = repository.fetchManifest()
        manifest = latest
        if (!latest?.donateUrl.isNullOrBlank()) {
            donateUrl = latest.donateUrl
        }
        if (latest != null && repository.isNewer(latest)) {
            updateState = UpdateUiState.Ready
        }
    }

    val updateAvailable = manifest?.let { repository.isNewer(it) } == true

    PremiumSettingsGroup(title = "About VANTA") {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable {
                    val next = versionTaps + 1
                    versionTaps = next
                    if (!labsUnlocked && next >= 7) {
                        onLabsUnlocked()
                        Toast.makeText(context, "Labs unlocked", Toast.LENGTH_SHORT).show()
                    }
                }
                .padding(horizontal = 16.dp, vertical = 14.dp)
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Version", color = AppText, fontSize = 16.sp)
                Text(
                    "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
                    color = AppTextMuted,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }
        PremiumSettingsClickItem(
            title = when {
                updateState == UpdateUiState.Checking -> "Checking for updates"
                updateState == UpdateUiState.Downloading -> "Downloading update"
                updateState == UpdateUiState.Failed -> "Update unavailable"
                updateAvailable -> "Update VANTA"
                else -> "Check for updates"
            },
            subtitle = when {
                updateState == UpdateUiState.Downloading -> "Keep VANTA open until the installer appears"
                updateState == UpdateUiState.Failed -> "Try again when you have a connection"
                updateAvailable -> "Version ${manifest?.versionName.orEmpty()} is ready".trim()
                else -> "You're on the latest build"
            },
            onClick = {
                scope.launch {
                    when {
                        updateAvailable && updateState != UpdateUiState.Downloading -> {
                            val latest = manifest ?: return@launch
                            if (!AppUpdateInstaller.canInstallPackages(context)) {
                                AppUpdateInstaller.requestInstallPermission(context)
                                Toast.makeText(
                                    context,
                                    "Allow VANTA to install updates, then tap Update again",
                                    Toast.LENGTH_LONG
                                ).show()
                                return@launch
                            }
                            updateState = UpdateUiState.Downloading
                            val file = repository.downloadApk(latest, AppUpdateInstaller.updateCacheFile(context))
                            if (file == null) {
                                updateState = UpdateUiState.Failed
                                return@launch
                            }
                            updateState = UpdateUiState.Ready
                            if (!AppUpdateInstaller.promptInstall(context, file)) {
                                updateState = UpdateUiState.Failed
                            }
                        }
                        else -> {
                            updateState = UpdateUiState.Checking
                            val latest = repository.fetchManifest()
                            manifest = latest
                            if (!latest?.donateUrl.isNullOrBlank()) {
                                donateUrl = latest.donateUrl
                            }
                            updateState = if (latest != null && repository.isNewer(latest)) {
                                UpdateUiState.Ready
                            } else {
                                UpdateUiState.Idle
                            }
                        }
                    }
                }
            }
        )
        if (updateState == UpdateUiState.Downloading) {
            LinearProgressIndicator(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                color = AppAccent,
                trackColor = AppAccent.copy(alpha = 0.2f)
            )
        }
        PremiumSettingsClickItem(
            title = "Support on Ko-fi",
            subtitle = "ko-fi.com/drewk312 (Safe & Private)",
            onClick = {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://ko-fi.com/drewk312"))
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                try {
                    context.startActivity(intent)
                } catch (_: android.content.ActivityNotFoundException) {
                    Toast.makeText(context, "Couldn't open the support link", Toast.LENGTH_SHORT).show()
                }
            }
        )
        PremiumSettingsClickItem(
            title = "App info",
            subtitle = "One library. Many sources. Better discovery.",
            onClick = { showAbout = !showAbout },
            showDivider = false
        )
    }

    if (showAbout) {
        VantaCard {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("About VANTA", color = AppText, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Text("Your music, beautifully connected.", color = AppTextSecondary, fontSize = 14.sp)
                Text(
                    "Bring your libraries together, discover your next favorite, and make every listening session your own.",
                    color = AppTextSecondary,
                    fontSize = 14.sp
                )
                Spacer(Modifier.height(8.dp))
                Text("Listening", color = AppText, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                Text("\u2022 Qobuz, Tidal, Deezer, and Amazon when a catalog match exists", color = AppTextSecondary, fontSize = 13.sp)
                Text("\u2022 Local files and optional private cloud libraries", color = AppTextSecondary, fontSize = 13.sp)
                Spacer(Modifier.height(8.dp))
                Text("Sound", color = AppText, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                Text(
                    "Immersive Sound is built in — width, depth, and balance tuned for headphones.",
                    color = AppTextSecondary,
                    fontSize = 13.sp,
                    lineHeight = 18.sp
                )
            }
        }
    }
}
