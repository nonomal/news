package org.vestifeed.api

import kotlinx.coroutines.flow.Flow
import okhttp3.HttpUrl
import org.vestifeed.db.table.EntryTable
import org.vestifeed.db.table.FeedTable
import org.vestifeed.db.table.LinkTable
import java.time.OffsetDateTime

interface Api {
    data class AddFeedResult(
        val feed: FeedTable.Feed,
        val feedLinks: List<LinkTable.Link>,
        val entries: List<Pair<EntryTable.Entry, List<LinkTable.Link>>>,
    )

    suspend fun addFeed(url: HttpUrl): AddFeedResult

    suspend fun getFeeds(): List<FeedTable.Feed>

    suspend fun updateFeedTitle(
        feedId: String,
        newTitle: String,
    ): Result<Unit>

    suspend fun deleteFeed(feedId: String): Result<Unit>

    suspend fun getEntries(includeReadEntries: Boolean): Flow<List<Pair<EntryTable.Entry, List<LinkTable.Link>>>>

    suspend fun getNewAndUpdatedEntries(
        maxEntryId: String?,
        maxEntryUpdated: OffsetDateTime?,
        lastSync: OffsetDateTime?,
    ): List<Pair<EntryTable.Entry, List<LinkTable.Link>>>

    suspend fun markEntriesAsRead(
        entriesIds: List<String>,
        read: Boolean,
    )

    suspend fun markEntriesAsBookmarked(
        entries: List<EntryTable.EntryWithoutContent>,
        bookmarked: Boolean,
    )
}