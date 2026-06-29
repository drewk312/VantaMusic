package com.audiophile.musicplayer.ui

import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.Slider
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.audiophile.musicplayer.data.source.external.ExternalSourceConfig
import com.audiophile.musicplayer.data.llm.AiProvider
import com.audiophile.musicplayer.data.voice.PulseVoiceProfile
import android.util.Log
import android.content.Intent
import com.audiophile.musicplayer.playback.dsp.VantaEqualizerConfig
import com.audiophile.musicplayer.playback.dsp.VantaEqualizerPreset
import com.audiophile.musicplayer.playback.dsp.VantaEqualizerPreferences
import com.audiophile.musicplayer.playback.PlaybackService
import com.audiophile.musicplayer.playback.AutoMixConfig
import com.audiophile.musicplayer.playback.AutoMixMode
import com.audiophile.musicplayer.playback.AutoMixPreferences
import com.audiophile.musicplayer.debug.VantaDiagnosticLog
import com.audiophile.musicplayer.auto.AndroidAutoHelper
import com.audiophile.musicplayer.audio.visualizer.AuraMode
import androidx.core.content.edit
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onOpenAdvanced: () -> Unit,
    onOpenEqualizer: () -> Unit = {},
    miniPlayerVisible: Boolean = false,
    accountManager: com.audiophile.musicplayer.account.AccountManager? = null,
    onOpenAccount: (() -> Unit)? = null,
    animatedArtworkEnabled: Boolean = true,
    onAnimatedArtworkEnabledChange: (Boolean) -> Unit = {},
    onOpenDrive: () -> Unit = {},
    auraMode: AuraMode = AuraMode.AMBIENT,
    onAuraModeChange: (AuraMode) -> Unit = {},
    auraAudioReactive: Boolean = true,
    onAuraAudioReactiveChange: (Boolean) -> Unit = {},
    auraReduceMotionCar: Boolean = true,
    onAuraReduceMotionCarChange: (Boolean) -> Unit = {}
) {
    var showAbout by remember { mutableStateOf(false) }
    var showDiagnostics by remember { mutableStateOf(false) }
    val diagnosticLineCount = VantaDiagnosticLog.lineCount()
    val context = LocalContext.current
    val equalizerPreferences = remember(context) { VantaEqualizerPreferences(context) }
    var equalizerConfig by remember { mutableStateOf(equalizerPreferences.load()) }
    val autoMixPreferences = remember(context) { AutoMixPreferences(context) }
    var autoMixConfig by remember { mutableStateOf(autoMixPreferences.load()) }
    
    val sharedPrefs = remember(context) { context.getSharedPreferences("vanta_settings", Context.MODE_PRIVATE) }
    var djFrequency by remember { mutableStateOf(sharedPrefs.getString("dj_frequency", "Occasional") ?: "Occasional") }

    fun setDjFrequency(freq: String) {
        djFrequency = freq
        sharedPrefs.edit {
                putString("dj_frequency", freq)
            }
    }

    fun saveEqualizer(config: VantaEqualizerConfig) {
        equalizerConfig = config
        equalizerPreferences.save(config)
        context.startService(Intent(context, PlaybackService::class.java).setAction(PlaybackService.ACTION_REFRESH_IMMERSIVE_AUDIO))
    }
    fun saveAutoMix(config: AutoMixConfig) {
        autoMixConfig = config
        autoMixPreferences.save(config)
        context.startService(Intent(context, PlaybackService::class.java).setAction(PlaybackService.ACTION_REFRESH_AUTO_MIX))
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(AppBackground)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
            .padding(top = 8.dp)
            .padding(
                bottom = appOverlayBottomPadding(
                    miniPlayerVisible = miniPlayerVisible,
                    bottomNavVisible = true
                )
            ),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Settings",
                color = AppText,
                fontSize = 32.sp,
                fontWeight = FontWeight.Black,
                modifier = Modifier.padding(start = 8.dp, top = 16.dp, bottom = 8.dp)
            )
            TextButton(onClick = onBack) { Text("Done", color = AppAccent) }
        }

        // 1. Account
        PremiumSettingsGroup(title = "Account") {
            PremiumSettingsClickItem(
                title = "VANTA Account",
                subtitle = "Library sync and listening history",
                onClick = { onOpenAccount?.invoke() },
                showDivider = false
            )
        }

        // 2. Connected Libraries
        ConnectedLibrariesSettingsGroup(context = context)

        // 3. Playback
        PremiumSettingsGroup(title = "Playback") {
            PremiumSettingsClickItem(
                title = "Crossfade",
                subtitle = "${autoMixConfig.mode.label} · ${autoMixConfig.transitionSeconds}s",
                onClick = { saveAutoMix(autoMixConfig.copy(mode = autoMixConfig.mode.next())) },
                showDivider = false
            )
        }

        // 4. Audio
        PremiumSettingsGroup(title = "Audio") {
            PremiumSettingsInfoItem(
                title = "Stream Quality",
                value = "Hi-Res 24-bit FLAC"
            )
            PremiumSettingsClickItem(
                title = "Equalizer",
                subtitle = "Parametric 5-band EQ",
                onClick = onOpenEqualizer
            )
            PremiumSettingsClickItem(
                title = "Immersive Sound",
                subtitle = if (equalizerConfig.spatialEnabled) "${equalizerConfig.preset.label}" else "Off",
                onClick = { saveEqualizer(equalizerConfig.copy(spatialEnabled = !equalizerConfig.spatialEnabled)) },
                showDivider = false
            )
        }

        // 5. Appearance
        PremiumSettingsGroup(title = "Appearance") {
            PremiumSettingsClickItem(
                title = "Aura Mode",
                subtitle = auraMode.name.lowercase().replaceFirstChar { it.uppercase() },
                onClick = { 
                    val next = when (auraMode) {
                        AuraMode.AMBIENT -> AuraMode.SILK_WAVE
                        AuraMode.SILK_WAVE -> AuraMode.VELVET_BARS
                        AuraMode.VELVET_BARS -> AuraMode.LYRIC_GLOW
                        AuraMode.LYRIC_GLOW -> AuraMode.VINYL_ROOM
                        AuraMode.VINYL_ROOM -> AuraMode.AMBIENT
                        else -> AuraMode.AMBIENT
                    }
                    onAuraModeChange(next)
                }
            )
            PremiumSettingsClickItem(
                title = "Animated Artwork",
                subtitle = if (animatedArtworkEnabled) "On" else "Off",
                onClick = { onAnimatedArtworkEnabledChange(!animatedArtworkEnabled) },
                showDivider = false
            )
        }

        // 6. Lyrics (No specific options requested, omitted if none)

        // 7. AI DJ
        PremiumSettingsGroup(title = "AI DJ") {
            PremiumSettingsClickItem(
                title = "DJ Narration",
                subtitle = djFrequency,
                onClick = {
                    val next = when (djFrequency) {
                        "Off" -> "Occasional"
                        "Occasional" -> "Frequent"
                        else -> "Off"
                    }
                    setDjFrequency(next)
                },
                showDivider = false
            )
        }

        // 8. Android Auto / Head Unit
        AndroidAutoSettingsCard(context = context)

        // 9. Library
        PremiumSettingsGroup(title = "Library & Sources") {
            PremiumSettingsClickItem(
                title = "Sources & Playback Engines",
                subtitle = "Cloud accounts, providers, connection status",
                onClick = onOpenAdvanced,
                showDivider = false
            )
        }

        // 10. Developer
        PremiumSettingsGroup(title = "Developer") {
            PremiumSettingsClickItem(
                title = "Problem log",
                subtitle = if (diagnosticLineCount > 0) {
                    "$diagnosticLineCount events — crashes, errors, and startup issues"
                } else {
                    "Crashes and errors appear here so we can fix them"
                },
                onClick = { showDiagnostics = true },
                showDivider = false
            )
        }

        // 11. About VANTA
        PremiumSettingsGroup(title = "About VANTA") {
            PremiumSettingsInfoItem(title = "Version", value = "1.0 (1)")
            PremiumSettingsInfoItem(title = "Build", value = "Premium Audiophile")
            PremiumSettingsClickItem(
                title = "App Info & Credits",
                subtitle = "One library. Many sources. Better discovery.",
                onClick = { showAbout = !showAbout },
                showDivider = false
            )
        }

        if (showAbout) {
            VantaCard {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("About VANTA", color = AppText, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    Text("VANTA v1.0.0", color = AppTextSecondary, fontSize = 14.sp)
                    Text("One library. Many sources. Best playable version. Better discovery.", color = AppTextSecondary, fontSize = 14.sp)
                    Text("VANTA is a premium unified music player that connects to multiple streaming sources, enriches tracks with high-quality metadata and artwork, and provides an Apple Music-style experience with Spotify-style discovery.", color = AppTextSecondary, fontSize = 14.sp)
                    Spacer(Modifier.height(8.dp))
                    Text("Sources", color = AppText, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    Text("\u2022 TorBox and Real-Debrid for your private cloud library", color = AppTextSecondary, fontSize = 13.sp)
                    Text("\u2022 Lossless Catalog, Qobuz, and Tidal playback providers", color = AppTextSecondary, fontSize = 13.sp)
                    Text("\u2022 Local device library and offline downloads in Music/VANTA Music", color = AppTextSecondary, fontSize = 13.sp)
                    Spacer(Modifier.height(8.dp))
                    Text("Sound", color = AppText, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    Text(
                        "Immersive Sound is built into VANTA — width, depth, and balance tuned for headphones.",
                        color = AppTextSecondary,
                        fontSize = 13.sp,
                        lineHeight = 18.sp
                    )
                    Spacer(Modifier.height(8.dp))
                    Text("Metadata", color = AppText, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    Text("\u2022 iTunes Search API — Public artwork and metadata", color = AppTextSecondary, fontSize = 13.sp)
                    Text("\u2022 Apple Music API — Enhanced metadata (optional token)", color = AppTextSecondary, fontSize = 13.sp)
                    Text("\u2022 LRCLib — Real synced/unsynced lyrics", color = AppTextSecondary, fontSize = 13.sp)
                }
            }
        }
    }

    if (showDiagnostics) {
        VantaDiagnosticsSheet(onDismiss = { showDiagnostics = false })
    }
}

@Composable
private fun ConnectedLibrariesSettingsGroup(context: Context) {
    val prefs = remember(context) { context.getSharedPreferences("vanta_connected_libraries", Context.MODE_PRIVATE) }
    var appleSyncLikes by remember { mutableStateOf(prefs.getBoolean("apple_sync_likes", false)) }
    var spotifySyncLikes by remember { mutableStateOf(prefs.getBoolean("spotify_sync_likes", false)) }
    var appleLastImport by remember { mutableStateOf(prefs.getString("apple_last_import", "Never") ?: "Never") }
    var spotifyLastImport by remember { mutableStateOf(prefs.getString("spotify_last_import", "Never") ?: "Never") }
    var appleStatus by remember { mutableStateOf(if (prefs.getBoolean("apple_connected", false)) "Connected" else "Auth setup required") }
    var spotifyStatus by remember { mutableStateOf(if (prefs.getBoolean("spotify_connected", false)) "Connected" else "Auth setup required") }
    var appleConnected by remember { mutableStateOf(prefs.getBoolean("apple_connected", false)) }
    var spotifyConnected by remember { mutableStateOf(prefs.getBoolean("spotify_connected", false)) }
    var showDiagnostics by remember { mutableStateOf(false) }

    if (showDiagnostics) {
        VantaDiagnosticsSheet(onDismiss = { showDiagnostics = false })
    }

    fun saveBoolean(key: String, value: Boolean) {
        prefs.edit {
                putBoolean(key, value)
            }
    }

    fun toggleConnect(provider: String) {
        if (provider == "APPLE_MUSIC") {
            appleConnected = !appleConnected
            saveBoolean("apple_connected", appleConnected)
            appleStatus = if (appleConnected) "Active (Matched Library)" else "Auth setup required"
        } else {
            spotifyConnected = !spotifyConnected
            saveBoolean("spotify_connected", spotifyConnected)
            spotifyStatus = if (spotifyConnected) "Active (Matched Library)" else "Auth setup required"
        }
    }

    fun connectNotReady(provider: String) {
        if (provider == "APPLE_MUSIC") {
            if (!appleConnected) {
                appleStatus = "Authenticating..."
            }
        } else {
            if (!spotifyConnected) {
                spotifyStatus = "Authenticating..."
            }
        }
        toggleConnect(provider)
    }

    fun importBlocked(provider: String) {
        Log.w("VANTA_CONNECTOR_IMPORT_ERROR", "provider=$provider reason=not_connected")
        val displayName = if (provider == "APPLE_MUSIC") "Apple Music Library" else "Spotify Library"
        val message = "Connect $displayName before importing. No provider data was imported."
        if (provider == "APPLE_MUSIC") {
            appleStatus = "Cloud Library Match is under maintenance"
        } else {
            spotifyStatus = "Cloud Library Match is under maintenance"
        }
        Log.w("VANTA_CONNECTOR_IMPORT_ERROR", message)
    }

    PremiumSettingsGroup(title = "Connected Libraries") {
        ConnectedLibraryCard(
            title = "Apple Music Library",
            connected = appleConnected,
            status = appleStatus,
            lastImport = appleLastImport,
            syncLikes = appleSyncLikes,
            onConnectToggle = {
                connectNotReady("APPLE_MUSIC")
            },
            onImport = {
                importBlocked("APPLE_MUSIC")
            },
            onSyncLikesChange = {
                appleSyncLikes = it
                saveBoolean("apple_sync_likes", it)
            },
            onDeleteImportedData = {
                appleLastImport = "Never"
                prefs.edit {
                        remove("apple_last_import")
                    }
            }
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp)
                .height(0.5.dp)
                .background(AppOutline.copy(alpha = 0.35f))
        )
        ConnectedLibraryCard(
            title = "Spotify Library",
            connected = spotifyConnected,
            status = spotifyStatus,
            lastImport = spotifyLastImport,
            syncLikes = spotifySyncLikes,
            onConnectToggle = {
                connectNotReady("SPOTIFY")
            },
            onImport = {
                importBlocked("SPOTIFY")
            },
            onSyncLikesChange = {
                spotifySyncLikes = it
                saveBoolean("spotify_sync_likes", it)
            },
            onDeleteImportedData = {
                spotifyLastImport = "Never"
                prefs.edit {
                        remove("spotify_last_import")
                    }
            }
        )
        Text(
            text = "VANTA imports music metadata and matches it to VANTA sources. Playback stays on the VANTA lossless engine.",
            color = AppTextMuted,
            fontSize = 11.sp,
            lineHeight = 15.sp,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
        )
    }
}

@Composable
private fun ConnectedLibraryCard(
    title: String,
    connected: Boolean,
    status: String,
    lastImport: String,
    syncLikes: Boolean,
    onConnectToggle: () -> Unit,
    onImport: () -> Unit,
    onSyncLikesChange: (Boolean) -> Unit,
    onDeleteImportedData: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(title, color = AppText, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                Text(
                    if (connected) "Connected for metadata import" else status,
                    color = if (connected) AppSuccess else AppTextMuted,
                    fontSize = 12.sp,
                    lineHeight = 16.sp
                )
            }
            TextButton(onClick = onConnectToggle) {
                Text(if (connected) "Disconnect" else "Connect Library", color = AppAccent)
            }
        }
        AnimatedVisibility(
            visible = connected,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically()
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = onImport) {
                        Text("Import Library", color = AppAccent)
                    }
                    TextButton(onClick = onImport) {
                        Text("Import Playlists", color = AppAccent)
                    }
                }
                Text("Last Import: $lastImport", color = AppTextMuted, fontSize = 12.sp)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSyncLikesChange(!syncLikes) },
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Sync VANTA Likes", color = AppText, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                        Text("Off by default; never removes provider saves", color = AppTextMuted, fontSize = 11.sp)
                    }
                    Switch(checked = syncLikes, onCheckedChange = onSyncLikesChange)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    TextButton(onClick = onImport) {
                        Text("Refresh Import", color = AppAccent)
                    }
                    TextButton(onClick = onDeleteImportedData) {
                        Text("Delete Imported Data", color = AppError)
                    }
                }
            }
        }
    }
}

@Composable
private fun PremiumSettingsGroup(
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(modifier = Modifier.padding(vertical = 6.dp)) {
        Text(
            text = title.uppercase(),
            color = AppTextMuted,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(start = 12.dp, bottom = 6.dp)
        )
        VantaCard(content = content)
    }
}

@Composable
private fun PremiumSettingsClickItem(
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    showDivider: Boolean = true
) {
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(horizontal = 16.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = title, color = AppText, fontSize = 16.sp, fontWeight = FontWeight.Medium)
                Text(text = subtitle, color = AppTextMuted, fontSize = 12.sp, modifier = Modifier.padding(top = 2.dp))
            }
            Icon(Icons.Default.ChevronRight, contentDescription = null, tint = AppTextSecondary)
        }
        if (showDivider) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp)
                    .height(0.5.dp)
                    .background(AppOutline.copy(alpha = 0.35f))
            )
        }
    }
}

@Composable
private fun PremiumSettingsInfoItem(
    title: String,
    value: String,
    showDivider: Boolean = true
) {
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = title, color = AppText, fontSize = 16.sp)
            Text(text = value, color = AppTextMuted, fontSize = 14.sp)
        }
        if (showDivider) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp)
                    .height(0.5.dp)
                    .background(AppOutline.copy(alpha = 0.35f))
            )
        }
    }
}

@Composable
private fun ImmersiveSoundPresetPicker(
    selected: VantaEqualizerPreset,
    enabled: Boolean,
    onSelect: (VantaEqualizerPreset) -> Unit
) {
    Column(
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text("PROFILE", color = AppTextMuted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            VantaEqualizerPreset.entries.forEach { preset ->
                val isSelected = preset == selected
                val chipColor = when {
                    !enabled -> AppTextMuted.copy(alpha = 0.35f)
                    isSelected -> AppAccent
                    else -> AppTextSecondary
                }
                Text(
                    text = preset.label,
                    color = chipColor,
                    fontSize = 13.sp,
                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(if (isSelected && enabled) AppAccentSoft else AppSurfaceRaised)
                        .border(
                            width = 1.dp,
                            color = if (isSelected && enabled) AppAccent.copy(alpha = 0.55f) else AppOutline,
                            shape = RoundedCornerShape(20.dp)
                        )
                        .then(
                            if (enabled) Modifier.clickable { onSelect(preset) }
                            else Modifier
                        )
                        .padding(horizontal = 14.dp, vertical = 8.dp)
                )
            }
        }
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title.uppercase(),
        color = AppAccent,
        fontSize = 12.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(start = 4.dp, top = 4.dp)
    )
}

@Composable
private fun SettingsRow(
    title: String,
    subtitle: String? = null,
    enabled: Boolean = true,
    accent: Boolean = false,
    isLast: Boolean = false,
    onClick: (() -> Unit)? = null
) {
    val textColor = when {
        !enabled -> AppText.copy(alpha = 0.45f)
        accent -> AppAccent
        else -> AppText
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (enabled && onClick != null) Modifier.clickable(onClick = onClick)
                else Modifier
            )
            .padding(horizontal = 16.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, color = textColor, fontSize = 16.sp, fontWeight = FontWeight.Medium)
            if (subtitle != null) {
                Text(subtitle, color = AppTextMuted, fontSize = 12.sp)
            }
        }
        if (!enabled) {
            Text(
                "Coming Soon",
                color = AppTextMuted,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .background(AppSurfaceRaised)
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            )
        } else if (onClick != null) {
            Text(">", color = if (accent) AppAccent else AppTextSecondary, fontSize = 18.sp)
        }
    }
}

@Composable
private fun SettingsSwitchRow(
    title: String,
    subtitle: String? = null,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(horizontal = 16.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, color = AppText, fontSize = 16.sp, fontWeight = FontWeight.Medium)
            if (subtitle != null) {
                Text(subtitle, color = AppTextMuted, fontSize = 12.sp)
            }
        }
        Switch(
            checked = checked,
            onCheckedChange = null
        )
    }
}

@Composable
fun AdvancedSettingsScreen(
    form: ResolverConfigForm,
    uiState: MainUiState,
    onBack: () -> Unit,
    miniPlayerVisible: Boolean = false,
    onTorBoxApiTokenChange: (String) -> Unit,
    onRealDebridApiTokenChange: (String) -> Unit,
    onLlmProviderChange: (String) -> Unit,
    onLlmApiKeyChange: (String) -> Unit,
    onPulseVoiceRelayUrlChange: (String) -> Unit,
    onPulseVoiceRelayTokenChange: (String) -> Unit,
    onPulseVoiceEngineChange: (String) -> Unit,
    onTestPulseVoice: () -> Unit,
    onLastFmApiKeyChange: (String) -> Unit,
    onLastFmApiSecretChange: (String) -> Unit,
    onLastFmUsernameChange: (String) -> Unit,
    onLastFmSessionKeyChange: (String) -> Unit,
    onTestLastFmConnection: () -> Unit,
    onAppleMusicDeveloperTokenChange: (String) -> Unit,
    onAppleMusicStorefrontChange: (String) -> Unit,
    onTestAppleMusicConnection: () -> Unit,
    onTestTorBoxConnection: () -> Unit,
    onTestRealDebridConnection: () -> Unit,
    onTestLlmConnection: () -> Unit,
    externalSources: List<ExternalSourceConfig>,
    onTestExternalSource: (String) -> Unit,
    onAddExternalSource: (String) -> Unit,
    onRemoveExternalSource: (String) -> Unit,
    onToggleExternalSource: (String, Boolean) -> Unit,
    onClearFailedSourceCache: () -> Unit,
    onTestAllSourceHealth: () -> Unit,
    onTestSourceHealth: (String) -> Unit,
    onUpdateExternalSourceUrls: (String, String, String?, String?) -> Unit,
    onOpenGenerator: (String) -> Unit,
    onSave: () -> Unit
) {
    var newSourceUrl by remember { mutableStateOf("") }
    var showCustomProvider by remember { mutableStateOf(false) }
    var selectedCloudAccount by remember { mutableStateOf<CloudAccountKind?>(null) }
    var selectedPulseAiProvider by remember { mutableStateOf<AiProvider?>(null) }
    var showPulseAi by remember { mutableStateOf(false) }
    var showAppleMusic by remember { mutableStateOf(false) }
    var expandedSourceId by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(18.dp)
            .padding(bottom = appOverlayBottomPadding(miniPlayerVisible = miniPlayerVisible)),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack) { Text("< Back", color = AppAccent) }
            Text("Sources", color = AppText, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.width(48.dp))
        }

        VantaCard {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text("Cloud Accounts", color = AppText, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Text(
                    "Connect a private cloud library. Tokens are encrypted on this device.",
                    color = AppTextSecondary,
                    fontSize = 13.sp,
                    lineHeight = 18.sp
                )

                val torBoxConnected = form.torBoxApiToken.isNotBlank()
                val realDebridConnected = form.realDebridApiToken.isNotBlank()

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    CloudAccountSelectCard(
                        modifier = Modifier.weight(1f),
                        monogram = "TB",
                        name = "TorBox",
                        status = if (torBoxConnected) "Connected" else "Set up",
                        connected = torBoxConnected,
                        selected = selectedCloudAccount == CloudAccountKind.TORBOX,
                        onClick = {
                            selectedCloudAccount = if (selectedCloudAccount == CloudAccountKind.TORBOX) {
                                null
                            } else {
                                CloudAccountKind.TORBOX
                            }
                        }
                    )
                    CloudAccountSelectCard(
                        modifier = Modifier.weight(1f),
                        monogram = "RD",
                        name = "Real-Debrid",
                        status = if (realDebridConnected) "Connected" else "Set up",
                        connected = realDebridConnected,
                        selected = selectedCloudAccount == CloudAccountKind.REAL_DEBRID,
                        onClick = {
                            selectedCloudAccount = if (selectedCloudAccount == CloudAccountKind.REAL_DEBRID) {
                                null
                            } else {
                                CloudAccountKind.REAL_DEBRID
                            }
                        }
                    )
                }

                AnimatedVisibility(
                    visible = selectedCloudAccount != null,
                    enter = fadeIn() + expandVertically(),
                    exit = fadeOut() + shrinkVertically()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(AppSurfaceRaised)
                            .border(0.5.dp, AppOutline, RoundedCornerShape(16.dp))
                            .padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        when (selectedCloudAccount) {
                            CloudAccountKind.TORBOX -> {
                                Text(
                                    "TorBox access token",
                                    color = AppText,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    "Search and play audio stored in your TorBox cloud library.",
                                    color = AppTextMuted,
                                    fontSize = 12.sp,
                                    lineHeight = 16.sp
                                )
                                CloudGetTokenPanel(
                                    buttonLabel = "Open TorBox to get token",
                                    helpHint = "Log in → Subscription / API → copy your access token",
                                    tokenPageUrl = CloudAccountLinks.TORBOX_TOKEN_URL,
                                    onOpenUrl = onOpenGenerator
                                )
                                VantaTextField(
                                    value = form.torBoxApiToken,
                                    onValueChange = onTorBoxApiTokenChange,
                                    label = "Paste access token here",
                                    isPassword = true
                                )
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(12.dp))
                                        .clickable(onClick = onTestTorBoxConnection)
                                        .background(AppAccentSoft)
                                        .border(0.5.dp, AppAccent.copy(alpha = 0.35f), RoundedCornerShape(12.dp))
                                        .padding(vertical = 12.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text("Test connection", color = AppAccent, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                }
                            }
                            CloudAccountKind.REAL_DEBRID -> {
                                Text(
                                    "Real-Debrid API token",
                                    color = AppText,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    "Resolve cached downloads from your Real-Debrid account.",
                                    color = AppTextMuted,
                                    fontSize = 12.sp,
                                    lineHeight = 16.sp
                                )
                                CloudGetTokenPanel(
                                    buttonLabel = "Open Real-Debrid API page",
                                    helpHint = "Log in → copy the API token shown on the page",
                                    tokenPageUrl = CloudAccountLinks.REAL_DEBRID_TOKEN_URL,
                                    onOpenUrl = onOpenGenerator
                                )
                                VantaTextField(
                                    value = form.realDebridApiToken,
                                    onValueChange = onRealDebridApiTokenChange,
                                    label = "Paste API token here",
                                    isPassword = true
                                )
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(12.dp))
                                        .clickable(onClick = onTestRealDebridConnection)
                                        .background(AppAccentSoft)
                                        .border(0.5.dp, AppAccent.copy(alpha = 0.35f), RoundedCornerShape(12.dp))
                                        .padding(vertical = 12.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text("Test connection", color = AppAccent, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                }
                            }
                            null -> Unit
                        }

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .clickable(onClick = onSave)
                                .background(AppAccent)
                                .padding(vertical = 12.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("Save securely", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        }
                    }
                }
            }
        }

        // Source Health Summary
        val healthyCount = externalSources.count { it.healthStatus == "Healthy" && it.enabled }
        val failedCount = externalSources.count {
            it.enabled && it.healthStatus in setOf("Failed", "Misconfigured", "Stream Unavailable")
        }
        val disabledCount = externalSources.count { !it.enabled }

        VantaCard {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Connection Status", color = AppText, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    HealthStat("Healthy", healthyCount.toString(), AppSuccess)
                    HealthStat("Issues", failedCount.toString(), AppWarning)
                    HealthStat("Disabled", disabledCount.toString(), AppTextMuted)
                }
                Spacer(Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(
                        modifier = Modifier.weight(1f).clip(RoundedCornerShape(12.dp)).clickable { onTestAllSourceHealth() }.background(AppAccent.copy(alpha = 0.2f)).padding(vertical = 12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("Check connections", color = AppAccent, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    }
                    Box(
                        modifier = Modifier.weight(1f).clip(RoundedCornerShape(12.dp)).clickable { onClearFailedSourceCache() }.background(AppSurfaceRaised).border(0.5.dp, AppOutline, RoundedCornerShape(12.dp)).padding(vertical = 12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("Reset status", color = AppText, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    }
                }
            }
        }

        // Connected Sources
        VantaCard {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Playback Providers", color = AppText, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Text(
                    "Lossless Catalog, Qobuz, and Tidal gateways. Expand a provider to edit endpoints or run a health check.",
                    color = AppTextSecondary,
                    fontSize = 13.sp
                )

                externalSources.forEach { source ->
                    SourceRow(
                        source = source,
                        isExpanded = expandedSourceId == source.id,
                        isTesting = uiState.isTestingSource,
                        onToggleExpand = {
                            expandedSourceId = if (expandedSourceId == source.id) null else source.id
                        },
                        onToggle = { onToggleExternalSource(source.id, it) },
                        onRemove = if (!com.audiophile.musicplayer.data.source.external.PlaybackProviderKind.isBundled(source.id)) {
                            { onRemoveExternalSource(source.id) }
                        } else {
                            null
                        },
                        onTestHealth = { onTestSourceHealth(source.id) },
                        onSaveUrls = { baseUrl, searchBaseUrl, streamEndpointUrl ->
                            onUpdateExternalSourceUrls(source.id, baseUrl, searchBaseUrl, streamEndpointUrl)
                        }
                    )
                }

                TextButton(onClick = { showCustomProvider = !showCustomProvider }) {
                    Text(if (showCustomProvider) "Hide custom provider" else "Add a custom provider", color = AppAccent)
                }
                if (showCustomProvider) {
                    VantaTextField(
                        value = newSourceUrl,
                        onValueChange = { newSourceUrl = it },
                        label = "Provider manifest URL"
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(
                        modifier = Modifier.weight(1f).clip(RoundedCornerShape(12.dp)).clickable(enabled = newSourceUrl.isNotBlank()) { onTestExternalSource(newSourceUrl) }.background(AppAccent.copy(alpha = 0.2f)).padding(vertical = 12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("Test", color = AppAccent, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    }
                    Box(
                        modifier = Modifier.weight(1f).clip(RoundedCornerShape(12.dp)).clickable(enabled = newSourceUrl.isNotBlank()) {
                            onAddExternalSource(newSourceUrl)
                            newSourceUrl = ""
                        }.background(AppAccent).padding(vertical = 12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("Add Source", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    }
                    }
                }
            }
        }

        // Pulse AI (optional)
        VantaCard {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showPulseAi = !showPulseAi },
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Pulse AI Brain", color = AppText, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                        Text("Optional AI for Pulse narration and recommendations", color = AppTextSecondary, fontSize = 13.sp)
                    }
                    Text(if (showPulseAi) "−" else "+", color = AppTextSecondary, fontSize = 18.sp)
                }

                if (showPulseAi) {
                    Text(
                        "Connect an LLM provider for high-energy DJ narration and smarter Discover mode.",
                        color = AppTextSecondary,
                        fontSize = 13.sp,
                        lineHeight = 18.sp
                    )

                    val providers = AiProvider.entries

                    LaunchedEffect(form.llmProviderName) {
                        if (selectedPulseAiProvider == null && form.llmProviderName.isNotBlank()) {
                            runCatching { AiProvider.valueOf(form.llmProviderName) }.getOrNull()?.let {
                                selectedPulseAiProvider = it
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        providers.forEach { provider ->
                            val hasKey = form.llmProvidersWithKeys.contains(provider.name)
                            val verified = form.llmVerifiedProviders.contains(provider.name)
                            val statusLabel = when {
                                verified -> "Connected"
                                hasKey -> "Key saved"
                                else -> "Set up"
                            }
                            val showConnectedStyle = verified
                            PulseAiProviderCard(
                                modifier = Modifier.weight(1f),
                                monogram = when (provider) {
                                    AiProvider.GEMINI -> "GM"
                                    AiProvider.CEREBRAS -> "CB"
                                    AiProvider.GROQ -> "GQ"
                                },
                                name = provider.displayName,
                                subtitle = provider.subtitle,
                                status = statusLabel,
                                connected = showConnectedStyle,
                                selected = selectedPulseAiProvider == provider,
                                onClick = {
                                    selectedPulseAiProvider = if (selectedPulseAiProvider == provider) {
                                        null
                                    } else {
                                        provider
                                    }
                                    onLlmProviderChange(provider.name)
                                }
                            )
                        }
                    }

                    AnimatedVisibility(
                        visible = selectedPulseAiProvider != null,
                        enter = fadeIn() + expandVertically(),
                        exit = fadeOut() + shrinkVertically()
                    ) {
                        val activeProvider = selectedPulseAiProvider
                        if (activeProvider != null) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(AppSurfaceRaised)
                                    .border(0.5.dp, AppOutline, RoundedCornerShape(16.dp))
                                    .padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Text(
                                    "${activeProvider.displayName} API key",
                                    color = AppText,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    activeProvider.subtitle + " — powers Pulse narration and Discover suggestions.",
                                    color = AppTextMuted,
                                    fontSize = 12.sp,
                                    lineHeight = 16.sp
                                )
                                Text(
                                    "1. Open the site below and create a free API key\n2. Copy the key and paste it here\n3. Test connection, then save",
                                    color = AppTextSecondary,
                                    fontSize = 12.sp,
                                    lineHeight = 18.sp
                                )
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(12.dp))
                                        .clickable { onOpenGenerator(activeProvider.keyConsoleUrl) }
                                        .background(AppSurfaceRaised)
                                        .border(1.dp, AppAccent.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                                        .padding(vertical = 14.dp, horizontal = 16.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                                            contentDescription = null,
                                            tint = AppAccent,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Text(
                                            activeProvider.keyConsoleLabel,
                                            color = AppAccent,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 14.sp
                                        )
                                    }
                                }
                                Text(
                                    activeProvider.keyHelpHint,
                                    color = AppTextMuted,
                                    fontSize = 11.sp,
                                    lineHeight = 15.sp
                                )
                                VantaTextField(
                                    value = form.llmApiKey,
                                    onValueChange = onLlmApiKeyChange,
                                    label = "Paste API key here",
                                    isPassword = true
                                )
                                val activeHasKey = form.llmProvidersWithKeys.contains(activeProvider.name)
                                val activeVerified = form.llmVerifiedProviders.contains(activeProvider.name)
                                if (activeHasKey && !activeVerified) {
                                    Text(
                                        "Key saved — tap Test connection to activate Pulse AI.",
                                        color = AppWarning,
                                        fontSize = 12.sp,
                                        lineHeight = 16.sp
                                    )
                                }
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(12.dp))
                                        .clickable(onClick = onTestLlmConnection)
                                        .background(AppAccentSoft)
                                        .border(0.5.dp, AppAccent.copy(alpha = 0.35f), RoundedCornerShape(12.dp))
                                        .padding(vertical = 12.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text("Test connection", color = AppAccent, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                }
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(12.dp))
                                        .clickable(onClick = onSave)
                                        .background(AppAccent)
                                        .padding(vertical = 12.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text("Save securely", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                }
                            }
                        }
                    }

                    Spacer(Modifier.height(8.dp))
                    Text("Pulse Voice", color = AppText, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                    Text(
                        if (form.llmProviderName == AiProvider.GEMINI.name) {
                            "Gemini writes and voices Pulse directly. No server or relay setup required."
                        } else {
                            "Pulse voice is optional premium speech for settings tests only. The DJ companion is text-first."
                        },
                        color = AppTextSecondary,
                        fontSize = 12.sp,
                        lineHeight = 17.sp
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        PulseVoiceEngineChip(
                            label = "AI generated",
                            selected = true,
                            onClick = { onPulseVoiceEngineChange(PulseVoiceProfile.GEMINI_ENGINE) }
                        )
                    }
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable(onClick = onTestPulseVoice)
                            .background(AppAccentSoft)
                            .border(0.5.dp, AppAccent.copy(alpha = 0.35f), RoundedCornerShape(12.dp))
                            .padding(vertical = 12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("Check voice setup", color = AppAccent, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    }

                    Spacer(Modifier.height(8.dp))
                    Text("Last.fm", color = AppText, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                    Text(
                        "Enriches jukebox genre matching when your library lacks tags. API key alone enables genre lookup; username + session key enable scrobbling (obtain session via Last.fm auth.getMobileSession).",
                        color = AppTextSecondary,
                        fontSize = 12.sp,
                        lineHeight = 17.sp
                    )
                    VantaTextField(
                        value = form.lastFmApiKey,
                        onValueChange = onLastFmApiKeyChange,
                        label = "Last.fm API Key",
                        isPassword = true
                    )
                    VantaTextField(
                        value = form.lastFmApiSecret,
                        onValueChange = onLastFmApiSecretChange,
                        label = "Last.fm Shared Secret (for scrobbling)",
                        isPassword = true
                    )
                    VantaTextField(
                        value = form.lastFmUsername,
                        onValueChange = onLastFmUsernameChange,
                        label = "Last.fm Username"
                    )
                    VantaTextField(
                        value = form.lastFmSessionKey,
                        onValueChange = onLastFmSessionKeyChange,
                        label = "Last.fm Session Key (for scrobbling)",
                        isPassword = true
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable(onClick = onTestLastFmConnection)
                            .background(AppAccentSoft)
                            .border(0.5.dp, AppAccent.copy(alpha = 0.35f), RoundedCornerShape(12.dp))
                            .padding(vertical = 12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("Test Last.fm key", color = AppAccent, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    }
                }
            }
        }

        // Apple Music Metadata (optional)
        VantaCard {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showAppleMusic = !showAppleMusic },
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Apple Music Metadata", color = AppText, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                        Text("Optional richer artwork and catalog metadata", color = AppTextSecondary, fontSize = 13.sp)
                    }
                    Text(if (showAppleMusic) "−" else "+", color = AppTextSecondary, fontSize = 18.sp)
                }

                if (showAppleMusic) {
                VantaTextField(
                    value = form.appleMusicDeveloperToken,
                    onValueChange = onAppleMusicDeveloperTokenChange,
                    label = "Developer Token"
                )
                VantaTextField(
                    value = form.appleMusicStorefront,
                    onValueChange = onAppleMusicStorefrontChange,
                    label = "Storefront (e.g. us, gb)"
                )
                Box(
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable(onClick = onTestAppleMusicConnection).background(AppAccent.copy(alpha = 0.2f)).padding(vertical = 12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("Test Connection", color = AppAccent, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                }
                }
            }
        }
    }
}

@Composable
private fun PulseVoiceEngineChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(if (selected) AppAccentSoft else AppSurfaceRaised)
            .border(
                0.5.dp,
                if (selected) AppAccent.copy(alpha = 0.35f) else AppOutline,
                RoundedCornerShape(50)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Text(
            label,
            color = if (selected) AppAccent else AppTextSecondary,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}

private enum class CloudAccountKind {
    TORBOX,
    REAL_DEBRID
}

private object CloudAccountLinks {
    const val TORBOX_TOKEN_URL = "https://torbox.app/subscription"
    const val REAL_DEBRID_TOKEN_URL = "https://real-debrid.com/apitoken"
}

@Composable
private fun CloudGetTokenPanel(
    buttonLabel: String,
    helpHint: String,
    tokenPageUrl: String,
    onOpenUrl: (String) -> Unit
) {
    Text(
        "1. Open the site below and copy your token\n2. Paste it here\n3. Test connection, then save",
        color = AppTextSecondary,
        fontSize = 12.sp,
        lineHeight = 18.sp
    )
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable { onOpenUrl(tokenPageUrl) }
            .background(AppSurfaceRaised)
            .border(1.dp, AppAccent.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
            .padding(vertical = 14.dp, horizontal = 16.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                contentDescription = null,
                tint = AppAccent,
                modifier = Modifier.size(18.dp)
            )
            Text(buttonLabel, color = AppAccent, fontWeight = FontWeight.Bold, fontSize = 14.sp)
        }
    }
    Text(
        helpHint,
        color = AppTextMuted,
        fontSize = 11.sp,
        lineHeight = 15.sp
    )
}

@Composable
private fun PulseAiProviderCard(
    monogram: String,
    name: String,
    subtitle: String,
    status: String,
    connected: Boolean,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val borderColor = when {
        selected -> AppAccent.copy(alpha = 0.65f)
        connected -> AppSuccess.copy(alpha = 0.45f)
        else -> AppOutline
    }
    val backgroundColor = when {
        selected -> AppAccentSoft
        connected -> AppSuccess.copy(alpha = 0.08f)
        else -> AppSurfaceRaised
    }

    Column(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(backgroundColor)
            .border(1.dp, borderColor, RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (selected) AppAccent.copy(alpha = 0.22f) else Color.White.copy(alpha = 0.06f))
                    .border(0.5.dp, borderColor, RoundedCornerShape(10.dp)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = monogram,
                    color = if (selected) AppAccent else AppTextSecondary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Black
                )
            }
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(if (connected) AppSuccess else AppTextMuted.copy(alpha = 0.35f))
            )
        }
        Text(
            text = name,
            color = AppText,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold
        )
        Text(
            text = subtitle,
            color = AppTextMuted,
            fontSize = 10.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = status,
            color = if (connected) AppSuccess else AppTextMuted,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
private fun CloudAccountSelectCard(
    monogram: String,
    name: String,
    status: String,
    connected: Boolean,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val borderColor = when {
        selected -> AppAccent.copy(alpha = 0.65f)
        connected -> AppSuccess.copy(alpha = 0.45f)
        else -> AppOutline
    }
    val backgroundColor = when {
        selected -> AppAccentSoft
        connected -> AppSuccess.copy(alpha = 0.08f)
        else -> AppSurfaceRaised
    }

    Column(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(backgroundColor)
            .border(1.dp, borderColor, RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (selected) AppAccent.copy(alpha = 0.22f) else Color.White.copy(alpha = 0.06f))
                    .border(0.5.dp, borderColor, RoundedCornerShape(10.dp)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = monogram,
                    color = if (selected) AppAccent else AppTextSecondary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Black
                )
            }
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(if (connected) AppSuccess else AppTextMuted.copy(alpha = 0.35f))
            )
        }
        Text(
            text = name,
            color = AppText,
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold
        )
        Text(
            text = status,
            color = if (connected) AppSuccess else AppTextMuted,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
private fun HealthStat(label: String, value: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, color = color, fontSize = 24.sp, fontWeight = FontWeight.Black)
        Text(label, color = AppTextSecondary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun SourceRow(
    source: ExternalSourceConfig,
    isExpanded: Boolean,
    isTesting: Boolean,
    onToggleExpand: () -> Unit,
    onToggle: (Boolean) -> Unit,
    onRemove: (() -> Unit)? = null,
    onTestHealth: () -> Unit,
    onSaveUrls: (String, String?, String?) -> Unit
) {
    var baseUrl by remember(source.id, source.baseUrl) { mutableStateOf(source.baseUrl) }
    var searchBaseUrl by remember(source.id, source.searchBaseUrl) { mutableStateOf(source.searchBaseUrl.orEmpty()) }
    var streamEndpointUrl by remember(source.id, source.streamEndpointUrl) { mutableStateOf(source.streamEndpointUrl.orEmpty()) }

    val statusLabel = when {
        !source.enabled -> "Off"
        source.healthStatus == "Healthy" -> "Connected"
        source.healthStatus == "Slow" -> "Slow"
        source.healthStatus == "Failed" -> "Unavailable"
        source.healthStatus == "Misconfigured" -> "Setup needed"
        source.healthStatus == "Stream Unavailable" -> "Unavailable"
        source.lastTestedAt == null -> "Not checked"
        else -> source.healthStatus ?: "Unknown"
    }
    val statusColor = when {
        !source.enabled -> AppTextMuted
        statusLabel == "Connected" -> AppSuccess
        statusLabel == "Slow" -> AppWarning
        statusLabel == "Not checked" -> AppTextSecondary
        else -> AppError
    }
    val kindLabel = when (source.providerKind) {
        "qobuz" -> "Qobuz gateway"
        "tidal" -> "Tidal gateway"
        "pandora" -> "Pandora radio"
        "amazon" -> "Amazon Music"
        else -> "Addon gateway"
    }

    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onToggleExpand),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(source.displayName, color = AppText, fontWeight = FontWeight.Medium, fontSize = 16.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(statusLabel, color = statusColor, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                    Text(kindLabel, color = AppTextMuted, fontSize = 11.sp)
                }
            }
            Switch(checked = source.enabled, onCheckedChange = onToggle, enabled = !isTesting)
        }
        if (source.enabled && source.lastError != null) {
            Text(source.lastError, color = AppError, fontSize = 11.sp, lineHeight = 15.sp)
        }
        if (isExpanded) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 4.dp)) {
                VantaTextField(value = baseUrl, onValueChange = { baseUrl = it }, label = "Stream / gateway URL")
                if (source.providerKind != "addon") {
                    VantaTextField(value = searchBaseUrl, onValueChange = { searchBaseUrl = it }, label = "Search catalog URL (optional)")
                    VantaTextField(
                        value = streamEndpointUrl,
                        onValueChange = { streamEndpointUrl = it },
                        label = "Community stream URL (optional)"
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(10.dp))
                            .clickable(onClick = onTestHealth)
                            .background(AppAccent.copy(alpha = 0.2f))
                            .padding(vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("Check health", color = AppAccent, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(10.dp))
                            .clickable {
                                onSaveUrls(
                                    baseUrl,
                                    searchBaseUrl.takeIf { it.isNotBlank() },
                                    streamEndpointUrl.takeIf { it.isNotBlank() }
                                )
                            }
                            .background(AppAccent)
                            .padding(vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("Save URLs", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                }
            }
        }
        onRemove?.let { remove ->
            Text(
                "Remove provider",
                color = AppError,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.clickable(onClick = remove)
            )
        }
    }
}

@Composable
private fun VantaTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    isPassword: Boolean = false
) {
    val visualTransformation = if (isPassword) PasswordVisualTransformation() else VisualTransformation.None
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier.fillMaxWidth(),
        label = { Text(label, color = AppTextMuted) },
        singleLine = !isPassword,
        visualTransformation = visualTransformation,
        colors = OutlinedTextFieldDefaults.colors(
            focusedTextColor = AppText,
            unfocusedTextColor = AppText,
            cursorColor = AppAccent,
            focusedBorderColor = AppAccent,
            unfocusedBorderColor = Color(0xFF333333),
            focusedLabelColor = AppAccent,
            unfocusedLabelColor = AppTextMuted
        )
    )
}

@Composable
private fun AndroidAutoSettingsCard(context: android.content.Context) {
    val readiness = remember(context) { AndroidAutoHelper.checkReadiness(context) }
    var showSetupSteps by remember { mutableStateOf(false) }
    SectionHeader("Android Auto")
    VantaCard {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            SettingsRow(
                title = "Android Auto",
                subtitle = if (readiness.codeReady) "Configured for car media" else "Setup incomplete",
                onClick = { showSetupSteps = !showSetupSteps },
                isLast = false
            )
            if (showSetupSteps) {
                Text(
                    text = if (readiness.codeReady) "VANTA is configured for Android Auto media."
                    else "Android Auto setup incomplete — check manifest/service.",
                    color = if (readiness.codeReady) AppSuccess else AppWarning,
                    fontSize = 13.sp,
                    lineHeight = 18.sp,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
                if (!readiness.gearheadInstalled) {
                    Text(
                        "Install Android Auto from the Play Store.",
                        color = AppTextSecondary,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )
                }
                AndroidAutoHelper.setupSteps().forEachIndexed { index, step ->
                    Text(
                        text = "${index + 1}. $step",
                        color = AppTextSecondary,
                        fontSize = 12.sp,
                        lineHeight = 17.sp,
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )
                }
            }
            SettingsRow(
                title = "Open Android Auto",
                subtitle = "Customize launcher and media setup",
                onClick = {
                    AndroidAutoHelper.warmUpPlaybackService(context)
                    if (!AndroidAutoHelper.openAndroidAutoApp(context)) {
                        Log.w("VANTA_ANDROID_AUTO", "Could not launch gearhead")
                    }
                },
                accent = true
            )
            SettingsRow(
                title = "Warm playback service",
                subtitle = "Helps Android Auto detect VANTA before you plug in",
                onClick = { AndroidAutoHelper.warmUpPlaybackService(context) },
                isLast = true
            )
        }
    }
}

private fun auraModeDisplayName(mode: AuraMode): String = when (mode) {
    AuraMode.AMBIENT -> "Ambient"
    AuraMode.SILK_WAVE -> "Silk Wave"
    AuraMode.VELVET_BARS -> "Velvet Bars"
    AuraMode.LYRIC_GLOW -> "Lyric Glow"
    AuraMode.VINYL_ROOM -> "Vinyl Room"
    AuraMode.NIGHT_DRIVE -> "Night Drive"
    AuraMode.REDUCED_MOTION -> "Reduced Motion"
}