package com.musicplayer.service

import android.app.*
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.net.Uri
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import android.support.v4.media.MediaMetadataCompat
import androidx.core.app.NotificationCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.MutableLiveData
import com.musicplayer.R
import com.musicplayer.model.Song
import com.musicplayer.ui.PlayerActivity

class MusicService : LifecycleService() {

    companion object {
        const val ACTION_PLAY_PAUSE = "com.musicplayer.PLAY_PAUSE"
        const val ACTION_NEXT = "com.musicplayer.NEXT"
        const val ACTION_PREVIOUS = "com.musicplayer.PREVIOUS"
        const val ACTION_STOP = "com.musicplayer.STOP"
        const val CHANNEL_ID = "music_player_channel"
        const val NOTIFICATION_ID = 101

        // LiveData for UI updates
        val currentSong = MutableLiveData<Song?>()
        val isPlaying = MutableLiveData<Boolean>(false)
        val currentPosition = MutableLiveData<Int>(0)
        val duration = MutableLiveData<Int>(0)
        val songList = MutableLiveData<List<Song>>(emptyList())
        val currentIndex = MutableLiveData<Int>(0)
        val shuffleMode = MutableLiveData<Boolean>(false)
        val repeatMode = MutableLiveData<Int>(0) // 0=off, 1=all, 2=one
    }

    private val binder = MusicBinder()
    private var mediaPlayer: MediaPlayer? = null
    private var audioManager: AudioManager? = null
    private var audioFocusRequest: AudioFocusRequest? = null
    private var mediaSession: MediaSessionCompat? = null
    private var songs: List<Song> = emptyList()
    private var currentSongIndex = 0
    private var shuffleEnabled = false
    private var repeatModeValue = 0
    private var shuffledIndices: MutableList<Int> = mutableListOf()

    inner class MusicBinder : Binder() {
        fun getService(): MusicService = this@MusicService
    }

    private val notificationReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                ACTION_PLAY_PAUSE -> togglePlayPause()
                ACTION_NEXT -> playNext()
                ACTION_PREVIOUS -> playPrevious()
                ACTION_STOP -> stopSelf()
            }
        }
    }

    private val audioFocusListener = AudioManager.OnAudioFocusChangeListener { focus ->
        when (focus) {
            AudioManager.AUDIOFOCUS_LOSS -> pauseMusic()
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> pauseMusic()
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> mediaPlayer?.setVolume(0.3f, 0.3f)
            AudioManager.AUDIOFOCUS_GAIN -> {
                mediaPlayer?.setVolume(1f, 1f)
                resumeMusic()
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        setupAudioManager()
        setupMediaSession()
        registerReceiver()
    }

    override fun onBind(intent: Intent): IBinder {
        super.onBind(intent)
        return binder
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        return START_STICKY
    }

    private fun setupAudioManager() {
        audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
    }

    private fun setupMediaSession() {
        mediaSession = MediaSessionCompat(this, "MusicPlayer").apply {
            setCallback(object : MediaSessionCompat.Callback() {
                override fun onPlay() { resumeMusic() }
                override fun onPause() { pauseMusic() }
                override fun onSkipToNext() { playNext() }
                override fun onSkipToPrevious() { playPrevious() }
                override fun onStop() { stopSelf() }
                override fun onSeekTo(pos: Long) { seekTo(pos.toInt()) }
            })
            isActive = true
        }
    }

    private fun registerReceiver() {
        val filter = IntentFilter().apply {
            addAction(ACTION_PLAY_PAUSE)
            addAction(ACTION_NEXT)
            addAction(ACTION_PREVIOUS)
            addAction(ACTION_STOP)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(notificationReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(notificationReceiver, filter)
        }
    }

    fun setSongList(list: List<Song>, index: Int = 0) {
        songs = list
        currentSongIndex = index
        songList.postValue(list)
        currentIndex.postValue(index)
        shuffledIndices = (list.indices).toMutableList()
        playSong(songs[currentSongIndex])
    }

    fun playSong(song: Song) {
        try {
            releaseMediaPlayer()
            if (!requestAudioFocus()) return

            mediaPlayer = MediaPlayer().apply {
                setWakeMode(applicationContext, PowerManager.PARTIAL_WAKE_LOCK)
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build()
                )
                setDataSource(applicationContext, Uri.parse(song.path))
                prepare()
                start()

                setOnCompletionListener {
                    when (repeatModeValue) {
                        2 -> playSong(song) // repeat one
                        else -> playNext()
                    }
                }

                setOnErrorListener { _, _, _ ->
                    playNext()
                    true
                }
            }

            currentSong.postValue(song)
            isPlaying.postValue(true)
            duration.postValue(mediaPlayer?.duration ?: 0)
            updateMediaSession(song)
            buildAndShowNotification(song)
            startPositionUpdater()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun requestAudioFocus(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                .setOnAudioFocusChangeListener(audioFocusListener)
                .build()
            audioFocusRequest = request
            audioManager?.requestAudioFocus(request) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        } else {
            @Suppress("DEPRECATION")
            audioManager?.requestAudioFocus(
                audioFocusListener,
                AudioManager.STREAM_MUSIC,
                AudioManager.AUDIOFOCUS_GAIN
            ) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        }
    }

    fun togglePlayPause() {
        if (mediaPlayer?.isPlaying == true) pauseMusic() else resumeMusic()
    }

    fun pauseMusic() {
        mediaPlayer?.pause()
        isPlaying.postValue(false)
        currentSong.value?.let { buildAndShowNotification(it) }
        updatePlaybackState(PlaybackStateCompat.STATE_PAUSED)
    }

    fun resumeMusic() {
        if (mediaPlayer != null) {
            mediaPlayer?.start()
            isPlaying.postValue(true)
            currentSong.value?.let { buildAndShowNotification(it) }
            updatePlaybackState(PlaybackStateCompat.STATE_PLAYING)
        }
    }

    fun playNext() {
        if (songs.isEmpty()) return
        currentSongIndex = getNextIndex()
        currentIndex.postValue(currentSongIndex)
        playSong(songs[currentSongIndex])
    }

    fun playPrevious() {
        if (songs.isEmpty()) return
        val pos = mediaPlayer?.currentPosition ?: 0
        if (pos > 3000) {
            seekTo(0)
            return
        }
        currentSongIndex = getPreviousIndex()
        currentIndex.postValue(currentSongIndex)
        playSong(songs[currentSongIndex])
    }

    private fun getNextIndex(): Int {
        return if (shuffleEnabled) {
            (0 until songs.size).random()
        } else {
            if (repeatModeValue == 1) {
                (currentSongIndex + 1) % songs.size
            } else {
                if (currentSongIndex < songs.size - 1) currentSongIndex + 1 else 0
            }
        }
    }

    private fun getPreviousIndex(): Int {
        return if (shuffleEnabled) {
            (0 until songs.size).random()
        } else {
            if (currentSongIndex > 0) currentSongIndex - 1 else songs.size - 1
        }
    }

    fun seekTo(position: Int) {
        mediaPlayer?.seekTo(position)
        currentPosition.postValue(position)
    }

    fun skipForward() {
        val newPos = (mediaPlayer?.currentPosition ?: 0) + 10000
        val max = mediaPlayer?.duration ?: 0
        seekTo(newPos.coerceAtMost(max))
    }

    fun skipBackward() {
        val newPos = (mediaPlayer?.currentPosition ?: 0) - 10000
        seekTo(newPos.coerceAtLeast(0))
    }

    fun getCurrentPosition(): Int = mediaPlayer?.currentPosition ?: 0
    fun getDurationMs(): Int = mediaPlayer?.duration ?: 0
    fun isCurrentlyPlaying(): Boolean = mediaPlayer?.isPlaying ?: false

    fun setShuffleMode(enabled: Boolean) {
        shuffleEnabled = enabled
        shuffleMode.postValue(enabled)
    }

    fun setRepeatMode(mode: Int) {
        repeatModeValue = mode
        repeatMode.postValue(mode)
    }

    private var positionRunnable: Runnable? = null
    private val handler = android.os.Handler(android.os.Looper.getMainLooper())

    private fun startPositionUpdater() {
        positionRunnable?.let { handler.removeCallbacks(it) }
        positionRunnable = object : Runnable {
            override fun run() {
                if (mediaPlayer?.isPlaying == true) {
                    currentPosition.postValue(mediaPlayer?.currentPosition ?: 0)
                }
                handler.postDelayed(this, 500)
            }
        }
        handler.post(positionRunnable!!)
    }

    private fun updateMediaSession(song: Song) {
        mediaSession?.setMetadata(
            MediaMetadataCompat.Builder()
                .putString(MediaMetadataCompat.METADATA_KEY_TITLE, song.title)
                .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, song.artist)
                .putString(MediaMetadataCompat.METADATA_KEY_ALBUM, song.album)
                .putLong(MediaMetadataCompat.METADATA_KEY_DURATION, song.duration)
                .build()
        )
        updatePlaybackState(PlaybackStateCompat.STATE_PLAYING)
    }

    private fun updatePlaybackState(state: Int) {
        mediaSession?.setPlaybackState(
            PlaybackStateCompat.Builder()
                .setState(state, mediaPlayer?.currentPosition?.toLong() ?: 0L, 1f)
                .setActions(
                    PlaybackStateCompat.ACTION_PLAY or
                    PlaybackStateCompat.ACTION_PAUSE or
                    PlaybackStateCompat.ACTION_SKIP_TO_NEXT or
                    PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS or
                    PlaybackStateCompat.ACTION_SEEK_TO
                )
                .build()
        )
    }

    private fun buildAndShowNotification(song: Song) {
        val playing = mediaPlayer?.isPlaying ?: false

        val playerIntent = Intent(this, PlayerActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this, 0, playerIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val playPausePending = createActionPendingIntent(ACTION_PLAY_PAUSE, 1)
        val nextPending = createActionPendingIntent(ACTION_NEXT, 2)
        val prevPending = createActionPendingIntent(ACTION_PREVIOUS, 3)
        val stopPending = createActionPendingIntent(ACTION_STOP, 4)

        val playPauseIcon = if (playing) R.drawable.ic_pause_notif else R.drawable.ic_play_notif
        val playPauseLabel = if (playing) "Pause" else "Play"

        val albumArt = getAlbumArt(song.albumId)

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(song.title)
            .setContentText(song.artist)
            .setSubText(song.album)
            .setSmallIcon(R.drawable.ic_music_note)
            .setLargeIcon(albumArt)
            .setContentIntent(pendingIntent)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOnlyAlertOnce(true)
            .setOngoing(playing)
            .addAction(R.drawable.ic_previous_notif, "Previous", prevPending)
            .addAction(playPauseIcon, playPauseLabel, playPausePending)
            .addAction(R.drawable.ic_next_notif, "Next", nextPending)
            .addAction(R.drawable.ic_stop_notif, "Stop", stopPending)
            .setStyle(
                androidx.media.app.NotificationCompat.MediaStyle()
                    .setMediaSession(mediaSession?.sessionToken)
                    .setShowActionsInCompactView(0, 1, 2)
            )
            .build()

        startForeground(NOTIFICATION_ID, notification)
    }

    private fun createActionPendingIntent(action: String, requestCode: Int): PendingIntent {
        val intent = Intent(action)
        return PendingIntent.getBroadcast(
            this, requestCode, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun getAlbumArt(albumId: Long): Bitmap? {
        return try {
            val uri = Uri.parse("content://media/external/audio/albumart/$albumId")
            contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it)
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Music Playback",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Music Player Controls"
                setShowBadge(false)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun releaseMediaPlayer() {
        mediaPlayer?.apply {
            if (isPlaying) stop()
            release()
        }
        mediaPlayer = null
    }

    override fun onDestroy() {
        super.onDestroy()
        positionRunnable?.let { handler.removeCallbacks(it) }
        releaseMediaPlayer()
        mediaSession?.release()
        try { unregisterReceiver(notificationReceiver) } catch (_: Exception) {}
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            audioFocusRequest?.let { audioManager?.abandonAudioFocusRequest(it) }
        }
        isPlaying.postValue(false)
        currentSong.postValue(null)
    }
}
