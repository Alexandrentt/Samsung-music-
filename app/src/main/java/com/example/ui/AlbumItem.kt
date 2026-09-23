package com.example.ui

import com.example.data.Song

data class AlbumItem(
    val name: String,
    val artist: String,
    val songs: List<Song>,
    val coverArtUrl: String?
)
