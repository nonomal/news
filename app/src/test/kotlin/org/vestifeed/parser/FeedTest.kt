package org.vestifeed.parser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FeedTest {

    @Test
    fun parsesHvgRssFeed() {
        val result = javaClass.getResourceAsStream("/rss/hvg.hu.rss.xml")!!.use {
            feed(it, "application/rss+xml")
        }

        assertTrue("Expected FeedResult.Success but got $result", result is FeedResult.Success)

        val feed = (result as FeedResult.Success).feed
        assertTrue("Expected RssFeed but got ${feed::class.simpleName}", feed is RssFeed)

        val rss = feed as RssFeed
        assertEquals(RssVersion.RSS_2_0, rss.version)
        assertEquals("HVG", rss.channel.title)
        assertEquals("https://hvg.hu", rss.channel.link)
        assertEquals("Friss hírek a HVG.hu hírportálról.", rss.channel.description)

        val items = rss.channel.items.getOrThrow()
        assertTrue("Expected at least one item but got ${items.size}", items.isNotEmpty())

        items.forEachIndexed { index, itemResult ->
            assertTrue("Item $index failed to parse: $itemResult", itemResult.isSuccess)
        }

        val first = items.first().getOrThrow()
        assertNotNull(first.title)
        assertNotNull(first.link)
        assertNotNull(first.description)
    }

    @Test
    fun parsesTrattRss092Feed() {
        val result = javaClass.getResourceAsStream("/rss/tratt.net.laurie.blog.entries.rss.xml")!!.use {
            feed(it, "application/rss+xml")
        }

        assertTrue("Expected FeedResult.Success but got $result", result is FeedResult.Success)

        val feed = (result as FeedResult.Success).feed
        assertTrue("Expected RssFeed but got ${feed::class.simpleName}", feed is RssFeed)

        val rss = feed as RssFeed
        assertEquals(RssVersion.RSS_0_92, rss.version)
        assertEquals("Laurence Tratt: Blog", rss.channel.title)
        assertEquals("https://tratt.net/", rss.channel.link)
        assertEquals("Laurence Tratt", rss.channel.description)

        val items = rss.channel.items.getOrThrow()
        assertTrue("Expected at least one item but got ${items.size}", items.isNotEmpty())

        items.forEachIndexed { index, itemResult ->
            assertTrue("Item $index failed to parse: $itemResult", itemResult.isSuccess)
        }

        val first = items.first().getOrThrow()
        assertNotNull(first.title)
        assertNotNull(first.link)
        assertNotNull(first.pubDate)
    }
}
