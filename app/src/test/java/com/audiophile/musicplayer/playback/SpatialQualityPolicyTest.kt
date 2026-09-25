package com.audiophile.musicplayer.playback

import com.audiophile.musicplayer.data.source.playback.RequestedAudioQuality.*
import org.junit.Assert.assertEquals
import org.junit.Test

class SpatialQualityPolicyTest {
    @Test fun installedSoftwareAtmosPromotesAuto() { assertEquals(AUTO_SPATIAL, selectSpatialQuality(AUTO_SPATIAL, false, true, true)) }
    @Test fun explicitExperimentalAtmosIsStillAvailable() { assertEquals(ATMOS, selectSpatialQuality(ATMOS, false, true, true)) }
    @Test fun explicitIamfIsPreserved() { assertEquals(IAMF, selectSpatialQuality(IAMF, false, true)) }
    @Test fun frozenReadyPlaybackCanRecover() { org.junit.Assert.assertTrue(shouldRecoverStalledPlayback(true, false, false, 1000, 1000)) }
    @Test fun deliberatePauseAndFocusLossDoNotRecover() {
        org.junit.Assert.assertFalse(shouldRecoverStalledPlayback(false, false, false, 1000, 1000))
        org.junit.Assert.assertFalse(shouldRecoverStalledPlayback(true, true, false, 1000, 1000))
        org.junit.Assert.assertFalse(shouldRecoverStalledPlayback(true, false, true, 1000, 1000))
        org.junit.Assert.assertFalse(shouldRecoverStalledPlayback(true, false, false, 1000, 2000))
    }

    @Test fun atmosCapableRoutePrefersAtmos() { assertEquals(AUTO_SPATIAL, selectSpatialQuality(AUTO_SPATIAL, true, true)) }
    @Test fun nonDolbyPhoneAutoStillRequestsSpatial() { assertEquals(AUTO_SPATIAL, selectSpatialQuality(AUTO_SPATIAL, false, true)) }
    @Test fun failedDecoderStillRequestsSpatial() { assertEquals(AUTO_SPATIAL, selectSpatialQuality(AUTO_SPATIAL, false, false)) }
    @Test fun mpeghRouteAutoStillRequestsSpatial() { assertEquals(AUTO_SPATIAL, selectSpatialQuality(AUTO_SPATIAL, false, false, false, true)) }
    @Test fun explicitCdPreferenceIsPreserved() { assertEquals(LOSSLESS_16, selectSpatialQuality(LOSSLESS_16, true, true)) }
}
