package org.vestifeed.entry

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.viewpager2.adapter.FragmentStateAdapter
import androidx.viewpager2.widget.ViewPager2
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.vestifeed.app.db
import org.vestifeed.app.sync
import org.vestifeed.databinding.FragmentUnreadPagerBinding
import org.vestifeed.navigation.AppFragment

class UnreadPagerFragment : AppFragment() {

    private var _binding: FragmentUnreadPagerBinding? = null
    private val binding get() = _binding!!

    private val initialEntryId: String by lazy {
        requireArguments().getString(ARG_INITIAL_ENTRY_ID, "").orEmpty()
    }

    private val unreadIds: List<String> by lazy {
        requireArguments().getStringArrayList(ARG_UNREAD_IDS).orEmpty()
    }

    private val pageChangeCallback = object : ViewPager2.OnPageChangeCallback() {
        override fun onPageSelected(position: Int) {
            val entryId = unreadIds.getOrNull(position) ?: return
            if (entryId == initialEntryId) return
            markRead(entryId)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = FragmentUnreadPagerBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val pageIds = when {
            unreadIds.isNotEmpty() -> unreadIds
            initialEntryId.isNotBlank() -> listOf(initialEntryId)
            else -> emptyList()
        }

        val initialIndex = pageIds.indexOf(initialEntryId).coerceAtLeast(0)
        val adapter = UnreadPagerAdapter(this@UnreadPagerFragment, pageIds)
        binding.pager.adapter = adapter
        binding.pager.registerOnPageChangeCallback(pageChangeCallback)
        if (initialIndex > 0) {
            binding.pager.setCurrentItem(initialIndex, false)
        }
    }

    override fun onDestroyView() {
        binding.pager.unregisterOnPageChangeCallback(pageChangeCallback)
        binding.pager.adapter = null
        _binding = null
        super.onDestroyView()
    }

    private fun markRead(entryId: String) {
        viewLifecycleOwner.lifecycleScope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    db().entry.updateReadAndReadSynced(
                        id = entryId,
                        extRead = true,
                        extReadSynced = false,
                    )
                }
                sync().runInBackground()
            }
        }
    }

    companion object {
        const val ARG_INITIAL_ENTRY_ID = "initialEntryId"
        const val ARG_UNREAD_IDS = "unreadIds"

        fun arguments(initialEntryId: String, unreadIds: List<String>): Bundle =
            bundleOf(
                ARG_INITIAL_ENTRY_ID to initialEntryId,
                ARG_UNREAD_IDS to ArrayList(unreadIds),
            )
    }
}

private class UnreadPagerAdapter(
    host: Fragment,
    private val entryIds: List<String>,
) : FragmentStateAdapter(host) {
    override fun getItemCount(): Int = entryIds.size

    override fun createFragment(position: Int): Fragment {
        return EntryFragment().apply {
            arguments = bundleOf("entryId" to entryIds[position])
        }
    }

    override fun getItemId(position: Int): Long = entryIds[position].hashCode().toLong()

    override fun containsItem(itemId: Long): Boolean {
        return entryIds.any { it.hashCode().toLong() == itemId }
    }
}
