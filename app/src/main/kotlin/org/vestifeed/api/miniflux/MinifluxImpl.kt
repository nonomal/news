package org.vestifeed.api.miniflux

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.google.gson.JsonPrimitive
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.vestifeed.api.miniflux.Miniflux.EntriesPayload
import org.vestifeed.api.miniflux.MinifluxApi.Companion.JSON
import org.vestifeed.http.executeAsync
import java.io.IOException
import java.time.OffsetDateTime

class MinifluxImpl(
    val client: OkHttpClient,
    val baseUrl: HttpUrl,
) : Miniflux {
    override suspend fun getFeeds(): List<Miniflux.Feed> {
        val req = Request.Builder().url(baseUrl.newBuilder().addPathSegment("feeds").build()).get()
            .build()
        val res = client.newCall(req).executeAsync()
        if (res.code == 200) {
            // todo pass links
            return JsonParser.parseString(res.body.string()).asJsonArray.map { it.asJsonObject }
                .map { it.toMinifluxFeed() }
        } else {
            throw IOException("unexpected response code ${res.code}")
        }
    }

    private fun JsonObject.toMinifluxFeed(): Miniflux.Feed {
        return Miniflux.Feed(
            id = this["id"].asLong,
            title = if (has("title") && !this["title"].isJsonNull) this["title"].asString else "",
            feedUrl = if (has("feed_url") && !this["feed_url"].isJsonNull) this["feed_url"].asString else "",
            siteUrl = if (has("site_url") && !this["site_url"].isJsonNull) this["site_url"].asString else "",
        )
    }

    override suspend fun getUnreadEntries(): List<Miniflux.Entry> {
        val urlBuilder = baseUrl.newBuilder().addPathSegment("entries")
        urlBuilder.addQueryParameter("status", "unread")
        urlBuilder.addQueryParameter("limit", "0")
        val req = Request.Builder().url(urlBuilder.build()).get().build()
        val res = client.newCall(req).executeAsync()
        if (res.isSuccessful) {
            val body = res.body.string()
            val payload =
                JsonParser.parseString(body).asJsonObject.toEntriesPayload()
            return payload.entries
        } else {
            throw IOException("http request failed with response code ${res.code}")
        }
    }

    override suspend fun getStarredEntries(): List<Miniflux.Entry> {
        val urlBuilder = baseUrl.newBuilder().addPathSegment("entries")
        urlBuilder.addQueryParameter("starred", "1")
        urlBuilder.addQueryParameter("limit", "0")
        val req = Request.Builder().url(urlBuilder.build()).get().build()
        val res = client.newCall(req).executeAsync()
        if (res.isSuccessful) {
            val starredBody = res.body.string()
            val starredPayload =
                JsonParser.parseString(starredBody).asJsonObject.toEntriesPayload()
            return starredPayload.entries
        } else {
            throw IOException("http request failed with response code ${res.code}")
        }
    }

    override suspend fun getEntriesChangedAfter(
        changedAfter: OffsetDateTime,
        limit: Long
    ): List<Miniflux.Entry> {
        val urlBuilder = baseUrl.newBuilder().addPathSegment("entries")
        urlBuilder.addQueryParameter("changed_after", changedAfter.toEpochSecond().toString())
        urlBuilder.addQueryParameter("limit", limit.toString())
        val req = Request.Builder().url(urlBuilder.build()).get().build()
        val res = client.newCall(req).executeAsync()
        if (res.isSuccessful) {
            val body = res.body.string()
            val payload =
                JsonParser.parseString(body).asJsonObject.toEntriesPayload()
            return payload.entries
        } else {
            throw IOException("http request failed with response code ${res.code}")
        }
    }

    private fun JsonObject.toEntriesPayload(): EntriesPayload {
        val total = if (has("total") && !this["total"].isJsonNull) this["total"].asLong else 0
        val entriesArray = getAsJsonArray("entries") ?: JsonArray()
        val entries = entriesArray.map { it.asJsonObject.toEntry() }
        return EntriesPayload(
            total = total,
            entries = entries,
        )
    }

    private fun JsonObject.toEntry(): Miniflux.Entry {
        return Miniflux.Entry(
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
                this["enclosures"].asJsonArray.map { it.asJsonObject.toEntryEnclosure() }
            } else null,
        )
    }

    private fun JsonObject.toEntryEnclosure(): Miniflux.EntryEnclosure {
        return Miniflux.EntryEnclosure(
            id = this["id"].asLong,
            user_id = this["user_id"].asLong,
            entry_id = this["entry_id"].asLong,
            url = this["url"].asString,
            mime_type = this["mime_type"].asString,
            size = this["size"].asLong,
        )
    }

    override suspend fun markEntriesAsRead(ids: List<Long>, read: Boolean) {
        val args = JsonObject().apply {
            add("entry_ids", JsonArray().apply { ids.forEach { add(it) } })
            add("status", JsonPrimitive(if (read) "read" else "unread"))
        }
        val req = Request.Builder().url(baseUrl.newBuilder().addPathSegment("entries").build())
            .put(args.toString().toRequestBody(JSON)).build()
        val res = client.newCall(req).executeAsync()
        if (!res.isSuccessful || res.code != 204) {
            throw IOException("unexpected response code ${res.code}")
        }
    }

    override suspend fun markEntriesAsStarred(ids: List<Long>, starred: Boolean) {
        ids.forEach { id ->
            val req = Request.Builder().url(
                baseUrl.newBuilder().addPathSegment("entries").addPathSegment(id.toString())
                    .addPathSegment("bookmark").build()
            ).put(ByteArray(0).toRequestBody(null, 0, 0)).build()
            val rawRes = client.newCall(req).executeAsync()
            if (!rawRes.isSuccessful) {
                throw IOException("http request failed with response code ${rawRes.code}")
            }
        }
    }
}