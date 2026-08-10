package org.vestifeed.entries

import android.os.SystemClock
import androidx.fragment.app.Fragment
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.vestifeed.R
import org.vestifeed.app.db
import org.vestifeed.db.table.ConfTable
import org.vestifeed.navigation.Activity

class BottomNavVisibilityAfterRecreateTest {

    @Test
    fun bottomNavRemainsVisibleOnUnreadTabAfterProcessDeathRecreate() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val db = context.db()
        db.conf.update {
            it.copy(backend = ConfTable.Backend.Embedded, syncOnStartup = false)
        }

        ActivityScenario.launch(Activity::class.java).use { scenario ->
            waitForTopFragment(scenario, isEntriesFragment = true, TIMEOUT_MILLIS)
            assertBottomNavVisible(scenario, expected = true)

            scenario.recreate()

            waitForTopFragment(scenario, isEntriesFragment = true, TIMEOUT_MILLIS)
            assertBottomNavVisible(scenario, expected = true)
        }
    }

    private fun waitForTopFragment(
        scenario: ActivityScenario<Activity>,
        isEntriesFragment: Boolean,
        timeoutMillis: Long,
    ) {
        val deadline = SystemClock.uptimeMillis() + timeoutMillis
        var lastSeen: String? = null
        while (SystemClock.uptimeMillis() < deadline) {
            var matched = false
            scenario.onActivity { activity ->
                val top = activity.supportFragmentManager
                    .findFragmentById(R.id.fragmentContainerView)
                lastSeen = top?.javaClass?.simpleName
                matched = if (isEntriesFragment) {
                    top is EntriesFragment
                } else {
                    top !is EntriesFragment
                }
            }
            if (matched) return
            SystemClock.sleep(POLL_INTERVAL_MILLIS)
        }
        throw AssertionError(
            "Expected top fragment to be ${if (isEntriesFragment) "EntriesFragment" else "non-EntriesFragment"} " +
                "but was $lastSeen",
        )
    }

    private fun assertBottomNavVisible(scenario: ActivityScenario<Activity>, expected: Boolean) {
        var actual = !expected
        scenario.onActivity { activity ->
            val bottomNav = activity.findViewById<android.view.View>(R.id.bottomNav)
            actual = bottomNav.visibility == android.view.View.VISIBLE
        }
        assertEquals("Expected bottom nav visibility=$expected but was $actual", expected, actual)
    }

    private companion object {
        const val TIMEOUT_MILLIS = 10_000L
        const val POLL_INTERVAL_MILLIS = 50L
    }
}
