package com.example.engine

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import com.example.data.DownloadHistoryItem
import com.example.data.Song
import com.example.data.SongMatching
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import android.media.MediaScannerConnection
import android.os.Environment
import java.io.File
import java.net.URLEncoder
import java.text.Normalizer
import java.util.Locale
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern

class MusicaEngine(private val context: Context) {

    private val client = OkHttpClient.Builder()
        .connectTimeout(15L, TimeUnit.SECONDS)
        .readTimeout(25L, TimeUnit.SECONDS)
        .build()    /**
     * Carpeta de almacenamiento para canciones descargadas.
     *
     * Prioriza la carpeta PÚBLICA /Music/SamsungMusic (almacenamiento compartido,
     * FUERA de Android/data): los archivos que viven ahí NO se pierden al
     * desinstalar la app y son recuperados por [scanAndRecoverExistingSongs].
     * Si el sistema no permite escribir ahí (permisos), hace fallback al
     * directorio app-specific y luego al interno, garantizando que la descarga
     * siempre tenga destino válido.
     */
    val musicFolder: File by lazy {
        val candidates = mutableListOf<File>()
        try {
            candidates.add(
                File(
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC),
                    "SamsungMusic"
                )
            )
        } catch (_: Exception) {}
        context.getExternalFilesDir(Environment.DIRECTORY_MUSIC)?.let { candidates.add(it) }
        candidates.add(File(context.filesDir, "Music"))

        // Elige la primera carpeta donde realmente podamos crear archivos.
        candidates.firstOrNull { dir ->
            try {
                if (!dir.exists()) dir.mkdirs()
                val probe = File(dir, ".write_probe_tmp")
                val ok = probe.createNewFile()
                probe.delete()
                ok
            } catch (_: Exception) {
                false
            }
        } ?: File(context.filesDir, "Music").apply { mkdirs() }
    }

    /**
     * Escanea canciones ya descargadas en la carpeta de música compartida y en la
     * carpeta interna legacy para recuperarlas tras reinstalación o borrado de caché.
     */
    suspend fun scanAndRecoverExistingSongs(): List<Song> = withContext(Dispatchers.IO) {
        val recovered = mutableListOf<Song>()
        val foldersToScan = mutableListOf<File>()
        try {
            val publicDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC)
            foldersToScan.add(File(publicDir, "SamsungMusic"))
        } catch (_: Exception) {}
        context.getExternalFilesDir(Environment.DIRECTORY_MUSIC)?.let { foldersToScan.add(it) }
        context.getExternalFilesDir("Music")?.let { foldersToScan.add(it) }
        foldersToScan.add(musicFolder)

        val seenPaths = mutableSetOf<String>()
        for (folder in foldersToScan.distinctBy { it.absolutePath }) {
            if (!folder.exists() || !folder.isDirectory) continue
            val audioFiles = folder.listFiles { file ->
                file.isFile && (file.name.endsWith(".m4a") || file.name.endsWith(".mp3") || file.name.endsWith(".wav")) && file.length() > 10_000L
            } ?: emptyArray()

            for (audioFile in audioFiles) {
                if (!seenPaths.add(audioFile.absolutePath)) continue
                // Parsing canónico: evita duplicados con sufijo basura/código.
                val (parsedId, baseTitle) = parseMediaFileName(audioFile.nameWithoutExtension)
                val cleanId = parsedId?.take(11).orEmpty()
                val id = if (cleanId.length == 11) "yt_$cleanId" else "local_${audioFile.name.hashCode()}"

                val probedMs = YouTubeAudioDownloader.probeDurationMs(audioFile.absolutePath)
                val lrcFile = File(audioFile.parentFile, "${audioFile.nameWithoutExtension}.lrc")
                val lrcPath = if (lrcFile.exists() && lrcFile.length() > 0) lrcFile.absolutePath else null

                recovered.add(
                    Song(
                        id = id,
                        title = baseTitle.ifBlank { "Canción descargada" },
                        artist = "Artista desconocido",
                        album = "Descargas",
                        durationMs = if (probedMs > 0) probedMs else 180000L,
                        filePath = audioFile.absolutePath,
                        fileSizeBytes = audioFile.length(),
                        coverArtUrl = if (cleanId.length == 11) "https://img.youtube.com/vi/$cleanId/hqdefault.jpg" else null,
                        isFavorite = false,
                        playCount = 0,
                        dateAdded = audioFile.lastModified(),
                        lastPlayedAt = null,
                        enrichmentScore = 80,
                        releaseId = null,
                        lrcFilePath = lrcPath,
                        isDownloaded = true,
                        bitrate = "320 kbps",
                        youtubeVideoId = if (cleanId.length == 11) cleanId else null,
                        youtubeChannel = "Música"
                    )
                )
            }
        }
        recovered
    }

    /**
     * Migra al almacenamiento COMPARTIDO (/Music/SamsungMusic) todas las canciones
     * descargadas que aún vivan en carpetas app-specific (Android/data), que se
     * PIERDEN al desinstalar la app o limpiar sus datos. Solo se ejecuta si la
     * carpeta pública es escribible; en caso contrario devuelve (0, 0).
     *
     * Devuelve (archivos movidos, filas actualizadas).
     */
    suspend fun migrateAppMusicToPublicFolder(
        listRowsUnderPath: suspend (String) -> List<Song>,
        updateSongRow: suspend (Song) -> Unit
    ): Pair<Int, Int> = withContext(Dispatchers.IO) {
        var moved = 0
        var updated = 0
        try {
            val target = musicFolder
            val publicRoot = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC)
            val publicRootUsable = try {
                publicRoot.isDirectory && publicRoot.canWrite()
            } catch (_: Exception) {
                false
            }
            if (target.absolutePath.startsWith(publicRoot.absolutePath) && !publicRootUsable) {
                // Destino público declarado pero sin escritura real: no tocar nada.
                return@withContext Pair(0, 0)
            }

            val dirs = buildList {
                context.getExternalFilesDir(Environment.DIRECTORY_MUSIC)?.let { add(it) }
                context.getExternalFilesDir("Music")?.let { add(it) }
            }.filter { it.isDirectory }
            if (dirs.isEmpty()) return@withContext Pair(0, 0)
            val prefixes = dirs.map { it.absolutePath.trimEnd('/') + "/" }

            val seenPaths = mutableSetOf<String>()

            for (prefix in prefixes) {
                val rows = listRowsUnderPath(prefix)
                for (row in rows) {
                    val f = File(row.filePath)
                    if (!f.isFile) continue
                    if (!seenPaths.add(f.absolutePath)) continue

                    var dest = File(target, f.name)
                    // Evita sobrescribir un archivo distinto con el mismo nombre.
                    var seq = 1
                    while (dest.exists() && dest.length() != f.length()) {
                        dest = File(target, "${f.nameWithoutExtension}_$seq.${f.extension}")
                        seq++
                    }
                    if (dest.exists() && dest.length() == f.length() && dest.length() > 0) {
                        // Ya existe en destino: solo borrar el origen y repuntar la fila.
                        f.delete()
                    } else {
                        if (!f.renameTo(dest)) {
                            try {
                                f.copyTo(dest, overwrite = false)
                                f.delete()
                            } catch (e: Exception) {
                                continue
                            }
                        }
                        moved++
                    }

                    val lrc = File(f.parentFile, "${f.nameWithoutExtension}.lrc")
                    var newLrc = row.lrcFilePath
                    if (lrc.isFile) {
                        val destLrc = File(target, "${dest.nameWithoutExtension}.lrc")
                        if (lrc.renameTo(destLrc) || lrc.delete()) {
                            newLrc = destLrc.absolutePath
                        }
                    }

                    updateSongRow(
                        row.copy(
                            filePath = dest.absolutePath,
                            fileSizeBytes = dest.length(),
                            lrcFilePath = newLrc
                        )
                    )
                    updated++
                    try {
                        MediaScannerConnection.scanFile(
                            context,
                            arrayOf(dest.absolutePath),
                            arrayOf("audio/mp4"),
                            null
                        )
                    } catch (_: Exception) {}
                }
            }
            Pair(moved, updated)
        } catch (e: Exception) {
            android.util.Log.w("MusicaEngine", "Migración de almacenamiento falló: ${e.message}")
            Pair(0, 0)
        }
    }

    data class PlaylistItem(val videoId: String, val title: String, val channel: String)

    companion object {
        /**
         * Parsing centralizado de nombres de archivo multimedia (causa raíz de los
         * duplicados con código al final). Devuelve (videoId?, título limpio).
         * - "Corazon_de_Papel_rY0WqhfEA2w" → ("rY0WqhfEA2w", "Corazon de Papel")
         * - "Sunsetz 5-rbSNzU" → (null, "Sunsetz") — sufijo basura, ID falso NO
         * - "Verano 2024" / "Song 2" → (null, título intacto)
         */
        fun parseMediaFileName(rawName: String): Pair<String?, String> {
            val noExt = rawName.substringBeforeLast('.')
            // 1) ¿Termina en un ID de YouTube canónico (_xxxxxxxxxxx o pegado)?
            val idMatch = Regex("[ _-]([A-Za-z0-9_-]{11})$").find(noExt)
            if (idMatch != null) {
                val maybeId = idMatch.groupValues[1]
                val looksLikeId = maybeId.any { it.isDigit() } && maybeId.any { it.isLetter() }
                if (looksLikeId) {
                    val title = noExt.substring(0, idMatch.range.first)
                        .replace('_', ' ').replace(Regex("\\s+"), " ").trim()
                    return Pair(maybeId, title)
                }
            }
            // 2) ID truncado tras guion bajo ("_5rbSNz")
            val shortMatch = Regex("_([A-Za-z0-9]{4,10})$").find(noExt)
            if (shortMatch != null && SongMatching.looksLikeHash(shortMatch.groupValues[1])) {
                val title = noExt.substring(0, shortMatch.range.first).replace('_', ' ').trim()
                return Pair(null, title)
            }
            // 3) Sin ID: título con guiones bajos y sufijo basura opcional
            val base = noExt.replace('_', ' ')
            return Pair(null, SongMatching.stripJunkSuffix(base).trim())
        }

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

    private data class YouTubeDetails(
        val title: String,
        val channel: String,
        val thumbnailUrl: String?,
        val durationMs: Long?
    )

    /**
     * Usa primero videoDetails de la propia página de YouTube. oEmbed queda como
     * respaldo porque solo devuelve título/autor y no incluye duración ni miniaturas
     * completas. Los campos se tratan como opcionales: YouTube puede cambiar el HTML.
     */
    private fun obtenerDetallesVideo(videoId: String): YouTubeDetails? {
        try {
            val url = "https://www.youtube.com/watch?v=$videoId"
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", USER_AGENT)
                .header("Accept-Language", "es-ES,es;q=0.9,en;q=0.8")
                .build()
            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val html = response.body?.string().orEmpty()
                    val matcher = Pattern.compile(
                        "ytInitialPlayerResponse\\s*=\\s*(\\{.*?\\})\\s*;",
                        Pattern.DOTALL
                    ).matcher(html)
                    if (matcher.find()) {
                        val playerJson = JSONObject(matcher.group(1) ?: "{}")
                        val details = playerJson.optJSONObject("videoDetails")
                        if (details != null) {
                            val title = details.optString("title", "").trim()
                            val channel = details.optString("author", "").trim()
                            val durationSeconds = details.optString("lengthSeconds", "")
                                .toLongOrNull()
                            val thumbs = details.optJSONObject("thumbnail")?.optJSONArray("thumbnails")
                            val thumbnail = thumbs?.let { arr ->
                                (arr.length() - 1 downTo 0).firstNotNullOfOrNull { index ->
                                    arr.optJSONObject(index)?.optString("url")?.takeIf { it.isNotBlank() }
                                }
                            }
                            if (title.isNotBlank()) {
                                return YouTubeDetails(
                                    title = title,
                                    channel = channel.ifBlank { "YouTube" },
                                    thumbnailUrl = thumbnail,
                                    durationMs = durationSeconds?.takeIf { it > 0 }?.times(1000L)
                                )
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            android.util.Log.w("MusicaEngine", "No se pudieron leer los detalles de YouTube: ${e.message}")
        }

        // Respaldo oEmbed: no reemplazar detalles completos si ya se consiguieron.
        return try {
            val url = "https://www.youtube.com/oembed?url=https://www.youtube.com/watch?v=$videoId&format=json"
            val request = Request.Builder().url(url).header("User-Agent", USER_AGENT).build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@use null
                val json = response.body?.string()?.let(::JSONObject) ?: return@use null
                val title = json.optString("title", "").trim()
                if (title.isBlank()) null else YouTubeDetails(
                    title = title,
                    channel = json.optString("author_name", "YouTube").ifBlank { "YouTube" },
                    thumbnailUrl = json.optString("thumbnail_url", "").takeIf { it.isNotBlank() },
                    durationMs = null
                )
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun obtenerTituloVideo(videoId: String): String? =
        obtenerDetallesVideo(videoId)?.title

    /**
     * Publica el audio en Music/SamsungMusic para que sea visible para otros
     * reproductores. Android 10+ requiere MediaStore; escribir directamente en
     * /storage/emulated/0/Music no es fiable con scoped storage.
     */
    private fun publicarEnMusicaCompartida(source: File): File {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            val publicDir = File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC),
                "SamsungMusic"
            )
            if (!publicDir.exists() && !publicDir.mkdirs()) {
                throw IllegalStateException("No se pudo crear la carpeta Música/SamsungMusic")
            }
            val destination = File(publicDir, source.name)
            if (source.absolutePath != destination.absolutePath) {
                source.copyTo(destination, overwrite = true)
                source.delete()
            }
            return destination
        }

        val publicDir = File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC),
            "SamsungMusic"
        )
        val expectedPath = File(publicDir, source.name)
        if (source.absolutePath == expectedPath.absolutePath && expectedPath.exists()) return expectedPath

        // Si el archivo canónico ya está publicado, reutilizarlo en lugar de
        // insertar otra fila MediaStore con el mismo audio y otro nombre.
        if (expectedPath.isFile && expectedPath.length() >= 10_000L &&
            expectedPath.length() == source.length() &&
            source.absolutePath != expectedPath.absolutePath
        ) {
            source.delete()
            return expectedPath
        }

        val resolver = context.contentResolver
        val mimeType = when (source.extension.lowercase(Locale.ROOT)) {
            "mp3" -> "audio/mpeg"
            "wav" -> "audio/wav"
            "ogg", "opus" -> "audio/ogg"
            else -> "audio/mp4"
        }
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, source.name)
            put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
            put(MediaStore.MediaColumns.RELATIVE_PATH, "Music/SamsungMusic")
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val uri: Uri = resolver.insert(
            MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY),
            values
        ) ?: throw IllegalStateException("Android no pudo registrar la canción en la biblioteca de música")

        try {
            resolver.openOutputStream(uri, "w")?.use { output ->
                source.inputStream().use { input -> input.copyTo(output) }
            } ?: throw IllegalStateException("No se pudo abrir el archivo de destino de MediaStore")
            val published = ContentValues().apply {
                put(MediaStore.MediaColumns.IS_PENDING, 0)
            }
            resolver.update(uri, published, null, null)

            // MediaStore puede cambiar el nombre si ya existe otro archivo con el
            // mismo nombre. Recuperar el nombre real evita crear un duplicado fuera
            // del índice multimedia.
            var publishedName = source.name
            resolver.query(uri, arrayOf(MediaStore.MediaColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    publishedName = cursor.getString(0)?.takeIf { it.isNotBlank() } ?: source.name
                }
            }
            val destination = File(publicDir, publishedName)
            if (!destination.exists() || destination.length() != source.length()) {
                throw IllegalStateException("Android publicó el audio, pero no se pudo resolver su ruta compartida")
            }
            source.delete()
            return destination
        } catch (e: Exception) {
            try { resolver.delete(uri, null, null) } catch (_: Exception) {}
            throw e
        }
    }

    /**
     * Descarga REAL: resuelve el stream de audio con NewPipeExtractor, lo baja a
     * disco con progreso en bytes, sondea la duración real del archivo y guarda
     * letras sincronizadas reales obtenidas de LRCLIB.
     */
    suspend fun procesarYGuardarAudio(
        videoId: String,
        titleYt: String,
        channelYt: String,
        autoEnriquecer: Boolean,
        onProgress: (DownloadProgress) -> Unit = {}
    ): Song = withContext(Dispatchers.IO) {
        val cleanId = YouTubeAudioDownloader.normalizeVideoId(videoId)
        val youtubeDetails = obtenerDetallesVideo(cleanId)
        val canonicalTitle = youtubeDetails?.title?.takeIf { it.isNotBlank() } ?: titleYt
        val canonicalChannel = youtubeDetails?.channel?.takeIf { it.isNotBlank() } ?: channelYt
        val meta = if (autoEnriquecer) {
            enriquecerCancion(cleanId, canonicalTitle, canonicalChannel)
        } else {
            val clean = limpiarTexto(canonicalTitle)
            val partes = clean.split(" - ", limit = 2)
            EnrichmentResult(
                title = if (partes.size == 2) partes[1].trim() else clean,
                artist = if (partes.size == 2) partes[0].trim() else canonicalChannel.ifBlank { "YouTube" },
                album = "YouTube",
                coverArtUrl = youtubeDetails?.thumbnailUrl ?: "https://img.youtube.com/vi/$cleanId/hqdefault.jpg",
                score = 65,
                releaseId = null,
                source = "YouTube"
            )
        }

        // Nombre estable y sin parámetros de URL. Usar el ID completo evita que
        // dos videos con los mismos primeros seis caracteres colisionen.
        val safeTitle = meta.title
            .replace(Regex("[^a-zA-Z0-9_ -]"), "_")
            .replace(Regex("\\s+"), " ")
            .trim()
            .take(40)
            .ifBlank { "Cancion" }
        val canonicalFileName = "${safeTitle}_${cleanId}.m4a"
        val defaultTargetFile = File(musicFolder, canonicalFileName)

        // Buscar por ID completo en todas las ubicaciones usadas por versiones
        // anteriores. No usar coincidencias parciales: causaban falsos duplicados.
        val publicDir = File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC),
            "SamsungMusic"
        )
        val candidateDirs = listOfNotNull(
            publicDir,
            musicFolder,
            context.getExternalFilesDir(Environment.DIRECTORY_MUSIC),
            context.getExternalFilesDir("Music"),
            File(context.filesDir, "Music")
        ).distinctBy { it.absolutePath }
        val existingFile = candidateDirs.asSequence()
            .filter { it.isDirectory }
            .flatMap { it.listFiles()?.asSequence() ?: emptySequence() }
            .firstOrNull { file ->
                file.isFile && file.length() >= 10_000L &&
                    file.extension.lowercase(Locale.ROOT) in setOf("m4a", "mp3", "wav", "ogg", "opus") &&
                    (
                        file.nameWithoutExtension.contains(cleanId, ignoreCase = true) ||
                        parseMediaFileName(file.nameWithoutExtension).first == cleanId
                    )
            }
        var targetFile = existingFile ?: defaultTargetFile

        val bytes: Long
        if (!targetFile.exists() || targetFile.length() < 10_000L) {
            bytes = YouTubeAudioDownloader.downloadToFile(
                cleanId, context, targetFile
            ) { written, total ->
                onProgress(
                    DownloadProgress(
                        step = "Descargando audio…",
                        percent = if (total > 0) (written.toFloat() / total) else 0f,
                        currentSongTitle = meta.title,
                        bytesDownloaded = written,
                        bytesTotal = total
                    )
                )
            }
        } else {
            bytes = targetFile.length()
            onProgress(
                DownloadProgress(
                    step = "Audio ya disponible en el dispositivo",
                    percent = 1.0f,
                    currentSongTitle = meta.title,
                    bytesDownloaded = bytes,
                    bytesTotal = bytes
                )
            )
        }

        // Publica el audio en la colección compartida Música para que lo vean
        // Samsung Music y otros reproductores incluso en Android 10+.
        targetFile = publicarEnMusicaCompartida(targetFile)

        // Prioriza la duración del archivo; si el contenedor no puede sondearse,
        // conserva la duración que YouTube publica en los detalles del video.
        val probedMs = YouTubeAudioDownloader.probeDurationMs(targetFile.absolutePath)
        val durationMs = when {
            probedMs > 0 -> probedMs
            (youtubeDetails?.durationMs ?: 0L) > 0L -> youtubeDetails!!.durationMs!!
            else -> 0L
        }

        // Letra real (sincronizada) desde LRCLIB usando la duración verdadera
        val lrcFile = File(targetFile.parentFile, "${safeTitle}_${cleanId}.lrc")
        if (!lrcFile.exists()) {
            val lrc = LrcParser.fetchLrcFromApi(meta.title, meta.artist, durationMs / 1000)
            if (lrc.isNullOrBlank()) {
                // Marcador vacío para no reintentar en cada arranque
                try { lrcFile.createNewFile() } catch (_: Exception) {}
            } else {
                LrcParser.saveLrc(lrcFile, lrc)
            }
        }

        try {
            MediaScannerConnection.scanFile(
                context,
                arrayOf(targetFile.absolutePath),
                arrayOf(
                    when (targetFile.extension.lowercase(Locale.ROOT)) {
                        "mp3" -> "audio/mpeg"
                        "wav" -> "audio/wav"
                        "ogg", "opus" -> "audio/ogg"
                        else -> "audio/mp4"
                    }
                ),
                null
            )
        } catch (_: Exception) {}

        Song(
            id = "yt_$cleanId",
            title = meta.title,
            artist = meta.artist,
            album = meta.album,
            durationMs = durationMs,
            filePath = targetFile.absolutePath,
            fileSizeBytes = targetFile.length(),
            coverArtUrl = youtubeDetails?.thumbnailUrl ?: meta.coverArtUrl ?: "https://img.youtube.com/vi/$cleanId/hqdefault.jpg",
            isFavorite = false,
            playCount = 0,
            dateAdded = System.currentTimeMillis(),
            lastPlayedAt = null,
            enrichmentScore = meta.score,
            releaseId = meta.releaseId,
            lrcFilePath = lrcFile.absolutePath,
            isDownloaded = true,
            youtubeVideoId = cleanId,
            youtubeChannel = canonicalChannel
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
                val basePercent = (index.toFloat() / total.toFloat())
                val currentPercent = basePercent * 0.95f + 0.05f

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

                // Descarga real con progreso de bytes por canción
                val song = try {
                    procesarYGuardarAudio(item.videoId, item.title, item.channel, autoEnriquecer) { p ->
                        val songFraction = p.percent * (0.95f / total.toFloat())
                        onProgress(
                            p.copy(
                                percent = currentPercent + songFraction,
                                currentSongTitle = p.currentSongTitle.ifBlank { item.title },
                                totalItems = total,
                                currentItemIndex = index + 1
                            )
                        )
                    }
                } catch (e: Exception) {
                    onProgress(
                        DownloadProgress(
                            step = "Sin audio disponible: ${item.title}",
                            percent = currentPercent,
                            currentSongTitle = item.title,
                            totalItems = total,
                            currentItemIndex = index + 1
                        )
                    )
                    continue
                }

                downloadedSongs.add(song)
                onProgress(
                    DownloadProgress(
                        step = "Guardada (${index + 1}/$total): ${item.title}",
                        percent = currentPercent,
                        currentSongTitle = item.title,
                        totalItems = total,
                        currentItemIndex = index + 1
                    )
                )
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
            val cleanId = YouTubeAudioDownloader.normalizeVideoId(videoId)
            onProgress(DownloadProgress(step = "Comprobando si ya existe en la biblioteca...", percent = 0.05f))

            val alreadyDone = isAlreadyDownloaded?.invoke(cleanId) ?: false
            if (alreadyDone) {
                onProgress(
                    DownloadProgress(
                        step = "Esta canción ya está descargada en tu biblioteca",
                        percent = 1.0f,
                        isFinished = true,
                        totalItems = 1,
                        currentItemIndex = 1
                    )
                )
                return@withContext emptyList()
            }

            onProgress(DownloadProgress(step = "Obteniendo información del video...", percent = 0.10f))
            val title = obtenerTituloVideo(cleanId) ?: "Canción de YouTube"
            val song = try {
                procesarYGuardarAudio(cleanId, title, "YouTube", autoEnriquecer) { p ->
                    onProgress(
                        p.copy(
                            percent = 0.10f + p.percent * 0.85f,
                            totalItems = 1,
                            currentItemIndex = 1
                        )
                    )
                }
            } catch (e: Exception) {
                onProgress(
                    DownloadProgress(
                        step = "No se pudo descargar el audio",
                        percent = 1.0f,
                        isFinished = true,
                        error = e.message ?: "Error de descarga"
                    )
                )
                return@withContext emptyList()
            }
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
                    // Carpeta COMPARTIDA: solo borrar MP3 no referenciados que
                    // parezcan descargas legacy de la app (sufijo con código);
                    // jamás tocar la música propia del usuario.
                    val baseName = archivo.nameWithoutExtension
                    val pareceLegacy = SongMatching.hasJunkSuffix(baseName) ||
                            Regex("_[A-Za-z0-9]{4,11}$").containsMatchIn(baseName)
                    if (pareceLegacy && archivo.delete()) eliminados++
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
