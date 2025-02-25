package feeds

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import api.NewsApi
import com.squareup.sqldelight.runtime.coroutines.asFlow
import com.squareup.sqldelight.runtime.coroutines.mapToList
import com.squareup.sqldelight.runtime.coroutines.mapToOne
import db.Database
import db.Feed
import db.Link
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import opml.exportOpml
import org.koin.android.annotation.KoinViewModel
import java.util.concurrent.atomic.AtomicInteger

@KoinViewModel
class FeedsModel(
    private val db: Database,
    private val api: NewsApi,
) : ViewModel() {

    private val _state = MutableStateFlow<State>(State.Loading)
    val state = _state.asStateFlow()

    private val hasActionInProgress = MutableStateFlow(false)
    private val importProgress = MutableStateFlow<ImportProgress?>(null)

    init {
        _state.update { State.Loading }

        combine(
            db.feedQueries.selectAll().asFlow().mapToList(),
            db.linkQueries.selectByEntryid(null).asFlow().mapToList(),
            hasActionInProgress,
            importProgress,
        ) { feeds, feedLinks, hasActionInProgress, importProgress ->
            if (importProgress != null) {
                _state.update { State.ImportingFeeds(importProgress) }
            } else {
                if (hasActionInProgress) {
                    _state.update { State.Loading }
                } else {
                    _state.update {
                        State.ShowingFeeds(
                            feeds = feeds.map { feed ->
                                feed.toItem(
                                    feedLinks.filter { it.feedId == feed.id },
                                    db.entryQueries.selectUnreadCount(feed.id).asFlow().mapToOne().first(),
                                )
                            }
                        )
                    }
                }
            }
        }.launchIn(viewModelScope)
    }

    suspend fun importOpml(opmlDocument: String): ImportResult {
        hasActionInProgress.update { true }

        return runCatching {
            val opmlFeeds = opml.importOpml(opmlDocument)
            importProgress.update { ImportProgress(0, opmlFeeds.size) }

            val added = AtomicInteger()
            val exists = AtomicInteger()
            val failed = AtomicInteger()
            val errors = mutableListOf<String>()

            val existingLinks = db.linkQueries.selectByEntryid(null).asFlow().mapToList().first()

            opmlFeeds.forEach { outline ->
                val outlineUrl = outline.xmlUrl.toHttpUrl()

                val feedAlreadyExists = existingLinks.any {
                    it.href.toUri().normalize() == outlineUrl.toUri().normalize()
                }

                if (feedAlreadyExists) {
                    exists.incrementAndGet()
                } else {
                    api.addFeed(outlineUrl).onSuccess { result ->
                        db.transaction {
                            db.feedQueries.insert(result.first)
                            result.second.forEach { db.linkQueries.insert(it) }
                        }

                        added.incrementAndGet()
                    }.onFailure {
                        errors += "Failed to import feed ${outline.xmlUrl}\nReason: ${it.message}"
                        failed.incrementAndGet()
                    }

                    importProgress.update {
                        ImportProgress(
                            imported = added.get() + exists.get() + failed.get(),
                            total = opmlFeeds.size,
                        )
                    }
                }
            }

            ImportResult(
                feedsAdded = added.get(),
                feedsUpdated = exists.get(),
                feedsFailed = failed.get(),
                errors = errors,
            )
        }.onSuccess {
            importProgress.update { null }
            hasActionInProgress.update { false }
        }.getOrElse {
            importProgress.update { null }
            hasActionInProgress.update { false }
            throw it
        }
    }

    suspend fun exportOpml(): ByteArray {
        val feeds = db.feedQueries.selectAll().asFlow().mapToList().first()
        val links = db.linkQueries.selectByEntryid(null).asFlow().mapToList().first()
        val feedsWithLinks = feeds.map { feed -> Pair(feed, links.filter { it.feedId == feed.id }) }
        return exportOpml(feedsWithLinks).toByteArray()
    }

    suspend fun addFeed(url: String) {
        hasActionInProgress.update { true }
        val fullUrl = if (!url.startsWith("http")) "https://$url" else url

        runCatching {
            withContext(Dispatchers.Default) {
                val feed = api.addFeed(fullUrl.toHttpUrl()).getOrThrow()

                db.transaction {
                    db.linkQueries.deleteByFeedId(feed.first.id)
                    db.feedQueries.insertOrReplace(feed.first)
                    feed.second.forEach { db.linkQueries.insertOrReplace(it) }
                }
            }
        }.onSuccess {
            hasActionInProgress.update { false }
        }.onFailure {
            hasActionInProgress.update { false }
        }.getOrThrow()
    }

    suspend fun rename(feedId: String, newTitle: String) {
        hasActionInProgress.update { true }

        runCatching {
            withContext(Dispatchers.Default) {
                val feed = db.feedQueries.selectById(feedId).asFlow().mapToOne().first()
                val trimmedNewTitle = newTitle.trim()
                api.updateFeedTitle(feedId, trimmedNewTitle)

                withContext(Dispatchers.Default) {
                    db.feedQueries.insertOrReplace(feed.copy(title = trimmedNewTitle))
                }
            }
        }.onSuccess {
            hasActionInProgress.update { false }
        }.onFailure {
            hasActionInProgress.update { false }
        }.getOrThrow()
    }

    suspend fun delete(feedId: String) {
        hasActionInProgress.update { true }

        runCatching {
            withContext(Dispatchers.Default) {
                api.deleteFeed(feedId)

                db.transaction {
                    db.linkQueries.deleteByFeedId(feedId)
                    db.feedQueries.deleteById(feedId)
                    val entries = db.entryQueries.selectByFeedId(feedId).executeAsList()
                    entries.forEach { db.linkQueries.selectByEntryid(it.id) }
                    db.entryQueries.deleteByFeedId(feedId)
                }
            }
        }.onSuccess {
            hasActionInProgress.update { false }
        }.onFailure {
            hasActionInProgress.update { false }
        }.getOrThrow()
    }

    private fun Feed.toItem(links: List<Link>, unreadCount: Long): FeedsAdapter.Item {
        return FeedsAdapter.Item(
            id = id,
            title = title,
            selfLink = links.firstOrNull { it.rel == "self" }?.href?.toString() ?: "",
            alternateLink = links.firstOrNull { it.rel == "alternate" }?.href?.toString() ?: "",
            unreadCount = unreadCount,
        )
    }

    sealed class State {
        object Loading : State()
        data class ShowingFeeds(val feeds: List<FeedsAdapter.Item>) : State()
        data class ImportingFeeds(val progress: ImportProgress) : State()
    }

    data class ImportProgress(
        val imported: Int,
        val total: Int,
    )

    data class ImportResult(
        val feedsAdded: Int,
        val feedsUpdated: Int,
        val feedsFailed: Int,
        val errors: List<String>,
    )
}