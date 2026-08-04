package org.vestifeed.backend

import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.vestifeed.db.Database
import org.vestifeed.parser.AtomLinkRel

class EmbeddedFeedTest {

    private lateinit var server: MockWebServer
    private lateinit var db: Database
    private lateinit var httpClient: OkHttpClient

    @Before
    fun before() {
        server = MockWebServer().apply { start() }
        db = Database(BundledSQLiteDriver(), ":memory:")
        httpClient = OkHttpClient()
    }

    @After
    fun after() {
        server.shutdown()
    }

    @Test
    fun addsTwoFeedsFromSameDomainWithoutConflict() = runBlocking {
        val baseUrl = server.url("/")
        val feedOneUrl = baseUrl.newBuilder().addPathSegment("one.xml").build()
        val feedTwoUrl = baseUrl.newBuilder().addPathSegment("two.xml").build()

        val feedOneBody = javaClass.getResourceAsStream("/rss/example.com.one.rss.xml")!!
            .bufferedReader()
            .readText()
        val feedTwoBody = javaClass.getResourceAsStream("/rss/example.com.two.rss.xml")!!
            .bufferedReader()
            .readText()

        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/rss+xml")
                .setBody(feedOneBody),
        )
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/rss+xml")
                .setBody(feedTwoBody),
        )

        val api = Embedded(db = db, httpClient = httpClient)

        val resultOne = api.addFeed(feedOneUrl)
        val resultTwo = api.addFeed(feedTwoUrl)

        assertEquals("https://example.com/one", resultOne.feed.id)
        assertEquals("Example Feed One", resultOne.feed.title)
        assertEquals("https://example.com/two", resultTwo.feed.id)
        assertEquals("Example Feed Two", resultTwo.feed.title)

        assertEquals(1, resultOne.entries.size)
        assertEquals("One item one", resultOne.entries.first().first.title)
        assertEquals("https://example.com/one", resultOne.entries.first().first.feedId)
        assertEquals(1, resultTwo.entries.size)
        assertEquals("Two item one", resultTwo.entries.first().first.title)
        assertEquals("https://example.com/two", resultTwo.entries.first().first.feedId)

        db.transaction {
            db.feed.insertOrReplace(resultOne.feed)
            db.link.insertForFeed(resultOne.feed.id, resultOne.feedLinks)
            resultOne.entries.forEach { (entry, links) ->
                db.entry.insertOrReplace(listOf(entry))
                db.link.insertForEntry(entry.id, links)
            }

            db.feed.insertOrReplace(resultTwo.feed)
            db.link.insertForFeed(resultTwo.feed.id, resultTwo.feedLinks)
            resultTwo.entries.forEach { (entry, links) ->
                db.entry.insertOrReplace(listOf(entry))
                db.link.insertForEntry(entry.id, links)
            }
        }

        val feeds = db.feed.selectAll()
        assertEquals(2, feeds.size)
        val feedIds = feeds.map { it.id }.toSet()
        assertEquals(
            setOf("https://example.com/one", "https://example.com/two"),
            feedIds,
        )

        val feedTitles = feeds.map { it.title }.toSet()
        assertEquals(
            setOf("Example Feed One", "Example Feed Two"),
            feedTitles,
        )

        val linksOne = db.link.selectByFeedId("https://example.com/one")
        assertEquals(2, linksOne.size)
        assertNotNull(
            linksOne.singleOrNull {
                it.rel is AtomLinkRel.Self && it.href == feedOneUrl.toString()
            },
        )
        assertNotNull(
            linksOne.singleOrNull {
                it.rel is AtomLinkRel.Alternate && it.href == "https://example.com/one"
            },
        )

        val linksTwo = db.link.selectByFeedId("https://example.com/two")
        assertEquals(2, linksTwo.size)
        assertNotNull(
            linksTwo.singleOrNull {
                it.rel is AtomLinkRel.Self && it.href == feedTwoUrl.toString()
            },
        )
        assertNotNull(
            linksTwo.singleOrNull {
                it.rel is AtomLinkRel.Alternate && it.href == "https://example.com/two"
            },
        )

        val entriesOne = db.entry.selectByFeedId("https://example.com/one")
        assertEquals(1, entriesOne.size)
        assertEquals("One item one", entriesOne.first().title)

        val entriesTwo = db.entry.selectByFeedId("https://example.com/two")
        assertEquals(1, entriesTwo.size)
        assertEquals("Two item one", entriesTwo.first().title)

        val allEntries = db.entry.selectByQuery("item")
        assertEquals(2, allEntries.size)
        assertEquals(
            setOf("One item one", "Two item one"),
            allEntries.map { it.title }.toSet(),
        )
    }

    /**
     * Regression test for author parsing. The fixture is the live
     * https://www.space.com/feeds.xml feed, captured once and stored at
     * `app/src/test/resources/rss/space.com.feeds.rss.xml` so the test
     * stays deterministic and does not hit the network.
     *
     * Each `<author>` element in that feed sits inside a heavily-indented
     * line and is wrapped in `<![CDATA[ ... ]]>` with a leading and trailing
     * space, e.g.:
     *
     *     \t\t<author><![CDATA[ stingrayghost@gmail.com (Jeff Spry) ]]></author>\t
     *
     * If the parser hands `textContent` straight to the database, the stored
     * author name is surrounded by tabs, newlines and the CDATA-internal
     * spaces. The UI now displays the author verbatim, so we must trim it.
     */
    @Test
    fun spaceComFeedSavesAuthorsWithoutSurroundingWhitespace() = runBlocking {
        val url = server.url("/feeds.xml")
        val feedBody = javaClass.getResourceAsStream("/rss/space.com.feeds.rss.xml")!!
            .bufferedReader()
            .readText()

        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/rss+xml")
                .setBody(feedBody),
        )

        val api = Embedded(db = db, httpClient = httpClient)
        val result = api.addFeed(url)

        db.transaction {
            db.feed.insertOrReplace(result.feed)
            db.link.insertForFeed(result.feed.id, result.feedLinks)
            result.entries.forEach { (entry, links) ->
                db.entry.insertOrReplace(listOf(entry))
                db.link.insertForEntry(entry.id, links)
            }
        }

        val stored = db.entry.selectByFeedId(result.feed.id)
        assertEquals(result.entries.size, stored.size)

        val authorsWithName = stored.filter { it.authorName.isNotBlank() }
        assertTrue(
            "Expected at least one entry with an author name, got ${stored.size} stored",
            authorsWithName.isNotEmpty(),
        )

        for (entry in authorsWithName) {
            assertEquals(
                "Expected trimmed authorName for '${entry.title}', got '${entry.authorName}'",
                entry.authorName.trim(),
                entry.authorName,
            )
        }

        val authors = authorsWithName.map { it.authorName }.toSet()
        assertTrue(
            "Expected 'stingrayghost@gmail.com (Jeff Spry)' in stored authors, got $authors",
            "stingrayghost@gmail.com (Jeff Spry)" in authors,
        )
        assertTrue(
            "Expected 'mwall@space.com (Mike Wall)' in stored authors, got $authors",
            "mwall@space.com (Mike Wall)" in authors,
        )
    }
}
