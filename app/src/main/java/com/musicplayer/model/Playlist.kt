package com.musicplayer.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.ForeignKey
import androidx.room.Index

@Entity(tableName = "playlists")
data class Playlist(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "playlist_songs",
    foreignKeys = [
        ForeignKey(
            entity = Playlist::class,
            parentColumns = ["id"],
            childColumns = ["playlistId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("playlistId")]
)
data class PlaylistSong(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val playlistId: Long,
    val songId: Long,
    val songTitle: String,
    val songArtist: String,
    val songPath: String,
    val songDuration: Long,
    val albumId: Long,
    val addedAt: Long = System.currentTimeMillis()
)

data class PlaylistWithSongs(
    val playlist: Playlist,
    val songs: List<PlaylistSong>
)

@Entity(tableName = "favourite_songs")
data class FavouriteSong(
    @PrimaryKey
    val songId: Long,
    val songTitle: String,
    val songArtist: String,
    val songPath: String,
    val songDuration: Long,
    val albumId: Long,
    val addedAt: Long = System.currentTimeMillis()
)
