package com.example.data

import java.text.Normalizer
import kotlin.math.abs

/**
 * Heurísticas PURAS (sin dependencias de Android) usadas por el dedupe de la
 * biblioteca. Viven fuera del ViewModel para poder probarlas con JUnit.
 */
object SongMatching {

    /** Minúsculas, sin acentos y solo [a-z0-9]: "Perdón" == "perd n" == "perdon". */
    fun norm(s: String): String =
        Normalizer.normalize(s, Normalizer.Form.NFD)
            .replace(Regex("\\p{InCombiningDiacriticalMarks}+"), "")
            .lowercase()
            .replace(Regex("[^a-z0-9]"), "")
            .trim()

    /** Similitud 0..100 (Levenshtein sobre títulos normalizados). */
    fun fuzzyRatio(s1: String, s2: String): Int {
        val a = norm(s1)
        val b = norm(s2)
        if (a == b) return 100
        val len1 = a.length
        val len2 = b.length
        if (len1 == 0 || len2 == 0) return 0
        val d = Array(len1 + 1) { IntArray(len2 + 1) }
        for (i in 0..len1) d[i][0] = i
        for (j in 0..len2) d[0][j] = j
        for (i in 1..len1) for (j in 1..len2) {
            d[i][j] = minOf(
                d[i - 1][j] + 1,
                d[i][j - 1] + 1,
                d[i - 1][j - 1] + if (a[i - 1] == b[j - 1]) 0 else 1
            )
        }
        return (((maxOf(len1, len2) - d[len1][len2]).toDouble() / maxOf(len1, len2)) * 100).toInt()
    }

    private val separatorThenToken = Regex("[\\s\\-_]+([A-Za-z0-9\\-]{4,11})$")
    private val ytId = Regex("^[A-Za-z0-9_-]{11}$")

    /**
     * ¿Parece un hash/ID de video y no una palabra real? Debe mezclar letra(s)
     * y dígito(s) — o ser exactamente un ID de YouTube de 11 caracteres.
     * "5-rbSNzU", "mZyXw1", "K1V9" → true. "2024", "II", "Papel" → false.
     */
    fun looksLikeHash(token: String): Boolean {
        if (ytId.matches(token)) return true
        val hasDigit = token.any { it.isDigit() }
        val hasLetter = token.any { it.isLetter() }
        return hasDigit && hasLetter
    }

    /** true si [title] termina en un sufijo basura con código. */
    fun hasJunkSuffix(title: String): Boolean {
        val t = title.trim()
        val m = separatorThenToken.find(t) ?: return false
        return looksLikeHash(m.groupValues[1]) && norm(t.substring(0, m.range.first)).length >= 4
    }

    /**
     * Quita el sufijo basura con código que se colaba en los títulos:
     * "Sunsetz 5-rbSNzU" → "Sunsetz", "Milagro K1V9" → "Milagro",
     * "fanshop supernova mZyXw1" → "fanshop supernova".
     *
     * Los títulos reales NO se tocan: "Verano 2024" (solo dígitos),
     * "Song 2" (muy corto), "Corazón de Papel" (solo letras) quedan igual.
     * Solo aplica si la base resultante conserva ≥4 caracteres alfanuméricos.
     */
    fun stripJunkSuffix(title: String): String {
        val t = title.trim()
        val m = separatorThenToken.find(t) ?: return t
        val token = m.groupValues[1]
        if (!looksLikeHash(token)) return t
        val base = t.substring(0, m.range.first).trim()
        return if (norm(base).length >= 4) base else t
    }

    /** Clave de agrupación por título: normalizado + sin sufijo basura. */
    fun titleKey(title: String): String = norm(stripJunkSuffix(title))

    /**
     * Dos filas apuntan al MISMO audio: duración casi idéntica (±1,5 s o ±3 %)
     * y tamaño casi idéntico (±2 %; si falta el tamaño confía en la duración).
     */
    fun sameAudio(a: Song, b: Song): Boolean {
        val dDur = abs(a.durationMs - b.durationMs)
        val maxDur = maxOf(a.durationMs, b.durationMs, 1L)
        if (dDur > 1_500 && dDur.toDouble() / maxDur > 0.03) return false
        val sa = a.fileSizeBytes
        val sb = b.fileSizeBytes
        if (sa <= 0 || sb <= 0) return true
        return abs(sa - sb).toDouble() / maxOf(sa, sb) <= 0.02
    }
}
