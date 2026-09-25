package com.audiophile.musicplayer.data.source.external

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.google.gson.JsonSyntaxException
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException

/**
 * Tidal PKCE / device-code tokens. Refresh is grant_type=refresh_token against
 * auth.tidal.com — the same pattern SpotiFLAC documents for OAuth providers.
 * Never log access or refresh tokens.
 */
object TidalOauthTokens {
    const val CLIENT_ID = "txNoH4kkV41MfH25"
    const val TOKEN_URL = "https://auth.tidal.com/v1/oauth2/token"
    const val REFRESH_SKEW_MS = 120_000L

    data class Snapshot(
        val accessToken: String,
        val refreshToken: String? = null,
        val expiresAtMs: Long? = null,
        val clientId: String? = null
    )

    fun parse(raw: String): Snapshot? {
        val value = raw.trim()
        if (value.isEmpty()) return null
        if (value.startsWith("{")) {
            return try {
                val obj = JsonParser.parseString(value).asJsonObject
                val access = stringField(obj, "access_token") ?: stringField(obj, "accessToken")
                if (access.isNullOrBlank()) return null
                Snapshot(
                    accessToken = access,
                    refreshToken = stringField(obj, "refresh_token") ?: stringField(obj, "refreshToken"),
                    expiresAtMs = expiryMs(obj),
                    clientId = stringField(obj, "client_id") ?: stringField(obj, "clientId")
                )
            } catch (_: JsonSyntaxException) {
                null
            } catch (_: IllegalStateException) {
                null
            }
        }
        if (value.length >= 16 && !value.contains(' ')) return Snapshot(accessToken = value)
        return null
    }

    fun needsRefresh(snapshot: Snapshot, nowMs: Long): Boolean {
        if (snapshot.refreshToken.isNullOrBlank()) return false
        val expiresAt = snapshot.expiresAtMs ?: return true
        return expiresAt - nowMs <= REFRESH_SKEW_MS
    }

    fun mergeStoredJson(previousRaw: String, next: Snapshot, nowMs: Long): String {
        val obj = try {
            val element = JsonParser.parseString(previousRaw.trim())
            if (element.isJsonObject) element.asJsonObject else JsonObject()
        } catch (_: JsonSyntaxException) {
            JsonObject()
        }
        obj.addProperty("access_token", next.accessToken)
        val refresh = next.refreshToken
        if (!refresh.isNullOrBlank()) obj.addProperty("refresh_token", refresh)
        next.clientId?.takeIf { it.isNotBlank() }?.let { obj.addProperty("client_id", it) }
        next.expiresAtMs?.let { expiresAtMs ->
            obj.addProperty("expires_at", expiresAtMs / 1000)
            val expiresIn = ((expiresAtMs - nowMs) / 1000).coerceAtLeast(0)
            obj.addProperty("expires_in", expiresIn)
        }
        return obj.toString()
    }

    fun parseRefreshResponse(body: String, previous: Snapshot, nowMs: Long): Snapshot? {
        return try {
            val obj = JsonParser.parseString(body).asJsonObject
            val access = stringField(obj, "access_token") ?: return null
            val refresh = stringField(obj, "refresh_token") ?: previous.refreshToken
            val expiresIn = numberField(obj, "expires_in")
            Snapshot(
                accessToken = access,
                refreshToken = refresh,
                expiresAtMs = expiresIn?.let { nowMs + it * 1000 },
                clientId = previous.clientId
            )
        } catch (_: JsonSyntaxException) {
            null
        } catch (_: IllegalStateException) {
            null
        }
    }

    fun refresh(http: OkHttpClient, previous: Snapshot): Snapshot? {
        val refreshToken = previous.refreshToken?.takeIf { it.isNotBlank() } ?: return null
        val request = Request.Builder()
            .url(TOKEN_URL)
            .header("Accept", "application/json")
            .header("User-Agent", "VANTA/1.0")
            .post(
                FormBody.Builder()
                    .add("grant_type", "refresh_token")
                    .add("refresh_token", refreshToken)
                    .add("client_id", previous.clientId?.takeIf { it.isNotBlank() } ?: CLIENT_ID)
                    .build()
            )
            .build()
        return try {
            http.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@use null
                parseRefreshResponse(response.body?.string().orEmpty(), previous, System.currentTimeMillis())
            }
        } catch (_: IOException) {
            null
        }
    }

    private fun stringField(obj: JsonObject, key: String): String? {
        if (!obj.has(key) || obj.get(key).isJsonNull) return null
        val value = obj.get(key)
        return if (value.isJsonPrimitive) value.asString.trim().takeIf { it.isNotEmpty() } else null
    }

    private fun numberField(obj: JsonObject, key: String): Long? {
        if (!obj.has(key) || obj.get(key).isJsonNull) return null
        val value = obj.get(key)
        if (!value.isJsonPrimitive) return null
        val primitive = value.asJsonPrimitive
        if (primitive.isNumber) return primitive.asLong
        if (primitive.isString && primitive.asString.matches(Regex("^\\d+$"))) return primitive.asString.toLong()
        return null
    }

    private fun expiryMs(obj: JsonObject): Long? {
        val expiresAt = obj.get("expires_at") ?: obj.get("expiresAt")
        if (expiresAt != null && expiresAt.isJsonPrimitive) {
            val primitive = expiresAt.asJsonPrimitive
            if (primitive.isNumber) {
                val number = primitive.asDouble
                if (!number.isFinite()) return null
                return if (number < 32_000_000_000.0) (number * 1000).toLong() else number.toLong()
            }
            if (primitive.isString && primitive.asString.isNotBlank()) {
                val parsed = runCatching { java.time.Instant.parse(primitive.asString.trim()).toEpochMilli() }.getOrNull()
                if (parsed != null) return parsed
            }
        }
        val expiresIn = numberField(obj, "expires_in")
        return expiresIn?.let { System.currentTimeMillis() + it * 1000 }
    }
}
