package org.vestifeed.entry

import androidx.core.os.bundleOf
import androidx.fragment.app.testing.launchFragmentInContainer
import org.junit.Test

class EntryFragmentTest {

    @Test
    fun launch() {
        launchFragmentInContainer<EntryFragment>(
            themeResId = com.google.android.material.R.style.Theme_Material3_DynamicColors_DayNight,
            fragmentArgs = bundleOf("entryId" to ""),
        )
    }
}
