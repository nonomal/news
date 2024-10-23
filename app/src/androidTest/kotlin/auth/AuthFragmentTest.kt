package auth

import androidx.fragment.app.testing.launchFragmentInContainer
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withId
import co.appreactor.news.R
import org.junit.Test

class AuthFragmentTest {

    @Test
    fun test() {
        launchFragmentInContainer<AuthFragment>(
            themeResId = com.google.android.material.R.style.Theme_Material3_DynamicColors_DayNight,
        ).use {
            onView(withId(R.id.useStandaloneBackend)).check(matches(isDisplayed()))
            onView(withId(R.id.useMinifluxBackend)).check(matches(isDisplayed()))
            onView(withId(R.id.useNextcloudBackend)).check(matches(isDisplayed()))
        }
    }
}