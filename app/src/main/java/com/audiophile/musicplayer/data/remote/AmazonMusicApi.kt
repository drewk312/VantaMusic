package com.audiophile.musicplayer.data.remote

import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST

interface AmazonMusicApi {
    
    /**
     * Placeholder contract for a future user-authorized source resolver.
     */
    @POST("api/hifi/stream")
    suspend fun getStreamContent(
        @Header("x-amz-access-token") token: String, // Anonymous/device token
        @Header("User-Agent") userAgent: String = "AmazonMusic/17.7.2 Mozilla/5.0 (Linux; Android 14)",
        @Body request: AmazonStreamRequest
    ): AmazonStreamResponse
}

data class AmazonStreamRequest(
    val asin: String,
    val audioQuality: String = "HD", // HD = FLAC, SD = OPUS
    val deviceType: String = "A1MPSLFC7L5AFK" // Extracted Android music app device type
)

data class AmazonStreamResponse(
    val streamingUrl: String?,
    val codec: String?,
    val bitRate: Int?
)
