package org.vestifeed.search

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.os.bundleOf
import androidx.core.view.isVisible
import androidx.core.widget.addTextChangedListener
import org.vestifeed.navigation.AppFragment
import androidx.fragment.app.commit
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import org.vestifeed.parser.AtomLinkRel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.vestifeed.R
import org.vestifeed.app.db
import org.vestifeed.app.sync
import org.vestifeed.databinding.FragmentSearchBinding
import org.vestifeed.dialog.showErrorDialog
import org.vestifeed.entries.CardListAdapterDecoration
import org.vestifeed.entries.EntriesAdapter
import org.vestifeed.entries.EntryRowMapper
import org.vestifeed.entry.EntryFragment
import org.vestifeed.navigation.hideKeyboard
import org.vestifeed.navigation.openUrl
import org.vestifeed.navigation.showKeyboard

class SearchFragment : AppFragment() {

    private val args = MutableStateFlow<Args?>(null)

    private val _state = MutableStateFlow<State>(State.QueryIsEmpty)
    private val state = _state.asStateFlow()

    private var _binding: FragmentSearchBinding? = null
    private val binding get() = _binding!!

    private val adapter by lazy {
        EntriesAdapter(requireActivity()) { onListItemClick(it) }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = FragmentSearchBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        initToolbar()
        initList()

        viewLifecycleOwner.lifecycleScope.launch {
            args.filterNotNull().collect { args ->
                if (args.query.length < 3) {
                    _state.update { State.QueryIsTooShort }
                    return@collect
                }

                _state.update { State.RunningQuery }

                val rows = withContext(Dispatchers.IO) { db().entry.selectByQuery(args.query) }
                val conf = withContext(Dispatchers.IO) { db().conf.select() }
                val items = rows.map { EntryRowMapper.toItem(it, conf) }

                _state.update { State.ShowingQueryResults(items) }
            }
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

    private fun setArgs(args: Args) {
        this.args.update { args }
    }

    private fun markAsRead(entryId: String) {
        viewLifecycleOwner.lifecycleScope.launch {
            runCatching {
                db().entry.updateReadAndReadSynced(
                    id = entryId,
                    extRead = true,
                    extReadSynced = false,
                )

                sync().runInBackground()
            }.onFailure { showErrorDialog(it) }
        }
    }

    private fun initToolbar() {
        binding.toolbar.setNavigationOnClickListener {
            hideKeyboard(binding.query)
            parentFragmentManager.popBackStack()
        }

        binding.query.addTextChangedListener(
            afterTextChanged = {
                binding.clear.isVisible = it!!.isNotEmpty()
                setArgs(Args(query = it.toString()))
            }
        )

        binding.query.requestFocus()
        binding.query.postDelayed({ showKeyboard(binding.query) }, 300)

        binding.clear.setOnClickListener { binding.query.setText("") }
    }

    private fun initList() {
        binding.list.apply {
            setHasFixedSize(true)
            layoutManager = LinearLayoutManager(context)
            this.adapter = this@SearchFragment.adapter
            val cardsGapPx = resources.getDimensionPixelSize(R.dimen.entries_cards_gap)
            addItemDecoration(CardListAdapterDecoration(cardsGapPx))
        }
    }

    private fun FragmentSearchBinding.setState(state: State) {
        listOf(toolbar, list, progress, message).forEach { it.isVisible = false }

        when (state) {
            is State.QueryIsEmpty,
            is State.QueryIsTooShort -> {
                listOf(toolbar).forEach { it.isVisible = true }
                message.isVisible = true
                message.setText(R.string.type_at_least_3_characters_to_search)
            }

            is State.RunningQuery -> listOf(toolbar, progress).forEach { it.isVisible = true }

            is State.ShowingQueryResults -> {
                listOf(toolbar, list).forEach { it.isVisible = true }
                if (state.items.isEmpty()) {
                    message.isVisible = true
                    message.setText(R.string.no_results)
                }
            }
        }

        if (state is State.ShowingQueryResults) {
            adapter.submitList(state.items)
        }
    }

    private fun onListItemClick(item: EntriesAdapter.Item) {
        markAsRead(item.id)

        if (item.openInBrowser) {
            val links = db().link.selectByEntryId(item.id)
            val htmlLink =
                links.firstOrNull { it.rel is AtomLinkRel.Alternate && it.type == "text/html" }

            if (htmlLink != null) {
                openUrl(
                    url = htmlLink.href,
                    useBuiltInBrowser = item.useBuiltInBrowser,
                )
            } else {
                openEntryFragment(item.id)
            }
        } else {
            openEntryFragment(item.id)
        }
    }

    private fun openEntryFragment(entryId: String) {
        parentFragmentManager.commit {
            replace(
                R.id.fragmentContainerView,
                EntryFragment::class.java,
                bundleOf("entryId" to entryId),
            )
            addToBackStack(null)
        }
    }

    data class Args(
        val query: String,
    )

    sealed class State {
        object QueryIsEmpty : State()
        object QueryIsTooShort : State()
        object RunningQuery : State()
        data class ShowingQueryResults(val items: List<EntriesAdapter.Item>) : State()
    }
}