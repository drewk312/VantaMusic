package com.audiophile.musicplayer.data.local.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Describes a configured resolver/playback provider or addon endpoint.
 */
@Entity(tableName = "addon_providers")
data class AddonProvider(
    @PrimaryKey
    val providerId: String,
    val displayName: String,
    val baseUrl: String? = null,
    val providerKind: String = "addon",
    val capabilitySearch: Boolean = true,
    val capabilityPlayback: Boolean = false,
    val capabilityDownload: Boolean = false,
    val capabilityRadio: Boolean = false,
    val isEnabled: Boolean = true,
    val healthStatus: String = "unknown",
    val lastCheckedAtEpochMs: Long? = null,
    val avgResolveMs: Long? = null,
    val notesJson: String? = null
)
