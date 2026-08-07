package org.vestifeed.e2e

import android.os.SystemClock
import android.view.InputDevice
import android.view.MotionEvent
import android.view.View
import android.view.animation.LinearInterpolator
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.RecyclerView
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.NoMatchingViewException
import androidx.test.espresso.UiController
import androidx.test.espresso.ViewAction
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.closeSoftKeyboard
import androidx.test.espresso.action.ViewActions.replaceText
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isClickable
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.isEnabled
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import org.hamcrest.Matcher
import org.hamcrest.Matchers.allOf
import org.junit.Assert.assertTrue
import org.junit.Test
import org.vestifeed.R
import org.vestifeed.backend.BackendSelectionFragment
import org.vestifeed.entries.EntriesFragment
import org.vestifeed.feeds.FeedsFragment
import org.vestifeed.navigation.Activity

class EmbeddedBackendTest {

    @Test
    fun freshInstallCanAddEmbeddedFeed() {
        ActivityScenario.launch(Activity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                assertTrue(
                    activity.supportFragmentManager.findFragmentById(
                        R.id.fragmentContainerView,
                    ) is BackendSelectionFragment,
                )
            }

            onView(withId(R.id.useMinifluxBackend)).check(
                matches(allOf(isDisplayed(), isEnabled(), isClickable())),
            )
            onView(withId(R.id.useEmbeddedBackend)).check(
                matches(allOf(isDisplayed(), isEnabled(), isClickable())),
            )
            onView(withId(R.id.useEmbeddedBackend)).perform(
                pressAndHoldClick(AUTH_BUTTON_HOLD_MILLIS),
            )

            scenario.onActivity { activity ->
                activity.supportFragmentManager.executePendingTransactions()
                assertTrue(
                    activity.supportFragmentManager.findFragmentById(
                        R.id.fragmentContainerView,
                    ) is EntriesFragment,
                )
            }

            onView(withId(R.id.bottomNav)).check(matches(isDisplayed()))
            onView(withId(R.id.feedsFragment)).perform(click())

            waitUntilFeedsFragment(scenario, FEEDS_SCREEN_TIMEOUT_MILLIS)

            waitUntilDisplayed(withId(R.id.fab), FEEDS_SCREEN_TIMEOUT_MILLIS)
            onView(withId(R.id.fab)).perform(click())
            waitUntilDisplayed(withId(R.id.url), FEEDS_SCREEN_TIMEOUT_MILLIS)
            onView(withId(R.id.url)).perform(
                replaceText(FEED_URL),
                closeSoftKeyboard(),
            )
            onView(withId(android.R.id.button1)).perform(click())

            waitUntilDisplayed(
                allOf(withId(R.id.primaryText), withText(FEED_TITLE)),
                FEED_ADD_TIMEOUT_MILLIS,
            )

            onView(withId(R.id.newsFragment)).perform(click())
            scenario.onActivity { activity ->
                activity.supportFragmentManager.executePendingTransactions()
                assertTrue(
                    activity.supportFragmentManager.findFragmentById(
                        R.id.fragmentContainerView,
                    ) is EntriesFragment,
                )
            }

            waitUntilDisplayed(withId(R.id.list), ENTRIES_SCREEN_TIMEOUT_MILLIS)
            smoothScrollFor(scenario, FINAL_SCROLL_DURATION_MILLIS)
        }
    }

    private fun pressAndHoldClick(durationMillis: Long): ViewAction {
        return object : ViewAction {
            override fun getConstraints(): Matcher<View> =
                allOf(isDisplayed(), isEnabled(), isClickable())

            override fun getDescription() = "press and hold for $durationMillis milliseconds"

            override fun perform(uiController: UiController, view: View) {
                val location = IntArray(2)
                view.getLocationOnScreen(location)
                val x = location[0] + view.width / 2f
                val y = location[1] + view.height / 2f
                val downTime = SystemClock.uptimeMillis()
                val down = MotionEvent.obtain(
                    downTime,
                    downTime,
                    MotionEvent.ACTION_DOWN,
                    x,
                    y,
                    0,
                ).apply {
                    source = InputDevice.SOURCE_TOUCHSCREEN
                }

                try {
                    if (!uiController.injectMotionEvent(down)) {
                        throw AssertionError("Failed to press view")
                    }
                } finally {
                    down.recycle()
                }

                uiController.loopMainThreadForAtLeast(durationMillis)

                val up = MotionEvent.obtain(
                    downTime,
                    SystemClock.uptimeMillis(),
                    MotionEvent.ACTION_UP,
                    x,
                    y,
                    0,
                ).apply {
                    source = InputDevice.SOURCE_TOUCHSCREEN
                }

                try {
                    if (!uiController.injectMotionEvent(up)) {
                        throw AssertionError("Failed to release view")
                    }
                } finally {
                    up.recycle()
                }

                uiController.loopMainThreadUntilIdle()
            }
        }
    }

    private fun smoothScrollFor(
        scenario: ActivityScenario<Activity>,
        durationMillis: Long,
    ) {
        scenario.onActivity { activity ->
            val distance = (SCROLL_DISTANCE_DP * activity.resources.displayMetrics.density).toInt()
            activity.findViewById<RecyclerView>(R.id.list).smoothScrollBy(
                0,
                distance,
                LinearInterpolator(),
                durationMillis.toInt(),
            )
        }
        SystemClock.sleep(durationMillis)
    }

    private fun waitUntilDisplayed(matcher: Matcher<View>, timeoutMillis: Long) {
        val timeoutAt = SystemClock.uptimeMillis() + timeoutMillis
        var lastFailure: Throwable? = null

        do {
            try {
                onView(matcher).check(matches(isDisplayed()))
                return
            } catch (failure: NoMatchingViewException) {
                lastFailure = failure
            } catch (failure: AssertionError) {
                lastFailure = failure
            }

            SystemClock.sleep(POLL_INTERVAL_MILLIS)
        } while (SystemClock.uptimeMillis() < timeoutAt)

        throw lastFailure ?: AssertionError("View did not become visible")
    }

    private fun waitUntilFeedsFragment(
        scenario: ActivityScenario<Activity>,
        timeoutMillis: Long,
    ) {
        val timeoutAt = SystemClock.uptimeMillis() + timeoutMillis
        var lastFailure: AssertionError? = null

        do {
            var current: Fragment? = null
            scenario.onActivity { activity ->
                activity.supportFragmentManager.executePendingTransactions()
                current = activity.supportFragmentManager.findFragmentById(
                    R.id.fragmentContainerView,
                )
            }

            if (current is FeedsFragment) return

            lastFailure = AssertionError("Container holds $current")
            SystemClock.sleep(POLL_INTERVAL_MILLIS)
        } while (SystemClock.uptimeMillis() < timeoutAt)

        throw lastFailure ?: AssertionError("FeedsFragment not installed")
    }

    private companion object {
        const val FEED_URL = "bubelov.com"
        const val FEED_TITLE = "Igor Bubelov"
        const val AUTH_BUTTON_HOLD_MILLIS = 1_000L
        const val FEEDS_SCREEN_TIMEOUT_MILLIS = 10_000L
        const val ENTRIES_SCREEN_TIMEOUT_MILLIS = 10_000L
        const val FEED_ADD_TIMEOUT_MILLIS = 120_000L
        const val FINAL_SCROLL_DURATION_MILLIS = 2_000L
        const val SCROLL_DISTANCE_DP = 4_000
        const val POLL_INTERVAL_MILLIS = 250L
    }
}
