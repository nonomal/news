package org.vestifeed.tags

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.os.bundleOf
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.core.view.updatePadding
import androidx.fragment.app.commit
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.vestifeed.R
import org.vestifeed.app.App
import org.vestifeed.app.db
import org.vestifeed.databinding.FragmentTagFeedsBinding
import org.vestifeed.db.table.ConfTable
import org.vestifeed.db.table.FeedTable
import org.vestifeed.dialog.showErrorDialog
import org.vestifeed.entries.EntriesFilter
import org.vestifeed.entries.EntriesFragment
import org.vestifeed.entries.toBundle
import org.vestifeed.feeds.FeedsAdapter
import org.vestifeed.navigation.AppFragment
import org.vestifeed.navigation.openUrl
import org.vestifeed.parser.AtomLinkRel

class TagFeedsFragment : AppFragment() {

    sealed class State {
        object Loading : State()
        data class ShowingFeeds(val tagName: String, val feeds: List<FeedsAdapter.Item>) : State()
    }

    private val db by lazy { (requireContext().applicationContext as App).db }

    private val tagId by lazy { requireArguments().getString("tagId", "") }

    private val isEditable: Boolean by lazy {
        db.conf.select().backend != ConfTable.Backend.Miniflux
    }

    private val state = MutableStateFlow<State>(State.Loading)

    private var _binding: FragmentTagFeedsBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = FragmentTagFeedsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        ViewCompat.setOnApplyWindowInsetsListener(binding.toolbar) { v, insets ->
            insets.getInsets(WindowInsetsCompat.Type.statusBars()).let {
                v.updatePadding(top = it.top)
            }
            insets
        }

        ViewCompat.setOnApplyWindowInsetsListener(binding.list) { v, insets ->
            insets.getInsets(WindowInsetsCompat.Type.navigationBars()).let {
                v.updatePadding(bottom = it.bottom)
            }
            insets
        }

        binding.toolbar.setNavigationOnClickListener { parentFragmentManager.popBackStack() }

        binding.list.apply {
            setHasFixedSize(true)
            adapter = createFeedsAdapter()
            layoutManager = LinearLayoutManager(requireContext())
        }

        viewLifecycleOwner.lifecycleScope.launch {
            state.update { State.Loading }
            val loaded = withContext(Dispatchers.IO) { loadFeeds() }
            state.update { loaded }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.RESUMED) {
                state.collect { binding.setState(it) }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun FeedTable.Feed.toItem(database: App): FeedsAdapter.Item {
        val links = database.db.link.selectByFeedId(id)
        val selfLink = links.firstOrNull { it.rel is AtomLinkRel.Self }?.href
            ?: links.firstOrNull()?.href
            ?: "https://example.com"

        return FeedsAdapter.Item(
            id = id,
            title = title,
            selfLink = selfLink,
            alternateLink = links.firstOrNull { it.rel is AtomLinkRel.Alternate }?.href,
            unreadCount = database.db.entry.selectByFeedId(id).filterNot { it.extRead }.size.toLong(),
            confUseBuiltInBrowser = database.db.conf.select().useBuiltInBrowser,
        )
    }

    private fun loadFeeds(): State {
        val tag = db.tag.selectById(tagId)
            ?: return State.ShowingFeeds(tagName = "", feeds = emptyList())
        val feedIds = db.feedTag.selectFeedIdsByTagId(tagId)
        val feeds = feedIds.mapNotNull { db.feed.selectById(it) }
            .map { feed -> feed.toItem(requireContext().applicationContext as App) }
        return State.ShowingFeeds(tagName = tag.name, feeds = feeds)
    }

    private fun createFeedsAdapter(): FeedsAdapter {
        return FeedsAdapter(
            callback = object : FeedsAdapter.Callback {
                override fun onClick(item: FeedsAdapter.Item) {
                    parentFragmentManager.commit {
                        replace(
                            R.id.fragmentContainerView,
                            EntriesFragment::class.java,
                            EntriesFilter.BelongToFeed(feedId = item.id).toBundle(),
                        )
                        addToBackStack(null)
                    }
                }

                override fun onSettingsClick(item: FeedsAdapter.Item) {
                    parentFragmentManager.commit {
                        replace(
                            R.id.fragmentContainerView,
                            org.vestifeed.feedsettings.FeedSettingsFragment::class.java,
                            bundleOf("feedId" to item.id),
                        )
                        addToBackStack(null)
                    }
                }

                override fun onOpenSelfLinkClick(item: FeedsAdapter.Item) {
                    openUrl(
                        url = item.selfLink.toString(),
                        useBuiltInBrowser = item.confUseBuiltInBrowser,
                    )
                }

                override fun onOpenAlternateLinkClick(item: FeedsAdapter.Item) {
                    openUrl(
                        url = item.alternateLink.toString(),
                        useBuiltInBrowser = item.confUseBuiltInBrowser,
                    )
                }

                override fun onAddToTagClick(item: FeedsAdapter.Item) {
                    // The tag-feeds view inherits the Feed action popup, but
                    // "add to tag" only makes sense from the feed side, not
                    // from inside a single tag's feed list.
                }

                override fun onRenameClick(item: FeedsAdapter.Item) {
                    // Rename is not supported inside the tag-feeds view.
                }

                override fun onDeleteClick(item: FeedsAdapter.Item) {
                    removeFeedFromTag(item.id)
                }
            },
            actionsMenuRes = R.menu.menu_tag_feed_actions,
            showActions = isEditable,
        )
    }

    private fun removeFeedFromTag(feedId: String) {
        viewLifecycleOwner.lifecycleScope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    db.feedTag.delete(feedId = feedId, tagId = tagId)
                }
            }.onSuccess {
                val loaded = withContext(Dispatchers.IO) { loadFeeds() }
                state.update { loaded }
            }.onFailure { e -> showErrorDialog(e) }
        }
    }

    private fun FragmentTagFeedsBinding.setState(state: State) {
        listOf(toolbar, list, progress, message).forEach { it.isVisible = false }

        when (state) {
            is State.Loading -> listOf(toolbar, progress).forEach { it.isVisible = true }
            is State.ShowingFeeds -> {
                toolbar.isVisible = true
                toolbar.title = if (state.tagName.isBlank()) getString(R.string.tag) else state.tagName
                (binding.list.adapter as? FeedsAdapter)?.submitList(state.feeds)

                if (state.feeds.isEmpty()) {
                    message.isVisible = true
                    message.text = getString(R.string.no_feeds_in_tag)
                } else {
                    list.isVisible = true
                }
            }
        }
    }
}
