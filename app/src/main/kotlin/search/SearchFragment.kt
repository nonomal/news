package search

import android.graphics.Rect
import android.os.Build
import android.os.Bundle
import android.text.Editable
import android.util.DisplayMetrics
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import androidx.appcompat.graphics.drawable.DrawerArrowDrawable
import androidx.core.content.getSystemService
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import co.appreactor.news.R
import co.appreactor.news.databinding.FragmentSearchBinding
import com.google.android.material.internal.TextWatcherAdapter
import entries.EntriesAdapter
import entries.EntriesAdapterCallback
import entries.EntriesAdapterItem
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import navigation.hideKeyboard
import navigation.showKeyboard
import org.koin.androidx.viewmodel.ext.android.viewModel

class SearchFragment : Fragment() {

    private val args by lazy { SearchFragmentArgs.fromBundle(requireArguments()) }

    private val model: SearchModel by viewModel()

    private var _binding: FragmentSearchBinding? = null
    private val binding get() = _binding!!

    private val adapter by lazy {
        EntriesAdapter(
            callback = adapterCallback,
            screenWidth = screenWidth(),
        )
    }

    private val adapterCallback = object : EntriesAdapterCallback {
        override fun onItemClick(item: EntriesAdapterItem) {
            TODO()
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSearchBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.toolbar.navigationIcon = DrawerArrowDrawable(context).also { it.progress = 1f }

        binding.toolbar.setNavigationOnClickListener {
            requireContext().hideKeyboard(binding.query)
            findNavController().popBackStack()
        }

        model.query
            .onEach { binding.clear.isVisible = it.isNotEmpty() }
            .launchIn(viewLifecycleOwner.lifecycleScope)

        binding.query.addTextChangedListener(object : TextWatcherAdapter() {
            override fun afterTextChanged(s: Editable) {
                model.setQuery(s.toString())
            }
        })

        binding.clear.setOnClickListener {
            binding.query.setText("")
        }

        binding.query.requestFocus()
        requireContext().showKeyboard()

        binding.list.setHasFixedSize(true)
        binding.list.layoutManager = LinearLayoutManager(context)
        binding.list.adapter = adapter
        binding.list.addItemDecoration(
            CardListAdapterDecoration(
                resources.getDimensionPixelSize(
                    R.dimen.entries_cards_gap
                )
            )
        )

        model.setFilter(args.filter!!)

        model.state
            .onEach { setState(it) }
            .launchIn(viewLifecycleOwner.lifecycleScope)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun setState(state: SearchModel.State) {
        binding.progress.isVisible = false

        when (state) {
            SearchModel.State.Loading -> {
                binding.progress.isVisible = true
            }
            is SearchModel.State.Loaded -> {
                binding.progress.isVisible = false
                val items = state.items
                binding.message.isVisible = items.isEmpty()
                adapter.submitList(items)
            }
        }
    }

    private fun screenWidth(): Int {
        return when {
            Build.VERSION.SDK_INT >= 31 -> {
                val windowManager = requireContext().getSystemService<WindowManager>()!!
                windowManager.currentWindowMetrics.bounds.width()
            }
            Build.VERSION.SDK_INT >= 30 -> {
                val displayMetrics = DisplayMetrics()
                @Suppress("DEPRECATION")
                requireContext().display?.getRealMetrics(displayMetrics)
                displayMetrics.widthPixels
            }
            else -> {
                val displayMetrics = DisplayMetrics()
                @Suppress("DEPRECATION")
                requireActivity().windowManager.defaultDisplay.getMetrics(displayMetrics)
                displayMetrics.widthPixels
            }
        }
    }

    private class CardListAdapterDecoration(private val gapInPixels: Int) : RecyclerView.ItemDecoration() {

        override fun getItemOffsets(
            outRect: Rect,
            view: View,
            parent: RecyclerView,
            state: RecyclerView.State,
        ) {
            val position = parent.getChildAdapterPosition(view)

            val bottomGap = if (position == (parent.adapter?.itemCount ?: 0) - 1) {
                gapInPixels
            } else {
                0
            }

            outRect.set(gapInPixels, gapInPixels, gapInPixels, bottomGap)
        }
    }
}