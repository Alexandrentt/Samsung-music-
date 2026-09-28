package com.example.ui

import com.example.data.Song

data class ArtistGroup(
    val normalizedKey: String,
    val displayName: String,
    val songs: List<Song>,
    val representativeCoverUrl: String? = null
) {
    val trackCount: Int get() = songs.size
}
