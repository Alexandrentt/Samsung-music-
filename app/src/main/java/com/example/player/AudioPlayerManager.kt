package com.example.player

import android.content.Context
import android.content.Intent
import android.media.MediaPlayer
import androidx.core.content.ContextCompat
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

class AudioPlayerManager(private val context: Context) {

    private val scope = CoroutineScope(Dispatchers.Main)
    private var mediaPlayer: MediaPlayer? = null
    private var progressJob: Job? = null

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

    init {
        instance = this
    }

    fun playSong(song: Song, newQueue: List<Song> = emptyList()) {
        if (newQueue.isNotEmpty()) {
            _queue.value = newQueue
        } else if (!_queue.value.any { it.id == song.id }) {
            _queue.value = _queue.value + song
        }

        _currentSong.value = song
        hasCountedHalfPlay = false
        loadLyricsForSong(song)
        startPlayback(song)
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
        try {
            mediaPlayer?.release()
            mediaPlayer = null

            val file = File(song.filePath)
            val mp = MediaPlayer()
            if (file.exists() && file.length() > 0) {
                mp.setDataSource(file.absolutePath)
                mp.prepare()
            }

            val actualDuration = if (mp.duration > 0) mp.duration.toLong() else song.durationSeconds * 1000L
            _durationMs.value = actualDuration
            _currentPositionMs.value = 0L

            mp.setOnCompletionListener {
                handleSongCompletion()
            }

            mp.start()
            mediaPlayer = mp
            _isPlaying.value = true
            startProgressTicker()
            notifyForegroundService(true)
        } catch (e: Exception) {
            e.printStackTrace()
            // Graceful fallback for synthetic demo playback
            _isPlaying.value = true
            _durationMs.value = song.durationSeconds * 1000L
            startProgressTicker()
            notifyForegroundService(true)
        }
    }

    private fun notifyForegroundService(playing: Boolean) {
        val song = _currentSong.value ?: return
        try {
            val intent = Intent(context, MusicPlaybackService::class.java).apply {
                action = MusicPlaybackService.ACTION_START
                putExtra(MusicPlaybackService.EXTRA_TITLE, song.title)
                putExtra(MusicPlaybackService.EXTRA_ARTIST, song.artist)
                putExtra(MusicPlaybackService.EXTRA_IS_PLAYING, playing)
                putExtra(MusicPlaybackService.EXTRA_ART_URL, song.coverArtUrl)
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
        try {
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
            _activeLyricIndex.value = LrcParser.findActiveIndex(timeMs, lyrics)
        }
    }

    fun skipToNext() {
        val q = _queue.value
        val cur = _currentSong.value
        if (q.isEmpty()) return

        val nextSong = if (_isShuffleEnabled.value) {
            val otherSongs = q.filter { it.id != cur?.id }
            if (otherSongs.isNotEmpty()) otherSongs.random() else q.first()
        } else {
            val idx = q.indexOfFirst { it.id == cur?.id }
            if (idx != -1 && idx + 1 < q.size) {
                q[idx + 1]
            } else if (_repeatMode.value == RepeatMode.ALL) {
                q.first()
            } else {
                null
            }
        }

        if (nextSong != null) {
            playSong(nextSong)
        } else {
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
        val cur = _currentSong.value
        if (q.isEmpty()) return

        val idx = q.indexOfFirst { it.id == cur?.id }
        if (idx > 0) {
            playSong(q[idx - 1])
        } else if (_repeatMode.value == RepeatMode.ALL) {
            playSong(q.last())
        } else {
            seekTo(0L)
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

    fun playWithLoop() {
        setRepeatMode(RepeatMode.ALL)
        if (!_isPlaying.value) resume()
    }

    fun playWithShuffle() {
        setShuffle(true)
        if (!_isPlaying.value) resume()
    }

    fun playNext(songs: List<Song>) {
        val currentQ = _queue.value.toMutableList()
        val cur = _currentSong.value
        val idx = currentQ.indexOfFirst { it.id == cur?.id }
        if (idx != -1) {
            currentQ.addAll(idx + 1, songs)
        } else {
            currentQ.addAll(songs)
        }
        _queue.value = currentQ
    }

    fun removeFromQueue(songId: String) {
        _queue.value = _queue.value.filter { it.id != songId }
    }

    private fun handleSongCompletion() {
        if (_repeatMode.value == RepeatMode.ONE) {
            _currentSong.value?.let { playSong(it) }
        } else {
            skipToNext()
        }
    }

    private fun startProgressTicker() {
        progressJob?.cancel()
        progressJob = scope.launch {
            while (isActive && _isPlaying.value) {
                val pos = try {
                    mediaPlayer?.currentPosition?.toLong() ?: (_currentPositionMs.value + 1000L)
                } catch (e: Exception) {
                    _currentPositionMs.value + 1000L
                }

                _currentPositionMs.value = pos
                updateActiveLyric(pos)

                val dur = _durationMs.value
                if (!hasCountedHalfPlay && dur > 0 && pos >= (dur / 2)) {
                    hasCountedHalfPlay = true
                    _currentSong.value?.let { onHalfPlayedCallback?.invoke(it.id) }
                }

                if (pos >= dur && dur > 0) {
                    handleSongCompletion()
                    break
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
    }

    fun release() {
        stopPlayer()
        progressJob?.cancel()
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
