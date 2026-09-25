package com.audiophile.musicplayer.ui

import android.content.Context
import android.net.Uri
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
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.Slider
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.activity.compose.BackHandler
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.ui.graphics.Brush
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
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
import com.audiophile.musicplayer.data.connectors.ConnectedLibraryProvider
import com.audiophile.musicplayer.data.connectors.ConnectedLibraryTokenStore
import com.audiophile.musicplayer.data.connectors.ConnectedLibraryImportResult
import com.audiophile.musicplayer.data.connectors.ConnectedLibraryManager
import com.audiophile.musicplayer.AudiophileApp
import androidx.compose.material3.AlertDialog
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import com.audiophile.musicplayer.data.source.external.ExternalSourceConfig
import com.audiophile.musicplayer.data.llm.AiProvider
import com.audiophile.musicplayer.data.voice.PulseVoiceProfile
import android.util.Log
import android.content.Intent
import com.audiophile.musicplayer.playback.dsp.VantaEqualizerConfig
import com.audiophile.musicplayer.playback.dsp.VantaEqualizerPreset
import com.audiophile.musicplayer.playback.dsp.VantaEqualizerPreferences
import com.audiophile.musicplayer.playback.dsp.AutoEqProfileValidator
import com.audiophile.musicplayer.playback.PlaybackService
import com.audiophile.musicplayer.playback.PlaybackCommandAuth
import com.audiophile.musicplayer.playback.PlaybackOutputPreferences
import com.audiophile.musicplayer.playback.OutputSwitchController
import com.audiophile.musicplayer.playback.SystemAudioProcessingHint
import com.audiophile.musicplayer.playback.SpatialHeadTracking
import com.audiophile.musicplayer.playback.AutoMixConfig
import com.audiophile.musicplayer.playback.AutoMixMode
import com.audiophile.musicplayer.playback.AutoMixPreferences
import com.audiophile.musicplayer.auto.AndroidAutoHelper
import com.audiophile.musicplayer.audio.visualizer.AuraMode
import androidx.core.content.edit

enum class SettingsSubpage {
    MAIN,
    AUDIO,
    PLAYBACK,
    APPEARANCE,
    SYNC_TV,
    LIBRARIES,
    AI_DJ,
    DONATION,
    ABOUT
}

@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onOpenAdvanced: () -> Unit,
    onOpenImports: () -> Unit = {},
    onLibraryChanged: () -> Unit = {},
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
    val dismissKeyboard = rememberKeyboardDismissal()
    var showLibraries by remember { mutableStateOf(false) }
    var showDiagnostics by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val equalizerPreferences = remember(context) { VantaEqualizerPreferences(context) }
    var equalizerConfig by remember { mutableStateOf(equalizerPreferences.load()) }
    var showAutoEqDialog by remember { mutableStateOf(false) }
    val autoMixPreferences = remember(context) { AutoMixPreferences(context) }
    var autoMixConfig by remember { mutableStateOf(autoMixPreferences.load()) }
    
    val sharedPrefs = remember(context) { context.getSharedPreferences("vanta_settings", Context.MODE_PRIVATE) }
    var labsUnlocked by remember { mutableStateOf(sharedPrefs.getBoolean("vanta_labs_unlocked", false)) }
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
        context.startService(com.audiophile.musicplayer.playback.PlaybackCommandAuth.createIntent(context, PlaybackService.ACTION_REFRESH_IMMERSIVE_AUDIO))
    }
    fun saveAutoMix(config: AutoMixConfig) {
        autoMixConfig = config
        autoMixPreferences.save(config)
        context.startService(com.audiophile.musicplayer.playback.PlaybackCommandAuth.createIntent(context, PlaybackService.ACTION_REFRESH_AUTO_MIX))
    }

    var currentSubpage by remember { mutableStateOf(SettingsSubpage.MAIN) }
    BackHandler(enabled = currentSubpage != SettingsSubpage.MAIN) {
        currentSubpage = SettingsSubpage.MAIN
    }

    val app = remember(context) { context.applicationContext as AudiophileApp }
    val deviceSync = remember(app) { app.appContainer.deviceLibrarySyncManager }
    val pairCode by deviceSync.pairCode.collectAsState()
    val syncStatus by deviceSync.status.collectAsState()
    val syncScope = rememberCoroutineScope()
    var enteredTvCode by rememberSaveable { mutableStateOf("") }
    LaunchedEffect(Unit) { if (pairCode.isBlank()) deviceSync.ensurePairCode() }

    val tokenStore = remember(app) { app.appContainer.connectedLibraryTokenStore }
    val connectedLibraryManager = remember(app) { app.appContainer.connectedLibraryManager }

    var streamQuality by remember {
        mutableStateOf(sharedPrefs.getString("stream_quality", "auto") ?: "auto")
    }
    var showQualityOptions by remember { mutableStateOf(false) }
    var headTrackingEnabled by remember { mutableStateOf(SpatialHeadTracking.isEnabled(context)) }
    val headTrackingAvailable = remember(context) { SpatialHeadTracking.isHeadTrackerAvailable(context) }
    var showOutputSheet by remember { mutableStateOf(false) }

    fun selectStreamQuality(next: String) {
        streamQuality = next
        sharedPrefs.edit { putString("stream_quality", next) }
        com.audiophile.musicplayer.data.source.external.SpotiFlacEndpoints.PREFERRED_STREAM_QUALITY = next
    }

    val scrollState = rememberScrollState()
    LaunchedEffect(currentSubpage) {
        scrollState.scrollTo(0)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(bottom = appOverlayBottomPadding(miniPlayerVisible = miniPlayerVisible, bottomNavVisible = true))
            .background(Brush.verticalGradient(listOf(AppBackgroundTop, AppBackgroundBottom)))
            .dismissKeyboardOnScroll()
            .imePadding()
            .verticalScroll(scrollState)
            .padding(top = appTopContentPadding(extra = 8.dp))
            .padding(horizontal = 24.dp)
            .padding(bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Navigation Header Bar
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (currentSubpage == SettingsSubpage.MAIN) {
                Text(
                    text = "Settings",
                    style = VantaType.pageTitle,
                    modifier = Modifier.padding(start = 4.dp, top = 4.dp, bottom = 4.dp)
                )
                TextButton(onClick = { dismissKeyboard(); onBack() }) {
                    Text("Done", color = AppAccent, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                }
            } else {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .clickable { dismissKeyboard(); currentSubpage = SettingsSubpage.MAIN }
                        .padding(vertical = 4.dp, horizontal = 2.dp)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = AppAccent,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = "Settings",
                        color = AppAccent,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
                TextButton(onClick = { dismissKeyboard(); onBack() }) {
                    Text("Done", color = AppTextSecondary, fontSize = 14.sp)
                }
            }
        }

        when (currentSubpage) {
            SettingsSubpage.MAIN -> {
                // Main Banner
                Column(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp))
                        .background(Brush.verticalGradient(listOf(Color(0xFF302A22), AppSurface)))
                        .padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text("VANTA AUDIOPHILE", color = AppAccent, fontSize = 10.sp, lineHeight = 14.sp, letterSpacing = 2.sp, fontWeight = FontWeight.Bold)
                    Text("Settings & System", color = AppText, style = VantaType.sectionTitle)
                    Text("Bit-perfect playback, studio streaming resolution, and sound tuning.", color = AppTextSecondary, fontSize = 13.sp, lineHeight = 18.sp)
                }

                if (onOpenAccount != null) {
                    SettingsCategoryCard(
                        title = "VANTA Account",
                        subtitle = "Library sync and listening history",
                        icon = Icons.Filled.Person,
                        onClick = { onOpenAccount.invoke() }
                    )
                }

                // 1. Audio & Sound Quality
                val qualityBadge = when (streamQuality) {
                    "24" -> "24-bit Hi-Res"
                    "16" -> "16-bit Lossless"
                    else -> "Spatial / Auto"
                }
                SettingsCategoryCard(
                    title = "Audio & Sound Quality",
                    subtitle = "24-bit Hi-Res Studio Master, Lossless CD, Equalizer, DSP, DAC Output",
                    icon = Icons.Filled.Tune,
                    badge = qualityBadge,
                    onClick = { currentSubpage = SettingsSubpage.AUDIO }
                )

                // 2. Playback & Crossfade
                SettingsCategoryCard(
                    title = "Playback & Crossfade",
                    subtitle = "Automix crossfade, silence transitions, Android Auto",
                    icon = Icons.Filled.PlayArrow,
                    badge = if (autoMixConfig.mode != AutoMixMode.OFF) "${autoMixConfig.transitionSeconds}s" else "Off",
                    onClick = { currentSubpage = SettingsSubpage.PLAYBACK }
                )

                // 3. Appearance & Visuals
                SettingsCategoryCard(
                    title = "Appearance & Visuals",
                    subtitle = "Aura visualizer, audio-reactive motion, animated artwork",
                    icon = Icons.Filled.AutoAwesome,
                    badge = auraMode.name.lowercase().replace('_', ' ').replaceFirstChar { it.uppercase() },
                    onClick = { currentSubpage = SettingsSubpage.APPEARANCE }
                )

                // 4. Connected Libraries
                SettingsCategoryCard(
                    title = "Connected Libraries",
                    subtitle = "Apple Music, Spotify, imported playlists",
                    icon = Icons.Filled.LibraryMusic,
                    onClick = { currentSubpage = SettingsSubpage.LIBRARIES }
                )

                // 5. Link Phone & TV
                SettingsCategoryCard(
                    title = "Link Phone & TV",
                    subtitle = if (pairCode.isNotBlank()) "Code: $pairCode · Push library & shared listening" else "Pair with TV screen",
                    icon = Icons.Filled.Tv,
                    badge = if (pairCode.isNotBlank()) "Linked" else null,
                    onClick = { currentSubpage = SettingsSubpage.SYNC_TV }
                )

                // 6. AI DJ Narration
                SettingsCategoryCard(
                    title = "AI DJ Narration",
                    subtitle = "Host speech frequency ($djFrequency) & commentary",
                    icon = Icons.Filled.Radio,
                    badge = djFrequency,
                    onClick = { currentSubpage = SettingsSubpage.AI_DJ }
                )

                // 7. Sources & Playback Engines (Calls onOpenAdvanced)
                SettingsCategoryCard(
                    title = "Sources & Playback Engines",
                    subtitle = "TorBox, Real-Debrid, metadata engines & community relays",
                    icon = Icons.Filled.Settings,
                    onClick = onOpenAdvanced
                )

                // 8. Support & Donations
                SettingsCategoryCard(
                    title = "Support & Donations",
                    subtitle = "Support on Ko-fi — help keep VANTA independent & growing",
                    icon = Icons.Filled.Favorite,
                    badge = "Donate",
                    onClick = { currentSubpage = SettingsSubpage.DONATION }
                )

                // 9. About & Diagnostics
                SettingsCategoryCard(
                    title = "About & Diagnostics",
                    subtitle = "Version ${com.audiophile.musicplayer.BuildConfig.VERSION_NAME}, audio pipeline metrics, problem logs",
                    icon = Icons.Filled.Info,
                    badge = if (labsUnlocked) "Labs" else null,
                    onClick = { currentSubpage = SettingsSubpage.ABOUT }
                )
            }

            SettingsSubpage.AUDIO -> {
                Text(
                    text = "Audio & Sound Quality",
                    style = VantaType.sectionTitle,
                    modifier = Modifier.padding(start = 4.dp, bottom = 4.dp)
                )

                // Prominent Streaming Quality Hero Card
                AudioQualityHeroCard(
                    currentQuality = streamQuality,
                    onClick = {
                        dismissKeyboard()
                        showQualityOptions = true
                    }
                )

                // Quick 1-tap quality options right here on the screen!
                Text(
                    "STREAMING RESOLUTION PREFERENCE",
                    color = AppAccent,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.5.sp,
                    modifier = Modifier.padding(start = 4.dp, top = 6.dp)
                )

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(18.dp))
                        .background(AppSurfaceRaised)
                        .padding(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    val qualityChoices = listOf(
                        Triple("atmos", "Dolby Atmos & Sony 360 Reality Audio", "#1 Priority: Dolby Atmos & Sony 360 bitstream · Falls back to 24-bit FLAC if unavailable"),
                        Triple("24", "24-bit Hi-Res Studio Master FLAC", "Max stereo fidelity · 24-bit / 96–192 kHz lossless · Bit-perfect studio sound"),
                        Triple("auto", "Smart Spatial + Lossless", "Balanced: Dolby Atmos bitstream & Sony 360 first, then 24-bit FLAC"),
                        Triple("16", "16-bit Lossless CD Quality", "Standard 16-bit / 44.1 kHz FLAC · Lower mobile data bandwidth")
                    )
                    qualityChoices.forEach { (value, optTitle, optDesc) ->
                        val isSelected = when (value) {
                            "atmos" -> streamQuality == "atmos"
                            "auto" -> streamQuality == "auto"
                            else -> streamQuality == value
                        }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .background(if (isSelected) AppAccent.copy(alpha = 0.14f) else Color.Transparent)
                                .clickable { selectStreamQuality(value) }
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = isSelected,
                                onClick = null,
                                colors = RadioButtonDefaults.colors(
                                    selectedColor = AppAccent,
                                    unselectedColor = AppTextMuted
                                )
                            )
                            Spacer(Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = optTitle,
                                    color = if (isSelected) AppAccent else AppText,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 14.sp
                                )
                                Spacer(Modifier.height(2.dp))
                                Text(
                                    text = optDesc,
                                    color = AppTextSecondary,
                                    fontSize = 11.sp,
                                    lineHeight = 15.sp
                                )
                            }
                        }
                    }
                }

                // Audiophile info note
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(AppSurfaceRaised.copy(alpha = 0.5f))
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text("Audiophile Pipeline", color = AppText, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                    Text("VANTA bypasses Android's native resampler when connected to USB DACs for bit-perfect output. Changes to resolution apply immediately to the next stream request.", color = AppTextMuted, fontSize = 11.sp, lineHeight = 16.sp)
                }

                // DSP & Tuning
                PremiumSettingsGroup(title = "DSP & Tuning") {
                    PremiumSettingsClickItem(
                        title = "Equalizer",
                        subtitle = "10-band Parametric EQ, Tube Preamp, Bass Cannon, Convolver",
                        onClick = onOpenEqualizer
                    )
                    PremiumSettingsClickItem(
                        title = "Sound Check (Volume Normalization)",
                        checked = equalizerConfig.loudnessNormalizationEnabled,
                        subtitle = if (equalizerConfig.loudnessNormalizationEnabled) {
                            "Active · ${"%.1f".format(equalizerConfig.replayGainDb)} dB stereo leveling; Dolby Atmos matched"
                        } else {
                            "Off · Stereo plays at master volume; Dolby Atmos retains wide dynamic range"
                        },
                        onClick = {
                            saveEqualizer(equalizerConfig.copy(
                                loudnessNormalizationEnabled = !equalizerConfig.loudnessNormalizationEnabled,
                                replayGainDb = if (equalizerConfig.replayGainDb == 0f) -7.5f else equalizerConfig.replayGainDb
                            ))
                        }
                    )
                    if (equalizerConfig.loudnessNormalizationEnabled) {
                        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                            Text(
                                "Stereo Leveling Target: ${"%.1f".format(equalizerConfig.replayGainDb)} dB (Bridges the gap to Dolby Atmos)",
                                color = AppTextSecondary,
                                fontSize = 12.sp
                            )
                            androidx.compose.material3.Slider(
                                value = equalizerConfig.replayGainDb,
                                onValueChange = {
                                    saveEqualizer(equalizerConfig.copy(replayGainDb = it))
                                },
                                valueRange = -18f..0f,
                                steps = 17,
                                colors = androidx.compose.material3.SliderDefaults.colors(
                                    thumbColor = AppAccent,
                                    activeTrackColor = AppAccent,
                                    inactiveTrackColor = AppAccent.copy(alpha = 0.2f)
                                )
                            )
                        }
                    }
                    PremiumSettingsClickItem(
                        title = "Headphone AutoEQ",
                        checked = equalizerConfig.autoEqEnabled,
                        subtitle = if (equalizerConfig.autoEqEnabled) {
                            "Correction active · ${if (equalizerConfig.autoEqProfileName.isNullOrBlank()) "no profile loaded" else "profile loaded"}"
                        } else {
                            "Off · correct your headphone frequency response"
                        },
                        onClick = {
                            val next = !equalizerConfig.autoEqEnabled
                            saveEqualizer(equalizerConfig.copy(
                                autoEqEnabled = next,
                                autoEqProfileName = equalizerConfig.autoEqProfileName
                            ))
                            if (next && equalizerConfig.autoEqProfileName == null) showAutoEqDialog = true
                        }
                    )
                    PremiumSettingsClickItem(
                        title = "Auto Headroom",
                        checked = equalizerConfig.autoHeadroomEnabled,
                        subtitle = if (equalizerConfig.autoHeadroomEnabled) {
                            "Prevents EQ gain from clipping"
                        } else {
                            "Off · your EQ boost may clip"
                        },
                        onClick = {
                            saveEqualizer(equalizerConfig.copy(
                                autoHeadroomEnabled = !equalizerConfig.autoHeadroomEnabled
                            ))
                        },
                        showDivider = false
                    )
                }

                // Spatial Audio
                PremiumSettingsGroup(title = "Spatial Audio") {
                    PremiumSettingsClickItem(
                        title = "Immersive Sound",
                        checked = equalizerConfig.spatialEnabled,
                        subtitle = if (equalizerConfig.spatialEnabled) {
                            "${equalizerConfig.preset.label} · best on headphones"
                        } else {
                            "Off · auto-safe on phone speaker"
                        },
                        onClick = { saveEqualizer(equalizerConfig.copy(
                            spatialEnabled = !equalizerConfig.spatialEnabled,
                            stereoWidenLevel = equalizerConfig.stereoWidenLevel.takeIf { it > 0f } ?: 0.35f,
                            eqBypassEnabled = false
                        )) },
                        showDivider = headTrackingAvailable
                    )
                    if (headTrackingAvailable) {
                        PremiumSettingsClickItem(
                            title = "Head Tracking",
                            checked = headTrackingEnabled,
                            subtitle = if (headTrackingEnabled) "Rotate spatial audio with your head" else "Off",
                            onClick = {
                                val next = !headTrackingEnabled
                                headTrackingEnabled = next
                                SpatialHeadTracking.setEnabled(context, next)
                                context.startService(PlaybackCommandAuth.createIntent(context, PlaybackService.ACTION_REFRESH_HEAD_TRACKING))
                            },
                            showDivider = false
                        )
                    }
                }

                // Hardware Output & DAC
                var floatOutputEnabled by remember {
                    mutableStateOf(PlaybackOutputPreferences(context).floatOutputEnabled())
                }
                PremiumSettingsGroup(title = "Hardware Output") {
                    PremiumSettingsClickItem(
                        title = "Float Output",
                        checked = floatOutputEnabled,
                        subtitle = if (floatOutputEnabled) {
                            "Hi-res stays 32-bit float up to the DAC write"
                        } else {
                            "Off · PCM output (matches Qobuz default)"
                        },
                        onClick = {
                            val next = !floatOutputEnabled
                            floatOutputEnabled = next
                            PlaybackOutputPreferences(context).setFloatOutputEnabled(next)
                            context.startService(PlaybackCommandAuth.createIntent(context, PlaybackService.ACTION_REFRESH_IMMERSIVE_AUDIO))
                        },
                        showDivider = true
                    )
                    PremiumSettingsClickItem(
                        title = "Output device",
                        subtitle = when {
                            OutputSwitchController.isUsbDacRoute(context) ->
                                "${OutputSwitchController.selectedDevice(context)?.label ?: "USB"} · DSP bypassed"
                            OutputSwitchController.isBuiltInSpeakerRoute(context) ->
                                "Phone speaker · Immersive auto-safe"
                            else -> OutputSwitchController.selectedDevice(context)?.label ?: "System default"
                        },
                        onClick = { dismissKeyboard(); showOutputSheet = true },
                        showDivider = false
                    )
                }

                // Samsung tip
                var showSamsungAtmosTip by remember {
                    mutableStateOf(SystemAudioProcessingHint.shouldShowSamsungAtmosTip(context))
                }
                if (showSamsungAtmosTip) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 4.dp, vertical = 6.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(AppSurfaceRaised.copy(alpha = 0.55f))
                            .padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text("Phone speaker sounding rough?", color = AppText, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                        Text("Samsung’s system Dolby Atmos can process audio again on top of VANTA. Turn it off under Sounds and vibration → Sound quality and effects.", color = AppTextMuted, fontSize = 12.sp, lineHeight = 17.sp)
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            TextButton(onClick = { SystemAudioProcessingHint.openSystemSoundSettings(context) }) { Text("Open sound settings", color = AppAccent, fontSize = 13.sp) }
                            TextButton(onClick = { SystemAudioProcessingHint.dismissSamsungAtmosTip(context); showSamsungAtmosTip = false }) { Text("Got it", color = AppTextMuted, fontSize = 13.sp) }
                        }
                    }
                }
            }

            SettingsSubpage.PLAYBACK -> {
                Text(
                    text = "Playback & Crossfade",
                    style = VantaType.sectionTitle,
                    modifier = Modifier.padding(start = 4.dp, bottom = 4.dp)
                )

                PremiumSettingsGroup(title = "Crossfade & Transitions") {
                    PremiumSettingsClickItem(
                        title = "Crossfade",
                        subtitle = autoMixConfig.mode.label,
                        onClick = { saveAutoMix(autoMixConfig.copy(mode = autoMixConfig.mode.next())) },
                        showDivider = autoMixConfig.mode != AutoMixMode.OFF
                    )
                    if (autoMixConfig.mode != AutoMixMode.OFF) {
                        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                            Text(
                                "Transition ${autoMixConfig.transitionSeconds}s",
                                color = AppTextSecondary,
                                fontSize = 12.sp
                            )
                            androidx.compose.material3.Slider(
                                value = autoMixConfig.transitionSeconds.toFloat(),
                                onValueChange = {
                                    saveAutoMix(autoMixConfig.copy(transitionSeconds = it.toInt().coerceIn(2, 12)))
                                },
                                valueRange = 2f..12f,
                                steps = 9,
                                colors = androidx.compose.material3.SliderDefaults.colors(
                                    thumbColor = AppAccent,
                                    activeTrackColor = AppAccent,
                                    inactiveTrackColor = AppAccent.copy(alpha = 0.2f)
                                )
                            )
                        }
                    }
                }

                // Android Auto
                AndroidAutoSettingsCard(context = context)
            }

            SettingsSubpage.APPEARANCE -> {
                Text(
                    text = "Appearance & Visuals",
                    style = VantaType.sectionTitle,
                    modifier = Modifier.padding(start = 4.dp, bottom = 4.dp)
                )

                PremiumSettingsGroup(title = "Aura Visualizer") {
                    PremiumSettingsClickItem(
                        title = "Aura Mode",
                        subtitle = auraMode.name.lowercase().replace('_', ' ').replaceFirstChar { it.uppercase() },
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
                        title = "Audio-Reactive Aura",
                        checked = auraAudioReactive,
                        subtitle = if (auraAudioReactive) "Visuals move with the music" else "Off",
                        onClick = { onAuraAudioReactiveChange(!auraAudioReactive) }
                    )
                    PremiumSettingsClickItem(
                        title = "Reduce Motion in Car",
                        checked = auraReduceMotionCar,
                        subtitle = if (auraReduceMotionCar) "Calmer visuals while driving" else "Off",
                        onClick = { onAuraReduceMotionCarChange(!auraReduceMotionCar) }
                    )
                    PremiumSettingsClickItem(
                        title = "Animated Artwork",
                        checked = animatedArtworkEnabled,
                        subtitle = if (animatedArtworkEnabled) "On" else "Off",
                        onClick = { onAnimatedArtworkEnabledChange(!animatedArtworkEnabled) },
                        showDivider = false
                    )
                }
            }

            SettingsSubpage.SYNC_TV -> {
                Text(
                    text = "Link Phone & TV",
                    style = VantaType.sectionTitle,
                    modifier = Modifier.padding(start = 4.dp, bottom = 4.dp)
                )

                PremiumSettingsGroup(title = "Enter Code from TV") {
                    Text(
                        "On your TV, open VANTA → Settings → Link Phone & TV to see your TV's 6-character code. Enter it here to sync your library and liked tracks.",
                        color = AppTextSecondary,
                        fontSize = 13.sp,
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp)
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = enteredTvCode,
                        onValueChange = { enteredTvCode = it.uppercase().take(8) },
                        label = { Text("TV Link Code", color = AppTextSecondary) },
                        placeholder = { Text("e.g. AB12CD", color = AppTextMuted) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = AppAccent,
                            unfocusedBorderColor = AppOutline,
                            focusedTextColor = AppText,
                            unfocusedTextColor = AppText,
                            cursorColor = AppAccent
                        )
                    )
                    Spacer(Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = {
                                if (enteredTvCode.isNotBlank()) {
                                    deviceSync.setPairCode(enteredTvCode)
                                }
                                syncScope.launch { deviceSync.pushFromThisDevice() }
                            },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = AppAccent, contentColor = AppBackground)
                        ) {
                            Text("Push to TV", fontWeight = FontWeight.SemiBold)
                        }
                        androidx.compose.material3.OutlinedButton(
                            onClick = {
                                if (enteredTvCode.isNotBlank()) {
                                    deviceSync.setPairCode(enteredTvCode)
                                }
                                syncScope.launch { deviceSync.pullToThisDevice() }
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Pull from TV", color = AppAccent)
                        }
                    }
                    if (syncStatus != null) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = syncStatus.orEmpty(),
                            color = AppAccent,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.padding(horizontal = 4.dp)
                        )
                    }
                }

                Spacer(Modifier.height(16.dp))

                PremiumSettingsGroup(title = "This Phone's Code") {
                    Text(
                        "Alternatively, enter this phone's code on your TV screen:",
                        color = AppTextSecondary,
                        fontSize = 13.sp,
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp)
                    )
                    Text(
                        if (pairCode.isBlank()) "————" else pairCode,
                        color = AppAccent,
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 4.sp,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                    PremiumSettingsClickItem(
                        title = "Generate new code",
                        subtitle = "Resets this phone's code and disconnects previous links",
                        onClick = {
                            deviceSync.setPairCode("")
                            deviceSync.ensurePairCode()
                        },
                        showDivider = false
                    )
                }
            }

            SettingsSubpage.LIBRARIES -> {
                Text(
                    text = "Connected Libraries",
                    style = VantaType.sectionTitle,
                    modifier = Modifier.padding(start = 4.dp, bottom = 4.dp)
                )

                ConnectedLibrariesSettingsGroup(
                    context = context,
                    tokenStore = tokenStore,
                    connectedLibraryManager = connectedLibraryManager,
                    onOpenImports = onOpenImports,
                    onLibraryChanged = onLibraryChanged
                )
            }

            SettingsSubpage.AI_DJ -> {
                Text(
                    text = "AI DJ Narration",
                    style = VantaType.sectionTitle,
                    modifier = Modifier.padding(start = 4.dp, bottom = 4.dp)
                )

                PremiumSettingsGroup(title = "Host Narration") {
                    PremiumSettingsClickItem(
                        title = "DJ Narration Frequency",
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
                Column(modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)) {
                    Text(
                        "The AI DJ generates context-aware speech between songs highlighting release stories, artist trivia, and music genres.",
                        color = AppTextMuted,
                        fontSize = 12.sp,
                        lineHeight = 17.sp
                    )
                }
            }

            SettingsSubpage.DONATION -> {
                Text(
                    text = "Support & Donations",
                    style = VantaType.sectionTitle,
                    modifier = Modifier.padding(start = 4.dp, bottom = 4.dp)
                )

                VantaCard {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .background(AppAccent.copy(alpha = 0.15f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Favorite,
                                    contentDescription = null,
                                    tint = AppAccent,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Spacer(Modifier.width(12.dp))
                            Column {
                                Text("Support VANTA Development", color = AppText, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                                Text("Independent · Ad-Free · Audiophile First", color = AppAccent, fontSize = 12.sp)
                            }
                        }
                        Text(
                            "VANTA is crafted for pure, uncompromised listening — bit-perfect DAC playback, studio masters, and immersive spatial audio. Your support directly helps fund gateway servers, spatial streaming relays, and continuous app updates.",
                            color = AppTextSecondary,
                            fontSize = 13.sp,
                            lineHeight = 18.sp
                        )
                    }
                }

                Spacer(Modifier.height(16.dp))

                PremiumSettingsGroup(title = "Contribute Online") {
                    PremiumSettingsClickItem(
                        title = "Support on Ko-fi",
                        subtitle = "ko-fi.com/drewk312 (Safe & Private)",
                        onClick = {
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://ko-fi.com/drewk312"))
                            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            try {
                                context.startActivity(intent)
                            } catch (_: android.content.ActivityNotFoundException) {
                                android.widget.Toast.makeText(context, "No web browser found", android.widget.Toast.LENGTH_SHORT).show()
                            }
                        },
                        showDivider = false
                    )
                }
            }

            SettingsSubpage.ABOUT -> {
                Text(
                    text = "About & Diagnostics",
                    style = VantaType.sectionTitle,
                    modifier = Modifier.padding(start = 4.dp, bottom = 4.dp)
                )

                AboutVantaSection(
                    labsUnlocked = labsUnlocked,
                    onLabsUnlocked = {
                        labsUnlocked = true
                        sharedPrefs.edit { putBoolean("vanta_labs_unlocked", true) }
                    }
                )

                if (labsUnlocked) {
                    PremiumSettingsGroup(title = "Help & Diagnostics") {
                        PremiumSettingsClickItem(
                            title = "Problem log",
                            subtitle = "Crashes and errors for troubleshooting",
                            onClick = { showDiagnostics = true },
                            showDivider = false
                        )
                    }

                    // BYOA Advanced connections
                    val byoaStore = remember(context) { (context.applicationContext as com.audiophile.musicplayer.AudiophileApp).appContainer.byoaCredentialStore }
                    var qobuzToken by remember { mutableStateOf(byoaStore.getQobuzUserToken().orEmpty()) }
                    var deezerArl by remember { mutableStateOf(byoaStore.getDeezerArl().orEmpty()) }
                    var tidalJson by remember { mutableStateOf(byoaStore.getTidalOauthJson().orEmpty()) }
                    var communitySessionJson by remember { mutableStateOf(byoaStore.getCommunityRelaySessionJson()) }
                    var customGateway by remember { mutableStateOf(byoaStore.getCustomGatewayUrl().orEmpty()) }
                    var expanded by remember { mutableStateOf(false) }
                    var status by remember { mutableStateOf(byoaStore.getDisplaySummary()) }
                    fun refreshStatus() { status = byoaStore.getDisplaySummary() }

                    PremiumSettingsGroup(title = "Advanced connections") {
                        PremiumSettingsClickItem(
                            title = "Manage connection details",
                            subtitle = status,
                            onClick = { expanded = !expanded },
                            showDivider = expanded
                        )
                        if (expanded) {
                            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                Text("Manage accounts saved on this device. Saved credentials are shared with your selected streaming server when needed.", color = AppTextMuted, fontSize = 11.sp, lineHeight = 15.sp)
                                Text("Import a connection exported by your music service. Some services occasionally require reconnection.", color = AppTextSecondary, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                                VantaTextField(value = communitySessionJson, onValueChange = { communitySessionJson = it }, label = "Community session JSON", isPassword = true)
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Box(modifier = Modifier.weight(1f).clip(RoundedCornerShape(10.dp)).clickable {
                                        val saved = byoaStore.saveCommunityRelaySessionJson(communitySessionJson)
                                        status = if (saved) byoaStore.getDisplaySummary() else "This connection could not be imported. Check the exported details and try again."
                                    }.background(AppAccent.copy(alpha=0.2f)).padding(vertical=8.dp), contentAlignment=Alignment.Center) { Text("Save Session", color=AppAccent, fontWeight=FontWeight.Bold, fontSize=12.sp) }
                                    Box(modifier = Modifier.weight(1f).clip(RoundedCornerShape(10.dp)).clickable {
                                        byoaStore.clearCommunityRelaySession(); communitySessionJson=""; refreshStatus()
                                    }.background(AppSurfaceRaised).border(0.5.dp, AppOutline, RoundedCornerShape(10.dp)).padding(vertical=8.dp), contentAlignment=Alignment.Center) { Text("Clear", color=AppTextSecondary, fontSize=12.sp) }
                                }
                                Text("Qobuz user_auth_token (paste token from your Qobuz Studio login, not password)", color = AppTextSecondary, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                                VantaTextField(value = qobuzToken, onValueChange = { qobuzToken = it }, label = "Qobuz user_auth_token", isPassword = true)
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Box(modifier = Modifier.weight(1f).clip(RoundedCornerShape(10.dp)).clickable {
                                        byoaStore.saveQobuzUserToken(qobuzToken); refreshStatus()
                                    }.background(AppAccent.copy(alpha=0.2f)).padding(vertical=8.dp), contentAlignment=Alignment.Center) { Text("Save Qobuz", color=AppAccent, fontWeight=FontWeight.Bold, fontSize=12.sp) }
                                    Box(modifier = Modifier.weight(1f).clip(RoundedCornerShape(10.dp)).clickable {
                                        byoaStore.clearQobuz(); qobuzToken=""; refreshStatus()
                                    }.background(AppSurfaceRaised).border(0.5.dp, AppOutline, RoundedCornerShape(10.dp)).padding(vertical=8.dp), contentAlignment=Alignment.Center) { Text("Clear", color=AppTextSecondary, fontSize=12.sp) }
                                }
                                Text("Deezer ARL cookie (from deezer.com → DevTools → Application → Cookies → arl)", color = AppTextSecondary, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                                VantaTextField(value = deezerArl, onValueChange = { deezerArl = it }, label = "Deezer ARL", isPassword = true)
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Box(modifier = Modifier.weight(1f).clip(RoundedCornerShape(10.dp)).clickable {
                                        byoaStore.saveDeezerArl(deezerArl); refreshStatus()
                                    }.background(AppAccent.copy(alpha=0.2f)).padding(vertical=8.dp), contentAlignment=Alignment.Center) { Text("Save ARL", color=AppAccent, fontWeight=FontWeight.Bold, fontSize=12.sp) }
                                    Box(modifier = Modifier.weight(1f).clip(RoundedCornerShape(10.dp)).clickable {
                                        byoaStore.clearDeezer(); deezerArl=""; refreshStatus()
                                    }.background(AppSurfaceRaised).border(0.5.dp, AppOutline, RoundedCornerShape(10.dp)).padding(vertical=8.dp), contentAlignment=Alignment.Center) { Text("Clear", color=AppTextSecondary, fontSize=12.sp) }
                                }
                                Text("Tidal OAuth JSON (access_token + refresh_token). VANTA refreshes the access token automatically — no CAPTCHA.", color = AppTextSecondary, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                                VantaTextField(value = tidalJson, onValueChange = { tidalJson = it }, label = "Tidal OAuth JSON", isPassword = true)
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Box(modifier = Modifier.weight(1f).clip(RoundedCornerShape(10.dp)).clickable {
                                        byoaStore.saveTidalOauthJson(tidalJson); refreshStatus()
                                    }.background(AppAccent.copy(alpha=0.2f)).padding(vertical=8.dp), contentAlignment=Alignment.Center) { Text("Save Tidal", color=AppAccent, fontWeight=FontWeight.Bold, fontSize=12.sp) }
                                    Box(modifier = Modifier.weight(1f).clip(RoundedCornerShape(10.dp)).clickable {
                                        byoaStore.clearTidal(); tidalJson=""; refreshStatus()
                                    }.background(AppSurfaceRaised).border(0.5.dp, AppOutline, RoundedCornerShape(10.dp)).padding(vertical=8.dp), contentAlignment=Alignment.Center) { Text("Clear", color=AppTextSecondary, fontSize=12.sp) }
                                }
                                Text("Custom streaming server", color = AppTextSecondary, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                                VantaTextField(value = customGateway, onValueChange = { customGateway = it }, label = "https://your-gateway.workers.dev")
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Box(modifier = Modifier.weight(1f).clip(RoundedCornerShape(10.dp)).clickable {
                                        byoaStore.saveCustomGatewayUrl(customGateway); refreshStatus()
                                    }.background(AppAccent.copy(alpha=0.2f)).padding(vertical=8.dp), contentAlignment=Alignment.Center) { Text("Save Gateway", color=AppAccent, fontWeight=FontWeight.Bold, fontSize=12.sp) }
                                    Box(modifier = Modifier.weight(1f).clip(RoundedCornerShape(10.dp)).clickable {
                                        byoaStore.clearCustomGateway(); customGateway=""; refreshStatus()
                                    }.background(AppSurfaceRaised).border(0.5.dp, AppOutline, RoundedCornerShape(10.dp)).padding(vertical=8.dp), contentAlignment=Alignment.Center) { Text("Use Default", color=AppTextSecondary, fontSize=12.sp) }
                                }
                                Text("Use a custom server only if you manage your own streaming connection.", color = AppTextMuted, fontSize = 11.sp, lineHeight=14.sp)
                            }
                        }
                    }
                }
            }
        }

        if (showQualityOptions) AudioQualityPreferenceDialog(
            selected = streamQuality,
            onSelect = { next ->
                selectStreamQuality(next)
                showQualityOptions = false
            },
            onDismiss = { showQualityOptions = false }
        )

        if (showAutoEqDialog) {
            AutoEqProfileDialog(
                initial = equalizerConfig.autoEqProfileName.orEmpty(),
                onDismiss = { showAutoEqDialog = false },
                onApply = { content ->
                    if (AutoEqProfileValidator.isValidDdcProfile(content)) {
                        saveEqualizer(equalizerConfig.copy(
                            autoEqEnabled = true,
                            autoEqProfileName = content
                        ))
                    }
                    showAutoEqDialog = false
                }
            )
        }

        if (showOutputSheet) {
            @OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
            androidx.compose.material3.ModalBottomSheet(
                onDismissRequest = { showOutputSheet = false },
                containerColor = AppSurface
            ) {
                OutputDeviceSheet(
                    castingManager = com.audiophile.musicplayer.playback.UpnpCastingHolder.manager,
                    streamUrl = null,
                    onDismiss = { showOutputSheet = false }
                )
            }
        }
    }

    if (showDiagnostics) {
        VantaDiagnosticsSheet(onDismiss = { showDiagnostics = false })
    }
}

@Composable
private fun SettingsCategoryCard(
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    badge: String? = null,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(AppSurfaceRaised)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(42.dp)
                .clip(CircleShape)
                .background(AppAccent.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = AppAccent,
                modifier = Modifier.size(22.dp)
            )
        }
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = AppText,
                fontWeight = FontWeight.SemiBold,
                fontSize = 15.sp
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = subtitle,
                color = AppTextSecondary,
                fontSize = 12.sp,
                lineHeight = 16.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
        if (!badge.isNullOrBlank()) {
            Spacer(Modifier.width(8.dp))
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(AppSurface)
                    .border(0.5.dp, AppOutline, RoundedCornerShape(8.dp))
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Text(
                    text = badge,
                    color = AppAccent,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
        Spacer(Modifier.width(8.dp))
        Icon(
            imageVector = Icons.Filled.ChevronRight,
            contentDescription = null,
            tint = AppTextMuted,
            modifier = Modifier.size(20.dp)
        )
    }
}

@Composable
private fun AudioQualityHeroCard(
    currentQuality: String,
    onClick: () -> Unit
) {
    val title = when (currentQuality) {
        "24" -> "24-bit Studio Master FLAC"
        "16" -> "16-bit Lossless CD Quality"
        "auto" -> "Smart Immersive + Lossless"
        "atmos" -> "Dolby Atmos Spatial Audio"
        "360" -> "Sony 360 Reality Audio"
        else -> "Hi-Res Lossless (24-bit)"
    }
    val subtitle = when (currentQuality) {
        "24" -> "Max Quality · 24-bit up to 192.0 kHz · Bit-Perfect Studio Sound"
        "16" -> "Standard Lossless CD Quality · 44.1 kHz · Data-Saving"
        "auto" -> "Atmos & 360 if available on track, otherwise 24-bit FLAC"
        "atmos" -> "E-AC-3 JOC / TrueHD Immersive Spatial Bitstream"
        "360" -> "MPEG-H 3D Binaural Spatial Audio"
        else -> "Hi-Res Lossless FLAC"
    }
    val badgeLabel = when (currentQuality) {
        "24" -> "24-BIT MAX"
        "16" -> "16-BIT CD"
        else -> "SPATIAL"
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(Brush.verticalGradient(listOf(Color(0xFF2E2417), AppSurfaceRaised)))
            .border(1.dp, AppAccent.copy(alpha = 0.35f), RoundedCornerShape(20.dp))
            .clickable(onClick = onClick)
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "STREAMING RESOLUTION",
                color = AppAccent,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.5.sp
            )
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(AppAccent.copy(alpha = 0.2f))
                    .padding(horizontal = 8.dp, vertical = 3.dp)
            ) {
                Text(
                    text = badgeLabel,
                    color = AppAccent,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.ExtraBold
                )
            }
        }
        Text(
            text = title,
            color = AppText,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = subtitle,
            color = AppTextSecondary,
            fontSize = 13.sp,
            lineHeight = 18.sp
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Tap to switch resolution options",
                color = AppAccent,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold
            )
            Icon(
                imageVector = Icons.Filled.ChevronRight,
                contentDescription = null,
                tint = AppAccent,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

@Composable
private fun ConnectedLibrariesSettingsGroup(
    context: Context,
    tokenStore: ConnectedLibraryTokenStore?,
    connectedLibraryManager: ConnectedLibraryManager?,
    onOpenImports: () -> Unit,
    onLibraryChanged: () -> Unit
) {
    val prefs = remember(context) { context.getSharedPreferences("vanta_connected_libraries", Context.MODE_PRIVATE) }
    val coroutineScope = rememberCoroutineScope()
    // Keys must match ConnectedLibraryManager.isSyncLikesEnabled().
    var appleSyncLikes by remember { mutableStateOf(prefs.getBoolean("apple_music_sync_likes", false)) }
    var spotifySyncLikes by remember { mutableStateOf(prefs.getBoolean("spotify_sync_likes", false)) }
    var appleLastImport by remember { mutableStateOf(prefs.getString("apple_music_last_import", "Never") ?: "Never") }
    var spotifyLastImport by remember { mutableStateOf(prefs.getString("spotify_last_import", "Never") ?: "Never") }
    var appleAutoRefresh by remember { mutableStateOf(prefs.getString("apple_music_auto_refresh", "off") ?: "off") }
    var spotifyAutoRefresh by remember { mutableStateOf(prefs.getString("spotify_auto_refresh", "off") ?: "off") }

    fun saveBoolean(key: String, value: Boolean) {
        prefs.edit {
                putBoolean(key, value)
            }
    }

    fun formatNow(): String = android.text.format.DateFormat.format("MMM d, h:mm a", System.currentTimeMillis()).toString()

    fun recordImport(provider: ConnectedLibraryProvider) {
        val now = formatNow()
        when (provider) {
            ConnectedLibraryProvider.APPLE_MUSIC -> appleLastImport = now
            ConnectedLibraryProvider.SPOTIFY -> spotifyLastImport = now
        }
        prefs.edit {
            putString("${provider.name.lowercase()}_last_import", now)
            putLong("${provider.name.lowercase()}_last_import_ms", System.currentTimeMillis())
        }
    }

    fun saveAutoRefresh(provider: ConnectedLibraryProvider, value: String) {
        val key = "${provider.name.lowercase()}_auto_refresh"
        when (provider) {
            ConnectedLibraryProvider.APPLE_MUSIC -> appleAutoRefresh = value
            ConnectedLibraryProvider.SPOTIFY -> spotifyAutoRefresh = value
        }
        connectedLibraryManager?.setAutoRefreshInterval(provider, value)
        prefs.edit { putString(key, value) }
    }

    // Real connection state is derived from encrypted token presence.
    val appleHasDevToken = remember(tokenStore) { tokenStore?.accessToken(ConnectedLibraryProvider.APPLE_MUSIC)?.isNotBlank() == true }
    val appleHasUserToken = remember(tokenStore) { tokenStore?.musicUserToken(ConnectedLibraryProvider.APPLE_MUSIC)?.isNotBlank() == true }
    val spotifyHasToken = remember(tokenStore) { tokenStore?.accessToken(ConnectedLibraryProvider.SPOTIFY)?.isNotBlank() == true }

    var appleConnected by remember { mutableStateOf(appleHasDevToken && appleHasUserToken) }
    var spotifyConnected by remember { mutableStateOf(spotifyHasToken) }
    var appleStatus by remember { mutableStateOf(statusText(appleConnected, "Apple Music")) }
    var spotifyStatus by remember { mutableStateOf(statusText(spotifyConnected, "Spotify")) }
    var showAppleDialog by remember { mutableStateOf(false) }
    var showSpotifyDialog by remember { mutableStateOf(false) }

    if (showAppleDialog) {
        AppleMusicTokenDialog(
            onDismiss = { showAppleDialog = false },
            onSave = { devToken, userToken ->
                val dev = devToken.trim()
                val user = userToken.trim()
                val stored = tokenStore?.storeTokens(
                    ConnectedLibraryProvider.APPLE_MUSIC,
                    accessToken = dev,
                    refreshToken = null,
                    musicUserToken = user
                )
                appleConnected = stored == true && dev.isNotBlank() && user.isNotBlank()
                appleStatus = if (stored == true) statusText(appleConnected, "Apple Music") else "Secure token storage is unavailable."
                showAppleDialog = false
            }
        )
    }

    if (showSpotifyDialog) {
        SpotifyTokenDialog(
            onDismiss = { showSpotifyDialog = false },
            onSave = { accessToken ->
                val token = accessToken.trim()
                val stored = tokenStore?.storeTokens(
                    ConnectedLibraryProvider.SPOTIFY,
                    accessToken = token,
                    refreshToken = null
                )
                spotifyConnected = stored == true && token.isNotBlank()
                spotifyStatus = if (stored == true) statusText(spotifyConnected, "Spotify") else "Secure token storage is unavailable."
                showSpotifyDialog = false
            }
        )
    }

    fun onConnectToggle(provider: ConnectedLibraryProvider) {
        if (tokenStore == null) {
            val message = "Secure token storage is unavailable on this device."
            when (provider) {
                ConnectedLibraryProvider.APPLE_MUSIC -> appleStatus = message
                ConnectedLibraryProvider.SPOTIFY -> spotifyStatus = message
            }
            return
        }
        when (provider) {
            ConnectedLibraryProvider.APPLE_MUSIC -> {
                if (appleConnected) {
                    connectedLibraryManager?.disconnect(ConnectedLibraryProvider.APPLE_MUSIC)
                        ?: tokenStore.clear(ConnectedLibraryProvider.APPLE_MUSIC)
                    appleConnected = false
                    appleStatus = statusText(false, "Apple Music")
                } else {
                    showAppleDialog = true
                }
            }
            ConnectedLibraryProvider.SPOTIFY -> {
                if (spotifyConnected) {
                    connectedLibraryManager?.disconnect(ConnectedLibraryProvider.SPOTIFY)
                        ?: tokenStore.clear(ConnectedLibraryProvider.SPOTIFY)
                    spotifyConnected = false
                    spotifyStatus = statusText(false, "Spotify")
                } else {
                    showSpotifyDialog = true
                }
            }
        }
    }

    fun onImport(provider: ConnectedLibraryProvider) {
        if (connectedLibraryManager == null || tokenStore == null) {
            Log.w("VANTA_CONNECTOR_IMPORT_ERROR", "provider=$provider reason=secure_store_unavailable")
            val message = "Secure storage unavailable. Cannot import."
            when (provider) {
                ConnectedLibraryProvider.APPLE_MUSIC -> appleStatus = message
                ConnectedLibraryProvider.SPOTIFY -> spotifyStatus = message
            }
            return
        }
        val hasToken = when (provider) {
            ConnectedLibraryProvider.APPLE_MUSIC -> appleConnected
            ConnectedLibraryProvider.SPOTIFY -> spotifyConnected
        }
        if (!hasToken) {
            Log.w("VANTA_CONNECTOR_IMPORT_ERROR", "provider=$provider reason=not_connected")
            val message = "Connect ${provider.displayName()} before importing."
            when (provider) {
                ConnectedLibraryProvider.APPLE_MUSIC -> appleStatus = message
                ConnectedLibraryProvider.SPOTIFY -> spotifyStatus = message
            }
            return
        }
        coroutineScope.launch {
            val statusPrefix = when (provider) {
                ConnectedLibraryProvider.APPLE_MUSIC -> appleStatus
                ConnectedLibraryProvider.SPOTIFY -> spotifyStatus
            }
            val runningStatus = "Importing ${provider.displayName()}..."
            when (provider) {
                ConnectedLibraryProvider.APPLE_MUSIC -> appleStatus = runningStatus
                ConnectedLibraryProvider.SPOTIFY -> spotifyStatus = runningStatus
            }
            val result = runCatching { connectedLibraryManager.importLibrary(provider) }
            result.onSuccess { importResult: ConnectedLibraryImportResult ->
                val summary = importResult.summary
                val succeeded = summary.errors.isEmpty()
                val finalStatus = if (succeeded) "Imported ${summary.tracksImported} tracks, ${summary.playlistsImported} playlists"
                    else "Sync did not finish. Reconnect your account or try again."
                when (provider) {
                    ConnectedLibraryProvider.APPLE_MUSIC -> appleStatus = finalStatus
                    ConnectedLibraryProvider.SPOTIFY -> spotifyStatus = finalStatus
                }
                if (succeeded) {
                    recordImport(provider)
                    onLibraryChanged()
                }
                Log.i(
                    "VANTA_CONNECTOR_IMPORT",
                    "provider=$provider tracks=${summary.tracksImported} playlists=${summary.playlistsImported} matched=${summary.matchedToVanta} errors=${summary.errors}"
                )
            }.onFailure { error ->
                Log.e("VANTA_CONNECTOR_IMPORT_ERROR", "provider=$provider reason=${error.message}", error)
                val errorStatus = "Import failed: ${error.message ?: "unknown"}"
                when (provider) {
                    ConnectedLibraryProvider.APPLE_MUSIC -> appleStatus = errorStatus
                    ConnectedLibraryProvider.SPOTIFY -> spotifyStatus = errorStatus
                }
            }
        }
    }

    PremiumSettingsGroup(title = "Connected Libraries") {
        if (tokenStore == null) {
            Text(
                text = "Connected Libraries require encrypted storage, which is unavailable on this device.",
                color = AppWarning,
                fontSize = 13.sp,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
            )
        } else {
            ConnectedLibraryCard(
                title = "Apple Music Library",
                connected = appleConnected,
                status = appleStatus,
                lastImport = appleLastImport,
                syncLikes = appleSyncLikes,
                autoRefreshInterval = appleAutoRefresh,
                onConnectToggle = { onConnectToggle(ConnectedLibraryProvider.APPLE_MUSIC) },
                onImport = { onImport(ConnectedLibraryProvider.APPLE_MUSIC) },
                onSyncLikesChange = {
                    appleSyncLikes = it
                    saveBoolean("apple_music_sync_likes", it)
                },
                onAutoRefreshChange = { saveAutoRefresh(ConnectedLibraryProvider.APPLE_MUSIC, it) },
                onDeleteImportedData = {
                    appleLastImport = "Never"
                    prefs.edit { remove("apple_music_last_import") }
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
                autoRefreshInterval = spotifyAutoRefresh,
                onConnectToggle = { onConnectToggle(ConnectedLibraryProvider.SPOTIFY) },
                onImport = { onImport(ConnectedLibraryProvider.SPOTIFY) },
                onSyncLikesChange = {
                    spotifySyncLikes = it
                    saveBoolean("spotify_sync_likes", it)
                },
                onAutoRefreshChange = { saveAutoRefresh(ConnectedLibraryProvider.SPOTIFY, it) },
                onDeleteImportedData = {
                    spotifyLastImport = "Never"
                    prefs.edit { remove("spotify_last_import") }
                }
            )
        }
        PremiumSettingsClickItem(
            title = "Import without connecting an account",
            subtitle = "Spotify export, Apple library, or a CSV/text playlist",
            onClick = onOpenImports
        )
        Text("Pandora: automatic account sync needs Pandora partner access. You can import an exported song list here.",
            color = AppTextSecondary, fontSize = 12.sp, modifier = Modifier.padding(16.dp))
        Text(
            text = "Connect only the accounts you choose. VANTA saves library and playlist copies, then finds playable matches. Service subscriptions and protected audio remain with each service.",
            color = AppTextMuted, fontSize = 11.sp, lineHeight = 15.sp,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
        )
    }
}

private fun ConnectedLibraryProvider.displayName(): String = when (this) {
    ConnectedLibraryProvider.APPLE_MUSIC -> "Apple Music"
    ConnectedLibraryProvider.SPOTIFY -> "Spotify"
}

private fun statusText(connected: Boolean, providerName: String): String =
    if (connected) "Active (token stored)" else "$providerName auth setup required"

@Composable
private fun AutoEqProfileDialog(
    initial: String,
    onDismiss: () -> Unit,
    onApply: (String) -> Unit
) {
    var content by remember { mutableStateOf(initial) }
    val valid = AutoEqProfileValidator.isValidDdcProfile(content)

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF1A1A1A),
        title = { Text("Headphone AutoEQ Profile", color = AppText, fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    "Paste a JamesDSP DDC headphone-correction profile (SR_44100 / SR_48000 biquad sections). " +
                        "It is applied by the native DSP and stored only on this device.",
                    color = AppTextSecondary,
                    fontSize = 13.sp,
                    lineHeight = 18.sp
                )
                VantaTextField(
                    value = content,
                    onValueChange = { content = it },
                    label = "DDC profile string",
                )
                Text(
                    if (content.isBlank()) {
                        "Paste your profile to validate it"
                    } else if (valid) {
                        "Valid: matches the native DDC biquad format"
                    } else {
                        "Invalid: needs both SR_44100 and SR_48000 sections with 5-coefficient biquad groups"
                    },
                    color = if (valid) AppAccent else AppTextMuted,
                    fontSize = 12.sp,
                    lineHeight = 16.sp
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onApply(content) },
                enabled = valid
            ) {
                Text("Apply", color = if (valid) AppAccent else AppTextMuted)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = AppTextMuted)
            }
        }
    )
}

@Composable
private fun AppleMusicTokenDialog(
    onDismiss: () -> Unit,
    onSave: (devToken: String, userToken: String) -> Unit
) {
    var devToken by remember { mutableStateOf("") }
    var userToken by remember { mutableStateOf("") }
    val canSave = devToken.isNotBlank() && userToken.isNotBlank()

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF1A1A1A),
        title = { Text("Connect Apple Music", color = AppText, fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    "Paste your Apple Music developer token and Music User Token. Tokens stay encrypted on this device.",
                    color = AppTextSecondary,
                    fontSize = 13.sp,
                    lineHeight = 18.sp
                )
                VantaTextField(
                    value = devToken,
                    onValueChange = { devToken = it },
                    label = "Developer Token"
                )
                VantaTextField(
                    value = userToken,
                    onValueChange = { userToken = it },
                    label = "Music User Token"
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(devToken, userToken) },
                enabled = canSave
            ) {
                Text("Save", color = if (canSave) AppAccent else AppTextMuted)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = AppTextMuted)
            }
        }
    )
}

@Composable
private fun SpotifyTokenDialog(
    onDismiss: () -> Unit,
    onSave: (accessToken: String) -> Unit
) {
    var accessToken by remember { mutableStateOf("") }
    val canSave = accessToken.isNotBlank()

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF1A1A1A),
        title = { Text("Connect Spotify", color = AppText, fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    "Paste a Spotify access token. VANTA uses it to read your library metadata only. Tokens stay encrypted on this device.",
                    color = AppTextSecondary,
                    fontSize = 13.sp,
                    lineHeight = 18.sp
                )
                VantaTextField(
                    value = accessToken,
                    onValueChange = { accessToken = it },
                    label = "Access Token",
                    isPassword = true
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(accessToken) },
                enabled = canSave
            ) {
                Text("Save", color = if (canSave) AppAccent else AppTextMuted)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = AppTextMuted)
            }
        }
    )
}

@Composable
private fun ConnectedLibraryCard(
    title: String,
    connected: Boolean,
    status: String,
    lastImport: String,
    syncLikes: Boolean,
    autoRefreshInterval: String,
    onConnectToggle: () -> Unit,
    onImport: () -> Unit,
    onSyncLikesChange: (Boolean) -> Unit,
    onAutoRefreshChange: (String) -> Unit,
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
                    status,
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
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Auto-refresh imported playlists", color = AppText, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ConnectedLibraryRefreshChip(
                            label = "Off",
                            selected = autoRefreshInterval == "off",
                            onClick = { onAutoRefreshChange("off") }
                        )
                        ConnectedLibraryRefreshChip(
                            label = "Weekly",
                            selected = autoRefreshInterval == "weekly",
                            onClick = { onAutoRefreshChange("weekly") }
                        )
                        ConnectedLibraryRefreshChip(
                            label = "Monthly",
                            selected = autoRefreshInterval == "monthly",
                            onClick = { onAutoRefreshChange("monthly") }
                        )
                    }
                    Text(
                        "Checks for changes when VANTA opens. Your saved copies stay available after disconnecting.",
                        color = AppTextMuted,
                        fontSize = 11.sp,
                        lineHeight = 15.sp
                    )
                }
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

                }
            }
        }
    }
}

@Composable
private fun ConnectedLibraryRefreshChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(if (selected) AppAccent.copy(alpha = 0.16f) else AppSurfaceRaised)
            .border(
                width = 0.5.dp,
                color = if (selected) AppAccent.copy(alpha = 0.7f) else AppOutline.copy(alpha = 0.35f),
                shape = RoundedCornerShape(999.dp)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 7.dp)
    ) {
        Text(
            text = label,
            color = if (selected) AppAccent else AppTextSecondary,
            fontSize = 12.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium
        )
    }
}

@Composable
internal fun PremiumSettingsGroup(
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(modifier = Modifier.padding(vertical = 6.dp)) {
        Text(
            text = title.uppercase(),
            color = AppTextMuted,
            fontSize = 10.sp,
            lineHeight = 14.sp,
            letterSpacing = 1.8.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(start = 4.dp, bottom = 10.dp)
        )
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp))
            .background(AppSurface).border(0.5.dp, AppOutline.copy(alpha = 0.4f), RoundedCornerShape(22.dp)),
            content = content)
    }
}

@Composable
internal fun PremiumSettingsClickItem(
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    showDivider: Boolean = true,
    checked: Boolean? = null
) {
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .then(if (checked == null) Modifier.clickable(onClick = onClick) else Modifier)
                .padding(horizontal = 16.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .then(if (checked != null) Modifier.clickable(onClick = onClick) else Modifier)
            ) {
                Text(text = title, color = AppText, fontSize = 16.sp, fontWeight = FontWeight.Medium)
                Text(text = subtitle, color = AppTextMuted, fontSize = 12.sp, lineHeight = 18.sp, modifier = Modifier.padding(top = 4.dp))
            }
            if (checked != null) Switch(
                checked = checked,
                onCheckedChange = { onClick() },
                colors = androidx.compose.material3.SwitchDefaults.colors(
                    checkedTrackColor = AppAccent,
                    checkedThumbColor = AppBackground
                )
            )
            else Icon(Icons.Default.ChevronRight, contentDescription = null, tint = AppTextMuted,
                modifier = Modifier.size(18.dp))
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
    var expandedSourceId by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(AppBackgroundTop, AppBackgroundBottom)))
            .verticalScroll(rememberScrollState())
            .padding(top = appTopContentPadding(extra = 8.dp))
            .padding(horizontal = 18.dp)
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
    val kindLabel = when {
        source.id == "qobuz_tidal" -> "Hi-res FLAC · Atmos when a source returns verified Atmos media"
        source.providerKind == "qobuz" -> "Up to 24-bit / 192 kHz · Hi-Res"
        source.providerKind == "tidal" -> "Atmos when a source returns E-AC-3 JOC"
        source.providerKind == "pandora" -> "Pandora radio"
        source.providerKind == "amazon" -> "Atmos when a source returns verified Atmos media"
        source.providerKind == "deezer" -> "Up to 16-bit / 44.1 kHz"
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
    SectionHeader("Android Auto")
    VantaCard {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Android Auto", color = AppText, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Text(
                if (readiness.codeReady) "VANTA is ready to appear as a media app in your car."
                else "VANTA's car media setup needs attention before it can appear in Android Auto.",
                color = if (readiness.codeReady) AppTextSecondary else AppWarning,
                fontSize = 13.sp,
                lineHeight = 18.sp
            )
            if (!readiness.gearheadInstalled) {
                Text("Install Android Auto from the Play Store to use this feature.", color = AppTextMuted, fontSize = 12.sp)
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .clickable {
                        AndroidAutoHelper.warmUpPlaybackService(context)
                        AndroidAutoHelper.openAndroidAutoApp(context)
                    }
                    .background(AppAccent)
                    .padding(vertical = 13.dp),
                contentAlignment = Alignment.Center
            ) {
                Text("Open Android Auto", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            }
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
