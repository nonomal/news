package org.vestifeed.backend

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.google.gson.JsonPrimitive
import okhttp3.HttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.vestifeed.db.Database
import org.vestifeed.db.table.EntryTable
import org.vestifeed.db.table.FeedTable
import org.vestifeed.db.table.LinkTable
import org.vestifeed.http.executeAsync
import org.vestifeed.parser.AtomLinkRel
import java.io.IOException
import java.time.OffsetDateTime

open class Miniflux(
    val client: OkHttpClient,
    val baseUrl: HttpUrl,
    db: Database,
) : Backend(db) {

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

    override suspend fun sync(initial: Boolean) {
        val sync = MinifluxSync(this, db)
        sync.syncFeeds()
        sync.syncEntries(initial = initial)
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
        // The Miniflux API defaults to the user's preferred sort order
        // (e.g. `published_at desc`), which means `changed_after` pagination
        // skips entries whose `published_at` is older even though their
        // `changed_at` is newer than the cursor. Pin the order to
        // `changed_at asc` so the cursor walks the change log chronologically.
        urlBuilder.addQueryParameter("order", "changed_at")
        urlBuilder.addQueryParameter("direction", "asc")
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
                extOpenGraphImageFetchedAt = null,
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

    companion object {
        val JSON = "application/json".toMediaType()
    }
}
