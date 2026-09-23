package com.example.widget

import android.content.Context
import com.example.data.AppDatabase
import com.example.player.AudioPlayerManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking

/**
 * Acciones de reproducción desde el widget de lista de reproducción.
 * El widget no tiene acceso al ViewModel, así que consulta Room directamente.
 */
object PlaylistWidgetPlayback {

    fun playAll(context: Context) {
        runBlocking(Dispatchers.IO) {
            try {
                val db = AppDatabase.getDatabase(context)
                val playlists = db.playlistDao().getAllPlaylistsWithSongsSnapshot()
                val target = playlists.firstOrNull { it.songs.isNotEmpty() } ?: return@runBlocking
                val songs = target.songs
                AudioPlayerManager.getInstance()?.startShuffled(songs)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun playSong(context: Context, songId: String) {
        runBlocking(Dispatchers.IO) {
            try {
                val db = AppDatabase.getDatabase(context)
                val playlists = db.playlistDao().getAllPlaylistsWithSongsSnapshot()
                val target = playlists.firstOrNull { p -> p.songs.any { it.id == songId } }
                    ?: return@runBlocking
                val queue = target.songs
                val song = queue.firstOrNull { it.id == songId } ?: return@runBlocking
                AudioPlayerManager.getInstance()?.playSong(song, queue)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}
