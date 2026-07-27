package org.vestifeed.entry

import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.PixelFormat
import android.graphics.drawable.Drawable
import android.os.Handler
import android.os.Looper
import android.text.SpannableStringBuilder
import android.text.style.ImageSpan
import android.widget.TextView
import androidx.core.graphics.drawable.toDrawable
import androidx.core.graphics.scale
import coil3.Image
import coil3.target.Target
import coil3.toBitmap

class TextViewImage(
    private val textView: TextView,
) : Drawable(), Target {

    private var drawable: Drawable? = null

    override fun draw(canvas: Canvas) {
        drawable?.draw(canvas)
    }

    override fun setAlpha(alpha: Int) {}

    override fun setColorFilter(colorFilter: ColorFilter?) {}

    @Deprecated(
        message = "Deprecated in Java",
        replaceWith = ReplaceWith("PixelFormat.OPAQUE", "android.graphics.PixelFormat"),
    )
    override fun getOpacity() = PixelFormat.OPAQUE

    override fun onStart(placeholder: Image?) {

    }

    override fun onError(error: Image?) {

    }

    override fun onSuccess(result: Image) {
        val unprocessedBitmap = result.toBitmap()
        val scaleToFullWidth = unprocessedBitmap.width >= textView.width / 5

        val bitmap = if (scaleToFullWidth) {
            val scaleFactor = textView.width.toFloat() / unprocessedBitmap.width.toFloat()
            unprocessedBitmap.scale(
                width = textView.width,
                height = (unprocessedBitmap.height * scaleFactor).toInt(),
            )
        } else {
            unprocessedBitmap
        }

        val verticalCutoff =
            (bitmap.height * textView.lineSpacingMultiplier - bitmap.height) / textView.lineSpacingMultiplier

        setBounds(0, 0, bitmap.width, (bitmap.height / textView.lineSpacingMultiplier).toInt())

        this.drawable = bitmap.toDrawable(textView.context.resources).apply {
            setBounds(
                0,
                -verticalCutoff.toInt(),
                bitmap.width,
                -verticalCutoff.toInt() + bitmap.height,
            )
        }

        Handler(Looper.getMainLooper()).post {
            val text = SpannableStringBuilder(textView.text)
            val spans = text.getSpans(0, text.length - 1, Any::class.java)

            spans.forEach {
                when (it) {
                    is ImageSpan -> {
                        val spanEnd = text.getSpanEnd(it)

                        if (scaleToFullWidth && spanEnd + 2 <= text.length - 1) {
                            if (text[spanEnd] != '\n' && text[spanEnd + 1] != '\n') {
                                text.insert(spanEnd, "\n\n")

                                if (text[spanEnd + 2] == ' ') {
                                    text.delete(spanEnd + 2, spanEnd + 3)
                                }
                            }

                            if (text[spanEnd] == '\n' && text[spanEnd + 1] != '\n') {
                                text.insert(spanEnd, "\n")
                            }
                        }
                    }
                }
            }

            textView.text = text
        }
    }
}
