package com.example.worker

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.example.data.AppDatabase
import com.example.data.MusicRepository
import com.example.engine.MusicaEngine
import java.io.File
import java.util.concurrent.TimeUnit

class PlaylistSyncWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val context = applicationContext
        return try {
            val db = AppDatabase.getDatabase(context)
            val repository = MusicRepository(db.songDao(), db.downloadHistoryDao(), db.playlistDao())
            val engine = MusicaEngine(context)

            val playlistId = MusicaEngine.PLAYLIST_ID_DEFAULT

            val existingPlaylist = db.playlistDao().getPlaylistById(1L)
            val targetPlaylistId = if (existingPlaylist == null) {
                repository.createPlaylist(
                    name = "Mi Playlist de YouTube",
                    description = "Actualizada automáticamente a diario desde YouTube",
                    coverArtUrl = null
                )
            } else {
                1L
            }

            // Fetch ALL items from the YouTube playlist (InnerTube multi-page scraper)
            val items = engine.obtenerItemsPlaylist(playlistId)

            for (item in items) {
                if (isStopped) break // graceful handling if system stops work

                val songId = "yt_${item.videoId}"
                val existingSong = db.songDao().getSongById(songId)
                val fileExists = existingSong != null && File(existingSong.filePath).exists() && File(existingSong.filePath).length() > 0

                if (!fileExists) {
                    // Download & process
                    val downloaded = engine.procesarYGuardarAudio(item.videoId, item.title, item.channel, autoEnriquecer = false)
                    db.songDao().insertSong(downloaded)
                    repository.recordDownload(item.videoId, item.title, item.channel, downloaded.filePath)
                    repository.addSongToPlaylist(targetPlaylistId, downloaded.id)
                } else {
                    // Already downloaded, make sure it's linked to the playlist
                    val inPlaylist = repository.isSongInPlaylist(targetPlaylistId, songId)
                    if (!inPlaylist) {
                        repository.addSongToPlaylist(targetPlaylistId, songId)
                    }
                }
            }

            Result.success()
        } catch (e: Exception) {
            e.printStackTrace()
            Result.retry()
        }
    }

    companion object {
        const val WORK_NAME = "DailyPlaylistSyncWorker"

        fun scheduleDailySync(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val syncRequest = PeriodicWorkRequestBuilder<PlaylistSyncWorker>(24, TimeUnit.HOURS)
                .setConstraints(constraints)
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                syncRequest
            )
        }
    }
}
