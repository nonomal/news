package org.vestifeed.entries

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.core.view.updatePadding
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.commit
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.launch
import org.vestifeed.R
import org.vestifeed.app.db
import org.vestifeed.app.sync
import org.vestifeed.databinding.FragmentEntriesBinding
import org.vestifeed.dialog.showErrorDialog
import org.vestifeed.navigation.AppFragment
import org.vestifeed.notifications.NotificationPermissionAccess
import org.vestifeed.notifications.NotificationPermissionPrefs
import org.vestifeed.notifications.UnreadEntriesNotification
import org.vestifeed.search.SearchFragment
import org.vestifeed.settings.SettingsFragment

/**
 * Pure renderer for the entries list. All data, mutations and intent
 * dispatch live in [EntriesViewModel]; this class wires up views and forwards
 * user input.
 */
/**
 * Activity-scoped holder for the entries list's scroll position. We can't
 * rely on the RecyclerView's own view-state restoration because the hosting
 * activity recreates the fragment instance on every config change (see
 * [org.vestifeed.navigation.Activity.onCreate]), which discards the
 * RecyclerView's saved hierarchy state. Keeping the position here means it
 * survives both the configuration change and the fragment replacement.
 *
 * Entries are keyed by filter so that switching between Unread and Bookmarked
 * (and back) doesn't clobber the other tab's scroll offset.
 */
class EntriesListScrollState : ViewModel() {
    private val positions = mutableMapOf<EntriesFilter, Pair<Int, Int>>()

    fun get(filter: EntriesFilter): Pair<Int, Int>? = positions[filter]

    fun set(filter: EntriesFilter, position: Int, offset: Int) {
        positions[filter] = position to offset
    }
}

class EntriesFragment : AppFragment() {

    private var _binding: FragmentEntriesBinding? = null
    private val binding get() = _binding!!

    private val filter: EntriesFilter? by lazy {
        arguments?.toEntriesFilter()
    }

    private val viewModel: EntriesViewModel by viewModels {
        EntriesViewModelFactory(
            filter = requireNotNull(filter) {
                "EntriesFragment requires a ${EntriesFilter.ARG_FILTER} argument"
            },
            db = db(),
            sync = sync(),
            resources = resources,
        )
    }

    private val scrollState: EntriesListScrollState by activityViewModels()

    private val navigator = lazy { EntriesNavigator(this) }

    private val adapter by lazy {
        EntriesAdapter(requireActivity()) { viewModel.onItemClicked(it) }
            .also { it.scrollToTopOnInsert(binding.list, ::isViewAlive) }
    }

    private val touchHelper by lazy { createTouchHelper() }

    private val notificationPrefs by lazy { NotificationPermissionPrefs(requireContext()) }

    /**
     * Guards the one-shot restore in [renderItems]. Reset on each
     * [onViewCreated] so that a view recreation within the same fragment
     * instance (rare but possible) re-arms the restore.
     */
    private var scrollPositionRestored: Boolean = false

    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) {
                notificationPrefs.reset()
            } else {
                notificationPrefs.markRequestedOnce()
            }
            updateNotificationWarningVisibility()
        }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = FragmentEntriesBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        if (filter == null) {
            showErrorDialog(getString(R.string.required_argument_is_missing, EntriesFilter.ARG_FILTER)) {
                requireActivity().finish()
            }
            return
        }

        scrollPositionRestored = false
        applyStatusBarInsets()
        initToolbarMenu()
        updateNotificationWarningVisibility()
        initList()
        initSwipeRefresh()
        observeState()
        observeActions()
    }

    override fun onResume() {
        super.onResume()
        if (filter is EntriesFilter.Unread) {
            sync().unreadScreenVisible = true
            UnreadEntriesNotification.cancel(requireContext())
        }
        updateNotificationWarningVisibility()
    }

    override fun onPause() {
        super.onPause()
        if (filter is EntriesFilter.Unread) {
            sync().unreadScreenVisible = false
        }
    }

    override fun onDestroyView() {
        saveScrollPosition()
        super.onDestroyView()
        _binding = null
    }

    /**
     * Snapshots the first visible item + its top offset into the
     * activity-scoped [scrollState] so a recreated fragment can put the
     * list back where the user left it. No-op if the layout manager hasn't
     * bound any items yet (e.g. we're tearing down the initial loading
     * state).
     */
    private fun saveScrollPosition() {
        val currentFilter = filter ?: return
        val list = _binding?.list ?: return
        val layoutManager = list.layoutManager as? LinearLayoutManager ?: return
        val position = layoutManager.findFirstVisibleItemPosition()
        if (position == RecyclerView.NO_POSITION) return
        val view = layoutManager.findViewByPosition(position) ?: return
        val offset = layoutManager.getDecoratedTop(view)
        scrollState.set(currentFilter, position, offset)
    }

    private fun isViewAlive(): Boolean = _binding != null

    private fun applyStatusBarInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.toolbar) { v, insets ->
            insets.getInsets(WindowInsetsCompat.Type.statusBars()).let {
                v.updatePadding(top = it.top)
            }
            insets
        }
    }

    private fun initToolbarMenu() {
        binding.toolbar.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.search -> openFragment(SearchFragment::class.java)
                R.id.settings -> openFragment(SettingsFragment::class.java)
                R.id.markAllAsRead -> {
                    viewModel.markAllAsRead()
                    true
                }
                R.id.notificationPermissionWarning -> {
                    onNotificationWarningClicked()
                    true
                }
                else -> false
            }
        }
    }

    private fun onNotificationWarningClicked() {
        if (_binding == null) return
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.notification_permission_required_title)
            .setMessage(R.string.notification_permission_required_message)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                notificationPrefs.markRequestedOnce()
                notificationPermissionLauncher.launch(NotificationPermissionAccess.permission)
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun updateNotificationWarningVisibility() {
        val item = binding.toolbar.menu.findItem(R.id.notificationPermissionWarning) ?: return
        val shouldShow = filter is EntriesFilter.Unread &&
            NotificationPermissionAccess.shouldShowWarning(
                context = requireContext(),
                fragment = this,
                prefs = notificationPrefs,
            )
        item.isVisible = shouldShow
        if (shouldShow) {
            val color = ContextCompat.getColor(requireContext(), R.color.warning)
            item.icon = item.icon?.mutate()?.apply { setTint(color) }
        }
    }

    private fun openFragment(cls: Class<out androidx.fragment.app.Fragment>): Boolean {
        parentFragmentManager.commit {
            replace(R.id.fragmentContainerView, cls, null)
            addToBackStack(null)
        }
        return true
    }

    private fun initList() {
        binding.list.apply {
            if (adapter == null) {
                layoutManager = LinearLayoutManager(requireContext())
                adapter = this@EntriesFragment.adapter
                addItemDecoration(
                    CardListAdapterDecoration(
                        resources.getDimensionPixelSize(R.dimen.entries_cards_gap),
                    ),
                )
            }
        }
        touchHelper?.attachToRecyclerView(binding.list)
    }

    private fun initSwipeRefresh() {
        val f = filter ?: return
        binding.swipeRefresh.isEnabled = f.swipeRefreshEnabled
        binding.swipeRefresh.setOnRefreshListener { viewModel.refresh() }
    }

    private fun observeState() {
        viewLifecycleOwner.lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.state.collect { state ->
                    renderTitle(state.title)
                    binding.swipeRefresh.isRefreshing = state.pullToRefreshInProgress
                    renderItems(state.items)
                }
            }
        }
    }

    private fun observeActions() {
        viewLifecycleOwner.lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.actions.collect { navigator.value.handle(it) }
            }
        }
    }

    private fun renderTitle(title: TitleState) {
        binding.toolbar.setTitle(
            when (title) {
                TitleState.Loading -> ""
                is TitleState.Custom -> title.title
                is TitleState.Res -> getString(title.resId, *title.args.toTypedArray())
            },
        )
    }

    private fun renderItems(items: ItemsState) {
        when (items) {
            ItemsState.Loading -> {
                binding.progress.isVisible = true
                binding.message.isVisible = false
                binding.swipeRefresh.isVisible = false
            }

            ItemsState.InitialSync -> {
                binding.progress.isVisible = true
                binding.message.isVisible = true
                binding.message.setText(R.string.initial_sync)
                binding.swipeRefresh.isVisible = false
            }

            is ItemsState.Showing -> {
                binding.progress.isVisible = false
                binding.message.isVisible = false
                binding.swipeRefresh.isVisible = true
                adapter.submitList(items.items)
                restoreScrollPositionIfNeeded()
            }

            is ItemsState.Empty -> {
                binding.progress.isVisible = false
                binding.swipeRefresh.isVisible = true
                binding.message.isVisible = true
                binding.message.text = getString(items.messageRes, *items.args.toTypedArray())
                adapter.submitList(emptyList())
            }
        }
    }

    private fun createTouchHelper(): ItemTouchHelper? {
        val policy = filter?.swipePolicy ?: return null
        val leftIcon = policy.left?.iconRes
        val rightIcon = policy.right?.iconRes
        if (leftIcon == null && rightIcon == null) return null

        val leftAction = policy.left
        val rightAction = policy.right

        return ItemTouchHelper(
            object : SwipeHelper(requireContext(), leftIcon ?: 0, rightIcon ?: 0) {
                override fun onSwiped(viewHolder: androidx.recyclerview.widget.RecyclerView.ViewHolder, direction: Int) {
                    val action = when (direction) {
                        ItemTouchHelper.LEFT -> leftAction
                        ItemTouchHelper.RIGHT -> rightAction
                        else -> null
                    } ?: return
                    val entry = adapter.currentList.getOrNull(viewHolder.bindingAdapterPosition) ?: return
                    val vm = viewModel
                    showUndoSnackbar(action.messageRes) {
                        viewLifecycleOwner.lifecycleScope.launch {
                            action.undo(vm, entry.id)
                        }
                    }
                    viewLifecycleOwner.lifecycleScope.launch {
                        action.apply(vm, entry.id)
                    }
                }
            },
        )
    }

    /**
     * Restores the list scroll position captured in [saveScrollPosition] for
     * the current filter, but only once per fragment instance — the
     * OG-image poll in the view model re-renders the same list every few
     * seconds and we don't want each render to yank the user back to the
     * saved offset.
     *
     * The actual scroll is posted to the RecyclerView so it runs after the
     * DiffUtil dispatch has laid out the new items.
     */
    private fun restoreScrollPositionIfNeeded() {
        if (scrollPositionRestored) return
        val currentFilter = filter ?: return
        scrollPositionRestored = true
        val saved = scrollState.get(currentFilter) ?: return
        binding.list.post {
            (binding.list.layoutManager as? LinearLayoutManager)
                ?.scrollToPositionWithOffset(saved.first, saved.second)
        }
    }

    /**
     * Shows a snackbar with an "undo" action that runs [onUndo] if the user
     * taps it before the snackbar dismisses. The snackbar is anchored to the
     * activity's bottom navigation so it doesn't cover the list.
     */
    private fun showUndoSnackbar(@StringRes messageRes: Int, onUndo: () -> Unit) {
        val rootView = _binding?.root ?: return
        Snackbar.make(rootView, messageRes, Snackbar.LENGTH_SHORT)
            .setAnchorView(requireActivity().findViewById(R.id.bottomNav))
            .setAction(R.string.undo) { onUndo() }
            .show()
    }
}