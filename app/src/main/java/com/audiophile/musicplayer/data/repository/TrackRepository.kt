package com.audiophile.musicplayer.data.repository

import android.util.Log
import com.audiophile.musicplayer.data.local.dao.TrackDao
import com.audiophile.musicplayer.data.source.ContentPurityFilter
import com.audiophile.musicplayer.data.source.isMusicContentAllowed
import com.audiophile.musicplayer.data.source.SelectedRecordingIdentity
import com.audiophile.musicplayer.data.source.SourceRegistry
import com.audiophile.musicplayer.data.source.SourceIdentityGate
import com.audiophile.musicplayer.data.local.entities.SourceType
import com.audiophile.musicplayer.data.local.entities.PlaylistTrackCrossRef
import com.audiophile.musicplayer.data.local.entities.TrackSource
import com.audiophile.musicplayer.data.local.entities.UnifiedTrack
import com.audiophile.musicplayer.data.local.entities.UnifiedTrackWithSources
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class SourceSelectionPolicy(
    val sourcePriority: List<SourceType> = DEFAULT_SOURCE_PRIORITY,
    val minimumBitrateKbps: Int = 0,
    val preferHigherBitrateWithinSameSource: Boolean = true,
    val selectedTitle: String? = null,
    val selectedArtist: String? = null,
    val selectedDurationMs: Long? = null,
    val selectedIsrc: String? = null,
    val preferredProviderId: String? = null,
    val preferredExternalTrackId: String? = null,
    val userQuery: String? = null
)

data class PlaybackSourceResolution(
    val trackId: Long,
    val orderedCandidates: List<TrackSource>
) {
    val primary: TrackSource?
        get() = orderedCandidates.firstOrNull()
}

val DEFAULT_SOURCE_PRIORITY = listOf(
    SourceType.LOCAL,
    SourceType.ADDON,
    SourceType.TORBOX,
    SourceType.YOUTUBE_MUSIC
)

class TrackRepository(private val trackDao: TrackDao) {

    /**
     * Adds a track to the database. If the track already exists (matched by artist and title),
     * it simply adds the new source to the existing track, preventing duplicates in the UI.
     */
    suspend fun addTrackSource(
        title: String,
        artist: String,
        album: String?,
        coverArtUrl: String?,
        sourceType: SourceType,
        streamUrl: String,
        bitrate: Int,
        localLibraryId: Long? = null,
        genre: String? = null,
        isrc: String? = null,
        durationMs: Long? = null,
        externalProviderId: String? = null,
        externalTrackId: String? = null,
        expiresAtMs: Long? = null
    ): Long = withContext(Dispatchers.IO) {
        if (ContentPurityFilter.isClearlyNonMusicContent(title, artist, album, durationMs)) {
            Log.w("VANTA_DB_TRUTH", "blocked_non_music_ingest title='$title' artist='$artist'")
            return@withContext -1L
        }
        val normalizedExpiresAtMs = normalizeSourceExpiry(streamUrl, expiresAtMs, externalProviderId)
        trackDao.findSourceByStreamUrl(streamUrl)?.let { existingSource ->
            trackDao.updateTrackMetadataIfMissing(
                trackId = existingSource.parentTrackId,
                albumName = album,
                coverArtUrl = coverArtUrl,
                genre = genre,
                isrc = isrc,
                durationMs = durationMs,
                localLibraryId = localLibraryId
            )
            val merged = existingSource.mergeIdentity(
                externalProviderId = externalProviderId,
                externalTrackId = externalTrackId,
                expiresAtMs = normalizedExpiresAtMs,
                streamUrl = streamUrl,
                bitrate = bitrate
            )
            if (merged != existingSource) {
                trackDao.updateTrackSource(merged)
                Log.d(
                    "VANTA_DB_TRUTH",
                    "patched_source_identity sourceId=${merged.sourceId} provider=${merged.externalProviderId} externalId=${merged.externalTrackId}"
                )
            }
            return@withContext existingSource.parentTrackId
        }

        // 1. Check if the song already exists
        val existingTrack = localLibraryId
            ?.let { trackDao.findTrackByLocalLibraryId(it) }
            ?: trackDao.findTrackByMetadata(artist, title)

        val trackId = if (existingTrack != null) {
            existingTrack.trackId
        } else {
            // 2. Doesn't exist, create it
            val newTrack = UnifiedTrack(
                title = title,
                artist = artist,
                albumName = album,
                coverArtUrl = coverArtUrl,
                genre = genre,
                localLibraryId = localLibraryId,
                isrc = isrc,
                durationMs = durationMs
            )
            val insertedId = trackDao.insertUnifiedTrack(newTrack)
            if (insertedId <= 0L) {
                // Race: another caller inserted the same track between our find and insert.
                trackDao.findTrackByMetadata(artist, title)?.trackId
                    ?: localLibraryId?.let { trackDao.findTrackByLocalLibraryId(it)?.trackId }
                    ?: run {
                        Log.w(
                            "VANTA_DB_TRUTH",
                            "skipped_duplicate_track artist=$artist title=$title localLibraryId=$localLibraryId"
                        )
                        return@withContext -1L
                    }
            } else {
                insertedId
            }
        }

        if (trackId <= 0L) {
            return@withContext trackId
        }

        trackDao.updateTrackMetadataIfMissing(
            trackId = trackId,
            albumName = album,
            coverArtUrl = coverArtUrl,
            genre = genre,
            isrc = isrc,
            durationMs = durationMs,
            localLibraryId = localLibraryId
        )

        // 3. Add the stream source to this track
        val existingSources = trackDao.getTrackWithSourcesById(trackId)?.sources.orEmpty()
        val duplicateSource = existingSources.firstOrNull { source ->
            source.sourceType == sourceType && source.streamUrl == streamUrl
        }
        if (duplicateSource != null) {
            val merged = duplicateSource.mergeIdentity(
                externalProviderId = externalProviderId,
                externalTrackId = externalTrackId,
                expiresAtMs = normalizedExpiresAtMs,
                streamUrl = streamUrl,
                bitrate = bitrate
            )
            if (merged != duplicateSource) {
                trackDao.updateTrackSource(merged)
            }
        } else {
            val source = TrackSource(
                parentTrackId = trackId,
                sourceType = sourceType,
                streamUrl = streamUrl,
                bitrate = bitrate,
                externalProviderId = externalProviderId,
                externalTrackId = externalTrackId,
                expiresAtMs = normalizedExpiresAtMs
            )
            // FK validation: verify parent track exists before inserting source
            val parentExists = trackDao.trackExists(trackId)
            if (!parentExists) {
                Log.w("VANTA_DB_TRUTH", "skipped_child_missing_parent trackId=$trackId artist=$artist title=$title")
                return@withContext trackId
            }
            trackDao.insertTrackSource(source)
        }

        // Patch gateway identity onto any ADDON row that is missing provider/track IDs.
        if (!externalProviderId.isNullOrBlank() && !externalTrackId.isNullOrBlank()) {
            existingSources.filter { existing ->
                existing.sourceType == sourceType &&
                    (existing.externalProviderId.isNullOrBlank() || existing.externalTrackId.isNullOrBlank())
            }.forEach { existing ->
                val merged = existing.mergeIdentity(
                    externalProviderId = externalProviderId,
                    externalTrackId = externalTrackId,
                    expiresAtMs = normalizedExpiresAtMs
                )
                if (merged != existing) {
                    trackDao.updateTrackSource(merged)
                }
            }
        }

        trackId
    }

    private fun TrackSource.mergeIdentity(
        externalProviderId: String?,
        externalTrackId: String?,
        expiresAtMs: Long? = null,
        streamUrl: String? = null,
        bitrate: Int? = null
    ): TrackSource = copy(
        externalProviderId = externalProviderId?.takeIf { it.isNotBlank() } ?: this.externalProviderId,
        externalTrackId = externalTrackId?.takeIf { it.isNotBlank() } ?: this.externalTrackId,
        expiresAtMs = expiresAtMs ?: this.expiresAtMs,
        streamUrl = streamUrl?.takeIf { it.isNotBlank() } ?: this.streamUrl,
        bitrate = bitrate?.takeIf { it > 0 } ?: this.bitrate
    )

    suspend fun updateSource(source: TrackSource) = withContext(Dispatchers.IO) {
        trackDao.updateTrackSource(
            source.copy(
                expiresAtMs = normalizeSourceExpiry(
                    streamUrl = source.streamUrl,
                    expiresAtMs = source.expiresAtMs,
                    providerId = source.externalProviderId
                )
            )
        )
    }

    private fun normalizeSourceExpiry(
        streamUrl: String,
        expiresAtMs: Long?,
        providerId: String?
    ): Long? = SourceRegistry.resolveMinExpiryMs(streamUrl, expiresAtMs, providerId)

    suspend fun updateCoverArtIfMissing(trackId: Long, coverArtUrl: String?) = withContext(Dispatchers.IO) {
        if (coverArtUrl != null && coverArtUrl.startsWith("http")) {
            trackDao.updateCoverArtIfMissing(trackId, coverArtUrl)
        }
    }

    suspend fun updateLastPlayedAt(trackId: Long) = withContext(Dispatchers.IO) {
        trackDao.updateLastPlayedAt(trackId, System.currentTimeMillis())
        Log.d("VANTA_HISTORY", "updated trackId=$trackId lastPlayedAt=${System.currentTimeMillis()}")
    }

    suspend fun getRecentlyPlayed(limit: Int = 20): List<UnifiedTrackWithSources> = withContext(Dispatchers.IO) {
        trackDao.getRecentlyPlayedWithSources(limit.coerceAtLeast(20) * 3)
            .filter { it.isMusicContentAllowed() }
            .take(limit)
    }

    suspend fun getRecentTrackIds(limit: Int = 10): List<Long> = withContext(Dispatchers.IO) {
        trackDao.getRecentTrackIds(limit)
    }

    /**
     * Retrieves all deduplicated tracks with their sources.
     */
    suspend fun getAllTracks(): List<UnifiedTrackWithSources> = withContext(Dispatchers.IO) {
        trackDao.getAllTracksWithSources().filter { it.isMusicContentAllowed() }
    }

    suspend fun getTrackCount(): Int = withContext(Dispatchers.IO) {
        trackDao.getTrackCount()
    }

    suspend fun getAllAlbumsWithTracks(): List<com.audiophile.musicplayer.data.local.entities.AlbumWithTracks> = withContext(Dispatchers.IO) {
        trackDao.getAllAlbumsWithTracks()
    }

    suspend fun searchLibrary(query: String, limit: Int = 25): List<UnifiedTrackWithSources> = withContext(Dispatchers.IO) {
        if (query.isBlank()) return@withContext emptyList()
        trackDao.searchTracksWithSources(query.trim(), limit.coerceAtLeast(25) * 2)
            .filter { it.isMusicContentAllowed() }
            .take(limit)
    }

    suspend fun getTrackWithSources(trackId: Long): UnifiedTrackWithSources? = withContext(Dispatchers.IO) {
        trackDao.getTrackWithSourcesById(trackId)
    }

    suspend fun getTrackById(trackId: Long): UnifiedTrack? = withContext(Dispatchers.IO) {
        trackDao.getTrackWithSourcesById(trackId)?.track
    }
    
    /**
     * Gets a specific track and automatically sorts its sources by highest bitrate first.
     * Use this when you are about to hit "Play" to ensure you get the FLAC/highest quality.
     */
    suspend fun getBestQualitySourcesForTrack(trackId: Long): List<TrackSource>? = withContext(Dispatchers.IO) {
        val trackWithSources = trackDao.getTrackWithSourcesById(trackId)
        trackWithSources?.sources?.sortedByDescending { it.bitrate }
    }

    /**
     * Returns a deterministic source ordering for playback with configurable policy.
     *
     * Recommended usage:
     *  1. Resolve once when user presses play.
     *  2. Attempt `resolution.primary`.
     *  3. If playback fails, call [getNextFallbackSource] with failed source IDs.
     */
    suspend fun resolvePlaybackSourcesForTrack(
        trackId: Long,
        policy: SourceSelectionPolicy = SourceSelectionPolicy()
    ): PlaybackSourceResolution? = withContext(Dispatchers.IO) {
        val trackWithSources = trackDao.getTrackWithSourcesById(trackId) ?: return@withContext null
        val identity = SelectedRecordingIdentity(
            title = policy.selectedTitle ?: trackWithSources.track.title,
            artist = policy.selectedArtist ?: trackWithSources.track.artist,
            durationMs = policy.selectedDurationMs ?: trackWithSources.track.durationMs,
            isrc = policy.selectedIsrc ?: trackWithSources.track.isrc,
            preferredProviderId = policy.preferredProviderId,
            preferredExternalTrackId = policy.preferredExternalTrackId,
            userQuery = policy.userQuery
        )
        val ordered = orderSources(trackWithSources.sources, policy, identity)
        PlaybackSourceResolution(trackId = trackId, orderedCandidates = ordered)
    }

    /**
     * Helper for fallback retries.
     */
    fun getNextFallbackSource(
        resolution: PlaybackSourceResolution,
        failedSourceIds: Set<Long>,
        unsupportedSourceIds: Set<Long> = emptySet()
    ): TrackSource? {
        return resolution.orderedCandidates.firstOrNull { it.sourceId !in failedSourceIds && it.sourceId !in unsupportedSourceIds }
    }
    
    /**
     * Adds an entire album (and all its tracks) to the user's library.
     */
    suspend fun addAlbumToLibrary(
        albumName: String,
        artistName: String,
        coverArtUrl: String?,
        releaseYear: Int?,
        tracks: List<UnifiedTrack>
    ): Long = withContext(Dispatchers.IO) {
        // 1. Create the Album descriptor
        val album = com.audiophile.musicplayer.data.local.entities.Album(
            album_name = albumName,
            artist_name = artistName,
            cover_art_url = coverArtUrl,
            release_year = releaseYear
        )
        var albumId = trackDao.insertAlbum(album)
        if (albumId <= 0L) {
            albumId = trackDao.findAlbumId(albumName, artistName) ?: return@withContext -1L
        }
        
        // 2. Insert all tracks belonging to this album
        tracks.forEach { track ->
            val trackWithAlbum = track.copy(albumId = albumId, albumName = albumName, coverArtUrl = coverArtUrl)
            trackDao.insertUnifiedTrack(trackWithAlbum)
        }
        
        albumId
    }

    suspend fun addTrackToPlaylist(playlistId: Long, trackId: Long): Unit = withContext(Dispatchers.IO) {
        val nextPosition = trackDao.getLastPlaylistPosition(playlistId) + 1
        trackDao.upsertPlaylistTrackCrossRef(
            PlaylistTrackCrossRef(
                playlistId = playlistId,
                trackId = trackId,
                position = nextPosition
            )
        )
    }

    suspend fun removeTrackFromPlaylist(playlistId: Long, trackId: Long): Unit = withContext(Dispatchers.IO) {
        trackDao.removeTrackFromPlaylist(playlistId, trackId)
    }

    suspend fun getOrderedPlaylistTracks(playlistId: Long): List<UnifiedTrack> = withContext(Dispatchers.IO) {
        trackDao.getOrderedTracksForPlaylist(playlistId)
    }

    suspend fun moveTrackWithinPlaylist(
        playlistId: Long,
        fromIndex: Int,
        toIndex: Int
    ): List<UnifiedTrack> = withContext(Dispatchers.IO) {
        val tracks = trackDao.getOrderedTracksForPlaylist(playlistId).toMutableList()
        if (fromIndex !in tracks.indices || toIndex !in tracks.indices) {
            return@withContext tracks
        }

        val moved = tracks.removeAt(fromIndex)
        tracks.add(toIndex, moved)

        tracks.forEachIndexed { idx, track ->
            trackDao.updatePlaylistTrackPosition(
                playlistId = playlistId,
                trackId = track.trackId,
                newPosition = idx
            )
        }
        tracks
    }

    /**
     * Removes poisoned database rows: SoundHelix streams attached to non-demo tracks,
     * migrates HTTP LOCAL sources to ADDON type, and purges YouTube live-stream junk.
     */
    suspend fun cleanupPoisonedData() = withContext(Dispatchers.IO) {
        trackDao.migrateLocalHttpToAddon()
        trackDao.deletePoisonedSources()
        cleanupJunkLibraryTracks()
    }

    /** One-time startup purge of YouTube live streams and 24/7 radio junk in the local library. */
    suspend fun cleanupJunkLibraryTracks(): Int = withContext(Dispatchers.IO) {
        val all = trackDao.getAllTracksWithSources()
        var deleted = 0
        for (trackWithSources in all) {
            val track = trackWithSources.track
            if (!ContentPurityFilter.isAllowed(
                    title = track.title,
                    artist = track.artist,
                    album = track.albumName,
                    durationMs = track.durationMs,
                    source = trackWithSources.sources.firstOrNull()?.externalProviderId
                )
            ) {
                trackDao.deleteSourcesForTrack(track.trackId)
                trackDao.deletePlaylistRefsForTrack(track.trackId)
                trackDao.deleteUnifiedTrack(track.trackId)
                deleted++
                Log.i(
                    "VANTA_JUNK_CLEANUP",
                    "Removed junk track: '${track.title}' by '${track.artist}'"
                )
            }
        }
        deleted
    }

    private fun orderSources(
        sources: List<TrackSource>,
        policy: SourceSelectionPolicy,
        identity: SelectedRecordingIdentity
    ): List<TrackSource> {
        val priorityIndex = policy.sourcePriority
            .withIndex()
            .associate { (index, type) -> type to index }

        val hasPreferredIdentity = !identity.preferredProviderId.isNullOrBlank() &&
            !identity.preferredExternalTrackId.isNullOrBlank()

        fun isPreferredSource(source: TrackSource): Boolean =
            hasPreferredIdentity &&
                source.externalProviderId == identity.preferredProviderId &&
                source.externalTrackId == identity.preferredExternalTrackId

        return sources
            .asSequence()
            .filter { it.streamUrl.isNotBlank() }
            .filter { it.sourceType != SourceType.APPLE_MUSIC && it.sourceType != SourceType.SPOTIFY }
            .filter { it.bitrate >= policy.minimumBitrateKbps }
            .filter { source ->
                if (!hasPreferredIdentity) return@filter true
                when {
                    isPreferredSource(source) -> true
                    source.sourceType == SourceType.LOCAL -> true
                    source.sourceType == SourceType.ADDON -> false
                    else -> true
                }
            }
            .sortedWith(
                compareBy<TrackSource> { source ->
                    when {
                        isPreferredSource(source) -> 0
                        source.sourceType == SourceType.LOCAL -> 1
                        else -> 2
                    }
                }
                    .thenBy { priorityIndex[it.sourceType] ?: Int.MAX_VALUE }
                    .thenComparator { a, b ->
                        if (!policy.preferHigherBitrateWithinSameSource) return@thenComparator 0
                        b.bitrate.compareTo(a.bitrate)
                    }
                    .thenBy { it.sourceId }
            )
            .toList()
            .also { ordered ->
                ordered.firstOrNull()?.let { primary ->
                    SourceIdentityGate.logSelected(
                        title = identity.title,
                        artist = identity.artist,
                        provider = primary.externalProviderId,
                        variantType = com.audiophile.musicplayer.data.source.VariantClassifier.classify(
                            identity.title, identity.artist
                        ).variantType,
                        score = primary.bitrate,
                        identityConfidence = if (isPreferredSource(primary)) 1.0f else 0.7f
                    )
                }
            }
    }
}
