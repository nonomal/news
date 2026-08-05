package org.vestifeed.backend

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.vestifeed.db.Database
import org.vestifeed.db.table.TagTable
import java.time.Instant
import java.time.OffsetDateTime
import java.util.UUID

internal class MinifluxSync(private val api: Miniflux, private val db: Database) {

    suspend fun syncFeeds() {
        val freshFeedPairs = api.getFeedsWithLinks()
        val cachedFeeds = withContext(Dispatchers.IO) { db.feed.selectAll() }
        val freshFeedIds = freshFeedPairs.map { it.feed.id.toLong() }
        val cachedFeedIds = cachedFeeds.map { it.id.toLong() }
        val deletedOnServerIds = cachedFeedIds.filterNot { freshFeedIds.contains(it) }
        withContext(Dispatchers.IO) {
            deletedOnServerIds.forEach {
                db.link.deleteByFeedId(it.toString())
                db.feedTag.deleteByFeedId(it.toString())
                db.feed.deleteById(it.toString())
            }
        }
        for (fresh in freshFeedPairs) {
            val feed = fresh.feed
            val freshLinks = fresh.links
            val cached = cachedFeedIds.contains(feed.id.toLong())
            if (cached) {
                val cachedFeed = cachedFeeds.find { it.id == feed.id }!!
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

    /**
     * Reconcile remote Miniflux categories into the local [TagTable] /
     * [FeedTagTable]. Runs after [syncFeeds] so we already know which feeds
     * exist locally. Remote categories are stored with `ext_source =
     * 'miniflux'` and `ext_miniflux_id` set; locally-created tags from an
     * Embedded session are kept around untouched (their source is
     * `embedded`) because the Miniflux API has no concept of "extra" tags
     * per feed.
     *
     * If a category disappears upstream, the local [TagTable] row is removed
     * and the corresponding [FeedTagTable] rows go with it (CASCADE).
     */
    suspend fun syncCategories() {
        val freshCategories = api.getCategories()
        val freshCategoryIds = freshCategories.map { it.id }
        val cachedTags = withContext(Dispatchers.IO) { db.tag.selectAll() }
        val remoteTags = cachedTags.filter { it.extSource == TagTable.Source.Miniflux }
        val cachedTagIds = remoteTags.mapNotNull { it.extMinifluxId }
        val deletedOnServerCategoryIds = cachedTagIds.filterNot { freshCategoryIds.contains(it) }

        withContext(Dispatchers.IO) {
            db.transaction {
                deletedOnServerCategoryIds.forEach { minifluxId ->
                    remoteTags.find { it.extMinifluxId == minifluxId }?.let { tag ->
                        db.feedTag.deleteByTagId(tag.id)
                        db.tag.deleteById(tag.id)
                    }
                }

                val byMinifluxId = remoteTags.associateBy { it.extMinifluxId }
                for (fresh in freshCategories) {
                    val existing = byMinifluxId[fresh.id]
                    val tag = if (existing == null) {
                        TagTable.Tag(
                            id = UUID.randomUUID().toString(),
                            name = fresh.title,
                            extSource = TagTable.Source.Miniflux,
                            extMinifluxId = fresh.id,
                        )
                    } else {
                        existing.copy(name = fresh.title)
                    }
                    db.tag.insertOrReplace(tag)
                }
            }
        }

        val freshFeedPairs = api.getFeedsWithLinks()
        val allTags = withContext(Dispatchers.IO) { db.tag.selectAll() }
        val byMinifluxId = allTags
            .filter { it.extSource == TagTable.Source.Miniflux }
            .associateBy { it.extMinifluxId }
        val freshFeedIds = freshFeedPairs.map { it.feed.id }

        withContext(Dispatchers.IO) {
            db.transaction {
                for (feedId in freshFeedIds) {
                    db.feedTag.deleteByFeedId(feedId)
                }
                for (fresh in freshFeedPairs) {
                    val categoryId = fresh.categoryId ?: continue
                    val tag = byMinifluxId[categoryId] ?: continue
                    db.feedTag.insert(feedId = fresh.feed.id, tagId = tag.id)
                }
            }
        }
    }

    suspend fun syncEntries(initial: Boolean) {
        if (initial) {
            val startedAt = Instant.now().toString()
            syncStarredEntries()
            syncUnreadEntries()
            withContext(Dispatchers.IO) {
                db.conf.update { it.copy(minifluxIncrementalSyncTimestamp = startedAt) }
            }
        } else {
            val unsyncedEntries =
                withContext(Dispatchers.IO) { db.entry.selectByReadSynced(false) }
            val unsyncedReadEntries = unsyncedEntries.filter { it.extRead }
            val unsyncedUnreadEntries = unsyncedEntries.filter { !it.extRead }

            if (unsyncedReadEntries.isNotEmpty()) {
                api.markEntriesAsRead(
                    entriesIds = unsyncedReadEntries.map { it.id },
                    read = true,
                )

                withContext(Dispatchers.IO) {
                    db.transaction {
                        unsyncedReadEntries.forEach {
                            db.entry.updateReadSynced(true, it.id)
                        }
                    }
                }
            }

            if (unsyncedUnreadEntries.isNotEmpty()) {
                api.markEntriesAsRead(
                    entriesIds = unsyncedUnreadEntries.map { it.id },
                    read = false,
                )

                withContext(Dispatchers.IO) {
                    db.transaction {
                        unsyncedUnreadEntries.forEach {
                            db.entry.updateReadSynced(true, it.id)
                        }
                    }
                }
            }

            val notSyncedEntries =
                withContext(Dispatchers.IO) {
                    db.entry.selectByBookmarkedSynced(
                        false
                    )
                }
            val notSyncedBookmarkedEntries =
                notSyncedEntries.filter { it.extBookmarked }
            val notSyncedNotBookmarkedEntries =
                notSyncedEntries.filterNot { it.extBookmarked }

            if (notSyncedBookmarkedEntries.isNotEmpty()) {
                api.markEntriesAsBookmarked(
                    entries = notSyncedBookmarkedEntries,
                    bookmarked = true,
                )

                withContext(Dispatchers.IO) {
                    db.transaction {
                        notSyncedBookmarkedEntries.forEach {
                            db.entry.updateBookmarkedSynced(true, it.id)
                        }
                    }
                }
            }

            if (notSyncedNotBookmarkedEntries.isNotEmpty()) {
                api.markEntriesAsBookmarked(
                    entries = notSyncedNotBookmarkedEntries,
                    bookmarked = false,
                )

                withContext(Dispatchers.IO) {
                    db.transaction {
                        notSyncedNotBookmarkedEntries.forEach {
                            db.entry.updateBookmarkedSynced(true, it.id)
                        }
                    }
                }
            }
            var changedAfter =
                OffsetDateTime.parse(db.conf.select().minifluxIncrementalSyncTimestamp)
            val batchSize = 100L
            while (true) {
                val currentBatch = api.getEntriesChangedAfter(changedAfter, batchSize)
                if (currentBatch.isEmpty()) {
                    break
                } else {
                    val newChangedAfter = currentBatch.maxOf { it.first.updated }
                    db.transaction {
                        currentBatch.forEach {
                            db.entry.insertOrReplace(listOf(it.first))
                            db.link.insertForEntry(it.first.id, it.second)
                        }
                        db.conf.update {
                            it.copy(
                                minifluxIncrementalSyncTimestamp = newChangedAfter.toString(),
                            )
                        }
                        changedAfter = newChangedAfter
                    }
                    if (currentBatch.size < batchSize) {
                        break
                    }
                }
            }
        }
    }

    suspend fun syncUnreadEntries() {
        val freshEntries = api.getUnreadEntries()
        db.transaction {
            freshEntries.forEach {
                db.entry.insertOrReplace(listOf(it.first))
                db.link.insertForEntry(it.first.id, it.second)
            }
        }
    }

    suspend fun syncStarredEntries() {
        val freshEntries = api.getStarredEntries()
        db.transaction {
            freshEntries.forEach {
                db.entry.insertOrReplace(listOf(it.first))
                db.link.insertForEntry(it.first.id, it.second)
            }
        }
    }
}
