package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "download_history")
data class DownloadHistoryItem(
    @PrimaryKey val id: String,
    val videoId: String,
    val title: String,
    val channel: String = "",
    val filePath: String = "",
    val downloadedAt: Long = System.currentTimeMillis(),
    val status: String = "completed"
)
