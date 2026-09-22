package com.example.ui

import android.app.Application
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.AppDatabase
import com.example.data.DownloadHistoryItem
import com.example.data.MusicRepository
import com.example.data.MusicStatistics
import com.example.data.Song
import com.example.engine.DownloadProgress
import com.example.engine.MusicaEngine
import com.example.player.AudioPlayerManager
import com.example.player.RepeatMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

enum class SamsungTab(val title: String) {
    CANCIONES("Canciones"),
    PLAYLISTS("Listas"),
    ALBUMES("Álbumes"),
    ARTISTAS("Artistas"),
    FAVORITOS("Favoritos"),
    DESCARGAS_YT("Descargas / YouTube")
}

data class AlbumItem(
    val name: String,
    val artist: String,
    val coverArtUrl: String?,
    val songCount: Int
)

data class ArtistItem(
    val name: String,
    val coverArtUrl: String?,
    val songCount: Int
)

class SamsungMusicViewModel(application: Application) : AndroidViewModel(application) {

    private val db = AppDatabase.getDatabase(application)
    private val repository = MusicRepository(db.songDao(), db.downloadHistoryDao(), db.playlistDao())
    val engine = MusicaEngine(application)
    val playerManager = AudioPlayerManager(application)

    private val _selectedTab = MutableStateFlow(SamsungTab.CANCIONES)
    val selectedTab: StateFlow<SamsungTab> = _selectedTab.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _isSearchActive = MutableStateFlow(false)
    val isSearchActive: StateFlow<Boolean> = _isSearchActive.asStateFlow()

    private val _downloadProgress = MutableStateFlow<DownloadProgress?>(null)
    val downloadProgress: StateFlow<DownloadProgress?> = _downloadProgress.asStateFlow()

    private val _isDownloading = MutableStateFlow(false)
    val isDownloading: StateFlow<Boolean> = _isDownloading.asStateFlow()

    private val _statsDialogData = MutableStateFlow<MusicStatistics?>(null)
    val statsDialogData: StateFlow<MusicStatistics?> = _statsDialogData.asStateFlow()

    private val _enrichProgress = MutableStateFlow<Pair<Int, Int>?>(null)
    val enrichProgress: StateFlow<Pair<Int, Int>?> = _enrichProgress.asStateFlow()

    private val _showNowPlayingSheet = MutableStateFlow(false)
    val showNowPlayingSheet: StateFlow<Boolean> = _showNowPlayingSheet.asStateFlow()

    private val _showSoundAliveDialog = MutableStateFlow(false)
    val showSoundAliveDialog: StateFlow<Boolean> = _showSoundAliveDialog.asStateFlow()

    private val _selectedPlaylistId = MutableStateFlow<Long?>(null)
    val selectedPlaylistId: StateFlow<Long?> = _selectedPlaylistId.asStateFlow()

    private val _songToAddToPlaylist = MutableStateFlow<Song?>(null)
    val songToAddToPlaylist: StateFlow<Song?> = _songToAddToPlaylist.asStateFlow()

    val rawSongs: StateFlow<List<Song>> = repository.allSongs.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        emptyList()
    )

    val favoriteSongs: StateFlow<List<Song>> = repository.favoriteSongs.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        emptyList()
    )

    val downloadHistory: StateFlow<List<DownloadHistoryItem>> = repository.downloadHistory.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        emptyList()
    )

    val playlistsWithSongs: StateFlow<List<com.example.data.PlaylistWithSongs>> =
        repository.allPlaylistsWithSongs.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5000),
            emptyList()
        )

    val playlists: StateFlow<List<com.example.data.Playlist>> =
        repository.allPlaylists.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5000),
            emptyList()
        )

    // Filtered songs based on search
    val filteredSongs: StateFlow<List<Song>> = combine(rawSongs, searchQuery) { songs, query ->
        if (query.isBlank()) songs
        else songs.filter {
            it.title.contains(query, ignoreCase = true) ||
                    it.artist.contains(query, ignoreCase = true) ||
                    it.album.contains(query, ignoreCase = true)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Albums grouped
    val albums: StateFlow<List<AlbumItem>> = rawSongs.combine(_searchQuery) { songs, _ ->
        songs.groupBy { it.album }.map { (albumName, albumSongs) ->
            AlbumItem(
                name = albumName,
                artist = albumSongs.firstOrNull()?.artist ?: "Varios Artistas",
                coverArtUrl = albumSongs.firstOrNull()?.coverArtUrl,
                songCount = albumSongs.size
            )
        }.sortedBy { it.name }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Artists grouped
    val artists: StateFlow<List<ArtistItem>> = rawSongs.combine(_searchQuery) { songs, _ ->
        songs.groupBy { it.artist }.map { (artistName, artistSongs) ->
            ArtistItem(
                name = artistName,
                coverArtUrl = artistSongs.firstOrNull()?.coverArtUrl,
                songCount = artistSongs.size
            )
        }.sortedBy { it.name }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        viewModelScope.launch {
            // Seed starter music if empty
            if (db.songDao().getSongCount() == 0) {
                seedInitialMusic()
            }
        }
    }

    private suspend fun seedInitialMusic() = withContext(Dispatchers.IO) {
        val despacitoLrc = """
[00:00.50] ¡Ay! Fonsi, D.Y.
[00:04.00] Oh, oh-oh
[00:08.50] Sí, sabes que ya llevo un rato mirándote
[00:13.20] Tengo que bailar contigo hoy
[00:17.80] Vi que tu mirada ya estaba llamándome
[00:22.40] Muéstrame el camino que yo voy
[00:27.10] Tú, tú eres el imán y yo soy el metal
[00:31.50] Me voy acercando y voy armando el plan
[00:36.00] Solo con pensarlo se acelera el pulso
[00:40.80] Ya, ya me está gustando más de lo normal
[00:45.30] Todos mis sentidos van pidiendo más
[00:49.80] Esto hay que tomarlo sin ningún apuro
[00:54.20] Des-pa-cito
[00:56.50] Quiero respirar tu cuello despacito
[01:00.80] Deja que te diga cosas al oído
[01:05.20] Para que te acuerdes si no estás conmigo
[01:09.60] Des-pa-cito
        """.trimIndent()

        val countingStarsLrc = """
[00:00.00] Lately, I've been, I've been losing sleep
[00:04.20] Dreaming about the things that we could be
[00:08.80] But, baby, I've been, I've been praying hard
[00:13.10] Said no more counting dollars, we'll be counting stars
[00:18.00] Yeah, we'll be counting stars
[00:23.50] I see this life, like a swinging vine
[00:26.50] Swing my heart across the line
[00:29.00] In my face is flashing signs
[00:32.00] Seek it out and ye shall find
[00:36.00] Old, but I'm not that old
[00:38.20] Young, but I'm not that bold
[00:40.80] And I don't think the world is sold
[00:43.00] On just doing what we're told
[00:46.00] I feel something so right doing the wrong thing
[00:51.50] I feel something so wrong doing the right thing
[00:56.00] I couldn't lie, couldn't lie, couldn't lie
[00:59.00] Everything that kills me makes me feel alive
[01:03.50] Lately, I've been, I've been losing sleep
        """.trimIndent()

        val horizonLrc = """
[00:01.00] Samsung Galaxy Official Theme
[00:05.50] Over the Horizon 2024
[00:12.20] ♪ Melodía orquestal y acústica ♪
[00:22.00] Experimenta un sonido envolvente
[00:32.40] Samsung SoundAlive & Dolby Atmos
[00:44.00] ♪ Crescendo instrumental y piano ♪
[01:02.00] Innovación, claridad y balance
[01:25.00] Reproducción fluida en segundo plano
[01:48.00] ♪ Acorde final relajante ♪
[02:05.00] Samsung Music One UI
        """.trimIndent()

        val initialTracks = listOf(
            Song(
                id = "s_over_the_horizon",
                title = "Over the Horizon 2024",
                artist = "Samsung Sound Lab",
                album = "Galaxy Official Sounds",
                durationMs = 215000L,
                filePath = "",
                coverArtUrl = "https://images.unsplash.com/photo-1614613535308-eb5fbd3d2c17?w=500&q=80",
                isFavorite = true,
                musicBrainzScore = 98,
                bitrate = "320 kbps",
                fileSizeBytes = 5200000L,
                lyricsLrc = horizonLrc
            ),
            Song(
                id = "s_despacito",
                title = "Despacito",
                artist = "Luis Fonsi, Daddy Yankee",
                album = "Vida",
                durationMs = 228000L,
                filePath = "",
                coverArtUrl = "https://images.unsplash.com/photo-1514525253161-7a46d19cd819?w=500&q=80",
                youtubeVideoId = "kJQP7kiw5Fk",
                youtubeChannel = "LuisFonsiVEVO",
                isFavorite = true,
                musicBrainzScore = 96,
                bitrate = "192 kbps",
                fileSizeBytes = 4300000L,
                lyricsLrc = despacitoLrc
            ),
            Song(
                id = "s_counting_stars",
                title = "Counting Stars",
                artist = "OneRepublic",
                album = "Native",
                durationMs = 257000L,
                filePath = "",
                coverArtUrl = "https://images.unsplash.com/photo-1470225620780-dba8ba36b745?w=500&q=80",
                youtubeVideoId = "hT_nvWreIhg",
                youtubeChannel = "OneRepublicVEVO",
                isFavorite = false,
                musicBrainzScore = 95,
                bitrate = "192 kbps",
                fileSizeBytes = 4900000L,
                lyricsLrc = countingStarsLrc
            ),
            Song(
                id = "s_bohemian_rhapsody",
                title = "Bohemian Rhapsody",
                artist = "Queen",
                album = "A Night at the Opera",
                durationMs = 354000L,
                filePath = "",
                coverArtUrl = "https://images.unsplash.com/photo-1465847899084-d164df4dedc6?w=500&q=80",
                youtubeVideoId = "fJ9rUzIMcZQ",
                youtubeChannel = "Queen Official",
                isFavorite = true,
                musicBrainzScore = 99,
                bitrate = "320 kbps",
                fileSizeBytes = 7200000L,
                lyricsLrc = "[00:00.00] Is this the real life? Is this just fantasy?\n[00:08.00] Caught in a landslide, no escape from reality\n[00:16.00] Open your eyes, look up to the skies and see\n[00:26.00] I'm just a poor boy, I need no sympathy\n[00:32.00] Because I'm easy come, easy go, little high, little low\n[00:41.00] Any way the wind blows doesn't really matter to me, to me"
            ),
            Song(
                id = "s_uptown_funk",
                title = "Uptown Funk",
                artist = "Mark Ronson ft. Bruno Mars",
                album = "Uptown Special",
                durationMs = 270000L,
                filePath = "",
                coverArtUrl = "https://images.unsplash.com/photo-1511671782779-c97d3d27a1d4?w=500&q=80",
                youtubeVideoId = "OPf0YbXqDm0",
                youtubeChannel = "MarkRonsonVEVO",
                isFavorite = false,
                musicBrainzScore = 94,
                bitrate = "192 kbps",
                fileSizeBytes = 5100000L,
                lyricsLrc = "[00:00.00] Doh, doh-doh-doh, doh-doh-doh, doh-doh\n[00:06.00] This hit, that ice cold, Michelle Pfeiffer, that white gold\n[00:12.00] This one for them hood girls, them good girls straight masterpieces\n[00:18.00] Stylin', wilin', livin' it up in the city\n[00:23.00] Got Chucks on with Saint Laurent, gotta kiss myself, I'm so pretty\n[00:28.00] I'm too hot (hot damn)\n[00:31.00] Called a police and a fireman\n[00:33.00] I'm too hot (hot damn)"
            )
        )
        repository.insertSongs(initialTracks)

        for (t in initialTracks) {
            if (t.youtubeVideoId != null) {
                repository.recordDownload(t.youtubeVideoId, t.title, t.youtubeChannel ?: "", t.filePath)
            }
        }

        if (repository.getPlaylistCount() == 0) {
            val p1Id = repository.createPlaylist(
                name = "Favoritos One UI",
                description = "Música seleccionada con alta fidelidad",
                coverArtUrl = "https://images.unsplash.com/photo-1514525253161-7a46d19cd819?w=500&q=80"
            )
            repository.addSongsToPlaylist(p1Id, listOf("s_over_the_horizon", "s_despacito", "s_bohemian_rhapsody"))

            val p2Id = repository.createPlaylist(
                name = "Éxitos Globales",
                description = "Lo más escuchado y descargado de YouTube",
                coverArtUrl = "https://images.unsplash.com/photo-1470225620780-dba8ba36b745?w=500&q=80"
            )
            repository.addSongsToPlaylist(p2Id, listOf("s_counting_stars", "s_uptown_funk"))
        }
    }

    fun selectTab(tab: SamsungTab) {
        _selectedTab.value = tab
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun toggleSearchActive(active: Boolean) {
        _isSearchActive.value = active
        if (!active) _searchQuery.value = ""
    }

    fun playSong(song: Song, queue: List<Song>? = null) {
        val q = queue ?: filteredSongs.value
        playerManager.playSong(song, q)
    }

    fun toggleFavorite(song: Song) {
        viewModelScope.launch {
            repository.setFavorite(song.id, !song.isFavorite)
        }
    }

    fun deleteSong(song: Song) {
        viewModelScope.launch {
            repository.deleteSong(song)
        }
    }

    fun openNowPlayingSheet() {
        _showNowPlayingSheet.value = true
    }

    fun closeNowPlayingSheet() {
        _showNowPlayingSheet.value = false
    }

    fun openSoundAliveDialog() {
        _showSoundAliveDialog.value = true
    }

    fun closeSoundAliveDialog() {
        _showSoundAliveDialog.value = false
    }

    fun dismissStatsDialog() {
        _statsDialogData.value = null
    }

    /**
     * Descarga desde una URL específica (individual o playlist)
     * Responde directamente al parámetro --url en musica.py
     */
    fun downloadFromUrl(url: String, autoEnrich: Boolean = true) {
        if (url.isBlank()) return
        viewModelScope.launch {
            _isDownloading.value = true
            try {
                val songs = engine.descargarDesdeUrl(url, autoEnrich) { progress ->
                    _downloadProgress.value = progress
                }
                repository.insertSongs(songs)
                for (s in songs) {
                    if (s.youtubeVideoId != null) {
                        repository.recordDownload(s.youtubeVideoId, s.title, s.youtubeChannel ?: "", s.filePath)
                    }
                }
            } catch (e: Exception) {
                _downloadProgress.value = DownloadProgress(
                    step = "Error en descarga",
                    percent = 0f,
                    error = e.localizedMessage
                )
            } finally {
                _isDownloading.value = false
            }
        }
    }

    /**
     * Descarga todas las playlists configuradas
     * Replica de python3 musica.py --descargar
     */
    fun downloadAllPlaylists(autoEnrich: Boolean = true) {
        viewModelScope.launch {
            _isDownloading.value = true
            try {
                for (url in MusicaEngine.PLAYLIST_PRESETS) {
                    val songs = engine.descargarDesdeUrl(url, autoEnrich) { progress ->
                        _downloadProgress.value = progress
                    }
                    repository.insertSongs(songs)
                    for (s in songs) {
                        if (s.youtubeVideoId != null) {
                            repository.recordDownload(s.youtubeVideoId, s.title, s.youtubeChannel ?: "", s.filePath)
                        }
                    }
                }
            } finally {
                _isDownloading.value = false
            }
        }
    }

    /**
     * Enriquece metadata de canciones (MusicBrainz & Portadas)
     * Replica de python3 musica.py --enriquecer / --forzar
     */
    fun enrichAllSongs(force: Boolean = false) {
        viewModelScope.launch {
            val list = rawSongs.value
            val toProcess = if (force) list else list.filter { it.musicBrainzScore < 80 }
            val total = toProcess.size
            if (total == 0) {
                Toast.makeText(getApplication(), "Todas las canciones ya están enriquecidas.", Toast.LENGTH_SHORT).show()
                return@launch
            }

            _enrichProgress.value = Pair(0, total)
            for ((index, song) in toProcess.withIndex()) {
                _enrichProgress.value = Pair(index + 1, total)
                val enriched = engine.enriquecerCancion(
                    tituloYt = song.title,
                    canal = song.youtubeChannel ?: song.artist,
                    videoId = song.youtubeVideoId ?: ""
                )
                repository.updateMetadata(
                    id = song.id,
                    title = enriched.title,
                    artist = enriched.artist,
                    album = enriched.album,
                    coverArtUrl = enriched.coverArtUrl,
                    score = enriched.score,
                    releaseId = enriched.releaseId
                )
            }
            _enrichProgress.value = null
            Toast.makeText(getApplication(), "Enriquecimiento completado ($total canciones).", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * Limpia la carpeta, deduplica y normaliza nombres
     * Replica de python3 musica.py --limpiar
     */
    fun cleanMusicFolder() {
        viewModelScope.launch {
            val res = engine.limpiarCarpeta(rawSongs.value)
            Toast.makeText(
                getApplication(),
                "Limpieza finalizada: ${res.first} renombrados, ${res.second} eliminados.",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    /**
     * Muestra las estadísticas de la biblioteca
     * Replica de python3 musica.py --stats
     */
    fun showStatistics() {
        viewModelScope.launch {
            val songs = rawSongs.value
            val favs = favoriteSongs.value
            val totalDur = songs.sumOf { it.durationMs } / 1000
            val artistsCount = songs.map { it.artist }.distinct().size
            val albumsCount = songs.map { it.album }.distinct().size
            val ytCount = songs.count { it.youtubeVideoId != null }
            val enrichedCount = songs.count { it.musicBrainzScore >= 80 }
            val totalBytes = songs.sumOf { it.fileSizeBytes }

            _statsDialogData.value = MusicStatistics(
                totalSongs = songs.size,
                totalFavorites = favs.size,
                totalDownloadedYt = ytCount,
                totalDurationSeconds = totalDur,
                totalArtists = artistsCount,
                totalAlbums = albumsCount,
                totalSizeBytes = totalBytes,
                enrichedCount = enrichedCount
            )
        }
    }

    /**
     * Exporta lista de canciones a CSV
     * Replica de python3 musica.py --exportar
     */
    fun exportToCsv(context: Context) {
        viewModelScope.launch {
            val csvContent = engine.exportarCsv(rawSongs.value, downloadHistory.value)
            try {
                val file = File(context.cacheDir, "canciones_samsung_music.csv")
                FileOutputStream(file).use { it.write(csvContent.toByteArray()) }

                val sendIntent = Intent(Intent.ACTION_SEND).apply {
                    type = "text/csv"
                    putExtra(Intent.EXTRA_SUBJECT, "Biblioteca Samsung Music")
                    putExtra(Intent.EXTRA_TEXT, csvContent)
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(Intent.createChooser(sendIntent, "Exportar canciones CSV").apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                })
            } catch (e: Exception) {
                Toast.makeText(context, "Error al exportar: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // --- PLAYLIST CRUD OPERATIONS ---
    fun selectPlaylist(playlistId: Long?) {
        _selectedPlaylistId.value = playlistId
    }

    fun openAddToPlaylistDialog(song: Song) {
        _songToAddToPlaylist.value = song
    }

    fun closeAddToPlaylistDialog() {
        _songToAddToPlaylist.value = null
    }

    fun createPlaylist(name: String, description: String = "") {
        if (name.isBlank()) return
        viewModelScope.launch {
            repository.createPlaylist(name.trim(), description.trim())
        }
    }

    fun renamePlaylist(playlistId: Long, newName: String) {
        if (newName.isBlank()) return
        viewModelScope.launch {
            repository.renamePlaylist(playlistId, newName.trim())
        }
    }

    fun updatePlaylistDescription(playlistId: Long, description: String) {
        viewModelScope.launch {
            repository.updatePlaylistDescription(playlistId, description.trim())
        }
    }

    fun deletePlaylist(playlistId: Long) {
        viewModelScope.launch {
            repository.deletePlaylist(playlistId)
            if (_selectedPlaylistId.value == playlistId) {
                _selectedPlaylistId.value = null
            }
        }
    }

    fun addSongToPlaylist(playlistId: Long, songId: String) {
        viewModelScope.launch {
            repository.addSongToPlaylist(playlistId, songId)
        }
    }

    fun addSongsToPlaylist(playlistId: Long, songIds: List<String>) {
        viewModelScope.launch {
            repository.addSongsToPlaylist(playlistId, songIds)
        }
    }

    fun removeSongFromPlaylist(playlistId: Long, songId: String) {
        viewModelScope.launch {
            repository.removeSongFromPlaylist(playlistId, songId)
        }
    }

    fun clearPlaylist(playlistId: Long) {
        viewModelScope.launch {
            repository.clearPlaylist(playlistId)
        }
    }

    /**
     * Reproducir a continuación (Samsung Music One UI)
     * Inserta la canción en la cola actual justo después de la pista en curso.
     */
    fun playNext(song: Song) {
        playerManager.playNext(song)
        Toast.makeText(
            getApplication(),
            "\"${song.title}\" se reproducirá a continuación",
            Toast.LENGTH_SHORT
        ).show()
    }

    fun playNext(songs: List<Song>) {
        if (songs.isEmpty()) return
        playerManager.playNext(songs)
        Toast.makeText(
            getApplication(),
            "${songs.size} canciones se reproducirán a continuación",
            Toast.LENGTH_SHORT
        ).show()
    }

    fun removeFromQueue(songId: String) {
        playerManager.removeFromQueue(songId)
    }

    fun playWithLoop(song: Song) {
        playerManager.playWithLoop(song)
        Toast.makeText(
            getApplication(),
            "Bucle activado para \"${song.title}\"",
            Toast.LENGTH_SHORT
        ).show()
    }

    fun playWithShuffle(song: Song, queue: List<Song>? = null) {
        val q = queue ?: filteredSongs.value
        playerManager.playWithShuffle(song, q)
        Toast.makeText(
            getApplication(),
            "Reproducción aleatoria activada",
            Toast.LENGTH_SHORT
        ).show()
    }

    fun toggleShuffleWithFeedback() {
        playerManager.toggleShuffle()
        val newState = playerManager.isShuffleEnabled.value
        Toast.makeText(
            getApplication(),
            if (newState) "Reproducción aleatoria activada" else "Reproducción aleatoria desactivada",
            Toast.LENGTH_SHORT
        ).show()
    }

    fun cycleRepeatModeWithFeedback() {
        playerManager.cycleRepeatMode()
        val msg = when (playerManager.repeatMode.value) {
            RepeatMode.ALL -> "Bucle: Repetir todas las canciones"
            RepeatMode.ONE -> "Bucle: Repetir canción actual"
            RepeatMode.OFF -> "Repetición en bucle desactivada"
        }
        Toast.makeText(getApplication(), msg, Toast.LENGTH_SHORT).show()
    }

    fun showFeedbackToast(msg: String) {
        Toast.makeText(getApplication(), msg, Toast.LENGTH_SHORT).show()
    }

    override fun onCleared() {
        super.onCleared()
        playerManager.release()
    }
}
