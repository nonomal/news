package org.vestifeed.tags

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.appcompat.widget.PopupMenu
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import java.text.NumberFormat
import org.vestifeed.R
import org.vestifeed.databinding.ListItemTagBinding

class TagsAdapter(
    private val callback: Callback,
) : ListAdapter<TagsAdapter.Item, TagsAdapter.ItemViewHolder>(
    Diff(),
) {

    data class Item(
        val id: String,
        val name: String,
        val feedCount: Long,
        val unreadCount: Long,
        val editable: Boolean,
    )

    class ItemViewHolder(
        private val binding: ListItemTagBinding,
        private val callback: Callback,
    ) : RecyclerView.ViewHolder(
        binding.root,
    ) {
        private val integerFormat = NumberFormat.getIntegerInstance()

        fun bind(item: Item) {
            binding.apply {
                primaryText.text = item.name
                secondaryText.text = secondaryText.resources.getQuantityString(
                    R.plurals.tag_feed_count,
                    item.feedCount.toInt().coerceAtLeast(0),
                    item.feedCount.toInt().coerceAtLeast(0),
                )

                unreadCount.isVisible = item.unreadCount > 0
                unreadCount.text = integerFormat.format(item.unreadCount)

                actions.isClickable = item.editable
                actions.alpha = if (item.editable) 1.0f else 0.3f

                actions.setOnClickListener {
                    if (!item.editable) return@setOnClickListener
                    val popup = PopupMenu(root.context, actions)
                    popup.apply {
                        menuInflater.inflate(R.menu.menu_tag_actions, popup.menu)
                        setOnMenuItemClickListener { menuItem ->
                            when (menuItem.itemId) {
                                R.id.renameTag -> {
                                    callback.onRenameClick(item)
                                }

                                R.id.deleteTag -> {
                                    callback.onDeleteClick(item)
                                }
                            }
                            true
                        }
                        show()
                    }
                }

                root.setOnClickListener { callback.onClick(item) }
            }
        }
    }

    interface Callback {
        fun onClick(item: Item)
        fun onRenameClick(item: Item)
        fun onDeleteClick(item: Item)
    }

    class Diff : DiffUtil.ItemCallback<Item>() {
        override fun areItemsTheSame(oldItem: Item, newItem: Item) = oldItem.id == newItem.id

        override fun areContentsTheSame(oldItem: Item, newItem: Item) = oldItem == newItem
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ItemViewHolder {
        val binding = ListItemTagBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false,
        )

        return ItemViewHolder(binding, callback)
    }

    override fun onBindViewHolder(holder: ItemViewHolder, position: Int) {
        holder.bind(getItem(position))
    }
}
