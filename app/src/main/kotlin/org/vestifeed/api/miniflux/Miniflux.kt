package org.vestifeed.api.miniflux

interface Miniflux {
    data class Feed(
        val id: Long,
        val title: String,
        val feedUrl: String,
        val siteUrl: String,
    )

    // https://miniflux.app/docs/api.html#endpoint-get-feeds
    fun getFeeds(): List<Feed>
}