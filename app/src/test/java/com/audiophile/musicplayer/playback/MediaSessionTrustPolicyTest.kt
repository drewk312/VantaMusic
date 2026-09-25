package com.audiophile.musicplayer.playback

import androidx.media3.common.Player
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MediaSessionTrustPolicyTest {
    @Test
    fun libraryAccessAllowedForSelfAndAutoOnly() {
        assertTrue(MediaSessionTrustPolicy.canAccessLibrary(MediaSessionTrustPolicy.TrustLevel.SELF))
        assertTrue(MediaSessionTrustPolicy.canAccessLibrary(MediaSessionTrustPolicy.TrustLevel.TRUSTED_LIBRARY))
        assertFalse(MediaSessionTrustPolicy.canAccessLibrary(MediaSessionTrustPolicy.TrustLevel.TRUSTED_TRANSPORT))
        assertFalse(MediaSessionTrustPolicy.canAccessLibrary(MediaSessionTrustPolicy.TrustLevel.LIMITED))
        assertFalse(MediaSessionTrustPolicy.canAccessLibrary(MediaSessionTrustPolicy.TrustLevel.REJECTED))
    }

    @Test
    fun trustedSystemTransportCanBrowseButCannotMutateLibrary() {
        assertTrue(MediaSessionTrustPolicy.canBrowseLibrary(MediaSessionTrustPolicy.TrustLevel.SELF))
        assertTrue(MediaSessionTrustPolicy.canBrowseLibrary(MediaSessionTrustPolicy.TrustLevel.TRUSTED_LIBRARY))
        assertTrue(MediaSessionTrustPolicy.canBrowseLibrary(MediaSessionTrustPolicy.TrustLevel.TRUSTED_TRANSPORT))
        assertFalse(MediaSessionTrustPolicy.canBrowseLibrary(MediaSessionTrustPolicy.TrustLevel.LIMITED))
        assertFalse(MediaSessionTrustPolicy.canBrowseLibrary(MediaSessionTrustPolicy.TrustLevel.REJECTED))
        assertFalse(MediaSessionTrustPolicy.canAccessLibrary(MediaSessionTrustPolicy.TrustLevel.TRUSTED_TRANSPORT))
    }

    @Test
    fun transportControllersCannotMutateTheQueueOrShuffleMode() {
        val commands = MediaSessionTrustPolicy.transportPlayerCommands()

        assertFalse(commands.contains(Player.COMMAND_CHANGE_MEDIA_ITEMS))
        assertFalse(commands.contains(Player.COMMAND_SET_SHUFFLE_MODE))
    }
}
