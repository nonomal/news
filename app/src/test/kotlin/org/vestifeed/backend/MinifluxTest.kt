package org.vestifeed.backend

import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import kotlinx.coroutines.runBlocking
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.vestifeed.db.Database
import org.vestifeed.db.table.EntryTable
import org.vestifeed.db.table.FeedTable
import org.vestifeed.db.table.LinkTable
import org.vestifeed.parser.AtomLinkRel
import java.time.OffsetDateTime

class MinifluxTest {
    private lateinit var db: Database

    @Before
    fun before() {
        db = Database(BundledSQLiteDriver(), ":memory:")
    }

    @Test
    fun syncFeeds() {
        val freshFeed = FeedTable.Feed(
            id = "1",
            title = "feed 1",
            extOpenEntriesInBrowser = false,
            extBlockedWords = "",
            extShowPreviewImages = null,
        )
        val freshLinks = listOf(
            LinkTable.Link(
                id = null,
                feedId = "1",
                entryId = null,
                href = "https://bubelov.com/index.xml",
                rel = AtomLinkRel.Self,
                type = null,
                hreflang = null,
                title = null,
                length = null,
                extEnclosureDownloadProgress = null,
                extCacheUri = null,
            ),
            LinkTable.Link(
                id = null,
                feedId = "1",
                entryId = null,
                href = "https://bubelov.com/",
                rel = AtomLinkRel.Alternate,
                type = "text/html",
                hreflang = null,
                title = null,
                length = null,
                extEnclosureDownloadProgress = null,
                extCacheUri = null,
            ),
        )
        val api = object : Miniflux(
            client = OkHttpClient(),
            baseUrl = "http://localhost".toHttpUrl(),
            db = db,
        ) {
            override suspend fun addFeed(url: okhttp3.HttpUrl): Backend.AddFeedResult =
                throw NotImplementedError()

            override suspend fun updateFeedTitle(feedId: String, newTitle: String): Result<Unit> =
                Result.success(Unit)

            override suspend fun deleteFeed(feedId: String): Result<Unit> =
                Result.success(Unit)

            override suspend fun getFeedsWithLinks(): List<Miniflux.FreshFeed> =
                listOf(Miniflux.FreshFeed(feed = freshFeed, links = freshLinks, categoryId = null))

            override suspend fun getUnreadEntries(): List<Pair<EntryTable.Entry, List<LinkTable.Link>>> =
                emptyList()

            override suspend fun getStarredEntries(): List<Pair<EntryTable.Entry, List<LinkTable.Link>>> =
                emptyList()

            override suspend fun getEntriesChangedAfter(
                changedAfter: OffsetDateTime,
                limit: Long,
            ): List<Pair<EntryTable.Entry, List<LinkTable.Link>>> = emptyList()

            override suspend fun markEntriesAsRead(entriesIds: List<String>, read: Boolean) = Unit

            override suspend fun markEntriesAsBookmarked(
                entries: List<EntryTable.EntryWithoutContent>,
                bookmarked: Boolean,
            ) = Unit

            override suspend fun getCategories(): List<Miniflux.MinifluxCategory> =
                emptyList()

            override suspend fun createCategory(title: String): Miniflux.MinifluxCategory =
                throw NotImplementedError()

            override suspend fun updateCategory(id: Long, title: String): Miniflux.MinifluxCategory =
                throw NotImplementedError()

            override suspend fun deleteCategory(id: Long): Result<Unit> =
                Result.success(Unit)

            override suspend fun moveFeedToCategory(feedId: String, categoryId: Long) = Unit
        }
        runBlocking { api.sync(initial = true) }
        val cacheFeeds = db.feed.selectAll()
        val cacheFeed1 = cacheFeeds.first()
        assert(cacheFeed1.id == freshFeed.id)
        assert(cacheFeed1.title == freshFeed.title)
        val cacheFeed1Links = db.link.selectByFeedId(freshFeed.id)
        assert(cacheFeed1Links.size == 2)
        assert(cacheFeed1Links.singleOrNull { it.href == freshLinks[0].href } != null)
        assert(cacheFeed1Links.singleOrNull { it.href == freshLinks[1].href } != null)
        runBlocking { api.sync(initial = true) }
        val cacheFeed1Links2 = db.link.selectByFeedId(freshFeed.id)
        assert(cacheFeed1Links2.size == 2)
        assert(cacheFeed1Links2.singleOrNull { it.href == freshLinks[0].href } != null)
        assert(cacheFeed1Links2.singleOrNull { it.href == freshLinks[1].href } != null)
    }

    @Test
    fun syncCategories_createsTagsAndAssociatesFeeds() {
        val freshFeed = FeedTable.Feed(
            id = "1",
            title = "feed 1",
            extOpenEntriesInBrowser = false,
            extBlockedWords = "",
            extShowPreviewImages = null,
        )
        val freshLinks = emptyList<LinkTable.Link>()
        val api = object : Miniflux(
            client = OkHttpClient(),
            baseUrl = "http://localhost".toHttpUrl(),
            db = db,
        ) {
            override suspend fun addFeed(url: okhttp3.HttpUrl): Backend.AddFeedResult =
                throw NotImplementedError()

            override suspend fun updateFeedTitle(feedId: String, newTitle: String): Result<Unit> =
                Result.success(Unit)

            override suspend fun deleteFeed(feedId: String): Result<Unit> =
                Result.success(Unit)

            override suspend fun getFeedsWithLinks(): List<Miniflux.FreshFeed> =
                listOf(Miniflux.FreshFeed(feed = freshFeed, links = freshLinks, categoryId = 42L))

            override suspend fun getUnreadEntries(): List<Pair<EntryTable.Entry, List<LinkTable.Link>>> =
                emptyList()

            override suspend fun getStarredEntries(): List<Pair<EntryTable.Entry, List<LinkTable.Link>>> =
                emptyList()

            override suspend fun getEntriesChangedAfter(
                changedAfter: OffsetDateTime,
                limit: Long,
            ): List<Pair<EntryTable.Entry, List<LinkTable.Link>>> = emptyList()

            override suspend fun markEntriesAsRead(entriesIds: List<String>, read: Boolean) = Unit

            override suspend fun markEntriesAsBookmarked(
                entries: List<EntryTable.EntryWithoutContent>,
                bookmarked: Boolean,
            ) = Unit

            override suspend fun getCategories(): List<Miniflux.MinifluxCategory> =
                listOf(
                    Miniflux.MinifluxCategory(id = 42L, title = "Tech"),
                    Miniflux.MinifluxCategory(id = 99L, title = "News"),
                )

            override suspend fun createCategory(title: String): Miniflux.MinifluxCategory =
                throw NotImplementedError()

            override suspend fun updateCategory(id: Long, title: String): Miniflux.MinifluxCategory =
                throw NotImplementedError()

            override suspend fun deleteCategory(id: Long): Result<Unit> =
                Result.success(Unit)

            override suspend fun moveFeedToCategory(feedId: String, categoryId: Long) = Unit
        }

        runBlocking { api.sync(initial = true) }

        val tags = db.tag.selectAll()
        assertEquals(2, tags.size)
        val tech = tags.single { it.extMinifluxId == 42L }
        assertEquals("Tech", tech.name)
        assertEquals(org.vestifeed.db.table.TagTable.Source.Miniflux, tech.extSource)

        // The tag id is a runtime-generated UUID. Verify the association by
        // looking up the tag that points at category 42 and checking the
        // feed_tag rows match.
        val tagIds = db.feedTag.selectTagIdsByFeedId("1")
        assertEquals(1, tagIds.size)
        assertEquals(tech.id, tagIds.single())
    }

    @Test
    fun syncCategories_removesDeletedCategories() {
        val freshFeed = FeedTable.Feed(
            id = "1",
            title = "feed 1",
            extOpenEntriesInBrowser = false,
            extBlockedWords = "",
            extShowPreviewImages = null,
        )
        val remoteCategoriesFirstRun = listOf(
            Miniflux.MinifluxCategory(id = 42L, title = "Tech"),
            Miniflux.MinifluxCategory(id = 99L, title = "News"),
        )
        val remoteCategoriesSecondRun = listOf(
            Miniflux.MinifluxCategory(id = 42L, title = "Tech"),
        )

        var currentCategories = remoteCategoriesFirstRun
        val api = object : Miniflux(
            client = OkHttpClient(),
            baseUrl = "http://localhost".toHttpUrl(),
            db = db,
        ) {
            override suspend fun addFeed(url: okhttp3.HttpUrl): Backend.AddFeedResult =
                throw NotImplementedError()

            override suspend fun updateFeedTitle(feedId: String, newTitle: String): Result<Unit> =
                Result.success(Unit)

            override suspend fun deleteFeed(feedId: String): Result<Unit> =
                Result.success(Unit)

            override suspend fun getFeedsWithLinks(): List<Miniflux.FreshFeed> =
                listOf(Miniflux.FreshFeed(feed = freshFeed, links = emptyList(), categoryId = 42L))

            override suspend fun getUnreadEntries(): List<Pair<EntryTable.Entry, List<LinkTable.Link>>> =
                emptyList()

            override suspend fun getStarredEntries(): List<Pair<EntryTable.Entry, List<LinkTable.Link>>> =
                emptyList()

            override suspend fun getEntriesChangedAfter(
                changedAfter: OffsetDateTime,
                limit: Long,
            ): List<Pair<EntryTable.Entry, List<LinkTable.Link>>> = emptyList()

            override suspend fun markEntriesAsRead(entriesIds: List<String>, read: Boolean) = Unit

            override suspend fun markEntriesAsBookmarked(
                entries: List<EntryTable.EntryWithoutContent>,
                bookmarked: Boolean,
            ) = Unit

            override suspend fun getCategories(): List<Miniflux.MinifluxCategory> = currentCategories

            override suspend fun createCategory(title: String): Miniflux.MinifluxCategory =
                throw NotImplementedError()

            override suspend fun updateCategory(id: Long, title: String): Miniflux.MinifluxCategory =
                throw NotImplementedError()

            override suspend fun deleteCategory(id: Long): Result<Unit> =
                Result.success(Unit)

            override suspend fun moveFeedToCategory(feedId: String, categoryId: Long) = Unit
        }

        runBlocking {
            api.sync(initial = true)
        }
        assertEquals(2, db.tag.selectAll().size)

        // Pretend the user removed category 99 on the server.
        currentCategories = remoteCategoriesSecondRun
        runBlocking {
            api.sync(initial = true)
        }
        assertEquals(1, db.tag.selectAll().size)
        assertEquals(42L, db.tag.selectAll().single().extMinifluxId)
    }
}
