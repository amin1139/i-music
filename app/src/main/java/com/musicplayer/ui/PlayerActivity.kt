package com.musicplayer.ui

import android.content.*
import android.net.Uri
import android.os.Bundle
import android.os.IBinder
import android.widget.SeekBar
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Observer
import com.bumptech.glide.Glide
import com.musicplayer.R
import com.musicplayer.databinding.ActivityPlayerBinding
import com.musicplayer.model.Song
import com.musicplayer.service.MusicService

class PlayerActivity : AppCompatActivity() {

    private lateinit var binding: ActivityPlayerBinding
    private var musicService: MusicService? = null
    private var isBound = false
    private var isSeekBarTracking = false

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as MusicService.MusicBinder
            musicService = binder.getService()
            isBound = true
        }
        override fun onServiceDisconnected(name: ComponentName?) {
            isBound = false
            musicService = null
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPlayerBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = ""

        bindMusicService()
        observeLiveData()
        setupControls()
    }

    private fun bindMusicService() {
        val intent = Intent(this, MusicService::class.java)
        bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
    }

    private fun observeLiveData() {
        MusicService.currentSong.observe(this) { song ->
            song?.let { updateUI(it) }
        }

        MusicService.isPlaying.observe(this) { playing ->
            binding.btnPlayPause.setImageResource(
                if (playing) R.drawable.ic_pause_large else R.drawable.ic_play_large
            )
        }

        MusicService.currentPosition.observe(this) { position ->
            if (!isSeekBarTracking) {
                binding.seekBar.progress = position
                binding.tvCurrentTime.text = formatTime(position)
            }
        }

        MusicService.duration.observe(this) { dur ->
            binding.seekBar.max = dur
            binding.tvTotalTime.text = formatTime(dur)
        }

        MusicService.shuffleMode.observe(this) { shuffled ->
            binding.btnShuffle.alpha = if (shuffled) 1f else 0.4f
        }

        MusicService.repeatMode.observe(this) { mode ->
            binding.btnRepeat.alpha = if (mode == 0) 0.4f else 1f
            binding.btnRepeat.setImageResource(
                if (mode == 2) R.drawable.ic_repeat_one else R.drawable.ic_repeat
            )
        }
    }

    private fun setupControls() {
        binding.btnPlayPause.setOnClickListener {
            musicService?.togglePlayPause()
        }

        binding.btnNext.setOnClickListener {
            musicService?.playNext()
        }

        binding.btnPrevious.setOnClickListener {
            musicService?.playPrevious()
        }

        binding.btnSkipForward.setOnClickListener {
            musicService?.skipForward()
        }

        binding.btnSkipBackward.setOnClickListener {
            musicService?.skipBackward()
        }

        binding.btnShuffle.setOnClickListener {
            val current = MusicService.shuffleMode.value ?: false
            musicService?.setShuffleMode(!current)
        }

        binding.btnRepeat.setOnClickListener {
            val current = MusicService.repeatMode.value ?: 0
            val next = (current + 1) % 3
            musicService?.setRepeatMode(next)
        }

        binding.seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    binding.tvCurrentTime.text = formatTime(progress)
                }
            }
            override fun onStartTrackingTouch(seekBar: SeekBar) {
                isSeekBarTracking = true
            }
            override fun onStopTrackingTouch(seekBar: SeekBar) {
                musicService?.seekTo(seekBar.progress)
                isSeekBarTracking = false
            }
        })
    }

    private fun updateUI(song: Song) {
        binding.tvSongTitle.text = song.title
        binding.tvArtistName.text = song.artist
        binding.tvAlbumName.text = song.album
        loadAlbumArt(song.albumId)
    }

    private fun loadAlbumArt(albumId: Long) {
        val uri = Uri.parse("content://media/external/audio/albumart/$albumId")
        Glide.with(this)
            .load(uri)
            .placeholder(R.drawable.ic_music_note_large)
            .error(R.drawable.ic_music_note_large)
            .centerCrop()
            .into(binding.ivAlbumArt)
    }

    private fun formatTime(ms: Int): String {
        val seconds = ms / 1000
        val minutes = seconds / 60
        val secs = seconds % 60
        return String.format("%d:%02d", minutes, secs)
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressedDispatcher.onBackPressed()
        return true
    }

    override fun onDestroy() {
        super.onDestroy()
        if (isBound) {
            unbindService(serviceConnection)
            isBound = false
        }
    }
}
