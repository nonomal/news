package entry

import android.content.ActivityNotFoundException
import android.content.Intent
import android.graphics.Rect
import android.os.Bundle
import android.text.SpannableStringBuilder
import android.text.method.LinkMovementMethod
import android.text.style.BulletSpan
import android.text.style.QuoteSpan
import android.text.style.URLSpan
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.net.toUri
import androidx.core.view.isVisible
import androidx.core.view.iterator
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import co.appreactor.feedk.AtomLinkRel
import co.appreactor.news.R
import co.appreactor.news.databinding.FragmentEntryBinding
import db.Entry
import db.Link
import dialog.showErrorDialog
import enclosures.EnclosuresAdapter
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import navigation.openUrl
import org.koin.androidx.viewmodel.ext.android.viewModel
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

class EntryFragment : Fragment() {

    private val args: EntryFragmentArgs by navArgs()

    private val model: EntryModel by viewModel()

    private var _binding: FragmentEntryBinding? = null
    private val binding get() = _binding!!

    private val enclosuresAdapter = createEnclosuresAdapter()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = FragmentEntryBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.toolbar.setNavigationOnClickListener { findNavController().popBackStack() }

        binding.enclosures.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = enclosuresAdapter
            addItemDecoration(CardListAdapterDecoration(resources.getDimensionPixelSize(R.dimen.dp_16)))
        }

        model.setArgs(
            EntryModel.Args(
                entryId = args.entryId,
                summaryView = binding.summaryView,
                lifecycleScope = lifecycleScope,
            )
        )

        model.state
            .onEach { setState(it) }
            .launchIn(viewLifecycleOwner.lifecycleScope)

        binding.apply {
            scrollView.setOnScrollChangeListener { _, _, _, _, _ ->
                if (scrollView.canScrollVertically(1)) {
                    fab.show()
                } else {
                    fab.hide()
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun setState(state: EntryModel.State) {
        binding.apply {
            val menu = binding.toolbar.menu!!

            when (state) {
                EntryModel.State.Progress -> {
                    menu.iterator().forEach { it.isVisible = false }
                    contentContainer.isVisible = false
                    progress.isVisible = true
                    fab.hide()
                }

                is EntryModel.State.Success -> {
                    menu.findItem(R.id.toggleBookmarked)?.isVisible = true
                    menu.findItem(R.id.comments)?.apply {
                        isVisible = state.entry.ext_comments_url.isNotBlank()
                        setOnMenuItemClickListener {
                            openUrl(
                                state.entry.ext_comments_url,
                                model.conf.value.use_built_in_browser
                            )
                            true
                        }
                    }
                    menu.findItem(R.id.feedSettings)?.isVisible = true
                    menu.findItem(R.id.share)?.isVisible = true

                    contentContainer.isVisible = true
                    binding.toolbar.title = state.feedTitle

                    binding.toolbar.setOnMenuItemClickListener {
                        onMenuItemClick(
                            menuItem = it,
                            entry = state.entry,
                            entryLinks = state.entryLinks,
                        )
                    }

                    updateBookmarkedButton(state.entry.ext_bookmarked)
                    title.text = state.entry.title
                    val format =
                        DateTimeFormatter.ofLocalizedDate(FormatStyle.LONG)
                    date.text = format.format(state.entry.published)
                    state.parsedContent.applyStyle(summaryView)
                    summaryView.text = state.parsedContent
                    summaryView.movementMethod = LinkMovementMethod.getInstance()
                    progress.isVisible = false

                    enclosuresAdapter.submitList(state.entryLinks
                        .filter { it.rel is AtomLinkRel.Enclosure }
                        .filter { it.type?.startsWith("audio") ?: false }
                        .mapIndexed { index, enclosure ->
                            EnclosuresAdapter.Item(
                                entryId = state.entry.id,
                                enclosure = enclosure,
                                primaryText = getString(R.string.audio_n, index + 1),
                                secondaryText = enclosure.href.toString()
                            )
                        })

                    val firstHtmlLink =
                        state.entryLinks.firstOrNull { it.rel is AtomLinkRel.Alternate && it.type == "text/html" }

                    if (firstHtmlLink == null) {
                        fab.hide()
                    } else {
                        fab.show()
                        fab.setOnClickListener {
                            openUrl(
                                firstHtmlLink.href.toString(),
                                model.conf.value.use_built_in_browser
                            )
                        }
                    }
                }

                is EntryModel.State.Error -> {
                    menu.iterator().forEach { it.isVisible = false }
                    contentContainer.isVisible = false
                    showErrorDialog(state.message) { findNavController().popBackStack() }
                }
            }
        }
    }

    private fun onMenuItemClick(
        menuItem: MenuItem?,
        entry: Entry,
        entryLinks: List<Link>,
    ): Boolean {
        when (menuItem?.itemId) {
            R.id.toggleBookmarked -> {
                lifecycleScope.launchWhenResumed {
                    model.setBookmarked(
                        entry.id,
                        !entry.ext_bookmarked,
                    )
                }

                return true
            }

            R.id.feedSettings -> {
                findNavController().navigate(
                    EntryFragmentDirections.actionEntryFragmentToFeedSettingsFragment(
                        feedId = entry.feed_id,
                    )
                )

                return true
            }

            R.id.share -> {
                val firstAlternateLink =
                    entryLinks.firstOrNull { it.rel is AtomLinkRel.Alternate } ?: return true

                val intent = Intent().apply {
                    action = Intent.ACTION_SEND
                    type = "text/plain"
                    putExtra(Intent.EXTRA_SUBJECT, entry.title)
                    putExtra(Intent.EXTRA_TEXT, firstAlternateLink.href.toString())
                }

                startActivity(Intent.createChooser(intent, ""))
                return true
            }
        }

        return false
    }

    private fun updateBookmarkedButton(bookmarked: Boolean) {
        binding.toolbar.menu?.findItem(R.id.toggleBookmarked)?.apply {
            if (bookmarked) {
                setIcon(R.drawable.ic_baseline_bookmark_24)
                setTitle(R.string.remove_bookmark)
            } else {
                setIcon(R.drawable.ic_baseline_bookmark_border_24)
                setTitle(R.string.bookmark)
            }
        }
    }

    fun downloadAudioEnclosure(enclosure: Link) {
        viewLifecycleOwner.lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.RESUMED) {
                runCatching { model.downloadAudioEnclosure(enclosure) }
                    .onFailure { showErrorDialog(it) }
            }
        }
    }

    fun playAudioEnclosure(enclosure: Link) {
        val intent = Intent(Intent.ACTION_VIEW)
        intent.setDataAndType(enclosure.extCacheUri!!.toUri(), enclosure.type)

        runCatching {
            startActivity(intent)
        }.onFailure {
            if (it is ActivityNotFoundException) {
                showErrorDialog(getString(R.string.you_have_no_apps_which_can_play_this_podcast))
            } else {
                showErrorDialog(it)
            }
        }
    }

    private fun deleteEnclosure(enclosure: Link) {
        viewLifecycleOwner.lifecycleScope.launch {
            runCatching { model.deleteEnclosure(enclosure) }
                .onFailure { showErrorDialog(it) }
        }
    }

    private fun SpannableStringBuilder.applyStyle(textView: TextView) {
        val spans = getSpans(0, length - 1, Any::class.java)

        spans.forEach {
            when (it) {
                is BulletSpan -> {
                    val radius = resources.getDimension(R.dimen.bullet_radius).toInt()
                    val gap = resources.getDimension(R.dimen.bullet_gap).toInt()

                    setSpan(
                        BulletSpan(gap, textView.currentTextColor, radius),
                        getSpanStart(it),
                        getSpanEnd(it),
                        0
                    )

                    removeSpan(it)
                }

                is QuoteSpan -> {
                    val color = binding.date.currentTextColor
                    val stripe = resources.getDimension(R.dimen.quote_stripe_width).toInt()
                    val gap = resources.getDimension(R.dimen.quote_gap).toInt()

                    setSpan(
                        QuoteSpan(color, stripe, gap),
                        getSpanStart(it),
                        getSpanEnd(it),
                        0
                    )

                    removeSpan(it)
                }

                is URLSpan -> {
                    if (it.url.startsWith("#")) {
                        removeSpan(it)
                    }
                }
            }
        }
    }

    private fun createEnclosuresAdapter(): EnclosuresAdapter {
        return EnclosuresAdapter(object : EnclosuresAdapter.Callback {
            override fun onDownloadClick(item: EnclosuresAdapter.Item) {
                downloadAudioEnclosure(item.enclosure)
            }

            override fun onPlayClick(item: EnclosuresAdapter.Item) {
                playAudioEnclosure(item.enclosure)
            }

            override fun onDeleteClick(item: EnclosuresAdapter.Item) {
                deleteEnclosure(item.enclosure)
            }
        })
    }

    private class CardListAdapterDecoration(private val gapInPixels: Int) :
        RecyclerView.ItemDecoration() {

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