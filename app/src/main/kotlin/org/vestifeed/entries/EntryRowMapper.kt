package org.vestifeed.entries

import android.content.res.Resources
import org.vestifeed.db.table.ConfTable
import org.vestifeed.db.table.EntryTable
import java.time.OffsetDateTime

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

    /**
     * @param now the reference point used by [EntryTimeFormatter] to decide
     *   between "just now", relative and strict formatting. Callers should
     *   capture a single value per list build so all rows in one render see
     *   the same clock reading.
     */
    fun toItem(
        row: EntryRowMappable,
        conf: ConfTable.Conf,
        now: OffsetDateTime,
        resources: Resources,
    ): EntriesAdapter.Item {
        return EntriesAdapter.Item(
            id = row.id,
            showImage = row.extShowPreviewImages || conf.showPreviewImages,
            cropImage = conf.cropPreviewImages,
            imageUrl = row.extOpenGraphImageUrl,
            imageWidth = row.extOpenGraphImageWidth,
            imageHeight = row.extOpenGraphImageHeight,
            title = row.title,
            subtitle = "${row.feedTitle} · ${EntryTimeFormatter.format(now, row.published, resources)}",
            summary = row.summary ?: "",
            read = row.extRead,
            openInBrowser = row.extOpenEntriesInBrowser,
            useBuiltInBrowser = conf.useBuiltInBrowser,
        )
    }
}