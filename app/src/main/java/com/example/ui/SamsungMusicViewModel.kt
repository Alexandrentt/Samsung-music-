package com.example.ui

import android.app.Application
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.core.content.FileProvider
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.AppDatabase
import com.example.data.DownloadHistoryItem
import com.example.data.MusicRepository
import com.example.data.MusicStatistics
import com.example.data.Playlist
import com.example.data.PlaylistWithSongs
import com.example.data.Song
import com.example.engine.DownloadProgress
import com.example.engine.MusicaEngine
import com.example.player.AudioPlayerManager
import com.example.player.RepeatMode
import com.example.worker.PlaylistSyncWorker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class SamsungMusicViewModel(application: Application) : AndroidViewModel(application) {

    private val db = AppDatabase.getDatabase(application)
    val repository = MusicRepository(db.songDao(), db.downloadHistoryDao(), db.playlistDao())
    val engine = MusicaEngine(application)
    val playerManager = AudioPlayerManager(application)

    private val _selectedTab = MutableStateFlow(SamsungTab.TRACKS)
    val selectedTab: StateFlow<SamsungTab> = _selectedTab.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _isSearchActive = MutableStateFlow(false)
    val isSearchActive: StateFlow<Boolean> = _isSearchActive.asStateFlow()

    private val _downloadProgress = MutableStateFlow<DownloadProgress?>(null)
    val downloadProgress: StateFlow<DownloadProgress?> = _downloadProgress.asStateFlow()

    private val _isDownloading = MutableStateFlow(false)
    val isDownloading: StateFlow<Boolean> = _isDownloading.asStateFlow()

    private val _youtubeSearchQuery = MutableStateFlow("")
    val youtubeSearchQuery: StateFlow<String> = _youtubeSearchQuery.asStateFlow()

    private val _isSearchingYouTube = MutableStateFlow(false)
    val isSearchingYouTube: StateFlow<Boolean> = _isSearchingYouTube.asStateFlow()

    private val _youtubeSearchResults = MutableStateFlow<List<MusicaEngine.PlaylistItem>>(emptyList())
    val youtubeSearchResults: StateFlow<List<MusicaEngine.PlaylistItem>> = _youtubeSearchResults.asStateFlow()

    val rawSongs: StateFlow<List<Song>> = repository.allSongs.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList()
    )

    /**
     * Coincidencias en la biblioteca local (canciones ya descargadas) para la
     * consulta de búsqueda actual. Se muestran ANTES de los resultados de YouTube.
     */
    val localSearchResults: StateFlow<List<Song>> = combine(
        rawSongs,
        _youtubeSearchQuery
    ) { songs, query ->
        val q = query.trim().lowercase()
        if (q.isBlank() || q.contains("youtube.com") || q.contains("youtu.be")) {
            emptyList()
        } else {
            songs.filter {
                it.title.lowercase().contains(q) ||
                        it.artist.lowercase().contains(q) ||
                        it.album.lowercase().contains(q)
            }.take(10)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

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

    private val _songViewMode = MutableStateFlow(SongViewMode.LIST)
    val songViewMode: StateFlow<SongViewMode> = _songViewMode.asStateFlow()

    private val prefs = application.getSharedPreferences("samsung_music_order_prefs", Context.MODE_PRIVATE)

    private val _customOrderList = MutableStateFlow<List<String>>(loadCustomOrder())
    val customOrderList: StateFlow<List<String>> = _customOrderList.asStateFlow()

    private fun loadCustomOrder(): List<String> {
        val raw = prefs.getString("custom_order_ids", "") ?: ""
        return if (raw.isBlank()) emptyList() else raw.split(",").filter { it.isNotBlank() }
    }

    private fun saveCustomOrder(list: List<String>) {
        _customOrderList.value = list
        prefs.edit().putString("custom_order_ids", list.joinToString(",")).apply()
    }

    fun moveSong(songId: String, offset: Int) {
        val currentDisplaySongs = filteredSongs.value
        val currentIds = currentDisplaySongs.map { it.id }.toMutableList()
        val index = currentIds.indexOf(songId)
        if (index == -1) return

        val targetIndex = when (offset) {
            -9999 -> 0
            9999 -> currentIds.lastIndex
            else -> (index + offset).coerceIn(0, currentIds.lastIndex)
        }

        if (index != targetIndex) {
            val item = currentIds.removeAt(index)
            currentIds.add(targetIndex, item)
            saveCustomOrder(currentIds)
            _songSortOrder.value = SongSortOrder(field = SongSortField.CUSTOM, ascending = true)
            showFeedbackToast("Orden personalizado actualizado")
        }
    }

    private val _songSortOrder = MutableStateFlow(SongSortOrder())
    val songSortOrder: StateFlow<SongSortOrder> = _songSortOrder.asStateFlow()

    val favoriteSongs: StateFlow<List<Song>> = repository.favoriteSongs.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList()
    )

    val downloadHistory: StateFlow<List<DownloadHistoryItem>> = repository.downloadHistory.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList()
    )

    val playlists: StateFlow<List<Playlist>> = repository.allPlaylists.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList()
    )

    val playlistsWithSongs: StateFlow<List<PlaylistWithSongs>> = repository.allPlaylistsWithSongs.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList()
    )

    val filteredSongs: StateFlow<List<Song>> = combine(
        rawSongs,
        _searchQuery,
        _songSortOrder,
        _customOrderList
    ) { songs, query, sortOrder, customList ->
        val filtered = if (query.isBlank()) {
            songs
        } else {
            val q = query.trim().lowercase()
            songs.filter {
                it.title.lowercase().contains(q) ||
                        it.artist.lowercase().contains(q) ||
                        it.album.lowercase().contains(q)
            }
        }
        val orderMap = customList.mapIndexed { idx, id -> id to idx }.toMap()
        filtered.sortedWith(sortOrder.comparator(orderMap))
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        playerManager.onHalfPlayedCallback = { songId ->
            viewModelScope.launch(Dispatchers.IO) {
                repository.incrementPlayCount(songId)
            }
        }
        viewModelScope.launch {
            com.example.engine.MusicDownloadService.downloadProgress.collect { progress ->
                if (progress != null) {
                    _downloadProgress.value = progress
                }
            }
        }
        viewModelScope.launch {
            com.example.engine.MusicDownloadService.isDownloading.collect { downloading ->
                _isDownloading.value = downloading
            }
        }
        viewModelScope.launch {
            seedInitialMusic()
        }
        recoverExistingSongs()
        autoEnrichExistingSongs()
        observePlaylistForWidget()
    }

    /**
     * Recupera canciones existentes guardadas en el almacenamiento compartido
     * público (/Music/SamsungMusic) o interno para que NO se pierdan si el usuario
     * desinstala y reinstala la aplicación.
     */
    private fun recoverExistingSongs() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val recovered = engine.scanAndRecoverExistingSongs()
                val existing = repository.allSongs.firstOrNull() ?: emptyList()
                val existingIds = existing.map { it.id }.toSet()
                for (song in recovered) {
                    if (song.id !in existingIds) {
                        repository.insertSong(song)
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    /**
     * Refresca el widget de lista de reproducción cuando cambian los datos.
     * El widget muestra la playlist seleccionada (o la primera con canciones).
     */
    private fun observePlaylistForWidget() {
        viewModelScope.launch(Dispatchers.IO) {
            repository.allPlaylistsWithSongs.collect { playlists ->
                com.example.widget.PlaylistWidgetProvider.refreshAll(getApplication())
            }
        }
    }

    /**
     * Al arrancar, completa los metadatos de las canciones existentes usando
     * MusicBrainz (solo las que aún no tienen una puntuación de enriquecimiento alta).
     */
    private fun autoEnrichExistingSongs() {
        viewModelScope.launch(Dispatchers.IO) {
            // Espera (hasta ~60 s) a que la biblioteca se cargue por primera vez.
            var songs = rawSongs.value
            var waitedMs = 0
            while (songs.isEmpty() && waitedMs < 60_000) {
                delay(1_000)
                waitedMs += 1_000
                songs = rawSongs.value
            }
            if (songs.isEmpty()) return@launch

            val pending = songs.filter { it.enrichmentScore < 70 || it.album == "YouTube Downloads" }
            if (pending.isEmpty()) return@launch

            val total = pending.size
            pending.forEachIndexed { index, song ->
                _enrichProgress.value = Pair(index + 1, total)
                val result = engine.buscarMusicBrainz(song.artist, song.title)
                    // Para canciones de YouTube sin enriquecer, el artista puede ser
                    // el nombre del canal: reintenta solo con el título.
                    ?: if (song.album == "YouTube Downloads") {
                        engine.buscarMusicBrainz("", song.title)
                    } else {
                        null
                    }
                if (result != null) {
                    repository.updateMetadata(
                        id = song.id,
                        title = result.title,
                        artist = result.artist,
                        album = result.album,
                        coverArtUrl = result.coverArtUrl ?: song.coverArtUrl,
                        score = result.score,
                        releaseId = result.releaseId
                    )
                }
                // Cortesía con la API pública de MusicBrainz (~1 req/seg).
                delay(1_100)
            }
            _enrichProgress.value = null
        }
    }

    /**
     * Migración: elimina los "archivos de audio" sintéticos generados por versiones
     * anteriores (un tono falso de 16 KB que no era música real). Tras la limpieza,
     * si la biblioteca queda vacía, se resincroniza la playlist con descargas reales.
     */
    private suspend fun purgeLegacyFakeAudio() {
        val songs = repository.allSongs.firstOrNull() ?: return
        var purged = 0
        for (song in songs) {
            if (!song.id.startsWith("yt_")) continue
            val f = File(song.filePath)
            val esFalso = !f.exists() || f.length() < 10_000L
            if (esFalso) {
                song.lyricsPath?.let { File(it).delete() }
                repository.deleteSong(song)
                purged++
            }
        }
        if (purged > 0) {
            android.util.Log.i("SamsungMusic", "Purga de audios sintéticos: $purged canciones eliminadas")
        }
    }

    suspend fun seedInitialMusic() = withContext(Dispatchers.IO) {
        // Schedule daily background sync via WorkManager
        PlaylistSyncWorker.scheduleDailySync(getApplication())

        // Limpia primero los archivos falsos de versiones anteriores
        purgeLegacyFakeAudio()

        val currentSongsCount = repository.allSongs.firstOrNull()?.size ?: 0
        val currentPlaylistsCount = repository.getPlaylistCount()

        if (currentPlaylistsCount == 0) {
            // Create ONLY the requested playlist
            val playlistId = repository.createPlaylist(
                name = "Mi Playlist",
                description = "Sincronizada diariamente desde YouTube",
                coverArtUrl = null
            )
            _selectedPlaylistId.value = playlistId
        }

        // If no songs yet, download the requested YouTube playlist
        if (currentSongsCount == 0) {
            downloadFromUrl(MusicaEngine.PLAYLIST_URL_DEFAULT, autoEnrich = false, showToast = false)
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
        if (!active) {
            _searchQuery.value = ""
        }
    }

    fun setSongSortOrder(order: SongSortOrder) {
        _songSortOrder.value = order
    }

    fun toggleSongSortDirection() {
        _songSortOrder.value = _songSortOrder.value.copy(ascending = !_songSortOrder.value.ascending)
    }

    fun toggleSongViewMode() {
        _songViewMode.value = if (_songViewMode.value == SongViewMode.LIST) SongViewMode.GRID else SongViewMode.LIST
    }

    fun setSongViewMode(mode: SongViewMode) {
        _songViewMode.value = mode
    }

    fun playSong(song: Song, queue: List<Song>? = null) {
        playerManager.playSong(song, queue ?: filteredSongs.value)
    }

    /**
     * Reproducción aleatoria real: construye una cola barajada con todas las
     * canciones indicadas y comienza por la primera de ellas desde el inicio.
     */
    fun playAllShuffled(songs: List<Song>) {
        if (songs.isEmpty()) return
        playerManager.startShuffled(songs)
    }

    fun toggleFavorite(song: Song) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.setFavorite(song.id, !song.isFavorite)
        }
    }

    fun deleteSong(song: Song) {
        viewModelScope.launch(Dispatchers.IO) {
            val file = File(song.filePath)
            if (file.exists()) file.delete()
            song.lyricsPath?.let {
                val lf = File(it)
                if (lf.exists()) lf.delete()
            }
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
        viewModelScope.launch(Dispatchers.IO) {
            repository.createPlaylist(name, description)
        }
    }

    fun renamePlaylist(playlistId: Long, newName: String) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.renamePlaylist(playlistId, newName)
        }
    }

    fun updatePlaylistDescription(playlistId: Long, description: String) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.updatePlaylistDescription(playlistId, description)
        }
    }

    fun deletePlaylist(playlistId: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.deletePlaylist(playlistId)
            if (_selectedPlaylistId.value == playlistId) {
                _selectedPlaylistId.value = null
            }
        }
    }

    fun addSongToPlaylist(playlistId: Long, songId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.addSongToPlaylist(playlistId, songId)
        }
    }

    fun addSongsToPlaylist(playlistId: Long, songIds: List<String>) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.addSongsToPlaylist(playlistId, songIds)
        }
    }

    fun removeSongFromPlaylist(playlistId: Long, songId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.removeSongFromPlaylist(playlistId, songId)
        }
    }

    fun clearPlaylist(playlistId: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.clearPlaylist(playlistId)
        }
    }

    fun playNext(songs: List<Song>) {
        playerManager.playNext(songs)
        showFeedbackToast("Se reproducirá a continuación")
    }

    fun removeFromQueue(songId: String) {
        playerManager.removeFromQueue(songId)
    }

    fun playWithLoop() {
        playerManager.playWithLoop()
        showFeedbackToast("Repetición activada")
    }

    fun playWithShuffle() {
        playerManager.playWithShuffle()
        showFeedbackToast("Modo aleatorio activado")
    }

    fun toggleShuffleWithFeedback() {
        playerManager.toggleShuffle()
        val text = if (playerManager.isShuffleEnabled.value) "Aleatorio activado" else "Aleatorio desactivado"
        showFeedbackToast(text)
    }

    fun cycleRepeatModeWithFeedback() {
        playerManager.cycleRepeatMode()
        val text = when (playerManager.repeatMode.value) {
            RepeatMode.OFF -> "Repetición desactivada"
            RepeatMode.ALL -> "Repetir todo"
            RepeatMode.ONE -> "Repetir 1"
        }
        showFeedbackToast(text)
    }

    private fun showFeedbackToast(msg: String) {
        android.os.Handler(android.os.Looper.getMainLooper()).post {
            try {
                Toast.makeText(getApplication(), msg, Toast.LENGTH_SHORT).show()
            } catch (_: Exception) {}
        }
    }

    fun downloadFromUrl(url: String, autoEnrich: Boolean = false, showToast: Boolean = true) {
        if (_isDownloading.value) {
            if (showToast) {
                showFeedbackToast("Ya hay una descarga en proceso")
            }
            return
        }
        com.example.engine.MusicDownloadService.startDownload(getApplication(), url, autoEnrich)
        if (showToast) {
            showFeedbackToast("Descarga iniciada en segundo plano con notificación activa")
        }
    }

    fun downloadAllPlaylists() {
        downloadFromUrl(MusicaEngine.PLAYLIST_URL_DEFAULT, autoEnrich = false)
    }

    fun setYouTubeSearchQuery(query: String) {
        _youtubeSearchQuery.value = query
    }

    fun searchYouTube(query: String) {
        val q = query.trim()
        _youtubeSearchQuery.value = q
        if (q.isBlank()) {
            _youtubeSearchResults.value = emptyList()
            return
        }

        // If it's a direct URL to a playlist or video, trigger download directly
        val isDirectUrl = q.contains("youtube.com") || q.contains("youtu.be")
        if (isDirectUrl) {
            downloadFromUrl(q, autoEnrich = false)
            return
        }

        viewModelScope.launch(Dispatchers.IO) {
            _isSearchingYouTube.value = true
            try {
                val results = engine.buscarVideosYouTube(q)
                _youtubeSearchResults.value = results
            } catch (e: Exception) {
                e.printStackTrace()
                _youtubeSearchResults.value = emptyList()
            } finally {
                _isSearchingYouTube.value = false
            }
        }
    }

    fun downloadPlaylistItem(item: MusicaEngine.PlaylistItem, autoEnrich: Boolean = false) {
        val url = "https://www.youtube.com/watch?v=${item.videoId}"
        downloadFromUrl(url, autoEnrich = autoEnrich)
    }

    /** Reproduce una coincidencia local desde la búsqueda unificada. */
    fun playLocalSearchResult(song: Song) {
        val queue = if (localSearchResults.value.size > 1) {
            localSearchResults.value
        } else {
            filteredSongs.value
        }
        playerManager.playSong(song, queue)
    }

    fun enrichAllSongs(force: Boolean = false) {
        viewModelScope.launch(Dispatchers.IO) {
            val songs = rawSongs.value
            var count = 0
            val total = songs.size
            _enrichProgress.value = Pair(0, total)

            for (s in songs) {
                if (force || s.enrichmentScore < 70) {
                    val result = engine.buscarMusicBrainz(s.artist, s.title)
                    if (result != null) {
                        repository.updateMetadata(
                            id = s.id,
                            title = result.title,
                            artist = result.artist,
                            album = result.album,
                            coverArtUrl = result.coverArtUrl ?: s.coverArtUrl,
                            score = result.score,
                            releaseId = result.releaseId
                        )
                    }
                }
                count++
                _enrichProgress.value = Pair(count, total)
            }
            _enrichProgress.value = null
        }
    }

    fun cleanMusicFolder() {
        viewModelScope.launch(Dispatchers.IO) {
            val (renamed, deleted) = engine.limpiarCarpeta(rawSongs.value)
            showFeedbackToast("Limpieza completada: $renamed renombrados, $deleted eliminados")
        }
    }

    fun clearHistory() {
        viewModelScope.launch(Dispatchers.IO) {
            repository.clearHistory()
        }
    }

    fun showStatistics() {
        val songs = rawSongs.value
        val favs = favoriteSongs.value.size
        val ytDownloads = downloadHistory.value.size
        val duration = songs.sumOf { it.durationSeconds }
        val artistsCount = songs.map { it.artist.lowercase() }.distinct().size
        val albumsCount = songs.map { it.album.lowercase() }.distinct().size
        val sizeBytes = songs.sumOf { it.fileSizeBytes }
        val enrichedCount = songs.count { it.enrichmentScore >= 70 }

        _statsDialogData.value = MusicStatistics(
            totalSongs = songs.size,
            totalFavorites = favs,
            totalDownloadedYt = ytDownloads,
            totalDurationSeconds = duration,
            totalArtists = artistsCount,
            totalAlbums = albumsCount,
            totalSizeBytes = sizeBytes,
            enrichedCount = enrichedCount
        )
    }

    fun exportToCsv(context: Context) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val csvContent = engine.exportarCsv(downloadHistory.value, rawSongs.value)
                val file = File(context.cacheDir, "historial_musica.csv")
                file.writeText(csvContent, Charsets.UTF_8)

                val uri = FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    file
                )

                val intent = Intent(Intent.ACTION_SEND).apply {
                    type = "text/csv"
                    putExtra(Intent.EXTRA_SUBJECT, "Historial de Descargas Samsung Music")
                    putExtra(Intent.EXTRA_STREAM, uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }

                val chooser = Intent.createChooser(intent, "Compartir historial CSV")
                chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(chooser)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        playerManager.release()
    }
}
