package com.audiophile.musicplayer.sync

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import androidx.core.content.edit

/**
 * Encrypted storage for VANTA Sync identity and encryption keys.
 *
 * Tries AES-256 [EncryptedSharedPreferences] first and falls back to plain
 * [SharedPreferences] if the device keystore is unavailable. The data here is
 * non-sensitive by itself (anonymous user ID + sync key), but encryption is used
 * to prevent trivial tampering.
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
            "secure_store_unavailable fallback=plain_shared_prefs",
            e
        )
        context.getSharedPreferences("vanta_sync_plain", Context.MODE_PRIVATE)
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
