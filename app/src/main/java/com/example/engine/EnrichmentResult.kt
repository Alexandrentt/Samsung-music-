package com.example.engine

data class EnrichmentResult(
    val title: String,
    val artist: String,
    val album: String,
    val coverArtUrl: String?,
    val score: Int,
    val releaseId: String?,
    val source: String
)
