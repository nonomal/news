package org.vestifeed.entries

import android.view.LayoutInflater
import android.view.ViewGroup
import android.view.WindowManager
import androidx.core.content.getSystemService
import androidx.fragment.app.FragmentActivity
import androidx.recyclerview.widget.ListAdapter
import org.vestifeed.databinding.ListItemEntryBinding

class EntriesAdapter(
    private val activity: FragmentActivity,
    private val callback: EntriesAdapterCallback,
) : ListAdapter<EntriesAdapter.Item, EntriesAdapterViewHolder>(EntriesAdapterDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): EntriesAdapterViewHolder {
        val binding = ListItemEntryBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false,
        )

        return EntriesAdapterViewHolder(
            binding = binding,
            callback = callback,
            screenWidth = screenWidth(),
        )
    }

    override fun onBindViewHolder(holder: EntriesAdapterViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    private fun screenWidth(): Int {
        val windowManager = activity.getSystemService<WindowManager>()!!
        return windowManager.currentWindowMetrics.bounds.width()
    }

    data class Item(
        val id: String,
        val showImage: Boolean,
        val cropImage: Boolean,
        val imageUrl: String,
        val imageWidth: Int,
        val imageHeight: Int,
        val title: String,
        val subtitle: String,
        val summary: String,
        var read: Boolean,
        val openInBrowser: Boolean,
        val useBuiltInBrowser: Boolean,
    )
}