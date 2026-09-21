package com.musicplayer.util

import android.content.Context
import com.musicplayer.model.Song
import org.json.JSONArray
import org.json.JSONObject

/**
 * Lightweight persistence for "resume last played" support.
 *
 * Deliberately backed by SharedPreferences rather than Room: this is a single,
 * frequently-overwritten record (not queryable data), so a full DB entity/DAO/migration
 * would be overkill. Writes are cheap enough to call from a position-tracking loop.
 */
object PlaybackStateStore {

    private const val PREFS_NAME = "playback_state"

    private const val KEY_SONG_ID = "song_id"
    private const val KEY_TITLE = "title"
    private const val KEY_ARTIST = "artist"
    private const val KEY_ALBUM = "album"
    private const val KEY_PATH = "path"
    private const val KEY_ALBUM_ID = "album_id"
    private const val KEY_DURATION = "duration"
    private const val KEY_POSITION = "position_ms"

    private const val KEY_QUEUE_JSON = "queue_json"
    private const val KEY_QUEUE_INDEX = "queue_index"
    private const val KEY_QUEUE_SHUFFLE = "queue_shuffle"
    private const val KEY_QUEUE_REPEAT = "queue_repeat"

    data class SavedPlaybackState(val song: Song, val positionMs: Int)

    data class SavedQueueState(
        val songs: List<Song>,
        val currentIndex: Int,
        val shuffleEnabled: Boolean,
        val repeatMode: Int
    )

    fun save(context: Context, song: Song, positionMs: Int) {
        prefs(context).edit()
            .putLong(KEY_SONG_ID, song.id)
            .putString(KEY_TITLE, song.title)
            .putString(KEY_ARTIST, song.artist)
            .putString(KEY_ALBUM, song.album)
            .putString(KEY_PATH, song.path)
            .putLong(KEY_ALBUM_ID, song.albumId)
            .putLong(KEY_DURATION, song.duration)
            .putInt(KEY_POSITION, positionMs.coerceAtLeast(0))
            .apply()
    }

    fun load(context: Context): SavedPlaybackState? {
        val p = prefs(context)
        val path = p.getString(KEY_PATH, null)
        val songId = p.getLong(KEY_SONG_ID, -1L)
        if (path.isNullOrEmpty() || songId == -1L) return null

        val song = Song(
            id = songId,
            title = p.getString(KEY_TITLE, null) ?: "Unknown",
            artist = p.getString(KEY_ARTIST, null) ?: "Unknown Artist",
            album = p.getString(KEY_ALBUM, null) ?: "Unknown Album",
            duration = p.getLong(KEY_DURATION, 0L),
            path = path,
            albumId = p.getLong(KEY_ALBUM_ID, -1L)
        )
        val position = p.getInt(KEY_POSITION, 0)
        return SavedPlaybackState(song, position)
    }

    /**
     * Persists the full playback queue (so Next/Previous, shuffle and repeat keep working
     * correctly after a restore), separately from the frequently-updated current
     * song/position above. Call only when the queue itself, its position, or the
     * shuffle/repeat mode actually change — not from a hot playback-tracking loop.
     */
    fun saveQueue(
        context: Context,
        songs: List<Song>,
        currentIndex: Int,
        shuffleEnabled: Boolean,
        repeatMode: Int
    ) {
        val array = JSONArray()
        for (song in songs) {
            val obj = JSONObject()
            obj.put("id", song.id)
            obj.put("title", song.title)
            obj.put("artist", song.artist)
            obj.put("album", song.album)
            obj.put("path", song.path)
            obj.put("albumId", song.albumId)
            obj.put("duration", song.duration)
            array.put(obj)
        }

        prefs(context).edit()
            .putString(KEY_QUEUE_JSON, array.toString())
            .putInt(KEY_QUEUE_INDEX, currentIndex)
            .putBoolean(KEY_QUEUE_SHUFFLE, shuffleEnabled)
            .putInt(KEY_QUEUE_REPEAT, repeatMode)
            .apply()
    }

    fun loadQueue(context: Context): SavedQueueState? {
        val p = prefs(context)
        val json = p.getString(KEY_QUEUE_JSON, null) ?: return null

        return try {
            val array = JSONArray(json)
            val songs = mutableListOf<Song>()
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                songs.add(
                    Song(
                        id = obj.getLong("id"),
                        title = obj.getString("title"),
                        artist = obj.getString("artist"),
                        album = obj.getString("album"),
                        duration = obj.getLong("duration"),
                        path = obj.getString("path"),
                        albumId = obj.getLong("albumId")
                    )
                )
            }
            if (songs.isEmpty()) return null

            SavedQueueState(
                songs = songs,
                currentIndex = p.getInt(KEY_QUEUE_INDEX, 0),
                shuffleEnabled = p.getBoolean(KEY_QUEUE_SHUFFLE, false),
                repeatMode = p.getInt(KEY_QUEUE_REPEAT, 0)
            )
        } catch (e: Exception) {
            null
        }
    }

    fun clear(context: Context) {
        prefs(context).edit().clear().apply()
    }

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
}
