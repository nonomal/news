package org.vestifeed.backend

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup
import org.vestifeed.db.Database
import org.vestifeed.db.table.FeedTable
import org.vestifeed.http.await
import org.vestifeed.parser.FeedResult
import org.vestifeed.parser.feed

class Embedded(
    db: Database,
    httpClient: OkHttpClient = OkHttpClient(),
) : Backend(db) {

    private val httpClient = httpClient

    private val fetcher = EmbeddedFeedFetcher(db, httpClient)

    override suspend fun addFeed(url: HttpUrl, categoryId: Long?): AddFeedResult {
        val request = Request.Builder().url(url).build()

        runCatching {
            httpClient.newCall(request).await()
        }.getOrElse {
            throw it
        }.use { response ->
            if (!response.isSuccessful) {
                throw Exception("Cannot fetch feed (url = $url, code = ${response.code})")
            }

            val contentType = response.header("content-type") ?: ""

            if (contentType.startsWith("text/html")) {
                val html = runCatching {
                    withContext(Dispatchers.IO) {
                        Jsoup.parse(response.body!!.string())
                    }
                }.getOrElse {
                    throw Exception("Failed to read response", it)
                }

                val feedElements = buildList {
                    addAll(html.select("link[type=\"application/atom+xml\"]"))
                    addAll(html.select("link[type=\"application/rss+xml\"]"))
                }

                if (feedElements.isEmpty()) {
                    throw Exception("Cannot find feed links in HTML page (url = $url)")
                }

                val href = feedElements.first().attr("href")
                val absolute = !href.startsWith("/")

                return if (absolute) {
                    addFeed(href.toHttpUrl(), categoryId)
                } else {
                    addFeed("$url$href".toHttpUrl(), categoryId)
                }
            } else {
                val result = runCatching {
                    withContext(Dispatchers.IO) {
                        feed(response.body.byteStream(), contentType)
                    }
                }.getOrElse {
                    throw Exception("Failed to read response", it)
                }

                return when (result) {
                    is FeedResult.Success -> {
                        val (feed, feedLinks) = result.feed.toFeed(url)
                        AddFeedResult(
                            feed = feed,
                            feedLinks = feedLinks,
                            entries = result.feed.getEntries(feed.id),
                        )
                    }

                    is FeedResult.UnsupportedMediaType -> {
                        throw Exception("Unsupported media type: ${result.mediaType}")
                    }

                    is FeedResult.UnsupportedFeedType -> {
                        throw Exception("Unsupported feed type")
                    }

                    is FeedResult.IOError -> {
                        throw result.cause
                    }

                    is FeedResult.ParserError -> {
                        throw result.cause
                    }
                }
            }
        }
    }

    override suspend fun getFeeds(): List<FeedTable.Feed> {
        return db.feed.selectAll()
    }

    override suspend fun updateFeedTitle(feedId: String, newTitle: String): Result<Unit> {
        return Result.success(Unit)
    }

    override suspend fun deleteFeed(feedId: String): Result<Unit> {
        return Result.success(Unit)
    }

    override suspend fun sync(initial: Boolean) {
        val feeds = withContext(Dispatchers.IO) { db.feed.selectAll() }

        for (feed in feeds) {
            val feedEntries = fetcher.fetchEntries(feed)
            withContext(Dispatchers.IO) {
                db.transaction {
                    db.entry.insertOrReplace(feedEntries.map { it.first })
                    for (feedEntry in feedEntries) {
                        db.link.insertForEntry(feedEntry.first.id, feedEntry.second)
                    }
                }
            }
        }
    }
}
