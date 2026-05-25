package org.vestifeed.api.miniflux

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.vestifeed.http.executeAsync
import java.io.IOException

class MinifluxImpl(
    val client: OkHttpClient,
    val baseUrl: HttpUrl,
) : Miniflux {
    override suspend fun getFeeds(): List<Miniflux.Feed> {
        // https://miniflux.app/docs/api.html#endpoint-get-feeds
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
}