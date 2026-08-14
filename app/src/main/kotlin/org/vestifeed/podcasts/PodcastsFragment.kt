package org.vestifeed.podcasts

import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.net.toUri
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.core.view.updatePadding
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.launch
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.vestifeed.R
import org.vestifeed.app.db
import org.vestifeed.app.sync
import org.vestifeed.databinding.FragmentPodcastsBinding
import org.vestifeed.dialog.showErrorDialog
import org.vestifeed.entries.CardListAdapterDecoration
import org.vestifeed.entries.SwipeHelper
import org.vestifeed.navigation.AppFragment

/**
 * Lists every audio enclosure on the device, sorted by entry publish date
 * (newest first). Shows the same swipe-to-read/bookmark affordances as the
 * entries screen plus an audio control row at the bottom of every card.
 */
class PodcastsFragment : AppFragment() {

    private var _binding: FragmentPodcastsBinding? = null
    private val binding get() = _binding!!

    private val viewModel: PodcastsViewModel by viewModels {
        PodcastsViewModelFactory(
            db = db(),
            sync = sync(),
            enclosuresRepo = org.vestifeed.enclosures.EnclosuresRepo(
                requireContext().applicationContext,
                db(),
            ),
        )
    }

    private val adapter = PodcastsAdapter(object : PodcastsAdapter.Callback {
        override fun onItemClick(item: PodcastsAdapter.Item) {
            // Tapping the card does nothing on this tab — the audio
            // controls below are the entry points. We could open the
            // parent entry in a future iteration, but for now keep the
            // behaviour predictable.
        }

        override fun onDownloadClick(item: PodcastsAdapter.Item) {
            requestLocalNetworkAccessThen { viewModel.downloadAudio(item) }
        }

        override fun onPlayPauseClick(item: PodcastsAdapter.Item) {
            viewModel.playAudio(item)
        }

        override fun onDeleteClick(item: PodcastsAdapter.Item) {
            viewModel.deleteAudio(item)
        }
    })

    private var swipeHelper: ItemTouchHelper? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = FragmentPodcastsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Match the EntriesFragment pattern: status bar inset becomes toolbar
        // padding-top so the toolbar still draws flush against the status bar
        // but the title sits clear of it. The navigation bar inset still has to
        // be applied to the list so the last row clears the gesture pill.
        ViewCompat.setOnApplyWindowInsetsListener(binding.toolbar) { v, insets ->
            val statusBar = insets.getInsets(WindowInsetsCompat.Type.statusBars()).top
            v.updatePadding(top = statusBar)
            // The MaterialToolbar caps its measured height at the actionBarSize
            // even when we bump its padding-top, so the existing layout
            // (EntriesFragment, etc.) only looks right because the toolbar has
            // menu items that push it past that minimum. Without a menu the
            // toolbar would otherwise stay flat — explicitly raise its minimum
            // height so it grows to include the status bar inset.
            v.minimumHeight = v.paddingTop +
                v.resources.getDimensionPixelSize(R.dimen.action_bar_default_height)
            insets
        }

        ViewCompat.setOnApplyWindowInsetsListener(binding.list) { v, insets ->
            insets.getInsets(WindowInsetsCompat.Type.navigationBars()).let {
                v.updatePadding(bottom = it.bottom)
            }
            insets
        }

        // When this tab is the root fragment (shown directly by the bottom
        // nav), there's nothing to pop — hide the up arrow so the screen
        // reads as a top-level destination. If it gets pushed onto the
        // back stack later, the arrow becomes meaningful.
        if (parentFragmentManager.backStackEntryCount == 0) {
            binding.toolbar.navigationIcon = null
        }

        binding.list.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = this@PodcastsFragment.adapter
            addItemDecoration(
                CardListAdapterDecoration(
                    resources.getDimensionPixelSize(R.dimen.entries_cards_gap),
                ),
            )
        }

        swipeHelper = createSwipeHelper().also {
            it.attachToRecyclerView(binding.list)
        }

        // Reload every time the screen comes back into view, so a download
        // completed in the background (or while we were paused) shows up.
        // The view model also reloads on sync completion, but a foreground
        // toggle is the only signal for "user came back after a download".
        // Initial load still happens in the VM init {}.
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.RESUMED) {
                viewModel.reload()
            }
        }

        observeState()
        observeActions()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        swipeHelper?.attachToRecyclerView(null)
        swipeHelper = null
        _binding = null
    }

    /** Snap the list back to position 0 — used by the bottom-nav reselect. */
    fun scrollToTop() {
        val list = _binding?.list ?: return
        list.stopScroll()
        (list.layoutManager as? LinearLayoutManager)
            ?.scrollToPositionWithOffset(0, 0)
    }

    private fun observeState() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.state.collect { renderState(it) }
            }
        }
    }

    private fun observeActions() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.actions.collect { handleAction(it) }
            }
        }
    }

    private fun renderState(state: PodcastsScreenState) {
        binding.toolbar.title = if (state.items.isEmpty()) {
            getString(R.string.podcasts)
        } else {
            getString(R.string.podcasts_n, state.items.size)
        }
        when {
            state.items.isEmpty() -> {
                binding.progress.isVisible = false
                binding.list.isVisible = false
                binding.message.isVisible = true
            }
            else -> {
                binding.progress.isVisible = false
                binding.list.isVisible = true
                binding.message.isVisible = false
                adapter.submitList(state.items)
            }
        }
    }

    private fun handleAction(action: PodcastsAction) {
        when (action) {
            is PodcastsAction.ShowError -> showErrorDialog(action.message)
            is PodcastsAction.PlayAudio -> launchPlayer(action.uri, action.mimeType)
        }
    }

    private fun launchPlayer(uri: String, mimeType: String) {
        val intent = Intent(Intent.ACTION_VIEW)
        intent.setDataAndType(uri.toUri(), mimeType)
        runCatching {
            startActivity(intent)
        }.onFailure {
            if (it is ActivityNotFoundException) {
                showErrorDialog(R.string.you_have_no_apps_which_can_play_this_podcast)
            } else {
                showErrorDialog(it)
            }
        }
    }

    /**
     * Download URLs may target the user's local network (a podcast host on
     * their NAS, for example). Gate the action on the same permission
     * dialog the entries tab uses for syncs.
     */
    private fun requestLocalNetworkAccessThen(after: () -> Unit) {
        viewLifecycleOwner.lifecycleScope.launch {
            val urls = viewModel.state.value.items.mapNotNull {
                it.href.toHttpUrlOrNull()
            }
            if (!requestLocalNetworkAccess(urls)) {
                showErrorDialog(R.string.local_network_permission_required)
                return@launch
            }
            after()
        }
    }

    /**
     * Swipe policy mirrors the entries Unread variant: left marks as read,
     * right marks as bookmarked. Each action gets its own undo snackbar so
     * an accidental swipe can be cancelled out.
     */
    private fun createSwipeHelper(): ItemTouchHelper {
        val leftIcon = R.drawable.ic_baseline_visibility_24
        val rightIcon = R.drawable.ic_baseline_bookmark_add_24
        return ItemTouchHelper(
            object : SwipeHelper(requireContext(), leftIcon, rightIcon) {
                override fun onSwiped(
                    viewHolder: RecyclerView.ViewHolder,
                    direction: Int,
                ) {
                    val item = adapter.currentList
                        .getOrNull(viewHolder.bindingAdapterPosition) ?: return
                    when (direction) {
                        ItemTouchHelper.LEFT -> {
                            showUndoSnackbar(R.string.marked_as_read) {
                                viewModel.setRead(item.entryId, read = false)
                            }
                            viewModel.setRead(item.entryId, read = true)
                        }
                        ItemTouchHelper.RIGHT -> {
                            showUndoSnackbar(R.string.bookmarked) {
                                viewModel.setBookmarked(item.entryId, bookmarked = false)
                            }
                            viewModel.setBookmarked(item.entryId, bookmarked = true)
                        }
                        else -> Unit
                    }
                }
            },
        )
    }

    /**
     * Shows a snackbar with an "undo" action that runs [onUndo] if the user
     * taps it before the snackbar dismisses. The snackbar is anchored to the
     * activity's bottom navigation so it doesn't cover the list.
     */
    private fun showUndoSnackbar(messageRes: Int, onUndo: () -> Unit) {
        val rootView = _binding?.root ?: return
        Snackbar.make(rootView, messageRes, Snackbar.LENGTH_SHORT)
            .setAnchorView(requireActivity().findViewById(R.id.bottomNav))
            .setAction(R.string.undo) { onUndo() }
            .show()
    }
}
