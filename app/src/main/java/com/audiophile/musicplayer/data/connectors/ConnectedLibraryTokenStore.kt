package com.audiophile.musicplayer.data.connectors

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import androidx.core.content.edit

/**
 * Encrypted storage for connected-library OAuth tokens.
 *
 * Uses AES-256 encrypted [EncryptedSharedPreferences]. If the device keystore is
 * unavailable, reads fail closed and writes report failure. Credentials are never
 * downgraded to plaintext storage.
 */
class ConnectedLibraryTokenStore(context: Context) {
    private val appContext = context.applicationContext
    private val prefs: SharedPreferences? = createSecurePrefs(appContext)

    val isAvailable: Boolean
        get() = prefs != null

    private fun createSecurePrefs(context: Context): SharedPreferences? = try {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context,
            "connected_libraries_secure",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    } catch (e: Exception) {
        Log.e(
            "VANTA_CONNECTOR_TOKEN",
            "provider=unknown status=secure_store_unavailable credentials_disabled",
            e
        )
        null
    }

    fun storeTokens(
        provider: ConnectedLibraryProvider,
        accessToken: String?,
        refreshToken: String?,
        musicUserToken: String? = null,
        expiresAtMs: Long? = null
    ): Boolean {
        val securePrefs = prefs ?: return false
        securePrefs.edit {
                putString("${provider.name}_access_token", accessToken)
                putString("${provider.name}_refresh_token", refreshToken)
                putString("${provider.name}_music_user_token", musicUserToken)
                putLong("${provider.name}_expires_at", expiresAtMs ?: 0L)
        }
        Log.i("VANTA_CONNECTOR_TOKEN", "provider=$provider status=stored")
        return true
    }

    fun accessToken(provider: ConnectedLibraryProvider): String? =
        prefs?.getString("${provider.name}_access_token", null)?.takeIf { it.isNotBlank() }

    fun refreshToken(provider: ConnectedLibraryProvider): String? =
        prefs?.getString("${provider.name}_refresh_token", null)?.takeIf { it.isNotBlank() }

    fun musicUserToken(provider: ConnectedLibraryProvider): String? =
        prefs?.getString("${provider.name}_music_user_token", null)?.takeIf { it.isNotBlank() }

    fun expiresAt(provider: ConnectedLibraryProvider): Long =
        prefs?.getLong("${provider.name}_expires_at", 0L) ?: 0L

    fun clear(provider: ConnectedLibraryProvider): Boolean {
        val securePrefs = prefs ?: return false
        securePrefs.edit {
                remove("${provider.name}_access_token")
                remove("${provider.name}_refresh_token")
                remove("${provider.name}_music_user_token")
                remove("${provider.name}_expires_at")
        }
        Log.i("VANTA_CONNECTOR_TOKEN", "provider=$provider status=cleared")
        return true
    }
}
