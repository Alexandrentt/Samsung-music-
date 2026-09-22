package com.example.data

import androidx.room.Embedded
import androidx.room.Junction
import androidx.room.Relation

data class PlaylistWithSongs(
    @Embedded
    val playlist: Playlist,
    @Relation(
        parentColumn = "id",
        entityColumn = "id",
        associateBy = Junction(
            value = PlaylistSongCrossRef::class,
            parentColumn = "playlistId",
            entityColumn = "songId"
        )
    )
    val songs: List<Song>
)
