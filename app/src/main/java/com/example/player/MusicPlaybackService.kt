package com.example.player

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import coil.Coil
import coil.request.ImageRequest
import com.example.MainActivity
import com.example.R
import com.example.widget.MusicAppWidgetProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.io.File
import kotlinx.coroutines.withContext

class MusicPlaybackService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var lastLoadedArtUrl: String? = null
    private var cachedCoverBitmap: Bitmap? = null
    private var isForegroundActive = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent == null) return START_STICKY

        when (intent.action) {
            ACTION_START -> {
                val title = intent.getStringExtra(EXTRA_TITLE) ?: "Samsung Music"
                val artist = intent.getStringExtra(EXTRA_ARTIST) ?: "Reproduciendo"
                val isPlaying = intent.getBooleanExtra(EXTRA_IS_PLAYING, true)
                val artUrl = intent.getStringExtra(EXTRA_ART_URL)
                val isShuffle = intent.getBooleanExtra(EXTRA_IS_SHUFFLE, false)
                val isFavorite = intent.getBooleanExtra(EXTRA_IS_FAVORITE, false)
                val positionMs = intent.getLongExtra(EXTRA_POSITION_MS, 0L)
                val durationMs = intent.getLongExtra(EXTRA_DURATION_MS, 0L)

                updateNotificationWithArtwork(
                    title = title,
                    artist = artist,
                    isPlaying = isPlaying,
                    artUrl = artUrl,
                    isShuffle = isShuffle,
                    isFavorite = isFavorite,
                    positionMs = positionMs,
                    durationMs = durationMs
                )
                MusicAppWidgetProvider.updateAllWidgets(this, title, artist, isPlaying)
            }
            ACTION_PAUSE -> {
                AudioPlayerManager.getInstance()?.pause()
                val title = intent.getStringExtra(EXTRA_TITLE) ?: "Samsung Music"
                val artist = intent.getStringExtra(EXTRA_ARTIST) ?: "En pausa"
                updateNotificationWithArtwork(
                    title = title,
                    artist = artist,
                    isPlaying = false,
                    artUrl = null,
                    isShuffle = false,
                    isFavorite = false,
                    positionMs = 0L,
                    durationMs = 0L
                )
                MusicAppWidgetProvider.updateAllWidgets(this, title, artist, false)
            }
            ACTION_STOP -> {
                AudioPlayerManager.getInstance()?.pause()
                MusicAppWidgetProvider.updateAllWidgets(this, "Samsung Music", "En pausa", false)
                isForegroundActive = false
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
            ACTION_TOGGLE -> {
                AudioPlayerManager.getInstance()?.togglePlayPause()
            }
            ACTION_PREV -> {
                AudioPlayerManager.getInstance()?.skipToPrevious()
            }
            ACTION_NEXT -> {
                AudioPlayerManager.getInstance()?.skipToNext()
            }
            ACTION_SHUFFLE -> {
                AudioPlayerManager.getInstance()?.toggleShuffle()
            }
            ACTION_FAVORITE -> {
                AudioPlayerManager.getInstance()?.toggleFavoriteCurrentSong()
            }
        }

        return START_STICKY
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Reproducción de Música Samsung",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Miniplayer de reproducción multimedia Samsung One UI"
                setShowBadge(false)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    private fun updateNotificationWithArtwork(
        title: String,
        artist: String,
        isPlaying: Boolean,
        artUrl: String?,
        isShuffle: Boolean,
        isFavorite: Boolean,
        positionMs: Long,
        durationMs: Long
    ) {
        // Render immediate notification with current cached artwork
        renderNotification(title, artist, isPlaying, cachedCoverBitmap, isShuffle, isFavorite, positionMs, durationMs)

        // If artUrl changed, fetch fresh bitmap in background
        if (!artUrl.isNullOrBlank() && artUrl != lastLoadedArtUrl) {
            lastLoadedArtUrl = artUrl
            serviceScope.launch {
                val bitmap = loadCoverBitmap(artUrl)
                if (bitmap != null) {
                    cachedCoverBitmap = bitmap
                    renderNotification(title, artist, isPlaying, bitmap, isShuffle, isFavorite, positionMs, durationMs)
                }
            }
        }
    }

    private suspend fun loadCoverBitmap(url: String): Bitmap? = withContext(Dispatchers.IO) {
        try {
            val imageLoader = Coil.imageLoader(this@MusicPlaybackService)
            val request = ImageRequest.Builder(this@MusicPlaybackService)
                .data(if (File(url).isFile) File(url) else url)
                .allowHardware(false) // RemoteViews requires software Bitmap
                .size(240, 150) // Tamaño optimizado para evitar saturar el buffer de Binder IPC (TransactionTooLargeException)
                .build()
            val result = imageLoader.execute(request)
            (result.drawable as? BitmapDrawable)?.bitmap
        } catch (e: Exception) {
            null
        }
    }

    private fun renderNotification(
        title: String,
        artist: String,
        isPlaying: Boolean,
        artBitmap: Bitmap?,
        isShuffle: Boolean,
        isFavorite: Boolean,
        positionMs: Long,
        durationMs: Long
    ) {
        val openAppIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val contentPendingIntent = PendingIntent.getActivity(
            this, 0, openAppIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        fun servicePendingIntent(requestCode: Int, action: String): PendingIntent {
            val intent = Intent(this, MusicPlaybackService::class.java).apply { this.action = action }
            return PendingIntent.getService(
                this, requestCode, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }

        val previous = servicePendingIntent(1, ACTION_PREV)
        val toggle = servicePendingIntent(2, ACTION_TOGGLE)
        val next = servicePendingIntent(3, ACTION_NEXT)
        val shuffle = servicePendingIntent(5, ACTION_SHUFFLE)
        val favorite = servicePendingIntent(6, ACTION_FAVORITE)
        val playPauseIcon = if (isPlaying) R.drawable.ic_notif_pause_dark else R.drawable.ic_notif_play_dark
        val favoriteIcon = if (isFavorite) R.drawable.ic_notif_thumb_up_filled else R.drawable.ic_notif_thumb_up
        val maxProgress = 1000
        val currentProgress = if (durationMs > 0L) {
            ((positionMs.toFloat() / durationMs.toFloat()) * maxProgress).toInt().coerceIn(0, maxProgress)
        } else 0

        // Android dibuja la notificación multimedia. Las RemoteViews personalizadas
        // suelen recortarse o recolocarse en Android 12+ y en capas de fabricantes.
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(artist)
            .setContentIntent(contentPendingIntent)
            .setLargeIcon(artBitmap)
            .setCategory(NotificationCompat.CATEGORY_TRANSPORT)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(isPlaying)
            .setSilent(true)
            .setProgress(maxProgress, currentProgress, false)
            .addAction(R.drawable.ic_notif_prev, "Anterior", previous)
            .addAction(playPauseIcon, if (isPlaying) "Pausar" else "Reproducir", toggle)
            .addAction(R.drawable.ic_notif_next, "Siguiente", next)
            .addAction(R.drawable.ic_notif_shuffle, if (isShuffle) "Aleatorio activado" else "Aleatorio", shuffle)
            .addAction(favoriteIcon, if (isFavorite) "Favorito" else "Me gusta", favorite)
            .setStyle(NotificationCompat.MediaStyle().setShowActionsInCompactView(0, 1, 2))
            .build()

        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
        try {
            if (!isForegroundActive) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    startForeground(
                        NOTIFICATION_ID,
                        notification,
                        android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
                    )
                } else {
                    startForeground(NOTIFICATION_ID, notification)
                }
                isForegroundActive = true
            } else {
                notificationManager?.notify(NOTIFICATION_ID, notification)
            }
        } catch (e: Throwable) {
            e.printStackTrace()
            try {
                val fallback = NotificationCompat.Builder(this, CHANNEL_ID)
                    .setSmallIcon(R.drawable.ic_launcher_foreground)
                    .setContentTitle(title)
                    .setContentText(artist)
                    .setContentIntent(contentPendingIntent)
                    .setLargeIcon(artBitmap)
                    .setCategory(NotificationCompat.CATEGORY_TRANSPORT)
                    .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                    .setOngoing(isPlaying)
                    .setSilent(true)
                    .setStyle(NotificationCompat.MediaStyle())
                    .build()
                if (!isForegroundActive) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        startForeground(
                            NOTIFICATION_ID,
                            fallback,
                            android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
                        )
                    } else {
                        startForeground(NOTIFICATION_ID, fallback)
                    }
                    isForegroundActive = true
                } else {
                    notificationManager?.notify(NOTIFICATION_ID, fallback)
                }
            } catch (ex: Throwable) {
                ex.printStackTrace()
            }
        }
    }

    companion object {
        const val CHANNEL_ID = "samsung_music_channel"
        const val NOTIFICATION_ID = 1001

        const val ACTION_START = "com.example.action.START"
        const val ACTION_PAUSE = "com.example.action.PAUSE"
        const val ACTION_STOP = "com.example.action.STOP"
        const val ACTION_TOGGLE = "com.example.action.TOGGLE"
        const val ACTION_PREV = "com.example.action.PREV"
        const val ACTION_NEXT = "com.example.action.NEXT"
        const val ACTION_SHUFFLE = "com.example.action.SHUFFLE"
        const val ACTION_FAVORITE = "com.example.action.FAVORITE"

        const val EXTRA_TITLE = "extra_title"
        const val EXTRA_ARTIST = "extra_artist"
        const val EXTRA_IS_PLAYING = "extra_is_playing"
        const val EXTRA_ART_URL = "extra_art_url"
        const val EXTRA_IS_SHUFFLE = "extra_is_shuffle"
        const val EXTRA_IS_FAVORITE = "extra_is_favorite"
        const val EXTRA_POSITION_MS = "extra_position_ms"
        const val EXTRA_DURATION_MS = "extra_duration_ms"
    }
}
