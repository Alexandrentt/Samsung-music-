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
import android.widget.RemoteViews
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
                .data(url)
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

        val prevIntent = Intent(this, MusicPlaybackService::class.java).apply { action = ACTION_PREV }
        val prevPendingIntent = PendingIntent.getService(
            this, 1, prevIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val toggleIntent = Intent(this, MusicPlaybackService::class.java).apply { action = ACTION_TOGGLE }
        val togglePendingIntent = PendingIntent.getService(
            this, 2, toggleIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val nextIntent = Intent(this, MusicPlaybackService::class.java).apply { action = ACTION_NEXT }
        val nextPendingIntent = PendingIntent.getService(
            this, 3, nextIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = Intent(this, MusicPlaybackService::class.java).apply { action = ACTION_STOP }
        val stopPendingIntent = PendingIntent.getService(
            this, 4, stopIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val shuffleIntent = Intent(this, MusicPlaybackService::class.java).apply { action = ACTION_SHUFFLE }
        val shufflePendingIntent = PendingIntent.getService(
            this, 5, shuffleIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val favIntent = Intent(this, MusicPlaybackService::class.java).apply { action = ACTION_FAVORITE }
        val favPendingIntent = PendingIntent.getService(
            this, 6, favIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val playPauseIconRes = if (isPlaying) R.drawable.ic_notif_pause_dark else R.drawable.ic_notif_play_dark
        val favIconRes = if (isFavorite) R.drawable.ic_notif_thumb_up_filled else R.drawable.ic_notif_thumb_up

        val maxProgress = 1000
        val currentProgress = if (durationMs > 0) {
            ((positionMs.toFloat() / durationMs.toFloat()) * maxProgress).toInt().coerceIn(0, maxProgress)
        } else {
            0
        }

        // Expanded RemoteViews matching the user screenshot exactly
        val remoteViewsExpanded = RemoteViews(packageName, R.layout.notification_media_player_expanded).apply {
            setTextViewText(R.id.notif_song_title, title)
            setTextViewText(R.id.notif_song_artist, artist)
            setImageViewResource(R.id.notif_btn_play_pause, playPauseIconRes)
            setImageViewResource(R.id.notif_btn_favorite, favIconRes)
            setProgressBar(R.id.notif_progress_bar, maxProgress, currentProgress, false)

            if (artBitmap != null) {
                setImageViewBitmap(R.id.notif_bg_art, artBitmap)
            } else {
                setImageViewResource(R.id.notif_bg_art, R.drawable.bg_notif_gradient)
            }

            setOnClickPendingIntent(R.id.notif_btn_play_pause, togglePendingIntent)
            setOnClickPendingIntent(R.id.notif_btn_prev, prevPendingIntent)
            setOnClickPendingIntent(R.id.notif_btn_next, nextPendingIntent)
            setOnClickPendingIntent(R.id.notif_btn_close, stopPendingIntent)
            setOnClickPendingIntent(R.id.notif_btn_shuffle, shufflePendingIntent)
            setOnClickPendingIntent(R.id.notif_btn_favorite, favPendingIntent)
            setOnClickPendingIntent(R.id.notif_root, contentPendingIntent)
        }

        // Collapsed RemoteViews
        val remoteViewsCollapsed = RemoteViews(packageName, R.layout.notification_media_player).apply {
            setTextViewText(R.id.notif_song_title, title)
            setTextViewText(R.id.notif_song_artist, artist)
            setImageViewResource(R.id.notif_btn_play_pause, playPauseIconRes)
            setProgressBar(R.id.notif_progress_bar, maxProgress, currentProgress, false)

            if (artBitmap != null) {
                setImageViewBitmap(R.id.notif_bg_art, artBitmap)
            } else {
                setImageViewResource(R.id.notif_bg_art, R.drawable.bg_notif_gradient)
            }

            setOnClickPendingIntent(R.id.notif_btn_play_pause, togglePendingIntent)
            setOnClickPendingIntent(R.id.notif_btn_prev, prevPendingIntent)
            setOnClickPendingIntent(R.id.notif_btn_next, nextPendingIntent)
            setOnClickPendingIntent(R.id.notif_btn_close, stopPendingIntent)
            setOnClickPendingIntent(R.id.notif_collapsed_root, contentPendingIntent)
        }

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(artist)
            .setContentIntent(contentPendingIntent)
            .setCustomContentView(remoteViewsCollapsed)
            .setCustomBigContentView(remoteViewsExpanded)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(isPlaying)
            .setSilent(true)

        val notification = builder.build()
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
            // Respaldo inmediato sin RemoteViews si el sistema rechaza la vista personalizada
            try {
                val fallbackBuilder = NotificationCompat.Builder(this, CHANNEL_ID)
                    .setSmallIcon(R.drawable.ic_launcher_foreground)
                    .setContentTitle(title)
                    .setContentText(artist)
                    .setContentIntent(contentPendingIntent)
                    .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                    .setOngoing(isPlaying)
                    .setSilent(true)

                val fallbackNotification = fallbackBuilder.build()
                if (!isForegroundActive) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        startForeground(
                            NOTIFICATION_ID,
                            fallbackNotification,
                            android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
                        )
                    } else {
                        startForeground(NOTIFICATION_ID, fallbackNotification)
                    }
                    isForegroundActive = true
                } else {
                    notificationManager?.notify(NOTIFICATION_ID, fallbackNotification)
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
