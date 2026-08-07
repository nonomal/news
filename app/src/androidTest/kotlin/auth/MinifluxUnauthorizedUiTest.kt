package org.vestifeed.auth

import android.os.SystemClock
import androidx.fragment.app.Fragment
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertNull
import org.junit.Test
import org.vestifeed.R
import org.vestifeed.app.db
import org.vestifeed.db.table.ConfTable
import org.vestifeed.entries.EntriesFragment
import org.vestifeed.navigation.Activity

class MinifluxUnauthorizedUiTest {

    @Test
    fun unauthorizedResponseLogsOutAndClearsConf() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val db = context.db()
        db.conf.update {
            it.copy(backend = ConfTable.Backend.Miniflux, syncOnStartup = false)
        }
        AuthEvents.reset()

        ActivityScenario.launch(Activity::class.java).use { scenario ->
            waitForFragment<EntriesFragment>(scenario, STARTED_TIMEOUT_MILLIS)

            AuthEvents.reportInvalidated()

            waitForFragment<AuthFragment>(scenario, LOGOUT_TIMEOUT_MILLIS)

            scenario.onActivity {
                assertNull(
                    "Expected backend to be cleared after invalidation",
                    db.conf.select().backend,
                )
            }
        }
    }

    private inline fun <reified T : Fragment> waitForFragment(
        scenario: ActivityScenario<Activity>,
        timeoutMillis: Long,
    ) {
        val deadline = SystemClock.uptimeMillis() + timeoutMillis
        var lastSeen: Fragment? = null

        while (SystemClock.uptimeMillis() < deadline) {
            scenario.onActivity { activity ->
                activity.supportFragmentManager.executePendingTransactions()
                lastSeen = activity.supportFragmentManager.findFragmentById(
                    R.id.fragmentContainerView,
                )
            }
            if (lastSeen is T) return
            SystemClock.sleep(POLL_INTERVAL_MILLIS)
        }

        throw AssertionError(
            "Expected ${T::class.java.simpleName} but found ${lastSeen?.javaClass?.simpleName}",
        )
    }

    private companion object {
        const val STARTED_TIMEOUT_MILLIS = 10_000L
        const val LOGOUT_TIMEOUT_MILLIS = 5_000L
        const val POLL_INTERVAL_MILLIS = 100L
    }
}
