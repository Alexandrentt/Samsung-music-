package com.example.engine

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.ReturnCode
import java.io.File

/**
 * Escribe etiquetas ID3 en el MP3 físico, no solo en la base de datos.
 * Los temporales viven en caché para respetar el almacenamiento restringido de Android.
 */
object AudioMetadataWriter {
    private const val TAG = "AudioMetadataWriter"

    fun writeTags(
        context: Context,
        audioFile: File,
        title: String,
        artist: String,
        album: String,
        lyrics: String? = null
    ): Boolean {
        if (!audioFile.isFile || !audioFile.extension.equals("mp3", ignoreCase = true)) return false
        val token = "${audioFile.nameWithoutExtension}_${System.nanoTime()}"
        val metadataFile = File(context.cacheDir, "$token.ffmetadata")
        val outputFile = File(context.cacheDir, "$token.tags.mp3")
        val backupFile = File(context.cacheDir, "$token.original.mp3")
        var backupReady = false
        var mediaUri: Uri? = null

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
                Log.w(TAG, "No se pudieron escribir las etiquetas ID3: ${session.failStackTrace ?: session.returnCode}")
                return false
            }

            audioFile.copyTo(backupFile, overwrite = true)
            backupReady = backupFile.isFile && backupFile.length() == audioFile.length()
            if (!backupReady) {
                Log.w(TAG, "No se pudo respaldar el MP3 original antes de etiquetarlo")
                return false
            }

            mediaUri = findMediaStoreUri(context, audioFile)
            if (mediaUri != null) {
                context.contentResolver.openOutputStream(mediaUri, "w")?.use { output ->
                    outputFile.inputStream().use { input -> input.copyTo(output) }
                } ?: throw IllegalStateException("No se pudo escribir el MP3 mediante MediaStore")
            } else {
                // Fallback para archivos dentro del almacenamiento privado de la app.
                outputFile.copyTo(audioFile, overwrite = true)
            }

            if (!audioFile.isFile || audioFile.length() <= 10_000L || !hasMp3Header(audioFile)) {
                throw IllegalStateException("El MP3 etiquetado no pasó la validación")
            }

            backupFile.delete()
            backupReady = false
            return true
        } catch (e: Exception) {
            Log.w(TAG, "No se pudieron escribir los metadatos de ${audioFile.name}", e)
            if (backupReady && backupFile.isFile) {
                try {
                    if (mediaUri != null) {
                        context.contentResolver.openOutputStream(mediaUri, "w")?.use { output ->
                            backupFile.inputStream().use { input -> input.copyTo(output) }
                        } ?: throw IllegalStateException("No se pudo restaurar desde el respaldo")
                    } else {
                        backupFile.copyTo(audioFile, overwrite = true)
                    }
                    backupReady = false
                } catch (restoreError: Exception) {
                    // Conservar el respaldo en caché si la restauración no fue posible.
                    Log.e(TAG, "El original sigue respaldado en ${backupFile.absolutePath}", restoreError)
                    backupReady = true
                }
            }
            return false
        } finally {
            try { metadataFile.delete() } catch (_: Exception) {}
            try { outputFile.delete() } catch (_: Exception) {}
            if (!backupReady) try { backupFile.delete() } catch (_: Exception) {}
        }
    }

    private fun findMediaStoreUri(context: Context, file: File): Uri? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return null
        val musicRoot = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC)
        val relativePath = try {
            val root = musicRoot.canonicalPath.trimEnd(File.separatorChar)
            val targetParent = file.parentFile?.canonicalPath ?: return null
            if (targetParent != root && !targetParent.startsWith(root + File.separator)) return null
            val relative = targetParent.removePrefix(root)
                .trimStart(File.separatorChar)
                .replace(File.separatorChar, '/')
            Environment.DIRECTORY_MUSIC + if (relative.isBlank()) "/" else "/$relative/"
        } catch (_: Exception) {
            return null
        }
        return try {
            context.contentResolver.query(
                MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY),
                arrayOf(MediaStore.Audio.Media._ID),
                "${MediaStore.MediaColumns.DISPLAY_NAME} = ? AND ${MediaStore.MediaColumns.RELATIVE_PATH} = ?",
                arrayOf(file.name, relativePath),
                null
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val id = cursor.getLong(0)
                    Uri.withAppendedPath(
                        MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY),
                        id.toString()
                    )
                } else null
            }
        } catch (e: Exception) {
            Log.w(TAG, "No se encontró URI de MediaStore para ${file.name}", e)
            null
        }
    }

    private fun hasMp3Header(file: File): Boolean {
        return try {
            file.inputStream().use { input ->
                val header = ByteArray(3)
                if (input.read(header) < 3) {
                    false
                } else {
                    (header[0] == 'I'.code.toByte() &&
                        header[1] == 'D'.code.toByte() &&
                        header[2] == '3'.code.toByte()) ||
                        (header[0] == 0xFF.toByte() && (header[1].toInt() and 0xE0) == 0xE0)
                }
            }
        } catch (_: Exception) {
            false
        }
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
