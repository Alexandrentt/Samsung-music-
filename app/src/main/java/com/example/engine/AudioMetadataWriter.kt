package com.example.engine

import android.util.Log
import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.ReturnCode
import java.io.File

/**
 * Escribe etiquetas ID3 en el MP3 físico, no solo en la base de datos.
 * Copia los streams sin recodificar el audio y conserva el .lrc externo.
 */
object AudioMetadataWriter {
    private const val TAG = "AudioMetadataWriter"

    fun writeTags(
        audioFile: File,
        title: String,
        artist: String,
        album: String,
        lyrics: String? = null
    ): Boolean {
        if (!audioFile.isFile || !audioFile.extension.equals("mp3", ignoreCase = true)) return false
        val parent = audioFile.parentFile ?: return false
        val token = audioFile.nameWithoutExtension
        val metadataFile = File(parent, ".$token.ffmetadata.tmp")
        val outputFile = File(parent, ".$token.tags.tmp.mp3")
        val backupFile = File(parent, ".$token.tags.bak.mp3")
        var backupCreated = false

        try {
            val metadata = buildString {
                append(";FFMETADATA1\n")
                append("title=").append(escape(title)).append('\n')
                append("artist=").append(escape(artist)).append('\n')
                append("album=").append(escape(album)).append('\n')
                if (!lyrics.isNullOrBlank()) {
                    append("lyrics=").append(escape(lyrics)).append('\n')
                    append("syncedlyrics=").append(escape(lyrics)).append('\n')
                }
            }
            metadataFile.writeText(metadata, Charsets.UTF_8)

            val session = FFmpegKit.executeWithArguments(
                arrayOf(
                    "-y", "-i", audioFile.absolutePath,
                    "-f", "ffmetadata", "-i", metadataFile.absolutePath,
                    "-map", "0", "-map_metadata", "1",
                    "-c", "copy", "-id3v2_version", "3",
                    outputFile.absolutePath
                )
            )
            if (!ReturnCode.isSuccess(session.returnCode) ||
                !outputFile.isFile || outputFile.length() <= 10_000L ||
                !hasMp3Header(outputFile)
            ) {
                Log.w(TAG, "No se pudieron escribir las etiquetas ID3: ${session.allLogsAsString}")
                return false
            }

            if (backupFile.exists() && !backupFile.delete()) {
                Log.w(TAG, "No se pudo limpiar un respaldo previo: ${backupFile.name}")
                return false
            }
            if (!audioFile.renameTo(backupFile)) {
                Log.w(TAG, "No se pudo crear respaldo antes de actualizar etiquetas")
                return false
            }
            backupCreated = true

            if (!outputFile.renameTo(audioFile)) {
                val restored = backupFile.renameTo(audioFile)
                backupCreated = !restored
                Log.w(TAG, "No se pudo reemplazar el MP3 con la versión etiquetada")
                return false
            }

            if (!audioFile.isFile || audioFile.length() <= 10_000L || !hasMp3Header(audioFile)) {
                audioFile.delete()
                val restored = backupFile.renameTo(audioFile)
                backupCreated = !restored
                return false
            }

            if (backupFile.exists() && backupFile.delete()) backupCreated = false
            return true
        } catch (e: Exception) {
            Log.w(TAG, "No se pudieron escribir los metadatos de ${audioFile.name}", e)
            if (backupFile.exists()) {
                try { if (audioFile.exists()) audioFile.delete() } catch (_: Exception) {}
                backupCreated = !backupFile.renameTo(audioFile)
            }
            return false
        } finally {
            try { metadataFile.delete() } catch (_: Exception) {}
            try { outputFile.delete() } catch (_: Exception) {}
            // Si no se pudo restaurar el original, conservar el .bak para no perder audio.
            if (!backupCreated) try { backupFile.delete() } catch (_: Exception) {}
        }
    }

    private fun hasMp3Header(file: File): Boolean = try {
        file.inputStream().use { input ->
            val header = ByteArray(3)
            if (input.read(header) < 3) return false
            (header[0] == 'I'.code.toByte() &&
                header[1] == 'D'.code.toByte() &&
                header[2] == '3'.code.toByte()) ||
                (header[0] == 0xFF.toByte() && (header[1].toInt() and 0xE0) == 0xE0)
        }
    } catch (_: Exception) {
        false
    }

    private fun escape(value: String): String = value
        .replace("\\", "\\\\")
        .replace("=", "\\=")
        .replace(";", "\\;")
        .replace("#", "\\#")
        .replace("\r\n", "\\n")
        .replace("\n", "\\n")
        .replace("\r", "\\n")
}
