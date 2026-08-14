package org.vestifeed.podcasts

data class PodcastsScreenState(
    val items: List<PodcastsAdapter.Item> = emptyList(),
)
