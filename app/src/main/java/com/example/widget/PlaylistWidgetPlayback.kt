package com.example.widget

import android.content.Context
import android.os.Handler
import android.os.Looper
import com.example.data.AppDatabase
import com.example.player.AudioPlayerManager
import kotlinx.coroutines.Dispatchers

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Acciones de reproducción desde el widget de lista de reproducción.
 * El widget no tiene acceso al ViewModel, así que consulta Room directamente.
 *
 * IMPORTANTE: la consulta corre en IO y la llamada a AudioPlayerManager SIEMPRE
 * en el hilo principal (los StateFlow y MediaPlayer no son thread-safe y un
 * startPlayback fuera del main thread provoca cierres aleatorios de la app).
 */
object PlaylistWidgetPlayback {

    private val mainHandler = Handler(Looper.getMainLooper())
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun playAll(context: Context) {
        val appContext = context.applicationContext
        scope.launch {
            try {
                val db = AppDatabase.getDatabase(appContext)
                val playlists = db.playlistDao().getAllPlaylistsWithSongsSnapshot()
                val songs = playlists.firstOrNull { it.songs.isNotEmpty() }?.songs ?: return@launch
                mainHandler.post {
                    try {
                        AudioPlayerManager.getInstance()?.startShuffled(songs)
                    } catch (e: Exception) {
                        android.util.Log.e("PlaylistWidgetPlayback", "playAll falló", e)
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun playSong(context: Context, songId: String) {
        val appContext = context.applicationContext
        scope.launch {
            try {
                val db = AppDatabase.getDatabase(appContext)
                val playlists = db.playlistDao().getAllPlaylistsWithSongsSnapshot()
                val queue = playlists.firstOrNull { p -> p.songs.any { it.id == songId } }?.songs
                    ?: return@launch
                val song = queue.firstOrNull { it.id == songId } ?: return@launch
                mainHandler.post {
                    try {
                        AudioPlayerManager.getInstance()?.playSong(song, queue)
                    } catch (e: Exception) {
                        android.util.Log.e("PlaylistWidgetPlayback", "playSong falló", e)
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}
