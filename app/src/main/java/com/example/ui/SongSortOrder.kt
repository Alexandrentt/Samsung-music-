package com.example.ui

import com.example.data.Song

/**
 * Criterios de ordenación de canciones. Cada criterio puede mostrarse
 * en orden ascendente o descendente (ver [ascending]).
 */
enum class SongSortField(val displayName: String) {
    CUSTOM("Personalizado"),
    TITLE("Título"),
    ARTIST("Artista"),
    ALBUM("Álbum"),
    DATE_ADDED("Fecha de adición"),
    DURATION("Duración"),
    PLAY_COUNT("Reproducciones")
}

data class SongSortOrder(
    val field: SongSortField = SongSortField.TITLE,
    val ascending: Boolean = true
)

/**
 * Devuelve un comparador según el campo y la dirección elegidos.
 * En modo [SongSortField.CUSTOM], usa el mapa de posiciones personalizadas.
 */
fun SongSortOrder.comparator(customOrderMap: Map<String, Int> = emptyMap()): Comparator<Song> {
    val base: Comparator<Song> = when (field) {
        SongSortField.CUSTOM -> compareBy { customOrderMap[it.id] ?: Int.MAX_VALUE }
        SongSortField.TITLE -> compareBy { it.title.lowercase() }
        SongSortField.ARTIST -> compareBy({ it.artist.lowercase() }, { it.title.lowercase() })
        SongSortField.ALBUM -> compareBy({ it.album.lowercase() }, { it.title.lowercase() })
        SongSortField.DATE_ADDED -> compareBy { it.dateAdded }
        SongSortField.DURATION -> compareBy { it.durationMs }
        SongSortField.PLAY_COUNT -> compareBy { it.playCount }
    }
    return if (ascending) base else base.reversed()
}
