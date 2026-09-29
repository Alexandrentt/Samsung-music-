package com.example

import com.example.data.Song
import com.example.data.SongMatching
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pruebas del parsing de nombres de archivo (causa raíz de los duplicados con
 * código al final) y de las heurísticas de fusión del dedupe.
 */
class SongMatchingTest {

    // ---------- parseMediaFileName ----------

    @Test
    fun testParseCanonicalFileName() {
        val (videoId, title) = com.example.engine.MusicaEngine.parseMediaFileName("Corazon_de_Papel_rY0WqhfEA2w")
        assertEquals("rY0WqhfEA2w", videoId)
        assertEquals("Corazon de Papel", title)
    }

    @Test
    fun testParseCanonicalFileNameWithSeparator() {
        val (videoId, title) = com.example.engine.MusicaEngine.parseMediaFileName("Sunsetz - Cigarettes After Sex_eF-3Os5vI2Q")
        assertEquals("eF-3Os5vI2Q", videoId)
        assertEquals("Sunsetz - Cigarettes After Sex", title)
    }

    @Test
    fun testParseJunkSuffixName() {
        // Archivos viejos con sufijo basura: NO debe generar un ID falso
        val (videoId, title) = com.example.engine.MusicaEngine.parseMediaFileName("Sunsetz 5-rbSNzU")
        assertNull(videoId)
        assertEquals("Sunsetz", title)
    }

    @Test
    fun testParseJunkSuffixLowercaseName() {
        val (videoId, title) = com.example.engine.MusicaEngine.parseMediaFileName("fanshop supernova mZyXw1")
        assertNull(videoId)
        assertEquals("fanshop supernova", title)
    }

    @Test
    fun testParseRealTitleWithYearIsNotStripped() {
        val (videoId, title) = com.example.engine.MusicaEngine.parseMediaFileName("Verano 2024")
        assertNull(videoId)
        assertEquals("Verano 2024", title) // "2024" es solo dígitos: palabra real
    }

    @Test
    fun testParseShortSongTitle() {
        val (videoId, title) = com.example.engine.MusicaEngine.parseMediaFileName("Song 2")
        assertNull(videoId)
        assertEquals("Song 2", title)
    }

    // ---------- norm / stripJunkSuffix / titleKey ----------

    @Test
    fun testNormAccentAndSpacing() {
        assertEquals("perdon", SongMatching.norm("Perdón"))
        assertEquals("perdn", SongMatching.norm("perd n")) // el espacio se elimina
        assertEquals("corazondepapel", SongMatching.norm("Corazón de Papel"))
    }

    @Test
    fun testStripJunkSuffixCases() {
        assertEquals("Sunsetz", SongMatching.stripJunkSuffix("Sunsetz 5-rbSNzU"))
        assertEquals("Milagro", SongMatching.stripJunkSuffix("Milagro K1V9"))
        assertEquals("fanshop supernova", SongMatching.stripJunkSuffix("fanshop supernova mZyXw1"))
        // Títulos reales NO se tocan
        assertEquals("Verano 2024", SongMatching.stripJunkSuffix("Verano 2024"))
        assertEquals("Song 2", SongMatching.stripJunkSuffix("Song 2"))
        assertEquals("Corazón de Papel", SongMatching.stripJunkSuffix("Corazón de Papel"))
    }

    @Test
    fun testTitleKeyGroupsJunkWithCanonical() {
        assertEquals(SongMatching.titleKey("Sunsetz"), SongMatching.titleKey("Sunsetz 5-rbSNzU"))
        assertEquals(SongMatching.titleKey("fanshop supernova"), SongMatching.titleKey("fanshop supernova mZyXw1"))
    }

    @Test
    fun testLooksLikeHash() {
        assertTrue(SongMatching.looksLikeHash("5-rbSNzU"))
        assertTrue(SongMatching.looksLikeHash("mZyXw1"))
        assertTrue(SongMatching.looksLikeHash("K1V9"))
        assertTrue(SongMatching.looksLikeHash("eF-3Os5vI2Q")) // ID de YouTube
        assertFalse(SongMatching.looksLikeHash("2024"))      // solo dígitos
        assertFalse(SongMatching.looksLikeHash("Papel"))     // solo letras
    }

    // ---------- sameAudio ----------

    private fun song(durMs: Long, size: Long) = Song(
        id = "s_$durMs", title = "t", artist = "a", album = "al",
        durationMs = durMs, fileSizeBytes = size
    )

    @Test
    fun testSameAudioIdentical() {
        assertTrue(SongMatching.sameAudio(song(200_000, 3_500_000), song(200_000, 3_500_000)))
    }

    @Test
    fun testSameAudioSmallDiff() {
        assertTrue(SongMatching.sameAudio(song(200_000, 3_500_000), song(200_800, 3_560_000)))
    }

    @Test
    fun testSameAudioDifferentDuration() {
        assertFalse(SongMatching.sameAudio(song(200_000, 3_500_000), song(240_000, 3_500_000)))
    }

    @Test
    fun testSameAudioDifferentSize() {
        assertFalse(SongMatching.sameAudio(song(200_000, 3_500_000), song(200_000, 2_100_000)))
    }

    @Test
    fun testSameAudioZeroSizeTrustsDuration() {
        assertTrue(SongMatching.sameAudio(song(200_000, 0), song(200_500, 3_500_000)))
    }
}
