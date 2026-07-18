package org.vestifeed.db

import java.time.OffsetDateTime
import java.util.UUID
import org.junit.Test
import org.junit.Assert.assertEquals
import org.junit.Before
import org.vestifeed.db.table.EntryTable
import org.vestifeed.db.table.FeedTable
import kotlin.collections.sortedByDescending

class EntryQueriesTest {

    private lateinit var db: Database

    @Before
    fun before() {
        db = db()
    }

    @Test
    fun insertOrReplace() {
        val item = entry()
        db.entry.insertOrReplace(listOf(item))
        assertEquals(item, db.entry.selectById(item.id))
    }

    @Test
    fun selectById() {
        val items = listOf(
            db.entry.insertOrReplace(),
            db.entry.insertOrReplace(),
            db.entry.insertOrReplace(),
        )

        assertEquals(
            items[1],
            db.entry.selectById(items[1].id),
        )
    }

    @Test
    fun selectByReadAndBookmarked() {
        val feed = createFeed()
        db.feed.insertOrReplace(feed)

        val all = listOf(
            entry().copy(feedId = feed.id, extRead = true, extBookmarked = true),
            entry().copy(feedId = feed.id, extRead = true, extBookmarked = false),
            entry().copy(feedId = feed.id, extRead = false, extBookmarked = false),
        )

        db.entry.insertOrReplace(all)

        assertEquals(
            all.filter { !it.extRead && !it.extBookmarked }.map { it.id },
            db.entry.selectUnread().map { it.id },
        )
    }

    @Test
    fun selectByReadSynced() {
        val all = listOf(
            entry().copy(extReadSynced = true),
            entry().copy(extReadSynced = false),
            entry().copy(extReadSynced = true),
        )

        db.entry.insertOrReplace(all)

        assertEquals(
            all.filter { it.extReadSynced }.map { it.withoutContent() }
                .sortedByDescending { it.published },
            db.entry.selectByReadSynced(true),
        )

        assertEquals(
            all.filter { !it.extReadSynced }.map { it.withoutContent() }
                .sortedByDescending { it.published },
            db.entry.selectByReadSynced(false),
        )
    }

    @Test
    fun selectByQuery() {
        val db = db()
        val feed = createFeed()
        db.feed.insertOrReplace(feed)

        val entries = listOf(
            entry().copy(feedId = feed.id, contentText = "Linux 5.19 introduces RSS API"),
            entry().copy(feedId = feed.id, contentText = "LinuX 5.19 introduces RSS API"),
            entry().copy(feedId = feed.id, contentText = "linux 5.19 introduces RSS API"),
            entry().copy(feedId = feed.id, contentText = "Injured Irons Destroy Specifically")
        )

        db.entry.insertOrReplace(entries)

        assertEquals(3, db.entry.selectByQuery("Linux").size)
        assertEquals(3, db.entry.selectByQuery("LinuX").size)
        assertEquals(3, db.entry.selectByQuery("linux").size)
        assertEquals(1, db.entry.selectByQuery("call").size)
    }

    @Test
    fun selectUnread() {
        val feed1 = createFeed(title = "Feed 1")
        val feed2 = createFeed(title = "Feed 2")
        db.feed.insertOrReplace(listOf(feed1, feed2))

        val entries = listOf(
            entry().copy(feedId = feed1.id, title = "Entry 1", extRead = false, extBookmarked = false),
            entry().copy(feedId = feed1.id, title = "Entry 2", extRead = true, extBookmarked = false),
            entry().copy(feedId = feed1.id, title = "Entry 3", extRead = false, extBookmarked = true),
            entry().copy(feedId = feed2.id, title = "Entry 4", extRead = false, extBookmarked = false),
            entry().copy(feedId = feed2.id, title = "Entry 5", extRead = true, extBookmarked = true),
        )

        db.entry.insertOrReplace(entries)

        val unread = db.entry.selectUnread()

        assertEquals(2, unread.size)
        assertEquals(2, db.entry.selectUnreadCount())
        assertEquals("Entry 1", unread.find { it.id == entries[0].id }?.title)
        assertEquals("Entry 4", unread.find { it.id == entries[3].id }?.title)
        assertEquals(feed1.title, unread.find { it.id == entries[0].id }?.feedTitle)
        assertEquals(feed2.title, unread.find { it.id == entries[3].id }?.feedTitle)
    }

    @Test
    fun selectBookmarked() {
        val feed1 = createFeed(title = "Feed 1")
        val feed2 = createFeed(title = "Feed 2")
        db.feed.insertOrReplace(listOf(feed1, feed2))

        val entries = listOf(
            entry().copy(feedId = feed1.id, title = "Entry 1", extBookmarked = true),
            entry().copy(feedId = feed1.id, title = "Entry 2", extBookmarked = false),
            entry().copy(feedId = feed2.id, title = "Entry 3", extBookmarked = true),
            entry().copy(feedId = feed2.id, title = "Entry 4", extBookmarked = false),
        )

        db.entry.insertOrReplace(entries)

        val bookmarked = db.entry.selectBookmarked()

        assertEquals(2, bookmarked.size)
        assertEquals(2, db.entry.selectBookmarkedCount())
        assertEquals("Entry 1", bookmarked.find { it.id == entries[0].id }?.title)
        assertEquals("Entry 3", bookmarked.find { it.id == entries[2].id }?.title)
        assertEquals(feed1.title, bookmarked.find { it.id == entries[0].id }?.feedTitle)
        assertEquals(feed2.title, bookmarked.find { it.id == entries[2].id }?.feedTitle)
    }

    @Test
    fun selectByFeedId() {
        val feed1 = createFeed(title = "Feed 1")
        val feed2 = createFeed(title = "Feed 2")
        db.feed.insertOrReplace(listOf(feed1, feed2))

        val entries = listOf(
            entry().copy(feedId = feed1.id, title = "Entry 1", extRead = false),
            entry().copy(feedId = feed1.id, title = "Entry 2", extRead = true),
            entry().copy(feedId = feed2.id, title = "Entry 3", extRead = false),
        )

        db.entry.insertOrReplace(entries)

        val feed1Entries = db.entry.selectByFeedId(feed1.id)
        val feed2Entries = db.entry.selectByFeedId(feed2.id)

        assertEquals(2, feed1Entries.size)
        assertEquals(1, feed2Entries.size)
        assertEquals("Entry 1", feed1Entries.find { it.id == entries[0].id }?.title)
        assertEquals("Entry 2", feed1Entries.find { it.id == entries[1].id }?.title)
        assertEquals("Entry 3", feed2Entries.find { it.id == entries[2].id }?.title)
    }
}

fun EntryTable.insertOrReplace(): EntryTable.Entry {
    val entry = entry()
    insertOrReplace(listOf(entry))
    return entry
}

fun entry() = EntryTable.Entry(
    contentType = "",
    contentSrc = "",
    contentText = "",
    summary = "",
    id = UUID.randomUUID().toString(),
    feedId = "",
    title = "",
    published = OffsetDateTime.now(),
    updated = OffsetDateTime.now(),
    authorName = "",
    extRead = false,
    extReadSynced = true,
    extBookmarked = false,
    extBookmarkedSynced = true,
    extCommentsUrl = "",
    extOpenGraphImageChecked = true,
    extOpenGraphImageUrl = "",
    extOpenGraphImageWidth = 0,
    extOpenGraphImageHeight = 0,
)

fun entryWithoutContent() = EntryTable.EntryWithoutContent(
    summary = "",
    id = UUID.randomUUID().toString(),
    feedId = "",
    title = "",
    published = OffsetDateTime.now(),
    updated = OffsetDateTime.now(),
    authorName = "",
    extRead = false,
    extReadSynced = true,
    extBookmarked = false,
    extBookmarkedSynced = true,
    extCommentsUrl = "",
    extOpenGraphImageChecked = true,
    extOpenGraphImageUrl = "",
    extOpenGraphImageWidth = 0,
    extOpenGraphImageHeight = 0,
)

fun EntryTable.Entry.withoutContent() = EntryTable.EntryWithoutContent(
    summary = summary,
    id = id,
    feedId = feedId,
    title = title,
    published = published,
    updated = updated,
    authorName = authorName,
    extRead = extRead,
    extReadSynced = extReadSynced,
    extBookmarked = extBookmarked,
    extBookmarkedSynced = extBookmarkedSynced,
    extCommentsUrl = extCommentsUrl,
    extOpenGraphImageChecked = extOpenGraphImageChecked,
    extOpenGraphImageUrl = extOpenGraphImageUrl,
    extOpenGraphImageWidth = extOpenGraphImageWidth,
    extOpenGraphImageHeight = extOpenGraphImageHeight,
)

fun EntryTable.EntryWithoutContent.toEntry(): EntryTable.Entry {
    return EntryTable.Entry(
        contentType = "",
        contentSrc = "",
        contentText = "",
        summary = summary,
        id = id,
        feedId = feedId,
        title = title,
        published = published,
        updated = updated,
        authorName = authorName,
        extRead = extRead,
        extReadSynced = extReadSynced,
        extBookmarked = extBookmarked,
        extBookmarkedSynced = extBookmarkedSynced,
        extCommentsUrl = extCommentsUrl,
        extOpenGraphImageChecked = extOpenGraphImageChecked,
        extOpenGraphImageUrl = extOpenGraphImageUrl,
        extOpenGraphImageWidth = extOpenGraphImageWidth,
        extOpenGraphImageHeight = extOpenGraphImageHeight,
    )
}

private fun createFeed(
    id: String = UUID.randomUUID().toString(),
    title: String = "Test Feed",
    extOpenEntriesInBrowser: Boolean? = null,
    extBlockedWords: String = "",
    extShowPreviewImages: Boolean? = null,
) = FeedTable.Feed(
    id = id,
    title = title,
    extOpenEntriesInBrowser = extOpenEntriesInBrowser,
    extBlockedWords = extBlockedWords,
    extShowPreviewImages = extShowPreviewImages,
)
