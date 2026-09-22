package com.example.player

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import com.example.data.Song
import com.example.engine.LrcParser
import com.example.engine.LyricLine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File

enum class RepeatMode {
    OFF, ALL, ONE
}

class AudioPlayerManager(private val context: Context) {

    companion object {
        var instance: AudioPlayerManager? = null
            private set
    }

    init {
        instance = this
    }

    private val scope = CoroutineScope(Dispatchers.Main + Job())
    private var mediaPlayer: MediaPlayer? = null
    private var progressJob: Job? = null

    private val _currentSong = MutableStateFlow<Song?>(null)
    val currentSong: StateFlow<Song?> = _currentSong.asStateFlow()

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _currentPositionMs = MutableStateFlow(0L)
    val currentPositionMs: StateFlow<Long> = _currentPositionMs.asStateFlow()

    private val _durationMs = MutableStateFlow(1L)
    val durationMs: StateFlow<Long> = _durationMs.asStateFlow()

    private val _isShuffleEnabled = MutableStateFlow(false)
    val isShuffleEnabled: StateFlow<Boolean> = _isShuffleEnabled.asStateFlow()

    private val _repeatMode = MutableStateFlow(RepeatMode.ALL)
    val repeatMode: StateFlow<RepeatMode> = _repeatMode.asStateFlow()

    private val _queue = MutableStateFlow<List<Song>>(emptyList())
    val queue: StateFlow<List<Song>> = _queue.asStateFlow()

    private val _currentLyrics = MutableStateFlow<List<LyricLine>>(emptyList())
    val currentLyrics: StateFlow<List<LyricLine>> = _currentLyrics.asStateFlow()

    private val _activeLyricIndex = MutableStateFlow(0)
    val activeLyricIndex: StateFlow<Int> = _activeLyricIndex.asStateFlow()

    private var isSimulatedPlayback = false
    private var nextPrioritySongId: String? = null
    private var hasCountedHalfPlay = false
    var onHalfPlayedCallback: ((String) -> Unit)? = null

    fun playSong(song: Song, newQueue: List<Song> = emptyList()) {
        hasCountedHalfPlay = false
        if (newQueue.isNotEmpty()) {
            _queue.value = newQueue
        } else if (!_queue.value.any { it.id == song.id }) {
            _queue.value = _queue.value + song
        }

        _currentSong.value = song
        _durationMs.value = if (song.durationMs > 0) song.durationMs else 210000L
        _currentPositionMs.value = 0L

        loadLyricsForSong(song)
        startPlayback(song)
    }

    /**
     * Samsung Music: "Reproducir a continuación"
     * Agrega la canción a la cola actual INMEDIATAMENTE después de la canción en reproducción,
     * no al final de la cola. Si la canción ya estaba en otra posición de la lista,
     * se reubica para que suene justo a continuación.
     */
    fun playNext(song: Song) {
        val current = _currentSong.value
        val currentQueue = _queue.value.toMutableList()

        if (current == null || currentQueue.isEmpty()) {
            playSong(song, listOf(song))
            return
        }

        if (current.id == song.id) {
            return
        }

        // Remover si ya existe previamente para evitar duplicados y moverla a la posición siguiente
        currentQueue.removeAll { it.id == song.id }

        val currentIndex = currentQueue.indexOfFirst { it.id == current.id }
        val insertIndex = if (currentIndex != -1) {
            currentIndex + 1
        } else {
            currentQueue.add(0, current)
            1
        }

        currentQueue.add(insertIndex, song)
        _queue.value = currentQueue
        nextPrioritySongId = song.id
    }

    /**
     * Agrega una colección de canciones inmediatamente a continuación de la pista actual.
     */
    fun playNext(songs: List<Song>) {
        if (songs.isEmpty()) return
        val current = _currentSong.value
        val currentQueue = _queue.value.toMutableList()

        if (current == null || currentQueue.isEmpty()) {
            playSong(songs.first(), songs)
            return
        }

        val songIds = songs.map { it.id }.toSet()
        currentQueue.removeAll { it.id in songIds }

        val currentIndex = currentQueue.indexOfFirst { it.id == current.id }
        val insertIndex = if (currentIndex != -1) currentIndex + 1 else 0

        currentQueue.addAll(insertIndex, songs)
        _queue.value = currentQueue
        nextPrioritySongId = songs.first().id
    }

    /**
     * Remueve una canción de la cola actual de reproducción.
     */
    fun removeFromQueue(songId: String) {
        val currentQueue = _queue.value.toMutableList()
        val index = currentQueue.indexOfFirst { it.id == songId }
        if (index != -1) {
            val isCurrent = _currentSong.value?.id == songId
            currentQueue.removeAt(index)
            _queue.value = currentQueue
            if (isCurrent) {
                if (currentQueue.isNotEmpty()) {
                    val nextIndex = index.coerceAtMost(currentQueue.size - 1)
                    playSong(currentQueue[nextIndex])
                } else {
                    stopPlayer()
                    _currentSong.value = null
                    _isPlaying.value = false
                }
            }
        }
    }

    private fun loadLyricsForSong(song: Song) {
        // 1. Intentar desde song.lyricsLrc
        var rawLrc = song.lyricsLrc
        // 2. Si es nulo, buscar archivo .lrc en disco
        if (rawLrc.isNullOrBlank()) {
            val candidateFile = if (!song.lrcFilePath.isNullOrBlank()) {
                File(song.lrcFilePath)
            } else if (song.filePath.isNotBlank()) {
                File(song.filePath.removeSuffix(".mp3") + ".lrc")
            } else null

            if (candidateFile != null && candidateFile.exists()) {
                rawLrc = candidateFile.readText(Charsets.UTF_8)
            }
        }

        val parsed = LrcParser.parse(rawLrc)
        if (parsed.isNotEmpty()) {
            _currentLyrics.value = parsed
        } else {
            // Generar sincronización inicial para una experiencia visual instantánea
            val dummyLines = listOf(
                LyricLine(0L, song.title),
                LyricLine(4000L, song.artist),
                LyricLine(8000L, "♪ ♪ ♪"),
                LyricLine(14000L, "Reproduciendo en Samsung Music"),
                LyricLine(22000L, "Audio de alta fidelidad con SoundAlive"),
                LyricLine(32000L, "Soporte de segundo plano activo"),
                LyricLine(45000L, "♪ ♪ ♪"),
                LyricLine(60000L, "Disfruta de la mejor experiencia musical"),
                LyricLine(80000L, "♪ ♪ ♪"),
                LyricLine(120000L, song.album),
                LyricLine(160000L, "Fin de la pista")
            )
            _currentLyrics.value = dummyLines
        }
        _activeLyricIndex.value = 0
    }

    private fun startPlayback(song: Song) {
        stopPlayer()
        isSimulatedPlayback = false

        try {
            val file = File(song.filePath)
            if (file.exists() && file.length() > 5000) {
                mediaPlayer = MediaPlayer().apply {
                    setAudioAttributes(
                        AudioAttributes.Builder()
                            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                            .setUsage(AudioAttributes.USAGE_MEDIA)
                            .build()
                    )
                    setDataSource(file.absolutePath)
                    prepare()
                    start()
                    setOnCompletionListener {
                        handleSongCompletion()
                    }
                }
                _durationMs.value = mediaPlayer?.duration?.toLong() ?: song.durationMs
                _isPlaying.value = true
            } else {
                isSimulatedPlayback = true
                _isPlaying.value = true
            }
        } catch (_: Exception) {
            isSimulatedPlayback = true
            _isPlaying.value = true
        }

        notifyForegroundService(true)
        startProgressTicker()
    }

    private fun notifyForegroundService(playing: Boolean) {
        val s = _currentSong.value ?: return
        try {
            MusicPlaybackService.startService(
                context = context,
                title = s.title,
                artist = s.artist,
                isPlaying = playing,
                artUrl = s.coverArtUrl
            )
        } catch (_: Exception) {}
    }

    fun togglePlayPause() {
        if (_currentSong.value == null) {
            val first = _queue.value.firstOrNull()
            if (first != null) {
                playSong(first)
            }
            return
        }

        if (_isPlaying.value) {
            pause()
        } else {
            resume()
        }
    }

    fun pause() {
        _isPlaying.value = false
        try {
            mediaPlayer?.pause()
        } catch (_: Exception) {}
        notifyForegroundService(false)
        progressJob?.cancel()
    }

    fun resume() {
        if (_currentSong.value == null) return
        _isPlaying.value = true
        try {
            mediaPlayer?.start()
        } catch (_: Exception) {
            isSimulatedPlayback = true
        }
        notifyForegroundService(true)
        startProgressTicker()
    }

    fun seekTo(positionMs: Long) {
        val clamped = positionMs.coerceIn(0L, _durationMs.value)
        _currentPositionMs.value = clamped
        try {
            mediaPlayer?.seekTo(clamped.toInt())
        } catch (_: Exception) {}
        updateActiveLyric(clamped)
    }

    fun seekToLyricLine(index: Int) {
        val lyrics = _currentLyrics.value
        if (index in lyrics.indices) {
            seekTo(lyrics[index].timeMs)
        }
    }

    private fun updateActiveLyric(timeMs: Long) {
        val lyrics = _currentLyrics.value
        if (lyrics.isNotEmpty()) {
            _activeLyricIndex.value = LrcParser.findActiveIndex(timeMs, lyrics)
        }
    }

    fun skipToNext() {
        val current = _currentSong.value ?: return
        val currentQueue = _queue.value
        if (currentQueue.isEmpty()) return

        val currentIndex = currentQueue.indexOfFirst { it.id == current.id }

        // Si se seleccionó una pista explícita para reproducir a continuación, darle prioridad inmediata
        if (nextPrioritySongId != null) {
            val priorityIndex = currentQueue.indexOfFirst { it.id == nextPrioritySongId }
            nextPrioritySongId = null
            if (priorityIndex in currentQueue.indices && priorityIndex != currentIndex) {
                playSong(currentQueue[priorityIndex])
                return
            }
        }

        val nextIndex = when {
            _isShuffleEnabled.value -> {
                currentQueue.indices.filter { it != currentIndex }.randomOrNull() ?: currentIndex
            }
            currentIndex < currentQueue.size - 1 -> currentIndex + 1
            _repeatMode.value == RepeatMode.ALL -> 0
            else -> currentIndex
        }

        if (nextIndex in currentQueue.indices && (nextIndex != currentIndex || currentQueue.size == 1)) {
            playSong(currentQueue[nextIndex])
        }
    }

    fun skipToPrevious() {
        if (_currentPositionMs.value > 3000L) {
            seekTo(0L)
            return
        }
        val current = _currentSong.value ?: return
        val currentQueue = _queue.value
        if (currentQueue.isEmpty()) return

        val currentIndex = currentQueue.indexOfFirst { it.id == current.id }
        val prevIndex = when {
            currentIndex > 0 -> currentIndex - 1
            _repeatMode.value == RepeatMode.ALL -> currentQueue.size - 1
            else -> 0
        }

        if (prevIndex in currentQueue.indices) {
            playSong(currentQueue[prevIndex])
        }
    }

    fun toggleShuffle() {
        _isShuffleEnabled.value = !_isShuffleEnabled.value
    }

    fun setShuffle(enabled: Boolean) {
        _isShuffleEnabled.value = enabled
    }

    fun cycleRepeatMode() {
        _repeatMode.value = when (_repeatMode.value) {
            RepeatMode.OFF -> RepeatMode.ALL
            RepeatMode.ALL -> RepeatMode.ONE
            RepeatMode.ONE -> RepeatMode.OFF
        }
    }

    fun setRepeatMode(mode: RepeatMode) {
        _repeatMode.value = mode
    }

    fun playWithLoop(song: Song) {
        _repeatMode.value = RepeatMode.ONE
        playSong(song)
    }

    fun playWithShuffle(song: Song, queue: List<Song>) {
        _isShuffleEnabled.value = true
        playSong(song, queue)
    }

    private fun handleSongCompletion() {
        when (_repeatMode.value) {
            RepeatMode.ONE -> {
                seekTo(0L)
                resume()
            }
            RepeatMode.ALL -> skipToNext()
            RepeatMode.OFF -> {
                val currentQueue = _queue.value
                val currentIndex = currentQueue.indexOfFirst { it.id == _currentSong.value?.id }
                if (currentIndex < currentQueue.size - 1) {
                    skipToNext()
                } else {
                    pause()
                    seekTo(0L)
                }
            }
        }
    }

    private fun startProgressTicker() {
        progressJob?.cancel()
        progressJob = scope.launch {
            while (isActive && _isPlaying.value) {
                delay(250)
                if (isSimulatedPlayback) {
                    val next = _currentPositionMs.value + 250L
                    if (next >= _durationMs.value) {
                        handleSongCompletion()
                    } else {
                        _currentPositionMs.value = next
                        updateActiveLyric(next)
                    }
                } else {
                    try {
                        val current = mediaPlayer?.currentPosition?.toLong() ?: 0L
                        _currentPositionMs.value = current
                        updateActiveLyric(current)
                    } catch (_: Exception) {}
                }

                // Incrementar contador si se reproduce más de la mitad de la canción
                val currentPos = _currentPositionMs.value
                val totalDur = _durationMs.value
                if (!hasCountedHalfPlay && totalDur > 1000L && currentPos >= (totalDur / 2)) {
                    hasCountedHalfPlay = true
                    _currentSong.value?.let { current ->
                        onHalfPlayedCallback?.invoke(current.id)
                    }
                }
            }
        }
    }

    private fun stopPlayer() {
        progressJob?.cancel()
        try {
            mediaPlayer?.stop()
            mediaPlayer?.release()
        } catch (_: Exception) {}
        mediaPlayer = null
    }

    fun release() {
        stopPlayer()
        try {
            MusicPlaybackService.stopService(context)
        } catch (_: Exception) {}
    }
}

