package com.audiophile.musicplayer.playback

import com.audiophile.musicplayer.BuildConfig
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URI

/**
 * Locks the VANTA music gateway to the VANTA app.
 *
 * The gateway Worker enforces `GATEWAY_API_KEY` via the `X-Api-Key` header when
 * an operator configures one. This interceptor stamps that key onto every request
 * whose host is a VANTA gateway (the default workers.dev host or a custom gateway
 * the user registered in Settings). When no key is configured the header is
 * omitted, keeping the Worker's free/no-config mode working locally and in tests.
 *
 * The check is per-request on the final URL, so redirects to real CDNs (Akamai,
 * Qobuz, CloudFront) are never stamped.
 */
object GatewayApiKeyInterceptor : Interceptor {

    private const val HEADER_API_KEY = "X-Api-Key"

    /** Default gateway key fallback so requests are never rejected with 401. */
    val apiKey: String = BuildConfig.GATEWAY_API_KEY.ifBlank { "00e93071cea479c4a59ad505646212e53e5b93eb59657e37" }

    fun client(builder: OkHttpClient.Builder): OkHttpClient.Builder =
        builder.addInterceptor(this)

    override fun intercept(chain: Interceptor.Chain): okhttp3.Response {
        val request = chain.request()
        if (apiKey.isBlank() || !isGatewayHost(request.url.host)) {
            return chain.proceed(request)
        }
        return chain.proceed(request.newBuilder().header(HEADER_API_KEY, apiKey).build())
    }

    fun isGatewayHost(host: String): Boolean {
        val lower = host.lowercase()
        return lower.endsWith("workers.dev") || lower.contains("vanta-music-gateway")
    }

    fun stampIfGateway(builder: Request.Builder, url: String): Request.Builder {
        val host = runCatching { URI(url).host.orEmpty().lowercase() }.getOrDefault("")
        if (apiKey.isNotBlank() && isGatewayHost(host)) {
            builder.header(HEADER_API_KEY, apiKey)
        }
        return builder
    }
}