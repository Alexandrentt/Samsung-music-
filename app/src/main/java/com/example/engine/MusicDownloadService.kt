package com.example.engine

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.data.AppDatabase
import com.example.data.MusicRepository
import com.example.data.Song
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import java.io.File

/**
 * Servicio en primer plano (Foreground Service) que asegura que las descargas continúen
 * sin interrupción en segundo plano, aun si el usuario sale de la aplicación o se apaga
 * la pantalla del teléfono (mediante WakeLock dedicado y Foreground Service tipo dataSync).
 *
 * Muestra el progreso de la descarga en tiempo real en la barra de notificaciones de Android.
 */
class MusicDownloadService : Service() {

    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(Dispatchers.IO + serviceJob)

    private var wakeLock: PowerManager.WakeLock? = null
    private lateinit var notificationManager: NotificationManager

    private var lastNotificationTime = 0L

    override fun onCreate() {
        super.onCreate()
        notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        createNotificationChannel()

        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "SamsungMusic:DownloadWakeLock"
        ).apply {
            setReferenceCounted(false)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        val url = intent?.getStringExtra(EXTRA_URL)
        val autoEnrich = intent?.getBooleanExtra(EXTRA_AUTO_ENRICH, false) ?: false

        if (action == ACTION_CANCEL) {
            stopSelf()
            return START_NOT_STICKY
        }

        if (!url.isNullOrBlank()) {
            startForegroundNotification("Iniciando descarga…", 0, "Preparando conexión")
            wakeLock?.acquire(15 * 60 * 1000L) // 15 minutos de margen de seguridad
            _isDownloading.value = true

            serviceScope.launch {
                executeDownload(url, autoEnrich)
            }
        }

        return START_NOT_STICKY
    }

    private suspend fun executeDownload(url: String, autoEnrich: Boolean) {
        val db = AppDatabase.getDatabase(applicationContext)
        val repository = MusicRepository(db.songDao(), db.downloadHistoryDao(), db.playlistDao())
        val engine = MusicaEngine(applicationContext)

        try {
            val currentPlaylists = repository.allPlaylists.firstOrNull() ?: emptyList()
            val targetPlaylistId = currentPlaylists.firstOrNull()?.id ?: repository.createPlaylist(
                name = "Mi Playlist",
                description = "Descargas de YouTube",
                coverArtUrl = null
            )

            val downloadedSongs = engine.descargarDesdeUrl(
                url = url,
                autoEnriquecer = autoEnrich,
                isAlreadyDownloaded = { videoId ->
                    val cleanId = YouTubeAudioDownloader.normalizeVideoId(videoId)
                    val songId = "yt_$cleanId"

                    // 1. Verificar en base de datos por ID o ID de YouTube
                    val existingSong = repository.getSongById(songId)
                        ?: repository.getSongById(cleanId)
                        ?: repository.getSongByYoutubeId(cleanId)

                    if (existingSong != null) {
                        // Si los metadatos venían de un catálogo externo, volver a
                        // validarlos con el título, canal y duración del video original.
                        // Una edición manual elimina releaseId y nunca se sobrescribe.
                        var songToUse = existingSong
                        if (existingSong.releaseId != null && !existingSong.titleManuallyEdited && !existingSong.artistManuallyEdited && !existingSong.albumManuallyEdited) {
                            try {
                                val refreshed = engine.revalidarMetadatos(existingSong)
                                if (refreshed != null) {
                                    repository.updateSong(refreshed)
                                    songToUse = refreshed
                                }
                            } catch (e: Exception) {
                                android.util.Log.w("MusicDownloadService", "No se pudieron revalidar metadatos", e)
                            }
                        }
                        val file = File(songToUse.filePath)
                        if (file.exists() && file.length() > 10_000L && file.extension.equals("mp3", ignoreCase = true)) {
                            if (!repository.isSongInPlaylist(targetPlaylistId, songToUse.id)) {
                                repository.addSongToPlaylist(targetPlaylistId, songToUse.id)
                            }
                            return@descargarDesdeUrl true
                        }
                    }

                    // 2. Buscar el ID completo en la carpeta pública y en las ubicaciones
                    // antiguas. No comparar solo seis caracteres: puede enlazar otra canción.
                    val publicDir = File(
                        android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_MUSIC),
                        "SamsungMusic"
                    )
                    val candidateDirs = listOfNotNull(
                        publicDir,
                        engine.musicFolder,
                        getExternalFilesDir(android.os.Environment.DIRECTORY_MUSIC),
                        getExternalFilesDir("Music"),
                        File(filesDir, "Music")
                    ).distinctBy { it.absolutePath }
                    val diskFile = candidateDirs.asSequence()
                        .filter { it.isDirectory }
                        .flatMap { it.listFiles()?.asSequence() ?: emptySequence() }
                        .firstOrNull { f ->
                            f.isFile && f.length() > 10_000L &&
                                f.extension.lowercase() == "mp3" &&
                                (
                                    f.nameWithoutExtension.contains(cleanId, ignoreCase = true) ||
                                    MusicaEngine.parseMediaFileName(f.nameWithoutExtension).first == cleanId
                                )
                        }

                    if (diskFile != null) {
                        val songToSave = existingSong?.copy(filePath = diskFile.absolutePath, fileSizeBytes = diskFile.length(), isDownloaded = true)
                            ?: Song(
                                id = songId,
                                title = diskFile.nameWithoutExtension.substringBeforeLast('_').replace('_', ' ').trim().ifBlank { "Canción" },
                                artist = "Artista desconocido",
                                album = "Descargas",
                                filePath = diskFile.absolutePath,
                                fileSizeBytes = diskFile.length(),
                                isDownloaded = true,
                                youtubeVideoId = cleanId
                            )
                        val previous = repository.getSongById(songToSave.id)
                            ?: repository.getSongByYoutubeId(cleanId)
                        val safeToSave = if (previous == null) songToSave else songToSave.copy(
                            title = if (previous.titleManuallyEdited) previous.title else songToSave.title,
                            artist = if (previous.artistManuallyEdited) previous.artist else songToSave.artist,
                            album = if (previous.albumManuallyEdited) previous.album else songToSave.album,
                            titleManuallyEdited = previous.titleManuallyEdited,
                            artistManuallyEdited = previous.artistManuallyEdited,
                            albumManuallyEdited = previous.albumManuallyEdited,
                            isFavorite = previous.isFavorite,
                            playCount = previous.playCount,
                            dateAdded = previous.dateAdded
                        )
                        repository.insertSong(safeToSave)
                        if (!repository.isSongInPlaylist(targetPlaylistId, safeToSave.id)) {
                            repository.addSongToPlaylist(targetPlaylistId, safeToSave.id)
                        }
                        return@descargarDesdeUrl true
                    }

                    false
                },
                onSongSaved = { song ->
                    val previous = repository.getSongById(song.id)
                        ?: song.youtubeVideoId?.let { repository.getSongByYoutubeId(it) }
                    val safeToSave = if (previous == null) song else song.copy(
                        title = if (previous.titleManuallyEdited) previous.title else song.title,
                        artist = if (previous.artistManuallyEdited) previous.artist else song.artist,
                        album = if (previous.albumManuallyEdited) previous.album else song.album,
                        titleManuallyEdited = previous.titleManuallyEdited,
                        artistManuallyEdited = previous.artistManuallyEdited,
                        albumManuallyEdited = previous.albumManuallyEdited,
                        isFavorite = previous.isFavorite,
                        playCount = previous.playCount,
                        dateAdded = previous.dateAdded
                    )
                    repository.insertSong(safeToSave)
                    repository.recordDownload(safeToSave.youtubeVideoId ?: safeToSave.id, safeToSave.title, safeToSave.artist, safeToSave.filePath)
                    repository.addSongToPlaylist(targetPlaylistId, safeToSave.id)
                },
                onProgress = { progress ->
                    _downloadProgress.value = progress
                    val now = System.currentTimeMillis()
                    // Actualiza la notificación con throttle para evitar saturar el sistema
                    if (now - lastNotificationTime > 400 || progress.percent >= 0.99f) {
                        lastNotificationTime = now
                        val percentInt = (progress.percent * 100).toInt().coerceIn(0, 100)
                        val text = if (progress.bytesTotal > 0) {
                            val curMb = progress.bytesDownloaded / (1024f * 1024f)
                            val totMb = progress.bytesTotal / (1024f * 1024f)
                            "$percentInt% • %.1f / %.1f MB".format(curMb, totMb)
                        } else {
                            "$percentInt% • ${progress.step}"
                        }
                        updateProgressNotification(
                            title = progress.currentSongTitle.ifBlank { "Descargando audio…" },
                            percent = percentInt,
                            content = text
                        )
                    }
                }
            )

            val count = downloadedSongs.size
            val finishedText = if (count == 1) {
                "\"${downloadedSongs.first().title}\" guardada en tu música"
            } else {
                "$count canciones guardadas en tu música"
            }

            showCompleteNotification(
                title = "Descarga completada",
                content = finishedText
            )
        } catch (e: Exception) {
            e.printStackTrace()
            _downloadProgress.value = DownloadProgress(
                step = "Error en la descarga",
                percent = 1.0f,
                isFinished = true,
                error = e.localizedMessage
            )
            showCompleteNotification(
                title = "Error en la descarga",
                content = e.localizedMessage ?: "No se pudo completar la descarga"
            )
        } finally {
            _isDownloading.value = false
            wakeLock?.let {
                if (it.isHeld) it.release()
            }
            stopForeground(STOP_FOREGROUND_DETACH)
            stopSelf()
        }
    }

    private fun startForegroundNotification(title: String, percent: Int, content: String) {
        val notification = buildProgressNotification(title, percent, content)
        startForeground(NOTIFICATION_ID, notification)
    }

    private fun updateProgressNotification(title: String, percent: Int, content: String) {
        val notification = buildProgressNotification(title, percent, content)
        notificationManager.notify(NOTIFICATION_ID, notification)
    }

    private fun buildProgressNotification(title: String, percent: Int, content: String) =
        NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle(title)
            .setContentText(content)
            .setProgress(100, percent, percent == 0)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(createPendingIntent())
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

    private fun showCompleteNotification(title: String, content: String) {
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setContentTitle(title)
            .setContentText(content)
            .setAutoCancel(true)
            .setContentIntent(createPendingIntent())
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()

        notificationManager.notify(NOTIFICATION_COMPLETED_ID, notification)
    }

    private fun createPendingIntent(): PendingIntent {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }
        return PendingIntent.getActivity(this, 0, intent, flags)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Descargas de música",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Progreso de descarga de canciones en segundo plano"
                setShowBadge(false)
            }
            notificationManager.createNotificationChannel(channel)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceJob.cancel()
        wakeLock?.let {
            if (it.isHeld) it.release()
        }
        _isDownloading.value = false
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val CHANNEL_ID = "samsung_music_downloads_channel"
        const val NOTIFICATION_ID = 2001
        const val NOTIFICATION_COMPLETED_ID = 2002

        const val EXTRA_URL = "extra_download_url"
        const val EXTRA_AUTO_ENRICH = "extra_auto_enrich"
        const val ACTION_CANCEL = "action_cancel_download"

        private val _downloadProgress = MutableStateFlow<DownloadProgress?>(null)
        val downloadProgress: StateFlow<DownloadProgress?> = _downloadProgress.asStateFlow()

        private val _isDownloading = MutableStateFlow(false)
        val isDownloading: StateFlow<Boolean> = _isDownloading.asStateFlow()

        fun startDownload(context: Context, url: String, autoEnrich: Boolean = false) {
            val intent = Intent(context, MusicDownloadService::class.java).apply {
                putExtra(EXTRA_URL, url)
                putExtra(EXTRA_AUTO_ENRICH, autoEnrich)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }
    }
}
