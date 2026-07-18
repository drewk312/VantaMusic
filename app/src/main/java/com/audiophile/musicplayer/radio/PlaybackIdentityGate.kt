package com.audiophile.musicplayer.radio

import android.util.Log
import com.audiophile.musicplayer.data.local.entities.UnifiedTrackWithSources
import com.audiophile.musicplayer.data.source.isPlayableMusicCandidate

sealed class GateVerdict {
    data object Passed : GateVerdict()
    data class Failed(val reason: String) : GateVerdict()
}

object PlaybackIdentityGate {

    private val hardBlockMarkers = listOf(
        "karaoke", "tribute to", "tribute act", "made famous by", "made popular by",
        "originally performed by", "in the style of", "as made famous by",
        "8-bit", "8 bit", "chip tune", "chiptune", "midi version",
        "kids bop", "kidz bop", "lullaby version",
        "white noise", "brown noise", "study beats", "lofi beats",
        "sped up", "slowed + reverb", "slowed and reverb", "nightcore",
        "sped-up", "speed up", "slowed down",
        "tiktok version", "reels version", "tiktok remix",
        "backing track", "sing along", "vocal version",
        "sound-a-like", "sound alike",
        "top electronic", "edm tribe", "electronic songs"
    )

    private val videoLiveMarkers = listOf(
        "ed sullivan", "live at", "live from", "live session", "live on",
        "live in", "live performance", "concert recording", "mtv unplugged", "tiny desk", "kexp",
        "snippet", "footage", "tv show", "talk show",
        "concert film", "full concert", "live performance",
        "performs live", "interview", "documentary",
        "official video", "official music video", "music video",
        "visualizer", "lyric video", "audio only"
    )

    private val coverVariantMarkers = listOf(
        "cover", "acoustic version", "acoustic cover", "unplugged",
        "instrumental", "instrumental version",
        "piano cover", "guitar cover", "violin cover",
        "string quartet", "vitamin string",
        "re-recorded", "re recorded", "re-record", "re record"
    )

    private val demotionMarkers = listOf(
        "remix", "mix", "edit", "rework", "rework",
        "redo", "take 2", "take 3",
        "demo", "rough mix", "rough cut",
        "alternate take", "alternate version", "alternate mix",
        "radio edit", "single edit", "short edit",
        "extended", "extended mix", "extended version",
        "club mix", "dub mix", "instrumental mix",
        "a cappella", "acapella"
    )

    fun verify(track: UnifiedTrackWithSources): GateVerdict {
        val title = track.track.title.orEmpty()
        val artist = track.track.artist.orEmpty()
        val album = track.track.albumName.orEmpty()
        val durationMs = track.track.durationMs
        val haystack = "$title $artist $album".lowercase()

        if (!track.isPlayableMusicCandidate()) {
            return GateVerdict.Failed("not_playable_music_candidate")
        }

        if (durationMs != null && (durationMs < 60_000L || durationMs > 600_000L)) {
            return GateVerdict.Failed("duration_out_of_range:${durationMs}ms")
        }

        if (hardBlockMarkers.any { haystack.contains(it) }) {
            val matched = hardBlockMarkers.first { haystack.contains(it) }
            Log.w("VANTA_RADIO_GATE", "HARD_BLOCK title='$title' artist='$artist' marker='$matched'")
            return GateVerdict.Failed("hard_block:$matched")
        }

        if (videoLiveMarkers.any { haystack.contains(it) }) {
            val matched = videoLiveMarkers.first { haystack.contains(it) }
            Log.w("VANTA_RADIO_GATE", "LIVE_VIDEO_BLOCK title='$title' artist='$artist' marker='$matched'")
            return GateVerdict.Failed("live_video:$matched")
        }

        if (coverVariantMarkers.any { haystack.contains(it) }) {
            val matched = coverVariantMarkers.first { haystack.contains(it) }
            Log.w("VANTA_RADIO_GATE", "COVER_VARIANT_BLOCK title='$title' artist='$artist' marker='$matched'")
            return GateVerdict.Failed("cover_variant:$matched")
        }

        if (durationMs != null && durationMs < 90_000L) {
            val matched = demotionMarkers.firstOrNull { haystack.contains(it) }
            if (matched != null) {
                Log.w("VANTA_RADIO_GATE", "SHORT_DEMOTED title='$title' artist='$artist' duration=${durationMs}ms marker='$matched'")
                return GateVerdict.Failed("short_demoted:$matched")
            }
        }

        return GateVerdict.Passed
    }

    fun verifyCandidate(title: String, artist: String, album: String?, durationMs: Long?): GateVerdict {
        val haystack = "$title ${artist} ${album.orEmpty()}".lowercase()

        if (hardBlockMarkers.any { haystack.contains(it) }) {
            val matched = hardBlockMarkers.first { haystack.contains(it) }
            return GateVerdict.Failed("hard_block:$matched")
        }
        if (videoLiveMarkers.any { haystack.contains(it) }) {
            val matched = videoLiveMarkers.first { haystack.contains(it) }
            return GateVerdict.Failed("live_video:$matched")
        }
        if (coverVariantMarkers.any { haystack.contains(it) }) {
            val matched = coverVariantMarkers.first { haystack.contains(it) }
            return GateVerdict.Failed("cover_variant:$matched")
        }
        return GateVerdict.Passed
    }
}
