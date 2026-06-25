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
import androidx.lifecycle.lifecycleScope
import com.google.android.material.tabs.TabLayout
import com.musicplayer.R
import com.musicplayer.adapter.SongAdapter
import com.musicplayer.adapter.FolderAdapter
import com.musicplayer.databinding.ActivityMainBinding
import com.musicplayer.db.AppDatabase
import com.musicplayer.model.Folder
import com.musicplayer.model.FavouriteSong
import com.musicplayer.model.Song
import com.musicplayer.service.MusicService
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var songAdapter: SongAdapter
    private lateinit var folderAdapter: FolderAdapter
    private var allSongs: List<Song> = emptyList()
    private var allFolders: List<Folder> = emptyList()
    private var favouriteIds: MutableSet<Long> = mutableSetOf()
    private var musicService: MusicService? = null
    private var isBound = false

    // 0 = All Songs, 1 = Favourites, 2 = Folders (list), 3 = Folders (inside a folder)
    private var currentTab = 0
    private var currentFolder: Folder? = null

    private val db by lazy { AppDatabase.getInstance(this) }

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

        setupTabs()
        setupRecyclerView()
        setupFolderRecyclerView()
        setupMiniPlayer()
        loadFavouriteIds()
        checkPermissions()
        bindService()
    }

    private fun setupTabs() {
        binding.tabLayout.addTab(binding.tabLayout.newTab().setText("All Songs"))
        binding.tabLayout.addTab(binding.tabLayout.newTab().setText("Favourites"))
        binding.tabLayout.addTab(binding.tabLayout.newTab().setText("Folders"))

        binding.tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab) {
                currentFolder = null
                currentTab = tab.position
                refreshCurrentTab()
            }
            override fun onTabUnselected(tab: TabLayout.Tab) {}
            override fun onTabReselected(tab: TabLayout.Tab) {}
        })
    }

    private fun setupRecyclerView() {
        songAdapter = SongAdapter(
            onSongClick = { song, index, albumArtView ->
                playSong(song, index, albumArtView)
            },
            onSongLongClick = { song ->
                showAddToPlaylistDialog(song)
            },
            onFavouriteClick = { song, heartView ->
                toggleFavourite(song, heartView)
            },
            isFavourite = { songId ->
                favouriteIds.contains(songId)
            }
        )
        binding.rvSongs.apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            adapter = songAdapter
        }
    }

    private fun setupFolderRecyclerView() {
        folderAdapter = FolderAdapter(
            onFolderClick = { folder ->
                currentFolder = folder
                showSongsList(folder.songs)
            }
        )
        binding.rvFolders.apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            adapter = folderAdapter
        }
    }

    private fun loadFavouriteIds() {
        lifecycleScope.launch {
            favouriteIds = db.playlistDao().getAllFavouriteIds().toMutableSet()
            songAdapter.notifyDataSetChanged()
        }
    }

    private fun toggleFavourite(song: Song, heartView: android.view.View?) {
        heartView?.let { animateHeartPop(it) }
        lifecycleScope.launch {
            if (favouriteIds.contains(song.id)) {
                db.playlistDao().removeFavourite(song.id)
                favouriteIds.remove(song.id)
            } else {
                db.playlistDao().insertFavourite(
                    FavouriteSong(
                        songId = song.id,
                        songTitle = song.title,
                        songArtist = song.artist,
                        songPath = song.path,
                        songDuration = song.duration,
                        albumId = song.albumId
                    )
                )
                favouriteIds.add(song.id)
            }
            songAdapter.notifyDataSetChanged()
            if (currentTab == 1) {
                refreshCurrentTab()
            }
        }
    }

    private fun animateHeartPop(view: android.view.View) {
        view.animate()
            .scaleX(1.4f)
            .scaleY(1.4f)
            .setDuration(140)
            .withEndAction {
                view.animate()
                    .scaleX(1f)
                    .scaleY(1f)
                    .setDuration(180)
                    .setInterpolator(android.view.animation.OvershootInterpolator(3f))
                    .start()
            }
            .start()
    }

    private fun refreshCurrentTab() {
        when (currentTab) {
            0 -> showSongsList(allSongs)
            1 -> showSongsList(allSongs.filter { favouriteIds.contains(it.id) })
            2 -> showFoldersList()
        }
    }

    private fun showSongsList(songs: List<Song>) {
        binding.rvFolders.visibility = android.view.View.GONE
        binding.rvSongs.visibility = android.view.View.VISIBLE
        songAdapter.submitList(songs) {
            binding.rvSongs.scheduleLayoutAnimation()
        }

        if (songs.isEmpty()) {
            binding.tvEmptyState.visibility = android.view.View.VISIBLE
            binding.rvSongs.visibility = android.view.View.GONE
        } else {
            binding.tvEmptyState.visibility = android.view.View.GONE
        }
    }

    private fun showFoldersList() {
        binding.rvSongs.visibility = android.view.View.GONE
        binding.rvFolders.visibility = android.view.View.VISIBLE
        folderAdapter.submitList(allFolders) {
            binding.rvFolders.scheduleLayoutAnimation()
        }

        if (allFolders.isEmpty()) {
            binding.tvEmptyState.visibility = android.view.View.VISIBLE
            binding.rvFolders.visibility = android.view.View.GONE
        } else {
            binding.tvEmptyState.visibility = android.view.View.GONE
        }
    }

    private fun setupMiniPlayer() {
        binding.miniPlayer.root.setOnClickListener {
            val intent = Intent(this, PlayerActivity::class.java)
            intent.putExtra(PlayerActivity.EXTRA_HAS_SHARED_ELEMENT, true)
            val options = androidx.core.app.ActivityOptionsCompat.makeSceneTransitionAnimation(
                this, binding.miniPlayer.ivMiniAlbumArt, "albumArtTransition"
            )
            startActivity(intent, options.toBundle())
        }

        binding.miniPlayer.btnMiniPlayPause.setOnClickListener {
            it.animate().scaleX(0.8f).scaleY(0.8f).setDuration(80).withEndAction {
                it.animate().scaleX(1f).scaleY(1f).setDuration(120)
                    .setInterpolator(android.view.animation.OvershootInterpolator(2f)).start()
            }.start()
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

    private fun playSong(song: Song, index: Int, albumArtView: android.view.View?) {
        val intent = Intent(this, MusicService::class.java)
        startService(intent)
        if (!isBound) {
            bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
        }
        val listForPlayback = currentFolder?.songs ?: when (currentTab) {
            1 -> allSongs.filter { favouriteIds.contains(it.id) }
            else -> allSongs
        }
        musicService?.setSongList(listForPlayback, listForPlayback.indexOf(song))
        val playerIntent = Intent(this, PlayerActivity::class.java)

        if (albumArtView != null) {
            playerIntent.putExtra(PlayerActivity.EXTRA_HAS_SHARED_ELEMENT, true)
            val options = androidx.core.app.ActivityOptionsCompat.makeSceneTransitionAnimation(
                this, albumArtView, "albumArtTransition"
            )
            startActivity(playerIntent, options.toBundle())
        } else {
            startActivity(playerIntent)
            overridePendingTransition(R.anim.slide_up_enter, R.anim.stay_dim)
        }
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
        allFolders = buildFolders(songs)
        refreshCurrentTab()
    }

    private fun buildFolders(songs: List<Song>): List<Folder> {
        return songs
            .filter { it.path.isNotEmpty() }
            .groupBy { song ->
                val lastSlash = song.path.lastIndexOf('/')
                if (lastSlash > 0) song.path.substring(0, lastSlash) else "/"
            }
            .map { (folderPath, songsInFolder) ->
                val folderName = folderPath.substringAfterLast('/').ifEmpty { "Root" }
                Folder(
                    name = folderName,
                    path = folderPath,
                    songCount = songsInFolder.size,
                    songs = songsInFolder
                )
            }
            .sortedBy { it.name.lowercase() }
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
        if (currentFolder != null || currentTab == 2) return
        val baseList = if (currentTab == 1) allSongs.filter { favouriteIds.contains(it.id) } else allSongs
        val filtered = if (query.isEmpty()) {
            baseList
        } else {
            baseList.filter {
                it.title.contains(query, true) || it.artist.contains(query, true)
            }
        }
        songAdapter.submitList(filtered)
    }

    override fun onBackPressed() {
        if (currentFolder != null) {
            currentFolder = null
            showFoldersList()
        } else {
            super.onBackPressed()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (isBound) {
            unbindService(serviceConnection)
            isBound = false
        }
    }
}
