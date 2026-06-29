package com.audiophile.musicplayer.account

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import androidx.core.content.edit

/**
 * Manages the local user profile / account state.
 *
 * VANTA must work without an account — playback is never blocked behind sign-in.
 * This lightweight manager stores a local display name and preferences.
 * Future: integrate with a cloud auth provider (Firebase Auth, Auth0, etc.)
 * for cross-device sync of library, sources, and listening history.
 */
class AccountManager(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("vanta_account", Context.MODE_PRIVATE)

    private val _profile = MutableStateFlow(loadProfile())
    val profile: StateFlow<UserProfile> = _profile.asStateFlow()

    data class UserProfile(
        val displayName: String = "",
        val email: String = "",
        val isOnboarded: Boolean = false,
        val avatarSeed: String = "", // used for deterministic avatar color
        val sourceSyncEnabled: Boolean = false,
        val historySyncEnabled: Boolean = false,
        val appleMusicUserToken: String? = null,
        val appleMusicStorefront: String? = null
    )

    /**
     * Whether the user has completed basic onboarding (set a display name).
     */
    val isSignedIn: Boolean
        get() = _profile.value.isOnboarded && _profile.value.displayName.isNotBlank()

    /**
     * Sets the user's display name and marks them as onboarded.
     */
    fun completeOnboarding(displayName: String) {
        if (displayName.isBlank()) return
        val updated = _profile.value.copy(
            displayName = displayName.trim(),
            isOnboarded = true,
            avatarSeed = displayName.trim().take(2).uppercase()
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

    /**
     * Signs out — clears profile but keeps local library intact.
     */
    fun signOut() {
        prefs.edit {
                clear()
            }
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
            displayName = prefs.getString("display_name", "") ?: "",
            email = prefs.getString("email", "") ?: "",
            isOnboarded = prefs.getBoolean("is_onboarded", false),
            avatarSeed = prefs.getString("avatar_seed", "") ?: "",
            sourceSyncEnabled = prefs.getBoolean("source_sync_enabled", false),
            historySyncEnabled = prefs.getBoolean("history_sync_enabled", false),
            appleMusicUserToken = prefs.getString("apple_music_user_token", null),
            appleMusicStorefront = prefs.getString("apple_music_storefront", null)
        )
    }

    private fun saveProfile(profile: UserProfile) {
        prefs.edit {
                putString("display_name", profile.displayName)
                putString("email", profile.email)
                putBoolean("is_onboarded", profile.isOnboarded)
                putString("avatar_seed", profile.avatarSeed)
                putBoolean("source_sync_enabled", profile.sourceSyncEnabled)
                putBoolean("history_sync_enabled", profile.historySyncEnabled)
                putString("apple_music_user_token", profile.appleMusicUserToken)
                putString("apple_music_storefront", profile.appleMusicStorefront)
            }
    }
}