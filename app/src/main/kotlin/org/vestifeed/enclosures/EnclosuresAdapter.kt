package org.vestifeed.enclosures

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import org.vestifeed.R
import org.vestifeed.databinding.ListItemEnclosureBinding
import org.vestifeed.db.table.LinkTable

class EnclosuresAdapter(
    private val callback: Callback,
) : ListAdapter<EnclosuresAdapter.Item, EnclosuresAdapter.ViewHolder>(DiffCallback()) {

    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int,
    ): ViewHolder {
        return ViewHolder(
            ListItemEnclosureBinding.inflate(
                LayoutInflater.from(parent.context),
                parent,
                false,
            )
        )
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position), callback)
    }

    interface Callback {
        fun onDownloadClick(item: Item)
        fun onPlayPauseClick(item: Item)
        fun onDeleteClick(item: Item)
    }

    data class Item(
        val entryId: String,
        val enclosure: LinkTable.Link,
        val primaryText: String,
        val secondaryText: String,
        val playbackState: PlaybackState = PlaybackState.Idle,
    )

    class ViewHolder(
        private val binding: ListItemEnclosureBinding,
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(item: Item, callback: Callback) = binding.apply {
            binding.primaryText.text = item.primaryText
            binding.secondaryText.text = item.secondaryText
            binding.supportingText.isVisible = false

            if (item.enclosure.extEnclosureDownloadProgress == null) {
                download.isVisible = true
                downloading.isVisible = false
                downloadProgress.isVisible = false
                play.isVisible = false
                delete.isVisible = false
            } else {
                val progress = item.enclosure.extEnclosureDownloadProgress
                val progressPercent = (progress * 100).toInt()
                download.isVisible = false
                downloading.isVisible = progress != 1.0
                downloadProgress.isVisible = progress != 1.0
                downloadProgress.progress = progressPercent
                delete.isVisible = progress == 1.0

                when (item.playbackState) {
                    PlaybackState.Idle -> {
                        play.isVisible = progress == 1.0
                        play.text = play.context.getString(R.string.listen)
                        play.setIconResource(R.drawable.ic_baseline_headset_24)
                    }
                    PlaybackState.Playing -> {
                        play.isVisible = true
                        play.text = play.context.getString(R.string.pause)
                        play.setIconResource(R.drawable.ic_baseline_pause_24)
                    }
                    PlaybackState.Paused -> {
                        play.isVisible = true
                        play.text = play.context.getString(R.string.resume)
                        play.setIconResource(R.drawable.ic_baseline_play_arrow_24)
                    }
                }
            }

            download.setOnClickListener { callback.onDownloadClick(item) }
            play.setOnClickListener { callback.onPlayPauseClick(item) }
            delete.setOnClickListener { callback.onDeleteClick(item) }
        }
    }

    class DiffCallback : DiffUtil.ItemCallback<Item>() {

        override fun areItemsTheSame(
            oldItem: Item,
            newItem: Item,
        ): Boolean {
            return newItem.entryId == oldItem.entryId && newItem.enclosure.href == oldItem.enclosure.href
        }

        override fun areContentsTheSame(
            oldItem: Item,
            newItem: Item,
        ): Boolean {
            return newItem.enclosure == oldItem.enclosure &&
                newItem.playbackState == oldItem.playbackState
        }
    }
}

enum class PlaybackState {
    Idle,
    Playing,
    Paused,
}