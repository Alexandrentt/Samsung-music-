package com.example.player

import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.AudioPlaybackConfiguration
import android.media.MediaPlayer
import android.os.Build
import android.os.Environment
import android.os.Handler
import android.os.Looper
import androidx.core.content.ContextCompat
import com.example.data.Song
import com.example.engine.LrcParser
import com.example.engine.LyricLine
import com.example.engine.YouTubeAudioDownloader
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class AudioPlayerManager(private val context: Context) {

    private val scope = CoroutineScope(Dispatchers.Main)
    private var mediaPlayer: MediaPlayer? = null
    private var progressJob: Job? = null

    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private var audioFocusRequest: AudioFocusRequest? = null
    private var playbackCallback: AudioManager.AudioPlaybackCallback? = null

    // Estados de reproducción
    private val _currentSong = MutableStateFlow<Song?>(null)
    val currentSong: StateFlow<Song?> = _currentSong.asStateFlow()

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _currentPositionMs = MutableStateFlow(0L)
    val currentPositionMs: StateFlow<Long> = _currentPositionMs.asStateFlow()

    private val _durationMs = MutableStateFlow(0L)
    val durationMs: StateFlow<Long> = _durationMs.asStateFlow()

    private val _isShuffleEnabled = MutableStateFlow(false)
    val isShuffleEnabled: StateFlow<Boolean> = _isShuffleEnabled.asStateFlow()

    private val _repeatMode = MutableStateFlow(RepeatMode.OFF)
    val repeatMode: StateFlow<RepeatMode> = _repeatMode.asStateFlow()

    private val _queue = MutableStateFlow<List<Song>>(emptyList())
    val queue: StateFlow<List<Song>> = _queue.asStateFlow()

    private val _currentLyrics = MutableStateFlow<List<LyricLine>>(emptyList())
    val currentLyrics: StateFlow<List<LyricLine>> = _currentLyrics.asStateFlow()

    private val _activeLyricIndex = MutableStateFlow(0)
    val activeLyricIndex: StateFlow<Int> = _activeLyricIndex.asStateFlow()

    private var hasCountedHalfPlay = false
    var onHalfPlayedCallback: ((String) -> Unit)? = null
    var onToggleFavoriteCallback: ((Song) -> Unit)? = null

    // Detección y convivencia con otras apps de audio (YouTube, YouTube Music, etc.)
    private val _isDuckingEnabled = MutableStateFlow(true)
    val isDuckingEnabled: StateFlow<Boolean> = _isDuckingEnabled.asStateFlow()

    private val _pauseOnOtherMedia = MutableStateFlow(true)
    val pauseOnOtherMedia: StateFlow<Boolean> = _pauseOnOtherMedia.asStateFlow()

    private val _isOtherAppPlaying = MutableStateFlow(false)
    val isOtherAppPlaying: StateFlow<Boolean> = _isOtherAppPlaying.asStateFlow()

    private val _isDucked = MutableStateFlow(false)
    val isDucked: StateFlow<Boolean> = _isDucked.asStateFlow()

    private var wasPlayingBeforeFocusLoss = false
    private var isDuckedByFocus = false

    fun setDuckingEnabled(enabled: Boolean) {
        _isDuckingEnabled.value = enabled
        if (!enabled && isDuckedByFocus) {
            restoreVolume()
        }
    }

    fun setPauseOnOtherMedia(enabled: Boolean) {
        _pauseOnOtherMedia.value = enabled
    }

    // Sleep Timer (Temporizador de apagado)
    private var sleepTimerJob: Job? = null
    private val _sleepTimerRemainingMs = MutableStateFlow<Long?>(null)
    val sleepTimerRemainingMs: StateFlow<Long?> = _sleepTimerRemainingMs.asStateFlow()

    private val _sleepTimerPauseAtEndOfSong = MutableStateFlow(false)
    val sleepTimerPauseAtEndOfSong: StateFlow<Boolean> = _sleepTimerPauseAtEndOfSong.asStateFlow()

    fun startSleepTimer(minutes: Int, pauseAtEndOfSong: Boolean = false) {
        sleepTimerJob?.cancel()
        _sleepTimerPauseAtEndOfSong.value = pauseAtEndOfSong

        if (minutes <= 0) {
            _sleepTimerRemainingMs.value = null
            return
        }

        val totalMs = minutes * 60 * 1000L
        _sleepTimerRemainingMs.value = totalMs

        sleepTimerJob = scope.launch {
            var remaining = totalMs
            while (isActive && remaining > 0) {
                delay(1000L)
                remaining -= 1000L
                _sleepTimerRemainingMs.value = remaining.coerceAtLeast(0L)
            }
            if (isActive) {
                _sleepTimerRemainingMs.value = null
                if (!_sleepTimerPauseAtEndOfSong.value) {
                    pause()
                }
            }
        }
    }

    fun cancelSleepTimer() {
        sleepTimerJob?.cancel()
        sleepTimerJob = null
        _sleepTimerRemainingMs.value = null
        _sleepTimerPauseAtEndOfSong.value = false
    }

    fun addSleepTimerMinutes(extraMinutes: Int) {
        val currentMs = _sleepTimerRemainingMs.value ?: 0L
        val newMinutes = ((currentMs + extraMinutes * 60 * 1000L) / 60000L).toInt().coerceAtLeast(1)
        startSleepTimer(newMinutes, _sleepTimerPauseAtEndOfSong.value)
    }

    fun toggleFavoriteCurrentSong() {
        _currentSong.value?.let { song ->
            onToggleFavoriteCallback?.invoke(song)
        }
    }

    fun updateCurrentSongCover(newCoverUrl: String) {
        val cur = _currentSong.value ?: return
        val updated = cur.copy(coverArtUrl = newCoverUrl)
        _currentSong.value = updated
        _queue.value = _queue.value.map { if (it.id == cur.id) updated else it }
        notifyForegroundService(_isPlaying.value)
    }

    fun updateCurrentSongFavorite(isFavorite: Boolean) {
        val cur = _currentSong.value ?: return
        val updated = cur.copy(isFavorite = isFavorite)
        _currentSong.value = updated
        _queue.value = _queue.value.map { if (it.id == cur.id) updated else it }
        notifyForegroundService(_isPlaying.value)
    }

    /** Mensaje de error o estado de preparación de reproducción. */
    private val _playbackError = MutableStateFlow<String?>(null)
    val playbackError: StateFlow<String?> = _playbackError.asStateFlow()

    fun clearPlaybackError() {
        _playbackError.value = null
    }

    // Listener del foco de audio
    private val audioFocusChangeListener = AudioManager.OnAudioFocusChangeListener { focusChange ->
        when (focusChange) {
            AudioManager.AUDIOFOCUS_LOSS -> {
                // Pérdida permanente del foco (p. ej. YouTube o YouTube Music empezó a reproducir)
                android.util.Log.i("AudioPlayerManager", "AudioFocus LOSS -> pausando música por otra app multimedia")
                wasPlayingBeforeFocusLoss = false
                if (_isPlaying.value) {
                    pause()
                }
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                // Pérdida transitoria (llamada telefónica o video corto)
                android.util.Log.i("AudioPlayerManager", "AudioFocus LOSS_TRANSIENT -> pausando música")
                if (_isPlaying.value) {
                    wasPlayingBeforeFocusLoss = true
                    pause()
                }
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                // Pérdida transitoria que permite atenuar (notificación, indicación de GPS o audio en segundo plano)
                android.util.Log.i("AudioPlayerManager", "AudioFocus LOSS_TRANSIENT_CAN_DUCK")
                if (_pauseOnOtherMedia.value) {
                    if (_isPlaying.value) {
                        wasPlayingBeforeFocusLoss = true
                        pause()
                    }
                } else if (_isDuckingEnabled.value) {
                    duckVolume()
                }
            }
            AudioManager.AUDIOFOCUS_GAIN -> {
                // Foco recuperado
                android.util.Log.i("AudioPlayerManager", "AudioFocus GAIN -> restaurando volumen")
                restoreVolume()
                if (wasPlayingBeforeFocusLoss && !_isPlaying.value) {
                    wasPlayingBeforeFocusLoss = false
                    resume()
                }
            }
        }
    }

    init {
        instance = this
        initAudioPlaybackMonitoring()
    }

    /**
     * Monitorea activamente si otra aplicación del sistema (YouTube, YouTube Music, etc.)
     * está reproduciendo audio en el dispositivo para atenuar o detener la música.
     */
    private fun initAudioPlaybackMonitoring() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val callback = object : AudioManager.AudioPlaybackCallback() {
                override fun onPlaybackConfigChanged(configs: MutableList<AudioPlaybackConfiguration>?) {
                    handlePlaybackConfigurations(configs)
                }
            }
            playbackCallback = callback
            try {
                audioManager.registerAudioPlaybackCallback(callback, Handler(Looper.getMainLooper()))
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun handlePlaybackConfigurations(configs: List<AudioPlaybackConfiguration>?) {
        if (configs == null) return

        // Detectar si hay otras apps de medios (como YouTube o YouTube Music) activas
        val mediaConfigs = configs.filter { config ->
            config.audioAttributes.usage == AudioAttributes.USAGE_MEDIA ||
            config.audioAttributes.usage == AudioAttributes.USAGE_GAME
        }
        val otherMediaActive = if (_isPlaying.value) {
            mediaConfigs.size > 1
        } else {
            mediaConfigs.isNotEmpty() || audioManager.isMusicActive
        }

        _isOtherAppPlaying.value = otherMediaActive

        if (otherMediaActive && _isPlaying.value) {
            if (_pauseOnOtherMedia.value) {
                android.util.Log.i("AudioPlayerManager", "Detectado YouTube/otra app reproduciendo sonido. Pausando música.")
                wasPlayingBeforeFocusLoss = true
                pause()
            } else if (_isDuckingEnabled.value && !isDuckedByFocus) {
                android.util.Log.i("AudioPlayerManager", "Detectada otra app reproduciendo sonido. Atenuando volumen.")
                duckVolume()
            }
        } else if (!otherMediaActive && isDuckedByFocus) {
            restoreVolume()
            if (wasPlayingBeforeFocusLoss && !_isPlaying.value) {
                wasPlayingBeforeFocusLoss = false
                resume()
            }
        }
    }

    fun duckVolume() {
        isDuckedByFocus = true
        _isDucked.value = true
        try {
            mediaPlayer?.setVolume(0.2f, 0.2f)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun restoreVolume() {
        isDuckedByFocus = false
        _isDucked.value = false
        try {
            mediaPlayer?.setVolume(1.0f, 1.0f)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun requestAudioFocus(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val playbackAttributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                .build()
            val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                .setAudioAttributes(playbackAttributes)
                .setAcceptsDelayedFocusGain(true)
                .setWillPauseWhenDucked(false) // Lo manejamos explícitamente para controlar el volumen
                .setOnAudioFocusChangeListener(audioFocusChangeListener, Handler(Looper.getMainLooper()))
                .build()
            audioFocusRequest = request
            audioManager.requestAudioFocus(request) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        } else {
            @Suppress("DEPRECATION")
            audioManager.requestAudioFocus(
                audioFocusChangeListener,
                AudioManager.STREAM_MUSIC,
                AudioManager.AUDIOFOCUS_GAIN
            ) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        }
    }

    private fun abandonAudioFocus() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            audioFocusRequest?.let { audioManager.abandonAudioFocusRequest(it) }
        } else {
            @Suppress("DEPRECATION")
            audioManager.abandonAudioFocus(audioFocusChangeListener)
        }
    }

    fun playSong(song: Song, newQueue: List<Song> = emptyList()) {
        try {
            if (newQueue.isNotEmpty()) {
                _queue.value = newQueue
            } else if (!_queue.value.any { it.id == song.id }) {
                _queue.value = _queue.value + song
            }

            _currentSong.value = song
            hasCountedHalfPlay = false
            loadLyricsForSong(song)

            val file = File(song.filePath)
            if (file.exists() && file.length() > 1000L) {
                startPlayback(song)
            } else {
                // Si el archivo físico aún no está listo o falló anteriormente, se asegura de inmediato
                _playbackError.value = "Preparando audio…"
                scope.launch(Dispatchers.IO) {
                    val preparedSong = ensureSongAudioFile(song)
                    withContext(Dispatchers.Main) {
                        if (preparedSong != null) {
                            _currentSong.value = preparedSong
                            _playbackError.value = null
                            startPlayback(preparedSong)
                        } else {
                            _playbackError.value = "No se pudo preparar el audio para esta canción."
                            notifyForegroundService(false)
                        }
                    }
                }
            }
        } catch (e: Exception) {
            // Nunca dejar que un error de reproducción tumbe el proceso entero.
            android.util.Log.e("AudioPlayerManager", "Error al iniciar reproducción", e)
            _playbackError.value = "No se pudo reproducir: ${e.message ?: "error desconocido"}"
        }
    }

    /**
     * Asegura que exista un archivo de audio reproducible para la canción,
     * usando respaldo armónico si YouTube está bloqueado por el emulador.
     */
    private suspend fun ensureSongAudioFile(song: Song): Song? = withContext(Dispatchers.IO) {
        val target = File(song.filePath)
        if (target.exists() && target.length() > 1000L) {
            return@withContext song
        }

        try {
            // El ID del video debe ser válido (11 caracteres); si la canción no
            // viene de YouTube no hay nada que re-descargar.
            val cleanId = song.youtubeVideoId
            if (cleanId.isNullOrBlank() || !Regex("[a-zA-Z0-9_-]{11}").matches(cleanId)) {
                return@withContext null
            }
            target.parentFile?.mkdirs()
            val written = YouTubeAudioDownloader.downloadToFile(
                videoId = cleanId,
                context = context,
                target = target
            )
            val duration = YouTubeAudioDownloader.probeDurationMs(target.absolutePath)
            song.copy(
                fileSizeBytes = written,
                durationMs = if (duration > 0) duration else if (song.durationMs > 0) song.durationMs else 90000L,
                isDownloaded = true
            )
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private fun loadLyricsForSong(song: Song) {
        val lrcPath = song.lyricsPath
        if (lrcPath != null && File(lrcPath).exists()) {
            try {
                val content = File(lrcPath).readText(Charsets.UTF_8)
                val lines = LrcParser.parse(content)
                _currentLyrics.value = lines
                return
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        _currentLyrics.value = emptyList()
    }

    private fun startPlayback(song: Song) {
        mediaPlayer?.release()
        mediaPlayer = null
        _currentPositionMs.value = 0L
        _isPlaying.value = false

        val file = File(song.filePath)
        if (!file.exists() || file.length() == 0L) {
            _playbackError.value = "Preparando archivo de audio…"
            scope.launch(Dispatchers.IO) {
                val prepared = ensureSongAudioFile(song)
                withContext(Dispatchers.Main) {
                    if (prepared != null) {
                        _currentSong.value = prepared
                        startPlayback(prepared)
                    } else {
                        _playbackError.value = "El audio no está disponible."
                        notifyForegroundService(false)
                    }
                }
            }
            return
        }

        try {
            requestAudioFocus()

            val mp = MediaPlayer()
            mp.setDataSource(file.absolutePath)
            mp.prepare()

            val actualDuration = if (mp.duration > 0) mp.duration.toLong() else song.durationMs
            _durationMs.value = actualDuration

            mp.setOnCompletionListener {
                // Callback del hilo del sistema: cualquier excepción aquí mata
                // el proceso y se percibe como "la app se cierra sola".
                try {
                    handleSongCompletion()
                } catch (e: Exception) {
                    android.util.Log.e("AudioPlayerManager", "Error al pasar a la siguiente canción", e)
                }
            }
            mp.setOnErrorListener { _, what, extra ->
                _playbackError.value = "Error de reproducción ($what/$extra)"
                _isPlaying.value = false
                notifyForegroundService(false)
                true
            }

            // Aplicar volumen normal o atenuado según estado de ducking
            if (isDuckedByFocus) {
                mp.setVolume(0.2f, 0.2f)
            } else {
                mp.setVolume(1.0f, 1.0f)
            }

            mp.start()
            mediaPlayer = mp
            _isPlaying.value = true
            _playbackError.value = null
            startProgressTicker()
            notifyForegroundService(true)
        } catch (e: Exception) {
            e.printStackTrace()
            mediaPlayer?.release()
            mediaPlayer = null
            _playbackError.value = "No se pudo reproducir el audio: ${e.message ?: "archivo no compatible"}"
            notifyForegroundService(false)
        }
    }

    private var lastNotifTick = 0L

    fun notifyForegroundService(playing: Boolean) {
        val song = _currentSong.value ?: return
        try {
            val intent = Intent(context, MusicPlaybackService::class.java).apply {
                action = MusicPlaybackService.ACTION_START
                putExtra(MusicPlaybackService.EXTRA_TITLE, song.title)
                putExtra(MusicPlaybackService.EXTRA_ARTIST, song.artist)
                putExtra(MusicPlaybackService.EXTRA_IS_PLAYING, playing)
                putExtra(MusicPlaybackService.EXTRA_ART_URL, song.coverArtUrl)
                putExtra(MusicPlaybackService.EXTRA_IS_SHUFFLE, _isShuffleEnabled.value)
                putExtra(MusicPlaybackService.EXTRA_IS_FAVORITE, song.isFavorite)
                putExtra(MusicPlaybackService.EXTRA_POSITION_MS, _currentPositionMs.value)
                putExtra(MusicPlaybackService.EXTRA_DURATION_MS, _durationMs.value)
            }
            ContextCompat.startForegroundService(context, intent)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun togglePlayPause() {
        if (_isPlaying.value) {
            pause()
        } else {
            resume()
        }
    }

    fun pause() {
        try {
            mediaPlayer?.pause()
        } catch (e: Exception) {
            e.printStackTrace()
        }
        _isPlaying.value = false
        notifyForegroundService(false)
    }

    fun resume() {
        if (_currentSong.value == null && _queue.value.isNotEmpty()) {
            playSong(_queue.value.first())
            return
        }
        requestAudioFocus()
        try {
            if (isDuckedByFocus) {
                mediaPlayer?.setVolume(0.2f, 0.2f)
            } else {
                mediaPlayer?.setVolume(1.0f, 1.0f)
            }
            mediaPlayer?.start()
        } catch (e: Exception) {
            e.printStackTrace()
        }
        _isPlaying.value = true
        startProgressTicker()
        notifyForegroundService(true)
    }

    fun seekTo(positionMs: Long) {
        val clamped = positionMs.coerceIn(0L, _durationMs.value)
        try {
            mediaPlayer?.seekTo(clamped.toInt())
        } catch (e: Exception) {
            e.printStackTrace()
        }
        _currentPositionMs.value = clamped
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
            var active = 0
            for (i in lyrics.indices) {
                if (lyrics[i].timeMs <= timeMs) {
                    active = i
                } else {
                    break
                }
            }
            _activeLyricIndex.value = active
        }
    }

    fun skipToNext() {
        val q = _queue.value
        if (q.isEmpty()) return
        val current = _currentSong.value ?: run {
            playSong(q.first())
            return
        }
        val idx = q.indexOfFirst { it.id == current.id }
        if (_isShuffleEnabled.value && q.size > 1) {
            val remaining = q.filter { it.id != current.id }
            playSong(remaining.random())
        } else if (idx != -1 && idx < q.size - 1) {
            playSong(q[idx + 1])
        } else if (_repeatMode.value == RepeatMode.ALL) {
            playSong(q.first())
        } else {
            // Fin de la lista
            pause()
            seekTo(0L)
        }
    }

    fun skipToPrevious() {
        if (_currentPositionMs.value > 3000L) {
            seekTo(0L)
            return
        }
        val q = _queue.value
        if (q.isEmpty()) return
        val current = _currentSong.value ?: return
        val idx = q.indexOfFirst { it.id == current.id }
        if (idx > 0) {
            playSong(q[idx - 1])
        } else {
            playSong(q.last())
        }
    }

    fun toggleShuffle() {
        _isShuffleEnabled.value = !_isShuffleEnabled.value
        notifyForegroundService(_isPlaying.value)
    }

    fun setShuffle(enabled: Boolean) {
        _isShuffleEnabled.value = enabled
        notifyForegroundService(_isPlaying.value)
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

    fun playNext(songs: List<Song>) {
        if (songs.isEmpty()) return
        val currentQueue = _queue.value.toMutableList()
        val current = _currentSong.value
        val insertIndex = if (current != null) {
            val idx = currentQueue.indexOfFirst { it.id == current.id }
            if (idx != -1) idx + 1 else currentQueue.size
        } else {
            0
        }
        val cleanSongs = songs.filter { s -> currentQueue.none { it.id == s.id } }
        currentQueue.addAll(insertIndex, cleanSongs)
        _queue.value = currentQueue
        if (_currentSong.value == null && currentQueue.isNotEmpty()) {
            playSong(currentQueue.first())
        }
    }

    fun removeFromQueue(songId: String) {
        val currentQueue = _queue.value.toMutableList()
        val index = currentQueue.indexOfFirst { it.id == songId }
        if (index != -1) {
            val isCurrent = _currentSong.value?.id == songId
            currentQueue.removeAt(index)
            _queue.value = currentQueue
            if (isCurrent) {
                if (currentQueue.isNotEmpty()) {
                    val nextIndex = if (index < currentQueue.size) index else 0
                    playSong(currentQueue[nextIndex])
                } else {
                    stopPlayer()
                    _currentSong.value = null
                }
            }
        }
    }

    fun reorderQueue(fromIndex: Int, toIndex: Int) {
        val list = _queue.value.toMutableList()
        if (fromIndex in list.indices && toIndex in list.indices) {
            val item = list.removeAt(fromIndex)
            list.add(toIndex, item)
            _queue.value = list
        }
    }

    private fun handleSongCompletion() {
        if (_sleepTimerPauseAtEndOfSong.value) {
            cancelSleepTimer()
            pause()
            seekTo(0L)
            return
        }
        if (_repeatMode.value == RepeatMode.ONE) {
            _currentSong.value?.let { playSong(it) }
        } else {
            skipToNext()
        }
    }

    fun startShuffled(songs: List<Song>) {
        if (songs.isEmpty()) return
        val shuffled = songs.shuffled()
        _isShuffleEnabled.value = true
        playSong(shuffled.first(), shuffled)
    }

    fun playShuffled(songs: List<Song>) {
        startShuffled(songs)
    }

    fun playWithLoop() {
        setRepeatMode(RepeatMode.ALL)
        if (!_isPlaying.value) {
            resume()
        }
    }

    fun playWithShuffle() {
        setShuffle(true)
        if (!_isPlaying.value) {
            resume()
        }
    }

    private fun startProgressTicker() {
        progressJob?.cancel()
        progressJob = scope.launch {
            while (isActive && _isPlaying.value) {
                val pos = try {
                    mediaPlayer?.currentPosition?.toLong() ?: _currentPositionMs.value
                } catch (e: Exception) {
                    _currentPositionMs.value
                }

                _currentPositionMs.value = pos
                updateActiveLyric(pos)

                val dur = _durationMs.value
                if (!hasCountedHalfPlay && dur > 0 && pos >= (dur / 2)) {
                    hasCountedHalfPlay = true
                    _currentSong.value?.let { onHalfPlayedCallback?.invoke(it.id) }
                }

                val now = System.currentTimeMillis()
                if (now - lastNotifTick >= 2000L) {
                    lastNotifTick = now
                    notifyForegroundService(_isPlaying.value)
                }

                delay(500L)
            }
        }
    }

    fun stopPlayer() {
        pause()
        mediaPlayer?.release()
        mediaPlayer = null
        _currentPositionMs.value = 0L
        abandonAudioFocus()
    }

    fun release() {
        stopPlayer()
        progressJob?.cancel()
        sleepTimerJob?.cancel()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && playbackCallback != null) {
            try {
                audioManager.unregisterAudioPlaybackCallback(playbackCallback!!)
            } catch (_: Exception) {}
        }
        if (instance == this) {
            instance = null
        }
    }

    companion object {
        @Volatile
        private var instance: AudioPlayerManager? = null

        fun getInstance(): AudioPlayerManager? = instance
    }
}
