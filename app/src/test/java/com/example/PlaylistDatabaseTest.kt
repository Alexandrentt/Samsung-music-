package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.AppDatabase
import com.example.data.Playlist
import com.example.data.PlaylistDao
import com.example.data.PlaylistSongCrossRef
import com.example.data.Song
import com.example.data.SongDao
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PlaylistDatabaseTest {

    private lateinit var database: AppDatabase
    private lateinit var playlistDao: PlaylistDao
    private lateinit var songDao: SongDao

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        playlistDao = database.playlistDao()
        songDao = database.songDao()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun testPlaylistCrudAndTrackAssociation() = runBlocking {
        // 1. Insert sample songs (downloaded tracks)
        val song1 = Song(
            id = "yt_1",
            title = "Bohemian Rhapsody",
            artist = "Queen",
            album = "A Night at the Opera",
            durationMs = 354000,
            filePath = "/storage/emulated/0/Music/song1.mp3"
        )
        val song2 = Song(
            id = "yt_2",
            title = "Hotel California",
            artist = "Eagles",
            album = "Hotel California",
            durationMs = 391000,
            filePath = "/storage/emulated/0/Music/song2.mp3"
        )
        songDao.insertSongs(listOf(song1, song2))

        // 2. Create Playlist (CREATE)
        val playlist = Playlist(
            name = "Rock Clásico",
            description = "Las mejores canciones de rock"
        )
        val playlistId = playlistDao.insertPlaylist(playlist)
        assertTrue(playlistId > 0)

        // 3. Associate tracks with playlist
        playlistDao.addSongToPlaylist(
            PlaylistSongCrossRef(playlistId = playlistId, songId = song1.id, orderIndex = 0)
        )
        playlistDao.addSongToPlaylist(
            PlaylistSongCrossRef(playlistId = playlistId, songId = song2.id, orderIndex = 1)
        )

        // 4. Query Playlist with Songs (READ)
        val playlistWithSongs = playlistDao.getPlaylistWithSongs(playlistId).first()
        assertNotNull(playlistWithSongs)
        assertEquals("Rock Clásico", playlistWithSongs!!.playlist.name)
        assertEquals(2, playlistWithSongs.songs.size)
        assertEquals("Bohemian Rhapsody", playlistWithSongs.songs[0].title)
        assertEquals("Hotel California", playlistWithSongs.songs[1].title)

        // 5. Update Playlist name & description (UPDATE)
        playlistDao.renamePlaylist(playlistId, "Rock Legendario")
        playlistDao.updatePlaylistDescription(playlistId, "Edición especial de rock")
        val updatedPlaylist = playlistDao.getPlaylistById(playlistId)
        assertEquals("Rock Legendario", updatedPlaylist?.name)
        assertEquals("Edición especial de rock", updatedPlaylist?.description)

        // 6. Remove a single song from the playlist
        playlistDao.removeSongFromPlaylist(playlistId, song1.id)
        val afterRemove = playlistDao.getPlaylistWithSongs(playlistId).first()
        assertEquals(1, afterRemove!!.songs.size)
        assertEquals("Hotel California", afterRemove.songs[0].title)

        // Verify song itself is still preserved in the library
        val preservedSong = songDao.getSongById(song1.id)
        assertNotNull(preservedSong)

        // 7. Delete the Playlist (DELETE)
        playlistDao.deletePlaylistById(playlistId)
        val deletedPlaylist = playlistDao.getPlaylistById(playlistId)
        assertNull(deletedPlaylist)

        // Verify cross-ref table cascade cleaned up and songs remain intact
        val remainingCrossRefs = playlistDao.getSongIdsForPlaylist(playlistId).first()
        assertTrue(remainingCrossRefs.isEmpty())
        val allSongs = songDao.getAllSongs().first()
        assertEquals(2, allSongs.size)
    }
}
