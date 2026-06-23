package com.musicplayer.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.musicplayer.R
import com.musicplayer.adapter.PlaylistAdapter
import com.musicplayer.databinding.ActivityPlaylistBinding
import com.musicplayer.db.AppDatabase
import com.musicplayer.model.Playlist
import com.musicplayer.model.PlaylistSong
import com.musicplayer.model.Song
import kotlinx.coroutines.launch

class PlaylistActivity : AppCompatActivity() {

    private lateinit var binding: ActivityPlaylistBinding
    private lateinit var playlistAdapter: PlaylistAdapter
    private val db by lazy { AppDatabase.getInstance(this) }
    private var songToAdd: Song? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPlaylistBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "Playlists"

        @Suppress("DEPRECATION")
        songToAdd = intent.getParcelableExtra("SONG_TO_ADD")

        setupRecyclerView()
        observePlaylists()

        binding.fabNewPlaylist.setOnClickListener {
            showCreatePlaylistDialog()
        }
    }

    private fun setupRecyclerView() {
        playlistAdapter = PlaylistAdapter(
            onPlaylistClick = { playlist ->
                if (songToAdd != null) {
                    addSongToPlaylist(playlist, songToAdd!!)
                } else {
                    openPlaylistDetail(playlist)
                }
            },
            onDeleteClick = { playlist ->
                showDeleteConfirmDialog(playlist)
            }
        )
        binding.rvPlaylists.apply {
            layoutManager = LinearLayoutManager(this@PlaylistActivity)
            adapter = playlistAdapter
        }
    }

    private fun observePlaylists() {
        db.playlistDao().getAllPlaylists().observe(this) { playlists ->
            playlistAdapter.submitList(playlists)
            binding.tvEmptyPlaylists.visibility =
                if (playlists.isEmpty()) android.view.View.VISIBLE
                else android.view.View.GONE
        }
    }

    private fun showCreatePlaylistDialog() {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_create_playlist, null)
        val etPlaylistName = dialogView.findViewById<EditText>(R.id.etPlaylistName)

        AlertDialog.Builder(this, R.style.AlertDialogTheme)
            .setTitle("New Playlist")
            .setView(dialogView)
            .setPositiveButton("Create") { _, _ ->
                val name = etPlaylistName.text.toString().trim()
                if (name.isNotEmpty()) {
                    createPlaylist(name)
                } else {
                    Toast.makeText(this, "Enter a playlist name", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun createPlaylist(name: String) {
        lifecycleScope.launch {
            val id = db.playlistDao().insertPlaylist(Playlist(name = name))
            if (songToAdd != null) {
                val song = songToAdd!!
                val playlistSong = PlaylistSong(
                    playlistId = id,
                    songId = song.id,
                    songTitle = song.title,
                    songArtist = song.artist,
                    songPath = song.path,
                    songDuration = song.duration,
                    albumId = song.albumId
                )
                db.playlistDao().insertPlaylistSong(playlistSong)
                Toast.makeText(this@PlaylistActivity, "Created playlist & added song", Toast.LENGTH_SHORT).show()
                finish()
            } else {
                Toast.makeText(this@PlaylistActivity, "Playlist created", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun addSongToPlaylist(playlist: Playlist, song: Song) {
        lifecycleScope.launch {
            val exists = db.playlistDao().isSongInPlaylist(playlist.id, song.id)
            if (exists) {
                Toast.makeText(this@PlaylistActivity, "Song already in playlist", Toast.LENGTH_SHORT).show()
            } else {
                val playlistSong = PlaylistSong(
                    playlistId = playlist.id,
                    songId = song.id,
                    songTitle = song.title,
                    songArtist = song.artist,
                    songPath = song.path,
                    songDuration = song.duration,
                    albumId = song.albumId
                )
                db.playlistDao().insertPlaylistSong(playlistSong)
                Toast.makeText(this@PlaylistActivity, "Added to ${playlist.name}", Toast.LENGTH_SHORT).show()
                finish()
            }
        }
    }

    private fun openPlaylistDetail(playlist: Playlist) {
        val intent = android.content.Intent(this, PlaylistDetailActivity::class.java).apply {
            putExtra("PLAYLIST_ID", playlist.id)
            putExtra("PLAYLIST_NAME", playlist.name)
        }
        startActivity(intent)
    }

    private fun showDeleteConfirmDialog(playlist: Playlist) {
        AlertDialog.Builder(this, R.style.AlertDialogTheme)
            .setTitle("Delete Playlist")
            .setMessage("Delete \"${playlist.name}\"?")
            .setPositiveButton("Delete") { _, _ ->
                lifecycleScope.launch {
                    db.playlistDao().deletePlaylist(playlist)
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressedDispatcher.onBackPressed()
        return true
    }
}
