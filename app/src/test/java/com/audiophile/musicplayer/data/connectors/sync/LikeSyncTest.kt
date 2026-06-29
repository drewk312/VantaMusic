package com.audiophile.musicplayer.data.connectors.sync

import com.audiophile.musicplayer.data.connectors.ConnectedLibraryAccount
import com.audiophile.musicplayer.data.connectors.ConnectedLibraryClient
import com.audiophile.musicplayer.data.connectors.ConnectedLibraryImportPage
import com.audiophile.musicplayer.data.connectors.ConnectedLibraryImportRequest
import com.audiophile.musicplayer.data.connectors.ConnectedLibraryProvider
import com.audiophile.musicplayer.data.connectors.ConnectedLibraryTokenStatus
import com.audiophile.musicplayer.data.connectors.ProviderSyncStatus
import com.audiophile.musicplayer.data.connectors.ProviderTrackLink
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LikeSyncTest {

    @Test
    fun syncDisabledByDefault() = runBlocking {
        val actions = manager().enqueueLikeSync(
            track = track(),
            accounts = listOf(account(syncLikes = false)),
            links = emptyList(),
            nowMs = 1L
        )

        assertTrue(actions.isEmpty())
    }

    @Test
    fun vantaLikeEnqueuesProviderSyncWhenEnabled() = runBlocking {
        val actions = manager().enqueueLikeSync(
            track = track(),
            accounts = listOf(account(syncLikes = true)),
            links = listOf(
                ProviderTrackLink(
                    id = "SPOTIFY:provider1",
                    provider = ConnectedLibraryProvider.SPOTIFY,
                    providerTrackId = "provider1",
                    vantaLocalTrackId = 5L,
                    matchConfidence = 1f,
                    linkedAt = 1L
                )
            ),
            nowMs = 2L
        )

        assertEquals(ProviderSyncStatus.QUEUED, actions.single().status)
        assertEquals("provider1", actions.single().providerTrackId)
    }

    @Test
    fun expiredTokenCausesNeedsReauth() = runBlocking {
        val actions = manager().enqueueLikeSync(
            track = track(),
            accounts = listOf(account(syncLikes = true, tokenStatus = ConnectedLibraryTokenStatus.EXPIRED)),
            links = emptyList(),
            nowMs = 3L
        )

        assertEquals(ProviderSyncStatus.NEEDS_REAUTH, actions.single().status)
    }

    @Test
    fun missingModifyScopeCausesNeedsReauth() = runBlocking {
        val actions = manager().enqueueLikeSync(
            track = track(),
            accounts = listOf(account(syncLikes = true).copy(scopesGranted = emptySet())),
            links = emptyList(),
            nowMs = 4L
        )

        assertEquals(ProviderSyncStatus.NEEDS_REAUTH, actions.single().status)
    }

    private fun manager(): ConnectedLibraryLikeSyncManager =
        ConnectedLibraryLikeSyncManager(mapOf(ConnectedLibraryProvider.SPOTIFY to FakeClient()))

    private fun track(): VantaLikedTrackIdentity =
        VantaLikedTrackIdentity(vantaTrackId = 5L, title = "Song", artist = "Artist", isrc = "USRC")

    private fun account(
        syncLikes: Boolean,
        tokenStatus: ConnectedLibraryTokenStatus = ConnectedLibraryTokenStatus.VALID
    ): ConnectedLibraryAccount =
        ConnectedLibraryAccount(
            id = "a",
            provider = ConnectedLibraryProvider.SPOTIFY,
            displayName = "Spotify Library",
            accountId = "u",
            connectedAt = 1L,
            tokenStatus = tokenStatus,
            scopesGranted = setOf("user-library-modify"),
            syncLikesEnabled = syncLikes
        )

    private class FakeClient : ConnectedLibraryClient {
        override val provider = ConnectedLibraryProvider.SPOTIFY
        override val minimumImportScopes = emptySet<String>()
        override val minimumLikeSyncScopes = setOf("user-library-modify")
        override val minimumPlaylistSyncScopes = emptySet<String>()

        override suspend fun fetchLibraryPage(
            account: ConnectedLibraryAccount,
            request: ConnectedLibraryImportRequest,
            cursor: String?
        ): ConnectedLibraryImportPage = ConnectedLibraryImportPage()

        override suspend fun findProviderTrackByIdentity(
            account: ConnectedLibraryAccount,
            isrc: String?,
            title: String,
            artist: String
        ): String? = "found_provider_id"

        override suspend fun saveLike(account: ConnectedLibraryAccount, providerTrackId: String) = Unit
    }
}
