package com.example.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "songs")
data class Song(
    @PrimaryKey val id: String,
    val title: String,
    val artist: String,
    val album: String,
    val durationMs: Long = 180000L,
    val filePath: String = "",
    val coverArtUrl: String? = null,
    val youtubeVideoId: String? = null,
    val youtubeChannel: String? = null,
    val isDownloaded: Boolean = false,
    val downloadedAt: Long = System.currentTimeMillis(),
    val isFavorite: Boolean = false,
    val musicBrainzScore: Int = 0,
    val releaseId: String? = null,
    val bitrate: String = "320 kbps",
    val fileSizeBytes: Long = 0L,
    val lyricsLrc: String? = null,
    val lrcFilePath: String? = null,
    val playCount: Int = 0,
    val lastPlayedAt: Long? = null,
    val enrichmentScore: Int = 0,
    val dateAdded: Long = System.currentTimeMillis(),
    @ColumnInfo(defaultValue = "0") val titleManuallyEdited: Boolean = false,
    @ColumnInfo(defaultValue = "0") val artistManuallyEdited: Boolean = false,
    @ColumnInfo(defaultValue = "0") val albumManuallyEdited: Boolean = false
) {
    val durationSeconds: Long get() = if (durationMs > 0) durationMs / 1000 else 180L
    val hasLyrics: Boolean get() = !lrcFilePath.isNullOrBlank() || !lyricsLrc.isNullOrBlank()
    val lyricsPath: String? get() = lrcFilePath
}
