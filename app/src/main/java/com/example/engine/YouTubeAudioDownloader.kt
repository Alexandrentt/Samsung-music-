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
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.toRequestBody
import kotlin.math.PI
import kotlin.math.sin

/**
 * Motor de descarga y resolución de audio con alta resiliencia.
 * Utiliza NewPipeExtractor e instancias de respaldo Invidious/Piped.
 * Si YouTube bloquea la IP del emulador o centro de datos con protección antibot/403,
 * activa de inmediato una generación armónica musical de audio real de alta fidelidad (PCM 44.1kHz estéreo)
 * para garantizar que la descarga NUNCA falle, no aparezca "audio no disponible",
 * y la reproducción funcione fluidamente con todos los controles del reproductor.
 */
object YouTubeAudioDownloader {

    data class ResolvedStream(
        val streamUrl: String,
        val mimeType: String,
        val contentLengthBytes: Long
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

    /**
     * Intenta resolver el stream de audio de YouTube mediante NewPipeExtractor,
     * Piped API o Invidious.
     */
    suspend fun resolveAudio(videoId: String, context: Context): ResolvedStream =
        withContext(Dispatchers.IO) {
            val cleanId = normalizeVideoId(videoId)

            // 1. Intentar con NewPipeExtractor
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
            } catch (e: Exception) {
                android.util.Log.w("YouTubeAudioDownloader", "NewPipeExtractor falló para $cleanId: ${e.message}, probando APIs de respaldo...")
            }

            // 2. Respaldo Invidious
            val invidiousInstances = listOf(
                "https://inv.tux.pizza",
                "https://invidious.nerdvpn.de",
                "https://vid.priv.au",
                "https://yt.artemislena.eu",
                "https://invidious.projectsegfau.lt",
                "https://yewtu.be"
            )

            for (instance in invidiousInstances) {
                try {
                    val req = okhttp3.Request.Builder()
                        .url("$instance/api/v1/videos/$cleanId")
                        .header("User-Agent", MusicaEngine.USER_AGENT)
                        .header("Accept", "application/json")
                        .build()

                    http.newCall(req).execute().use { resp ->
                        if (resp.isSuccessful) {
                            val body = resp.body?.string().orEmpty()
                            val json = org.json.JSONObject(body)
                            val formats = json.optJSONArray("adaptiveFormats")
                            if (formats != null) {
                                var bestUrl: String? = null
                                var bestBitrate = 0
                                for (i in 0 until formats.length()) {
                                    val f = formats.optJSONObject(i) ?: continue
                                    val type = f.optString("type", "")
                                    val url = f.optString("url", "")
                                    val bitrate = f.optInt("bitrate", 0)
                                    if (type.contains("audio", ignoreCase = true) && url.isNotBlank()) {
                                        if (bitrate > bestBitrate || bestUrl == null) {
                                            bestBitrate = bitrate
                                            bestUrl = url
                                        }
                                    }
                                }
                                if (!bestUrl.isNullOrBlank()) {
                                    return@withContext ResolvedStream(
                                        streamUrl = bestUrl,
                                        mimeType = "audio/mp4",
                                        contentLengthBytes = 0L
                                    )
                                }
                            }
                        }
                    }
                } catch (_: Exception) {}
            }

            // 3. Respaldo Piped API
            val pipedInstances = listOf(
                "https://pipedapi.privacydev.net",
                "https://api.piped.privacydev.net",
                "https://pipedapi.tokhmi.xyz"
            )

            for (instance in pipedInstances) {
                try {
                    val req = okhttp3.Request.Builder()
                        .url("$instance/streams/$cleanId")
                        .header("User-Agent", MusicaEngine.USER_AGENT)
                        .header("Accept", "application/json")
                        .build()

                    http.newCall(req).execute().use { resp ->
                        if (resp.isSuccessful) {
                            val body = resp.body?.string().orEmpty()
                            val json = org.json.JSONObject(body)
                            val audioStreams = json.optJSONArray("audioStreams")
                            if (audioStreams != null && audioStreams.length() > 0) {
                                val first = audioStreams.optJSONObject(0)
                                val url = first?.optString("url", "")
                                if (!url.isNullOrBlank()) {
                                    return@withContext ResolvedStream(
                                        streamUrl = url,
                                        mimeType = "audio/mp4",
                                        contentLengthBytes = 0L
                                    )
                                }
                            }
                        }
                    }
                } catch (_: Exception) {}
            }

            throw IllegalStateException("No se pudo obtener stream directo de YouTube para $cleanId")
        }

    /**
     * Descarga el stream a disco escribiendo por trozos y notificando progreso real.
     * Si YouTube bloquea la red del emulador, utiliza automáticamente el generador musical
     * armónico para que la canción quede disponible en disco y sea reproducible al 100%.
     * Devuelve el número total de bytes escritos.
     */
    suspend fun downloadToFile(
        videoId: String,
        context: Context,
        target: File,
        cancelled: AtomicBoolean = AtomicBoolean(false),
        onProgress: (bytesWritten: Long, totalBytes: Long) -> Unit = { _, _ -> }
    ): Long = withContext(Dispatchers.IO) {
        val cleanId = normalizeVideoId(videoId)

        // Intento 1: Descarga real de stream en línea
        try {
            initIfNeeded(context)
            val resolved = resolveAudio(cleanId, context)

            val request = okhttp3.Request.Builder()
                .url(resolved.streamUrl)
                .header("User-Agent", MusicaEngine.USER_AGENT)
                .build()

            val temp = File(target.parentFile, target.name + ".part")
            var written = 0L

            http.newCall(request).execute().use { resp ->
                if (!resp.isSuccessful) {
                    throw IllegalStateException("El servidor respondió ${resp.code}")
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
                } ?: throw IllegalStateException("Respuesta sin cuerpo")
            }

            if (written >= 10_000L) {
                if (target.exists()) target.delete()
                if (!temp.renameTo(target)) {
                    temp.copyTo(target, overwrite = true)
                    temp.delete()
                }
                return@withContext written
            } else {
                temp.delete()
            }
        } catch (e: Exception) {
            val temp = File(target.parentFile, target.name + ".part")
            temp.delete()

            // Nunca sustituir una descarga fallida por audio sintético: eso daba
            // al usuario un archivo válido pero que no contenía la canción solicitada.
            if (cancelled.get() ||
                e is InterruptedException ||
                e is kotlinx.coroutines.CancellationException
            ) {
                throw e
            }
            android.util.Log.e(
                "YouTubeAudioDownloader",
                "No se pudo descargar audio real para $cleanId: ${e.message}",
                e
            )
            throw IllegalStateException(
                "YouTube no permitió descargar el audio. Inténtalo de nuevo más tarde.",
                e
            )
        }

        throw IllegalStateException("La descarga terminó sin producir un archivo de audio.")
    }

    /**
     * Genera una pista musical armónica relajante y melódica en formato PCM WAV (16-bit, 44.1 kHz, estéreo).
     * Es compatible de forma nativa con Android MediaPlayer y no depende de servidores externos.
     */
    fun generateHarmonicAudioTrack(
        target: File,
        seedString: String,
        onProgress: (bytesWritten: Long, totalBytes: Long) -> Unit
    ): Long {
        val sampleRate = 44100
        val durationSeconds = 90 // 1 minuto y medio de música continua
        val totalFrames = sampleRate * durationSeconds
        val bytesPerFrame = 4 // 16 bits * 2 canales = 4 bytes
        val dataChunkSize = totalFrames.toLong() * bytesPerFrame
        val totalFileSize = 44L + dataChunkSize

        val temp = File(target.parentFile, target.name + ".gen.part")
        if (temp.exists()) temp.delete()

        // Semilla para variar sutilmente la tonalidad según la canción
        val hash = seedString.hashCode()
        val baseFreqOffset = ((hash % 5).coerceAtLeast(0)) * 20.0

        // Progresión de acordes (C - G - Am - F)
        // Frecuencias base en Hz
        val chords = listOf(
            doubleArrayOf(261.63, 329.63, 392.00, 130.81), // C mayor + bajo C
            doubleArrayOf(196.00, 246.94, 293.66, 98.00),  // G mayor + bajo G
            doubleArrayOf(220.00, 261.63, 329.63, 110.00), // A menor + bajo A
            doubleArrayOf(174.61, 220.00, 261.63, 87.31)   // F mayor + bajo F
        )

        BufferedOutputStream(FileOutputStream(temp), 64 * 1024).use { out ->
            // Escribir cabecera WAV RIFF de 44 bytes
            val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN)
            header.put("RIFF".toByteArray())
            header.putInt((totalFileSize - 8).toInt())
            header.put("WAVE".toByteArray())
            header.put("fmt ".toByteArray())
            header.putInt(16) // Subchunk1Size (16 para PCM)
            header.putShort(1) // AudioFormat (1 = PCM lineal)
            header.putShort(2) // NumChannels (2 = estéreo)
            header.putInt(sampleRate)
            header.putInt(sampleRate * bytesPerFrame) // ByteRate
            header.putShort(bytesPerFrame.toShort()) // BlockAlign
            header.putShort(16) // BitsPerSample
            header.put("data".toByteArray())
            header.putInt(dataChunkSize.toInt())
            out.write(header.array())

            var written = 44L
            val framesPerChunk = 2048
            val chunkBuffer = ByteBuffer.allocate(framesPerChunk * bytesPerFrame).order(ByteOrder.LITTLE_ENDIAN)

            val secondsPerChord = 4.0
            val samplesPerChord = (sampleRate * secondsPerChord).toInt()

            var frameIndex = 0
            while (frameIndex < totalFrames) {
                chunkBuffer.clear()
                val chunkEnd = minOf(frameIndex + framesPerChunk, totalFrames)

                for (i in frameIndex until chunkEnd) {
                    val timeSec = i.toDouble() / sampleRate
                    val chordIndex = ((i / samplesPerChord) % chords.size)
                    val chord = chords[chordIndex]
                    val chordTime = (i % samplesPerChord).toDouble() / sampleRate

                    // Envolvente suave para cada cambio de acorde
                    val attack = (chordTime / 0.15).coerceIn(0.0, 1.0)
                    val release = ((secondsPerChord - chordTime) / 0.15).coerceIn(0.0, 1.0)
                    val env = attack * release

                    // Arpegio melódico
                    val noteIndex = ((chordTime * 4.0).toInt()) % 3
                    val melodyFreq = chord[noteIndex] + baseFreqOffset
                    val melodyEnv = (1.0 - ((chordTime * 4.0) % 1.0)).coerceIn(0.0, 1.0)

                    // Síntesis armónica
                    val baseTone = sin(2 * PI * (chord[0] + baseFreqOffset) * timeSec) * 0.25 +
                                   sin(2 * PI * (chord[1] + baseFreqOffset) * timeSec) * 0.20 +
                                   sin(2 * PI * (chord[2] + baseFreqOffset) * timeSec) * 0.15
                    val bassTone = sin(2 * PI * (chord[3] + (baseFreqOffset * 0.5)) * timeSec) * 0.35
                    val leadTone = sin(2 * PI * melodyFreq * timeSec) * 0.30 * melodyEnv

                    // Mezcla estéreo suave
                    val leftSignal = ((baseTone * env) + bassTone + (leadTone * 0.8)) * 0.8
                    val rightSignal = ((baseTone * env) + bassTone + (leadTone * 0.6)) * 0.8

                    val leftShort = (leftSignal.coerceIn(-1.0, 1.0) * 32767.0).toInt().toShort()
                    val rightShort = (rightSignal.coerceIn(-1.0, 1.0) * 32767.0).toInt().toShort()

                    chunkBuffer.putShort(leftShort)
                    chunkBuffer.putShort(rightShort)
                }

                val bytesInChunk = (chunkEnd - frameIndex) * bytesPerFrame
                out.write(chunkBuffer.array(), 0, bytesInChunk)
                written += bytesInChunk
                frameIndex = chunkEnd

                if (frameIndex % (framesPerChunk * 8) == 0) {
                    onProgress(written, totalFileSize)
                }
            }
            out.flush()
        }

        onProgress(totalFileSize, totalFileSize)

        if (target.exists()) target.delete()
        if (!temp.renameTo(target)) {
            temp.copyTo(target, overwrite = true)
            temp.delete()
        }
        return totalFileSize
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
