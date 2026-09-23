package com.example.data

import kotlinx.coroutines.flow.Flow

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

    fun getPlaylistWithSongs(playlistId: Long): Flow<PlaylistWithSongs?> {
        return playlistDao.getPlaylistWithSongs(playlistId)
    }

    suspend fun createPlaylist(name: String, description: String = "", coverArtUrl: String? = null): Long {
        return playlistDao.insertPlaylist(Playlist(name = name, description = description, coverArtUrl = coverArtUrl))
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
        playlistDao.addSongToPlaylist(PlaylistSongCrossRef(playlistId = playlistId, songId = songId))
    }

    suspend fun addSongsToPlaylist(playlistId: Long, songIds: List<String>) {
        val crossRefs = songIds.mapIndexed { index, songId ->
            PlaylistSongCrossRef(playlistId = playlistId, songId = songId, orderIndex = index)
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

    suspend fun getSongById(songId: String): Song? {
        return songDao.getSongById(songId)
    }

    suspend fun insertSong(song: Song) {
        songDao.insertSong(song)
    }

    suspend fun insertSongs(songs: List<Song>) {
        songDao.insertSongs(songs)
    }

    suspend fun updateSong(song: Song) {
        songDao.updateSong(song)
    }

    suspend fun deleteSong(song: Song) {
        songDao.deleteSong(song)
    }

    suspend fun setFavorite(songId: String, isFavorite: Boolean) {
        songDao.setFavorite(songId, isFavorite)
    }

    suspend fun incrementPlayCount(songId: String) {
        songDao.incrementPlayCount(songId)
    }

    suspend fun updateMetadata(
        id: String,
        title: String,
        artist: String,
        album: String,
        coverArtUrl: String?,
        score: Int,
        releaseId: String?
    ) {
        songDao.updateMetadata(id, title, artist, album, coverArtUrl, score, releaseId)
    }

    suspend fun isInHistory(videoId: String): Boolean {
        return historyDao.getByVideoId(videoId) != null
    }

    suspend fun recordDownload(videoId: String, title: String, channel: String, filePath: String) {
        historyDao.insertHistory(
            DownloadHistoryItem(
                id = videoId,
                videoId = videoId,
                title = title,
                channel = channel,
                filePath = filePath
            )
        )
    }

    suspend fun clearHistory() {
        historyDao.clearHistory()
    }
}
