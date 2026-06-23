package com.musicplayer.ui

import android.content.*
import android.os.Bundle
import android.os.IBinder
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.musicplayer.adapter.PlaylistSongAdapter
import com.musicplayer.databinding.ActivityPlaylistDetailBinding
import com.musicplayer.db.AppDatabase
import com.musicplayer.model.PlaylistSong
import com.musicplayer.model.Song
import com.musicplayer.service.MusicService
import kotlinx.coroutines.launch

class PlaylistDetailActivity : AppCompatActivity() {

    private lateinit var binding: ActivityPlaylistDetailBinding
    private lateinit var adapter: PlaylistSongAdapter
    private val db by lazy { AppDatabase.getInstance(this) }
    private var playlistId = 0L
    private var musicService: MusicService? = null
    private var isBound = false
    private var playlistSongs: List<PlaylistSong> = emptyList()

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            musicService = (service as MusicService.MusicBinder).getService()
            isBound = true
        }
        override fun onServiceDisconnected(name: ComponentName?) {
            isBound = false
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPlaylistDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)

        playlistId = intent.getLongExtra("PLAYLIST_ID", 0L)
        val playlistName = intent.getStringExtra("PLAYLIST_NAME") ?: "Playlist"

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = playlistName

        bindService(Intent(this, MusicService::class.java), serviceConnection, Context.BIND_AUTO_CREATE)

        setupRecyclerView()
        observeSongs()

        binding.btnPlayAll.setOnClickListener {
            if (playlistSongs.isNotEmpty()) {
                playPlaylist(0)
            } else {
                Toast.makeText(this, "No songs in playlist", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun setupRecyclerView() {
        adapter = PlaylistSongAdapter(
            onSongClick = { _, index -> playPlaylist(index) },
            onRemoveClick = { song -> confirmRemove(song) }
        )
        binding.rvPlaylistSongs.apply {
            layoutManager = LinearLayoutManager(this@PlaylistDetailActivity)
            this.adapter = this@PlaylistDetailActivity.adapter
        }
    }

    private fun observeSongs() {
        db.playlistDao().getSongsInPlaylist(playlistId).observe(this) { songs ->
            playlistSongs = songs
            adapter.submitList(songs)
            binding.tvSongCount.text = "${songs.size} songs"
            binding.tvEmptyPlaylist.visibility =
                if (songs.isEmpty()) android.view.View.VISIBLE else android.view.View.GONE
        }
    }

    private fun playPlaylist(startIndex: Int) {
        val songs = playlistSongs.map { ps ->
            Song(
                id = ps.songId,
                title = ps.songTitle,
                artist = ps.songArtist,
                album = "",
                duration = ps.songDuration,
                path = ps.songPath,
                albumId = ps.albumId
            )
        }
        val intent = Intent(this, MusicService::class.java)
        startService(intent)
        musicService?.setSongList(songs, startIndex)
        startActivity(Intent(this, PlayerActivity::class.java))
    }

    private fun confirmRemove(song: PlaylistSong) {
        AlertDialog.Builder(this)
            .setTitle("Remove Song")
            .setMessage("Remove \"${song.songTitle}\" from playlist?")
            .setPositiveButton("Remove") { _, _ ->
                lifecycleScope.launch {
                    db.playlistDao().removeFromPlaylist(playlistId, song.songId)
                    Toast.makeText(this@PlaylistDetailActivity, "Removed", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressedDispatcher.onBackPressed()
        return true
    }

    override fun onDestroy() {
        super.onDestroy()
        if (isBound) unbindService(serviceConnection)
    }
}
