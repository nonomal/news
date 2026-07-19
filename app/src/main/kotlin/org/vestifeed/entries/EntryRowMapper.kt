package org.vestifeed.entries

import org.vestifeed.db.table.ConfTable
import org.vestifeed.db.table.EntryTable
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/**
 * Anything that can be turned into an [EntriesAdapter.Item]. Both
 * [EntryTable.EntriesAdapterRow] (used by the entries screen) and
 * [EntryTable.SelectByQuery] (used by the search screen) implement this so
 * that the display logic lives in one place.
 */
interface EntryRowMappable {
    val id: String
    val extShowPreviewImages: Boolean
    val extOpenGraphImageUrl: String
    val extOpenGraphImageWidth: Int
    val extOpenGraphImageHeight: Int
    val title: String
    val feedTitle: String
    val published: OffsetDateTime
    val summary: String?
    val extRead: Boolean
    val extOpenEntriesInBrowser: Boolean
}

object EntryRowMapper {

    fun toItem(row: EntryRowMappable, conf: ConfTable.Conf): EntriesAdapter.Item {
        return EntriesAdapter.Item(
            id = row.id,
            showImage = row.extShowPreviewImages || conf.showPreviewImages,
            cropImage = conf.cropPreviewImages,
            imageUrl = row.extOpenGraphImageUrl,
            imageWidth = row.extOpenGraphImageWidth,
            imageHeight = row.extOpenGraphImageHeight,
            title = row.title,
            subtitle = "${row.feedTitle} · ${DATE_TIME_FORMAT.format(row.published)}",
            summary = row.summary ?: "",
            read = row.extRead,
            openInBrowser = row.extOpenEntriesInBrowser,
            useBuiltInBrowser = conf.useBuiltInBrowser,
        )
    }

    private val DATE_TIME_FORMAT: DateTimeFormatter = DateTimeFormatter.ofLocalizedDateTime(
        FormatStyle.MEDIUM,
        FormatStyle.SHORT,
    )
}