package com.example.player

import android.content.Context
import android.content.Intent
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Captura crashes NO manejados de la app y los guarda en un archivo.
 *
 * Objetivo: dejar de adivinar la causa del "se cierra sola". El archivo
 * crash_log.txt queda en la carpeta pública de la app y la pantalla de
 * descargas permite compartirlo (Compartir registro de errores) para
 * diagnosticar el problema exacto con el stack trace completo.
 */
object CrashHandler {

    private const val FILE_NAME = "crash_log.txt"
    private const val MAX_FILE_BYTES = 512 * 1024L // recorta el log viejo

    fun install(context: Context) {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                saveCrash(context.applicationContext, thread, throwable)
            } catch (_: Throwable) {
                // Nada puede salvarnos aquí ya; no interferir con el crash.
            }
            // Delegar al handler del sistema para el diálogo normal de cierre.
            previous?.uncaughtException(thread, throwable)
        }
    }

    private fun saveCrash(context: Context, thread: Thread, throwable: Throwable) {
        val sw = StringWriter()
        throwable.printStackTrace(PrintWriter(sw))

        val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
        val header = buildString {
            append("════════ CRASH ").append(timestamp).append(" ════════\n")
            append("Hilo: ").append(thread.name).append("\n")
            append("App: Música v")
            try {
                val pm = context.packageManager
                val info = pm.getPackageInfo(context.packageName, 0)
                append(info.versionName).append(" (").append(info.versionCode).append(")")
            } catch (_: Exception) {
                append("?")
            }
            append("\nAndroid SDK: ").append(android.os.Build.VERSION.SDK_INT)
            append(" / ").append(android.os.Build.MODEL).append("\n\n")
            append(sw.toString()).append("\n")
        }

        val dir = File(context.getExternalFilesDir(null) ?: context.filesDir, "logs")
        if (!dir.exists()) dir.mkdirs()
        val logFile = File(dir, FILE_NAME)

        // Recorta el archivo si creció demasiado (conserva el final, lo más reciente)
        if (logFile.exists() && logFile.length() > MAX_FILE_BYTES) {
            val text = logFile.readText()
            logFile.writeText(text.takeLast(MAX_FILE_BYTES.toInt() / 2))
        }

        logFile.appendText(header)
    }

    /** Ruta del log de crashes, o null si aún no hay crashes registrados. */
    fun crashLogFile(context: Context): File? {
        val dir = File(context.getExternalFilesDir(null) ?: context.filesDir, "logs")
        val f = File(dir, FILE_NAME)
        return if (f.exists() && f.length() > 0) f else null
    }

    /** Intent para compartir el registro de errores (usado desde la UI). */
    fun shareIntent(context: Context): Intent? {
        val file = crashLogFile(context) ?: return null
        return try {
            val uri = androidx.core.content.FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )
            Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_SUBJECT, "Registro de errores de Música")
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        } catch (_: Exception) {
            null
        }
    }
}
