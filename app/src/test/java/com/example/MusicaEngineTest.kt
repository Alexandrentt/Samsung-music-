package com.example

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.text.Normalizer
import java.util.Locale
import java.util.regex.Pattern

class MusicaEngineTest {

    private fun extraerInfoUrl(url: String): Pair<String?, String?> {
        val playlistMatcher = Pattern.compile("[?&]list=([a-zA-Z0-9_-]+)").matcher(url)
        val playlistId = if (playlistMatcher.find()) playlistMatcher.group(1) else null

        val videoMatcher = Pattern.compile("(?:v=|youtu\\.be/|embed/|shorts/)([a-zA-Z0-9_-]{11})").matcher(url)
        val videoId = if (videoMatcher.find()) videoMatcher.group(1) else null

        return Pair(playlistId, videoId)
    }

    private fun limpiarTexto(raw: String): String {
        var t = raw.replace(Regex("\\([^)]*\\)"), "")
        t = t.replace(Regex("\\[[^\\]]*\\]"), "")
        val garbageRegex = Regex("(?i)(official\\s*(video|audio|music\\s*video|lyric[s]?|visualizer)|lyrics?|sub[s]?\\.?\\s*(español|english|espanol)?|video\\s*oficial|videoclip\\s*oficial|hd|4k|remastered?\\s*\\d*)")
        t = garbageRegex.replace(t, "")
        return t.replace(Regex("\\s+"), " ").trim(' ', '-', '–', '—', '|', ':')
    }

    private fun normalizar(raw: String): String {
        val normalized = Normalizer.normalize(raw, Normalizer.Form.NFD)
        val ascii = Regex("\\p{InCombiningDiacriticalMarks}+").replace(normalized, "")
        return ascii.lowercase(Locale.ROOT).trim()
    }

    private fun fuzzyRatio(s1: String, s2: String): Int {
        val norm1 = normalizar(s1)
        val norm2 = normalizar(s2)
        if (norm1 == norm2) return 100
        if (norm1.isEmpty() || norm2.isEmpty()) return 0
        val len1 = norm1.length
        val len2 = norm2.length
        val distance = Array(len1 + 1) { IntArray(len2 + 1) }
        for (i in 0..len1) distance[i][0] = i
        for (j in 0..len2) distance[0][j] = j
        for (i in 1..len1) {
            for (j in 1..len2) {
                val cost = if (norm1[i - 1] == norm2[j - 1]) 0 else 1
                distance[i][j] = minOf(
                    distance[i - 1][j] + 1,
                    distance[i][j - 1] + 1,
                    distance[i - 1][j - 1] + cost
                )
            }
        }
        val levDist = distance[len1][len2]
        val maxLen = maxOf(len1, len2)
        return (((maxLen - levDist).toDouble() / maxLen) * 100.0).toInt()
    }

    @Test
    fun testExtraerInfoUrl() {
        val playlistUrl = "https://youtube.com/playlist?list=PLCUqyibcwbIAI0E8rbFcKhKMuUP0dfcIj&si=CFlp1A7Y_8g4mKEG"
        val (playlistId, videoId) = extraerInfoUrl(playlistUrl)

        assertEquals("PLCUqyibcwbIAI0E8rbFcKhKMuUP0dfcIj", playlistId)
    }

    @Test
    fun testLimpiarTexto() {
        val raw = "Diego Verdaguer - Corazon de Papel (Video original 1981) [Official Audio]"
        val cleaned = limpiarTexto(raw)

        assertEquals("Diego Verdaguer - Corazon de Papel", cleaned)
    }

    @Test
    fun testFuzzyRatio() {
        val score = fuzzyRatio("Corazón de Papel", "Corazon de Papel")

        assertTrue("Fuzzy ratio should be >= 90, got $score", score >= 90)
    }

    @Test
    fun testUrlDetection() {
        val testUrl = "https://www.youtube.com/watch?v=yG7MPEQm1-w"
        val (playlistId, videoId) = extraerInfoUrl(testUrl)
        assertEquals("yG7MPEQm1-w", videoId)
        assertEquals(null, playlistId)
    }
}
