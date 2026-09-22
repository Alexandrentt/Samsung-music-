package com.example.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface DownloadHistoryDao {
    @Query("SELECT * FROM download_history ORDER BY downloadedAt DESC")
    fun getAllHistory(): Flow<List<DownloadHistoryItem>>

    @Query("SELECT * FROM download_history WHERE videoId = :videoId LIMIT 1")
    suspend fun getByVideoId(videoId: String): DownloadHistoryItem?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertHistory(item: DownloadHistoryItem)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<DownloadHistoryItem>)

    @Delete
    suspend fun deleteHistory(item: DownloadHistoryItem)

    @Query("DELETE FROM download_history WHERE videoId = :videoId")
    suspend fun deleteByVideoId(videoId: String)

    @Query("DELETE FROM download_history")
    suspend fun clearHistory()

    @Query("SELECT COUNT(*) FROM download_history")
    suspend fun getHistoryCount(): Int
}
