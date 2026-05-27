package org.vestifeed.api.miniflux

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.vestifeed.db.Database
import org.vestifeed.db.table.EntryTable
import org.vestifeed.db.table.FeedTable
import org.vestifeed.db.table.LinkTable
import org.vestifeed.db.table.LogTable
import org.vestifeed.parser.AtomLinkRel
import java.time.Instant
import java.time.OffsetDateTime

class MinifluxSync(val db: Database, val api: Miniflux) {
    suspend fun syncFeeds() {
        val freshFeeds = api.getFeeds()
        val cachedFeeds = withContext(Dispatchers.IO) { db.feed.selectAll() }
        val freshFeedIds = freshFeeds.map { it.id }
        val cachedFeedIds = cachedFeeds.map { it.id.toLong() }
        val deletedOnServerIds = cachedFeedIds.filterNot { freshFeedIds.contains(it) }
        withContext(Dispatchers.IO) {
            deletedOnServerIds.forEach {
                db.link.deleteByFeedId(it.toString())
                db.feed.deleteById(it.toString())
            }
        }
        for (freshFeed in freshFeeds) {
            val cached = cachedFeedIds.contains(freshFeed.id)
            val (feed, freshLinks) = freshFeed.parse()
            if (cached) {
                val cachedFeed = cachedFeeds.find { it.id.toLong() == freshFeed.id }!!
                withContext(Dispatchers.IO) {
                    db.feed.insertOrReplace(
                        feed.copy(
                            extOpenEntriesInBrowser = cachedFeed.extOpenEntriesInBrowser,
                            extBlockedWords = cachedFeed.extBlockedWords,
                            extShowPreviewImages = cachedFeed.extShowPreviewImages,
                        )
                    )
                }
                val cachedLinks =
                    withContext(Dispatchers.IO) { db.link.selectByFeedId(cachedFeed.id) }
                val linksDeletedOnServer =
                    cachedLinks.filter { c -> freshLinks.none { f -> c.href == f.href && c.type == f.type } }
                withContext(Dispatchers.IO) {
                    linksDeletedOnServer.forEach { db.link.deleteById(it.id!!) }
                }
                for (freshLink in freshLinks) {
                    val cachedLink =
                        cachedLinks.find { it.href == freshLink.href && it.type == freshLink.type }
                    withContext(Dispatchers.IO) {
                        if (cachedLink == null) {
                            db.link.insertForFeed(feed.id, listOf(freshLink))
                        } else {
                            db.link.insertForFeed(
                                feed.id, listOf(
                                    freshLink.copy(
                                        extEnclosureDownloadProgress = cachedLink.extEnclosureDownloadProgress,
                                        extCacheUri = cachedLink.extCacheUri,
                                    )
                                )
                            )
                        }
                    }
                }
            } else {
                withContext(Dispatchers.IO) {
                    db.feed.insertOrReplace(feed)
                    db.link.insertForFeed(feed.id, freshLinks)
                }
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

    suspend fun initialSync() {
        val startedAt = Instant.now().toString()
        syncFeeds()
        syncStarredEntries()
        syncUnreadEntries()
        withContext(Dispatchers.IO) {
            db.conf.update {
                it.copy(
                    minifluxInitialSyncCompleted = true,
                    minifluxIncrementalSyncTimestamp = startedAt,
                )
            }
        }
    }

    suspend fun incrementalSync() {
        db.log.insert(
            LogTable.InsertArgs(
                level = "info",
                tag = "miniflux_sync",
                message = "Syncing changed entries",
            )
        )
        var changedAfter = OffsetDateTime.parse(db.conf.select().minifluxIncrementalSyncTimestamp)
        db.log.insert(
            LogTable.InsertArgs(
                level = "info",
                tag = "miniflux_sync",
                message = "changedAfter = $changedAfter",
            )
        )
        val batchSize = 100L
        while (true) {
            val currentBatch = api.getEntriesChangedAfter(changedAfter, batchSize)
            db.log.insert(
                LogTable.InsertArgs(
                    level = "info",
                    tag = "miniflux_sync",
                    message = "Got ${currentBatch.size} changed entries",
                )
            )
            if (currentBatch.isEmpty()) {
                break
            } else {
                val newChangedAfter = currentBatch.maxOf { it.changed_at }
                val typedCurrentBatch = currentBatch.map { it.toVestiEntry() }
                db.transaction {
                    typedCurrentBatch.forEach {
                        db.entry.insertOrReplace(listOf(it.first))
                        db.link.insertForEntry(it.first.id, it.second)
                    }
                    db.log.insert(
                        LogTable.InsertArgs(
                            level = "info",
                            tag = "miniflux_sync",
                            message = "Bumping lastEntriesSyncDatetime to $newChangedAfter",
                        )
                    )
                    db.conf.update {
                        it.copy(
                            minifluxIncrementalSyncTimestamp = newChangedAfter,
                        )
                    }
                    changedAfter = OffsetDateTime.parse(newChangedAfter)
                }
                if (currentBatch.size < batchSize) {
                    break
                }
            }
        }
    }

    suspend fun syncUnreadEntries() {
        val freshEntries = api.getUnreadEntries().map { it.toVestiEntry() }
        db.transaction {
            freshEntries.forEach {
                db.entry.insertOrReplace(listOf(it.first))
                db.link.insertForEntry(it.first.id, it.second)
            }
        }
    }

    suspend fun syncStarredEntries() {
        val freshEntries = api.getStarredEntries().map { it.toVestiEntry() }
        db.transaction {
            freshEntries.forEach {
                db.entry.insertOrReplace(listOf(it.first))
                db.link.insertForEntry(it.first.id, it.second)
            }
        }
    }

    private fun Miniflux.Entry.toVestiEntry(): Pair<EntryTable.Entry, List<LinkTable.Link>> {
        val links = mutableListOf<LinkTable.Link>()

        if (url.isNotBlank()) {
            links += LinkTable.Link(
                id = null,
                feedId = null,
                entryId = id.toString(),
                href = url,
                rel = AtomLinkRel.Alternate,
                type = "text/html",
                hreflang = null,
                title = null,
                length = null,
                extEnclosureDownloadProgress = null,
                extCacheUri = null,
            )
        }

        enclosures?.forEach { enclosure ->
            links += LinkTable.Link(
                id = null,
                feedId = null,
                entryId = id.toString(),
                href = enclosure.url,
                rel = AtomLinkRel.Enclosure,
                type = enclosure.mime_type,
                hreflang = null,
                title = null,
                length = enclosure.size,
                extEnclosureDownloadProgress = null,
                extCacheUri = null,
            )
        }

        return Pair(
            EntryTable.Entry(
                contentType = "html",
                contentSrc = "",
                contentText = content,
                summary = null,
                id = id.toString(),
                feedId = feed_id.toString(),
                title = title,
                published = OffsetDateTime.parse(published_at),
                updated = OffsetDateTime.parse(changed_at),
                authorName = author,
                extRead = status == "read",
                extReadSynced = true,
                extBookmarked = starred,
                extBookmarkedSynced = true,
                extCommentsUrl = comments_url,
                extOpenGraphImageChecked = false,
                extOpenGraphImageUrl = "",
                extOpenGraphImageWidth = 0,
                extOpenGraphImageHeight = 0,
            ), links
        )
    }
}