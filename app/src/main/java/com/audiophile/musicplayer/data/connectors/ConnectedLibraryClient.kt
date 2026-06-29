package com.audiophile.musicplayer.data.connectors

interface ConnectedLibraryClient {
    val provider: ConnectedLibraryProvider
    val minimumImportScopes: Set<String>
    val minimumLikeSyncScopes: Set<String>
    val minimumPlaylistSyncScopes: Set<String>

    suspend fun fetchLibraryPage(
        account: ConnectedLibraryAccount,
        request: ConnectedLibraryImportRequest,
        cursor: String?
    ): ConnectedLibraryImportPage

    suspend fun findProviderTrackByIdentity(
        account: ConnectedLibraryAccount,
        isrc: String?,
        title: String,
        artist: String
    ): String?

    suspend fun saveLike(account: ConnectedLibraryAccount, providerTrackId: String)
}

object ConnectedLibraryConsentText {
    const val BEFORE_IMPORT =
        "VANTA imports music metadata like titles, artists, albums, playlists, artwork, and provider IDs so it can match your library to VANTA's own catalog. VANTA does not import passwords or play audio from these services."

    const val BEFORE_TASTE_USE =
        "VANTA uses a compact local taste profile from your connected library and VANTA listening activity. Your full library is not sent to the LLM by default."
}

object ConnectedLibraryLogRedactor {
    private val sensitivePatterns = listOf(
        Regex("(?i)(access_token|refresh_token|authorization|music_user_token|developer_token)=\\S+"),
        Regex("(?i)Bearer\\s+[A-Za-z0-9._\\-]+")
    )

    fun redact(message: String): String =
        sensitivePatterns.fold(message) { current, pattern ->
            pattern.replace(current) { match ->
                val key = match.value.substringBefore('=', missingDelimiterValue = "authorization")
                "$key=<redacted>"
            }
        }
}
