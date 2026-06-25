package com.musicplayer.ui

import android.animation.Animator
import android.animation.AnimatorInflater
import android.animation.ObjectAnimator
import android.content.*
import android.net.Uri
import android.os.Bundle
import android.os.IBinder
import android.widget.SeekBar
import androidx.appcompat.app.AppCompatActivity
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
    private var enteredWithSharedElement = false

    private var albumRotator: ObjectAnimator? = null
    private var glowPulser: Animator? = null

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

        enteredWithSharedElement = intent.getBooleanExtra(EXTRA_HAS_SHARED_ELEMENT, false)

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = ""

        setupRotationAnimator()
        setupGlowPulse()
        bindMusicService()
        observeLiveData()
        setupControls()
        animateEntrance()
    }

    private fun setupRotationAnimator() {
        albumRotator = AnimatorInflater.loadAnimator(
            this, R.animator.rotate_album_art
        ) as ObjectAnimator
        albumRotator?.setTarget(binding.cardAlbumArt)
    }

    private fun setupGlowPulse() {
        glowPulser = AnimatorInflater.loadAnimator(this, R.animator.pulse_glow)
        glowPulser?.setTarget(binding.ivPlayGlow)
    }

    private fun animateEntrance() {
        if (!enteredWithSharedElement) {
            binding.albumArtContainer.alpha = 0f
            binding.albumArtContainer.scaleX = 0.85f
            binding.albumArtContainer.scaleY = 0.85f
            binding.albumArtContainer.animate()
                .alpha(1f)
                .scaleX(1f)
                .scaleY(1f)
                .setDuration(380)
                .setStartDelay(60)
                .setInterpolator(android.view.animation.OvershootInterpolator(0.6f))
                .start()
        }

        binding.controlsContainer.alpha = 0f
        binding.controlsContainer.translationY = 40f
        binding.controlsContainer.animate()
            .alpha(1f)
            .translationY(0f)
            .setDuration(320)
            .setStartDelay(140)
            .start()
    }

    private fun playRotation(playing: Boolean) {
        val rotator = albumRotator ?: return
        if (playing) {
            if (!rotator.isStarted) {
                rotator.start()
            } else if (rotator.isPaused) {
                rotator.resume()
            }
        } else {
            if (rotator.isStarted && !rotator.isPaused) {
                rotator.pause()
            }
        }
    }

    private fun playGlowPulse(playing: Boolean) {
        val pulser = glowPulser ?: return
        if (playing) {
            if (!pulser.isStarted) pulser.start() else if (pulser.isPaused) pulser.resume()
        } else {
            binding.ivPlayGlow.animate().alpha(0.25f).scaleX(1f).scaleY(1f).setDuration(250).start()
            if (pulser.isStarted) pulser.pause()
        }
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
            playRotation(playing)
            playGlowPulse(playing)
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
            animateToggle(binding.btnShuffle, shuffled)
        }

        MusicService.repeatMode.observe(this) { mode ->
            animateToggle(binding.btnRepeat, mode != 0)
            binding.btnRepeat.setImageResource(
                if (mode == 2) R.drawable.ic_repeat_one else R.drawable.ic_repeat
            )
        }
    }

    private fun animateToggle(view: android.view.View, active: Boolean) {
        val targetAlpha = if (active) 1f else 0.4f
        view.animate()
            .alpha(targetAlpha)
            .scaleX(if (active) 1.15f else 1f)
            .scaleY(if (active) 1.15f else 1f)
            .setDuration(220)
            .withEndAction {
                if (active) {
                    view.animate().scaleX(1f).scaleY(1f).setDuration(150).start()
                }
            }
            .start()
    }

    private fun setupControls() {
        binding.btnPlayPause.setOnClickListener {
            bounceButton(it)
            musicService?.togglePlayPause()
        }

        binding.btnNext.setOnClickListener {
            bounceButton(it)
            musicService?.playNext()
        }

        binding.btnPrevious.setOnClickListener {
            bounceButton(it)
            musicService?.playPrevious()
        }

        binding.btnSkipForward.setOnClickListener {
            bounceButton(it)
            musicService?.skipForward()
        }

        binding.btnSkipBackward.setOnClickListener {
            bounceButton(it)
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

    private fun bounceButton(view: android.view.View) {
        view.animate()
            .scaleX(0.85f)
            .scaleY(0.85f)
            .setDuration(80)
            .withEndAction {
                view.animate().scaleX(1f).scaleY(1f).setDuration(120)
                    .setInterpolator(android.view.animation.OvershootInterpolator(2f))
                    .start()
            }
            .start()
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

    override fun finish() {
        super.finish()
        if (!enteredWithSharedElement) {
            overridePendingTransition(R.anim.stay_brighten, R.anim.slide_down_exit)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        albumRotator?.cancel()
        glowPulser?.cancel()
        if (isBound) {
            unbindService(serviceConnection)
            isBound = false
        }
    }

    companion object {
        const val EXTRA_HAS_SHARED_ELEMENT = "EXTRA_HAS_SHARED_ELEMENT"
    }
}
