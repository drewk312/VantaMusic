package com.audiophile.musicplayer.radio

import com.audiophile.musicplayer.data.dj.JukeboxTrackEligibility
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RadioCandidateGateTest {

    @Test
    fun junkFilterRejectsLiveRecordings() {
        assertTrue("Studio track should be accepted", 
            !JukeboxTrackEligibility.isJunkOrVideoTrack("Denver", "Jack Harlow", 158_000L))
        
        assertTrue("Live track should be rejected", 
            JukeboxTrackEligibility.isJunkOrVideoTrack("Denver (Live at Red Rocks)", "Jack Harlow", 160_000L))
    }

    @Test
    fun radioFilterRejectsKaraoke() {
        assertTrue("Karaoke should be excluded",
            JukeboxTrackEligibility.shouldExcludeFromRadioQueue("Denver (Karaoke Version)", "Jack Harlow", 158_000L))
    }
}
