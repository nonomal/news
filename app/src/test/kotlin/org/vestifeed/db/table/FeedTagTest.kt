package org.vestifeed.db.table

import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.vestifeed.db.Database

class FeedTagTest {

    private lateinit var db: Database

    @Before
    fun before() {
        db = Database(BundledSQLiteDriver(), ":memory:")
    }

    @Test
    fun feedTagSchema_createTableStatement() {
        val statement = FeedTagTable.SCHEMA
        assertTrue(statement.contains("CREATE TABLE feed_tag"))
        assertTrue(statement.contains("feed_id TEXT NOT NULL REFERENCES feed"))
        assertTrue(statement.contains("tag_id TEXT NOT NULL REFERENCES tag"))
        assertTrue(statement.contains("PRIMARY KEY (feed_id, tag_id)"))
        assertTrue(statement.contains("ON DELETE CASCADE"))
    }

    @Test
    fun feedTagQueries_insert_single() {
        db.tag.insertOrReplace(createTag("t1"))
        db.feed.insertOrReplace(createFeed("f1"))
        db.feedTag.insert(feedId = "f1", tagId = "t1")
        assertEquals(listOf("t1"), db.feedTag.selectTagIdsByFeedId("f1"))
        assertEquals(listOf("f1"), db.feedTag.selectFeedIdsByTagId("t1"))
    }

    @Test
    fun feedTagQueries_insert_isIdempotent() {
        db.tag.insertOrReplace(createTag("t1"))
        db.feed.insertOrReplace(createFeed("f1"))
        db.feedTag.insert(feedId = "f1", tagId = "t1")
        db.feedTag.insert(feedId = "f1", tagId = "t1")
        db.feedTag.insert(feedId = "f1", tagId = "t1")
        assertEquals(1, db.feedTag.selectTagIdsByFeedId("f1").size)
    }

    @Test
    fun feedTagQueries_select_manyToMany() {
        db.tag.insertOrReplace(createTag("t1"))
        db.tag.insertOrReplace(createTag("t2"))
        db.feed.insertOrReplace(createFeed("f1"))
        db.feed.insertOrReplace(createFeed("f2"))
        db.feedTag.insert("f1", "t1")
        db.feedTag.insert("f1", "t2")
        db.feedTag.insert("f2", "t1")

        assertEquals(setOf("t1", "t2"), db.feedTag.selectTagIdsByFeedId("f1").toSet())
        assertEquals(listOf("t1"), db.feedTag.selectTagIdsByFeedId("f2"))
        assertEquals(setOf("f1", "f2"), db.feedTag.selectFeedIdsByTagId("t1").toSet())
        assertEquals(listOf("f1"), db.feedTag.selectFeedIdsByTagId("t2"))
    }

    @Test
    fun feedTagQueries_delete_specific() {
        db.tag.insertOrReplace(createTag("t1"))
        db.tag.insertOrReplace(createTag("t2"))
        db.feed.insertOrReplace(createFeed("f1"))
        db.feedTag.insert("f1", "t1")
        db.feedTag.insert("f1", "t2")

        db.feedTag.delete(feedId = "f1", tagId = "t1")

        assertEquals(listOf("t2"), db.feedTag.selectTagIdsByFeedId("f1"))
    }

    @Test
    fun feedTagQueries_deleteByFeedId() {
        db.tag.insertOrReplace(createTag("t1"))
        db.feed.insertOrReplace(createFeed("f1"))
        db.feed.insertOrReplace(createFeed("f2"))
        db.feedTag.insert("f1", "t1")
        db.feedTag.insert("f2", "t1")

        db.feedTag.deleteByFeedId("f1")

        assertTrue(db.feedTag.selectTagIdsByFeedId("f1").isEmpty())
        assertEquals(listOf("f2"), db.feedTag.selectFeedIdsByTagId("t1"))
    }

    @Test
    fun feedTagQueries_deleteByTagId() {
        db.tag.insertOrReplace(createTag("t1"))
        db.tag.insertOrReplace(createTag("t2"))
        db.feed.insertOrReplace(createFeed("f1"))
        db.feedTag.insert("f1", "t1")
        db.feedTag.insert("f1", "t2")

        db.feedTag.deleteByTagId("t1")

        assertEquals(listOf("t2"), db.feedTag.selectTagIdsByFeedId("f1"))
        assertTrue(db.feedTag.selectFeedIdsByTagId("t1").isEmpty())
    }

    @Test
    fun feedTagQueries_cascadeOnFeedDelete() {
        db.tag.insertOrReplace(createTag("t1"))
        db.feed.insertOrReplace(createFeed("f1"))
        db.feedTag.insert("f1", "t1")

        db.feed.deleteById("f1")

        assertTrue(db.feedTag.selectTagIdsByFeedId("f1").isEmpty())
        // Tag itself should still exist.
        assertTrue(db.tag.selectAll().isNotEmpty())
    }

    @Test
    fun feedTagQueries_cascadeOnTagDelete() {
        db.tag.insertOrReplace(createTag("t1"))
        db.feed.insertOrReplace(createFeed("f1"))
        db.feedTag.insert("f1", "t1")

        db.tag.deleteById("t1")

        assertTrue(db.feedTag.selectFeedIdsByTagId("t1").isEmpty())
        // Feed itself should still exist.
        assertTrue(db.feed.selectAll().isNotEmpty())
    }

    private fun createTag(id: String) = TagTable.Tag(
        id = id,
        name = "Tag $id",
        extSource = TagTable.Source.Embedded,
        extMinifluxId = null,
    )

    private fun createFeed(id: String) = FeedTable.Feed(
        id = id,
        title = "Feed $id",
        extOpenEntriesInBrowser = null,
        extBlockedWords = "",
        extShowPreviewImages = null,
    )
}
