package org.vestifeed.podcasts

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PodcastsAdapterTest {

    private fun item(
        downloadProgress: Double? = null,
        entryRead: Boolean = false,
        played: Boolean = false,
    ) = PodcastsAdapter.Item(
        id = "id",
        entryId = "e",
        linkId = 1L,
        href = "https://example.com/a.mp3",
        type = "audio/mpeg",
        primaryText = "Title",
        secondaryText = "",
        downloadProgress = downloadProgress,
        cacheUri = if (downloadProgress == 1.0) "/cache/a.mp3" else null,
        entryRead = entryRead,
        played = played,
    )

    @Test
    fun badge_isDownloadingWhileInFlight() {
        assertEquals(StatusBadge.Downloading, statusBadgeFor(item(downloadProgress = 0.5)))
    }

    @Test
    fun badge_isUnplayedWhenDownloadedAndNotPlayed() {
        val row = item(downloadProgress = 1.0, played = false, entryRead = false)
        assertEquals(StatusBadge.Unplayed, statusBadgeFor(row))
    }

    @Test
    fun badge_isPlayedWhenDownloadedAndPlayed() {
        val row = item(downloadProgress = 1.0, played = true, entryRead = false)
        assertEquals(StatusBadge.Played, statusBadgeFor(row))
    }

    @Test
    fun badge_doesNotDependOnEntryReadFlag() {
        // Entry is marked read (e.g. opened from unread tab), but the
        // enclosure itself was never played. The badge should track
        // per-enclosure play state, not the parent entry's read state.
        val row = item(downloadProgress = 1.0, played = false, entryRead = true)
        assertEquals(StatusBadge.Unplayed, statusBadgeFor(row))
    }

    @Test
    fun badge_isNullWhenNotDownloaded() {
        assertNull(statusBadgeFor(item(downloadProgress = null)))
    }
}