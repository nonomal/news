package org.vestifeed.api.miniflux

import java.time.OffsetDateTime

interface Miniflux {
    data class Feed(
        val id: Long,
        val title: String,
        val feedUrl: String,
        val siteUrl: String,
    )

    // https://miniflux.app/docs/api.html#endpoint-get-feeds
    suspend fun getFeeds(): List<Feed>

    data class EntriesPayload(
        val total: Long,
        val entries: List<Entry>,
    )

    data class Entry(
        val id: Long,
        val feed_id: Long,
        val status: String,
        val title: String,
        val url: String,
        val comments_url: String,
        val published_at: String,
        val created_at: String,
        val changed_at: String,
        val content: String,
        val author: String,
        val starred: Boolean,
        val enclosures: List<EntryEnclosure>?,
    )

    data class EntryEnclosure(
        val id: Long,
        val user_id: Long,
        val entry_id: Long,
        val url: String,
        val mime_type: String,
        val size: Long,
    )

    // https://miniflux.app/docs/api.html#endpoint-get-entries
    suspend fun getUnreadEntries(): List<Entry>

    // https://miniflux.app/docs/api.html#endpoint-get-entries
    suspend fun getStarredEntries(): List<Entry>

    // https://miniflux.app/docs/api.html#endpoint-get-entries
    suspend fun getEntriesChangedAfter(changedAfter: OffsetDateTime, limit: Long): List<Entry>

    // https://miniflux.app/docs/api.html#endpoint-update-entries
    suspend fun markEntriesAsRead(ids: List<Long>, read: Boolean)

    // https://miniflux.app/docs/api.html#endpoint-update-entries
    suspend fun markEntriesAsStarred(ids: List<Long>, starred: Boolean)
}