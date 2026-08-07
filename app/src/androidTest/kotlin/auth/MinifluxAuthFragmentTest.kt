package org.vestifeed.auth

import android.os.Build
import android.os.SystemClock
import androidx.fragment.app.Fragment
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.closeSoftKeyboard
import androidx.test.espresso.action.ViewActions.replaceText
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import java.util.concurrent.TimeUnit
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.vestifeed.R
import org.vestifeed.app.db
import org.vestifeed.backend.BackendSelectionFragment
import org.vestifeed.db.table.ConfTable
import org.vestifeed.entries.EntriesFragment
import org.vestifeed.navigation.Activity

@RunWith(AndroidJUnit4::class)
class MinifluxAuthFragmentTest {

    private lateinit var server: MockWebServer

    @Before
    fun setUp() {
        server = MockWebServer().apply { start() }
        InstrumentationRegistry.getInstrumentation().targetContext
            .db().conf.delete()
        AuthEvents.reset()
    }

    @After
    fun tearDown() {
        server.shutdown()
        AuthEvents.reset()
    }

    @Test
    fun selectingMinifluxFromBackendSelectorShowsAuthFragment() {
        ActivityScenario.launch(Activity::class.java).use { scenario ->
            waitForFragment<BackendSelectionFragment>(scenario, STARTUP_TIMEOUT_MILLIS)

            onView(withId(R.id.useMinifluxBackend)).perform(click())

            waitForFragment<MinifluxAuthFragment>(scenario, NAV_TIMEOUT_MILLIS)

            onView(withId(R.id.url)).check(matches(isDisplayed()))
            onView(withId(R.id.token)).check(matches(isDisplayed()))
            onView(withId(R.id.connect)).check(matches(isDisplayed()))
        }
    }

    @Test
    fun successfulAuthNavigatesToEntriesFragmentAndPersistsConf() {
        server.enqueue(MockResponse().setResponseCode(200).setBody("[]"))

        val baseUrl = server.url("/v1/").toString().removeSuffix("/v1/")
        val token = "test-token"

        ActivityScenario.launch(Activity::class.java).use { scenario ->
            navigateToMinifluxAuth(scenario)

            onView(withId(R.id.url)).perform(replaceText(baseUrl), closeSoftKeyboard())
            onView(withId(R.id.token)).perform(replaceText(token), closeSoftKeyboard())
            onView(withId(R.id.connect)).perform(click())

            waitForFragment<EntriesFragment>(scenario, AUTH_TIMEOUT_MILLIS)

            val conf = InstrumentationRegistry.getInstrumentation().targetContext
                .db().conf.select()
            assertEquals(ConfTable.Backend.Miniflux, conf.backend)
            assertEquals(baseUrl, conf.minifluxUrl)
            assertEquals(token, conf.minifluxToken)

            val request = server.takeRequest(REQUEST_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS)
            assertEquals("/v1/feeds", request!!.path)
        }
    }

    @Test
    fun unauthorized401DoesNotNavigateToEntriesFragmentAndSignalsInvalidation() {
        server.enqueue(MockResponse().setResponseCode(401))

        val baseUrl = server.url("/v1/").toString().removeSuffix("/v1/")

        ActivityScenario.launch(Activity::class.java).use { scenario ->
            navigateToMinifluxAuth(scenario)

            onView(withId(R.id.url)).perform(replaceText(baseUrl), closeSoftKeyboard())
            onView(withId(R.id.token)).perform(replaceText("bad-token"), closeSoftKeyboard())
            onView(withId(R.id.connect)).perform(click())

            val request = server.takeRequest(REQUEST_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS)
            assertEquals("/v1/feeds", request!!.path)

            // The errorInterceptor reports the invalidation synchronously
            // before throwing MinifluxUnauthenticatedException.
            assertTrue(
                "Expected AuthEvents.invalidationCount > 0 after 401",
                AuthEvents.invalidationCount.value > 0,
            )

            // After catching MinifluxUnauthenticatedException the fragment
            // must not navigate to EntriesFragment (the success path). It
            // either stays on MinifluxAuthFragment or, if the activity's
            // AuthEvents collector has already called logOut(), ends up on
            // BackendSelectionFragment.
            assertTrue(
                "Expected not to land on EntriesFragment after a 401",
                !waitForFragmentOr<EntriesFragment>(scenario, POST_AUTH_TIMEOUT_MILLIS),
            )
        }
    }

    private fun navigateToMinifluxAuth(scenario: ActivityScenario<Activity>) {
        waitForFragment<BackendSelectionFragment>(scenario, STARTUP_TIMEOUT_MILLIS)
        onView(withId(R.id.useMinifluxBackend)).perform(click())
        waitForFragment<MinifluxAuthFragment>(scenario, NAV_TIMEOUT_MILLIS)
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

    private inline fun <reified T : Fragment> waitForFragmentOr(
        scenario: ActivityScenario<Activity>,
        timeoutMillis: Long,
    ): Boolean {
        val deadline = SystemClock.uptimeMillis() + timeoutMillis
        var lastSeen: Fragment? = null

        while (SystemClock.uptimeMillis() < deadline) {
            scenario.onActivity { activity ->
                activity.supportFragmentManager.executePendingTransactions()
                lastSeen = activity.supportFragmentManager.findFragmentById(
                    R.id.fragmentContainerView,
                )
            }
            if (lastSeen is T) return true
            SystemClock.sleep(POLL_INTERVAL_MILLIS)
        }

        return false
    }

    private companion object {
        const val STARTUP_TIMEOUT_MILLIS = 10_000L
        const val NAV_TIMEOUT_MILLIS = 5_000L
        const val AUTH_TIMEOUT_MILLIS = 10_000L
        const val POST_AUTH_TIMEOUT_MILLIS = 2_000L
        const val REQUEST_TIMEOUT_MILLIS = 5_000L
        const val POLL_INTERVAL_MILLIS = 100L

        // ACCESS_LOCAL_NETWORK only exists on API 37+; on older devices the
        // LocalNetworkAccess check short-circuits and no permission is needed.
        val ACCESS_LOCAL_NETWORK_AVAILABLE = Build.VERSION.SDK_INT >= 37
    }
}
