package com.example.engine

data class DownloadProgress(
    val step: String,
    val percent: Float,
    val currentSongTitle: String = "",
    val totalItems: Int = 1,
    val currentItemIndex: Int = 0,
    val isFinished: Boolean = false,
    val error: String? = null
)
