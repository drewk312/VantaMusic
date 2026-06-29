package com.audiophile.musicplayer.data.lyrics

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface LyricsCacheDao {
    @Query("SELECT * FROM lyrics_cache WHERE lyricsKey = :key LIMIT 1")
    suspend fun getLyrics(key: String): LyricsCacheEntity?

    @Query("SELECT * FROM lyrics_cache WHERE isrc = :isrc LIMIT 1")
    suspend fun getLyricsByIsrc(isrc: String): LyricsCacheEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLyrics(lyrics: LyricsCacheEntity)

    @Query("DELETE FROM lyrics_cache WHERE lyricsKey = :key")
    suspend fun deleteLyrics(key: String)

    @Query("DELETE FROM lyrics_cache WHERE isrc = :isrc")
    suspend fun deleteLyricsByIsrc(isrc: String)
    
    @Query("DELETE FROM lyrics_cache")
    suspend fun clearAll()
}
