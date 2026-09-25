package com.example.engine

import android.content.Context
import android.media.MediaPlayer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.downloader.Downloader
import org.schabi.newpipe.extractor.downloader.Request
import org.schabi.newpipe.extractor.downloader.Response
import org.schabi.newpipe.extractor.localization.Localization
import org.schabi.newpipe.extractor.services.youtube.ItagItem
import org.schabi.newpipe.extractor.services.youtube.YoutubeService
import org.schabi.newpipe.extractor.stream.StreamInfo
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

/**
 * Motor de descarga REAL de audio desde YouTube.
 *
 * Estrategia en cascada (todas las fuentes devuelven el audio REAL de la canción):
 *  1. NewPipeExtractor (el mismo motor de NewPipe).
 *  2. API interna de YouTube (InnerTube) con el cliente ANDROID: devuelve URLs
 *     directas de googlevideo sin firma, descargables tal cual.
 *  3. InnerTube con el cliente IOS como tercer intento.
 *
 * NO genera audio sintético bajo ninguna circunstancia: si todas las fuentes
 * fallan, lanza el error real para que la notificación muestre el motivo.
 */
object YouTubeAudioDownloader {

    data class ResolvedStream(
        val streamUrl: String,
        val mimeType: String,
        val contentLengthBytes: Long,
        val downloadUserAgent: String = MusicaEngine.USER_AGENT
    )

    private val http: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15L, TimeUnit.SECONDS)
            .readTimeout(30L, TimeUnit.SECONDS)
            .build()
    }

    @Volatile
    private var initialized = false

    private val initLock = Any()

    private fun initIfNeeded(context: Context) {
        if (initialized) return
        synchronized(initLock) {
            if (initialized) return
            val downloader = object : Downloader() {
                override fun execute(request: Request): Response {
                    val builder = okhttp3.Request.Builder()
                        .url(request.url())
                        .header("User-Agent", MusicaEngine.USER_AGENT)
                    request.headers().forEach { (name, values) ->
                        values.forEach { value ->
                            if (!name.equals("User-Agent", ignoreCase = true)) {
                                builder.header(name, value)
                            }
                        }
                    }
                    val body = request.dataToSend()
                    val call = if (body != null) {
                        builder.post(body.toRequestBody(null)).build()
                    } else {
                        builder.get().build()
                    }
                    val resp = http.newCall(call).execute()
                    val bytes = resp.body?.bytes() ?: ByteArray(0)
                    return Response(
                        resp.code,
                        resp.message,
                        resp.headers.toMultimap(),
                        bytes.toString(Charsets.ISO_8859_1),
                        request.url()
                    )
                }
            }
            NewPipe.init(downloader, Localization("es", "MX"))
            YoutubeService(0)
            initialized = true
        }
    }

    // ── Clientes internos de YouTube (InnerTube) ────────────────────────────
    // Estos clientes reciben URLs directas de googlevideo sin cifrar, por lo
    // que el audio se descarga sin necesidad de descifrar firmas.
    private const val ANDROID_UA =
        "com.google.android.youtube/20.10.38 (Linux; U; Android 14) gzip"
    private const val IOS_UA =
        "com.google.ios.youtube/20.10.4 (iPhone16,2; U; CPU iOS 18_1_0 like Mac OS X;) gzip"

    private fun innertubeRequestBody(videoId: String, clientJson: String): String {
        val client = JSONObject(clientJson)
        client.put("hl", "es")
        return JSONObject()
            .put("context", JSONObject().put("client", client))
            .put("videoId", videoId)
            .put("contentCheckOk", true)
            .put("racyCheckOk", true)
            .toString()
    }

    private val ANDROID_CLIENT = JSONObject()
        .put("clientName", "ANDROID")
        .put("clientVersion", "20.10.38")
        .put("androidSdkVersion", 34)
        .put("osName", "Android")
        .put("osVersion", "14")
        .toString()

    private val IOS_CLIENT = JSONObject()
        .put("clientName", "IOS")
        .put("clientVersion", "20.10.4")
        .put("deviceMake", "Apple")
        .put("deviceModel", "iPhone16,2")
        .put("osName", "iPhone")
        .put("osVersion", "18.1.0.22B83")
        .toString()

    /**
     * Resuelve el stream de audio mediante la API interna de YouTube con el
     * cliente dado (ANDROID o IOS). Devuelve null si la respuesta no es
     * utilizable (video no disponible, sin URLs directas, etc.).
     */
    private fun resolveViaInnertube(videoId: String, clientJson: String, userAgent: String): ResolvedStream? {
        return try {
            val body = innertubeRequestBody(videoId, clientJson)
            val request = okhttp3.Request.Builder()
                .url("https://www.youtube.com/youtubei/v1/player")
                .header("User-Agent", userAgent)
                .header("Content-Type", "application/json")
                .post(body.toRequestBody("application/json; charset=utf-8".toMediaType()))
                .build()

            http.newCall(request).execute().use { resp ->
                if (!resp.isSuccessful) return null
                val json = JSONObject(resp.body?.string() ?: return null)

                val status = json.optJSONObject("playabilityStatus")?.optString("status")
                if (status != "OK") {
                    android.util.Log.w(
                        "YouTubeAudioDownloader",
                        "InnerTube ${json.optJSONObject("playabilityStatus")?.optString("status")}: " +
                                json.optJSONObject("playabilityStatus")?.optString("reason")
                    )
                    return null
                }

                val formats = json.optJSONObject("streamingData")
                    ?.optJSONArray("adaptiveFormats") ?: return null

                var bestUrl: String? = null
                var bestMime = ""
                var bestBitrate = 0L
                var bestLength = 0L

                for (i in 0 until formats.length()) {
                    val f = formats.optJSONObject(i) ?: continue
                    val mime = f.optString("mimeType", "")
                    val url = f.optString("url", "")
                    // Solo formatos con URL directa (los cifrados se descartan)
                    if (url.isBlank() || !mime.startsWith("audio", ignoreCase = true)) continue
                    val bitrate = f.optLong("bitrate", 0L)
                    // Prioriza audio/mp4 (AAC): archivo .m4a nativo para MediaPlayer
                    val esMp4 = mime.startsWith("audio/mp4", ignoreCase = true)
                    val mejorQueElActual = esMp4 && !bestMime.startsWith("audio/mp4") ||
                            bitrate > bestBitrate && (esMp4 == bestMime.startsWith("audio/mp4"))
                    if (bestUrl == null || mejorQueElActual) {
                        bestUrl = url
                        bestMime = mime.substringBefore(';').trim()
                        bestBitrate = bitrate
                        bestLength = f.optLong("contentLength", 0L)
                    }
                }

                if (bestUrl != null) {
                    ResolvedStream(
                        streamUrl = bestUrl,
                        mimeType = bestMime.ifBlank { "audio/mp4" },
                        contentLengthBytes = bestLength,
                        downloadUserAgent = userAgent
                    )
                } else {
                    null
                }
            }
        } catch (e: Exception) {
            android.util.Log.w("YouTubeAudioDownloader", "InnerTube falló: ${e.message}")
            null
        }
    }

    /**
     * Intenta resolver el stream de audio REAL por varias vías, en orden:
     * NewPipeExtractor → InnerTube ANDROID → InnerTube IOS.
     * Lanza la excepción con el motivo real si todas fallan.
     */
    suspend fun resolveAudio(videoId: String, context: Context): ResolvedStream =
        withContext(Dispatchers.IO) {
            val cleanId = normalizeVideoId(videoId)
            val errores = mutableListOf<String>()

            // 1. NewPipeExtractor
            try {
                initIfNeeded(context)
                val info = StreamInfo.getInfo(
                    YoutubeService(0),
                    "https://www.youtube.com/watch?v=$cleanId"
                )
                val streams = info.audioStreams
                if (!streams.isNullOrEmpty()) {
                    val best = streams
                        .filter { it.isUrl }
                        .maxByOrNull { it.averageBitrate }
                        ?: streams.first()
                    val contentLength = best.itagItem?.contentLength ?: ItagItem.CONTENT_LENGTH_UNKNOWN
                    return@withContext ResolvedStream(
                        streamUrl = best.content,
                        mimeType = best.format?.mimeType ?: "audio/mp4",
                        contentLengthBytes = if (contentLength != ItagItem.CONTENT_LENGTH_UNKNOWN) contentLength else 0L
                    )
                }
                errores.add("NewPipeExtractor: sin streams de audio")
            } catch (e: Exception) {
                errores.add("NewPipeExtractor: ${e.message ?: e.javaClass.simpleName}")
            }

            // 2. InnerTube cliente ANDROID
            resolveViaInnertube(cleanId, ANDROID_CLIENT, ANDROID_UA)?.let { return@withContext it }

            // 3. InnerTube cliente IOS
            resolveViaInnertube(cleanId, IOS_CLIENT, IOS_UA)?.let { return@withContext it }

            throw IllegalStateException(
                "No se pudo obtener el audio de YouTube para $cleanId. " +
                        "Motivos: ${errores.joinToString("; ")}. Verifica tu conexión."
            )
        }

    /**
     * Descarga el stream REAL a disco escribiendo por trozos y notificando
     * progreso real en bytes. Devuelve el número total de bytes escritos.
     * No genera audio sintético: si la red falla, propaga el error real.
     */
    suspend fun downloadToFile(
        videoId: String,
        context: Context,
        target: File,
        cancelled: AtomicBoolean = AtomicBoolean(false),
        onProgress: (bytesWritten: Long, totalBytes: Long) -> Unit = { _, _ -> }
    ): Long = withContext(Dispatchers.IO) {
        val cleanId = normalizeVideoId(videoId)
        val resolved = resolveAudio(cleanId, context)

        val request = okhttp3.Request.Builder()
            .url(resolved.streamUrl)
            .header("User-Agent", resolved.downloadUserAgent)
            .build()

        val temp = File(target.parentFile, target.name + ".part")
        var written = 0L

        try {
            http.newCall(request).execute().use { resp ->
                if (!resp.isSuccessful) {
                    throw IllegalStateException("El servidor de audio respondió ${resp.code}")
                }
                val total = resp.body?.contentLength() ?: resolved.contentLengthBytes
                resp.body?.byteStream()?.use { input ->
                    FileOutputStream(temp).use { output ->
                        val buffer = ByteArray(64 * 1024)
                        while (true) {
                            if (cancelled.get()) throw InterruptedException("Descarga cancelada")
                            val read = input.read(buffer)
                            if (read == -1) break
                            output.write(buffer, 0, read)
                            written += read
                            if (total > 0) onProgress(written, total)
                        }
                        output.flush()
                    }
                } ?: throw IllegalStateException("Respuesta sin cuerpo al descargar el audio")
            }

            if (written < 10_000L) {
                throw IllegalStateException("El audio descargado es demasiado pequeño ($written bytes)")
            }

            if (target.exists()) target.delete()
            if (!temp.renameTo(target)) {
                temp.copyTo(target, overwrite = true)
                temp.delete()
            }
            written
        } catch (e: Exception) {
            temp.delete()
            throw e
        }
    }

    /**
     * Sondea la duración real del archivo usando MediaPlayer (sin reproducir).
     */
    suspend fun probeDurationMs(filePath: String): Long = withContext(Dispatchers.IO) {
        try {
            val mp = MediaPlayer()
            mp.setDataSource(filePath)
            mp.prepare()
            val duration = mp.duration.toLong()
            mp.release()
            if (duration > 0) duration else 0L
        } catch (e: Exception) {
            0L
        }
    }

    /**
     * Normaliza IDs tipo "yG7MPEQm1-w", "3.5#yG7MPEQm1-w" o URLs completas al ID de 11 caracteres.
     */
    fun normalizeVideoId(raw: String): String {
        val cleaned = raw.substringAfterLast('#').substringBefore("&t=")
        val match = Regex("([a-zA-Z0-9_-]{11})").find(cleaned)
        return match?.groupValues?.get(1) ?: cleaned
    }
}
