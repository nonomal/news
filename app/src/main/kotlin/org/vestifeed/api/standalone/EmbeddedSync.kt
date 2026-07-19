package org.vestifeed.api.standalone

import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.vestifeed.db.Database
import org.vestifeed.db.table.EntryTable
import org.vestifeed.db.table.FeedTable
import org.vestifeed.db.table.LinkTable
import org.vestifeed.http.await
import org.vestifeed.parser.AtomEntry
import org.vestifeed.parser.AtomFeed
import org.vestifeed.parser.AtomLinkRel
import org.vestifeed.parser.FeedResult
import org.vestifeed.parser.RssFeed
import org.vestifeed.parser.RssItem
import org.vestifeed.parser.RssItemGuid
import org.vestifeed.parser.feed
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.time.OffsetDateTime
import java.util.Date
import java.util.Locale

class EmbeddedSync(private val db: Database) {
    private val httpClient = OkHttpClient()

    suspend fun syncFeedsAndEntries() {
        val feeds = withContext(Dispatchers.IO) { db.feed.selectAll() }

        for (feed in feeds) {
            val feedEntries = fetchEntries(feed)
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

    private suspend fun fetchEntries(feed: FeedTable.Feed): List<Pair<EntryTable.Entry, List<LinkTable.Link>>> {
        val feedLinks = withContext(Dispatchers.IO) { db.link.selectByFeedId(feed.id) }
        val feedSelfLink = feedLinks.firstOrNull { it.rel is AtomLinkRel.Self }
            ?: throw Exception("self link is missing")
        val request = Request.Builder().url(feedSelfLink.href).build()
        val response = httpClient.newCall(request).await()
        response.use {
            if (!response.isSuccessful) throw Exception("feed request failed")
            val feedResult = feed(response.body.byteStream(), response.header("content-type") ?: "")
            return when (feedResult) {
                is FeedResult.Success -> {
                    when (val parsedFeed = feedResult.feed) {
                        is AtomFeed -> {
                            parsedFeed.entries.map { atomEntry -> atomEntry.toEntry(feed.id) }
                        }

                        is RssFeed -> {
                            parsedFeed.channel.items
                                .getOrElse { return emptyList() }
                                .mapNotNull { it.getOrNull() }
                                .map { rssItem -> rssItem.toEntry(feed.id) }
                        }
                    }
                }

                is FeedResult.UnsupportedMediaType -> {
                    throw Exception("unsupported media type")
                }

                is FeedResult.UnsupportedFeedType -> {
                    throw Exception("unsupported feed type")
                }

                is FeedResult.IOError -> {
                    throw feedResult.cause
                }

                is FeedResult.ParserError -> {
                    throw feedResult.cause
                }
            }
        }
    }

    private fun AtomEntry.toEntry(feedId: String): Pair<EntryTable.Entry, List<LinkTable.Link>> {
        return Pair(
            EntryTable.Entry(
                contentType = content.type.toString(),
                contentSrc = content.src,
                contentText = content.text,
                summary = summary?.text ?: "",
                id = id,
                feedId = feedId,
                title = title,
                published = OffsetDateTime.parse(published),
                updated = OffsetDateTime.parse(updated),
                authorName = authorName,
                extRead = false,
                extReadSynced = true,
                extBookmarked = false,
                extBookmarkedSynced = true,
                extCommentsUrl = "",
                extOpenGraphImageChecked = false,
                extOpenGraphImageUrl = "",
                extOpenGraphImageWidth = 0,
                extOpenGraphImageHeight = 0,
            ), links.map {
                LinkTable.Link(
                    id = null,
                    feedId = null,
                    entryId = id,
                    href = it.href,
                    rel = it.rel,
                    type = it.type,
                    hreflang = it.hreflang,
                    title = it.title,
                    length = it.length,
                    extEnclosureDownloadProgress = null,
                    extCacheUri = null,
                )
            }
        )
    }

    private fun RssItem.toEntry(feedId: String): Pair<EntryTable.Entry, List<LinkTable.Link>> {
        val id = when (val guid = guid) {
            is RssItemGuid.StringGuid -> "guid:${guid.value}"
            is RssItemGuid.UrlGuid -> "guid:${guid.value}"
            else -> {
                val feedIdComponent = "feed-id:$feedId"
                val titleHashComponent = "title-sha256:${sha256(title ?: "")}"
                val descriptionHashComponent = "description-sha256:${sha256(description ?: "")}"
                "$feedIdComponent,$titleHashComponent,$descriptionHashComponent"
            }
        }

        val links = mutableListOf<LinkTable.Link>()

        if (!link.isNullOrBlank()) {
            links += LinkTable.Link(
                id = null,
                feedId = null,
                entryId = id,
                href = link,
                rel = AtomLinkRel.Alternate,
                type = "text/html",
                hreflang = "",
                title = "",
                length = null,
                extEnclosureDownloadProgress = null,
                extCacheUri = null,
            )
        }

        if (enclosure != null) {
            links += LinkTable.Link(
                id = null,
                feedId = null,
                entryId = id,
                href = enclosure.url.toString(),
                rel = AtomLinkRel.Enclosure,
                type = enclosure.type,
                hreflang = "",
                title = "",
                length = enclosure.length,
                extEnclosureDownloadProgress = null,
                extCacheUri = null,
            )
        }

        val rawDescription = description ?: ""
        val summary = rawDescription.toEntrySummary()

        return Pair(
            EntryTable.Entry(
                contentType = "html",
                contentSrc = "",
                contentText = rawDescription,
                summary = summary,
                id = id,
                feedId = feedId,
                title = title ?: "",
                published = OffsetDateTime.parse((pubDate ?: Date()).toIsoString()),
                updated = OffsetDateTime.parse((pubDate ?: Date()).toIsoString()),
                authorName = author ?: "",
                extRead = false,
                extReadSynced = true,
                extBookmarked = false,
                extBookmarkedSynced = true,
                extCommentsUrl = "",
                extOpenGraphImageChecked = false,
                extOpenGraphImageUrl = "",
                extOpenGraphImageWidth = 0,
                extOpenGraphImageHeight = 0,
            ), links
        )
    }

    private fun sha256(string: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val hash = digest.digest(string.toByteArray())
        return Base64.encodeToString(hash, Base64.DEFAULT)
    }

    private fun Date.toIsoString(): String = ISO.format(this)

    companion object {
        private val ISO = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSXXX", Locale.US)
    }
}