package org.vestifeed.entry

import android.graphics.drawable.Drawable
import android.text.Html
import android.widget.TextView
import androidx.core.view.doOnLayout
import androidx.lifecycle.LifecycleCoroutineScope
import coil3.imageLoader
import coil3.request.ImageRequest
import coil3.size.Dimension
import coil3.size.Size
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

class TextViewImageGetter(
    private val textView: TextView,
    private val scope: LifecycleCoroutineScope,
    private val baseUrl: HttpUrl?,
) : Html.ImageGetter {

    override fun getDrawable(source: String): Drawable {
        val drawable = TextViewImage(textView)
        enqueue(source, drawable)
        return drawable
    }

    private fun enqueue(source: String, drawable: TextViewImage) {
        val width = textView.width

        if (width == 0) {
            textView.doOnLayout { enqueue(source, drawable) }
            return
        }

        val url = if (source.startsWith("http")) {
            source.toHttpUrlOrNull()
        } else if (baseUrl == null) {
            null
        } else {
            "${baseUrl.scheme}://${baseUrl.host}/$source".toHttpUrlOrNull()
        } ?: return

        scope.launch {
            runCatching {
                withContext(Dispatchers.Main) {
                    val request = ImageRequest.Builder(textView.context)
                        .data(url.toString())
                        .size(Size(width, Dimension.Undefined))
                        .target(drawable)
                        .build()
                    textView.context.imageLoader.enqueue(request)
                }
            }
        }
    }
}

