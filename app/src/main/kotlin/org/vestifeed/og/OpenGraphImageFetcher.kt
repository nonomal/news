package org.vestifeed.og

import coil3.PlatformContext
import coil3.imageLoader
import coil3.request.ErrorResult
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup
import org.vestifeed.db.Database
import org.vestifeed.db.table.EntryTable
import org.vestifeed.db.table.LogTable
import org.vestifeed.http.await
import org.vestifeed.parser.AtomLinkRel
import java.util.concurrent.TimeUnit
import kotlin.time.Duration.Companion.seconds

class OpenGraphImageFetcher(
    private val db: Database,
    private val imageContext: PlatformContext,
) {
    val lastDownload = MutableStateFlow<EntryTable.EntryWithoutContent?>(null)

    private val httpClient = OkHttpClient.Builder()
        .callTimeout(10, TimeUnit.SECONDS)
        .build()

    suspend fun fetchAndWatch() {
        while (true) {
            val uncheckedEntries = withContext(Dispatchers.IO) {
                db.entry.selectByOgImageChecked(
                    extOgImageChecked = false,
                    limit = 1,
                )
            }

            if (uncheckedEntries.isEmpty()) {
                delay(1.seconds)
            } else {
                if (fetchEntryImages(uncheckedEntries).isNotEmpty()) {
                    lastDownload.update { uncheckedEntries.first() }
                }
            }
        }
    }

    private suspend fun fetchEntryImages(entries: List<EntryTable.EntryWithoutContent>): List<EntryTable.EntryWithoutContent> {
        if (entries.isEmpty()) {
            return emptyList()
        }

        val successfulEntries = mutableListOf<EntryTable.EntryWithoutContent>()

        for (entry in entries) {
            if (fetchEntryImage(entry)) {
                successfulEntries += entry
            }
        }

        return successfulEntries
    }

    private suspend fun fetchEntryImage(entry: EntryTable.EntryWithoutContent): Boolean {
        withContext(Dispatchers.IO) {
            db.log.insert(
                LogTable.InsertArgs(
                    level = "debug",
                    tag = "OpenGraphImageFetcher",
                    message = "Trying to fetch an image for entry ${entry.id} (${entry.title})",
                )
            )
        }
        val links = withContext(Dispatchers.IO) {
            db.link.selectByEntryId(entry.id)
        }
        val htmlLink =
            links.firstOrNull { it.rel is AtomLinkRel.Alternate && it.type == "text/html" }
                ?: links.firstOrNull { it.rel is AtomLinkRel.Alternate }
        if (htmlLink == null) {
            withContext(Dispatchers.IO) {
                db.entry.updateOgImageChecked(true, entry.id)
            }
            return false
        }

        val htmlLinkResponse = try {
            httpClient.newCall(Request.Builder().url(htmlLink.href).build()).await()
        } catch (_: Throwable) {
            withContext(Dispatchers.IO) {
                db.entry.updateOgImageChecked(true, entry.id)
            }
            return false
        }

        if (!htmlLinkResponse.isSuccessful) {
            withContext(Dispatchers.IO) {
                db.entry.updateOgImageChecked(true, entry.id)
            }
            return false
        }

        val html = try {
            htmlLinkResponse.body.string()
        } catch (_: Throwable) {
            withContext(Dispatchers.IO) {
                db.entry.updateOgImageChecked(true, entry.id)
            }
            return false
        }

        val metas = Jsoup.parse(html).select("meta[property=\"og:image\"]")
        val imageUrl = metas.firstOrNull()?.attr("content") ?: ""

        if (imageUrl.isBlank()) {
            withContext(Dispatchers.IO) {
                db.entry.updateOgImageChecked(true, entry.id)
            }
            return false
        }

        val imageRequest = ImageRequest.Builder(imageContext)
            .data(imageUrl)
            .size(800)
            .build()

        val bitmap = when (val imageResult = imageContext.imageLoader.execute(imageRequest)) {
            is SuccessResult -> {
                imageResult.image.toBitmap()
            }

            is ErrorResult -> {
                withContext(Dispatchers.IO) {
                    db.entry.updateOgImageChecked(true, entry.id)
                }
                return false
            }
        }

        db.entry.updateOgImage(
            extOgImageUrl = imageUrl,
            extOgImageWidth = bitmap.width.toLong(),
            extOgImageHeight = bitmap.height.toLong(),
            id = entry.id,
        )

        return true
    }
}