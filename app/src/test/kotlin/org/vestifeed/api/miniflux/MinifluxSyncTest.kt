package org.vestifeed.api.miniflux

import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Test
import org.vestifeed.db.Database

class MinifluxSyncTest {
    private lateinit var db: Database

    @Before
    fun before() {
        db = Database(BundledSQLiteDriver(), ":memory:")
    }

    @Test
    fun syncFeeds() {
        val apiFeeds = listOf(
            Miniflux.Feed(
                id = 1,
                title = "feed 1",
                feedUrl = "https://bubelov.com/index.xml",
                siteUrl = "https://bubelov.com/",
            )
        )
        val api = object: Miniflux {
            override fun getFeeds() = apiFeeds
        }
        val sync = MinifluxSync(db, api)
        runBlocking { sync.syncFeeds() }
        val cacheFeeds = db.feed.selectAll()
        assert(cacheFeeds.single().id == apiFeeds.single().id.toString())
        assert(cacheFeeds.single().title == apiFeeds.single().title)
    }
}