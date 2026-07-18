package com.audiophile.musicplayer.sync

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import androidx.core.content.edit
import com.audiophile.musicplayer.security.FailClosedSharedPreferences

/**
 * Encrypted storage for VANTA Sync identity and encryption keys.
 *
 * Uses AES-256 [EncryptedSharedPreferences]. If the device keystore is unavailable,
 * reads return defaults and writes fail closed instead of persisting a sync key in
 * plaintext.
 */
class SyncIdentityStore(context: Context) {

    private val appContext = context.applicationContext
    private val prefs: SharedPreferences = createSecurePrefs(appContext)

    private fun createSecurePrefs(context: Context): SharedPreferences = try {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context,
            "vanta_sync_secure",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    } catch (e: Exception) {
        Log.e(
            "VANTA_SYNC_STORE",
            "secure_store_unavailable sync_persistence_disabled",
            e
        )
        FailClosedSharedPreferences
    }

    fun storeSyncKey(syncKey: String?) {
        prefs.edit { putString(KEY_SYNC_KEY, syncKey) }
    }

    fun syncKey(): String? =
        prefs.getString(KEY_SYNC_KEY, null)?.takeIf { it.isNotBlank() }

    fun storeLastSnapshotId(snapshotId: String?) {
        prefs.edit { putString(KEY_LAST_SNAPSHOT_ID, snapshotId) }
    }

    fun lastSnapshotId(): String? =
        prefs.getString(KEY_LAST_SNAPSHOT_ID, null)?.takeIf { it.isNotBlank() }

    fun storeLastSyncedAtMs(atMs: Long) {
        prefs.edit { putLong(KEY_LAST_SYNCED_AT_MS, atMs) }
    }

    fun lastSyncedAtMs(): Long =
        prefs.getLong(KEY_LAST_SYNCED_AT_MS, 0L)

    fun clear() {
        prefs.edit { clear() }
    }

    companion object {
        private const val KEY_SYNC_KEY = "sync_key"
        private const val KEY_LAST_SNAPSHOT_ID = "last_snapshot_id"
        private const val KEY_LAST_SYNCED_AT_MS = "last_synced_at_ms"
    }
}
