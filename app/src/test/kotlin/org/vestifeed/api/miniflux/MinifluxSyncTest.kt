package org.vestifeed.api.miniflux

import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Test
import org.vestifeed.db.Database
import java.time.OffsetDateTime

class MinifluxSyncTest {
    private lateinit var db: Database

    @Before
    fun before() {
        db = Database(BundledSQLiteDriver(), ":memory:")
    }

    @Test
    fun syncFeeds() {
        val apiFeed1 = Miniflux.Feed(
            id = 1,
            title = "feed 1",
            feedUrl = "https://bubelov.com/index.xml",
            siteUrl = "https://bubelov.com/",
        )
        val apiFeeds = listOf(apiFeed1)
        val api = object : Miniflux {
            override suspend fun getFeeds() = apiFeeds
            override suspend fun getUnreadEntries() = emptyList<Miniflux.Entry>()
            override suspend fun getStarredEntries() = emptyList<Miniflux.Entry>()
            override suspend fun getEntriesChangedAfter(changedAfter: OffsetDateTime, limit: Long) =
                emptyList<Miniflux.Entry>()
        }
        val sync = MinifluxSync(db, api)
        runBlocking { sync.syncFeeds() }
        val cacheFeeds = db.feed.selectAll()
        val cacheFeed1 = cacheFeeds.first()
        assert(cacheFeed1.id == apiFeed1.id.toString())
        assert(cacheFeed1.title == apiFeed1.title)
        val cacheFeed1Links = db.link.selectByFeedId(apiFeed1.id.toString())
        assert(cacheFeed1Links.size == 2)
        assert(cacheFeed1Links.singleOrNull { it.href == apiFeed1.feedUrl } != null)
        assert(cacheFeed1Links.singleOrNull { it.href == apiFeed1.siteUrl } != null)
        runBlocking { sync.syncFeeds() }
        val cacheFeed1Links2 = db.link.selectByFeedId(apiFeed1.id.toString())
        assert(cacheFeed1Links2.size == 2)
        assert(cacheFeed1Links2.singleOrNull { it.href == apiFeed1.feedUrl } != null)
        assert(cacheFeed1Links2.singleOrNull { it.href == apiFeed1.siteUrl } != null)
    }
}