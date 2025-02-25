package feeds

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import co.appreactor.news.R
import co.appreactor.news.databinding.FragmentFeedsBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import common.AppFragment
import common.ListAdapterDecoration
import common.hide
import common.show
import common.showDialog
import common.showErrorDialog
import common.showKeyboard
import entries.EntriesFilter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.androidx.viewmodel.ext.android.viewModel

class FeedsFragment : AppFragment(lockDrawer = false) {

    private val model: FeedsModel by viewModel()

    private var _binding: FragmentFeedsBinding? = null
    private val binding get() = _binding!!

    private val adapter = FeedsAdapter(callback = object : FeedsAdapter.Callback {
        override fun onClick(item: FeedsAdapter.Item) {
            findNavController().navigate(
                FeedsFragmentDirections.actionFeedsFragmentToFeedEntriesFragment(
                    EntriesFilter.BelongToFeed(feedId = item.id)
                )
            )
        }

        override fun onSettingsClick(item: FeedsAdapter.Item) {
            findNavController().navigate(
                FeedsFragmentDirections.actionFeedsFragmentToFeedSettingsFragment(
                    feedId = item.id,
                )
            )
        }

        override fun onOpenSelfLinkClick(item: FeedsAdapter.Item) {
            val state = model.state.value

            if (state is FeedsModel.State.ShowingFeeds) {
                //openUrl(item.selfLink, state.conf.useBuiltInBrowser)
            }
        }

        override fun onOpenAlternateLinkClick(item: FeedsAdapter.Item) {
            val state = model.state.value

            if (state is FeedsModel.State.ShowingFeeds) {
                //openUrl(item.alternateLink, state.conf.useBuiltInBrowser)
            }
        }

        override fun onRenameClick(item: FeedsAdapter.Item) {
            val dialog = MaterialAlertDialogBuilder(requireContext())
                .setTitle(getString(R.string.rename))
                .setView(R.layout.dialog_rename_feed)
                .setPositiveButton(R.string.rename) { dialogInterface, _ ->
                    val dialog = dialogInterface as AlertDialog
                    val title = dialog.findViewById<TextInputEditText>(R.id.title)!!

                    viewLifecycleOwner.lifecycleScope.launch {
                        runCatching { model.rename(item.id, title.text.toString()) }.onFailure { showErrorDialog(it) }
                    }
                }
                .setNegativeButton(R.string.cancel, null)
                .setOnDismissListener { hideKeyboard() }
                .show()

            val title = dialog.findViewById<TextInputEditText>(R.id.title)!!
            title.append(item.title)

            requireContext().showKeyboard()
        }

        override fun onDeleteClick(item: FeedsAdapter.Item) {
            viewLifecycleOwner.lifecycleScope.launch {
                runCatching { model.delete(item.id) }.onFailure { showErrorDialog(it) }
            }
        }
    })

    private val importFeedsLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri == null) {
            return@registerForActivityResult
        }

        viewLifecycleOwner.lifecycleScope.launch {
            runCatching {
                val result = withContext(Dispatchers.Default) {
                    requireContext().contentResolver.openInputStream(uri)!!.use { inputStream ->
                        model.importOpml(inputStream.bufferedReader().readText())
                    }
                }

                val message = buildString {
                    append(getString(R.string.added_d, result.feedsAdded))
                    append("\n")
                    append(
                        getString(
                            R.string.exists_d,
                            result.feedsUpdated,
                        )
                    )
                    append("\n")
                    append(
                        getString(
                            R.string.failed_d,
                            result.feedsFailed,
                        )
                    )

                    if (result.errors.isNotEmpty()) {
                        append("\n\n")
                    }

                    result.errors.forEach {
                        append(it)

                        if (result.errors.last() != it) {
                            append("\n\n")
                        }
                    }
                }

                requireContext().showDialog(
                    title = getString(R.string.import_title),
                    message = message,
                )
            }.onFailure {
                showErrorDialog(it)
            }
        }
    }

    @Suppress("BlockingMethodInNonBlockingContext")
    private val exportFeedsLauncher = registerForActivityResult(
        ActivityResultContracts.CreateDocument()
    ) { uri ->
        if (uri == null) {
            return@registerForActivityResult
        }

        lifecycleScope.launchWhenResumed {
            withContext(Dispatchers.IO) {
                requireContext().contentResolver.openOutputStream(uri)?.use {
                    //it.write(model.exportAsOpml())
                }
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentFeedsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        initToolbar()
        initList()
        initImportButton()
        initFab()

        model.state
            .onEach { setState(it) }
            .catch { showErrorDialog(it) }
            .launchIn(viewLifecycleOwner.lifecycleScope)

        val args = FeedsFragmentArgs.fromBundle(requireArguments())

        if (args.url.isNotBlank()) {
            viewLifecycleOwner.lifecycleScope.launch {
                runCatching {
                    model.addFeed(args.url)
                }.onFailure {
                    showErrorDialog(it)
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun initToolbar() = toolbar?.apply {
        setTitle(R.string.feeds)
        inflateMenu(R.menu.menu_feeds)

        setOnMenuItemClickListener {
            when (it.itemId) {
                R.id.importFeeds -> {
                    importFeedsLauncher.launch("*/*")
                }

                R.id.exportFeeds -> {
                    exportFeedsLauncher.launch("feeds.opml")
                }
            }

            true
        }
    }

    private fun initList() = binding.list.apply {
        setHasFixedSize(true)
        layoutManager = LinearLayoutManager(requireContext())
        adapter = this@FeedsFragment.adapter
        addItemDecoration(ListAdapterDecoration(resources.getDimensionPixelSize(R.dimen.dp_8)))

        addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
                if (canScrollVertically(1) || !canScrollVertically(-1)) {
                    binding.fab.show()
                } else {
                    binding.fab.hide()
                }
            }
        })
    }

    private fun initImportButton() {
        binding.importOpml.setOnClickListener {
            importFeedsLauncher.launch("*/*")
        }
    }

    private fun initFab() {
        binding.fab.setOnClickListener {
            val alert = MaterialAlertDialogBuilder(requireContext())
                .setTitle(getString(R.string.add_feed))
                .setView(R.layout.dialog_add_feed)
                .setPositiveButton(R.string.add) { dialogInterface, _ ->
                    val dialog = dialogInterface as AlertDialog
                    val url = dialog.findViewById<TextInputEditText>(R.id.url)?.text.toString()
                    viewLifecycleOwner.lifecycleScope.launch {
                        runCatching { model.addFeed(url) }.onFailure { showErrorDialog(it) }
                    }
                }
                .setNegativeButton(R.string.cancel, null)
                .setOnDismissListener { hideKeyboard() }
                .show()

            alert.findViewById<EditText>(R.id.url)?.apply {
                setOnEditorActionListener { _, actionId, _ ->
                    if (actionId == EditorInfo.IME_ACTION_DONE) {
                        alert.dismiss()

                        viewLifecycleOwner.lifecycleScope.launch {
                            runCatching { model.addFeed(text.toString()) }.onFailure { showErrorDialog(it) }
                        }

                        return@setOnEditorActionListener true
                    }

                    false
                }
            }

            requireContext().showKeyboard()
        }
    }

    private fun setState(state: FeedsModel.State) = binding.apply {
        when (state) {
            FeedsModel.State.Loading -> {
                list.hide()
                progress.show(animate = true)
                message.hide()
                importOpml.hide()
                fab.hide()
            }

            is FeedsModel.State.ShowingFeeds -> {
                adapter.submitList(state.feeds)
                list.show()
                progress.hide()
                message.hide()

                if (state.feeds.isEmpty()) {
                    message.show(animate = true)
                    message.text = getString(R.string.you_have_no_feeds)
                    importOpml.show(animate = true)
                } else {
                    message.hide()
                    importOpml.hide()
                }

                fab.show()
            }

            is FeedsModel.State.ImportingFeeds -> {
                list.hide()

                progress.show(animate = true)
                message.show(animate = true)

                message.text = getString(
                    R.string.importing_feeds_n_of_n,
                    state.progress.imported,
                    state.progress.total,
                )

                importOpml.hide()
                fab.hide()
            }
        }
    }

    private fun hideKeyboard() {
        requireActivity().window.setSoftInputMode(
            WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN
        )
    }
}