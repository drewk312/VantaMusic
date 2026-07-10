package com.audiophile.musicplayer.data.source

import android.util.Log
import com.audiophile.musicplayer.data.canonical.CanonicalTrack

enum class SearchItemStatus {
    METADATA_ONLY,        // Metadata match only, no source
    SOURCE_FOUND,         // Source match exists but stream not yet validated
    VALIDATING,           // Currently resolving/validating stream
    VALIDATED_PLAYABLE,   // Stream validated and confirmed playable
    LOCAL_PLAYABLE,       // Local file with valid path
    PREVIEW,              // Short preview available
    DEMO_ONLY,            // Demo track with demo stream
    ENHANCED,             // Metadata enriched but no playable source
    STREAM_UNAVAILABLE,   // Stream validation failed
    NOT_PLAYABLE          // Explicitly not playable
}

/** Unified source-of-truth for whether a track is confirmed playable (stream validated or local file verified).
 * MiniPlayer visibility and other "actually playing" UI checks must use this. */
fun SearchItemStatus.isConfirmedPlayable(): Boolean =
    this == SearchItemStatus.VALIDATED_PLAYABLE ||
    this == SearchItemStatus.LOCAL_PLAYABLE ||
    this == SearchItemStatus.PREVIEW ||
    this == SearchItemStatus.DEMO_ONLY

/** Tracks that can enter the playback flow (resolve + validate + play).
 * PlayerController.playQueue() and play buttons should accept this. */
fun SearchItemStatus.canEnterPlaybackFlow(): Boolean =
    this == SearchItemStatus.SOURCE_FOUND ||
    this == SearchItemStatus.VALIDATING ||
    this == SearchItemStatus.VALIDATED_PLAYABLE ||
    this == SearchItemStatus.LOCAL_PLAYABLE ||
    this == SearchItemStatus.PREVIEW ||
    this == SearchItemStatus.DEMO_ONLY

fun SearchItemStatus.canResolveStream(): Boolean =
    this == SearchItemStatus.SOURCE_FOUND ||
    this == SearchItemStatus.VALIDATED_PLAYABLE ||
    this == SearchItemStatus.LOCAL_PLAYABLE ||
    this == SearchItemStatus.PREVIEW ||
    this == SearchItemStatus.DEMO_ONLY

fun SearchItemStatus.isMetadataOnly(): Boolean =
    this == SearchItemStatus.METADATA_ONLY ||
    this == SearchItemStatus.ENHANCED

fun SearchItemStatus.isUnavailable(): Boolean =
    this == SearchItemStatus.STREAM_UNAVAILABLE ||
    this == SearchItemStatus.NOT_PLAYABLE

fun SearchItemStatus.isUnvalidated(): Boolean =
    this == SearchItemStatus.SOURCE_FOUND

fun SearchItemStatus.isValidating(): Boolean =
    this == SearchItemStatus.VALIDATING

/**
 * Simplified user-facing label for search result badges.
 * Returns null when no badge is needed (e.g. Play button is sufficient).
 * Returns a label string when a status badge should be shown.
 */
fun SearchItemStatus.userFacingLabel(): String? = when {
    this == SearchItemStatus.PREVIEW -> "Preview"
    this == SearchItemStatus.DEMO_ONLY -> "Demo"
    this.isMetadataOnly() -> "View"
    this.isUnavailable() -> "Unavailable"
    this == SearchItemStatus.VALIDATED_PLAYABLE || this == SearchItemStatus.LOCAL_PLAYABLE -> "Song"
    this == SearchItemStatus.SOURCE_FOUND || this == SearchItemStatus.VALIDATING -> "Track"
    else -> null
}

/**
 * Whether this status should show a green/success badge.
 * Only true for confirmed playable (stream validated or local file).
 * SOURCE_FOUND and VALIDATING are NOT confirmed — they get neutral styling.
 */
fun SearchItemStatus.isConfirmedSuccess(): Boolean =
    this == SearchItemStatus.VALIDATED_PLAYABLE ||
    this == SearchItemStatus.LOCAL_PLAYABLE

fun com.audiophile.musicplayer.data.local.entities.UnifiedTrackWithSources.sourceValidityStatus(): SearchItemStatus {
    val source = sources.firstOrNull {
        it.streamUrl.isNotBlank() &&
            it.sourceType != com.audiophile.musicplayer.data.local.entities.SourceType.APPLE_MUSIC &&
            it.sourceType != com.audiophile.musicplayer.data.local.entities.SourceType.SPOTIFY
    }
    return when {
        source == null -> SearchItemStatus.METADATA_ONLY
        source.sourceType == com.audiophile.musicplayer.data.local.entities.SourceType.LOCAL -> SearchItemStatus.LOCAL_PLAYABLE
        source.sourceType == com.audiophile.musicplayer.data.local.entities.SourceType.TORBOX -> SearchItemStatus.SOURCE_FOUND
        source.sourceType == com.audiophile.musicplayer.data.local.entities.SourceType.ADDON -> SearchItemStatus.SOURCE_FOUND
        else -> SearchItemStatus.SOURCE_FOUND
    }
}

fun com.audiophile.musicplayer.data.local.entities.UnifiedTrackWithSources.isConfirmedPlayable(): Boolean =
    sourceValidityStatus().isConfirmedPlayable()

data class SourceSearchResult(
    val id: String,
    val providerId: String,
    val title: String,
    val artist: String,
    val album: String?,
    val coverSeed: String,
    val durationMs: Long?,
    val isrc: String? = null,
    val status: SearchItemStatus,
    val qualityLabel: String?,
    val featuredArtists: List<String> = emptyList(),
    val isDolbyAtmos: Boolean = false,
    val isSpatialAudio: Boolean = false,
    val isSurround: Boolean = false,
    val isHiRes: Boolean = false
) {
    val artworkUrl: String?
        get() {
            val seed = coverSeed
            if (seed.startsWith("http://") || seed.startsWith("https://")) return seed
            return null
        }
}

fun SourceSearchResult.isLikelyMusicTrack(): Boolean {
    val titleLower = title.lowercase()
    val artistLower = artist.lowercase()

    if (!ContentPurityFilter.isAllowed(title, artist, album, durationMs, providerId)) {
        Log.d("VANTA_SEARCH_FILTER", "filtered_non_music title='$title' artist='$artist' reason=content_purity provider=$providerId")
        return false
    }

    if (isBroadcastLikeMetadata(title, artist, album)) {
        Log.d("VANTA_SEARCH_FILTER", "filtered_non_music title='$title' artist='$artist' reason=continuous_broadcast provider=$providerId")
        return false
    }

    if (isSportsOrVideoMetadata(title, artist, album)) {
        Log.d("VANTA_SEARCH_FILTER", "filtered_non_music title='$title' artist='$artist' reason=sports_or_video provider=$providerId")
        return false
    }

    val nonMusicKeywords = listOf(
        "compilation", "full match", "interview", "documentary", "podcast", "reaction",
        "hour", "hours", "official trailer", "news", "episode", "drone catches",
        "learn", "alphabet", "cartel", "watch mojo", "watchmojo", "top 10",
        "things to do", "overview", "guide", "local guide",
        "homes.com", "airport", "FAA", "airspace",
        "shutdown", "travel guide", "city", "tacos",
        "bucket list", "frontline", "bloomberg"
    )
    val hasNonMusicKeyword = nonMusicKeywords.any { titleLower.contains(it) || artistLower.contains(it) }

    val hasMusicSignals = isrc != null ||
        (album != null && durationMs != null && durationMs in 60000L..720000L)

    if (hasNonMusicKeyword && !hasMusicSignals) {
        Log.d("VANTA_SEARCH_FILTER", "filtered_non_music title='$title' artist='$artist' reason=non_music_keyword provider=$providerId")
        return false
    }

    if (durationMs != null && (durationMs < 30000L || durationMs > 900000L)) {
        Log.d("VANTA_SEARCH_FILTER", "filtered_non_music title='$title' artist='$artist' reason=bad_duration durationMs=$durationMs")
        return false
    }

    if (artist.isBlank() && isrc == null) {
        Log.d("VANTA_SEARCH_FILTER", "filtered_non_music title='$title' artist='$artist' reason=missing_artist_no_isrc provider=$providerId")
        return false
    }

    Log.d("VANTA_SEARCH_FILTER", "ranked_music_candidate title='$title' artist='$artist' provider=$providerId isrc=${isrc != null} album=${album != null} durationMs=$durationMs")
    return true
}

fun CanonicalTrack.isLikelyMusicTrack(): Boolean {
    val titleLower = title.lowercase()
    val artistLower = artist.lowercase()

    if (!ContentPurityFilter.isAllowed(title, artist, album, durationMs, sourceProviderId)) {
        Log.d("VANTA_SEARCH_FILTER", "filtered_non_music title='$title' artist='$artist' reason=content_purity canonical=true")
        return false
    }

    if (isBroadcastLikeMetadata(title, artist, album)) {
        Log.d("VANTA_SEARCH_FILTER", "filtered_non_music title='$title' artist='$artist' reason=continuous_broadcast canonical=true")
        return false
    }


    if (isSportsOrVideoMetadata(title, artist, album)) {
        Log.d("VANTA_SEARCH_FILTER", "filtered_non_music title='$title' artist='$artist' reason=sports_or_video canonical=true")
        return false
    }

    val nonMusicKeywords = listOf(
        "compilation", "full match", "interview", "documentary", "podcast", "reaction",
        "hour", "hours", "official trailer", "news", "episode", "drone catches",
        "learn", "alphabet", "cartel", "watch mojo", "watchmojo", "top 10",
        "things to do", "overview", "guide", "local guide",
        "homes.com", "airport", "FAA", "airspace",
        "shutdown", "travel guide", "city", "tacos",
        "bucket list", "frontline", "bloomberg"
    )
    val hasNonMusicKeyword = nonMusicKeywords.any { titleLower.contains(it) || artistLower.contains(it) }

    val hasMusicSignals = isrc != null ||
        (album != null && durationMs != null && durationMs in 60000L..720000L)

    if (hasNonMusicKeyword && !hasMusicSignals) {
        Log.d("VANTA_SEARCH_FILTER", "filtered_non_music title='$title' artist='$artist' reason=non_music_keyword canonical=true")
        return false
    }

    if (durationMs != null && (durationMs < 30000L || durationMs > 900000L)) {
        Log.d("VANTA_SEARCH_FILTER", "filtered_non_music title='$title' artist='$artist' reason=bad_duration durationMs=$durationMs canonical=true")
        return false
    }

    if (artist.isBlank() && isrc == null) {
        Log.d("VANTA_SEARCH_FILTER", "filtered_non_music title='$title' artist='$artist' reason=missing_artist_no_isrc canonical=true")
        return false
    }

    Log.d("VANTA_SEARCH_FILTER", "ranked_music_candidate canonical=true title='$title' artist='$artist' isrc=${isrc != null} album=${album != null} durationMs=$durationMs")
    return true
}

/** Reject YouTube live streams and 24/7 radio junk before they enter search or the library. */
fun isJunkOrLiveStream(
    title: String?,
    artist: String?,
    durationMs: Long?,
    isLive: Boolean?
): Boolean {
    if (isLive == true) return true
    return !ContentPurityFilter.isAllowed(title, artist, null, durationMs, null)
}

/** Reject provider video/search inventory that is clearly not a recording. */
fun isSportsOrVideoMetadata(title: String, artist: String, album: String? = null): Boolean {
    val combined = "$title $artist ${album.orEmpty()}".lowercase()
        .replace(Regex("\\s+"), " ")
        .trim()
    val hardMarkers = listOf(
        "extended highlights", "match highlights", "football highlights", "soccer highlights",
        "football club", "nbc sports", "sky sports", "espn", "premier league",
        "champions league", "europa league", "fa cup", "league highlights",
        "post match", "pre match", "full match", "matchday", "kickoff",
        "touchdown", "quarterfinal", "semifinal", "game highlights",
        "ed sullivan", "live at", "live from", "live session", "live on", "live in",
        "mtv unplugged", "tiny desk", "kexp", "snippet", "footage", "broadcast",
        "tv show", "talk show", "concert film", "full concert", "live performance",
        "performs live"
    )
    if (hardMarkers.any(combined::contains)) return true
    if (Regex("""\b\d+\s*[-–]\s*\d+\b""").containsMatchIn(title) &&
        listOf("football", "soccer", "sports", "match", "united", "city").any(combined::contains)
    ) return true
    return false
}

/** Rejects continuous stations and playlist broadcasts that providers sometimes label as songs. */
fun isBroadcastLikeMetadata(title: String, artist: String, album: String? = null): Boolean {
    val titleText = title.lowercase().replace(Regex("\\s+"), " ").trim()
    val artistText = artist.lowercase().replace(Regex("\\s+"), " ").trim()
    val albumText = album.orEmpty().lowercase().replace(Regex("\\s+"), " ").trim()
    val combined = "$titleText $artistText $albumText"

    val explicitBroadcastMarkers = listOf(
        "24/7", "24 7", "24-7", "live radio", "radio live", "radio station",
        "internet radio", "nonstop radio", "non-stop radio", "radio stream",
        "continuous mix", "nonstop mix", "non-stop mix", "live stream",
        "livestream", "fm live", "am radio", "webradio", "web radio",
        "hits radio", "radio hits", "radio channel", "radio feed",
        "auto mix", "auto generated", "radio mix", "radio show",
        "rock classic live", "classic rock radio", "radio hits mix", "greatest hits live",
        "pop hits mix", "best songs mix", "top hits mix", "radio station mix",
        "continuous play", "automix", "auto dj", "best of radio"
    )
    if (explicitBroadcastMarkers.any(combined::contains)) return true
    if (titleText.startsWith("best radio ") || titleText.startsWith("radio 1 ")) return true
    if (titleText == "radio" || artistText == "radio") return true
    if ("radio" in titleText && ("pop hits" in titleText || "live stream" in titleText || "livestream" in titleText)) {
        return true
    }
    if (artistText == "radio mix" || artistText.endsWith(" radio station") || artistText == "internet radio") {
        return true
    }
    if (artistText.contains("radio") && (artistText.contains("mix") || artistText.contains("station"))) {
        return true
    }
    if (("live" in titleText || "radio" in titleText) &&
        (artistText.endsWith(" fm") || artistText.endsWith(" am") || artistText.endsWith(" radio"))
    ) {
        return true
    }
    if (titleText.contains("radio") && titleText.contains("mix") && titleText.length <= 32) return true
    return false
}

/** Live streams often masquerade as songs with zero/unknown duration or extremely long runtime. */
fun isLikelyContinuousStream(durationMs: Long?): Boolean {
    if (durationMs == null || durationMs <= 0L) return false
    // Normal songs rarely exceed 20 minutes; radio recordings/streams often do.
    return durationMs >= 20 * 60 * 1000L
}

/**
 * Detects YouTube playlist/compilation artifacts that masquerade as individual songs.
 * Shared by jukebox eligibility and general playable-candidate filtering.
 */
fun isPlaylistCompilationArtifact(
    title: String,
    artist: String,
    album: String? = null,
    durationMs: Long? = null
): Boolean {
    val titleText = title.lowercase().replace(Regex("\\s+"), " ").trim()
    val artistText = artist.lowercase().replace(Regex("\\s+"), " ").trim()
    val albumText = album.orEmpty().lowercase().replace(Regex("\\s+"), " ").trim()
    val combined = "$titleText $artistText $albumText"

    val decades = Regex("""\b(\d{2})s\b""").findAll(titleText).map { it.value }.distinct().toList()
    if (decades.size >= 2) return true

    val compilationMarkers = listOf(
        "timeless songs", "best of all time", "nonstop", "non-stop", "non stop",
        "full album", "mix playlist", "hours of", "continuous",
        "greatest hits mix", "best hits mix", "mega mix", "super mix",
        "playlist mix", "music mix", "collection mix", "evergreen songs",
        "songs ever", "all time hits", "best songs", "top songs mix"
    )
    if (compilationMarkers.any { combined.contains(it) }) return true

    if (title.length > 100 &&
        (title.contains('–') || title.contains('—') || title.contains(" - "))
    ) {
        return true
    }

    val playlistTitleSignals = listOf(
        "oldies", "hits", "songs", "playlist", "mix", "timeless",
        "best of", "greatest", "nonstop", "hours", "continuous", " ever"
    )
    val titleLooksLikePlaylist = playlistTitleSignals.count { titleText.contains(it) } >= 2 ||
        (titleText.contains("70") && titleText.contains("80") && titleText.contains("90"))

    val channelArtistMarkers = listOf(
        " station", " channel", " melodies", " music", " records",
        " hits", " sounds", " vibes", " tunes", " playlist", " radio"
    )
    val knownChannelArtists = setOf(
        "harmony melodies", "pop station", "rock station", "music box",
        "retro hits", "golden oldies", "classic hits", "hit station",
        "love songs", "chill music", "relaxing music", "study music",
        "oldies station", "jazz station", "soul station", "blues station"
    )
    val artistLooksLikeChannel = artistText in knownChannelArtists ||
        channelArtistMarkers.any { artistText.contains(it.trim()) }

    if (titleLooksLikePlaylist && artistLooksLikeChannel) return true
    if (artistLooksLikeChannel && decades.isNotEmpty()) return true

    return false
}

/** Unified guard for DJ sets, queue autofill, and recommendation feeds. */
fun com.audiophile.musicplayer.data.local.entities.UnifiedTrackWithSources.isPlayableMusicCandidate(): Boolean {
    if (!sourceValidityStatus().canEnterPlaybackFlow()) return false
    if (!ContentPurityFilter.isAllowed(
            title = track.title.orEmpty(),
            artist = track.artist.orEmpty(),
            album = track.albumName,
            durationMs = track.durationMs,
            source = sources.firstOrNull()?.externalProviderId
        )
    ) {
        return false
    }
    if (isBroadcastLikeMetadata(
            title = track.title.orEmpty(),
            artist = track.artist.orEmpty(),
            album = track.albumName
        )
    ) {
        return false
    }
    if (isLikelyContinuousStream(track.durationMs)) return false
    if (isPlaylistCompilationArtifact(
            title = track.title.orEmpty(),
            artist = track.artist.orEmpty(),
            album = track.albumName,
            durationMs = track.durationMs
        )
    ) {
        return false
    }
    return sources.any {
        it.streamUrl.isNotBlank() &&
            it.sourceType != com.audiophile.musicplayer.data.local.entities.SourceType.APPLE_MUSIC &&
            it.sourceType != com.audiophile.musicplayer.data.local.entities.SourceType.SPOTIFY
    }
}

data class ResolvedStream(
    val streamUrl: String,
    val bitrateKbps: Int,
    val mimeType: String? = null,
    val expiresAt: Long? = null,
    val qualityLabel: String? = null,
    val format: String? = null,
    val isSpatialAudio: Boolean = false,
    val isDolbyAtmos: Boolean = false,
    val isSurround: Boolean = false,
    val providerId: String? = null
)
