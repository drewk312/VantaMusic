package com.audiophile.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.PrintWriter
import java.time.LocalDateTime
import javax.swing.JFileChooser
import javax.swing.UIManager

private val AppBg = Color(0xFF090D12)
private val Panel = Color(0xFF111822)
private val PanelRaised = Color(0xFF151E29)
private val PanelMuted = Color(0xFF0E141B)
private val DividerColor = Color(0xFF1F2A38)
private val Accent = Color(0xFF66D9EF)
private val AccentSoft = Color(0xFF93C5FD)
private val AccentWarm = Color(0xFFF5B971)
private val Good = Color(0xFF60D394)
private val TextStrong = Color(0xFFF5F7FA)
private val TextBody = Color(0xFFB3C0CF)
private val TextDim = Color(0xFF748397)

private enum class DesktopSection(val label: String) {
    Library("Library"),
    Search("Search"),
    Queue("Queue"),
    Sources("Sources")
}

data class DesktopTrack(
    val id: String,
    val title: String,
    val artist: String,
    val album: String,
    val source: String,
    val bitrateKbps: Int,
    val durationSec: Int = 180,
    val pathOrHint: String = ""
)

private data class ResolverHit(
    val title: String,
    val artist: String,
    val album: String,
    val provider: String,
    val quality: String,
    val bitrateKbps: Int,
    val endpointHint: String
)

data class TorBoxCandidate(
    val fileName: String,
    val torrentName: String,
    val quality: String,
    val bitrateKbps: Int,
    val streamHint: String,
    val torrentId: Long? = null,
    val fileId: Int? = null,
    val infoHash: String? = null
)

fun main() = application {
    Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
        writeDesktopErrorLog(thread, throwable)
        throwable.printStackTrace()
    }
    Window(
        onCloseRequest = ::exitApplication,
        title = "Audiophile Desktop",
        resizable = true
    ) {
        MaterialTheme {
            DesktopHarnessApp()
        }
    }
}

@Composable
private fun DesktopHarnessApp() {
    val library = remember { mutableStateListOf<DesktopTrack>().apply { addAll(seedLibrary()) } }
    val upNextQueue = remember { mutableStateListOf<DesktopTrack>() }
    val torBoxCandidates = remember { mutableStateListOf<TorBoxCandidate>() }
    val activityFeed = remember { mutableStateListOf("Desktop harness ready") }
    val player = remember { DesktopAudioPlayer() }
    val torBoxClient = remember { DesktopTorBoxClient() }
    val coroutineScope = rememberCoroutineScope()

    var activeSection by remember { mutableStateOf(DesktopSection.Library) }
    var query by remember { mutableStateOf("") }
    var folderPath by remember { mutableStateOf(defaultImportDirectory().absolutePath) }
    var resolverBaseUrl by remember { mutableStateOf("https://mono.squid.wtf/") }
    var torBoxToken by remember { mutableStateOf("") }
    var communityInstances by remember { mutableStateOf("https://mono.squid.wtf/\nhttps://community.example/") }
    var status by remember { mutableStateOf("Ready") }
    var resolvedHit by remember { mutableStateOf<ResolverHit?>(null) }
    var isTorBoxSyncing by remember { mutableStateOf(false) }

    fun pushStatus(message: String) {
        status = message
        activityFeed.add(0, message)
        while (activityFeed.size > 8) {
            activityFeed.removeLast()
        }
    }

    fun syncTorBox() {
        if (torBoxToken.isBlank()) {
            activeSection = DesktopSection.Sources
            pushStatus("Add a real TorBox token first")
            return
        }
        if (isTorBoxSyncing) return

        coroutineScope.launch {
            isTorBoxSyncing = true
            pushStatus("Syncing TorBox...")
            try {
                val fetched = withContext(Dispatchers.IO) {
                    torBoxClient.fetchAudioCandidates(torBoxToken)
                }
                torBoxCandidates.clear()
                torBoxCandidates.addAll(fetched)
                fetched.mapIndexed { index, candidate -> candidate.toTrack(index) }
                    .forEach { track ->
                        if (track.pathOrHint.isNotBlank() && library.none { it.pathOrHint == track.pathOrHint }) {
                            library.add(track)
                        }
                    }
                activeSection = DesktopSection.Library
                pushStatus("Synced ${fetched.size} real TorBox audio file(s)")
            } catch (e: Exception) {
                pushStatus("TorBox sync failed: ${e.message ?: "unknown error"}")
            } finally {
                isTorBoxSyncing = false
            }
        }
    }

    DisposableEffect(player) {
        player.onStatusMessage = { message -> pushStatus(message) }
        player.onTrackEnded = {
            if (upNextQueue.isNotEmpty()) {
                val nextTrack = upNextQueue.removeAt(0)
                player.play(nextTrack)
                pushStatus("Advanced to ${nextTrack.title}")
            } else {
                pushStatus("Queue ended")
            }
        }
        onDispose {
            player.close()
        }
    }

    val visibleTracks = remember(query, library.toList()) {
        if (query.isBlank()) {
            library.toList()
        } else {
            val needle = query.trim().lowercase()
            library.filter { track ->
                track.title.lowercase().contains(needle) ||
                    track.artist.lowercase().contains(needle) ||
                    track.album.lowercase().contains(needle) ||
                    track.source.lowercase().contains(needle)
            }
        }
    }

    Surface(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(colors = listOf(AppBg, Color(0xFF0C1219)))),
        color = Color.Transparent
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            TopBar(
                query = query,
                onQueryChange = { query = it },
                folderPath = folderPath,
                onFolderPathChange = { folderPath = it },
                onImport = {
                    val imported = importAudioDirectory(folderPath)
                    if (imported.isEmpty()) {
                        pushStatus("No audio files found in $folderPath")
                    } else {
                        imported.forEach { track ->
                            if (library.none { it.pathOrHint == track.pathOrHint }) {
                                library.add(track)
                            }
                        }
                        activeSection = DesktopSection.Library
                        query = ""
                        pushStatus("Imported ${imported.size} track(s) from ${File(folderPath).name}")
                    }
                },
                onUseMusic = {
                    folderPath = defaultImportDirectory().absolutePath
                    pushStatus("Set import path to $folderPath")
                },
                onBrowse = {
                    chooseDirectory(folderPath)?.let { directory ->
                        folderPath = directory.absolutePath
                        pushStatus("Selected ${directory.absolutePath}")
                    } ?: pushStatus("Folder selection canceled")
                },
                onResolve = {
                    resolvedHit = buildResolverHit(query, library.toList(), torBoxCandidates.toList())
                    activeSection = DesktopSection.Search
                    pushStatus(if (resolvedHit != null) "Resolved mock provider match" else "Type a query first")
                },
                onSyncTorBox = {
                    syncTorBox()
                },
                onOpenDsp = { DspState.panelOpen = true },
                status = status
            )

            Row(
                modifier = Modifier.fillMaxSize(),
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                SideRail(
                    activeSection = activeSection,
                    onSelect = { activeSection = it },
                    libraryCount = library.size,
                    queueCount = upNextQueue.size
                )

                Column(
                    modifier = Modifier.weight(1.45f).fillMaxHeight(),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    HeroNowPlayingCard(
                        nowPlaying = player.currentTrack,
                        isPlaying = player.isPlaying,
                        playbackPositionSec = player.playbackPositionSec,
                        queueCount = upNextQueue.size,
                        onTogglePlay = {
                            if (player.currentTrack != null) {
                                player.togglePause()
                                pushStatus(if (player.isPlaying) "Playing ${player.currentTrack?.title}" else "Paused")
                            }
                        },
                        onPlayNextQueue = {
                            if (upNextQueue.isNotEmpty()) {
                                val nextTrack = upNextQueue.removeAt(0)
                                player.play(nextTrack)
                                pushStatus("Playing ${nextTrack.title}")
                            }
                        },
                        onOpenDsp = { DspState.panelOpen = true }
                    )

                    when (activeSection) {
                        DesktopSection.Library -> LibrarySection(
                            tracks = visibleTracks,
                            activeTrackId = player.currentTrack?.id,
                            onPlay = { track ->
                                player.play(track)
                                pushStatus("Playing ${track.title}")
                            },
                            onPlayNext = { track ->
                                upNextQueue.add(0, track)
                                pushStatus("\"${track.title}\" will play next")
                            },
                            onAddToQueue = { track ->
                                upNextQueue.add(track)
                                pushStatus("\"${track.title}\" added to queue")
                            },
                            onDownload = { track ->
                                pushStatus("Simulated download for ${track.title}")
                            }
                        )

                        DesktopSection.Search -> SearchSection(
                            query = query,
                            libraryMatches = visibleTracks.take(8),
                            candidateMatches = torBoxCandidates.filter {
                                query.isNotBlank() && (
                                    it.fileName.contains(query, ignoreCase = true) ||
                                        it.torrentName.contains(query, ignoreCase = true)
                                    )
                            }.take(5),
                            hit = resolvedHit,
                            onResolve = {
                                resolvedHit = buildResolverHit(query, library.toList(), torBoxCandidates.toList())
                                pushStatus(if (resolvedHit != null) "Resolved mock provider match" else "Type a query first")
                            },
                            onPlayLocal = { track ->
                                player.play(track)
                                pushStatus("Playing ${track.title}")
                            },
                            onQueueLocal = { track ->
                                upNextQueue.add(track)
                                pushStatus("\"${track.title}\" added to queue")
                            },
                            onPlayResolved = {
                                val track = resolvedHit?.toTrack() ?: return@SearchSection
                                if (library.none { it.pathOrHint == track.pathOrHint }) {
                                    library.add(track)
                                }
                                player.play(track)
                                pushStatus("Playing resolved track ${track.title}")
                            },
                            onSaveResolved = {
                                val track = resolvedHit?.toTrack() ?: return@SearchSection
                                if (library.none { it.pathOrHint == track.pathOrHint }) {
                                    library.add(track)
                                }
                                pushStatus("Saved resolved track ${track.title}")
                            }
                        )

                        DesktopSection.Queue -> QueueSection(
                            queue = upNextQueue,
                            onMoveUp = { index ->
                                if (index > 0) {
                                    val item = upNextQueue.removeAt(index)
                                    upNextQueue.add(index - 1, item)
                                }
                            },
                            onMoveDown = { index ->
                                if (index < upNextQueue.lastIndex) {
                                    val item = upNextQueue.removeAt(index)
                                    upNextQueue.add(index + 1, item)
                                }
                            },
                            onRemove = { index ->
                                if (index in upNextQueue.indices) {
                                    pushStatus("Removed ${upNextQueue[index].title} from queue")
                                    upNextQueue.removeAt(index)
                                }
                            }
                        )

                        DesktopSection.Sources -> SourcesSection(
                            resolverBaseUrl = resolverBaseUrl,
                            torBoxToken = torBoxToken,
                            communityInstances = communityInstances,
                            folderPath = folderPath,
                            torBoxCandidates = torBoxCandidates.toList(),
                            onResolverBaseUrlChange = { resolverBaseUrl = it },
                            onTorBoxTokenChange = { torBoxToken = it },
                            onCommunityInstancesChange = { communityInstances = it },
                            onFolderPathChange = { folderPath = it },
                            onSave = { pushStatus("Saved desktop source settings") },
                            onSync = { syncTorBox() }
                        )
                    }
                }

                Column(
                    modifier = Modifier.weight(0.9f).fillMaxHeight(),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    StatsCard(
                        libraryCount = library.size,
                        queueCount = upNextQueue.size,
                        sourceCount = library.map { it.source }.distinct().size,
                        status = status
                    )
                    CompactQueueCard(
                        modifier = Modifier.weight(1f),
                        queue = upNextQueue,
                        onPlayItem = { track ->
                            player.play(track)
                            upNextQueue.remove(track)
                            pushStatus("Playing ${track.title}")
                        }
                    )
                    ActivityCard(
                        modifier = Modifier.weight(1f),
                        entries = activityFeed
                    )
                }
            }
        }

        DspPanelSheet()
    }
}

@Composable
private fun TopBar(
    query: String,
    onQueryChange: (String) -> Unit,
    folderPath: String,
    onFolderPathChange: (String) -> Unit,
    onImport: () -> Unit,
    onUseMusic: () -> Unit,
    onBrowse: () -> Unit,
    onResolve: () -> Unit,
    onSyncTorBox: () -> Unit,
    onOpenDsp: () -> Unit,
    status: String
) {
    AppCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top
        ) {
            Column(modifier = Modifier.weight(0.34f)) {
                Text("Audiophile", color = TextStrong, fontSize = 28.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    "Desktop test bench for search, import, queue, resolver hits, TorBox sync, and DSP.",
                    color = TextBody,
                    fontSize = 13.sp
                )
            }
            Spacer(modifier = Modifier.width(18.dp))
            Column(modifier = Modifier.weight(0.66f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    AppTextField(
                        value = query,
                        onValueChange = onQueryChange,
                        label = "Search tracks, artists, albums, or test resolver text",
                        modifier = Modifier.weight(1f)
                    )
                    Button(onClick = onResolve) {
                        Text("Resolve")
                    }
                    TextButton(onClick = onSyncTorBox) {
                        Text("Sync TorBox")
                    }
                    TextButton(onClick = onOpenDsp) {
                        Text("DSP")
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    AppTextField(
                        value = folderPath,
                        onValueChange = onFolderPathChange,
                        label = "Import folder path",
                        modifier = Modifier.weight(1f)
                    )
                    Button(onClick = onImport) {
                        Text("Import")
                    }
                    TextButton(onClick = onUseMusic) {
                        Text("Music")
                    }
                    TextButton(onClick = onBrowse) {
                        Text("Browse")
                    }
                }
                Text(status, color = AccentWarm, fontSize = 12.sp)
            }
        }
    }
}

@Composable
private fun SideRail(
    activeSection: DesktopSection,
    onSelect: (DesktopSection) -> Unit,
    libraryCount: Int,
    queueCount: Int
) {
    AppCard(
        modifier = Modifier.width(148.dp).fillMaxHeight()
    ) {
        Column(
            modifier = Modifier.fillMaxHeight(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("Test", color = TextDim, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            DesktopSection.entries.forEach { section ->
                RailButton(
                    label = section.label,
                    active = section == activeSection,
                    badge = when (section) {
                        DesktopSection.Library -> libraryCount.toString()
                        DesktopSection.Queue -> queueCount.toString()
                        else -> null
                    },
                    onClick = { onSelect(section) }
                )
            }
            Spacer(modifier = Modifier.weight(1f))
            Text("Desktop playback supports imported local files and direct URLs.", color = TextDim, fontSize = 11.sp)
        }
    }
}

@Composable
private fun HeroNowPlayingCard(
    nowPlaying: DesktopTrack?,
    isPlaying: Boolean,
    playbackPositionSec: Int,
    queueCount: Int,
    onTogglePlay: () -> Unit,
    onPlayNextQueue: () -> Unit,
    onOpenDsp: () -> Unit
) {
    AppCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(154.dp)
                    .clip(RoundedCornerShape(26.dp))
                    .background(
                        Brush.linearGradient(
                            colors = listOf(Color(0xFF18293A), Color(0xFF274B66), Color(0xFF152232))
                        )
                    ),
                contentAlignment = Alignment.BottomStart
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Now Playing", color = TextDim, fontSize = 12.sp)
                    Text(nowPlaying?.source ?: "Idle", color = AccentSoft, fontWeight = FontWeight.SemiBold)
                }
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(nowPlaying?.title ?: "No track selected", color = TextStrong, fontSize = 32.sp, fontWeight = FontWeight.Bold)
                Text(
                    listOfNotNull(nowPlaying?.artist, nowPlaying?.album).joinToString(" • ").ifBlank { "Import a folder, resolve a query, or sync TorBox samples." },
                    color = TextBody,
                    fontSize = 14.sp
                )
                ProgressStrip(
                    progress = if (nowPlaying == null || nowPlaying.durationSec == 0) 0f else playbackPositionSec.toFloat() / nowPlaying.durationSec.toFloat(),
                    leftLabel = formatTime(playbackPositionSec),
                    centerLabel = if (isPlaying) "Playing" else "Paused",
                    rightLabel = formatTime(nowPlaying?.durationSec ?: 0)
                )
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Button(onClick = onTogglePlay, enabled = nowPlaying != null) {
                        Text(if (isPlaying) "Pause" else "Play")
                    }
                    TextButton(onClick = onPlayNextQueue, enabled = queueCount > 0) {
                        Text("Play Next In Queue")
                    }
                    TextButton(onClick = onOpenDsp) {
                        Text("Open DSP")
                    }
                    StatusPill("Queue $queueCount")
                }
            }
        }
    }
}

@Composable
private fun LibrarySection(
    tracks: List<DesktopTrack>,
    activeTrackId: String?,
    onPlay: (DesktopTrack) -> Unit,
    onPlayNext: (DesktopTrack) -> Unit,
    onAddToQueue: (DesktopTrack) -> Unit,
    onDownload: (DesktopTrack) -> Unit
) {
    AppCard(
        modifier = Modifier.fillMaxSize()
    ) {
        SectionHeader("Library", "${tracks.size} visible tracks")
        Spacer(modifier = Modifier.height(10.dp))
        if (tracks.isEmpty()) {
            EmptyState("No tracks match the current search.")
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                itemsIndexed(tracks) { _, track ->
                    TrackRow(
                        track = track,
                        active = track.id == activeTrackId,
                        onPlay = { onPlay(track) },
                        onPlayNext = { onPlayNext(track) },
                        onQueue = { onAddToQueue(track) },
                        onSave = { onDownload(track) }
                    )
                }
            }
        }
    }
}

@Composable
private fun SearchSection(
    query: String,
    libraryMatches: List<DesktopTrack>,
    candidateMatches: List<TorBoxCandidate>,
    hit: ResolverHit?,
    onResolve: () -> Unit,
    onPlayLocal: (DesktopTrack) -> Unit,
    onQueueLocal: (DesktopTrack) -> Unit,
    onPlayResolved: () -> Unit,
    onSaveResolved: () -> Unit
) {
    AppCard(
        modifier = Modifier.fillMaxSize()
    ) {
        SectionHeader("Search", "Resolver test surface")
        Spacer(modifier = Modifier.height(10.dp))
        if (query.isBlank()) {
            EmptyState("Type a query in the top bar and resolve it.")
            return@AppCard
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    StatusPill("${libraryMatches.size} library")
                    StatusPill("${candidateMatches.size} TorBox")
                    StatusPill(if (hit != null) "resolver ready" else "unresolved")
                }
            }

            if (libraryMatches.isNotEmpty()) {
                item {
                    Text("Library Matches", color = TextBody, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                }
                itemsIndexed(libraryMatches) { _, track ->
                    TrackRow(
                        track = track,
                        active = false,
                        onPlay = { onPlayLocal(track) },
                        onPlayNext = { onQueueLocal(track) },
                        onQueue = { onQueueLocal(track) },
                        onSave = { }
                    )
                }
            }

            if (candidateMatches.isNotEmpty()) {
                item {
                    Text("TorBox Candidates", color = TextBody, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                }
                itemsIndexed(candidateMatches) { _, candidate ->
                    AppRowCard {
                        Text(candidate.fileName, color = TextStrong, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text("${candidate.torrentName} • ${candidate.quality} ${candidate.bitrateKbps}kbps", color = TextBody, fontSize = 12.sp)
                    }
                }
            }

            item {
                if (hit == null) {
                    EmptyState("No resolved hit yet for \"$query\".") {
                        Button(onClick = onResolve) {
                            Text("Resolve Query")
                        }
                    }
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        Text("Resolved Match", color = TextBody, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(180.dp)
                                .clip(RoundedCornerShape(22.dp))
                                .background(
                                    Brush.linearGradient(
                                        colors = listOf(Color(0xFF132030), Color(0xFF1C3044))
                                    )
                                )
                                .padding(20.dp)
                        ) {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(hit.title, color = TextStrong, fontSize = 28.sp, fontWeight = FontWeight.Bold)
                                Text("${hit.artist} • ${hit.album}", color = TextBody, fontSize = 14.sp)
                                Spacer(modifier = Modifier.height(12.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    StatusPill(hit.provider)
                                    StatusPill("${hit.quality} ${hit.bitrateKbps}kbps")
                                }
                            }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Button(onClick = onPlayResolved) {
                                Text("Play Resolved")
                            }
                            TextButton(onClick = onSaveResolved) {
                                Text("Save To Library")
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun QueueSection(
    queue: List<DesktopTrack>,
    onMoveUp: (Int) -> Unit,
    onMoveDown: (Int) -> Unit,
    onRemove: (Int) -> Unit
) {
    AppCard(
        modifier = Modifier.fillMaxSize()
    ) {
        SectionHeader("Up Next", "${queue.size} tracks queued")
        Spacer(modifier = Modifier.height(10.dp))
        if (queue.isEmpty()) {
            EmptyState("Queue is empty.")
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                itemsIndexed(queue) { index, track ->
                    AppRowCard {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(track.title, color = TextStrong, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text("${track.artist} • ${track.album}", color = TextBody, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                if (index > 0) InlineAction("Up") { onMoveUp(index) }
                                if (index < queue.lastIndex) InlineAction("Down") { onMoveDown(index) }
                                InlineAction("Remove") { onRemove(index) }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SourcesSection(
    resolverBaseUrl: String,
    torBoxToken: String,
    communityInstances: String,
    folderPath: String,
    torBoxCandidates: List<TorBoxCandidate>,
    onResolverBaseUrlChange: (String) -> Unit,
    onTorBoxTokenChange: (String) -> Unit,
    onCommunityInstancesChange: (String) -> Unit,
    onFolderPathChange: (String) -> Unit,
    onSave: () -> Unit,
    onSync: () -> Unit
) {
    AppCard(
        modifier = Modifier.fillMaxSize()
    ) {
        SectionHeader("Sources", "Desktop config and TorBox samples")
        Spacer(modifier = Modifier.height(10.dp))
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                AppTextField(
                    value = resolverBaseUrl,
                    onValueChange = onResolverBaseUrlChange,
                    label = "Resolver base URL",
                    modifier = Modifier.fillMaxWidth()
                )
            }
            item {
                AppTextField(
                    value = torBoxToken,
                    onValueChange = onTorBoxTokenChange,
                    label = "TorBox token",
                    modifier = Modifier.fillMaxWidth()
                )
            }
            item {
                AppTextField(
                    value = folderPath,
                    onValueChange = onFolderPathChange,
                    label = "Default import path",
                    modifier = Modifier.fillMaxWidth()
                )
            }
            item {
                AppTextField(
                    value = communityInstances,
                    onValueChange = onCommunityInstancesChange,
                    label = "Community instances",
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = false,
                    minLines = 3
                )
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(onClick = onSave) {
                        Text("Save Sources")
                    }
                    TextButton(onClick = onSync) {
                        Text("Sync TorBox Samples")
                    }
                }
            }
            itemsIndexed(torBoxCandidates) { _, candidate ->
                AppRowCard {
                    Text(candidate.fileName, color = TextStrong, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("${candidate.torrentName} • ${candidate.quality} ${candidate.bitrateKbps}kbps", color = TextBody, fontSize = 12.sp)
                }
            }
        }
    }
}

@Composable
private fun StatsCard(
    libraryCount: Int,
    queueCount: Int,
    sourceCount: Int,
    status: String
) {
    AppCard {
        SectionHeader("Status", "Current desktop state")
        Spacer(modifier = Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatusPill("$libraryCount tracks")
            StatusPill("$queueCount queued")
            StatusPill("$sourceCount sources")
        }
        Spacer(modifier = Modifier.height(12.dp))
        Text(status, color = Good, fontSize = 13.sp)
    }
}

@Composable
private fun CompactQueueCard(
    modifier: Modifier = Modifier,
    queue: List<DesktopTrack>,
    onPlayItem: (DesktopTrack) -> Unit
) {
    AppCard(
        modifier = modifier
    ) {
        SectionHeader("Quick Queue", if (queue.isEmpty()) "Nothing queued" else "${queue.size} waiting")
        Spacer(modifier = Modifier.height(10.dp))
        if (queue.isEmpty()) {
            EmptyState("Use Next or Queue from the library.")
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                itemsIndexed(queue.take(8)) { _, track ->
                    AppRowCard {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(track.title, color = TextStrong, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(track.artist, color = TextBody, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                            InlineAction("Play") { onPlayItem(track) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ActivityCard(
    modifier: Modifier = Modifier,
    entries: List<String>
) {
    AppCard(
        modifier = modifier
    ) {
        SectionHeader("Activity", "Recent actions")
        Spacer(modifier = Modifier.height(10.dp))
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            itemsIndexed(entries) { _, entry ->
                AppRowCard {
                    Text(entry, color = TextBody, fontSize = 12.sp)
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(title: String, subtitle: String) {
    Column {
        Text(title, color = TextStrong, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Text(subtitle, color = TextDim, fontSize = 12.sp)
    }
}

@Composable
private fun RailButton(
    label: String,
    active: Boolean,
    badge: String?,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick),
        color = if (active) Color(0xFF182432) else Color.Transparent
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 11.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(label, color = if (active) TextStrong else TextBody, fontWeight = if (active) FontWeight.SemiBold else FontWeight.Medium)
            if (badge != null) {
                Box(
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(if (active) Accent.copy(alpha = 0.18f) else PanelRaised)
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(badge, color = if (active) Accent else TextDim, fontSize = 11.sp)
                }
            }
        }
    }
}

@Composable
private fun TrackRow(
    track: DesktopTrack,
    active: Boolean,
    onPlay: () -> Unit,
    onPlayNext: () -> Unit,
    onQueue: () -> Unit,
    onSave: () -> Unit
) {
    AppRowCard(
        highlight = active
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(if (active) Accent.copy(alpha = 0.16f) else PanelRaised)
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    track.title,
                    color = if (active) AccentSoft else TextStrong,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    "${track.artist} • ${track.album}",
                    color = TextBody,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text("${track.source} ${track.bitrateKbps}kbps", color = TextDim, fontSize = 11.sp)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                InlineAction("Play", onPlay)
                InlineAction("Next", onPlayNext)
                InlineAction("Queue", onQueue)
                InlineAction("Save", onSave)
            }
        }
    }
}

@Composable
private fun ProgressStrip(
    progress: Float,
    leftLabel: String,
    centerLabel: String,
    rightLabel: String
) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(leftLabel, color = TextDim, fontSize = 12.sp)
            Text(centerLabel, color = Accent, fontSize = 12.sp)
            Text(rightLabel, color = TextDim, fontSize = 12.sp)
        }
        Spacer(modifier = Modifier.height(8.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(999.dp))
                .background(PanelRaised)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(progress.coerceIn(0f, 1f))
                    .height(6.dp)
                    .clip(RoundedCornerShape(999.dp))
                    .background(
                        Brush.horizontalGradient(
                            colors = listOf(Accent, AccentSoft)
                        )
                    )
            )
        }
    }
}

@Composable
private fun StatusPill(text: String) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(PanelRaised)
            .border(1.dp, DividerColor, RoundedCornerShape(999.dp))
            .padding(horizontal = 10.dp, vertical = 7.dp)
    ) {
        Text(text, color = TextBody, fontSize = 12.sp)
    }
}

@Composable
private fun EmptyState(message: String, action: @Composable (() -> Unit)? = null) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 22.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(message, color = TextDim, fontSize = 13.sp)
        action?.invoke()
    }
}

@Composable
private fun InlineAction(label: String, onClick: () -> Unit) {
    Text(
        text = label,
        color = Accent,
        fontSize = 12.sp,
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 6.dp)
    )
}

@Composable
private fun AppTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    singleLine: Boolean = true,
    minLines: Int = 1
) {
    TextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier,
        singleLine = singleLine,
        minLines = minLines,
        label = {
            Text(label)
        },
        colors = TextFieldDefaults.colors(
            focusedContainerColor = PanelRaised,
            unfocusedContainerColor = PanelRaised,
            focusedIndicatorColor = Accent,
            unfocusedIndicatorColor = DividerColor,
            focusedLabelColor = AccentSoft,
            unfocusedLabelColor = TextDim,
            focusedTextColor = TextStrong,
            unfocusedTextColor = TextStrong
        )
    )
}

@Composable
private fun AppCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Panel),
        border = androidx.compose.foundation.BorderStroke(1.dp, DividerColor)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(Panel.copy(alpha = 0.98f), PanelMuted.copy(alpha = 0.98f))
                    )
                )
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            content()
        }
    }
}

@Composable
private fun AppRowCard(
    highlight: Boolean = false,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(if (highlight) Color(0xFF162433) else PanelRaised)
            .border(1.dp, if (highlight) Accent.copy(alpha = 0.26f) else DividerColor, RoundedCornerShape(18.dp))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        content()
    }
}

private fun seedLibrary(): List<DesktopTrack> = emptyList()

private fun seedTorBoxCandidates(): List<TorBoxCandidate> = emptyList()

private fun buildResolverHit(
    query: String,
    library: List<DesktopTrack>,
    torBoxCandidates: List<TorBoxCandidate>
): ResolverHit? {
    val trimmed = query.trim()
    if (trimmed.isBlank()) return null
    val needle = trimmed.lowercase()

    library.firstOrNull {
        it.title.lowercase().contains(needle) ||
            it.artist.lowercase().contains(needle) ||
            it.album.lowercase().contains(needle)
    }?.let { match ->
        return ResolverHit(
            title = match.title,
            artist = match.artist,
            album = match.album,
            provider = "${match.source}-library",
            quality = if (match.bitrateKbps >= 1000) "FLAC" else "STREAM",
            bitrateKbps = match.bitrateKbps,
            endpointHint = match.pathOrHint.ifBlank { "resolver://library/${match.id}" }
        )
    }

    torBoxCandidates.firstOrNull {
        it.fileName.lowercase().contains(needle) || it.torrentName.lowercase().contains(needle)
    }?.let { candidate ->
        val (artist, title) = splitArtistAndTitle(candidate.fileName.substringBeforeLast(".", candidate.fileName))
        return ResolverHit(
            title = title,
            artist = artist,
            album = candidate.torrentName,
            provider = "torbox-cache",
            quality = candidate.quality,
            bitrateKbps = candidate.bitrateKbps,
            endpointHint = candidate.streamHint
        )
    }

    return null
}

private fun ResolverHit.toTrack(): DesktopTrack {
    return DesktopTrack(
        id = "resolved-${title.lowercase()}-${artist.lowercase()}",
        title = title,
        artist = artist,
        album = album,
        source = provider,
        bitrateKbps = bitrateKbps,
        durationSec = 210,
        pathOrHint = endpointHint
    )
}

private fun TorBoxCandidate.toTrack(index: Int): DesktopTrack {
    val trimmedName = fileName.substringBeforeLast(".", fileName)
    val (artist, title) = splitArtistAndTitle(trimmedName)
    return DesktopTrack(
        id = "torbox-$index-${title.lowercase()}",
        title = title,
        artist = artist,
        album = torrentName,
        source = "torbox",
        bitrateKbps = bitrateKbps,
        durationSec = 200,
        pathOrHint = streamHint
    )
}

private fun importAudioDirectory(path: String): List<DesktopTrack> {
    val directory = File(path).takeIf { it.exists() && it.isDirectory } ?: return emptyList()
    return directory.walkTopDown()
        .filter { it.isFile && it.extension.lowercase() in setOf("flac", "wav", "mp3", "m4a", "ogg", "opus") }
        .take(500)
        .mapIndexed { index, file ->
            val (artist, title) = splitArtistAndTitle(file.nameWithoutExtension)
            DesktopTrack(
                id = "local-${file.absolutePath.hashCode()}-$index",
                title = title,
                artist = artist,
                album = file.parentFile?.name ?: "Imported",
                source = "local",
                bitrateKbps = estimateBitrate(file.extension),
                durationSec = 180,
                pathOrHint = file.absolutePath
            )
        }
        .toList()
}

private fun chooseDirectory(initialPath: String): File? {
    return try {
        UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName())
        JFileChooser(File(initialPath).takeIf { it.exists() } ?: defaultImportDirectory()).apply {
            fileSelectionMode = JFileChooser.DIRECTORIES_ONLY
            dialogTitle = "Choose Audio Folder"
            isAcceptAllFileFilterUsed = false
        }.let { chooser ->
            val result = chooser.showOpenDialog(null)
            if (result == JFileChooser.APPROVE_OPTION) chooser.selectedFile else null
        }
    } catch (_: Exception) {
        null
    }
}

private fun defaultImportDirectory(): File {
    val userHome = File(System.getProperty("user.home"))
    val preferred = listOf("Music", "Downloads", "Desktop")
        .map { File(userHome, it) }
        .firstOrNull { it.exists() && it.isDirectory }
    return preferred ?: userHome
}

private fun splitArtistAndTitle(name: String): Pair<String, String> {
    val normalized = name.trim()
    val parts = normalized.split(" - ", limit = 2)
    return if (parts.size == 2) {
        parts[0].trim().ifBlank { "Unknown Artist" } to parts[1].trim().ifBlank { normalized }
    } else {
        "Unknown Artist" to normalized
    }
}

private fun estimateBitrate(extension: String): Int = when (extension.lowercase()) {
    "flac", "wav" -> 1411
    "mp3", "ogg", "opus" -> 320
    "m4a" -> 256
    else -> 256
}

private fun formatTime(seconds: Int): String {
    val safe = seconds.coerceAtLeast(0)
    return "${safe / 60}:${(safe % 60).toString().padStart(2, '0')}"
}

private fun writeDesktopErrorLog(thread: Thread, throwable: Throwable) {
    runCatching {
        val logDir = File("desktopApp/build/logs").apply { mkdirs() }
        val logFile = File(logDir, "desktop-error.log")
        PrintWriter(FileOutputStream(logFile, true).bufferedWriter(Charsets.UTF_8)).use { writer ->
            writer.println("==== ${LocalDateTime.now()} ====")
            writer.println("Thread: ${thread.name}")
            throwable.printStackTrace(writer)
            writer.println()
        }
    }
}
