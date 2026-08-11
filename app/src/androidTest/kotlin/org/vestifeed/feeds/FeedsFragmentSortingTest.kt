package org.vestifeed.feeds

import android.os.SystemClock
import android.widget.TextView
import androidx.fragment.app.testing.FragmentScenario
import androidx.fragment.app.testing.launchFragmentInContainer
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Test
import org.vestifeed.R
import org.vestifeed.app.db
import org.vestifeed.db.table.ConfTable
import org.vestifeed.db.table.FeedTable
import java.util.UUID

class FeedsFragmentSortingTest {

    @Test
    fun feedsAreSortedCaseInsensitively() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val db = context.db()
        db.conf.update {
            it.copy(backend = ConfTable.Backend.Embedded, syncOnStartup = false)
        }
        db.transaction {
            db.link.deleteAll()
            db.entry.deleteAll()
            db.feed.deleteAll()
        }

        val titles = listOf("banana", "Apple", "cherry", "Banana", "apple")
        titles.forEach { title ->
            db.feed.insertOrReplace(
                FeedTable.Feed(
                    id = UUID.randomUUID().toString(),
                    title = title,
                    extOpenEntriesInBrowser = false,
                    extBlockedWords = "",
                    extShowPreviewImages = false,
                ),
            )
        }

        launchFragmentInContainer<FeedsFragment>(
            themeResId = com.google.android.material.R.style.Theme_Material3_DynamicColors_DayNight,
        ).use { scenario ->
            val visibleTitles = waitForFeedTitles(scenario, titles.size)
            assertEquals(
                "Expected feeds to be sorted case-insensitively so 'C'/'c' appear next to each other",
                listOf("Apple", "apple", "banana", "Banana", "cherry"),
                visibleTitles,
            )
        }
    }

    private fun waitForFeedTitles(
        scenario: FragmentScenario<FeedsFragment>,
        expectedCount: Int,
    ): List<String> {
        val deadline = SystemClock.uptimeMillis() + LOAD_TIMEOUT_MILLIS
        var lastTitles: List<String> = emptyList()
        while (SystemClock.uptimeMillis() < deadline) {
            val titles = readFeedTitles(scenario)
            if (titles.size == expectedCount) {
                return titles
            }
            lastTitles = titles
            SystemClock.sleep(POLL_INTERVAL_MILLIS)
        }
        throw AssertionError(
            "Expected the list to have $expectedCount items but had ${lastTitles.size}: $lastTitles",
        )
    }

    private fun readFeedTitles(scenario: FragmentScenario<FeedsFragment>): List<String> {
        var titles: List<String> = emptyList()
        scenario.onFragment { fragment ->
            val list = fragment.view?.findViewById<RecyclerView>(R.id.list) ?: return@onFragment
            val adapter = list.adapter ?: return@onFragment
            val layoutManager = list.layoutManager as? LinearLayoutManager ?: return@onFragment
            val ordered = mutableListOf<String>()
            for (position in 0 until adapter.itemCount) {
                val viewHolder = list.findViewHolderForAdapterPosition(position)
                if (viewHolder == null) {
                    val firstVisible = layoutManager.findFirstVisibleItemPosition()
                    val lastVisible = layoutManager.findLastVisibleItemPosition()
                    if (position < firstVisible || position > lastVisible) {
                        return@onFragment
                    }
                    continue
                }
                val titleView = viewHolder.itemView.findViewById<TextView>(R.id.primaryText)
                ordered.add(titleView.text.toString())
            }
            titles = ordered
        }
        return titles
    }

    private companion object {
        const val LOAD_TIMEOUT_MILLIS = 15_000L
        const val POLL_INTERVAL_MILLIS = 200L
    }
}
