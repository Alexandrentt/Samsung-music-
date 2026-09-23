package com.example.ui

import com.example.data.Song

data class ArtistItem(
    val name: String,
    val songs: List<Song>,
    val coverArtUrl: String?
)
