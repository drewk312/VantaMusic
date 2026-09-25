package com.audiophile.musicplayer.common

import android.util.Log
import java.util.concurrent.atomic.AtomicLong

/**
 * Phase 1.5 runtime-truth acceptance logs.
 *
 * Emits structured, low-frequency identity evidence for on-device gates.
 * Do not call from per-frame Compose draw paths without signature/throttle guards.
 */
object AcceptanceTruth {
    private val generationSeq = AtomicLong(0L)

    @Volatile
    var activeGeneration: Long = 0L
        private set

    private var lastMiniSig: String? = null
    private var lastNowPlayingSig: String? = null
    private var lastPositionTrackId: String? = null
    private var lastPositionMs: Long = -1L
    private var lastPositionPlaying: Boolean? = null
    private var lastPositionSampleAtMs: Long = 0L

    private var radioSeedTitle: String? = null
    private var radioPlayedCount: Int = 0
    private var radioFailedTransitions: Int = 0
    private val radioCanonicalKeys = linkedSetOf<String>()
    private val radioArtists = linkedSetOf<String>()
    private var radioSeedArtist: String? = null
    private var radioSeedArtistCount: Int = 0
    private var radioDuplicateCanonicalCount: Int = 0
    private var radioLastCanonicalKey: String? = null

    fun nextGeneration(): Long {
        val gen = generationSeq.incrementAndGet()
        activeGeneration = gen
        return gen
    }

    fun bindGeneration(generation: Long) {
        if (generation > 0L) activeGeneration = generation
    }

    fun search(
        query: String,
        title: String?,
        artist: String?,
        album: String?,
        isrc: String?,
        providerId: String?,
        externalTrackId: String?,
        artwork: String?,
        durationMs: Long?,
        canonicalTrackId: String? = null,
        canonicalArtistId: String? = null,
        canonicalAlbumId: String? = null
    ) {
        Log.i(
            "VANTA_ACCEPTANCE_SEARCH",
            "query='${query.take(80)}' " +
                "title='${title.orEmpty()}' " +
                "artist='${artist.orEmpty()}' " +
                "album='${album.orEmpty()}' " +
                "isrc='${isrc.orEmpty()}' " +
                "providerId='${providerId.orEmpty()}' " +
                "externalTrackId='${externalTrackId.orEmpty()}' " +
                "artwork='${artworkHost(artwork)}' " +
                "durationMs=${durationMs ?: -1} " +
                "canonicalTrackId=${canonicalTrackId.orEmpty()} " +
                "canonicalArtistId=${canonicalArtistId.orEmpty()} " +
                "canonicalAlbumId=${canonicalAlbumId.orEmpty()}"
        )
    }

    fun graph(
        event: String,
        title: String? = null,
        artist: String? = null,
        album: String? = null,
        canonicalTrackId: Long? = null,
        canonicalArtistId: Long? = null,
        canonicalAlbumId: Long? = null,
        method: String? = null
    ) {
        Log.i(
            "VANTA_ACCEPTANCE_GRAPH",
            "event='$event' " +
                "title='${title.orEmpty()}' " +
                "artist='${artist.orEmpty()}' " +
                "album='${album.orEmpty()}' " +
                "canonicalTrackId=${canonicalTrackId ?: -1} " +
                "canonicalArtistId=${canonicalArtistId ?: -1} " +
                "canonicalAlbumId=${canonicalAlbumId ?: -1} " +
                "method='${method.orEmpty()}'"
        )
    }

    fun handoff(
        generation: Long,
        searchTitle: String?,
        searchArtist: String?,
        searchIsrc: String?,
        preferredProvider: String?,
        preferredExternalId: String?,
        resolvedTrackId: Long?,
        album: String? = null
    ) {
        bindGeneration(generation)
        Log.i(
            "VANTA_ACCEPTANCE_HANDOFF",
            "generation=$generation " +
                "searchTitle='${searchTitle.orEmpty()}' " +
                "searchArtist='${searchArtist.orEmpty()}' " +
                "searchIsrc='${searchIsrc.orEmpty()}' " +
                "album='${album.orEmpty()}' " +
                "preferredProvider='${preferredProvider.orEmpty()}' " +
                "preferredExternalId='${preferredExternalId.orEmpty()}' " +
                "resolvedTrackId=${resolvedTrackId ?: -1}"
        )
    }

    fun active(
        generation: Long,
        trackId: Long?,
        title: String?,
        artist: String?,
        album: String?,
        isrc: String?,
        preferredProvider: String? = null,
        preferredExternalId: String? = null
    ) {
        bindGeneration(generation)
        Log.i(
            "VANTA_ACCEPTANCE_ACTIVE",
            "generation=$generation " +
                "trackId=${trackId ?: -1} " +
                "title='${title.orEmpty()}' " +
                "artist='${artist.orEmpty()}' " +
                "album='${album.orEmpty()}' " +
                "isrc='${isrc.orEmpty()}' " +
                "preferredProvider='${preferredProvider.orEmpty()}' " +
                "preferredExternalId='${preferredExternalId.orEmpty()}'"
        )
    }

    fun mini(
        trackId: String?,
        title: String?,
        artist: String?,
        isPlaying: Boolean,
        positionMs: Long,
        durationMs: Long,
        artworkPresent: Boolean
    ) {
        val sig = "$trackId|$title|$artist|$isPlaying|$artworkPresent|${positionMs / 2000L}"
        if (sig == lastMiniSig) return
        lastMiniSig = sig
        Log.i(
            "VANTA_ACCEPTANCE_MINI",
            "trackId='${trackId.orEmpty()}' " +
                "title='${title.orEmpty()}' " +
                "artist='${artist.orEmpty()}' " +
                "isPlaying=$isPlaying " +
                "positionMs=$positionMs " +
                "durationMs=$durationMs " +
                "artworkPresent=$artworkPresent " +
                "generation=$activeGeneration"
        )
    }

    fun nowPlaying(
        trackId: String?,
        title: String?,
        artist: String?,
        album: String?,
        isrc: String?,
        isPlaying: Boolean,
        positionMs: Long,
        durationMs: Long,
        preferredProvider: String?,
        preferredExternalId: String?,
        artworkPresent: Boolean
    ) {
        val sig = "$trackId|$title|$artist|$album|$isPlaying|$preferredProvider|$preferredExternalId"
        if (sig == lastNowPlayingSig) return
        lastNowPlayingSig = sig
        Log.i(
            "VANTA_ACCEPTANCE_NOWPLAYING",
            "trackId='${trackId.orEmpty()}' " +
                "title='${title.orEmpty()}' " +
                "artist='${artist.orEmpty()}' " +
                "album='${album.orEmpty()}' " +
                "isrc='${isrc.orEmpty()}' " +
                "isPlaying=$isPlaying " +
                "positionMs=$positionMs " +
                "durationMs=$durationMs " +
                "preferredProvider='${preferredProvider.orEmpty()}' " +
                "preferredExternalId='${preferredExternalId.orEmpty()}' " +
                "artworkPresent=$artworkPresent " +
                "generation=$activeGeneration"
        )
    }

    fun lyrics(
        trackId: String?,
        title: String?,
        artist: String?,
        album: String?,
        isrc: String?,
        provider: String?,
        lyricsMatchMethod: String,
        available: Boolean
    ) {
        Log.i(
            "VANTA_ACCEPTANCE_LYRICS",
            "trackId='${trackId.orEmpty()}' " +
                "title='${title.orEmpty()}' " +
                "artist='${artist.orEmpty()}' " +
                "album='${album.orEmpty()}' " +
                "isrc='${isrc.orEmpty()}' " +
                "provider='${provider.orEmpty()}' " +
                "lyricsMatchMethod='$lyricsMatchMethod' " +
                "available=$available " +
                "generation=$activeGeneration"
        )
    }

    fun artist(
        artist: String?,
        navigationMode: String,
        seedTrackId: String?,
        activePlaybackTrackId: String?,
        artistId: String? = null
    ) {
        Log.i(
            "VANTA_ACCEPTANCE_ARTIST",
            "artist='${artist.orEmpty()}' " +
                "navigationMode='$navigationMode' " +
                "artistId='${artistId.orEmpty()}' " +
                "seedTrackId='${seedTrackId.orEmpty()}' " +
                "activePlaybackTrackId='${activePlaybackTrackId.orEmpty()}' " +
                "generation=$activeGeneration"
        )
    }

    fun album(
        album: String?,
        artist: String?,
        seedTrackId: String?,
        navigationMode: String,
        albumId: String? = null
    ) {
        Log.i(
            "VANTA_ACCEPTANCE_ALBUM",
            "album='${album.orEmpty()}' " +
                "artist='${artist.orEmpty()}' " +
                "seedTrackId='${seedTrackId.orEmpty()}' " +
                "navigationMode='$navigationMode' " +
                "albumId='${albumId.orEmpty()}' " +
                "generation=$activeGeneration"
        )
    }

    fun position(
        trackId: String?,
        positionMs: Long,
        durationMs: Long,
        isPlaying: Boolean,
        reason: String
    ) {
        val now = System.currentTimeMillis()
        val trackChanged = trackId != lastPositionTrackId
        val playChanged = lastPositionPlaying != null && lastPositionPlaying != isPlaying
        val jump = lastPositionMs >= 0L && kotlin.math.abs(positionMs - lastPositionMs) >= 2_000L
        val sampleDue = isPlaying && (now - lastPositionSampleAtMs) >= 2_000L
        if (!trackChanged && !playChanged && !jump && !sampleDue && reason == "poll") return

        lastPositionTrackId = trackId
        lastPositionMs = positionMs
        lastPositionPlaying = isPlaying
        if (sampleDue || reason != "poll") lastPositionSampleAtMs = now

        Log.i(
            "VANTA_POSITION_TRUTH",
            "trackId='${trackId.orEmpty()}' " +
                "positionMs=$positionMs " +
                "durationMs=$durationMs " +
                "isPlaying=$isPlaying " +
                "reason='$reason' " +
                "generation=$activeGeneration"
        )
    }

    fun libraryAction(
        trackId: String?,
        isrc: String?,
        title: String?,
        artist: String?,
        before: Boolean,
        requestedAction: String,
        after: Boolean?,
        matchedBy: String
    ) {
        Log.i(
            "VANTA_LIBRARY_ACTION",
            "trackId='${trackId.orEmpty()}' " +
                "isrc='${isrc.orEmpty()}' " +
                "title='${title.orEmpty()}' " +
                "artist='${artist.orEmpty()}' " +
                "before=$before " +
                "requestedAction='$requestedAction' " +
                "after=${after?.toString() ?: "pending"} " +
                "matchedBy='$matchedBy'"
        )
    }

    fun radioSeed(
        seedTrackId: Long?,
        seedTitle: String?,
        seedArtist: String?,
        queueSize: Int,
        queueIndex: Int,
        firstTitle: String?
    ) {
        radioSeedTitle = seedTitle
        radioSeedArtist = seedArtist?.trim()?.lowercase()
        radioPlayedCount = 0
        radioFailedTransitions = 0
        radioCanonicalKeys.clear()
        radioArtists.clear()
        radioSeedArtistCount = 0
        radioDuplicateCanonicalCount = 0
        radioLastCanonicalKey = null
        Log.i(
            "VANTA_RADIO_TRUTH",
            "seedTrackId=${seedTrackId ?: -1} " +
                "seedTitle='${seedTitle.orEmpty()}' " +
                "seedArtist='${seedArtist.orEmpty()}' " +
                "queueSize=$queueSize " +
                "queueIndex=$queueIndex " +
                "firstTitle='${firstTitle.orEmpty()}'"
        )
        noteRadioTransition(
            title = firstTitle,
            artist = seedArtist,
            isrc = null,
            failed = false
        )
    }

    fun noteRadioTransition(
        title: String?,
        artist: String?,
        isrc: String?,
        failed: Boolean
    ) {
        if (failed) {
            radioFailedTransitions++
            return
        }
        radioPlayedCount++
        val key = canonicalKey(isrc, title, artist)
        if (!radioCanonicalKeys.add(key)) {
            radioDuplicateCanonicalCount++
        }
        val artistKey = artist?.trim()?.lowercase().orEmpty()
        if (artistKey.isNotBlank()) {
            radioArtists.add(artistKey)
            if (artistKey == radioSeedArtist) radioSeedArtistCount++
        }
        radioLastCanonicalKey = key
    }

    fun radioSummary() {
        Log.i(
            "VANTA_ACCEPTANCE_RADIO_SUMMARY",
            "seed='${radioSeedTitle.orEmpty()}' " +
                "playedCount=$radioPlayedCount " +
                "uniqueCanonicalTracks=${radioCanonicalKeys.size} " +
                "uniqueArtists=${radioArtists.size} " +
                "seedArtistCount=$radioSeedArtistCount " +
                "duplicateCanonicalCount=$radioDuplicateCanonicalCount " +
                "failedTransitions=$radioFailedTransitions " +
                "lastCanonical='${radioLastCanonicalKey.orEmpty()}'"
        )
    }

    fun trackSwitch(
        oldTrackId: Long?,
        newTrackId: Long?,
        generation: Long,
        queueIndex: Int,
        title: String? = null,
        artist: String? = null
    ) {
        Log.i(
            "VANTA_TRACK_TRUTH",
            "oldTrackId=${oldTrackId ?: -1} " +
                "newTrackId=${newTrackId ?: -1} " +
                "generation=$generation " +
                "queueIndex=$queueIndex " +
                "title='${title.orEmpty()}' " +
                "artist='${artist.orEmpty()}'"
        )
    }

    private fun canonicalKey(isrc: String?, title: String?, artist: String?): String {
        val cleanIsrc = isrc?.trim()?.takeIf { it.isNotBlank() }
        if (cleanIsrc != null) return "isrc:${cleanIsrc.uppercase()}"
        return "ta:${title.orEmpty().trim().lowercase()}|${artist.orEmpty().trim().lowercase()}"
    }

    private fun artworkHost(value: String?): String {
        if (value.isNullOrBlank()) return ""
        return VantaLogger.urlHost(value)
    }
}
