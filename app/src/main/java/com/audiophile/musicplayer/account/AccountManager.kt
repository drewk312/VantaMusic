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
 * VANTA must work without an account — playback is never blocked behind sign-in.
 * This lightweight manager stores a local display name, a stable anonymous VANTA
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
        val sourceSyncEnabled: Boolean = false,
        val historySyncEnabled: Boolean = false,
        val shareListeningActivity: Boolean = false,
        val appleMusicUserToken: String? = null,
        val appleMusicStorefront: String? = null
    )

    /**
     * Whether the user has completed basic onboarding (set a display name).
     */
    val isSignedIn: Boolean
        get() = _profile.value.isOnboarded && _profile.value.displayName.isNotBlank()

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

    fun setAppleMusicSession(userToken: String?, storefront: String?) {
        val updated = _profile.value.copy(
            appleMusicUserToken = userToken,
            appleMusicStorefront = storefront
        )
        saveProfile(updated)
        _profile.value = updated
    }

    private fun loadProfile(): UserProfile {
        return UserProfile(
            vantaUserId = prefs.getString("vanta_user_id", "") ?: "",
            displayName = prefs.getString("display_name", "") ?: "",
            email = prefs.getString("email", "") ?: "",
            isOnboarded = prefs.getBoolean("is_onboarded", false),
            avatarSeed = prefs.getString("avatar_seed", "") ?: "",
            sourceSyncEnabled = prefs.getBoolean("source_sync_enabled", false),
            historySyncEnabled = prefs.getBoolean("history_sync_enabled", false),
            shareListeningActivity = prefs.getBoolean("share_listening_activity", false),
            appleMusicUserToken = prefs.getString("apple_music_user_token", null),
            appleMusicStorefront = prefs.getString("apple_music_storefront", null)
        )
    }

    private fun saveProfile(profile: UserProfile) {
        prefs.edit {
            putString("vanta_user_id", profile.vantaUserId)
            putString("display_name", profile.displayName)
            putString("email", profile.email)
            putBoolean("is_onboarded", profile.isOnboarded)
            putString("avatar_seed", profile.avatarSeed)
            putBoolean("source_sync_enabled", profile.sourceSyncEnabled)
            putBoolean("history_sync_enabled", profile.historySyncEnabled)
            putBoolean("share_listening_activity", profile.shareListeningActivity)
            putString("apple_music_user_token", profile.appleMusicUserToken)
            putString("apple_music_storefront", profile.appleMusicStorefront)
        }
    }

    private fun generateUserId(): String =
        "vanta_${UUID.randomUUID().toString().replace("-", "").take(20)}"
}
