package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "songs")
data class Song(
    @PrimaryKey
    val id: String,
    val title: String,
    val artist: String,
    val album: String,
    val durationMs: Long,
    val filePath: String,
    val coverArtUrl: String? = null,
    val youtubeVideoId: String? = null,
    val youtubeChannel: String? = null,
    val isDownloaded: Boolean = true,
    val downloadedAt: Long = System.currentTimeMillis(),
    val isFavorite: Boolean = false,
    val musicBrainzScore: Int = 0,
    val releaseId: String? = null,
    val bitrate: String = "192 kbps",
    val fileSizeBytes: Long = 0L,
    val lyricsLrc: String? = null,
    val lrcFilePath: String? = null
)
