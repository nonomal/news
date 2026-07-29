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
import androidx.fragment.app.commit
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
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

    private val navigator = lazy { EntriesNavigator(this) }

    private val adapter by lazy {
        EntriesAdapter(requireActivity()) { viewModel.onItemClicked(it) }
            .also { it.scrollToTopOnInsert(binding.list, ::isViewAlive) }
    }

    private val touchHelper by lazy { createTouchHelper() }

    private val notificationPrefs by lazy { NotificationPermissionPrefs(requireContext()) }

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
        super.onDestroyView()
        _binding = null
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