package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.Song
import com.example.player.AudioPlayerManager
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PlayNextQueueTest {

    private lateinit var playerManager: AudioPlayerManager

    private val song1 = Song(id = "s1", title = "Track 1", artist = "Artist 1", album = "Album 1", durationMs = 180000, filePath = "/music/1.mp3")
    private val song2 = Song(id = "s2", title = "Track 2", artist = "Artist 2", album = "Album 2", durationMs = 200000, filePath = "/music/2.mp3")
    private val song3 = Song(id = "s3", title = "Track 3", artist = "Artist 3", album = "Album 3", durationMs = 210000, filePath = "/music/3.mp3")
    private val nextSong = Song(id = "s_next", title = "Track Next", artist = "Artist Next", album = "Album Next", durationMs = 190000, filePath = "/music/next.mp3")

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        playerManager = AudioPlayerManager(context)
    }

    @Test
    fun testPlayNextInsertsImmediatelyAfterCurrent() = runBlocking {
        val initialList = listOf(song1, song2, song3)
        playerManager.playSong(song1, initialList)

        assertEquals("s1", playerManager.currentSong.value?.id)
        assertEquals(listOf("s1", "s2", "s3"), playerManager.queue.value.map { it.id })

        // User triggers "Reproducir a continuación" on nextSong
        playerManager.playNext(nextSong)

        // Queue should now place nextSong immediately after song1
        val updatedQueueIds = playerManager.queue.value.map { it.id }
        assertEquals(listOf("s1", "s_next", "s2", "s3"), updatedQueueIds)
    }

    @Test
    fun testPlayNextListInsertsImmediatelyAfterCurrent() = runBlocking {
        val initialList = listOf(song1, song2, song3)
        playerManager.playSong(song1, initialList)

        val batch = listOf(
            Song(id = "b1", title = "Batch 1", artist = "Artist", album = "Album", durationMs = 120000, filePath = "/music/b1.mp3"),
            Song(id = "b2", title = "Batch 2", artist = "Artist", album = "Album", durationMs = 120000, filePath = "/music/b2.mp3")
        )

        playerManager.playNext(batch)

        val updatedQueueIds = playerManager.queue.value.map { it.id }
        assertEquals(listOf("s1", "b1", "b2", "s2", "s3"), updatedQueueIds)
    }

    @Test
    fun testRemoveFromQueue() = runBlocking {
        val initialList = listOf(song1, song2, song3)
        playerManager.playSong(song1, initialList)

        playerManager.removeFromQueue("s2")

        val updatedQueueIds = playerManager.queue.value.map { it.id }
        assertEquals(listOf("s1", "s3"), updatedQueueIds)
    }
}
