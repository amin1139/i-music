package com.musicplayer.db

import androidx.lifecycle.LiveData
import androidx.room.*
import com.musicplayer.model.Playlist
import com.musicplayer.model.PlaylistSong
import com.musicplayer.model.FavouriteSong

@Dao
interface PlaylistDao {

    // Playlist operations
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlaylist(playlist: Playlist): Long

    @Delete
    suspend fun deletePlaylist(playlist: Playlist)

    @Update
    suspend fun updatePlaylist(playlist: Playlist)

    @Query("SELECT * FROM playlists ORDER BY createdAt DESC")
    fun getAllPlaylists(): LiveData<List<Playlist>>

    @Query("SELECT * FROM playlists ORDER BY createdAt DESC")
    suspend fun getAllPlaylistsSync(): List<Playlist>

    @Query("SELECT * FROM playlists WHERE id = :id")
    suspend fun getPlaylistById(id: Long): Playlist?

    // PlaylistSong operations
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlaylistSong(song: PlaylistSong)

    @Query("DELETE FROM playlist_songs WHERE playlistId = :playlistId AND songId = :songId")
    suspend fun removeFromPlaylist(playlistId: Long, songId: Long)

    @Query("SELECT * FROM playlist_songs WHERE playlistId = :playlistId ORDER BY addedAt ASC")
    fun getSongsInPlaylist(playlistId: Long): LiveData<List<PlaylistSong>>

    @Query("SELECT * FROM playlist_songs WHERE playlistId = :playlistId ORDER BY addedAt ASC")
    suspend fun getSongsInPlaylistSync(playlistId: Long): List<PlaylistSong>

    @Query("SELECT COUNT(*) FROM playlist_songs WHERE playlistId = :playlistId")
    fun getSongCount(playlistId: Long): LiveData<Int>

    @Query("SELECT COUNT(*) FROM playlist_songs WHERE playlistId = :playlistId")
    suspend fun getSongCountSync(playlistId: Long): Int

    @Query("SELECT EXISTS(SELECT 1 FROM playlist_songs WHERE playlistId = :playlistId AND songId = :songId)")
    suspend fun isSongInPlaylist(playlistId: Long, songId: Long): Boolean

    // Favourite operations
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFavourite(song: FavouriteSong)

    @Query("DELETE FROM favourite_songs WHERE songId = :songId")
    suspend fun removeFavourite(songId: Long)

    @Query("SELECT * FROM favourite_songs ORDER BY addedAt DESC")
    fun getAllFavourites(): LiveData<List<FavouriteSong>>

    @Query("SELECT EXISTS(SELECT 1 FROM favourite_songs WHERE songId = :songId)")
    suspend fun isFavourite(songId: Long): Boolean

    @Query("SELECT songId FROM favourite_songs")
    suspend fun getAllFavouriteIds(): List<Long>
}
