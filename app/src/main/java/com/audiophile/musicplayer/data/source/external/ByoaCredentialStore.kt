package com.audiophile.musicplayer.data.source.external

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.core.content.edit
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.google.gson.Gson
import okhttp3.OkHttpClient

/**
 * Encrypted BYOA vault — mirrors SpotiFLAC's bbolt Save*Custom* pattern.
 * Never stores raw Qobuz password long-term, only user_auth_token.
 * All prefs encrypted with MasterKey AES256_GCM (same as ResolverConfigStore).
 */
class ByoaCredentialStore(context: Context) {

    private val gson = Gson()

    private val prefs: SharedPreferences = try {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context,
            PREFS_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    } catch (e: Exception) {
        Log.e(TAG, "EncryptedSharedPreferences unavailable, falling back to plain (non-persistent warning)", e)
        // Fallback: plain prefs but mark unavailable so caller can warn
        context.getSharedPreferences(PREFS_NAME + "_fallback", Context.MODE_PRIVATE)
    }

    val isEncrypted: Boolean
        get() = prefs.javaClass.name.contains("EncryptedSharedPreferences")

    // Qobuz user_auth_token (from api.json/0.2/user/login) — NOT email/pass
    fun getQobuzUserToken(): String? = prefs.getString(KEY_QOBUZ_USER_TOKEN, null)?.takeIf { it.isNotBlank() }
    fun saveQobuzUserToken(token: String) = prefs.edit { putString(KEY_QOBUZ_USER_TOKEN, token.trim()) }
    fun clearQobuz() = prefs.edit { remove(KEY_QOBUZ_USER_TOKEN) }

    fun hasQobuzToken(): Boolean = !getQobuzUserToken().isNullOrBlank()

    // Deezer ARL cookie
    fun getDeezerArl(): String? = prefs.getString(KEY_DEEZER_ARL, null)?.takeIf { it.isNotBlank() }
    fun saveDeezerArl(arl: String) = prefs.edit { putString(KEY_DEEZER_ARL, arl.trim()) }
    fun clearDeezer() = prefs.edit { remove(KEY_DEEZER_ARL) }
    fun hasDeezerArl(): Boolean = !getDeezerArl().isNullOrBlank()

    // Tidal OAuth JSON (access_token + refresh_token + expires_at)
    fun getTidalOauthJson(): String? = prefs.getString(KEY_TIDAL_OAUTH, null)?.takeIf { it.isNotBlank() }
    fun saveTidalOauthJson(json: String) = prefs.edit { putString(KEY_TIDAL_OAUTH, json.trim()) }
    fun clearTidal() = prefs.edit { remove(KEY_TIDAL_OAUTH) }
    fun hasTidal(): Boolean = !getTidalOauthJson().isNullOrBlank()

    // Amazon marketplace refresh (optional)
    fun getAmazonToken(): String? = prefs.getString(KEY_AMAZON_TOKEN, null)?.takeIf { it.isNotBlank() }
    fun saveAmazonToken(token: String) = prefs.edit { putString(KEY_AMAZON_TOKEN, token.trim()) }
    fun clearAmazon() = prefs.edit { remove(KEY_AMAZON_TOKEN) }

    /**
     * Optional device-local community relay session. This is deliberately never
     * compiled into the APK or forwarded to the shared gateway.
     */
    fun getCommunityRelaySession(): CommunityRelaySession? {
        val raw = prefs.getString(KEY_COMMUNITY_SESSION, null)?.takeIf { it.isNotBlank() } ?: return null
        return runCatching {
            gson.fromJson(raw, CommunityRelaySession::class.java)
                ?.takeIf {
                    it.sessionId.isNotBlank() &&
                        it.sessionSecret.isNotBlank() &&
                        it.expiresAt.isNotBlank()
                }
        }.getOrNull()
    }

    fun getCommunityRelaySessionJson(): String = prefs.getString(KEY_COMMUNITY_SESSION, "").orEmpty()

    fun saveCommunityRelaySessionJson(json: String): Boolean {
        val parsed = runCatching {
            gson.fromJson(json.trim(), CommunityRelaySession::class.java)
                ?.takeIf {
                    it.sessionId.isNotBlank() &&
                        it.sessionSecret.isNotBlank() &&
                        it.expiresAt.isNotBlank()
                }
        }.getOrNull() ?: return false
        prefs.edit { putString(KEY_COMMUNITY_SESSION, gson.toJson(parsed)) }
        return true
    }

    fun clearCommunityRelaySession() = prefs.edit { remove(KEY_COMMUNITY_SESSION) }

    // Custom gateway URL (user's own workers.dev) — overrides DEFAULT_GATEWAY_BASE_URL when set
    fun getCustomGatewayUrl(): String? = prefs.getString(KEY_CUSTOM_GATEWAY, null)?.takeIf { it.isNotBlank() }
    fun saveCustomGatewayUrl(url: String) {
        val trimmed = url.trim().trimEnd('/')
        if (trimmed.isBlank()) {
            prefs.edit { remove(KEY_CUSTOM_GATEWAY) }
        } else {
            prefs.edit { putString(KEY_CUSTOM_GATEWAY, trimmed) }
        }
    }
    fun clearCustomGateway() = prefs.edit { remove(KEY_CUSTOM_GATEWAY) }

    fun getEffectiveGatewayUrl(): String = getCustomGatewayUrl() ?: SpotiFlacEndpoints.DEFAULT_GATEWAY_BASE_URL

    fun buildByoaHeaders(): Map<String, String> {
        val headers = mutableMapOf<String, String>()
        getQobuzUserToken()?.let { headers[HEADER_QOBUZ_TOKEN] = it }
        getDeezerArl()?.let { headers[HEADER_DEEZER_ARL] = it }
        getTidalOauthJson()?.let { headers[HEADER_TIDAL_TOKEN] = it }
        getAmazonToken()?.let { headers[HEADER_AMAZON_TOKEN] = it }
        return headers
    }

    /**
     * PKCE/device-code auto-reconnect: refresh the stored Tidal access token
     * before the phone sends X-Tidal-Token. Silent — no CAPTCHA, no browser.
     */
    fun refreshTidalAccessIfNeeded(http: OkHttpClient, nowMs: Long = System.currentTimeMillis()) {
        synchronized(tidalRefreshLock) {
            val raw = getTidalOauthJson() ?: return
            val parsed = TidalOauthTokens.parse(raw) ?: return
            if (!TidalOauthTokens.needsRefresh(parsed, nowMs)) return
            val next = TidalOauthTokens.refresh(http, parsed) ?: return
            saveTidalOauthJson(TidalOauthTokens.mergeStoredJson(raw, next, nowMs))
            Log.d(TAG, "tidal oauth refreshed")
        }
    }

    fun clearAll() = prefs.edit { clear() }

    fun getDisplaySummary(): String {
        val saved = buildList {
            if (hasQobuzToken()) add("Qobuz")
            if (hasDeezerArl()) add("Deezer")
            if (hasTidal()) add("Tidal")
            if (!getAmazonToken().isNullOrBlank()) add("Amazon Music")
        }
        val session = getCommunityRelaySession()
        return when {
            session != null && !CommunityRelaySigner.isUsable(session) -> "Your imported connection needs renewal"
            saved.isNotEmpty() -> "Saved accounts · ${saved.joinToString(", ")}"
            session != null -> "An imported connection is ready"
            !getCustomGatewayUrl().isNullOrBlank() -> "Using your custom streaming server"
            else -> "Using VANTA’s default connection"
        }
    }

    fun getStatusSummary(): String = buildString {
        val community = getCommunityRelaySession()
        val communityStatus = when {
            community == null -> "missing"
            CommunityRelaySigner.isUsable(community) -> "active"
            else -> "expired"
        }
        append("community=$communityStatus qobuz=${hasQobuzToken()} deezer=${hasDeezerArl()} tidal=${hasTidal()} amazon=${!getAmazonToken().isNullOrBlank()} gateway=${getCustomGatewayUrl() ?: "default"} encrypted=$isEncrypted")
    }

    companion object {
        private const val TAG = "ByoaCredentialStore"
        private const val PREFS_NAME = "byoa_credentials_secure"
        private const val KEY_QOBUZ_USER_TOKEN = "qobuz_user_auth_token"
        private const val KEY_DEEZER_ARL = "deezer_arl"
        private const val KEY_TIDAL_OAUTH = "tidal_oauth_json"
        private const val KEY_AMAZON_TOKEN = "amazon_token"
        private const val KEY_COMMUNITY_SESSION = "community_relay_session_json"
        private const val KEY_CUSTOM_GATEWAY = "custom_gateway_url"
        private val tidalRefreshLock = Any()

        const val HEADER_QOBUZ_TOKEN = "X-Qobuz-Token"
        const val HEADER_DEEZER_ARL = "X-Deezer-Arl"
        const val HEADER_TIDAL_TOKEN = "X-Tidal-Token"
        const val HEADER_AMAZON_TOKEN = "X-Amazon-Token"
        const val HEADER_GATEWAY_URL = "X-Gateway-Url"
    }
}
