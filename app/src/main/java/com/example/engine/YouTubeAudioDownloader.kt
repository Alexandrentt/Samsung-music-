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
import org.schabi.newpipe.extractor.stream.AudioStream
import org.schabi.newpipe.extractor.stream.StreamInfo
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.toRequestBody

/**
 * Descarga REAL de audio desde YouTube usando NewPipeExtractor (el mismo motor
 * que usa NewPipe). Resuelve la URL del stream de audio, la descarga por trozos
 * escribiendo en disco, y reporta progreso real en bytes.
 */
object YouTubeAudioDownloader {

    data class ResolvedStream(
        val streamUrl: String,
        val mimeType: String,
        val contentLengthBytes: Long
    )

    private val http: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(20L, TimeUnit.SECONDS)
            .readTimeout(60L, TimeUnit.SECONDS)
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
            // Toca la clase del servicio para asegurar que los extensiones JS/DASH carguen.
            YoutubeService(0)
            initialized = true
        }
    }

    /**
     * Intenta resolver el stream de audio de varios formatos de ID de video
     * (los IDs pueden ir precedidos de un prefijo de timestamp para reproducción
     * a mitad de video, por ejemplo "#3.5&t=30s"). Normaliza el ID primero.
     */
    suspend fun resolveAudio(videoId: String, context: Context): ResolvedStream =
        withContext(Dispatchers.IO) {
            initIfNeeded(context)
            val cleanId = normalizeVideoId(videoId)
            val info = StreamInfo.getInfo(
                YoutubeService(0),
                "https://www.youtube.com/watch?v=$cleanId"
            )
            val streams = info.audioStreams
            if (streams.isNullOrEmpty()) {
                throw IllegalStateException("No hay streams de audio disponibles para este video")
            }
            // Prioriza streams de entrega directa por URL (no DASH/manifest) con
            // mejor bitrate; MediaPlayer necesita URLs progresivas descargables.
            val best = streams
                .filter { it.isUrl }
                .maxByOrNull { it.averageBitrate }
                ?: streams.first()
            val contentLength = best.itagItem?.contentLength ?: ItagItem.CONTENT_LENGTH_UNKNOWN
            ResolvedStream(
                streamUrl = best.content,
                mimeType = best.format?.mimeType ?: "audio/mp4",
                contentLengthBytes = if (contentLength != ItagItem.CONTENT_LENGTH_UNKNOWN) contentLength else 0L
            )
        }

    /**
     * Descarga el stream a disco escribiendo por trozos y notificando progreso real.
     * Devuelve el número total de bytes escritos.
     */
    suspend fun downloadToFile(
        videoId: String,
        context: Context,
        target: File,
        cancelled: AtomicBoolean = AtomicBoolean(false),
        onProgress: (bytesWritten: Long, totalBytes: Long) -> Unit = { _, _ -> }
    ): Long = withContext(Dispatchers.IO) {
        initIfNeeded(context)
        val resolved = resolveAudio(videoId, context)

        val request = okhttp3.Request.Builder()
            .url(resolved.streamUrl)
            .header("User-Agent", MusicaEngine.USER_AGENT)
            .build()

        val temp = File(target.parentFile, target.name + ".part")
        var written = 0L

        try {
            http.newCall(request).execute().use { resp ->
                if (!resp.isSuccessful) {
                    throw IllegalStateException("El servidor respondió ${resp.code} al descargar el audio")
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
                temp.delete()
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
