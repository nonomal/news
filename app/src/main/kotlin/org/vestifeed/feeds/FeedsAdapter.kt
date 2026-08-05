package org.vestifeed.feeds

import java.text.NumberFormat
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.annotation.MenuRes
import androidx.appcompat.widget.PopupMenu
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import org.vestifeed.R
import org.vestifeed.databinding.ListItemFeedBinding

class FeedsAdapter(
    private val callback: Callback,
    @MenuRes private val actionsMenuRes: Int = R.menu.menu_feed_actions,
    private val showActions: Boolean = true,
    private val tagsEditable: Boolean = true,
) : ListAdapter<FeedsAdapter.Item, FeedsAdapter.ItemViewHolder>(
    Diff(),
) {

    data class Item(
        val id: String,
        val title: String,
        val selfLink: String,
        val alternateLink: String?,
        val unreadCount: Long,
        val confUseBuiltInBrowser: Boolean,
    )

    class ItemViewHolder(
        private val binding: ListItemFeedBinding,
        private val callback: Callback,
        @MenuRes private val actionsMenuRes: Int,
        private val showActions: Boolean,
        private val tagsEditable: Boolean,
    ) : RecyclerView.ViewHolder(
        binding.root,
    ) {
        private val integerFormat = NumberFormat.getIntegerInstance()

        fun bind(item: Item) {
            binding.apply {
                primaryText.text = item.title
                secondaryText.text = item.selfLink.toString()

                unreadCount.isVisible = item.unreadCount > 0
                unreadCount.text = integerFormat.format(item.unreadCount)

                actions.isVisible = showActions

                if (showActions) {
                    actions.setOnClickListener {
                        val popup = PopupMenu(root.context, actions)

                        popup.apply {
                            menuInflater.inflate(actionsMenuRes, popup.menu)
                            menu.findItem(R.id.openAlternateLink)?.isVisible =
                                item.alternateLink != null
                            menu.findItem(R.id.addToTag)?.isVisible = tagsEditable

                            setOnMenuItemClickListener {
                                when (it.itemId) {
                                    R.id.openSettings -> {
                                        callback.onSettingsClick(item)
                                    }

                                    R.id.openSelfLink -> {
                                        callback.onOpenSelfLinkClick(item)
                                    }

                                    R.id.openAlternateLink -> {
                                        callback.onOpenAlternateLinkClick(item)
                                    }

                                    R.id.addToTag -> {
                                        callback.onAddToTagClick(item)
                                    }

                                    R.id.rename -> {
                                        callback.onRenameClick(item)
                                    }

                                    R.id.delete -> {
                                        callback.onDeleteClick(item)
                                    }

                                    R.id.removeFromTag -> {
                                        callback.onDeleteClick(item)
                                    }
                                }

                                true
                            }

                            show()
                        }
                    }
                }

                root.setOnClickListener { callback.onClick(item) }
            }
        }
    }

    interface Callback {
        fun onClick(item: Item)
        fun onSettingsClick(item: Item)
        fun onOpenSelfLinkClick(item: Item)
        fun onOpenAlternateLinkClick(item: Item)
        fun onAddToTagClick(item: Item)
        fun onRenameClick(item: Item)
        fun onDeleteClick(item: Item)
    }

    class Diff : DiffUtil.ItemCallback<Item>() {
        override fun areItemsTheSame(oldItem: Item, newItem: Item) = oldItem.id == newItem.id

        override fun areContentsTheSame(oldItem: Item, newItem: Item) = oldItem == newItem
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ItemViewHolder {
        val binding = ListItemFeedBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false,
        )

        return ItemViewHolder(binding, callback, actionsMenuRes, showActions, tagsEditable)
    }

    override fun onBindViewHolder(holder: ItemViewHolder, position: Int) {
        holder.bind(getItem(position))
    }
}
