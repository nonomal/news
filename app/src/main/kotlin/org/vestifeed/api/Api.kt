package org.vestifeed.api

import android.util.Base64
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.google.gson.JsonPrimitive
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.jsoup.Jsoup
import org.vestifeed.api.miniflux.minifluxHttpClient
import org.vestifeed.api.standalone.toEntrySummary
import org.vestifeed.db.Database
import org.vestifeed.db.table.ConfTable
import org.vestifeed.db.table.EntryTable
import org.vestifeed.db.table.FeedTable
import org.vestifeed.db.table.LinkTable
import org.vestifeed.http.await
import org.vestifeed.http.executeAsync
import org.vestifeed.parser.AtomEntry
import org.vestifeed.parser.AtomFeed
import org.vestifeed.parser.AtomLink
import org.vestifeed.parser.AtomLinkRel
import org.vestifeed.parser.FeedResult
import org.vestifeed.parser.RssFeed
import org.vestifeed.parser.RssItem
import org.vestifeed.parser.RssItemGuid
import org.vestifeed.parser.feed
import java.io.IOException
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.time.OffsetDateTime
import java.util.Date
import java.util.Locale

typealias ParsedFeed = org.vestifeed.parser.Feed

sealed class Api {
    data class AddFeedResult(
        val feed: FeedTable.Feed,
        val feedLinks: List<LinkTable.Link>,
        val entries: List<Pair<EntryTable.Entry, List<LinkTable.Link>>>,
    )

    abstract suspend fun addFeed(url: HttpUrl): AddFeedResult

    abstract suspend fun getFeeds(): List<FeedTable.Feed>

    abstract suspend fun updateFeedTitle(
        feedId: String,
        newTitle: String,
    ): Result<Unit>

    abstract suspend fun deleteFeed(feedId: String): Result<Unit>

    class Standalone(private val db: Database) : Api() {

        private val httpClient = OkHttpClient()

        override suspend fun addFeed(url: HttpUrl): AddFeedResult {
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
                        addFeed(href.toHttpUrl())
                    } else {
                        addFeed("$url$href".toHttpUrl())
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

        suspend fun getNewAndUpdatedEntries(
            maxEntryId: String?,
            maxEntryUpdated: OffsetDateTime?,
            lastSync: OffsetDateTime?,
        ): List<Pair<EntryTable.Entry, List<LinkTable.Link>>> {
            val fetchedEntries = mutableListOf<Pair<EntryTable.Entry, List<LinkTable.Link>>>()
            val feeds = withContext(Dispatchers.IO) { db.feed.selectAll() }
            feeds.forEach { fetchedEntries += fetchEntries(it) }
            return fetchedEntries
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

        private fun ParsedFeed.toFeed(feedUrl: HttpUrl): Pair<FeedTable.Feed, List<LinkTable.Link>> {
            return when (this) {
                is AtomFeed -> {
                    val selfLink = links.single { it.rel == AtomLinkRel.Self }
                    val links = links.map { it.toLink(feedId = selfLink.href, entryId = null) }

                    Pair(
                        FeedTable.Feed(
                            id = selfLink.href,
                            title = title,
                            extOpenEntriesInBrowser = false,
                            extBlockedWords = "",
                            extShowPreviewImages = null,
                        ), links
                    )
                }

                is RssFeed -> {
                    val selfLink = LinkTable.Link(
                        feedId = channel.link,
                        entryId = null,
                        href = feedUrl.toString(),
                        rel = AtomLinkRel.Self,
                        type = null,
                        hreflang = null,
                        title = null,
                        length = null,
                        extEnclosureDownloadProgress = null,
                        extCacheUri = null,
                        id = null,
                    )

                    val alternateLink = LinkTable.Link(
                        feedId = channel.link,
                        entryId = null,
                        href = channel.link,
                        rel = AtomLinkRel.Alternate,
                        type = null,
                        hreflang = null,
                        title = null,
                        length = null,
                        extEnclosureDownloadProgress = null,
                        extCacheUri = null,
                        id = null,
                    )

                    Pair(
                        FeedTable.Feed(
                            id = channel.link,
                            title = channel.title,
                            extOpenEntriesInBrowser = false,
                            extBlockedWords = "",
                            extShowPreviewImages = null,
                        ), listOf(selfLink, alternateLink)
                    )
                }
            }
        }

        private fun AtomLink.toLink(
            feedId: String?,
            entryId: String?,
        ): LinkTable.Link {
            return LinkTable.Link(
                id = null,
                feedId = feedId,
                entryId = entryId,
                href = href,
                rel = rel,
                type = type,
                hreflang = hreflang,
                title = title,
                length = length,
                extEnclosureDownloadProgress = null,
                extCacheUri = null,
            )
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

        private fun ParsedFeed.getEntries(feedId: String): List<Pair<EntryTable.Entry, List<LinkTable.Link>>> {
            return when (this) {
                is RssFeed -> {
                    this.channel.items
                        .getOrElse { emptyList() }
                        .filter { it.isSuccess }
                        .map { it.getOrThrow().toEntry(feedId) }
                }

                is AtomFeed -> {
                    this.entries.map { it.toEntry(feedId) }
                }
            }
        }

        companion object {
            private val ISO = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSXXX", Locale.US)
        }
    }

    open class Miniflux(val client: OkHttpClient, val baseUrl: HttpUrl) : Api() {

        companion object {
            val JSON = "application/json".toMediaType()
        }

        private data class MinifluxFeed(
            val id: Long,
            val title: String,
            val feedUrl: String,
            val siteUrl: String,
        )

        private data class EntriesPayload(
            val total: Long,
            val entries: List<EntryJson>,
        )

        private data class EntryJson(
            val id: Long,
            val feed_id: Long,
            val status: String,
            val title: String,
            val url: String,
            val comments_url: String,
            val published_at: String,
            val created_at: String,
            val changed_at: String,
            val content: String,
            val author: String,
            val starred: Boolean,
            val enclosures: List<EntryEnclosureJson>?,
        )

        private data class EntryEnclosureJson(
            val id: Long,
            val user_id: Long,
            val entry_id: Long,
            val url: String,
            val mime_type: String,
            val size: Long,
        )

        private suspend fun getFeed(id: Long): MinifluxFeed {
            // https://miniflux.app/docs/api.html#endpoint-get-feed
            val req = Request.Builder().url(
                baseUrl.newBuilder().addPathSegment("feeds").addPathSegment(id.toString()).build()
            ).get().build()
            val res = client.newCall(req).executeAsync()
            return if (res.code == 200) {
                JsonParser.parseString(res.body.string()).asJsonObject.toMinifluxFeed()
            } else {
                throw IOException("unexpected response code ${res.code}")
            }
        }

        override suspend fun addFeed(url: HttpUrl): AddFeedResult {
            // https://miniflux.app/docs/api.html#endpoint-create-feed
            val args = JsonObject().apply { add("feed_url", JsonPrimitive(url.toString())) }
            val req = Request.Builder().url(baseUrl.newBuilder().addPathSegment("feeds").build())
                .post(args.toString().toRequestBody(JSON)).build()
            val res = client.newCall(req).executeAsync()
            if (res.code == 201) {
                val body = JsonParser.parseString(res.body.string()).asJsonObject
                val feedId = body["feed_id"].asLong
                val (feed, links) = getFeed(feedId).toVestiFeed()
                return AddFeedResult(
                    feed = feed,
                    feedLinks = links,
                    entries = emptyList(),
                )
            } else {
                throw IOException("unexpected response code ${res.code}")
            }
        }

        final override suspend fun getFeeds(): List<FeedTable.Feed> {
            return getFeedsWithLinks().map { it.first }
        }

        open suspend fun getFeedsWithLinks(): List<Pair<FeedTable.Feed, List<LinkTable.Link>>> {
            // https://miniflux.app/docs/api.html#endpoint-get-feeds
            val req = Request.Builder().url(baseUrl.newBuilder().addPathSegment("feeds").build()).get()
                .build()
            val res = client.newCall(req).executeAsync()
            return if (res.code == 200) {
                JsonParser.parseString(res.body.string()).asJsonArray.map { it.asJsonObject }
                    .map { it.toMinifluxFeed() }
                    .map { it.toVestiFeed() }
            } else {
                throw IOException("unexpected response code ${res.code}")
            }
        }

        override suspend fun updateFeedTitle(feedId: String, newTitle: String): Result<Unit> {
            // https://miniflux.app/docs/api.html#endpoint-update-feed
            val args = JsonObject().apply { add("title", JsonPrimitive(newTitle)) }
            val req = Request.Builder()
                .url(baseUrl.newBuilder().addPathSegment("feeds").addPathSegment(feedId).build())
                .put(args.toString().toRequestBody(JSON)).build()
            val res = client.newCall(req).executeAsync()
            return if (res.isSuccessful) {
                Result.success(Unit)
            } else {
                Result.failure(IOException("unexpected response code ${res.code}"))
            }
        }

        override suspend fun deleteFeed(feedId: String): Result<Unit> {
            // https://miniflux.app/docs/api.html#endpoint-remove-feed
            val req = Request.Builder()
                .url(baseUrl.newBuilder().addPathSegment("feeds").addPathSegment(feedId).build())
                .delete().build()
            val res = client.newCall(req).executeAsync()
            return if (res.code == 204) {
                Result.success(Unit)
            } else {
                Result.failure(IOException("unexpected response code ${res.code}"))
            }
        }

        open suspend fun getUnreadEntries(): List<Pair<EntryTable.Entry, List<LinkTable.Link>>> {
            // https://miniflux.app/docs/api.html#endpoint-get-entries
            val urlBuilder = baseUrl.newBuilder().addPathSegment("entries")
            urlBuilder.addQueryParameter("status", "unread")
            urlBuilder.addQueryParameter("limit", "0")
            val req = Request.Builder().url(urlBuilder.build()).get().build()
            val res = client.newCall(req).executeAsync()
            return if (res.isSuccessful) {
                val body = res.body.string()
                val payload = JsonParser.parseString(body).asJsonObject.toEntriesPayload()
                payload.entries.map { it.toEntry() }
            } else {
                throw IOException("http request failed with response code ${res.code}")
            }
        }

        open suspend fun getStarredEntries(): List<Pair<EntryTable.Entry, List<LinkTable.Link>>> {
            // https://miniflux.app/docs/api.html#endpoint-get-entries
            val urlBuilder = baseUrl.newBuilder().addPathSegment("entries")
            urlBuilder.addQueryParameter("starred", "1")
            urlBuilder.addQueryParameter("limit", "0")
            val req = Request.Builder().url(urlBuilder.build()).get().build()
            val res = client.newCall(req).executeAsync()
            return if (res.isSuccessful) {
                val starredBody = res.body.string()
                val starredPayload =
                    JsonParser.parseString(starredBody).asJsonObject.toEntriesPayload()
                starredPayload.entries.map { it.toEntry() }
            } else {
                throw IOException("http request failed with response code ${res.code}")
            }
        }

        open suspend fun getEntriesChangedAfter(
            changedAfter: OffsetDateTime,
            limit: Long,
        ): List<Pair<EntryTable.Entry, List<LinkTable.Link>>> {
            // https://miniflux.app/docs/api.html#endpoint-get-entries
            val urlBuilder = baseUrl.newBuilder().addPathSegment("entries")
            urlBuilder.addQueryParameter("changed_after", changedAfter.toEpochSecond().toString())
            urlBuilder.addQueryParameter("limit", limit.toString())
            val req = Request.Builder().url(urlBuilder.build()).get().build()
            val res = client.newCall(req).executeAsync()
            return if (res.isSuccessful) {
                val body = res.body.string()
                val payload = JsonParser.parseString(body).asJsonObject.toEntriesPayload()
                payload.entries.map { it.toEntry() }
            } else {
                throw IOException("http request failed with response code ${res.code}")
            }
        }

        open suspend fun markEntriesAsRead(entriesIds: List<String>, read: Boolean) {
            // https://miniflux.app/docs/api.html#endpoint-update-entries
            val args = JsonObject().apply {
                add("entry_ids", JsonArray().apply { entriesIds.forEach { add(it.toLong()) } })
                add("status", JsonPrimitive(if (read) "read" else "unread"))
            }
            val req = Request.Builder().url(baseUrl.newBuilder().addPathSegment("entries").build())
                .put(args.toString().toRequestBody(JSON)).build()
            val res = client.newCall(req).executeAsync()
            if (!res.isSuccessful || res.code != 204) {
                throw IOException("unexpected response code ${res.code}")
            }
        }

        open suspend fun markEntriesAsBookmarked(
            entries: List<EntryTable.EntryWithoutContent>,
            bookmarked: Boolean,
        ) {
            // https://miniflux.app/docs/api.html#endpoint-update-entry
            entries.forEach { entry ->
                val req = Request.Builder().url(
                    baseUrl.newBuilder().addPathSegment("entries").addPathSegment(entry.id)
                        .addPathSegment("bookmark").build()
                ).put(ByteArray(0).toRequestBody(null, 0, 0)).build()
                val rawRes = client.newCall(req).executeAsync()
                if (!rawRes.isSuccessful) {
                    throw IOException("http request failed with response code ${rawRes.code}")
                }
            }
        }

        private fun EntryJson.toEntry(): Pair<EntryTable.Entry, List<LinkTable.Link>> {
            val links = mutableListOf<LinkTable.Link>()

            if (url.isNotBlank()) {
                links += LinkTable.Link(
                    id = null,
                    feedId = null,
                    entryId = id.toString(),
                    href = url,
                    rel = AtomLinkRel.Alternate,
                    type = "text/html",
                    hreflang = null,
                    title = null,
                    length = null,
                    extEnclosureDownloadProgress = null,
                    extCacheUri = null,
                )
            }

            enclosures?.forEach { enclosure ->
                links += LinkTable.Link(
                    id = null,
                    feedId = null,
                    entryId = id.toString(),
                    href = enclosure.url,
                    rel = AtomLinkRel.Enclosure,
                    type = enclosure.mime_type,
                    hreflang = null,
                    title = null,
                    length = enclosure.size,
                    extEnclosureDownloadProgress = null,
                    extCacheUri = null,
                )
            }

            return Pair(
                EntryTable.Entry(
                    contentType = "html",
                    contentSrc = "",
                    contentText = content,
                    summary = null,
                    id = id.toString(),
                    feedId = feed_id.toString(),
                    title = title,
                    published = OffsetDateTime.parse(published_at),
                    updated = OffsetDateTime.parse(changed_at),
                    authorName = author,
                    extRead = status == "read",
                    extReadSynced = true,
                    extBookmarked = starred,
                    extBookmarkedSynced = true,
                    extCommentsUrl = comments_url,
                    extOpenGraphImageChecked = false,
                    extOpenGraphImageUrl = "",
                    extOpenGraphImageWidth = 0,
                    extOpenGraphImageHeight = 0,
                ), links
            )
        }

        private fun JsonObject.toEntriesPayload(): EntriesPayload {
            val total = if (has("total") && !this["total"].isJsonNull) this["total"].asLong else 0
            val entriesArray = getAsJsonArray("entries") ?: JsonArray()
            val entries = entriesArray.map { it.asJsonObject.toEntryJson() }
            return EntriesPayload(
                total = total,
                entries = entries,
            )
        }

        private fun JsonObject.toEntryJson(): EntryJson {
            return EntryJson(
                id = this["id"].asLong,
                feed_id = this["feed_id"].asLong,
                status = this["status"].asString,
                title = this["title"].asString,
                url = this["url"].asString,
                comments_url = if (has("comments_url") && !this["comments_url"].isJsonNull) this["comments_url"].asString else "",
                published_at = if (has("published_at") && !this["published_at"].isJsonNull) this["published_at"].asString else "",
                created_at = if (has("created_at") && !this["created_at"].isJsonNull) this["created_at"].asString else "",
                changed_at = if (has("changed_at") && !this["changed_at"].isJsonNull) this["changed_at"].asString else "",
                content = if (has("content") && !this["content"].isJsonNull) this["content"].asString else "",
                author = if (has("author") && !this["author"].isJsonNull) this["author"].asString else "",
                starred = if (has("starred") && !this["starred"].isJsonNull) this["starred"].asBoolean else false,
                enclosures = if (has("enclosures") && !this["enclosures"].isJsonNull) {
                    this["enclosures"].asJsonArray.map { it.asJsonObject.toEntryEnclosureJson() }
                } else null,
            )
        }

        private fun JsonObject.toEntryEnclosureJson(): EntryEnclosureJson {
            return EntryEnclosureJson(
                id = this["id"].asLong,
                user_id = this["user_id"].asLong,
                entry_id = this["entry_id"].asLong,
                url = this["url"].asString,
                mime_type = this["mime_type"].asString,
                size = this["size"].asLong,
            )
        }

        private fun JsonObject.toMinifluxFeed(): MinifluxFeed {
            return MinifluxFeed(
                id = this["id"].asLong,
                title = if (has("title") && !this["title"].isJsonNull) this["title"].asString else "",
                feedUrl = if (has("feed_url") && !this["feed_url"].isJsonNull) this["feed_url"].asString else "",
                siteUrl = if (has("site_url") && !this["site_url"].isJsonNull) this["site_url"].asString else "",
            )
        }

        private fun MinifluxFeed.toVestiFeed(): Pair<FeedTable.Feed, List<LinkTable.Link>> {
            val feedId = id.toString()

            val selfLink = LinkTable.Link(
                id = null,
                feedId = feedId,
                entryId = null,
                href = feedUrl,
                rel = AtomLinkRel.Self,
                type = null,
                hreflang = null,
                title = null,
                length = null,
                extEnclosureDownloadProgress = null,
                extCacheUri = null,
            )
            val alternateLink = LinkTable.Link(
                id = null,
                feedId = feedId,
                entryId = null,
                href = siteUrl,
                rel = AtomLinkRel.Alternate,
                type = "text/html",
                hreflang = null,
                title = null,
                length = null,
                extEnclosureDownloadProgress = null,
                extCacheUri = null,
            )
            val feed = FeedTable.Feed(
                id = feedId,
                title = title,
                extOpenEntriesInBrowser = false,
                extBlockedWords = "",
                extShowPreviewImages = null,
            )
            return Pair(feed, listOf(selfLink, alternateLink))
        }
    }
}

fun api(db: Database): Api {
    val conf = db.conf.select()
    return when (conf.backend) {
        ConfTable.Backend.Miniflux -> {
            if (conf.minifluxUrl == null || conf.minifluxToken == null) {
                Api.Standalone(db)
            } else {
                Api.Miniflux(
                    client = minifluxHttpClient(token = conf.minifluxToken),
                    baseUrl = "${conf.minifluxUrl}/v1/".toHttpUrl(),
                )
            }
        }

        ConfTable.Backend.Embedded -> Api.Standalone(db)
        null -> Api.Standalone(db)
    }
}
