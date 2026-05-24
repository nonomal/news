package org.vestifeed.api

import org.vestifeed.api.miniflux.MinifluxApiBuilder
import org.vestifeed.api.standalone.StandaloneNewsApi
import org.vestifeed.db.Database
import kotlinx.coroutines.flow.Flow
import okhttp3.HttpUrl
import org.vestifeed.db.table.ConfTable
import org.vestifeed.db.table.EntryTable
import org.vestifeed.db.table.FeedTable
import org.vestifeed.db.table.LinkTable
import java.time.OffsetDateTime

class HotSwapApi(private val db: Database) : Api {

    private lateinit var api: Api

    init {
        updateApi()
    }

    private fun updateApi() {
        val conf = db.conf.select()
        api = when (conf.backend) {
            ConfTable.Backend.Embedded -> {
                StandaloneNewsApi(db)
            }

            ConfTable.Backend.Miniflux -> {
                MinifluxApiBuilder().build(
                    url = conf.minifluxUrl!!,
                    token = conf.minifluxToken!!,
                )
            }

            null -> {
                StandaloneNewsApi(db)
            }
        }
    }

    override suspend fun addFeed(url: HttpUrl): Api.AddFeedResult {
        updateApi()
        return api.addFeed(url)
    }

    override suspend fun getFeeds(): List<FeedTable.Feed> {
        updateApi()
        return api.getFeeds()
    }

    override suspend fun updateFeedTitle(feedId: String, newTitle: String): Result<Unit> {
        updateApi()
        return api.updateFeedTitle(feedId, newTitle)
    }

    override suspend fun deleteFeed(feedId: String): Result<Unit> {
        updateApi()
        return api.deleteFeed(feedId)
    }

    override suspend fun getEntries(includeReadEntries: Boolean): Flow<List<Pair<EntryTable.Entry, List<LinkTable.Link>>>> {
        updateApi()
        return api.getEntries(includeReadEntries)
    }

    override suspend fun getNewAndUpdatedEntries(
        maxEntryId: String?,
        maxEntryUpdated: OffsetDateTime?,
        lastSync: OffsetDateTime?,
    ): List<Pair<EntryTable.Entry, List<LinkTable.Link>>> {
        updateApi()
        return api.getNewAndUpdatedEntries(maxEntryId, maxEntryUpdated, lastSync)
    }

    override suspend fun markEntriesAsRead(entriesIds: List<String>, read: Boolean) {
        updateApi()
        return api.markEntriesAsRead(entriesIds, read)
    }

    override suspend fun markEntriesAsBookmarked(
        entries: List<EntryTable.EntryWithoutContent>,
        bookmarked: Boolean,
    ) {
        updateApi()
        return api.markEntriesAsBookmarked(entries, bookmarked)
    }
}