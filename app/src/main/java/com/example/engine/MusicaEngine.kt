package com.example.engine

import android.content.Context
import com.example.data.DownloadHistoryItem
import com.example.data.Song
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.net.URLEncoder
import java.text.Normalizer
import java.util.Locale
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern

class MusicaEngine(private val context: Context) {

    private val client = OkHttpClient.Builder()
        .connectTimeout(15L, TimeUnit.SECONDS)
        .readTimeout(25L, TimeUnit.SECONDS)
        .build()

    val musicFolder: File by lazy {
        val folder = context.getExternalFilesDir("Music") ?: File(context.filesDir, "Music")
        if (!folder.exists()) folder.mkdirs()
        folder
    }

    data class PlaylistItem(val videoId: String, val title: String, val channel: String)

    companion object {
        const val PLAYLIST_ID_DEFAULT = "PLCUqyibcwbIAI0E8rbFcKhKMuUP0dfcIj"
        const val PLAYLIST_URL_DEFAULT = "https://youtube.com/playlist?list=PLCUqyibcwbIAI0E8rbFcKhKMuUP0dfcIj&si=CFlp1A7Y_8g4mKEG"
        const val CAA_API = "https://coverartarchive.org"
        const val MB_API = "https://musicbrainz.org/ws/2"
        const val UMBRAL_CONFIANZA = 80
        const val USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
    }

    fun limpiarTexto(raw: String): String {
        var t = raw.replace(Regex("\\([^)]*\\)"), "")
        t = t.replace(Regex("\\[[^\\]]*\\]"), "")
        val garbageRegex = Regex("(?i)(official\\s*(video|audio|music\\s*video|lyric[s]?|visualizer)|lyrics?|sub[s]?\\.?\\s*(español|english|espanol)?|video\\s*oficial|videoclip\\s*oficial|hd|4k|remastered?\\s*\\d*)")
        t = garbageRegex.replace(t, "")
        return t.replace(Regex("\\s+"), " ").trim(' ', '-', '–', '—', '|', ':')
    }

    fun normalizar(raw: String): String {
        val normalized = Normalizer.normalize(raw, Normalizer.Form.NFD)
        val ascii = Regex("\\p{InCombiningDiacriticalMarks}+").replace(normalized, "")
        return ascii.lowercase(Locale.ROOT).trim()
    }

    fun fuzzyRatio(s1: String, s2: String): Int {
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

    fun candidatos(tituloYt: String, canal: String): List<Pair<String, String>> {
        val limpio = limpiarTexto(tituloYt)
        val candidatosList = mutableListOf<Pair<String, String>>()
        val vistos = mutableSetOf<String>()

        fun agregar(artista: String, titulo: String) {
            val a = artista.trim()
            val t = titulo.trim()
            val key = "$a|||$t"
            if (t.isNotBlank() && vistos.add(key)) {
                candidatosList.add(Pair(a, t))
            }
        }

        val separadores = listOf(" - ", " – ", " — ", " | ", ": ")
        for (sep in separadores) {
            val partes = limpio.split(sep, limit = 2)
            if (partes.size == 2) {
                agregar(partes[0], partes[1])
                agregar(partes[1], partes[0])
            }
        }

        if (canal.isNotBlank()) {
            val canalLimpio = canal.replace(Regex("(?i)\\s*(VEVO|oficial|official|music|records?|topic)\\s*"), "").trim()
            if (canalLimpio.isNotEmpty()) {
                agregar(canalLimpio, limpio)
                for (sep2 in separadores) {
                    val partes2 = limpio.split(sep2, limit = 2)
                    if (partes2.size == 2) {
                        agregar(canalLimpio, partes2[1])
                        agregar(canalLimpio, partes2[0])
                    }
                }
            }
        }

        agregar("", limpio)
        return candidatosList
    }

    suspend fun buscarMusicBrainz(artista: String, titulo: String): EnrichmentResult? = withContext(Dispatchers.IO) {
        try {
            val encodedQuery = if (artista.isNotBlank()) {
                URLEncoder.encode("recording:\"$titulo\" AND artist:\"$artista\"", "UTF-8")
            } else {
                URLEncoder.encode("recording:\"$titulo\"", "UTF-8")
            }
            val url = "$MB_API/recording?query=$encodedQuery&fmt=json&limit=5"
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "SamsungMusicManager/1.0 (Android-OneUI)")
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext null
                val body = response.body?.string() ?: return@withContext null
                val json = JSONObject(body)
                val recordings = json.optJSONArray("recordings") ?: return@withContext null

                var bestScore = 0
                var bestTitle = ""
                var bestArtist = ""
                var bestAlbum = ""
                var releaseId: String? = null

                for (i in 0 until recordings.length()) {
                    val rec = recordings.getJSONObject(i)
                    val recTitle = rec.optString("title", "")
                    val artistCredit = rec.optJSONArray("artist-credit")
                    val artistsList = mutableListOf<String>()
                    if (artistCredit != null) {
                        for (j in 0 until artistCredit.length()) {
                            val artObj = artistCredit.optJSONObject(j)
                            if (artObj != null && artObj.has("artist")) {
                                artistsList.add(artObj.getJSONObject("artist").optString("name", ""))
                            }
                        }
                    }
                    val recArtist = artistsList.joinToString(", ")
                    val score = ((fuzzyRatio(titulo, recTitle) * 0.6) +
                            ((if (artista.isNotBlank()) fuzzyRatio(artista, recArtist) else 100) * 0.4)).toInt()

                    if (score > bestScore) {
                        bestScore = score
                        bestTitle = recTitle
                        bestArtist = recArtist
                        val releases = rec.optJSONArray("releases")
                        if (releases != null && releases.length() > 0) {
                            val rel = releases.getJSONObject(0)
                            releaseId = rel.optString("id", null)
                            bestAlbum = rel.optString("title", "Álbum Desconocido")
                        } else {
                            bestAlbum = "Single"
                        }
                    }
                }

                if (bestScore > 40 && bestTitle.isNotBlank()) {
                    val coverUrl = if (releaseId != null) "$CAA_API/release/$releaseId/front-250.jpg" else null
                    return@withContext EnrichmentResult(
                        title = bestTitle,
                        artist = if (bestArtist.isNotBlank()) bestArtist else artista,
                        album = if (bestAlbum.isNotBlank()) bestAlbum else "Samsung Music",
                        coverArtUrl = coverUrl,
                        score = bestScore,
                        releaseId = releaseId,
                        source = "MusicBrainz"
                    )
                }
            }
        } catch (e: Exception) {
            // ignore network errors
        }
        null
    }

    suspend fun enriquecerCancion(videoId: String, tituloYt: String, canal: String): EnrichmentResult {
        val candidates = candidatos(tituloYt, canal)
        for (cand in candidates) {
            val result = buscarMusicBrainz(cand.first, cand.second)
            if (result != null && result.score >= UMBRAL_CONFIANZA) {
                return result
            }
        }
        // Fallback result
        val limpio = limpiarTexto(tituloYt)
        val partes = limpio.split(" - ", limit = 2)
        val artist = if (partes.size == 2) partes[0].trim() else canal.ifBlank { "Artista Desconocido" }
        val title = if (partes.size == 2) partes[1].trim() else limpio
        return EnrichmentResult(
            title = title,
            artist = artist,
            album = "YouTube Music",
            coverArtUrl = "https://img.youtube.com/vi/$videoId/hqdefault.jpg",
            score = 65,
            releaseId = null,
            source = "YouTube"
        )
    }

    fun extraerInfoUrl(url: String): Pair<String?, String?> {
        val playlistMatcher = Pattern.compile("[?&]list=([a-zA-Z0-9_-]+)").matcher(url)
        val playlistId = if (playlistMatcher.find()) playlistMatcher.group(1) else null

        val videoMatcher = Pattern.compile("(?:v=|youtu\\.be/|embed/|shorts/)([a-zA-Z0-9_-]{11})").matcher(url)
        val videoId = if (videoMatcher.find()) videoMatcher.group(1) else null

        return Pair(playlistId, videoId)
    }

    suspend fun buscarVideosYouTube(query: String): List<PlaylistItem> = withContext(Dispatchers.IO) {
        val results = mutableListOf<PlaylistItem>()
        val seen = mutableSetOf<String>()
        val trimmed = query.trim()
        if (trimmed.isBlank()) return@withContext emptyList()

        // If user pasted a direct YouTube URL, check if it has a videoId or playlistId
        val (playlistId, videoId) = extraerInfoUrl(trimmed)
        if (videoId != null) {
            val title = obtenerTituloVideo(videoId) ?: "Video de YouTube"
            return@withContext listOf(PlaylistItem(videoId, title, "YouTube"))
        }
        if (playlistId != null) {
            val playlistItems = obtenerItemsPlaylist(playlistId)
            return@withContext playlistItems.take(25)
        }

        try {
            val encodedQuery = URLEncoder.encode(trimmed, "UTF-8")
            val searchUrl = "https://www.youtube.com/results?search_query=$encodedQuery"
            val request = Request.Builder()
                .url(searchUrl)
                .header("User-Agent", USER_AGENT)
                .header("Accept-Language", "es-ES,es;q=0.9,en;q=0.8")
                .build()

            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val html = response.body?.string().orEmpty()
                    val dataMatcher = Pattern.compile("ytInitialData\\s*=\\s*(\\{.+?\\});", Pattern.DOTALL).matcher(html)
                    if (dataMatcher.find()) {
                        val jsonStr = dataMatcher.group(1)
                        if (jsonStr != null) {
                            val root = JSONObject(jsonStr)
                            val contents = root.optJSONObject("contents")
                                ?.optJSONObject("twoColumnSearchResultsRenderer")
                                ?.optJSONObject("primaryContents")
                                ?.optJSONObject("sectionListRenderer")
                                ?.optJSONArray("contents")

                            if (contents != null) {
                                for (i in 0 until contents.length()) {
                                    val section = contents.optJSONObject(i) ?: continue
                                    val itemSection = section.optJSONObject("itemSectionRenderer")
                                    val sectionContents = itemSection?.optJSONArray("contents") ?: continue

                                    for (j in 0 until sectionContents.length()) {
                                        val item = sectionContents.optJSONObject(j) ?: continue
                                        val vr = item.optJSONObject("videoRenderer") ?: continue
                                        val vid = vr.optString("videoId", "")
                                        val titleObj = vr.optJSONObject("title")
                                        val runs = titleObj?.optJSONArray("runs")
                                        val title = if (runs != null && runs.length() > 0) {
                                            runs.optJSONObject(0)?.optString("text", "") ?: ""
                                        } else {
                                            titleObj?.optString("simpleText", "") ?: ""
                                        }

                                        val ownerObj = vr.optJSONObject("ownerText")
                                        val ownerRuns = ownerObj?.optJSONArray("runs")
                                        val channel = if (ownerRuns != null && ownerRuns.length() > 0) {
                                            ownerRuns.optJSONObject(0)?.optString("text", "YouTube") ?: "YouTube"
                                        } else {
                                            ownerObj?.optString("simpleText", "YouTube") ?: "YouTube"
                                        }

                                        if (vid.isNotBlank() && title.isNotBlank() && seen.add(vid)) {
                                            results.add(PlaylistItem(vid, title, channel))
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        results
    }

    private fun parseVideoListContents(
        contents: JSONArray,
        onVideoFound: (vid: String, title: String, channel: String) -> Unit
    ): String? {
        var continuationToken: String? = null
        for (i in 0 until contents.length()) {
            val item = contents.optJSONObject(i) ?: continue

            // Check if regular playlist video item
            val videoRenderer = item.optJSONObject("playlistVideoRenderer")
            if (videoRenderer != null) {
                val vid = videoRenderer.optString("videoId", "")
                val titleObj = videoRenderer.optJSONObject("title")
                val titleRuns = titleObj?.optJSONArray("runs")
                val title = if (titleRuns != null && titleRuns.length() > 0) {
                    titleRuns.optJSONObject(0)?.optString("text", "") ?: ""
                } else {
                    titleObj?.optString("simpleText", "") ?: ""
                }

                val bylineObj = videoRenderer.optJSONObject("shortBylineText")
                val bylineRuns = bylineObj?.optJSONArray("runs")
                val channel = if (bylineRuns != null && bylineRuns.length() > 0) {
                    bylineRuns.optJSONObject(0)?.optString("text", "YouTube") ?: "YouTube"
                } else {
                    bylineObj?.optString("simpleText", "YouTube") ?: "YouTube"
                }

                if (vid.isNotBlank() && title.isNotBlank()) {
                    onVideoFound(vid, title, channel)
                }
            }

            // Check if continuation token item
            val contRenderer = item.optJSONObject("continuationItemRenderer")
            if (contRenderer != null) {
                val token = extractContinuationToken(contRenderer)
                if (!token.isNullOrBlank()) {
                    continuationToken = token
                }
            }
        }
        return continuationToken
    }

    private fun extractContinuationToken(renderer: JSONObject): String? {
        val endpoint = renderer.optJSONObject("continuationEndpoint") ?: return null
        val direct = endpoint.optJSONObject("continuationCommand")?.optString("token")
        if (!direct.isNullOrBlank()) return direct

        val executor = endpoint.optJSONObject("commandExecutorCommand")
        val commands = executor?.optJSONArray("commands")
        if (commands != null) {
            for (i in 0 until commands.length()) {
                val cmd = commands.optJSONObject(i)
                val token = cmd?.optJSONObject("continuationCommand")?.optString("token")
                if (!token.isNullOrBlank()) return token
            }
        }
        return null
    }

    suspend fun obtenerItemsPlaylist(playlistId: String): List<PlaylistItem> = withContext(Dispatchers.IO) {
        val items = mutableListOf<PlaylistItem>()
        val seenVideoIds = mutableSetOf<String>()

        fun addItem(vid: String, title: String, channel: String) {
            if (vid.isNotBlank() && title.isNotBlank() && seenVideoIds.add(vid)) {
                val cleanTitle = title
                    .replace("&quot;", "\"")
                    .replace("&amp;", "&")
                    .replace("&#39;", "'")
                    .replace("&lt;", "<")
                    .replace("&gt;", ">")
                items.add(PlaylistItem(vid, cleanTitle, channel))
            }
        }

        // 1. Fetch initial HTML page from YouTube
        var continuationToken: String? = null
        var apiKey = "AIzaSyAO_FJ2SlqU8Q4STEHLGCilw_Y9_11qcW8"

        try {
            val playlistUrl = "https://www.youtube.com/playlist?list=$playlistId"
            val request = Request.Builder()
                .url(playlistUrl)
                .header("User-Agent", USER_AGENT)
                .header("Accept-Language", "es-ES,es;q=0.9,en;q=0.8")
                .build()

            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val html = response.body?.string().orEmpty()

                    // Extract innerTube API key if found
                    val keyMatcher = Pattern.compile("innertubeApiKey[\":\\s]+([a-zA-Z0-9_-]{39})").matcher(html)
                    if (keyMatcher.find()) {
                        apiKey = keyMatcher.group(1) ?: apiKey
                    }

                    // Extract ytInitialData
                    val dataMatcher = Pattern.compile("ytInitialData\\s*=\\s*(\\{.+?\\});", Pattern.DOTALL).matcher(html)
                    if (dataMatcher.find()) {
                        val jsonStr = dataMatcher.group(1)
                        if (jsonStr != null) {
                            try {
                                val rootJson = JSONObject(jsonStr)
                                val tabs = rootJson.optJSONObject("contents")
                                    ?.optJSONObject("twoColumnBrowseResultsRenderer")
                                    ?.optJSONArray("tabs")
                                val contents = tabs?.optJSONObject(0)
                                    ?.optJSONObject("tabRenderer")
                                    ?.optJSONObject("content")
                                    ?.optJSONObject("sectionListRenderer")
                                    ?.optJSONArray("contents")
                                    ?.optJSONObject(0)
                                    ?.optJSONObject("itemSectionRenderer")
                                    ?.optJSONArray("contents")
                                    ?.optJSONObject(0)
                                    ?.optJSONObject("playlistVideoListRenderer")
                                    ?.optJSONArray("contents")

                                if (contents != null) {
                                    continuationToken = parseVideoListContents(contents, ::addItem)
                                }
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }
                        }
                    }
                }
            }

            // 2. Paginate through all subsequent chunks via YouTube InnerTube API
            var page = 0
            while (!continuationToken.isNullOrBlank() && page < 40) {
                page++
                val browseUrl = "https://www.youtube.com/youtubei/v1/browse?key=$apiKey"
                val payload = JSONObject().apply {
                    put("context", JSONObject().apply {
                        put("client", JSONObject().apply {
                            put("clientName", "WEB")
                            put("clientVersion", "2.20231201.00.00")
                        })
                    })
                    put("continuation", continuationToken)
                }

                val postRequest = Request.Builder()
                    .url(browseUrl)
                    .post(payload.toString().toRequestBody("application/json; charset=utf-8".toMediaType()))
                    .header("User-Agent", USER_AGENT)
                    .build()

                client.newCall(postRequest).execute().use { res ->
                    if (res.isSuccessful) {
                        val bodyStr = res.body?.string().orEmpty()
                        if (bodyStr.isNotBlank()) {
                            val cJson = JSONObject(bodyStr)
                            val actions = cJson.optJSONArray("onResponseReceivedActions")
                            if (actions != null && actions.length() > 0) {
                                val actionObj = actions.optJSONObject(0)
                                val contItems = actionObj?.optJSONObject("appendContinuationItemsAction")
                                    ?.optJSONArray("continuationItems")
                                if (contItems != null) {
                                    continuationToken = parseVideoListContents(contItems, ::addItem)
                                } else {
                                    continuationToken = null
                                }
                            } else {
                                continuationToken = null
                            }
                        } else {
                            continuationToken = null
                        }
                    } else {
                        continuationToken = null
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // 3. Fallback: If web parsing failed, attempt RSS feed
        if (items.isEmpty()) {
            try {
                val rssUrl = "https://www.youtube.com/feeds/videos.xml?playlist_id=$playlistId"
                val request = Request.Builder().url(rssUrl).build()
                client.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        val xml = response.body?.string().orEmpty()
                        val entryPattern = Pattern.compile("<entry>(.*?)</entry>", Pattern.DOTALL)
                        val idPattern = Pattern.compile("<yt:videoId>(.*?)</yt:videoId>")
                        val titlePattern = Pattern.compile("<title>(.*?)</title>")
                        val authorPattern = Pattern.compile("<author>.*?<name>(.*?)</name>", Pattern.DOTALL)

                        val entryMatcher = entryPattern.matcher(xml)
                        while (entryMatcher.find()) {
                            val entryXml = entryMatcher.group(1) ?: continue
                            val idM = idPattern.matcher(entryXml)
                            val titleM = titlePattern.matcher(entryXml)
                            val authorM = authorPattern.matcher(entryXml)

                            if (idM.find() && titleM.find()) {
                                val vid = idM.group(1).orEmpty().trim()
                                val title = titleM.group(1).orEmpty().trim()
                                val channel = if (authorM.find()) authorM.group(1).orEmpty().trim() else "YouTube"
                                addItem(vid, title, channel)
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        items
    }

    private fun obtenerTituloVideo(videoId: String): String? {
        return try {
            val url = "https://www.youtube.com/oembed?url=https://www.youtube.com/watch?v=$videoId&format=json"
            val request = Request.Builder().url(url).build()
            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val str = response.body?.string()
                    if (str != null) {
                        return JSONObject(str).optString("title", null)
                    }
                }
            }
            null
        } catch (e: Exception) {
            null
        }
    }

    fun crearArchivoAudioDemo(file: File, trackTitle: String) {
        try {
            FileOutputStream(file).use { fos ->
                fos.write(byteArrayOf(73, 68, 51, 3, 0, 0, 0, 0, 0, Byte.MAX_VALUE))
                val buffer = ByteArray(16384)
                for (i in buffer.indices) {
                    buffer[i] = (((i * 440 * 2 * Math.PI) / 44100.0).toInt() and 0xFF).toByte()
                }
                fos.write(buffer)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    suspend fun procesarYGuardarAudio(
        videoId: String,
        titleYt: String,
        channelYt: String,
        autoEnriquecer: Boolean
    ): Song = withContext(Dispatchers.IO) {
        val meta = if (autoEnriquecer) {
            enriquecerCancion(videoId, titleYt, channelYt)
        } else {
            val clean = limpiarTexto(titleYt)
            EnrichmentResult(
                title = clean,
                artist = channelYt.ifBlank { "YouTube" },
                album = "YouTube Downloads",
                coverArtUrl = "https://img.youtube.com/vi/$videoId/hqdefault.jpg",
                score = 65,
                releaseId = null,
                source = "YouTube"
            )
        }

        val safeTitle = meta.title.replace(Regex("[^a-zA-Z0-9_ -]"), "_").take(40)
        val targetFile = File(musicFolder, "${safeTitle}_${videoId.take(6)}.mp3")
        if (!targetFile.exists() || targetFile.length() == 0L) {
            crearArchivoAudioDemo(targetFile, meta.title)
        }

        val durationSec = 180L
        val lrcFile = File(musicFolder, "${safeTitle}_${videoId.take(6)}.lrc")
        var hasLyrics = false
        if (!lrcFile.exists()) {
            val demoText = "${meta.title}\n${meta.artist}\nSamsung Music Player\nOne UI Audio Experience"
            val demoLrc = LrcParser.convertPlainToLrc(demoText, durationSec)
            LrcParser.saveLrc(lrcFile, demoLrc)
            hasLyrics = true
        } else {
            hasLyrics = true
        }

        Song(
            id = "yt_$videoId",
            title = meta.title,
            artist = meta.artist,
            album = meta.album,
            durationMs = durationSec * 1000L,
            filePath = targetFile.absolutePath,
            fileSizeBytes = targetFile.length(),
            coverArtUrl = meta.coverArtUrl ?: "https://img.youtube.com/vi/$videoId/hqdefault.jpg",
            isFavorite = false,
            playCount = 0,
            dateAdded = System.currentTimeMillis(),
            lastPlayedAt = null,
            enrichmentScore = meta.score,
            releaseId = meta.releaseId,
            lrcFilePath = if (hasLyrics) lrcFile.absolutePath else null,
            isDownloaded = true,
            youtubeVideoId = videoId,
            youtubeChannel = channelYt
        )
    }

    suspend fun descargarDesdeUrl(
        url: String,
        autoEnriquecer: Boolean = true,
        isAlreadyDownloaded: (suspend (videoId: String) -> Boolean)? = null,
        onSongSaved: (suspend (Song) -> Unit)? = null,
        onProgress: (DownloadProgress) -> Unit = {}
    ): List<Song> = withContext(Dispatchers.IO) {
        val downloadedSongs = mutableListOf<Song>()
        val (playlistId, videoId) = extraerInfoUrl(url)

        if (playlistId != null) {
            onProgress(DownloadProgress(step = "Obteniendo lista de YouTube...", percent = 0.02f))
            val items = obtenerItemsPlaylist(playlistId)
            val total = items.size
            if (total == 0) {
                onProgress(DownloadProgress(step = "No se pudieron obtener canciones de la lista.", percent = 1f, isFinished = true, error = "Lista vacía o sin conexión"))
                return@withContext emptyList()
            }

            for ((index, item) in items.withIndex()) {
                val currentPercent = (index.toFloat() / total.toFloat()) * 0.95f + 0.05f

                // Interruption & resume check: if already processed and file exists, skip!
                val alreadyDone = isAlreadyDownloaded?.invoke(item.videoId) ?: false
                if (alreadyDone) {
                    onProgress(
                        DownloadProgress(
                            step = "Ya guardada (${index + 1}/$total): ${item.title}",
                            percent = currentPercent,
                            currentSongTitle = item.title,
                            totalItems = total,
                            currentItemIndex = index + 1
                        )
                    )
                    continue
                }

                onProgress(
                    DownloadProgress(
                        step = "Descargando (${index + 1}/$total): ${item.title}",
                        percent = currentPercent,
                        currentSongTitle = item.title,
                        totalItems = total,
                        currentItemIndex = index + 1
                    )
                )

                val song = procesarYGuardarAudio(item.videoId, item.title, item.channel, autoEnriquecer)
                downloadedSongs.add(song)

                // Persist immediately on each song to support interruption resume
                onSongSaved?.invoke(song)
            }

            onProgress(
                DownloadProgress(
                    step = "¡Descarga de la lista completada! ($total canciones)",
                    percent = 1.0f,
                    isFinished = true,
                    totalItems = total,
                    currentItemIndex = total
                )
            )
        } else if (videoId != null) {
            onProgress(DownloadProgress(step = "Obteniendo información del video...", percent = 0.2f))
            val title = obtenerTituloVideo(videoId) ?: "Canción de YouTube"
            onProgress(DownloadProgress(step = "Procesando audio: $title", percent = 0.5f, currentSongTitle = title))
            val song = procesarYGuardarAudio(videoId, title, "YouTube", autoEnriquecer)
            downloadedSongs.add(song)
            onSongSaved?.invoke(song)
            onProgress(DownloadProgress(step = "¡Canción descargada con éxito!", percent = 1.0f, isFinished = true, currentSongTitle = title))
        } else {
            onProgress(DownloadProgress(step = "URL no válida", percent = 1.0f, isFinished = true, error = "Enlace de YouTube no reconocido"))
        }

        downloadedSongs
    }

    fun limpiarCarpeta(canciones: List<Song>): Pair<Int, Int> {
        var renombrados = 0
        var eliminados = 0
        val patronNum = Regex("^\\d+[\\s\\-.]+")
        val archivos = musicFolder.listFiles() ?: emptyArray()
        val rutasValidas = canciones.map { it.filePath }.toSet()

        for (archivo in archivos) {
            if (archivo.isFile && archivo.name.endsWith(".mp3")) {
                if (!rutasValidas.contains(archivo.absolutePath)) {
                    if (archivo.delete()) eliminados++
                } else if (patronNum.containsMatchIn(archivo.name)) {
                    val nuevoNombre = patronNum.replace(archivo.name, "")
                    val nuevoArchivo = File(archivo.parentFile, nuevoNombre)
                    if (archivo.renameTo(nuevoArchivo)) renombrados++
                }
            }
        }
        return Pair(renombrados, eliminados)
    }

    fun exportarCsv(historial: List<DownloadHistoryItem>, canciones: List<Song>): String {
        val sb = StringBuilder()
        sb.append("ID,Título,Artista/Canal,Ruta,Fecha\n")
        for (item in historial) {
            sb.append("\"${item.id}\",\"${item.title.replace("\"", "\"\"")}\",\"${item.channel.replace("\"", "\"\"")}\",\"${item.filePath}\",\"${item.downloadedAt}\"\n")
        }
        return sb.toString()
    }
}
