package com.audiophile.musicplayer.ui

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.audiophile.musicplayer.data.display.DisplayMetadataCleaner
import com.audiophile.musicplayer.data.local.entities.UnifiedTrackWithSources

enum class VantaActionSheetAction {
    PLAY,
    PLAY_NEXT,
    ADD_TO_QUEUE,
    START_RADIO,
    START_SONIC_RADIO,
    ADD_TO_LIBRARY,
    REMOVE_FROM_LIBRARY,
    FAVORITE,
    UNFAVORITE,
    ADD_TO_PLAYLIST,
    VIEW_ALBUM,
    VIEW_ARTIST,
    SHARE_TRACK,
    SHARE_ALBUM,
    SHARE_ARTIST,
    SHARE_PLAYLIST,
    DOWNLOAD_LOCAL,
    SLEEP_TIMER,
    REMOVE_FROM_QUEUE,
    MOVE_QUEUE_ITEM,
    SHUFFLE_QUEUE,
    VIEW_FILE_INFO,
}

data class VantaActionAvailability(
    val action: VantaActionSheetAction,
    val visible: Boolean,
    val enabled: Boolean,
    val reasonDisabled: String? = null
)

sealed class VantaActionContext {
    data class Track(
        val trackId: String,
        val title: String,
        val artist: String,
        val album: String?,
        val artworkUrl: String?,
        val sourceProviderId: String?,
        val externalTrackId: String?,
        val isPlayable: Boolean,
        val isInLibrary: Boolean,
        val isFavorite: Boolean,
        val hasAlbum: Boolean,
        val hasArtist: Boolean,
        val canStartRadio: Boolean,
        val canQueue: Boolean,
        val canShare: Boolean,
        val qualityLabel: String?,
        val explicit: Boolean,
        val isNowPlaying: Boolean = false,
        val qualityInfo: com.audiophile.musicplayer.data.display.VantaQualityInfo? = null,
        val acousticness: Double? = null,
        val streamUrl: String? = null,
        val durationMs: Long? = null
    ) : VantaActionContext()

    data class Album(
        val albumName: String,
        val artistName: String,
        val artworkUrl: String?,
        val releaseYear: Int?,
        val genre: String?,
        val explicit: Boolean,
        val isInLibrary: Boolean,
        val canShare: Boolean
    ) : VantaActionContext()

    data class Artist(
        val artistName: String,
        val artistId: String?,
        val artworkUrl: String?,
        val isInLibrary: Boolean,
        val canStartRadio: Boolean,
        val canShare: Boolean
    ) : VantaActionContext()

    data class Playlist(
        val playlistId: Long,
        val name: String,
        val trackCount: Int,
        val isInLibrary: Boolean,
        val canShare: Boolean
    ) : VantaActionContext()

    data class QueueItem(
        val index: Int,
        val track: UnifiedTrackWithSources,
        val isCurrent: Boolean
    ) : VantaActionContext()

    data class SearchResult(
        val trackId: String,
        val title: String,
        val artist: String,
        val album: String?,
        val artworkUrl: String?,
        val sourceProviderId: String?,
        val externalTrackId: String?,
        val isPlayable: Boolean,
        val isInLibrary: Boolean,
        val isFavorite: Boolean,
        val hasAlbum: Boolean,
        val hasArtist: Boolean,
        val canStartRadio: Boolean,
        val canQueue: Boolean,
        val canShare: Boolean,
        val qualityLabel: String?,
        val explicit: Boolean,
        val qualityInfo: com.audiophile.musicplayer.data.display.VantaQualityInfo? = null,
        val acousticness: Double? = null
    ) : VantaActionContext()

    data class Station(
        val stationId: String,
        val title: String,
        val artworkUrl: String?
    ) : VantaActionContext()
}

class VantaActionResolver {
    fun actionsFor(context: VantaActionContext): List<VantaActionAvailability> {
        val actions = mutableListOf<VantaActionAvailability>()
        
        when (context) {
            is VantaActionContext.Track -> {
                // Primary Group
                actions.add(VantaActionAvailability(VantaActionSheetAction.PLAY, visible = !context.isNowPlaying, enabled = context.isPlayable))
                actions.add(VantaActionAvailability(VantaActionSheetAction.PLAY_NEXT, visible = !context.isNowPlaying, enabled = context.canQueue))
                actions.add(VantaActionAvailability(VantaActionSheetAction.ADD_TO_QUEUE, visible = !context.isNowPlaying, enabled = context.canQueue))
                actions.add(VantaActionAvailability(VantaActionSheetAction.START_RADIO, visible = true, enabled = context.canStartRadio))
                actions.add(VantaActionAvailability(VantaActionSheetAction.START_SONIC_RADIO, visible = true, enabled = context.canStartRadio))

                // Library Group
                if (context.isInLibrary) {
                    actions.add(VantaActionAvailability(VantaActionSheetAction.REMOVE_FROM_LIBRARY, visible = true, enabled = true))
                } else {
                    actions.add(VantaActionAvailability(VantaActionSheetAction.ADD_TO_LIBRARY, visible = true, enabled = true))
                }
                if (context.isFavorite) {
                    actions.add(VantaActionAvailability(VantaActionSheetAction.UNFAVORITE, visible = true, enabled = true))
                } else {
                    actions.add(VantaActionAvailability(VantaActionSheetAction.FAVORITE, visible = true, enabled = true))
                }
                actions.add(VantaActionAvailability(VantaActionSheetAction.ADD_TO_PLAYLIST, visible = true, enabled = true))

                // Navigate Group
                actions.add(VantaActionAvailability(VantaActionSheetAction.VIEW_ALBUM, visible = context.hasAlbum, enabled = context.hasAlbum))
                actions.add(VantaActionAvailability(VantaActionSheetAction.VIEW_ARTIST, visible = context.hasArtist, enabled = context.hasArtist))

                // Share Group
                actions.add(VantaActionAvailability(VantaActionSheetAction.SHARE_TRACK, visible = context.canShare, enabled = context.canShare))

                // Manage Group
                val hasSource = !context.sourceProviderId.isNullOrBlank()
                actions.add(VantaActionAvailability(VantaActionSheetAction.DOWNLOAD_LOCAL, visible = hasSource, enabled = hasSource))
                actions.add(VantaActionAvailability(VantaActionSheetAction.SLEEP_TIMER, visible = true, enabled = true))
                if (context.isNowPlaying) {
                    actions.add(VantaActionAvailability(VantaActionSheetAction.SHUFFLE_QUEUE, visible = true, enabled = true))
                }
                actions.add(VantaActionAvailability(VantaActionSheetAction.VIEW_FILE_INFO, visible = true, enabled = true))
            }
            is VantaActionContext.Album -> {
                actions.add(VantaActionAvailability(VantaActionSheetAction.PLAY, visible = true, enabled = true))
                actions.add(VantaActionAvailability(VantaActionSheetAction.SHARE_ALBUM, visible = context.canShare, enabled = context.canShare))
            }
            is VantaActionContext.Artist -> {
                actions.add(VantaActionAvailability(VantaActionSheetAction.START_RADIO, visible = context.canStartRadio, enabled = context.canStartRadio))
                actions.add(VantaActionAvailability(VantaActionSheetAction.SHARE_ARTIST, visible = context.canShare, enabled = context.canShare))
            }
            is VantaActionContext.Playlist -> {
                actions.add(VantaActionAvailability(VantaActionSheetAction.PLAY, visible = true, enabled = context.trackCount > 0))
                actions.add(VantaActionAvailability(VantaActionSheetAction.SHARE_PLAYLIST, visible = context.canShare, enabled = context.canShare))
            }
            is VantaActionContext.QueueItem -> {
                actions.add(VantaActionAvailability(VantaActionSheetAction.PLAY, visible = !context.isCurrent, enabled = true))
                actions.add(VantaActionAvailability(VantaActionSheetAction.REMOVE_FROM_QUEUE, visible = true, enabled = true))
                actions.add(VantaActionAvailability(VantaActionSheetAction.MOVE_QUEUE_ITEM, visible = true, enabled = true))
                actions.add(VantaActionAvailability(VantaActionSheetAction.SHUFFLE_QUEUE, visible = true, enabled = true))
            }
            is VantaActionContext.SearchResult -> {
                actions.add(VantaActionAvailability(VantaActionSheetAction.PLAY, visible = true, enabled = context.isPlayable))
                if (context.isInLibrary) {
                    actions.add(VantaActionAvailability(VantaActionSheetAction.REMOVE_FROM_LIBRARY, visible = true, enabled = true))
                } else {
                    actions.add(VantaActionAvailability(VantaActionSheetAction.ADD_TO_LIBRARY, visible = true, enabled = true))
                }
                actions.add(VantaActionAvailability(VantaActionSheetAction.ADD_TO_QUEUE, visible = true, enabled = context.canQueue))
            }
            is VantaActionContext.Station -> {
                actions.add(VantaActionAvailability(VantaActionSheetAction.PLAY, visible = true, enabled = true))
            }
        }
        
        val contextName = when (context) {
            is VantaActionContext.Track -> "track"
            is VantaActionContext.Album -> "album"
            is VantaActionContext.Artist -> "artist"
            is VantaActionContext.Playlist -> "playlist"
            is VantaActionContext.QueueItem -> "queueitem"
            is VantaActionContext.SearchResult -> "searchresult"
            is VantaActionContext.Station -> "station"
        }
        actions.forEach { avail ->
            android.util.Log.d("VANTA_ACTION_SHEET", "context='$contextName' action='${avail.action}' visible=${avail.visible} enabled=${avail.enabled}")
        }
        
        return actions
    }
}

@Composable
fun VantaActionSheet(
    context: VantaActionContext,
    onDismiss: () -> Unit,
    onActionSelected: (VantaActionSheetAction) -> Unit
) {
    val resolver = remember { VantaActionResolver() }
    val availabilities = remember(context) { resolver.actionsFor(context) }
    
    val headerTitle = when (context) {
        is VantaActionContext.Track -> context.title
        is VantaActionContext.Album -> context.albumName
        is VantaActionContext.Artist -> context.artistName
        is VantaActionContext.Playlist -> context.name
        is VantaActionContext.QueueItem -> context.track.track.title
        is VantaActionContext.SearchResult -> context.title
        is VantaActionContext.Station -> context.title
    }
    
    val headerSubtitle = when (context) {
        is VantaActionContext.Track -> context.artist
        is VantaActionContext.Album -> context.artistName
        is VantaActionContext.Artist -> "Artist"
        is VantaActionContext.Playlist -> "${context.trackCount} songs"
        is VantaActionContext.QueueItem -> context.track.track.artist
        is VantaActionContext.SearchResult -> context.artist
        is VantaActionContext.Station -> "Station"
    }

    val artworkUrl = when (context) {
        is VantaActionContext.Track -> context.artworkUrl
        is VantaActionContext.Album -> context.artworkUrl
        is VantaActionContext.Artist -> context.artworkUrl
        is VantaActionContext.Playlist -> null
        is VantaActionContext.QueueItem -> context.track.track.coverArtUrl
        is VantaActionContext.SearchResult -> context.artworkUrl
        is VantaActionContext.Station -> context.artworkUrl
    }

    val explicit = when (context) {
        is VantaActionContext.Track -> context.explicit
        is VantaActionContext.Album -> context.explicit
        is VantaActionContext.SearchResult -> context.explicit
        is VantaActionContext.QueueItem -> false // UnifiedTrack has no explicit field
        else -> false
    }

    val qualityLabel = when (context) {
        is VantaActionContext.Track -> context.qualityLabel
        is VantaActionContext.SearchResult -> context.qualityLabel
        else -> null
    }

    val contextQualityInfo = when (context) {
        is VantaActionContext.Track -> context.qualityInfo
        is VantaActionContext.SearchResult -> context.qualityInfo
        else -> null
    }

    val contextAcousticness = when (context) {
        is VantaActionContext.Track -> context.acousticness
        is VantaActionContext.SearchResult -> context.acousticness
        else -> null
    }

    val cleaned = remember(headerTitle, headerSubtitle) {
        DisplayMetadataCleaner.computeDisplayMetadata(
            rawTitle = headerTitle,
            rawArtist = headerSubtitle,
            rawAlbum = null
        )
    }
    val sheetTitle = cleaned.title.ifBlank { headerTitle }
    val sheetArtist = cleaned.artist.ifBlank { headerSubtitle }

    VantaBottomSheet(visible = true, onDismiss = onDismiss) {
        // Context Header
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .border(0.5.dp, AppOutline, RoundedCornerShape(12.dp))
            ) {
                NetworkArtwork(
                    artworkUrl = artworkUrl,
                    seed = headerTitle,
                    modifier = Modifier.fillMaxSize()
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = sheetTitle,
                        color = AppText,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (explicit) VantaExplicitBadge()
                }
                Text(
                    text = sheetArtist,
                    color = AppTextSecondary,
                    fontSize = 14.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (contextQualityInfo != null || qualityLabel != null || contextAcousticness != null) {
                    val resolvedInfo = contextQualityInfo ?: qualityLabel?.let {
                        com.audiophile.musicplayer.data.display.VantaQualityInfo.fromSource(
                            bitrate = null,
                            quality = it,
                            mime = null,
                            status = null,
                            isValidated = true
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                    VantaFormatBadgeRow(
                        qualityInfo = resolvedInfo,
                        acousticness = contextAcousticness,
                        size = FormatBadgeSize.Compact
                    )
                }
            }
        }

        Spacer(Modifier.height(20.dp))
        VantaSheetDivider()

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp)
        ) {
            // Group 1: Primary actions
            val primaryActions = availabilities.filter {
                it.action in listOf(
                    VantaActionSheetAction.PLAY,
                    VantaActionSheetAction.PLAY_NEXT,
                    VantaActionSheetAction.ADD_TO_QUEUE,
                    VantaActionSheetAction.START_RADIO,
                    VantaActionSheetAction.START_SONIC_RADIO
                ) && it.visible
            }
            if (primaryActions.isNotEmpty()) {
                primaryActions.forEach { actionAvail ->
                    ActionItem(actionAvail, onActionSelected, onDismiss)
                }
                VantaSheetDivider()
            }

            // Group 2: Library actions
            val libraryActions = availabilities.filter {
                it.action in listOf(
                    VantaActionSheetAction.ADD_TO_LIBRARY,
                    VantaActionSheetAction.REMOVE_FROM_LIBRARY,
                    VantaActionSheetAction.FAVORITE,
                    VantaActionSheetAction.UNFAVORITE,
                    VantaActionSheetAction.ADD_TO_PLAYLIST
                ) && it.visible
            }
            if (libraryActions.isNotEmpty()) {
                libraryActions.forEach { actionAvail ->
                    ActionItem(actionAvail, onActionSelected, onDismiss)
                }
                VantaSheetDivider()
            }

            // Group 3: Navigation actions
            val navigateActions = availabilities.filter {
                it.action in listOf(
                    VantaActionSheetAction.VIEW_ALBUM,
                    VantaActionSheetAction.VIEW_ARTIST
                ) && it.visible
            }
            if (navigateActions.isNotEmpty()) {
                navigateActions.forEach { actionAvail ->
                    ActionItem(actionAvail, onActionSelected, onDismiss)
                }
                VantaSheetDivider()
            }

            // Group 4: Share actions
            val shareActions = availabilities.filter {
                it.action in listOf(
                    VantaActionSheetAction.SHARE_TRACK,
                    VantaActionSheetAction.SHARE_ALBUM,
                    VantaActionSheetAction.SHARE_ARTIST,
                    VantaActionSheetAction.SHARE_PLAYLIST
                ) && it.visible
            }
            if (shareActions.isNotEmpty()) {
                shareActions.forEach { actionAvail ->
                    ActionItem(actionAvail, onActionSelected, onDismiss)
                }
                VantaSheetDivider()
            }

            // Group 5: Manage actions
            val manageActions = availabilities.filter {
                it.action in listOf(
                    VantaActionSheetAction.SHUFFLE_QUEUE,
                    VantaActionSheetAction.DOWNLOAD_LOCAL,
                    VantaActionSheetAction.SLEEP_TIMER,
                    VantaActionSheetAction.VIEW_FILE_INFO,
                    VantaActionSheetAction.REMOVE_FROM_QUEUE,
                    VantaActionSheetAction.MOVE_QUEUE_ITEM
                ) && it.visible
            }
            if (manageActions.isNotEmpty()) {
                manageActions.forEach { actionAvail ->
                    ActionItem(actionAvail, onActionSelected, onDismiss)
                }
            }
        }
    }
}

@Composable
private fun ActionItem(
    availability: VantaActionAvailability,
    onActionSelected: (VantaActionSheetAction) -> Unit,
    onDismiss: () -> Unit
) {
    val icon = when (availability.action) {
        VantaActionSheetAction.PLAY -> Icons.Filled.PlayArrow
        VantaActionSheetAction.PLAY_NEXT -> Icons.Filled.SkipNext
        VantaActionSheetAction.ADD_TO_QUEUE -> Icons.AutoMirrored.Filled.QueueMusic
        VantaActionSheetAction.START_RADIO -> Icons.Filled.Radio
        VantaActionSheetAction.START_SONIC_RADIO -> Icons.Filled.GraphicEq
        VantaActionSheetAction.ADD_TO_LIBRARY -> Icons.Filled.LibraryAdd
        VantaActionSheetAction.REMOVE_FROM_LIBRARY -> Icons.Filled.LibraryAddCheck
        VantaActionSheetAction.FAVORITE -> Icons.Filled.FavoriteBorder
        VantaActionSheetAction.UNFAVORITE -> Icons.Filled.Favorite
        VantaActionSheetAction.ADD_TO_PLAYLIST -> Icons.AutoMirrored.Filled.PlaylistAdd
        VantaActionSheetAction.VIEW_ALBUM -> Icons.Filled.Album
        VantaActionSheetAction.VIEW_ARTIST -> Icons.Filled.Person
        VantaActionSheetAction.SHARE_TRACK,
        VantaActionSheetAction.SHARE_ALBUM,
        VantaActionSheetAction.SHARE_ARTIST,
        VantaActionSheetAction.SHARE_PLAYLIST -> Icons.Filled.Share
        VantaActionSheetAction.DOWNLOAD_LOCAL -> Icons.Filled.Download
        VantaActionSheetAction.SLEEP_TIMER -> Icons.Filled.Timer
        VantaActionSheetAction.REMOVE_FROM_QUEUE -> Icons.Filled.RemoveCircle
        VantaActionSheetAction.MOVE_QUEUE_ITEM -> Icons.Filled.ArrowUpward
        VantaActionSheetAction.SHUFFLE_QUEUE -> Icons.Filled.Shuffle
        VantaActionSheetAction.VIEW_FILE_INFO -> Icons.Filled.Info
    }

    val label = when (availability.action) {
        VantaActionSheetAction.PLAY -> "Play"
        VantaActionSheetAction.PLAY_NEXT -> "Play Next"
        VantaActionSheetAction.ADD_TO_QUEUE -> "Add to Queue"
        VantaActionSheetAction.START_RADIO -> "Start Radio"
        VantaActionSheetAction.START_SONIC_RADIO -> "Sonic Match Radio"
        VantaActionSheetAction.ADD_TO_LIBRARY -> "Add to Library"
        VantaActionSheetAction.REMOVE_FROM_LIBRARY -> "Remove from Library"
        VantaActionSheetAction.FAVORITE -> "Favorite"
        VantaActionSheetAction.UNFAVORITE -> "Unfavorite"
        VantaActionSheetAction.ADD_TO_PLAYLIST -> "Add to Playlist"
        VantaActionSheetAction.VIEW_ALBUM -> "View Album"
        VantaActionSheetAction.VIEW_ARTIST -> "View Artist"
        VantaActionSheetAction.SHARE_TRACK -> "Share Song"
        VantaActionSheetAction.SHARE_ALBUM -> "Share Album"
        VantaActionSheetAction.SHARE_ARTIST -> "Share Artist"
        VantaActionSheetAction.SHARE_PLAYLIST -> "Share Playlist"
        VantaActionSheetAction.DOWNLOAD_LOCAL -> "Download"
        VantaActionSheetAction.SLEEP_TIMER -> "Sleep Timer"
        VantaActionSheetAction.REMOVE_FROM_QUEUE -> "Remove from Queue"
        VantaActionSheetAction.MOVE_QUEUE_ITEM -> "Move Up Next"
        VantaActionSheetAction.SHUFFLE_QUEUE -> "Shuffle Queue"
        VantaActionSheetAction.VIEW_FILE_INFO -> "View File Info"
    }

    VantaSheetAction(
        icon = icon,
        label = label,
        subtitle = if (!availability.enabled) availability.reasonDisabled ?: "Unavailable" else null,
        onClick = {
            if (availability.enabled) {
                onDismiss()
                onActionSelected(availability.action)
            }
        },
        tint = if (availability.enabled) AppText else AppTextMuted
    )
}
