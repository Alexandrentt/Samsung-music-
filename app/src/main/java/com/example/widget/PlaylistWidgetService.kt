package com.example.widget

import android.content.Context
import android.content.Intent
import android.os.Binder
import android.widget.RemoteViews
import android.widget.RemoteViewsService
import com.example.R
import com.example.data.AppDatabase
import com.example.data.Song

/**
 * RemoteViewsService que alimenta la lista de canciones del widget,
 * leyendo la playlist directamente desde Room.
 */
class PlaylistWidgetService : RemoteViewsService() {

    companion object {
        const val ACTION_PLAY_ALL = "com.example.widget.action.PLAY_ALL"
        const val ACTION_PLAY_SONG = "com.example.widget.action.PLAY_SONG"
    }

    override fun onGetViewFactory(intent: Intent): RemoteViewsFactory {
        val widgetId = intent.getIntExtra(
            AppWidgetManager_EXTRA_APPWIDGET_ID,
            android.appwidget.AppWidgetManager.INVALID_APPWIDGET_ID
        )
        return PlaylistWidgetFactory(applicationContext, widgetId)
    }

    class PlaylistWidgetFactory(
        private val context: Context,
        private val widgetId: Int
    ) : RemoteViewsService.RemoteViewsFactory {

        private val db by lazy { AppDatabase.getDatabase(context) }
        private var songs: List<Song> = emptyList()
        private var playlistName: String = ""

        override fun onCreate() {}

        override fun onDestroy() {}

        override fun onDataSetChanged() {
            // Permitir consultas de Room en el hilo del widget (permiso temporal).
            val identityToken = Binder.clearCallingIdentity()
            try {
                val playlists = db.playlistDao().getAllPlaylistsWithSongsSnapshot()
                val target = playlists.firstOrNull { it.songs.isNotEmpty() }
                if (target != null) {
                    playlistName = target.playlist.name
                    songs = target.songs.take(30)
                } else {
                    playlistName = ""
                    songs = emptyList()
                }
            } catch (e: Exception) {
                e.printStackTrace()
                songs = emptyList()
            } finally {
                Binder.restoreCallingIdentity(identityToken)
            }
        }

        override fun getCount(): Int = songs.size

        override fun getViewAt(position: Int): RemoteViews {
            val views = RemoteViews(context.packageName, R.layout.widget_playlist_item)
            val song = songs.getOrNull(position)
            if (song != null) {
                views.setTextViewText(R.id.playlist_widget_item_title, song.title)
                views.setTextViewText(R.id.playlist_widget_item_artist, song.artist)

                // Click en la fila: reproducir esta canción (con toda la playlist como cola)
                val fillInIntent = Intent().apply {
                    putExtra(PlaylistWidgetProvider.EXTRA_SONG_ID, song.id)
                }
                views.setOnClickFillInIntent(R.id.playlist_widget_item_root, fillInIntent)
            }
            return views
        }

        override fun getLoadingView(): RemoteViews? = null

        override fun getViewTypeCount(): Int = 1

        override fun getItemId(position: Int): Long = position.toLong()

        override fun hasStableIds(): Boolean = false

        companion object {
            const val AppWidgetManager_EXTRA_APPWIDGET_ID = "appWidgetId"
        }
    }
}
