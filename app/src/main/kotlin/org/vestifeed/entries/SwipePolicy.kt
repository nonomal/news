package org.vestifeed.entries

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes

/**
 * Per-filter swipe behavior. A `null` arm disables that swipe direction entirely.
 */
data class SwipePolicy(
    val left: SwipeAction?,
    val right: SwipeAction?,
) {
    companion object {
        val NONE = SwipePolicy(left = null, right = null)
    }
}

/**
 * Description of what a single swipe direction does, together with the icon to
 * draw under the swiping finger and the snackbar copy shown after the swipe is
 * committed. [apply] runs immediately on swipe-complete; [undo] runs if the
 * user taps the "undo" action on the snackbar within the snackbar's display
 * window.
 */
data class SwipeAction(
    @DrawableRes val iconRes: Int,
    @StringRes val messageRes: Int,
    val apply: suspend EntriesViewModel.(entryId: String) -> Unit,
    val undo: suspend EntriesViewModel.(entryId: String) -> Unit,
)