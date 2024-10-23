package enclosures

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import co.appreactor.news.databinding.ListItemEnclosureBinding
import db.Link

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
        fun onPlayClick(item: Item)
        fun onDeleteClick(item: Item)
    }

    data class Item(
        val entryId: String,
        val enclosure: Link,
        val primaryText: String,
        val secondaryText: String,
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
                play.isVisible = progress == 1.0
                delete.isVisible = progress == 1.0
            }

            download.setOnClickListener { callback.onDownloadClick(item) }
            play.setOnClickListener { callback.onPlayClick(item) }
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
            return newItem.enclosure == oldItem.enclosure
        }
    }
}