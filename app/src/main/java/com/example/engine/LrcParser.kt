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

object LrcParser {
    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    private val TIME_TAG_REGEX = Pattern.compile("\\[(\\d{2}):(\\d{2})(?:\\.(\\d{2,3}))?\\]")

    fun parse(lrcContent: String?): List<LyricLine> {
        if (lrcContent.isNullOrBlank()) return emptyList()
        val lines = mutableListOf<LyricLine>()
        for (raw in lrcContent.lines()) {
            val matcher = TIME_TAG_REGEX.matcher(raw)
            if (matcher.find()) {
                val minutes = matcher.group(1)?.toLongOrNull() ?: 0L
                val seconds = matcher.group(2)?.toLongOrNull() ?: 0L
                var millis = 0L
                val millisRaw = matcher.group(3)
                if (millisRaw != null) {
                    millis = if (millisRaw.length == 2) {
                        (millisRaw.toLongOrNull() ?: 0L) * 10
                    } else {
                        millisRaw.toLongOrNull() ?: 0L
                    }
                }
                val totalMs = ((minutes * 60 + seconds) * 1000) + millis
                val text = raw.substring(matcher.end()).trim()
                if (text.isNotBlank()) {
                    lines.add(LyricLine(totalMs, text))
                }
            }
        }
        return lines.sortedBy { it.timeMs }
    }

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

    fun saveLrc(file: File, content: String): Boolean {
        return try {
            file.writeText(content, Charsets.UTF_8)
            true
        } catch (e: Exception) {
            false
        }
    }

    suspend fun fetchLrcFromApi(
        title: String,
        artist: String,
        durationSeconds: Long? = null
    ): String? = withContext(Dispatchers.IO) {
        try {
            val encodedTitle = URLEncoder.encode(title, "UTF-8")
            val encodedArtist = URLEncoder.encode(artist, "UTF-8")
            val url = "https://lrclib.net/api/get?track_name=$encodedTitle&artist_name=$encodedArtist" +
                    if (durationSeconds != null) "&duration=$durationSeconds" else ""

            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "SamsungMusicManager/1.0 (Android-OneUI)")
                .build()

            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val body = response.body?.string()
                    if (!body.isNullOrBlank()) {
                        val json = JSONObject(body)
                        val syncedLyrics = json.optString("syncedLyrics", "")
                        if (syncedLyrics.isNotBlank()) {
                            return@withContext syncedLyrics
                        }
                        val plainLyrics = json.optString("plainLyrics", "")
                        if (plainLyrics.isNotBlank()) {
                            return@withContext convertPlainToLrc(plainLyrics, durationSeconds ?: 180L)
                        }
                    }
                }
            }

            // Fallback search
            val searchUrl = "https://lrclib.net/api/search?q=$encodedTitle+$encodedArtist"
            val searchRequest = Request.Builder()
                .url(searchUrl)
                .header("User-Agent", "SamsungMusicManager/1.0 (Android-OneUI)")
                .build()

            client.newCall(searchRequest).execute().use { response ->
                if (response.isSuccessful) {
                    val body = response.body?.string()
                    if (!body.isNullOrBlank()) {
                        val arr = JSONArray(body)
                        if (arr.length() > 0) {
                            val first = arr.getJSONObject(0)
                            val synced = first.optString("syncedLyrics", "")
                            if (synced.isNotBlank()) return@withContext synced
                            val plain = first.optString("plainLyrics", "")
                            if (plain.isNotBlank()) {
                                return@withContext convertPlainToLrc(plain, durationSeconds ?: 180L)
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            // ignore network errors
        }
        null
    }

    fun convertPlainToLrc(plain: String, durationSec: Long): String {
        val lines = plain.lines().filter { it.isNotBlank() }
        if (lines.isEmpty()) return ""
        val stepMs = (durationSec * 1000) / (lines.size + 1)
        val sb = StringBuilder()
        lines.forEachIndexed { idx, line ->
            val ms = (idx + 1) * stepMs
            val mm = (ms / 1000) / 60
            val ss = (ms / 1000) % 60
            val cent = (ms % 1000) / 10
            sb.append(String.format("[%02d:%02d.%02d] %s\n", mm, ss, cent, line))
        }
        return sb.toString()
    }
}
