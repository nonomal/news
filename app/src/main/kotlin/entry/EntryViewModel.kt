package entry

import android.app.Application
import android.text.Html
import android.text.SpannableStringBuilder
import android.widget.TextView
import androidx.core.text.HtmlCompat
import androidx.lifecycle.LifecycleCoroutineScope
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.appreactor.news.R
import common.ConfRepository
import db.Conf
import feeds.FeedsRepository
import sync.NewsApiSync
import db.Entry
import db.Link
import entries.EntriesRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import links.LinksRepository
import org.koin.android.annotation.KoinViewModel

@KoinViewModel
class EntryViewModel(
    private val app: Application,
    private val feedsRepository: FeedsRepository,
    private val entriesRepository: EntriesRepository,
    private val linksRepository: LinksRepository,
    private val confRepo: ConfRepository,
    private val newsApiSync: NewsApiSync,
) : ViewModel() {

    val state = MutableStateFlow<State?>(null)

    lateinit var conf: Conf

    suspend fun onViewCreated(
        entryId: String,
        summaryView: TextView,
        lifecycleScope: LifecycleCoroutineScope,
    ) = withContext(Dispatchers.IO) {
        conf = confRepo.select().first()

        if (state.value != null) {
            return@withContext
        }

        state.value = State.Progress

        runCatching {
            val entry = entriesRepository.selectById(entryId).first()

            if (entry == null) {
                val message = app.getString(R.string.cannot_find_entry_with_id_s, entryId)
                state.value = State.Error(message)
                return@withContext
            }

            val feed = feedsRepository.selectById(entry.feedId).first()

            if (feed == null) {
                val message = app.getString(R.string.cannot_find_feed_with_id_s, entry.feedId)
                state.value = State.Error(message)
                return@withContext
            }

            state.value = State.Success(
                feedTitle = feed.title,
                entry = entry,
                entryLinks = linksRepository.selectByEntryId(entry.id).first(),
                parsedContent = parseEntryContent(
                    entry.contentText ?: "",
                    TextViewImageGetter(
                        textView = summaryView,
                        scope = lifecycleScope,
                        baseUrl = null,
                    ),
                ),
            )
        }.onFailure {
            state.value = State.Error(it.message ?: "")
        }
    }

    fun setBookmarked(entryId: String, bookmarked: Boolean) {
        val prevState = state.value

        if (prevState is State.Success) {
            viewModelScope.launch { entriesRepository.setBookmarked(entryId, bookmarked, false) }
            state.value = prevState.copy(entry = prevState.entry.copy(bookmarked = bookmarked))
        }

        viewModelScope.launch {
            newsApiSync.sync(
                NewsApiSync.SyncArgs(
                    syncFeeds = false,
                    syncFlags = true,
                    syncEntries = false,
                )
            )
        }
    }

    private fun parseEntryContent(
        content: String,
        imageGetter: Html.ImageGetter,
    ): SpannableStringBuilder {
        val summary = HtmlCompat.fromHtml(
            content,
            HtmlCompat.FROM_HTML_MODE_LEGACY,
            imageGetter,
            null,
        ) as SpannableStringBuilder

        if (summary.isBlank()) {
            return summary
        }

        while (summary.contains("\n\n\n")) {
            val index = summary.indexOf("\n\n\n")
            summary.delete(index, index + 1)
        }

        while (summary.startsWith("\n\n")) {
            summary.delete(0, 1)
        }

        while (summary.endsWith("\n\n")) {
            summary.delete(summary.length - 2, summary.length - 1)
        }

        return summary
    }

    sealed class State {

        object Progress : State()

        data class Success(
            val feedTitle: String,
            val entry: Entry,
            val entryLinks: List<Link>,
            val parsedContent: SpannableStringBuilder,
        ) : State()

        data class Error(val message: String) : State()
    }
}