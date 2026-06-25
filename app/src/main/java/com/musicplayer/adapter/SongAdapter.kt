package com.musicplayer.adapter

import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.musicplayer.R
import com.musicplayer.databinding.ItemSongBinding
import com.musicplayer.model.Song

class SongAdapter(
    private val onSongClick: (Song, Int, View?) -> Unit,
    private val onSongLongClick: (Song) -> Unit,
    private val onFavouriteClick: (Song, View?) -> Unit,
    private val isFavourite: (Long) -> Boolean
) : ListAdapter<Song, SongAdapter.SongViewHolder>(SongDiffCallback()) {

    inner class SongViewHolder(private val binding: ItemSongBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(song: Song, position: Int) {
            binding.tvSongTitle.text = song.title
            binding.tvArtistName.text = song.artist
            binding.tvDuration.text = song.getDurationFormatted()

            val albumUri = Uri.parse("content://media/external/audio/albumart/${song.albumId}")
            Glide.with(binding.root.context)
                .load(albumUri)
                .placeholder(R.drawable.ic_music_note_large)
                .error(R.drawable.ic_music_note_large)
                .centerCrop()
                .into(binding.ivAlbumArt)

            val favIcon = if (isFavourite(song.id)) {
                R.drawable.ic_favourite_filled
            } else {
                R.drawable.ic_favourite_border
            }
            binding.btnFavourite.setImageResource(favIcon)

            binding.btnFavourite.setOnClickListener {
                onFavouriteClick(song, binding.btnFavourite)
            }

            binding.root.setOnClickListener { onSongClick(song, position, binding.ivAlbumArt) }
            binding.root.setOnLongClickListener {
                onSongLongClick(song)
                true
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SongViewHolder {
        val binding = ItemSongBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return SongViewHolder(binding)
    }

    override fun onBindViewHolder(holder: SongViewHolder, position: Int) {
        holder.bind(getItem(position), position)
    }
}

class SongDiffCallback : DiffUtil.ItemCallback<Song>() {
    override fun areItemsTheSame(oldItem: Song, newItem: Song) = oldItem.id == newItem.id
    override fun areContentsTheSame(oldItem: Song, newItem: Song) = oldItem == newItem
}
