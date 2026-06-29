package com.audiophile.musicplayer.data.connectors.sync

import android.util.Log
import com.audiophile.musicplayer.data.connectors.ConnectedLibraryAccount
import com.audiophile.musicplayer.data.connectors.ConnectedLibraryClient
import com.audiophile.musicplayer.data.connectors.ConnectedLibraryLogRedactor
import com.audiophile.musicplayer.data.connectors.ConnectedLibraryProvider
import com.audiophile.musicplayer.data.connectors.ConnectedLibraryTokenStatus
import com.audiophile.musicplayer.data.connectors.ProviderSyncAction
import com.audiophile.musicplayer.data.connectors.ProviderSyncActionType
import com.audiophile.musicplayer.data.connectors.ProviderSyncStatus
import com.audiophile.musicplayer.data.connectors.ProviderTrackLink

data class VantaLikedTrackIdentity(
    val vantaTrackId: Long,
    val title: String,
    val artist: String,
    val isrc: String? = null
)

class ConnectedLibraryLikeSyncManager(
    private val clients: Map<ConnectedLibraryProvider, ConnectedLibraryClient>
) {
    suspend fun enqueueLikeSync(
        track: VantaLikedTrackIdentity,
        accounts: List<ConnectedLibraryAccount>,
        links: List<ProviderTrackLink>,
        nowMs: Long = System.currentTimeMillis()
    ): List<ProviderSyncAction> {
        return accounts
            .filter { it.syncLikesEnabled }
            .map { account ->
                val client = clients[account.provider]
                val missingScopes = client?.minimumLikeSyncScopes.orEmpty() - account.scopesGranted
                val tokenNeedsAuth = account.tokenStatus == ConnectedLibraryTokenStatus.NEEDS_REAUTH ||
                    account.tokenStatus == ConnectedLibraryTokenStatus.EXPIRED ||
                    account.tokenStatus != ConnectedLibraryTokenStatus.VALID
                val link = links.firstOrNull { it.provider == account.provider && it.vantaLocalTrackId == track.vantaTrackId }
                val providerTrackId = if (tokenNeedsAuth || missingScopes.isNotEmpty()) {
                    link?.providerTrackId
                } else {
                    link?.providerTrackId ?: client?.findProviderTrackByIdentity(
                        account = account,
                        isrc = track.isrc,
                        title = track.title,
                        artist = track.artist
                    )
                }
                val status = when {
                    tokenNeedsAuth || missingScopes.isNotEmpty() -> ProviderSyncStatus.NEEDS_REAUTH
                    providerTrackId.isNullOrBlank() -> ProviderSyncStatus.WAITING_FOR_PROVIDER_MATCH
                    else -> ProviderSyncStatus.QUEUED
                }
                ProviderSyncAction(
                    id = "${account.provider.name}:${track.vantaTrackId}:$nowMs",
                    provider = account.provider,
                    actionType = ProviderSyncActionType.SAVE_LIKE,
                    vantaTrackId = track.vantaTrackId,
                    providerTrackId = providerTrackId,
                    status = status,
                    createdAt = nowMs
                ).also {
                    Log.i("VANTA_LIKE_SYNC", "provider=${it.provider} track=${it.vantaTrackId} status=${it.status} reason=enqueue")
                }
            }
    }

    suspend fun runQueuedAction(
        account: ConnectedLibraryAccount,
        action: ProviderSyncAction
    ): ProviderSyncAction {
        if (action.status != ProviderSyncStatus.QUEUED || action.providerTrackId.isNullOrBlank()) {
            return action
        }
        val client = clients[action.provider] ?: return action.copy(
            status = ProviderSyncStatus.FAILED,
            lastError = "missing_connector_client",
            attemptCount = action.attemptCount + 1
        )
        return runCatching {
            client.saveLike(account, action.providerTrackId)
            action.copy(
                status = ProviderSyncStatus.COMPLETED,
                attemptCount = action.attemptCount + 1,
                completedAt = System.currentTimeMillis()
            )
        }.getOrElse { error ->
            val reason = ConnectedLibraryLogRedactor.redact(error.message ?: error.javaClass.simpleName)
            Log.w("VANTA_LIKE_SYNC", "provider=${action.provider} track=${action.vantaTrackId} status=FAILED reason=$reason")
            action.copy(
                status = ProviderSyncStatus.FAILED,
                attemptCount = action.attemptCount + 1,
                lastError = reason
            )
        }
    }
}
