package org.vestifeed.api.miniflux

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.vestifeed.db.Database
import org.vestifeed.db.table.FeedTable
import org.vestifeed.db.table.LinkTable
import org.vestifeed.parser.AtomLinkRel

class MinifluxSync(val db: Database, val api: Miniflux) {
    suspend fun syncFeeds() {
        val freshFeeds = api.getFeeds().map { it.toVestiFeed().first }
        val cachedFeeds = withContext(Dispatchers.IO) { db.feed.selectAll() }
        val insertQueue = withContext(Dispatchers.IO) {
            freshFeeds.map {
                val cachedFeed = cachedFeeds.find { cached -> cached.id == it.id }
                if (cachedFeed == null) {
                    it
                } else {
                    it.copy(
                        extOpenEntriesInBrowser = cachedFeed.extOpenEntriesInBrowser,
                        extBlockedWords = cachedFeed.extBlockedWords,
                        extShowPreviewImages = cachedFeed.extShowPreviewImages,
                    )
                }
            }
        }
        withContext(Dispatchers.IO) {
            db.transaction {
                db.feed.deleteAll()
                db.feed.insertOrReplace(insertQueue)
            }
        }
    }

    private fun Miniflux.Feed.toVestiFeed(): Pair<FeedTable.Feed, List<LinkTable.Link>> {
        val feedId = id.toString()

        val selfLink = LinkTable.Link(
            id = null,
            feedId = feedId,
            entryId = null,
            href = feedUrl,
            rel = AtomLinkRel.Self,
            type = null,
            hreflang = null,
            title = null,
            length = null,
            extEnclosureDownloadProgress = null,
            extCacheUri = null,
        )
        val alternateLink = LinkTable.Link(
            id = null,
            feedId = feedId,
            entryId = null,
            href = siteUrl,
            rel = AtomLinkRel.Alternate,
            type = "text/html",
            hreflang = null,
            title = null,
            length = null,
            extEnclosureDownloadProgress = null,
            extCacheUri = null,
        )
        val feed = FeedTable.Feed(
            id = feedId,
            title = title,
            extOpenEntriesInBrowser = false,
            extBlockedWords = "",
            extShowPreviewImages = null,
        )
        return Pair(feed, listOf(selfLink, alternateLink))
    }
}