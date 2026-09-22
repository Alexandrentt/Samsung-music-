package com.example.engine

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.URLEncoder
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern

data class LyricLine(
    val timeMs: Long,
    val text: String
)

object LrcParser {

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    // Matches [01:23.45] or [01:23.456]
    private val TIME_TAG_REGEX = Pattern.compile("\\[(\\d{2}):(\\d{2})(?:\\.(\\d{2,3}))?\\]")

    /**
     * Parsea un string en formato .lrc a una lista ordenada de LyricLine
     */
    fun parse(lrcContent: String?): List<LyricLine> {
        if (lrcContent.isNullOrBlank()) return emptyList()

        val lines = mutableListOf<LyricLine>()
        val rawLines = lrcContent.lines()

        for (raw in rawLines) {
            val matcher = TIME_TAG_REGEX.matcher(raw)
            if (matcher.find()) {
                val minutes = matcher.group(1)?.toLongOrNull() ?: 0L
                val seconds = matcher.group(2)?.toLongOrNull() ?: 0L
                val millisRaw = matcher.group(3)
                val millis = when {
                    millisRaw == null -> 0L
                    millisRaw.length == 2 -> (millisRaw.toLongOrNull() ?: 0L) * 10
                    else -> millisRaw.toLongOrNull() ?: 0L
                }

                val totalMs = (minutes * 60 + seconds) * 1000 + millis
                val text = raw.substring(matcher.end()).trim()
                if (text.isNotBlank()) {
                    lines.add(LyricLine(totalMs, text))
                }
            }
        }

        return lines.sortedBy { it.timeMs }
    }

    /**
     * Retorna el índice de la línea actual que corresponde al tiempo de reproducción
     */
    fun findActiveIndex(currentPositionMs: Long, lyrics: List<LyricLine>): Int {
        if (lyrics.isEmpty()) return -1
        if (currentPositionMs < lyrics.first().timeMs) return 0

        for (i in lyrics.indices.reversed()) {
            if (currentPositionMs >= lyrics[i].timeMs) {
                return i
            }
        }
        return 0
    }

    /**
     * Guarda el archivo .lrc en disco
     */
    fun saveLrc(file: File, content: String): Boolean {
        return try {
            file.writeText(content, Charsets.UTF_8)
            true
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Descarga la letra sincronizada en formato LRC desde la API pública de LRCLIB
     */
    suspend fun fetchLrcFromApi(
        title: String,
        artist: String,
        durationSeconds: Long? = null
    ): String? = withContext(Dispatchers.IO) {
        try {
            val encTitle = URLEncoder.encode(title.trim(), "UTF-8")
            val encArtist = URLEncoder.encode(artist.trim(), "UTF-8")
            var url = "https://lrclib.net/api/get?track_name=$encTitle&artist_name=$encArtist"
            if (durationSeconds != null && durationSeconds > 0) {
                url += "&duration=$durationSeconds"
            }

            val req = Request.Builder()
                .url(url)
                .header("User-Agent", "SamsungMusic-Android/1.0 (https://github.com)")
                .build()

            client.newCall(req).execute().use { response ->
                if (response.isSuccessful) {
                    val body = response.body?.string() ?: return@withContext null
                    val json = JSONObject(body)
                    val syncedLyrics = json.optString("syncedLyrics", null)
                    if (!syncedLyrics.isNullOrBlank()) {
                        return@withContext syncedLyrics
                    }
                    val plain = json.optString("plainLyrics", null)
                    if (!plain.isNullOrBlank()) {
                        return@withContext convertPlainToLrc(plain, durationSeconds ?: 180)
                    }
                }
            }

            // Fallback a búsqueda abierta si no hubo coincidencia exacta
            val searchUrl = "https://lrclib.net/api/search?q=${URLEncoder.encode("$artist $title", "UTF-8")}"
            val searchReq = Request.Builder()
                .url(searchUrl)
                .header("User-Agent", "SamsungMusic-Android/1.0")
                .build()

            client.newCall(searchReq).execute().use { response ->
                if (response.isSuccessful) {
                    val body = response.body?.string() ?: return@withContext null
                    val jsonArray = JSONArray(body)
                    for (i in 0 until jsonArray.length()) {
                        val item = jsonArray.getJSONObject(i)
                        val synced = item.optString("syncedLyrics", null)
                        if (!synced.isNullOrBlank()) {
                            return@withContext synced
                        }
                    }
                }
            }
        } catch (_: Exception) {
            // Ignorar errores de red
        }
        null
    }

    /**
     * Si no hay letra sincronizada, genera marcas de tiempo distribuidas para sincronización fluida
     */
    fun convertPlainToLrc(plain: String, durationSec: Long): String {
        val lines = plain.lines().filter { it.isNotBlank() }
        if (lines.isEmpty()) return ""
        val stepMs = (durationSec * 1000) / (lines.size + 1)
        val sb = StringBuilder()
        for ((idx, line) in lines.withIndex()) {
            val ms = (idx + 1) * stepMs
            val mm = (ms / 1000) / 60
            val ss = (ms / 1000) % 60
            val cent = (ms % 1000) / 10
            sb.append(String.format("[%02d:%02d.%02d] %s\n", mm, ss, cent, line))
        }
        return sb.toString()
    }
}
