package com.audiophile.musicplayer.account

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import androidx.core.content.edit
import java.util.UUID

/**
 * Manages the local user profile / account state.
 *
 * VANTA works fully offline and anonymously — playback is never blocked behind
 * sign-in. This manager stores a local display name, a stable anonymous VANTA
 * user ID, and social/sync preferences. The ID can later be linked to a cloud
 * auth provider (Firebase, Apple, Google) for cross-device sync.
 */
class AccountManager(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("vanta_account", Context.MODE_PRIVATE)

    private val _profile = MutableStateFlow(loadProfile())
    val profile: StateFlow<UserProfile> = _profile.asStateFlow()

    data class UserProfile(
        val vantaUserId: String = "",
        val displayName: String = "",
        val email: String = "",
        val isOnboarded: Boolean = false,
        val avatarSeed: String = "",
        val isCloudAuthenticated: Boolean = false,
        val authProvider: String? = null,
        val sourceSyncEnabled: Boolean = false,
        val historySyncEnabled: Boolean = false,
        val shareListeningActivity: Boolean = false
    )

    /**
     * Whether the user has completed basic onboarding (set a display name).
     */
    val isSignedIn: Boolean
        get() = _profile.value.isCloudAuthenticated

    val cloudUserIdOrNull: String?
        get() = _profile.value.vantaUserId.takeIf { _profile.value.isCloudAuthenticated && it.isNotBlank() }

    fun linkCloudIdentity(userId: String, email: String?, displayName: String?, provider: String) {
        if (userId.isBlank()) return
        val existing = _profile.value
        val resolvedName = displayName?.trim().takeIf { !it.isNullOrBlank() }
            ?: existing.displayName.ifBlank { email?.substringBefore('@').orEmpty() }
        val updated = existing.copy(
            vantaUserId = userId,
            displayName = resolvedName,
            email = email.orEmpty(),
            isOnboarded = resolvedName.isNotBlank(),
            avatarSeed = resolvedName.take(2).uppercase(),
            isCloudAuthenticated = true,
            authProvider = provider
        )
        saveProfile(updated)
        _profile.value = updated
    }

    /**
     * Returns the stable VANTA user ID, generating one if necessary.
     */
    fun ensureUserId(): String {
        val current = _profile.value
        if (current.vantaUserId.isNotBlank()) return current.vantaUserId
        val generated = generateUserId()
        val updated = current.copy(vantaUserId = generated)
        saveProfile(updated)
        _profile.value = updated
        return generated
    }

    /**
     * Sets the user's display name and marks them as onboarded.
     */
    fun completeOnboarding(displayName: String) {
        if (displayName.isBlank()) return
        val userId = ensureUserId()
        val trimmed = displayName.trim()
        val updated = _profile.value.copy(
            vantaUserId = userId,
            displayName = trimmed,
            isOnboarded = true,
            avatarSeed = trimmed.take(2).uppercase()
        )
        saveProfile(updated)
        _profile.value = updated
    }

    /**
     * Updates sync preferences.
     */
    fun setSourceSyncEnabled(enabled: Boolean) {
        val updated = _profile.value.copy(sourceSyncEnabled = enabled)
        saveProfile(updated)
        _profile.value = updated
    }

    fun setHistorySyncEnabled(enabled: Boolean) {
        val updated = _profile.value.copy(historySyncEnabled = enabled)
        saveProfile(updated)
        _profile.value = updated
    }

    fun setShareListeningActivity(enabled: Boolean) {
        val updated = _profile.value.copy(shareListeningActivity = enabled)
        saveProfile(updated)
        _profile.value = updated
    }

    /**
     * Signs out — clears profile but keeps local library intact.
     */
    fun signOut() {
        prefs.edit { clear() }
        _profile.value = UserProfile()
    }

    private fun loadProfile(): UserProfile {
        return UserProfile(
            vantaUserId = prefs.getString("vanta_user_id", "") ?: "",
            displayName = prefs.getString("display_name", "") ?: "",
            email = prefs.getString("email", "") ?: "",
            isOnboarded = prefs.getBoolean("is_onboarded", false),
            avatarSeed = prefs.getString("avatar_seed", "") ?: "",
            isCloudAuthenticated = prefs.getBoolean("cloud_authenticated", false),
            authProvider = prefs.getString("auth_provider", null),
            sourceSyncEnabled = prefs.getBoolean("source_sync_enabled", false),
            historySyncEnabled = prefs.getBoolean("history_sync_enabled", false),
            shareListeningActivity = prefs.getBoolean("share_listening_activity", false)
        )
    }

    private fun saveProfile(profile: UserProfile) {
        prefs.edit {
            putString("vanta_user_id", profile.vantaUserId)
            putString("display_name", profile.displayName)
            putString("email", profile.email)
            putBoolean("is_onboarded", profile.isOnboarded)
            putString("avatar_seed", profile.avatarSeed)
            putBoolean("cloud_authenticated", profile.isCloudAuthenticated)
            putString("auth_provider", profile.authProvider)
            putBoolean("source_sync_enabled", profile.sourceSyncEnabled)
            putBoolean("history_sync_enabled", profile.historySyncEnabled)
            putBoolean("share_listening_activity", profile.shareListeningActivity)
            remove("apple_music_user_token")
            remove("apple_music_storefront")
        }
    }

    private fun generateUserId(): String =
        "vanta_${UUID.randomUUID().toString().replace("-", "").take(20)}"
}
