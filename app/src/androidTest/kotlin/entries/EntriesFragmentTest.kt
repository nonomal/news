package org.vestifeed.entries

import androidx.fragment.app.testing.launchFragmentInContainer
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Test
import org.vestifeed.app.db
import org.vestifeed.db.table.ConfTable

class EntriesFragmentTest {

    @Test
    fun launch() {
        val db = InstrumentationRegistry.getInstrumentation().targetContext.db()
        db.conf.update { it.copy(backend = ConfTable.Backend.Embedded) }

        launchFragmentInContainer<EntriesFragment>(
            themeResId = com.google.android.material.R.style.Theme_Material3_DynamicColors_DayNight,
            fragmentArgs = EntriesFilter.Unread.toBundle(),
        )
    }
}
