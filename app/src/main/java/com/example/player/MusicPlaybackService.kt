package com.example.player

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import java.net.URL

class MusicPlaybackService : Service() {

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var wakeLock: PowerManager.WakeLock? = null

    companion object {
        const val CHANNEL_ID = "samsung_music_playback_channel"
        const val NOTIFICATION_ID = 1001

        const val ACTION_START = "com.example.action.START"
        const val ACTION_PLAY = "com.example.action.PLAY"
        const val ACTION_PAUSE = "com.example.action.PAUSE"
        const val ACTION_TOGGLE = "com.example.action.TOGGLE"
        const val ACTION_PREV = "com.example.action.PREV"
        const val ACTION_NEXT = "com.example.action.NEXT"
        const val ACTION_STOP = "com.example.action.STOP"

        const val EXTRA_TITLE = "extra_title"
        const val EXTRA_ARTIST = "extra_artist"
        const val EXTRA_IS_PLAYING = "extra_is_playing"
        const val EXTRA_ART_URL = "extra_art_url"

        var isRunning = false
            private set

        fun startService(context: Context, title: String, artist: String, isPlaying: Boolean, artUrl: String?) {
            val intent = Intent(context, MusicPlaybackService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_TITLE, title)
                putExtra(EXTRA_ARTIST, artist)
                putExtra(EXTRA_IS_PLAYING, isPlaying)
                putExtra(EXTRA_ART_URL, artUrl)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stopService(context: Context) {
            val intent = Intent(context, MusicPlaybackService::class.java).apply {
                action = ACTION_STOP
            }
            context.stopService(intent)
        }
    }

    override fun onCreate() {
        super.onCreate()
        isRunning = true
        createNotificationChannel()

        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "SamsungMusic:WakeLock").apply {
            setReferenceCounted(false)
            acquire(60 * 60 * 1000L) // 1 hour safety timeout
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action ?: return START_STICKY

        when (action) {
            ACTION_START -> {
                val title = intent.getStringExtra(EXTRA_TITLE) ?: "Samsung Music"
                val artist = intent.getStringExtra(EXTRA_ARTIST) ?: "Reproduciendo en segundo plano"
                val isPlaying = intent.getBooleanExtra(EXTRA_IS_PLAYING, true)
                val artUrl = intent.getStringExtra(EXTRA_ART_URL)
                updateNotification(title, artist, isPlaying, artUrl)
            }
            ACTION_TOGGLE, ACTION_PLAY, ACTION_PAUSE -> {
                AudioPlayerManager.instance?.togglePlayPause()
            }
            ACTION_PREV -> {
                AudioPlayerManager.instance?.skipToPrevious()
            }
            ACTION_NEXT -> {
                AudioPlayerManager.instance?.skipToNext()
            }
            ACTION_STOP -> {
                AudioPlayerManager.instance?.pause()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
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
                description = "Controles de reproducción multimedia en segundo plano"
                setShowBadge(false)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun updateNotification(
        title: String,
        artist: String,
        isPlaying: Boolean,
        artUrl: String?
    ) {
        val openAppIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val contentPendingIntent = PendingIntent.getActivity(
            this,
            0,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Actions
        val prevPendingIntent = PendingIntent.getService(
            this,
            1,
            Intent(this, MusicPlaybackService::class.java).apply { action = ACTION_PREV },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val togglePendingIntent = PendingIntent.getService(
            this,
            2,
            Intent(this, MusicPlaybackService::class.java).apply { action = ACTION_TOGGLE },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val nextPendingIntent = PendingIntent.getService(
            this,
            3,
            Intent(this, MusicPlaybackService::class.java).apply { action = ACTION_NEXT },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopPendingIntent = PendingIntent.getService(
            this,
            4,
            Intent(this, MusicPlaybackService::class.java).apply { action = ACTION_STOP },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(artist)
            .setSubText("Samsung Music")
            .setContentIntent(contentPendingIntent)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(isPlaying)
            .addAction(android.R.drawable.ic_media_previous, "Anterior", prevPendingIntent)
            .addAction(
                if (isPlaying) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play,
                if (isPlaying) "Pausar" else "Reproducir",
                togglePendingIntent
            )
            .addAction(android.R.drawable.ic_media_next, "Siguiente", nextPendingIntent)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Detener", stopPendingIntent)
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .setBigContentTitle(title)
                    .bigText("$artist\nReproducción en segundo plano activa")
            )

        val notification = builder.build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    override fun onDestroy() {
        isRunning = false
        wakeLock?.let {
            if (it.isHeld) it.release()
        }
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
