package com.audiophile.musicplayer.data.dj

import com.audiophile.musicplayer.data.local.entities.UnifiedTrackWithSources
import com.audiophile.musicplayer.data.source.canEnterPlaybackFlow
import com.audiophile.musicplayer.data.source.isPlayableMusicCandidate
import com.audiophile.musicplayer.data.source.sourceValidityStatus

/** LLM suggestions must pass deterministic resolve + playability gates before entering playback. */
object DjLlmPlaybackGuard {

    fun canEnterPlaybackFromResolvedCandidate(track: UnifiedTrackWithSources?): Boolean {
        if (track == null) return false
        return track.sourceValidityStatus().canEnterPlaybackFlow() &&
            !JukeboxTrackEligibility.shouldExcludeFromRadioQueue(track) &&
            track.isPlayableMusicCandidate()
    }
}
