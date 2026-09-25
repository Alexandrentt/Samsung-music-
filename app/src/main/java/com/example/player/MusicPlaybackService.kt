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
    private lateinit var notificationManager: NotificationManager

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        createNotificationChannel()
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
        isForegroundActive = false
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // El contrato de startForegroundService exige llamar a startForeground()
        // SIEMPRE y de inmediato; de lo contrario Android mata el proceso
        // (crash "Context.startForegroundService() did not then call
        // Service.startForeground()" — causa típica de que la app se cierre
        // sola en cuanto reproduce). Renderizamos la notificación básica aquí y
        // el contenido completo va dentro de try/catch para que cualquier
        // error de RemoteViews no tire la app entera.
        try {
            renderNotification(
                title = "Música",
                artist = "Reproduciendo…",
                isPlaying = false,
                artBitmap = cachedCoverBitmap,
                isShuffle = false,
                isFavorite = false,
                positionMs = 0L,
                durationMs = 0L
            )
        } catch (e: Exception) {
            android.util.Log.e("MusicPlaybackService", "No se pudo renderizar la notificación base", e)
            try {
                startForeground(NOTIFICATION_ID, basicNotification("Música"))
            } catch (e2: Exception) {
                android.util.Log.e("MusicPlaybackService", "No se pudo iniciar el foreground", e2)
                stopSelf()
                return START_NOT_STICKY
            }
        }

        if (intent == null) return START_STICKY

        try {
            when (intent.action) {
                ACTION_START -> {
                    val title = intent.getStringExtra(EXTRA_TITLE) ?: "Música"
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
                    val title = intent.getStringExtra(EXTRA_TITLE) ?: "Música"
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
                    MusicAppWidgetProvider.updateAllWidgets(this, "Música", "En pausa", false)
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
        } catch (e: Exception) {
            // Ninguna acción del servicio debe poder tumbar el proceso.
            android.util.Log.e("MusicPlaybackService", "Error procesando acción ${intent.action}", e)
        }

        return START_STICKY
    }

    /** Notificación mínima de respaldo sin RemoteViews personalizadas. */
    private fun basicNotification(title: String): Notification {
        val openAppIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val contentPendingIntent = PendingIntent.getActivity(
            this, 0, openAppIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText("Reproduciendo…")
            .setContentIntent(contentPendingIntent)
            .setSilent(true)
            .setOngoing(true)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Reproducción de Música",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Miniplayer de reproducción multimedia"
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
        try {
            renderNotification(title, artist, isPlaying, cachedCoverBitmap, isShuffle, isFavorite, positionMs, durationMs)
        } catch (e: Exception) {
            // RemoteViews puede fallar en algunas capas (p. ej. One UI);
            // usamos la notificación básica para no crashear el proceso.
            android.util.Log.e("MusicPlaybackService", "RemoteViews falló, usando notificación básica", e)
            try {
                startForeground(NOTIFICATION_ID, basicNotification(title))
            } catch (e2: Exception) {
                android.util.Log.e("MusicPlaybackService", "Foreground básico también falló", e2)
            }
        }

        // If artUrl changed, fetch fresh bitmap in background
        if (!artUrl.isNullOrBlank() && artUrl != lastLoadedArtUrl) {
            lastLoadedArtUrl = artUrl
            serviceScope.launch {
                val bitmap = loadCoverBitmap(artUrl)
                if (bitmap != null) {
                    cachedCoverBitmap = bitmap
                    try {
                        renderNotification(title, artist, isPlaying, bitmap, isShuffle, isFavorite, positionMs, durationMs)
                    } catch (e: Exception) {
                        android.util.Log.e("MusicPlaybackService", "Render con portada falló", e)
                    }
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
                .size(600, 360)
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

        // RemoteViews construidos con red de seguridad: si el layout trae una
        // clase no permitida por RemoteViews (p. ej. <View> en Android 14),
        // safeRemoteViews devuelve null y se publica la notificación básica
        // en vez de una vista que el sistema rechaza matando el proceso.
        val remoteViewsExpanded = safeRemoteViews(R.layout.notification_media_player_expanded) {
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

        // Collapsed RemoteViews (mismas reglas de seguridad)
        val remoteViewsCollapsed = safeRemoteViews(R.layout.notification_media_player) {
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

        var builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(artist)
            .setContentIntent(contentPendingIntent)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(isPlaying)
            .setSilent(true)
        if (remoteViewsExpanded != null && remoteViewsCollapsed != null) {
            builder = builder
                .setCustomBigContentView(remoteViewsExpanded)
                .setCustomContentView(remoteViewsCollapsed)
        }

        // notify() en vez de startForeground() para las actualizaciones de
        // progreso posteriores: reconstruir el foreground cada 2 s (como hacía
        // el ticker) dispara oportunidades de crash innecesarias. startForeground
        // ya se garantizó al entrar en onStartCommand.
        val notification = builder.build()
        if (isForegroundActive) {
            notificationManager.notify(NOTIFICATION_ID, notification)
        } else {
            isForegroundActive = true
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    /**
     * Red de seguridad contra BadForegroundServiceNotificationException.
     *
     * RemoteViews permite solo un subconjunto de vistas; en Android 14 un
     * layout con una clase fuera de esa lista (p. ej. <View>) inflaba bien
     * en el build pero el sistema LANZA al validar la notificación y mata el
     * proceso (RemoteServiceException que ningún try/catch del servicio ve).
     *
     * Aquí inflamos el layout con las MISMAS reglas (RemoteViews.apply)
     * antes de publicarlo: si falla, devolvemos null y la notificación sale
     * básica — el usuario sigue reproduciendo música sin crash.
     */
    private fun safeRemoteViews(layoutRes: Int, configure: RemoteViews.() -> Unit): RemoteViews? {
        return try {
            val rv = RemoteViews(packageName, layoutRes)
            rv.configure()
            rv.apply(applicationContext, null) // validación de inflación local
            rv
        } catch (e: Exception) {
            android.util.Log.e("MusicPlaybackService", "RemoteViews inválido (layout=$layoutRes), usando básica", e)
            null
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
