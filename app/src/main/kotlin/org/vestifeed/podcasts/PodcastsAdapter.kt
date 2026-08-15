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
            ),
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

            bookmarkIcon.isVisible = item.bookmarked

            // The download progress bar only makes sense while the file is
            // actively downloading. Showing a full bar at 100% would look
            // like work-in-progress, which is why we hide it then and rely on
            // the inline action label to convey "downloaded".
            val progress = item.downloadProgress
            downloadProgress.isVisible = progress != null && progress < 1.0
            if (downloadProgress.isVisible) {
                downloadProgress.progress = (progress!! * 100).toInt()
            }

            val ctx = binding.root.context
            val sb = statusBadgeFor(item)
            val badge: String? = when (sb) {
                StatusBadge.Played -> ctx.getString(R.string.played)
                StatusBadge.Downloading -> ctx.getString(R.string.downloading)
                StatusBadge.Unplayed -> ctx.getString(R.string.unplayed)
                else -> null
            }
            statusBadge.isVisible = badge != null
            statusBadge.text = badge.orEmpty()

            // The inline action label is the only way to engage with a row
            // now that the more-vert menu is gone. It mirrors the row's
            // available action: "Download" when the enclosure hasn't been
            // cached yet, "Listen" once it's on disk. While a download is
            // in progress we hide the label — the progress bar already
            // invites attention.
            val downloaded = progress == 1.0
            val downloading = progress != null && !downloaded
            val showAction = !downloading
            actionText.isVisible = showAction
            if (showAction) {
                actionText.text = ctx.getString(
                    if (downloaded) R.string.listen else R.string.download,
                )
            }

            // The whole row is tappable: "Download" when there's nothing on
            // disk, "Listen" once there is. The inline label forwards to the
            // same intent so a precise tap on the label still does the right
            // thing. Long-press isn't wired — the delete flow has to come
            // from somewhere else once the file is on disk.
            root.setOnClickListener { callback.onItemClick(item) }

            actionText.setOnClickListener {
                if (downloaded) {
                    callback.onPlayPauseClick(item)
                } else {
                    callback.onDownloadClick(item)
                }
            }
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

/**
 * Lightweight render of the audio enclosure state for the right-hand
 * badge. Kept at file scope so the nested [PodcastsAdapter.ViewHolder] can
 * call [statusBadgeFor] without the receiver-style ambiguity you get
 * from `binding.apply { ... }`.
 */
internal enum class StatusBadge { Downloading, Played, Unplayed }

/**
 * Decides which status badge (if any) to render for a given row. An
 * active download always wins over the read state; once the file is on
 * disk we distinguish read vs unread; otherwise no badge at all.
 */
internal fun statusBadgeFor(item: PodcastsAdapter.Item): StatusBadge? {
    val progress = item.downloadProgress
    return when {
        progress != null && progress < 1.0 -> StatusBadge.Downloading
        progress == 1.0 && !item.read -> StatusBadge.Unplayed
        progress == 1.0 && item.read -> StatusBadge.Played
        else -> null
    }
}
