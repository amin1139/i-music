package com.musicplayer.adapter

import android.net.Uri
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.musicplayer.R
import com.musicplayer.databinding.ItemPlaylistSongBinding
import com.musicplayer.model.PlaylistSong

class PlaylistSongAdapter(
    private val onSongClick: (PlaylistSong, Int) -> Unit,
    private val onRemoveClick: (PlaylistSong) -> Unit
) : ListAdapter<PlaylistSong, PlaylistSongAdapter.ViewHolder>(Diff()) {

    inner class ViewHolder(private val binding: ItemPlaylistSongBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(song: PlaylistSong, position: Int) {
            binding.tvSongTitle.text = song.songTitle
            binding.tvArtistName.text = song.songArtist

            val minutes = (song.songDuration / 1000) / 60
            val seconds = (song.songDuration / 1000) % 60
            binding.tvDuration.text = String.format("%d:%02d", minutes, seconds)

            val albumUri = Uri.parse("content://media/external/audio/albumart/${song.albumId}")
            Glide.with(binding.root.context)
                .load(albumUri)
                .placeholder(R.drawable.ic_music_note_large)
                .error(R.drawable.ic_music_note_large)
                .centerCrop()
                .into(binding.ivAlbumArt)

            binding.root.setOnClickListener { onSongClick(song, position) }
            binding.btnRemove.setOnClickListener { onRemoveClick(song) }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemPlaylistSongBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position), position)
    }

    class Diff : DiffUtil.ItemCallback<PlaylistSong>() {
        override fun areItemsTheSame(a: PlaylistSong, b: PlaylistSong) = a.id == b.id
        override fun areContentsTheSame(a: PlaylistSong, b: PlaylistSong) = a == b
    }
}
