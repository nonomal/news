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
import androidx.fragment.app.commit
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import kotlinx.coroutines.launch
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.vestifeed.R
import org.vestifeed.app.db
import org.vestifeed.app.sync
import org.vestifeed.databinding.FragmentPodcastsBinding
import org.vestifeed.dialog.showErrorDialog
import org.vestifeed.navigation.AppFragment
import org.vestifeed.settings.SettingsFragment

/**
 * Lists every audio enclosure on the device, sorted by entry publish date
 * (newest first). Renders the same flat list pattern as the Feeds and
 * Tags tabs — a single row per enclosure with an action menu exposing
 * download / play / delete — so the visual rhythm matches the rest of
 * the app.
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
            // Tapping a row used to do nothing on this tab. Now it plays the
            // enclosure when it has been downloaded locally and otherwise
            // kicks off the download — mirroring the "tap to engage" pattern
            // users expect from the feed and tag lists.
            if (item.cacheUri.isNullOrBlank()) {
                requestLocalNetworkAccessThen { viewModel.downloadAudio(item) }
            } else {
                viewModel.playAudio(item)
            }
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

        // When this tab is the root fragment (shown directly by the bottom
        // nav), there's nothing to pop — hide the up arrow so the screen
        // reads as a top-level destination. If it gets pushed onto the
        // back stack later, the arrow becomes meaningful.
        if (parentFragmentManager.backStackEntryCount == 0) {
            binding.toolbar.navigationIcon = null
        }

        binding.toolbar.setOnMenuItemClickListener {
            when (it.itemId) {
                R.id.settings -> {
                    parentFragmentManager.commit {
                        replace(R.id.fragmentContainerView, SettingsFragment::class.java, null)
                        addToBackStack(null)
                    }
                    true
                }
                else -> false
            }
        }

        binding.list.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = this@PodcastsFragment.adapter
            // Row dividers aren't strictly required — the matching feeds and
            // tags tabs render as one flat row per item too — but the
            // background drawable on each row (`?selectableItemBackground`)
            // provides a clear ripple boundary on tap.
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
}
