package com.audiophile.musicplayer.update

import com.audiophile.musicplayer.BuildConfig
import com.audiophile.musicplayer.data.source.external.SpotiFlacEndpoints
import com.google.gson.JsonParseException
import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit

class AppUpdateRepository(
    private val gatewayBase: String = SpotiFlacEndpoints.DEFAULT_GATEWAY_BASE_URL,
    private val currentVersionCode: Int = BuildConfig.VERSION_CODE,
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .callTimeout(25, TimeUnit.SECONDS)
        .addInterceptor(com.audiophile.musicplayer.playback.GatewayApiKeyInterceptor)
        .build(),
    private val downloadClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.MINUTES)
        .callTimeout(6, TimeUnit.MINUTES)
        .followRedirects(true)
        .followSslRedirects(true)
        .addInterceptor(com.audiophile.musicplayer.playback.GatewayApiKeyInterceptor)
        .build()
) {
    suspend fun fetchManifest(): AppReleaseManifest? = withContext(Dispatchers.IO) {
        val url = gatewayBase.trim().trimEnd('/') + "/app/update"
        val request = Request.Builder().url(url).get().build()
        try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext null
                val body = response.body?.string().orEmpty()
                if (body.isBlank()) return@withContext null
                parseManifest(body)
            }
        } catch (_: IOException) {
            null
        } catch (_: JsonParseException) {
            null
        } catch (_: IllegalStateException) {
            null
        } catch (_: NumberFormatException) {
            null
        }
    }

    fun isNewer(manifest: AppReleaseManifest): Boolean =
        manifest.versionCode > currentVersionCode && manifest.apkUrl.isNotBlank()

    suspend fun downloadApk(manifest: AppReleaseManifest, destination: File): File? =
        withContext(Dispatchers.IO) {
            if (manifest.apkUrl.isBlank()) return@withContext null
            val parent = destination.parentFile
            if (parent != null && !parent.exists() && !parent.mkdirs()) return@withContext null
            val request = Request.Builder().url(manifest.apkUrl).get().build()
            try {
                downloadClient.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) return@withContext null
                    val body = response.body ?: return@withContext null
                    body.byteStream().use { input ->
                        destination.outputStream().use { output ->
                            input.copyTo(output)
                        }
                    }
                    if (destination.length() == 0L) return@withContext null
                    destination
                }
            } catch (_: IOException) {
                null
            }
        }

    companion object {
        fun parseManifest(body: String): AppReleaseManifest? {
            val root = JsonParser.parseString(body)?.asJsonObject ?: return null
            val versionCode = root.get("versionCode")?.asInt ?: return null
            val apkUrl = root.get("apkUrl")?.asString?.trim().orEmpty()
            if (versionCode <= 0) return null
            return AppReleaseManifest(
                versionCode = versionCode,
                versionName = root.get("versionName")?.asString?.trim().orEmpty().ifBlank { versionCode.toString() },
                apkUrl = apkUrl,
                donateUrl = root.get("donateUrl")?.asString?.trim().orEmpty(),
                changelog = root.get("changelog")?.asString?.trim().orEmpty()
            )
        }
    }
}
