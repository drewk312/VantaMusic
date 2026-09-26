package com.audiophile.musicplayer.data.connectors.spotify

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.util.Log
import androidx.browser.customtabs.CustomTabsIntent
import com.audiophile.musicplayer.data.connectors.ConnectedLibraryProvider
import com.audiophile.musicplayer.data.connectors.ConnectedLibraryTokenStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.UUID

/**
 * Manages Spotify OAuth 2.0 PKCE authentication flow for VANTA.
 *
 * Implements standard PKCE (Proof Key for Code Exchange) RFC 7636:
 * 1. Generates cryptographic code verifier and SHA-256 code challenge.
 * 2. Opens Spotify authorization endpoint in Custom Tabs / default browser.
 * 3. Catches redirect intent at vanta://spotify-callback.
 * 4. Exchanges authorization code + code verifier for access & refresh tokens.
 * 5. Securely saves tokens into [ConnectedLibraryTokenStore].
 * 6. Handles seamless automatic token refreshing before expiry.
 */
class SpotifyOAuthManager(
    private val context: Context,
    private val tokenStore: ConnectedLibraryTokenStore,
    private val httpClient: OkHttpClient = OkHttpClient()
) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("vanta_spotify_oauth", Context.MODE_PRIVATE)

    companion object {
        private const val TAG = "VANTA_SPOTIFY_OAUTH"
        const val DEFAULT_CLIENT_ID = "b9f2913fdbb64417a86f78816c729517"
        const val REDIRECT_URI = "vanta://spotify-callback"
        const val AUTH_ENDPOINT = "https://accounts.spotify.com/authorize"
        const val TOKEN_ENDPOINT = "https://accounts.spotify.com/api/token"

        const val SCOPES = "user-read-private user-read-email user-library-read user-library-modify playlist-read-private playlist-read-collaborative playlist-modify-public playlist-modify-private user-top-read"

        private const val KEY_CUSTOM_CLIENT_ID = "spotify_custom_client_id"
        private const val KEY_PENDING_VERIFIER = "pending_code_verifier"
        private const val KEY_PENDING_STATE = "pending_state"
    }

    /**
     * Active client ID: user custom ID if set, otherwise built-in default.
     */
    fun getClientId(): String {
        return prefs.getString(KEY_CUSTOM_CLIENT_ID, null)?.trim()?.takeIf { it.isNotBlank() }
            ?: DEFAULT_CLIENT_ID
    }

    /**
     * Store or clear a custom Spotify Client ID.
     */
    fun setCustomClientId(clientId: String?) {
        val trimmed = clientId?.trim()
        if (trimmed.isNullOrBlank()) {
            prefs.edit().remove(KEY_CUSTOM_CLIENT_ID).apply()
        } else {
            prefs.edit().putString(KEY_CUSTOM_CLIENT_ID, trimmed).apply()
        }
    }

    fun hasCustomClientId(): Boolean =
        !prefs.getString(KEY_CUSTOM_CLIENT_ID, null).isNullOrBlank()

    /**
     * Generates a 64-byte URL-safe base64 unpadded PKCE code verifier (RFC 7636).
     */
    fun generateCodeVerifier(): String {
        val randomBytes = ByteArray(64)
        SecureRandom().nextBytes(randomBytes)
        return java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes)
    }

    /**
     * Computes the SHA-256 code challenge for the given verifier.
     */
    fun generateCodeChallenge(verifier: String): String {
        val bytes = verifier.toByteArray(Charsets.US_ASCII)
        val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
        return java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(digest)
    }

    /**
     * Generates a random state string for CSRF mitigation.
     */
    fun generateState(): String = UUID.randomUUID().toString()

    /**
     * Constructs the authorization URL for Spotify PKCE.
     */
    fun buildAuthorizeUri(challenge: String, state: String): Uri {
        return Uri.parse(AUTH_ENDPOINT).buildUpon()
            .appendQueryParameter("client_id", getClientId())
            .appendQueryParameter("response_type", "code")
            .appendQueryParameter("redirect_uri", REDIRECT_URI)
            .appendQueryParameter("state", state)
            .appendQueryParameter("scope", SCOPES)
            .appendQueryParameter("code_challenge_method", "S256")
            .appendQueryParameter("code_challenge", challenge)
            .build()
    }

    /**
     * Launches the Spotify authorization in an in-app Custom Tab or system browser.
     */
    fun launchLogin(activityContext: Context) {
        val verifier = generateCodeVerifier()
        val challenge = generateCodeChallenge(verifier)
        val state = generateState()

        prefs.edit()
            .putString(KEY_PENDING_VERIFIER, verifier)
            .putString(KEY_PENDING_STATE, state)
            .apply()

        val authUri = buildAuthorizeUri(challenge, state)
        Log.i(TAG, "Launching Spotify PKCE login with client_id=${getClientId()} redirect=$REDIRECT_URI")

        try {
            val customTabsIntent = CustomTabsIntent.Builder()
                .setShowTitle(true)
                .build()
            customTabsIntent.launchUrl(activityContext, authUri)
        } catch (e: Exception) {
            Log.w(TAG, "Custom Tabs launch failed, falling back to standard ACTION_VIEW intent", e)
            val intent = Intent(Intent.ACTION_VIEW, authUri).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            activityContext.startActivity(intent)
        }
    }

    /**
     * Processes incoming deep link redirect from Spotify (vanta://spotify-callback).
     */
    suspend fun handleCallback(uri: Uri): Result<SpotifyAuthTokens> = withContext(Dispatchers.IO) {
        val error = uri.getQueryParameter("error")
        if (!error.isNullOrBlank()) {
            Log.e(TAG, "Spotify authorization error: $error")
            return@withContext Result.failure(IllegalStateException("Spotify authorization denied: $error"))
        }

        val returnedState = uri.getQueryParameter("state")
        val savedState = prefs.getString(KEY_PENDING_STATE, null)
        if (savedState.isNullOrBlank() || returnedState != savedState) {
            Log.e(TAG, "Spotify state mismatch. returned=$returnedState saved=$savedState")
            return@withContext Result.failure(IllegalStateException("Spotify authorization failed: state mismatch (CSRF protection)."))
        }

        val code = uri.getQueryParameter("code")
        if (code.isNullOrBlank()) {
            return@withContext Result.failure(IllegalStateException("Spotify did not return an authorization code."))
        }

        val verifier = prefs.getString(KEY_PENDING_VERIFIER, null)
        if (verifier.isNullOrBlank()) {
            return@withContext Result.failure(IllegalStateException("Missing PKCE code verifier for Spotify token exchange."))
        }

        val formBody = FormBody.Builder()
            .add("grant_type", "authorization_code")
            .add("code", code)
            .add("redirect_uri", REDIRECT_URI)
            .add("client_id", getClientId())
            .add("code_verifier", verifier)
            .build()

        val request = Request.Builder()
            .url(TOKEN_ENDPOINT)
            .post(formBody)
            .header("Content-Type", "application/x-www-form-urlencoded")
            .build()

        try {
            val response = httpClient.newCall(request).execute()
            val bodyString = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                Log.e(TAG, "Token exchange failed: HTTP ${response.code} $bodyString")
                return@withContext Result.failure(IllegalStateException("Spotify token exchange failed (${response.code}): $bodyString"))
            }

            val json = JSONObject(bodyString)
            val accessToken = json.getString("access_token")
            val refreshToken = json.optString("refresh_token").takeIf { it.isNotBlank() }
            val expiresIn = json.optLong("expires_in", 3600L)
            val expiresAtMs = System.currentTimeMillis() + (expiresIn * 1000L)

            val stored = tokenStore.storeTokens(
                provider = ConnectedLibraryProvider.SPOTIFY,
                accessToken = accessToken,
                refreshToken = refreshToken,
                expiresAtMs = expiresAtMs
            )

            if (!stored) {
                Log.e(TAG, "Failed to persist Spotify tokens to secure token store")
                return@withContext Result.failure(IllegalStateException("Failed to securely store Spotify credentials on device."))
            }

            // Cleanup pending state
            prefs.edit()
                .remove(KEY_PENDING_STATE)
                .remove(KEY_PENDING_VERIFIER)
                .apply()

            Log.i(TAG, "Spotify OAuth PKCE authentication successful! Expires in ${expiresIn}s")
            Result.success(SpotifyAuthTokens(accessToken, refreshToken, expiresAtMs))
        } catch (e: Exception) {
            Log.e(TAG, "Network error during Spotify token exchange", e)
            Result.failure(e)
        }
    }

    /**
     * Refreshes the Spotify access token using the stored refresh token.
     */
    suspend fun refreshAccessToken(): Result<String> = withContext(Dispatchers.IO) {
        val refreshToken = tokenStore.refreshToken(ConnectedLibraryProvider.SPOTIFY)
            ?: return@withContext Result.failure(IllegalStateException("No Spotify refresh token stored"))

        val formBody = FormBody.Builder()
            .add("grant_type", "refresh_token")
            .add("refresh_token", refreshToken)
            .add("client_id", getClientId())
            .build()

        val request = Request.Builder()
            .url(TOKEN_ENDPOINT)
            .post(formBody)
            .header("Content-Type", "application/x-www-form-urlencoded")
            .build()

        try {
            val response = httpClient.newCall(request).execute()
            val bodyString = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                Log.e(TAG, "Spotify token refresh failed: HTTP ${response.code} $bodyString")
                return@withContext Result.failure(IllegalStateException("Token refresh failed: $bodyString"))
            }

            val json = JSONObject(bodyString)
            val newAccessToken = json.getString("access_token")
            val newRefreshToken = json.optString("refresh_token").takeIf { it.isNotBlank() } ?: refreshToken
            val expiresIn = json.optLong("expires_in", 3600L)
            val expiresAtMs = System.currentTimeMillis() + (expiresIn * 1000L)

            tokenStore.storeTokens(
                provider = ConnectedLibraryProvider.SPOTIFY,
                accessToken = newAccessToken,
                refreshToken = newRefreshToken,
                expiresAtMs = expiresAtMs
            )

            Log.i(TAG, "Spotify access token refreshed successfully")
            Result.success(newAccessToken)
        } catch (e: Exception) {
            Log.e(TAG, "Network error refreshing Spotify token", e)
            Result.failure(e)
        }
    }

    /**
     * Gets a valid access token, automatically refreshing if expired or expiring soon.
     */
    suspend fun getValidAccessToken(): String? {
        val currentToken = tokenStore.accessToken(ConnectedLibraryProvider.SPOTIFY) ?: return null
        val expiresAt = tokenStore.expiresAt(ConnectedLibraryProvider.SPOTIFY)
        val now = System.currentTimeMillis()

        // If expires in less than 2 minutes and refresh token exists, refresh
        if (expiresAt > 0 && now >= (expiresAt - 120_000L)) {
            val refreshResult = refreshAccessToken()
            return refreshResult.getOrNull() ?: currentToken
        }
        return currentToken
    }
}

data class SpotifyAuthTokens(
    val accessToken: String,
    val refreshToken: String?,
    val expiresAtMs: Long
)
