package co.appreactor.nextcloud.news.feeditem

import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.text.SpannableStringBuilder
import android.text.method.LinkMovementMethod
import android.text.style.BulletSpan
import android.text.style.ImageSpan
import android.text.style.QuoteSpan
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.text.HtmlCompat
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import co.appreactor.nextcloud.news.*
import kotlinx.android.synthetic.main.fragment_feed_item.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import org.koin.android.viewmodel.ext.android.viewModel
import timber.log.Timber

class FeedItemFragment : Fragment() {

    private val args: FeedItemFragmentArgs by navArgs()

    private val model: FeedItemFragmentModel by viewModel()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(
            R.layout.fragment_feed_item,
            container,
            false
        )
    }

    @SuppressLint("NewApi")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        toolbar.setNavigationOnClickListener {
            findNavController().popBackStack()
        }

        progress.isVisible = true

        lifecycleScope.launch {
            val item = model.getFeedItem(args.newsItemId)!!

            if (item.unread) {
                model.toggleReadFlag(item.id)

                launch {
                    runCatching {
                        delay(5000)
                        model.syncFeedItemsFlags()
                    }.getOrElse {
                        Timber.e(it)

                        Toast.makeText(
                            requireContext(),
                            R.string.cannot_sync_bookmarks,
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
            }

            toolbar.title = model.getFeed(item.feedId)!!.title
            title.text = item.title
            date.text = model.getDate(item)

            val imageGetter = TextViewImageGetter(textView)

            val body = HtmlCompat.fromHtml(
                item.body,
                HtmlCompat.FROM_HTML_MODE_LEGACY,
                imageGetter,
                null
            ) as SpannableStringBuilder

            val spans = body.getSpans(0, body.length - 1, Any::class.java)

            spans.forEach {
                when (it) {
                    is BulletSpan -> {
                        val radius = resources.getDimension(R.dimen.bullet_radius).toInt()
                        val gap = resources.getDimension(R.dimen.bullet_gap).toInt()

                        val span = if (Build.VERSION.SDK_INT >= 28) {
                            BulletSpan(gap, textView.currentTextColor, radius)
                        } else {
                            BulletSpan(gap, textView.currentTextColor)
                        }

                        body.setSpan(
                            span,
                            body.getSpanStart(it),
                            body.getSpanEnd(it),
                            0
                        )

                        body.removeSpan(it)
                    }

                    is QuoteSpan -> {
                        val color = date.currentTextColor
                        val stripe = resources.getDimension(R.dimen.quote_stripe_width).toInt()
                        val gap = resources.getDimension(R.dimen.quote_gap).toInt()

                        val span = if (Build.VERSION.SDK_INT >= 28) {
                            QuoteSpan(color, stripe, gap)
                        } else {
                            QuoteSpan(color)
                        }

                        body.setSpan(
                            span,
                            body.getSpanStart(it),
                            body.getSpanEnd(it),
                            0
                        )

                        body.removeSpan(it)
                    }

                    is ImageSpan -> {
                        if (body[body.getSpanEnd(it) + 1] != '\n') {
                            body.insert(body.getSpanEnd(it), "\n \n")
                        }
                    }
                }
            }

            while (body.contains("\u00A0")) {
                val index = body.indexOfFirst { it == '\u00A0' }
                body.delete(index, index + 1)
            }

            while (body.contains("\n\n\n")) {
                val index = body.indexOf("\n\n\n")
                body.delete(index, index + 1)
            }

            textView.text = body
            textView.movementMethod = LinkMovementMethod.getInstance()

            progress.isVisible = false
        }

        lifecycleScope.launchWhenResumed {
            model.getStarredFlag(args.newsItemId).collect { starred ->
                val menuItem = toolbar.menu.findItem(R.id.toggleStarred)

                if (starred) {
                    menuItem.setIcon(R.drawable.ic_baseline_bookmark_24)
                    menuItem.setTitle(R.string.remove_bookmark)
                } else {
                    menuItem.setIcon(R.drawable.ic_baseline_bookmark_border_24)
                    menuItem.setTitle(R.string.bookmark)
                }
            }
        }

        toolbar.setOnMenuItemClickListener { menuItem ->
            if (menuItem.itemId == R.id.toggleStarred) {
                lifecycleScope.launch {
                    model.toggleStarredFlag(args.newsItemId)

                    runCatching {
                        model.syncFeedItemsFlags()
                    }.getOrElse {
                        Timber.e(it)

                        Toast.makeText(
                            requireContext(),
                            R.string.cannot_sync_bookmarks,
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }

                return@setOnMenuItemClickListener true
            }

            false
        }

        fab.setOnClickListener {
            lifecycleScope.launch {
                val item = model.getFeedItem(args.newsItemId)!!
                val intent = Intent(Intent.ACTION_VIEW)
                intent.data = Uri.parse(item.url)
                startActivity(intent)
            }
        }
    }
}