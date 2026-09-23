package com.example.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.RemoteViews
import com.example.MainActivity
import com.example.R

/**
 * Widget que muestra las canciones de la primera lista de reproducción
 * (o la última seleccionada) y permite reproducirlas directamente.
 */
class PlaylistWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        for (appWidgetId in appWidgetIds) {
            updateWidget(context, appWidgetManager, appWidgetId)
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        when (intent.action) {
            ACTION_REFRESH -> {
                val manager = AppWidgetManager.getInstance(context)
                val ids = manager.getAppWidgetIds(ComponentName(context, PlaylistWidgetProvider::class.java))
                for (id in ids) {
                    updateWidget(context, manager, id)
                }
            }
            PlaylistWidgetService.ACTION_PLAY_ALL -> {
                PlaylistWidgetPlayback.playAll(context)
            }
            PlaylistWidgetService.ACTION_PLAY_SONG -> {
                val songId = intent.getStringExtra(EXTRA_SONG_ID)
                if (songId != null) {
                    PlaylistWidgetPlayback.playSong(context, songId)
                }
            }
        }
    }

    companion object {
        const val ACTION_REFRESH = "com.example.widget.action.REFRESH_PLAYLIST_WIDGET"
        const val EXTRA_WIDGET_ID = "extra_widget_id"
        const val EXTRA_SONG_ID = "extra_song_id"

        fun refreshAll(context: Context) {
            try {
                val manager = AppWidgetManager.getInstance(context) ?: return
                val ids = manager.getAppWidgetIds(ComponentName(context, PlaylistWidgetProvider::class.java))
                for (id in ids) {
                    updateWidget(context, manager, id)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        private fun updateWidget(context: Context, manager: AppWidgetManager, appWidgetId: Int) {
            val views = RemoteViews(context.packageName, R.layout.widget_playlist)

            // El adapter lee la playlist real desde Room (con el widgetId en el URI).
            val adapterIntent = Intent(context, PlaylistWidgetService::class.java).apply {
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
                data = Uri.parse(toUri(Intent.URI_INTENT_SCHEME))
            }
            views.setRemoteAdapter(R.id.playlist_widget_list, adapterIntent)
            views.setEmptyView(R.id.playlist_widget_list, R.id.playlist_widget_subtitle)

            // Encabezado: abrir la app
            val openAppIntent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val openPendingIntent = PendingIntent.getActivity(
                context, 20, openAppIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.playlist_widget_root, openPendingIntent)

            // Botón reproducir todo
            val playAllIntent = Intent(context, PlaylistWidgetProvider::class.java).apply {
                action = PlaylistWidgetService.ACTION_PLAY_ALL
                putExtra(EXTRA_WIDGET_ID, appWidgetId)
            }
            val playAllPendingIntent = PendingIntent.getBroadcast(
                context, 21, playAllIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.playlist_widget_btn_play_all, playAllPendingIntent)

            // Plantilla de click para cada canción de la lista
            val songClickIntent = Intent(context, PlaylistWidgetProvider::class.java).apply {
                action = PlaylistWidgetService.ACTION_PLAY_SONG
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
            }
            val songClickPendingIntent = PendingIntent.getBroadcast(
                context, 22, songClickIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setPendingIntentTemplate(R.id.playlist_widget_list, songClickPendingIntent)

            try {
                manager.updateAppWidget(appWidgetId, views)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}
