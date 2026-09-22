package com.example.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface PlaylistDao {

    // --- CREATE ---
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlaylist(playlist: Playlist): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlaylists(playlists: List<Playlist>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun addSongToPlaylist(crossRef: PlaylistSongCrossRef)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun addSongsToPlaylist(crossRefs: List<PlaylistSongCrossRef>)

    // --- READ ---
    @Query("SELECT * FROM playlists ORDER BY updatedAt DESC")
    fun getAllPlaylists(): Flow<List<Playlist>>

    @Transaction
    @Query("SELECT * FROM playlists ORDER BY updatedAt DESC")
    fun getAllPlaylistsWithSongs(): Flow<List<PlaylistWithSongs>>

    @Transaction
    @Query("SELECT * FROM playlists WHERE id = :playlistId LIMIT 1")
    fun getPlaylistWithSongs(playlistId: Long): Flow<PlaylistWithSongs?>

    @Query("SELECT * FROM playlists WHERE id = :playlistId LIMIT 1")
    suspend fun getPlaylistById(playlistId: Long): Playlist?

    @Query("SELECT songId FROM playlist_song_cross_ref WHERE playlistId = :playlistId ORDER BY orderIndex ASC, addedAt ASC")
    fun getSongIdsForPlaylist(playlistId: Long): Flow<List<String>>

    @Query("SELECT COUNT(*) FROM playlist_song_cross_ref WHERE playlistId = :playlistId AND songId = :songId")
    suspend fun isSongInPlaylist(playlistId: Long, songId: String): Int

    @Query("SELECT COUNT(*) FROM playlists")
    suspend fun getPlaylistCount(): Int

    // --- UPDATE ---
    @Update
    suspend fun updatePlaylist(playlist: Playlist)

    @Query("UPDATE playlists SET name = :newName, updatedAt = :updatedAt WHERE id = :playlistId")
    suspend fun renamePlaylist(
        playlistId: Long,
        newName: String,
        updatedAt: Long = System.currentTimeMillis()
    )

    @Query("UPDATE playlists SET description = :newDescription, updatedAt = :updatedAt WHERE id = :playlistId")
    suspend fun updatePlaylistDescription(
        playlistId: Long,
        newDescription: String,
        updatedAt: Long = System.currentTimeMillis()
    )

    @Query("UPDATE playlists SET coverArtUrl = :coverArtUrl, updatedAt = :updatedAt WHERE id = :playlistId")
    suspend fun updatePlaylistCover(
        playlistId: Long,
        coverArtUrl: String?,
        updatedAt: Long = System.currentTimeMillis()
    )

    @Query("UPDATE playlist_song_cross_ref SET orderIndex = :newOrder WHERE playlistId = :playlistId AND songId = :songId")
    suspend fun updateSongOrder(playlistId: Long, songId: String, newOrder: Int)

    // --- DELETE ---
    @Delete
    suspend fun deletePlaylist(playlist: Playlist)

    @Query("DELETE FROM playlists WHERE id = :playlistId")
    suspend fun deletePlaylistById(playlistId: Long)

    @Query("DELETE FROM playlist_song_cross_ref WHERE playlistId = :playlistId AND songId = :songId")
    suspend fun removeSongFromPlaylist(playlistId: Long, songId: String)

    @Query("DELETE FROM playlist_song_cross_ref WHERE playlistId = :playlistId")
    suspend fun clearPlaylistSongs(playlistId: Long)
}
