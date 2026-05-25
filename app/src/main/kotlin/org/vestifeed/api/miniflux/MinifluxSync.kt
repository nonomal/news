package org.vestifeed.api.miniflux

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.vestifeed.db.Database
import org.vestifeed.db.table.FeedTable
import org.vestifeed.db.table.LinkTable
import org.vestifeed.parser.AtomLinkRel

class MinifluxSync(val db: Database, val api: Miniflux) {
    suspend fun syncFeeds() {
        val freshFeeds = api.getFeeds()
        val cachedFeeds = withContext(Dispatchers.IO) { db.feed.selectAll() }
        val freshFeedIds = freshFeeds.map { it.id }
        val cachedFeedIds = cachedFeeds.map { it.id.toLong() }
        val deletedOnServerIds = cachedFeedIds.filterNot { freshFeedIds.contains(it) }
        deletedOnServerIds.forEach {
            db.link.deleteByFeedId(it.toString())
            db.feed.deleteById(it.toString())
        }
        for (freshFeed in freshFeeds) {
            val cached = cachedFeedIds.contains(freshFeed.id)
            val (feed, freshLinks) = freshFeed.parse()
            if (cached) {
                val cachedFeed = cachedFeeds.find { it.id.toLong() == freshFeed.id }!!
                db.feed.insertOrReplace(
                    feed.copy(
                        extOpenEntriesInBrowser = cachedFeed.extOpenEntriesInBrowser,
                        extBlockedWords = cachedFeed.extBlockedWords,
                        extShowPreviewImages = cachedFeed.extShowPreviewImages,
                    )
                )
                val cachedLinks = db.link.selectByFeedId(cachedFeed.id)
                val linksDeletedOnServer =
                    cachedLinks.filter { c -> freshLinks.none { f -> c.href == f.href && c.type == f.type } }
                linksDeletedOnServer.forEach { db.link.deleteById(it.id!!) }
                for (freshLink in freshLinks) {
                    val cachedLink = cachedLinks.find { it.href == freshLink.href && it.type == freshLink.type }
                    if (cachedLink == null) {
                        db.link.insertForFeed(feed.id, listOf(freshLink))
                    } else {
                        db.link.insertForFeed(feed.id, listOf(freshLink.copy(
                            extEnclosureDownloadProgress = cachedLink.extEnclosureDownloadProgress,
                            extCacheUri = cachedLink.extCacheUri,
                        )))
                    }
                }
            } else {
                db.feed.insertOrReplace(feed)
                db.link.insertForFeed(feed.id, freshLinks)
            }
        }
    }

    private fun Miniflux.Feed.parse(): Pair<FeedTable.Feed, List<LinkTable.Link>> {
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