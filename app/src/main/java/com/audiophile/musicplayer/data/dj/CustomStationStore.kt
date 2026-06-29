package com.audiophile.musicplayer.data.dj

import android.content.Context
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject

/** Persists user-built jukebox stations (multi-artist, artist seed, song seed). */
class CustomStationStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("vanta_custom_stations", Context.MODE_PRIVATE)

    fun allStations(): List<JukeboxStation> {
        val raw = prefs.getString("stations_json", null)
        if (raw.isNullOrBlank()) return emptyList()
        return try {
            decode(raw)
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun find(id: String): JukeboxStation? = allStations().find { it.id == id }

    fun save(station: JukeboxStation) {
        val current = allStations().filterNot { it.id == station.id }
        val updated = (current + station).takeLast(40)
        prefs.edit().putString("stations_json", encode(updated)).apply()
    }

    fun delete(id: String) {
        val updated = allStations().filterNot { it.id == id }
        prefs.edit().putString("stations_json", encode(updated)).apply()
    }

    private fun encode(stations: List<JukeboxStation>): String {
        val array = JSONArray()
        stations.forEach { s -> array.put(encodeStation(s)) }
        return array.toString()
    }

    private fun encodeStation(s: JukeboxStation): JSONObject =
        JSONObject()
            .put("id", s.id)
            .put("name", s.name)
            .put("description", s.description)
            .put("genreKeywords", JSONArray(s.genreKeywords))
            .put("decadeStart", s.decadeStart ?: JSONObject.NULL)
            .put("decadeEnd", s.decadeEnd ?: JSONObject.NULL)
            .put("emoji", s.emoji)
            .put("stationType", s.stationType.name)
            .put("seedArtists", JSONArray(s.seedArtists))
            .put("seedTrackIds", JSONArray(s.seedTrackIds))

    private fun decode(raw: String): List<JukeboxStation> {
        val array = JSONArray(raw)
        val out = mutableListOf<JukeboxStation>()
        for (i in 0 until array.length()) {
            try {
                val obj = array.getJSONObject(i)
                val genres = obj.optJSONArray("genreKeywords")?.let { arr ->
                    (0 until arr.length()).map { arr.getString(it) }
                } ?: emptyList()
                val artists = obj.optJSONArray("seedArtists")?.let { arr ->
                    (0 until arr.length()).map { arr.getString(it) }
                } ?: emptyList()
                val trackIds = obj.optJSONArray("seedTrackIds")?.let { arr ->
                    (0 until arr.length()).map { arr.getLong(it) }
                } ?: emptyList()
                val decadeStart = if (obj.isNull("decadeStart")) null else obj.optInt("decadeStart")
                val decadeEnd = if (obj.isNull("decadeEnd")) null else obj.optInt("decadeEnd")
                out.add(
                    JukeboxStation(
                        id = obj.getString("id"),
                        name = obj.getString("name"),
                        description = obj.getString("description"),
                        genreKeywords = genres,
                        decadeStart = decadeStart,
                        decadeEnd = decadeEnd,
                        emoji = obj.optString("emoji", "🎵"),
                        stationType = try {
                            JukeboxStationType.valueOf(
                                obj.optString("stationType", JukeboxStationType.PRESET.name)
                            )
                        } catch (_: IllegalArgumentException) {
                            JukeboxStationType.PRESET
                        },
                        seedArtists = artists,
                        seedTrackIds = trackIds
                    )
                )
            } catch (e: Exception) {
                Log.w("VANTA_STATION_STORE", "Skipping corrupt station at index $i: ${e.message}")
            }
        }
        return out
    }
}
