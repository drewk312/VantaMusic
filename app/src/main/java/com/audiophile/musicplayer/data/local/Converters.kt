package com.audiophile.musicplayer.data.local

import androidx.room.TypeConverter
import com.audiophile.musicplayer.data.importer.ImportMatchStatus
import com.audiophile.musicplayer.data.importer.MatchConfidence
import com.audiophile.musicplayer.data.importer.PlayabilityStatus
import com.audiophile.musicplayer.data.local.entities.SourceType
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

class Converters {
    private val gson = Gson()
    private val stringListType = object : TypeToken<List<String>>() {}.type

    @TypeConverter
    fun sourceTypeToString(value: SourceType): String = value.name

    @TypeConverter
    fun stringToSourceType(value: String): SourceType = SourceType.valueOf(value)

    @TypeConverter
    fun matchStatusToString(value: ImportMatchStatus): String = value.name

    @TypeConverter
    fun stringToMatchStatus(value: String): ImportMatchStatus = ImportMatchStatus.valueOf(value)

    @TypeConverter
    fun playabilityStatusToString(value: PlayabilityStatus): String = value.name

    @TypeConverter
    fun stringToPlayabilityStatus(value: String): PlayabilityStatus = PlayabilityStatus.valueOf(value)

    @TypeConverter
    fun matchConfidenceToString(value: MatchConfidence): String = value.name

    @TypeConverter
    fun stringToMatchConfidence(value: String): MatchConfidence = MatchConfidence.valueOf(value)

    @TypeConverter
    fun stringListToJson(value: List<String>): String = gson.toJson(value)

    @TypeConverter
    fun jsonToStringList(value: String?): List<String> {
        if (value.isNullOrBlank()) return emptyList()
        return runCatching { gson.fromJson<List<String>>(value, stringListType) }.getOrDefault(emptyList())
    }
}
