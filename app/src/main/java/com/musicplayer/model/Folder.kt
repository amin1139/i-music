package com.musicplayer.model

data class Folder(
    val name: String,
    val path: String,
    val songCount: Int,
    val songs: List<Song>
)
