package org.vestifeed.entries

import android.graphics.Rect
import android.view.View
import androidx.recyclerview.widget.RecyclerView

/**
 * Adds uniform horizontal/vertical padding around every card and an extra
 * bottom gap on the last card so the final card doesn't kiss the bottom of
 * the screen.
 */
class CardListAdapterDecoration(private val gapInPixels: Int) :
    RecyclerView.ItemDecoration() {

    override fun getItemOffsets(
        outRect: Rect,
        view: View,
        parent: RecyclerView,
        state: RecyclerView.State,
    ) {
        val position = parent.getChildAdapterPosition(view)

        val bottomGap = if (position == (parent.adapter?.itemCount ?: 0) - 1) {
            gapInPixels
        } else {
            0
        }

        outRect.set(gapInPixels, gapInPixels, gapInPixels, bottomGap)
    }
}