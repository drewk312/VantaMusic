package com.audiophile.musicplayer.data.dj

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** Station-scoped taste — thumbs on Yacht Rock don't poison global recommendations. */
class StationTasteMemory(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("vanta_station_taste", Context.MODE_PRIVATE)

    data class StationSignal(
        val stationId: String,
        val trackId: Long,
        val artist: String,
        val positive: Boolean,
        val timestamp: Long
    )

    fun savedStationIds(): List<String> =
        prefs.getString("saved_stations", "").orEmpty()
            .split("|")
            .filter { it.isNotBlank() }

    fun saveStation(stationId: String) {
        val current = savedStationIds().toMutableSet()
        current.add(stationId)
        prefs.edit().putString("saved_stations", current.joinToString("|")).apply()
    }

    fun recordStationLike(stationId: String, trackId: Long, artist: String) {
        appendSignal(StationSignal(stationId, trackId, artist, positive = true, timestamp = System.currentTimeMillis()))
    }

    fun recordStationSkip(stationId: String, trackId: Long, artist: String) {
        appendSignal(StationSignal(stationId, trackId, artist, positive = false, timestamp = System.currentTimeMillis()))
    }

    fun stationArtistAffinity(stationId: String, artist: String): Float {
        val key = artist.trim().lowercase()
        if (key.isBlank()) return 0f
        var score = 0f
        for (signal in loadSignals(stationId)) {
            if (signal.artist.trim().lowercase() != key) continue
            score += if (signal.positive) 2f else -2f
        }
        return score
    }

    fun stationTrackAffinity(stationId: String, trackId: Long): Float {
        var score = 0f
        for (signal in loadSignals(stationId)) {
            if (signal.trackId != trackId) continue
            score += if (signal.positive) 2.5f else -2.5f
        }
        return score
    }

    private fun appendSignal(signal: StationSignal) {
        val key = "signals_${signal.stationId}"
        val updated = (listOf(signal) + loadSignals(signal.stationId)).take(60)
        prefs.edit().putString(key, encodeSignals(updated)).apply()
    }

    private fun loadSignals(stationId: String): List<StationSignal> {
        val raw = prefs.getString("signals_$stationId", null)
        if (raw.isNullOrBlank()) return emptyList()
        return try {
            decodeSignals(raw)
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun encodeSignals(signals: List<StationSignal>): String {
        val array = JSONArray()
        signals.forEach { s ->
            array.put(
                JSONObject()
                    .put("stationId", s.stationId)
                    .put("trackId", s.trackId)
                    .put("artist", s.artist)
                    .put("positive", s.positive)
                    .put("timestamp", s.timestamp)
            )
        }
        return array.toString()
    }

    private fun decodeSignals(raw: String): List<StationSignal> {
        val array = JSONArray(raw)
        val out = mutableListOf<StationSignal>()
        for (i in 0 until array.length()) {
            val obj = array.getJSONObject(i)
            out.add(
                StationSignal(
                    stationId = obj.getString("stationId"),
                    trackId = obj.getLong("trackId"),
                    artist = obj.getString("artist"),
                    positive = obj.getBoolean("positive"),
                    timestamp = obj.optLong("timestamp", 0L)
                )
            )
        }
        return out
    }
}
