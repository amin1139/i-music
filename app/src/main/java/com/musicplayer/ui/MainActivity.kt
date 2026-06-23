package com.musicplayer.ui

import android.Manifest
import android.content.*
import android.content.pm.PackageManager
import android.database.Cursor
import android.os.*
import android.provider.MediaStore
import android.view.Menu
import android.view.MenuItem
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SearchView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.tabs.TabLayout
import com.musicplayer.R
import com.musicplayer.adapter.SongAdapter
import com.musicplayer.databinding.ActivityMainBinding
import com.musicplayer.model.Song
import com.musicplayer.service.MusicService

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var songAdapter: SongAdapter
    private var allSongs: List<Song> = emptyList()
    private var musicService: MusicService? = null
    private var isBound = false

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as MusicService.MusicBinder
            musicService = binder.getService()
            isBound = true
            observeService()
        }
        override fun onServiceDisconnected(name: ComponentName?) {
            isBound = false
            musicService = null
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)
        supportActionBar?.title = "Music Player"

        setupRecyclerView()
        setupMiniPlayer()
        checkPermissions()
        bindService()
    }

    private fun setupRecyclerView() {
        songAdapter = SongAdapter(
            onSongClick = { song, index ->
                playSong(song, index)
            },
            onSongLongClick = { song ->
                showAddToPlaylistDialog(song)
            }
        )
        binding.rvSongs.apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            adapter = songAdapter
        }
    }

    private fun setupMiniPlayer() {
        binding.miniPlayer.root.setOnClickListener {
            val intent = Intent(this, PlayerActivity::class.java)
            startActivity(intent)
        }

        binding.miniPlayer.btnMiniPlayPause.setOnClickListener {
            musicService?.togglePlayPause()
        }

        binding.miniPlayer.btnMiniNext.setOnClickListener {
            musicService?.playNext()
        }
    }

    private fun bindService() {
        val intent = Intent(this, MusicService::class.java)
        startService(intent)
        bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
    }

    private fun observeService() {
        MusicService.currentSong.observe(this) { song ->
            if (song != null) {
                binding.miniPlayer.root.visibility = android.view.View.VISIBLE
                binding.miniPlayer.tvMiniTitle.text = song.title
                binding.miniPlayer.tvMiniArtist.text = song.artist
                loadAlbumArt(song.albumId, binding.miniPlayer.ivMiniAlbumArt)
            }
        }

        MusicService.isPlaying.observe(this) { playing ->
            val icon = if (playing) R.drawable.ic_pause else R.drawable.ic_play
            binding.miniPlayer.btnMiniPlayPause.setImageResource(icon)
        }
    }

    private fun playSong(song: Song, index: Int) {
        val intent = Intent(this, MusicService::class.java)
        startService(intent)
        if (!isBound) {
            bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
        }
        musicService?.setSongList(allSongs, index)
        val playerIntent = Intent(this, PlayerActivity::class.java)
        startActivity(playerIntent)
    }

    private fun showAddToPlaylistDialog(song: Song) {
        val intent = Intent(this, PlaylistActivity::class.java).apply {
            putExtra("SONG_TO_ADD", song)
        }
        startActivity(intent)
    }

    private fun loadAlbumArt(albumId: Long, imageView: android.widget.ImageView) {
        try {
            val uri = android.net.Uri.parse("content://media/external/audio/albumart/$albumId")
            com.bumptech.glide.Glide.with(this)
                .load(uri)
                .placeholder(R.drawable.ic_music_note_large)
                .error(R.drawable.ic_music_note_large)
                .centerCrop()
                .into(imageView)
        } catch (e: Exception) {
            imageView.setImageResource(R.drawable.ic_music_note_large)
        }
    }

    private fun checkPermissions() {
        val permissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            arrayOf(Manifest.permission.READ_MEDIA_AUDIO)
        } else {
            arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
        }

        val notGranted = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (notGranted.isEmpty()) {
            loadSongs()
        } else {
            ActivityCompat.requestPermissions(this, notGranted.toTypedArray(), 100)
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 100 && grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
            loadSongs()
        } else {
            Toast.makeText(this, "Storage permission needed to load music", Toast.LENGTH_LONG).show()
        }
    }

    private fun loadSongs() {
        val songs = mutableListOf<Song>()
        val uri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.DATA,
            MediaStore.Audio.Media.ALBUM_ID
        )
        val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0 AND ${MediaStore.Audio.Media.DURATION} > 30000"
        val sortOrder = "${MediaStore.Audio.Media.TITLE} ASC"

        val cursor: Cursor? = contentResolver.query(uri, projection, selection, null, sortOrder)
        cursor?.use {
            val idCol = it.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val titleCol = it.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
            val artistCol = it.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
            val albumCol = it.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
            val durationCol = it.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
            val dataCol = it.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA)
            val albumIdCol = it.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)

            while (it.moveToNext()) {
                songs.add(
                    Song(
                        id = it.getLong(idCol),
                        title = it.getString(titleCol) ?: "Unknown",
                        artist = it.getString(artistCol) ?: "Unknown Artist",
                        album = it.getString(albumCol) ?: "Unknown Album",
                        duration = it.getLong(durationCol),
                        path = it.getString(dataCol) ?: "",
                        albumId = it.getLong(albumIdCol)
                    )
                )
            }
        }

        allSongs = songs
        songAdapter.submitList(songs)

        if (songs.isEmpty()) {
            binding.tvEmptyState.visibility = android.view.View.VISIBLE
            binding.rvSongs.visibility = android.view.View.GONE
        } else {
            binding.tvEmptyState.visibility = android.view.View.GONE
            binding.rvSongs.visibility = android.view.View.VISIBLE
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.main_menu, menu)
        val searchItem = menu.findItem(R.id.action_search)
        val searchView = searchItem.actionView as SearchView
        searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?) = false
            override fun onQueryTextChange(newText: String?): Boolean {
                filterSongs(newText ?: "")
                return true
            }
        })
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_playlist -> {
                startActivity(Intent(this, PlaylistActivity::class.java))
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun filterSongs(query: String) {
        val filtered = if (query.isEmpty()) {
            allSongs
        } else {
            allSongs.filter {
                it.title.contains(query, true) || it.artist.contains(query, true)
            }
        }
        songAdapter.submitList(filtered)
    }

    override fun onDestroy() {
        super.onDestroy()
        if (isBound) {
            unbindService(serviceConnection)
            isBound = false
        }
    }
}
