package org.vestifeed.entries

import android.os.Parcel
import android.os.Parcelable
import androidx.annotation.StringRes
import androidx.core.os.bundleOf
import org.vestifeed.R
import org.vestifeed.db.Database
import org.vestifeed.db.table.EntryTable

/**
 * Discriminator + per-variant policy for the entries screen. Each variant
 * declares how to load its rows, what title to show in the toolbar, whether
 * swipe-to-refresh is enabled, which swipe actions appear and what empty
 * message to display. Adding a new entries tab (e.g. "Today", "By Tag") is
 * now a matter of adding a new variant rather than threading another `when`
 * branch through the fragment.
 */
sealed class EntriesFilter : Parcelable {

    abstract val swipeRefreshEnabled: Boolean
    abstract val swipePolicy: SwipePolicy

    /** Load the rows that should appear in the list for this filter. */
    abstract suspend fun loadEntries(db: Database): List<EntryTable.EntriesAdapterRow>

    /** Toolbar title for this filter. */
    abstract suspend fun resolveTitle(db: Database): TitleFormat

    /** Resource id of the empty-list message, given the current feed count. */
    @StringRes
    abstract fun emptyMessageRes(feedCount: Int): Int

    /** Either a parameterized resource or a literal string for the toolbar. */
    sealed class TitleFormat {
        data class Res(@StringRes val resId: Int, val args: List<Any> = emptyList()) : TitleFormat()
        data class Custom(val title: String) : TitleFormat()
    }

    object Unread : EntriesFilter() {
        override val swipeRefreshEnabled = true
        override val swipePolicy = SwipePolicy(
            left = SwipeAction(
                iconRes = R.drawable.ic_baseline_visibility_24,
                messageRes = R.string.marked_as_read,
                apply = { setRead(it, read = true) },
                undo = { setRead(it, read = false) },
            ),
            right = SwipeAction(
                iconRes = R.drawable.ic_baseline_bookmark_add_24,
                messageRes = R.string.bookmarked,
                apply = { setBookmarked(it, bookmarked = true) },
                undo = { setBookmarked(it, bookmarked = false) },
            ),
        )

        override suspend fun loadEntries(db: Database): List<EntryTable.EntriesAdapterRow> {
            return db.entry.selectUnread()
        }

        override suspend fun resolveTitle(db: Database): TitleFormat {
            return TitleFormat.Res(
                resId = R.string.unread_n,
                args = listOf(db.entry.selectUnreadCount()),
            )
        }

        override fun emptyMessageRes(feedCount: Int): Int {
            return if (feedCount == 0) R.string.you_have_no_feeds
            else R.string.news_list_is_empty
        }

        override fun describeContents(): Int = 0

        override fun writeToParcel(parcel: Parcel, flags: Int) {
            parcel.writeInt(0)
        }
    }

    object Bookmarked : EntriesFilter() {
        override val swipeRefreshEnabled = false
        override val swipePolicy = SwipePolicy(
            left = SwipeAction(
                iconRes = R.drawable.ic_baseline_bookmark_remove_24,
                messageRes = R.string.removed_from_bookmarks,
                apply = { setBookmarked(it, bookmarked = false) },
                undo = { setBookmarked(it, bookmarked = true) },
            ),
            right = SwipeAction(
                iconRes = R.drawable.ic_baseline_bookmark_remove_24,
                messageRes = R.string.removed_from_bookmarks,
                apply = { setBookmarked(it, bookmarked = false) },
                undo = { setBookmarked(it, bookmarked = true) },
            ),
        )

        override suspend fun loadEntries(db: Database): List<EntryTable.EntriesAdapterRow> {
            return db.entry.selectBookmarked()
        }

        override suspend fun resolveTitle(db: Database): TitleFormat {
            return TitleFormat.Res(
                resId = R.string.bookmarks_n,
                args = listOf(db.entry.selectBookmarkedCount()),
            )
        }

        override fun emptyMessageRes(feedCount: Int): Int = R.string.you_have_no_bookmarks

        override fun describeContents(): Int = 0

        override fun writeToParcel(parcel: Parcel, flags: Int) {
            parcel.writeInt(1)
        }
    }

    data class BelongToFeed(val feedId: String) : EntriesFilter() {
        override val swipeRefreshEnabled = true
        override val swipePolicy = SwipePolicy(
            left = SwipeAction(
                iconRes = R.drawable.ic_baseline_visibility_24,
                messageRes = R.string.marked_as_read,
                apply = { setRead(it, read = true) },
                undo = { setRead(it, read = false) },
            ),
            right = SwipeAction(
                iconRes = R.drawable.ic_baseline_bookmark_add_24,
                messageRes = R.string.bookmarked,
                apply = { setBookmarked(it, bookmarked = true) },
                undo = { setBookmarked(it, bookmarked = false) },
            ),
        )

        override suspend fun loadEntries(db: Database): List<EntryTable.EntriesAdapterRow> {
            return db.entry.selectByFeedId(feedId).filterNot { it.extRead }
        }

        override suspend fun resolveTitle(db: Database): TitleFormat {
            val feed = db.feed.selectById(feedId)
            return TitleFormat.Custom(feed?.title ?: feedId)
        }

        override fun emptyMessageRes(feedCount: Int): Int = R.string.news_list_is_empty

        override fun describeContents(): Int = 0

        override fun writeToParcel(parcel: Parcel, flags: Int) {
            parcel.writeInt(2)
            parcel.writeString(feedId)
        }
    }

    /**
     * Filter for the "Tags" tab: shows unread entries from every feed that
     * has been tagged with [tagId]. The toolbar title is the tag's display
     * name. Empty list surfaces a tag-specific empty message.
     */
    data class BelongToTag(val tagId: String) : EntriesFilter() {
        override val swipeRefreshEnabled = true
        override val swipePolicy = SwipePolicy(
            left = SwipeAction(
                iconRes = R.drawable.ic_baseline_visibility_24,
                messageRes = R.string.marked_as_read,
                apply = { setRead(it, read = true) },
                undo = { setRead(it, read = false) },
            ),
            right = SwipeAction(
                iconRes = R.drawable.ic_baseline_bookmark_add_24,
                messageRes = R.string.bookmarked,
                apply = { setBookmarked(it, bookmarked = true) },
                undo = { setBookmarked(it, bookmarked = false) },
            ),
        )

        override suspend fun loadEntries(db: Database): List<EntryTable.EntriesAdapterRow> {
            val feedIds = db.feedTag.selectFeedIdsByTagId(tagId)
            return db.entry.selectUnreadByFeedIds(feedIds)
        }

        override suspend fun resolveTitle(db: Database): TitleFormat {
            val tag = db.tag.selectById(tagId)
            return TitleFormat.Custom(tag?.name ?: tagId)
        }

        override fun emptyMessageRes(feedCount: Int): Int = R.string.tag_has_no_unread_entries

        override fun describeContents(): Int = 0

        override fun writeToParcel(parcel: Parcel, flags: Int) {
            parcel.writeInt(3)
            parcel.writeString(tagId)
        }
    }

    companion object {
        const val ARG_FILTER = "filter"

        @JvmField
        val CREATOR: Parcelable.Creator<EntriesFilter> = object : Parcelable.Creator<EntriesFilter> {
            override fun createFromParcel(parcel: Parcel): EntriesFilter {
                return when (parcel.readInt()) {
                    0 -> Unread
                    1 -> Bookmarked
                    2 -> BelongToFeed(parcel.readString()!!)
                    3 -> BelongToTag(parcel.readString()!!)
                    else -> throw IllegalArgumentException("Unknown EntriesFilter type")
                }
            }

            override fun newArray(size: Int): Array<EntriesFilter?> {
                return arrayOfNulls(size)
            }
        }
    }
}

fun EntriesFilter.toBundle() = bundleOf(EntriesFilter.ARG_FILTER to this)

fun android.os.Bundle.toEntriesFilter(): EntriesFilter? =
    getParcelable(EntriesFilter.ARG_FILTER, EntriesFilter::class.java)