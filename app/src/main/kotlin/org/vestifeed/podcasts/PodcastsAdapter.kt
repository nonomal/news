package org.vestifeed.podcasts

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import org.vestifeed.R
import org.vestifeed.databinding.ListItemPodcastBinding
import org.vestifeed.enclosures.PlaybackState

class PodcastsAdapter(
    private val callback: Callback,
) : ListAdapter<PodcastsAdapter.Item, PodcastsAdapter.ViewHolder>(DiffCallback()) {

    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int,
    ): ViewHolder {
        return ViewHolder(
            ListItemPodcastBinding.inflate(
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
        fun onItemClick(item: Item)
        fun onDownloadClick(item: Item)
        fun onPlayPauseClick(item: Item)
        fun onDeleteClick(item: Item)
    }

    data class Item(
        val id: String,
        val entryId: String,
        val linkId: Long,
        val href: String,
        val type: String,
        val primaryText: String,
        val secondaryText: String,
        val downloadProgress: Double? = null,
        val cacheUri: String? = null,
        val read: Boolean = false,
        val bookmarked: Boolean = false,
        val playbackState: PlaybackState = PlaybackState.Idle,
    )

    class ViewHolder(
        private val binding: ListItemPodcastBinding,
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(item: Item, callback: Callback) = binding.apply {
            primaryText.text = item.primaryText
            primaryText.isEnabled = !item.read

            secondaryText.text = item.secondaryText
            secondaryText.isVisible = item.secondaryText.isNotBlank()
            secondaryText.isEnabled = !item.read

            // Audio controls are always visible — the whole point of the
            // Podcasts tab is that one tap kicks off the download or
            // playback flow for every row.
            bookmarkIcon.isVisible = item.bookmarked
            card.alpha = if (item.read) 0.55f else 1f

            if (item.downloadProgress == null) {
                download.isVisible = true
                downloading.isVisible = false
                downloadProgress.isVisible = false
                play.isVisible = false
                delete.isVisible = false
            } else {
                val progress = item.downloadProgress
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

            root.setOnClickListener { callback.onItemClick(item) }
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
            return newItem.id == oldItem.id
        }

        override fun areContentsTheSame(
            oldItem: Item,
            newItem: Item,
        ): Boolean {
            return newItem == oldItem
        }
    }
}
