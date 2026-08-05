package org.vestifeed.entries

import androidx.annotation.StringRes

/**
 * UI state for [EntriesFragment]. Single source of truth — the fragment is a
 * pure renderer and does not compute any of this itself.
 */
data class EntriesScreenState(
    val title: TitleState = TitleState.Loading,
    val items: ItemsState = ItemsState.Loading,
    val running: Boolean = false,
    /**
     * True only while a user-initiated pull-to-refresh is in flight. The
     * swipe-refresh spinner must reflect this, not [running], so that
     * background syncs triggered by [org.vestifeed.entries.EntriesViewModel.setRead]
     * or [org.vestifeed.entries.EntriesViewModel.setBookmarked] don't flash
     * the indicator.
     */
    val pullToRefreshInProgress: Boolean = false,
)

sealed class TitleState {
    data object Loading : TitleState()
    data class Res(@StringRes val resId: Int, val args: List<Any> = emptyList()) : TitleState()
    data class Custom(val title: String) : TitleState()
}

sealed class ItemsState {
    data object Loading : ItemsState()

    /**
     * Sync is in progress and we don't yet have any items to show. The
     * fragment renders a "first sync in progress" message.
     */
    data object InitialSync : ItemsState()

    data class Showing(val items: List<EntriesAdapter.Item>) : ItemsState()

    data class Empty(@StringRes val messageRes: Int, val args: List<Any> = emptyList()) : ItemsState()
}

/**
 * One-shot user-intent that the ViewModel asks the fragment to perform after
 * an item interaction. Delivered through a SharedFlow so the fragment can
 * react without owning the side-effecting IO itself.
 */
sealed class EntriesItemAction {
    data class OpenEntry(val entryId: String) : EntriesItemAction()
    data class OpenUnreadPager(
        val initialEntryId: String,
        val unreadIds: List<String>,
    ) : EntriesItemAction()
    data class OpenExternal(val href: String, val useBuiltInBrowser: Boolean) : EntriesItemAction()
    data object NoExternalLinks : EntriesItemAction()
    data class OpenImageExternal(val href: String, val useBuiltInBrowser: Boolean) : EntriesItemAction()
}