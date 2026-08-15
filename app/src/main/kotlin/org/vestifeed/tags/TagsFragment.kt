package org.vestifeed.tags

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AlertDialog
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
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.vestifeed.R
import org.vestifeed.app.App
import org.vestifeed.app.db
import org.vestifeed.databinding.FragmentTagsBinding
import org.vestifeed.db.table.ConfTable
import org.vestifeed.db.table.TagTable
import org.vestifeed.dialog.showErrorDialog
import org.vestifeed.entries.EntriesFilter
import org.vestifeed.entries.EntriesFragment
import org.vestifeed.entries.toBundle
import org.vestifeed.navigation.AppFragment
import org.vestifeed.navigation.showKeyboard
import org.vestifeed.settings.SettingsFragment
import java.util.UUID

class TagsFragment : AppFragment() {

    sealed class State {
        object Loading : State()
        data class ShowingTags(val tags: List<TagsAdapter.Item>) : State()
    }

    private val db by lazy { (requireContext().applicationContext as App).db }

    private val isEditable: Boolean by lazy {
        db.conf.select().backend != ConfTable.Backend.Miniflux
    }

    private val state = MutableStateFlow<State>(State.Loading)

    private var _binding: FragmentTagsBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = FragmentTagsBinding.inflate(inflater, container, false)
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

        // When the Tags tab is the root fragment (shown directly by the
        // bottom nav), there's nothing to pop — hide the up arrow so the
        // screen reads as a top-level destination. When reached from the
        // Feeds screen (which pushes it on the back stack), the arrow is
        // kept so the user can return.
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
            setHasFixedSize(true)
            adapter = createTagsAdapter()
            layoutManager = LinearLayoutManager(requireContext())
        }

        binding.fab.isVisible = isEditable
        binding.fab.setOnClickListener { showAddTagDialog() }

        viewLifecycleOwner.lifecycleScope.launch {
            state.update { State.Loading }
            val items = withContext(Dispatchers.IO) { loadTags() }
            state.update { State.ShowingTags(items) }
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

    private fun loadTags(): List<TagsAdapter.Item> {
        val tags = db.tag.selectAll()
        return tags.map { tag ->
            val feedIds = db.feedTag.selectFeedIdsByTagId(tag.id)
            TagsAdapter.Item(
                id = tag.id,
                name = tag.name,
                feedCount = feedIds.size.toLong(),
                unreadCount = db.entry.selectUnreadCountByTagId(tag.id),
                editable = isEditable,
            )
        }
    }

    private fun createTagsAdapter(): TagsAdapter {
        return TagsAdapter(callback = object : TagsAdapter.Callback {
            override fun onClick(item: TagsAdapter.Item) {
                parentFragmentManager.commit {
                    replace(
                        R.id.fragmentContainerView,
                        EntriesFragment::class.java,
                        EntriesFilter.BelongToTag(tagId = item.id).toBundle(),
                    )
                    addToBackStack(null)
                }
            }

            override fun onRenameClick(item: TagsAdapter.Item) {
                showRenameTagDialog(item)
            }

            override fun onDeleteClick(item: TagsAdapter.Item) {
                deleteTag(item.id)
            }
        })
    }

    private fun showAddTagDialog() {
        val dialog =
            MaterialAlertDialogBuilder(requireContext()).setTitle(getString(R.string.add_tag))
                .setView(R.layout.dialog_rename_tag)
                .setPositiveButton(R.string.add) { dialogInterface, _ ->
                    onAddTagSubmit(dialogInterface as AlertDialog)
                }
                .setNegativeButton(R.string.cancel, null).show()

        val titleView = dialog.findViewById<TextInputEditText>(R.id.title)!!
        titleView.requestFocus()
        titleView.postDelayed({ showKeyboard(titleView) }, 300)
    }

    private fun showRenameTagDialog(item: TagsAdapter.Item) {
        val dialog =
            MaterialAlertDialogBuilder(requireContext()).setTitle(getString(R.string.rename_tag))
                .setView(R.layout.dialog_rename_tag)
                .setPositiveButton(R.string.rename) { dialogInterface, _ ->
                    onRenameTagSubmit(item.id, dialogInterface as AlertDialog)
                }
                .setNegativeButton(R.string.cancel, null).show()

        val titleView = dialog.findViewById<TextInputEditText>(R.id.title)!!
        titleView.append(item.name)
        titleView.requestFocus()
        titleView.postDelayed({ showKeyboard(titleView) }, 300)
    }

    private fun onAddTagSubmit(dialogInterface: AlertDialog) {
        val title = (dialogInterface).findViewById<TextInputEditText>(R.id.title)?.text.toString()
        val trimmed = title.trim()
        if (trimmed.isBlank()) {
            showErrorDialog(R.string.field_is_empty)
            return
        }
        viewLifecycleOwner.lifecycleScope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    db.tag.insertOrReplace(
                        TagTable.Tag(
                            id = UUID.randomUUID().toString(),
                            name = trimmed,
                            extSource = TagTable.Source.Embedded,
                            extMinifluxId = null,
                        )
                    )
                }
            }.onSuccess {
                reloadTags()
            }.onFailure { e -> showErrorDialog(e) }
        }
    }

    private fun onRenameTagSubmit(tagId: String, dialogInterface: AlertDialog) {
        val title = (dialogInterface).findViewById<TextInputEditText>(R.id.title)?.text.toString()
        val trimmed = title.trim()
        if (trimmed.isBlank()) {
            showErrorDialog(R.string.field_is_empty)
            return
        }
        viewLifecycleOwner.lifecycleScope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    val existing = db.tag.selectById(tagId)
                        ?: throw Exception(getString(R.string.cannot_find_tag_with_id_s, tagId))
                    db.tag.insertOrReplace(
                        existing.copy(name = trimmed)
                    )
                }
            }.onSuccess {
                reloadTags()
            }.onFailure { e -> showErrorDialog(e) }
        }
    }

    private fun deleteTag(tagId: String) {
        viewLifecycleOwner.lifecycleScope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    db.transaction {
                        db.feedTag.deleteByTagId(tagId)
                        db.tag.deleteById(tagId)
                    }
                }
            }.onSuccess {
                reloadTags()
            }.onFailure { e -> showErrorDialog(e) }
        }
    }

    private fun reloadTags() {
        viewLifecycleOwner.lifecycleScope.launch {
            val items = withContext(Dispatchers.IO) { loadTags() }
            state.update { State.ShowingTags(items) }
        }
    }

    private fun FragmentTagsBinding.setState(state: State) {
        listOf(toolbar, list, progress, message, fab).forEach { it.isVisible = false }

        when (state) {
            is State.Loading -> listOf(toolbar, progress).forEach { it.isVisible = true }
            is State.ShowingTags -> {
                listOf(toolbar, list).forEach { it.isVisible = true }
                // The "Add tag" FAB is hidden in Miniflux mode because tags
                // are read-only there; the same flag controls the
                // per-row rename/delete affordance.
                if (isEditable) fab.isVisible = true
                (binding.list.adapter as? TagsAdapter)?.submitList(state.tags)

                if (state.tags.isEmpty()) {
                    message.isVisible = true
                    message.text = getString(R.string.you_have_no_tags)
                }
            }
        }
    }
}
