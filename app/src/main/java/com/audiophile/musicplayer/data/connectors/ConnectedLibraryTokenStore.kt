package com.audiophile.musicplayer.data.connectors

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import androidx.core.content.edit

class ConnectedLibraryTokenStore(context: Context) {
    private val appContext = context.applicationContext
    private val prefs: SharedPreferences = createSecurePrefs(appContext)

    private fun createSecurePrefs(context: Context): SharedPreferences = try {
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
        Log.e("VANTA_CONNECTOR_TOKEN", "provider=unknown status=secure_store_unavailable")
        throw e
    }

    fun storeTokens(
        provider: ConnectedLibraryProvider,
        accessToken: String?,
        refreshToken: String?,
        musicUserToken: String? = null,
        expiresAtMs: Long? = null
    ) {
        prefs.edit {
                putString("${provider.name}_access_token", accessToken)
                putString("${provider.name}_refresh_token", refreshToken)
                putString("${provider.name}_music_user_token", musicUserToken)
                putLong("${provider.name}_expires_at", expiresAtMs ?: 0L)
            }
        Log.i("VANTA_CONNECTOR_TOKEN", "provider=$provider status=stored")
    }

    fun accessToken(provider: ConnectedLibraryProvider): String? =
        prefs.getString("${provider.name}_access_token", null)?.takeIf { it.isNotBlank() }

    fun refreshToken(provider: ConnectedLibraryProvider): String? =
        prefs.getString("${provider.name}_refresh_token", null)?.takeIf { it.isNotBlank() }

    fun musicUserToken(provider: ConnectedLibraryProvider): String? =
        prefs.getString("${provider.name}_music_user_token", null)?.takeIf { it.isNotBlank() }

    fun clear(provider: ConnectedLibraryProvider) {
        prefs.edit {
                remove("${provider.name}_access_token")
                remove("${provider.name}_refresh_token")
                remove("${provider.name}_music_user_token")
                remove("${provider.name}_expires_at")
            }
        Log.i("VANTA_CONNECTOR_TOKEN", "provider=$provider status=cleared")
    }
}