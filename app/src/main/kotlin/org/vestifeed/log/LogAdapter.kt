package org.vestifeed.log

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import org.vestifeed.databinding.ListItemLogBinding
import org.vestifeed.db.table.LogTable

class LogAdapter : ListAdapter<LogAdapter.Item, LogAdapter.ViewHolder>(DiffCallback()) {

    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int,
    ): ViewHolder {
        return ViewHolder(
            ListItemLogBinding.inflate(
                LayoutInflater.from(parent.context),
                parent,
                false,
            )
        )
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    data class Item(
        val entry: LogTable.Entry,
    )

    class ViewHolder(
        private val binding: ListItemLogBinding,
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(item: Item) = binding.apply {
            val entry = item.entry
            level.text = entry.level.value.uppercase()
            tag.text = entry.tag
            message.text = entry.message
            timestamp.text = entry.timestamp

            val color = when (entry.level) {
                LogLevel.ERROR -> 0xFFDC3545.toInt()
                LogLevel.WARN -> 0xFFFFC107.toInt()
                LogLevel.INFO -> 0xFF17A2B8.toInt()
                LogLevel.DEBUG -> 0xFF6C757D.toInt()
                LogLevel.TRACE -> 0xFFADE4E4.toInt()
            }
            level.setTextColor(color)
        }
    }

    class DiffCallback : DiffUtil.ItemCallback<Item>() {
        override fun areItemsTheSame(
            oldItem: Item,
            newItem: Item,
        ): Boolean {
            return newItem.entry.id == oldItem.entry.id
        }

        override fun areContentsTheSame(
            oldItem: Item,
            newItem: Item,
        ): Boolean {
            return newItem.entry == oldItem.entry
        }
    }
}