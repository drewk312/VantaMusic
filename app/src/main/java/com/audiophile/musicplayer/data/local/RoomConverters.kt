package com.audiophile.musicplayer.data.local

import androidx.room.TypeConverter
import com.audiophile.musicplayer.data.local.entities.SourceType

class RoomConverters {

    @TypeConverter
    fun fromSourceType(value: SourceType?): String? = value?.name

    @TypeConverter
    fun toSourceType(value: String?): SourceType? {
        return value?.let { runCatching { SourceType.valueOf(it) }.getOrNull() }
    }
}
