package com.example.data

import kotlinx.coroutines.flow.Flow
import java.io.File

data class MusicStatistics(
    val totalSongs: Int,
    val totalFavorites: Int,
    val totalDownloadedYt: Int,
    val totalDurationSeconds: Long,
    val totalArtists: Int,
    val totalAlbums: Int,
    val totalSizeBytes: Long,
    val enrichedCount: Int
)

class MusicRepository(
    private val songDao: SongDao,
    private val historyDao: DownloadHistoryDao,
    private val playlistDao: PlaylistDao
) {
    val allSongs: Flow<List<Song>> = songDao.getAllSongs()
    val favoriteSongs: Flow<List<Song>> = songDao.getFavoriteSongs()
    val downloadHistory: Flow<List<DownloadHistoryItem>> = historyDao.getAllHistory()
    val allPlaylists: Flow<List<Playlist>> = playlistDao.getAllPlaylists()
    val allPlaylistsWithSongs: Flow<List<PlaylistWithSongs>> = playlistDao.getAllPlaylistsWithSongs()

    fun getPlaylistWithSongs(playlistId: Long): Flow<PlaylistWithSongs?> =
        playlistDao.getPlaylistWithSongs(playlistId)

    suspend fun createPlaylist(name: String, description: String = "", coverArtUrl: String? = null): Long {
        val playlist = Playlist(
            name = name,
            description = description,
            coverArtUrl = coverArtUrl
        )
        return playlistDao.insertPlaylist(playlist)
    }

    suspend fun renamePlaylist(playlistId: Long, newName: String) {
        playlistDao.renamePlaylist(playlistId, newName)
    }

    suspend fun updatePlaylistDescription(playlistId: Long, description: String) {
        playlistDao.updatePlaylistDescription(playlistId, description)
    }

    suspend fun deletePlaylist(playlistId: Long) {
        playlistDao.deletePlaylistById(playlistId)
    }

    suspend fun addSongToPlaylist(playlistId: Long, songId: String) {
        val crossRef = PlaylistSongCrossRef(
            playlistId = playlistId,
            songId = songId
        )
        playlistDao.addSongToPlaylist(crossRef)
    }

    suspend fun addSongsToPlaylist(playlistId: Long, songIds: List<String>) {
        val crossRefs = songIds.mapIndexed { idx, sId ->
            PlaylistSongCrossRef(
                playlistId = playlistId,
                songId = sId,
                orderIndex = idx
            )
        }
        playlistDao.addSongsToPlaylist(crossRefs)
    }

    suspend fun removeSongFromPlaylist(playlistId: Long, songId: String) {
        playlistDao.removeSongFromPlaylist(playlistId, songId)
    }

    suspend fun isSongInPlaylist(playlistId: Long, songId: String): Boolean {
        return playlistDao.isSongInPlaylist(playlistId, songId) > 0
    }

    suspend fun clearPlaylist(playlistId: Long) {
        playlistDao.clearPlaylistSongs(playlistId)
    }

    suspend fun getPlaylistCount(): Int = playlistDao.getPlaylistCount()

    suspend fun insertSong(song: Song) = songDao.insertSong(song)
    suspend fun insertSongs(songs: List<Song>) = songDao.insertSongs(songs)
    suspend fun updateSong(song: Song) = songDao.updateSong(song)
    suspend fun deleteSong(song: Song) {
        songDao.deleteSong(song)
        if (song.filePath.isNotBlank()) {
            val file = File(song.filePath)
            if (file.exists()) {
                file.delete()
            }
        }
    }
    suspend fun setFavorite(songId: String, isFavorite: Boolean) = songDao.setFavorite(songId, isFavorite)

    suspend fun updateMetadata(
        id: String,
        title: String,
        artist: String,
        album: String,
        coverArtUrl: String?,
        score: Int,
        releaseId: String?
    ) = songDao.updateMetadata(id, title, artist, album, coverArtUrl, score, releaseId)

    suspend fun isInHistory(videoId: String): Boolean {
        return historyDao.getByVideoId(videoId) != null
    }

    suspend fun recordDownload(videoId: String, title: String, channel: String, filePath: String) {
        val item = DownloadHistoryItem(
            id = "youtube $videoId",
            videoId = videoId,
            title = title,
            channel = channel,
            filePath = filePath,
            downloadedAt = System.currentTimeMillis()
        )
        historyDao.insertHistory(item)
    }

    suspend fun clearHistory() = historyDao.clearHistory()
}
