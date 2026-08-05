package org.vestifeed.entries

import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.fragment.app.commit
import org.vestifeed.R
import org.vestifeed.dialog.showErrorDialog
import org.vestifeed.entry.EntryFragment
import org.vestifeed.navigation.openUrl

/**
 * Translates [EntriesItemAction]s into actual navigation. Kept separate from
 * the fragment so the wiring can be unit-tested and reused (e.g. from search
 * results that share the same entry-tap semantics).
 */
class EntriesNavigator(private val host: Fragment) {

    fun handle(action: EntriesItemAction) {
        when (action) {
            is EntriesItemAction.OpenEntry -> openEntryFragment(action.entryId)
            is EntriesItemAction.OpenExternal -> openExternal(action.href, action.useBuiltInBrowser)
            is EntriesItemAction.OpenImageExternal -> openExternal(action.href, action.useBuiltInBrowser)
            EntriesItemAction.NoExternalLinks ->
                host.showErrorDialog(R.string.this_entry_doesnt_have_any_external_links)
        }
    }

    private fun openEntryFragment(entryId: String) {
        host.parentFragmentManager.commit {
            replace(
                R.id.fragmentContainerView,
                EntryFragment::class.java,
                bundleOf("entryId" to entryId),
            )
            addToBackStack(null)
        }
    }

    private fun openExternal(href: String, useBuiltInBrowser: Boolean) {
        host.openUrl(url = href, useBuiltInBrowser = useBuiltInBrowser)
    }
}