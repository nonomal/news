package org.vestifeed.db.table

import androidx.sqlite.SQLiteConnection
import androidx.sqlite.SQLiteStatement
import androidx.sqlite.execSQL
import org.vestifeed.db.bindTextOrNull
import java.time.OffsetDateTime
import kotlin.use

class EntryTable(private val conn: SQLiteConnection) {
    companion object {
        const val SCHEMA = """
            CREATE TABLE entry (
                content_type TEXT,
                content_src TEXT,
                content_text TEXT,
                summary TEXT,
                id TEXT PRIMARY KEY NOT NULL,
                feed_id TEXT NOT NULL,
                title TEXT NOT NULL,
                published TEXT NOT NULL,
                updated TEXT NOT NULL,
                author_name TEXT NOT NULL,
                ext_read INTEGER NOT NULL,
                ext_read_synced INTEGER NOT NULL,
                ext_bookmarked INTEGER NOT NULL,
                ext_bookmarked_synced INTEGER NOT NULL,
                ext_comments_url TEXT NOT NULL,
                ext_og_image_checked INTEGER NOT NULL,
                ext_og_image_url TEXT NOT NULL,
                ext_og_image_width INTEGER NOT NULL,
                ext_og_image_height INTEGER NOT NULL
            ) STRICT;
        """
    }

    data class Entry(
        val contentType: String?,
        val contentSrc: String?,
        val contentText: String?,
        val summary: String?,
        val id: String,
        val feedId: String,
        val title: String,
        val published: OffsetDateTime,
        val updated: OffsetDateTime,
        val authorName: String,
        val extRead: Boolean,
        val extReadSynced: Boolean,
        val extBookmarked: Boolean,
        val extBookmarkedSynced: Boolean,
        val extCommentsUrl: String,
        val extOpenGraphImageChecked: Boolean,
        val extOpenGraphImageUrl: String,
        val extOpenGraphImageWidth: Int,
        val extOpenGraphImageHeight: Int,
    )

    private fun SQLiteStatement.toEntry(): Entry {
        return Entry(
            contentType = this.getTextOrNull(0),
            contentSrc = this.getTextOrNull(1),
            contentText = this.getTextOrNull(2),
            summary = this.getTextOrNull(3),
            id = this.getText(4),
            feedId = this.getText(5),
            title = this.getText(6),
            published = OffsetDateTime.parse(this.getText(7)),
            updated = OffsetDateTime.parse(this.getText(8)),
            authorName = this.getText(9),
            extRead = this.getInt(10) == 1,
            extReadSynced = this.getInt(11) == 1,
            extBookmarked = this.getInt(12) == 1,
            extBookmarkedSynced = this.getInt(13) == 1,
            extCommentsUrl = this.getText(14),
            extOpenGraphImageChecked = this.getInt(15) == 1,
            extOpenGraphImageUrl = this.getText(16),
            extOpenGraphImageWidth = this.getInt(17),
            extOpenGraphImageHeight = this.getInt(18)
        )
    }

    fun Entry.withoutContent(): EntryWithoutContent {
        return EntryWithoutContent(
            summary = summary,
            id = id,
            feedId = feedId,
            title = title,
            published = published,
            updated = updated,
            authorName = authorName,
            extRead = extRead,
            extReadSynced = extReadSynced,
            extBookmarked = extBookmarked,
            extBookmarkedSynced = extBookmarkedSynced,
            extCommentsUrl = extCommentsUrl,
            extOpenGraphImageChecked = extOpenGraphImageChecked,
            extOpenGraphImageUrl = extOpenGraphImageUrl,
            extOpenGraphImageWidth = extOpenGraphImageWidth,
            extOpenGraphImageHeight = extOpenGraphImageHeight,
        )
    }

    fun insertOrReplace(entries: List<Entry>) {
        conn.prepare(
            """
            INSERT OR REPLACE INTO
            entry (content_type, content_src, content_text, summary, id, feed_id, title, published, updated, author_name, ext_read, ext_read_synced, ext_bookmarked, ext_bookmarked_synced, ext_comments_url, ext_og_image_checked, ext_og_image_url, ext_og_image_width, ext_og_image_height)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?);
            """
        ).use { stmt ->
            entries.forEach { entry ->
                stmt.bindTextOrNull(1, entry.contentType)
                stmt.bindTextOrNull(2, entry.contentSrc)
                stmt.bindTextOrNull(3, entry.contentText)
                stmt.bindTextOrNull(4, entry.summary)
                stmt.bindText(5, entry.id)
                stmt.bindText(6, entry.feedId)
                stmt.bindText(7, entry.title)
                stmt.bindText(8, entry.published.toString())
                stmt.bindText(9, entry.updated.toString())
                stmt.bindText(10, entry.authorName)
                stmt.bindInt(11, if (entry.extRead) 1 else 0)
                stmt.bindInt(12, if (entry.extReadSynced) 1 else 0)
                stmt.bindInt(13, if (entry.extBookmarked) 1 else 0)
                stmt.bindInt(14, if (entry.extBookmarkedSynced) 1 else 0)
                stmt.bindText(15, entry.extCommentsUrl)
                stmt.bindInt(16, if (entry.extOpenGraphImageChecked) 1 else 0)
                stmt.bindText(17, entry.extOpenGraphImageUrl)
                stmt.bindInt(18, entry.extOpenGraphImageWidth)
                stmt.bindInt(19, entry.extOpenGraphImageHeight)
                stmt.step()
                stmt.reset()
            }
        }
    }

    data class ShortEntry(
        val published: OffsetDateTime,
        val title: String,
    )

    fun selectAllPublishedAndTitle(): List<ShortEntry> {
        conn.prepare(
            """
            SELECT published, title 
            FROM entry 
            ORDER BY published DESC;
            """
        ).use { stmt ->
            return buildList {
                while (stmt.step()) {
                    add(
                        ShortEntry(
                            published = OffsetDateTime.parse(stmt.getText(0)),
                            title = stmt.getText(1),
                        )
                    )
                }
            }

        }
    }

    fun selectById(entryId: String): Entry? {
        conn.prepare(
            """
            SELECT content_type, content_src, content_text, summary, id, feed_id, title, published, updated, author_name, ext_read, ext_read_synced, ext_bookmarked, ext_bookmarked_synced, ext_comments_url, ext_og_image_checked, ext_og_image_url, ext_og_image_width, ext_og_image_height
            FROM entry
            WHERE id = ?;
            """
        ).use { stmt ->
            stmt.bindText(1, entryId)
            return if (stmt.step()) stmt.toEntry() else null
        }
    }

    data class EntriesAdapterRow(
        val id: String,
        val feedId: String,
        val extBookmarked: Boolean,
        val extShowPreviewImages: Boolean,
        val extOpenGraphImageUrl: String,
        val extOpenGraphImageWidth: Int,
        val extOpenGraphImageHeight: Int,
        val title: String,
        val feedTitle: String,
        val published: OffsetDateTime,
        val summary: String,
        val extRead: Boolean,
        val extOpenEntriesInBrowser: Boolean,
    )

    fun selectByFeedId(feedId: String): List<EntriesAdapterRow> {
        conn.prepare(
            """
            SELECT e.id, e.feed_id, e.ext_bookmarked, e.ext_og_image_url,
                   e.ext_og_image_width, e.ext_og_image_height, e.title,
                   f.title as feed_title, f.ext_show_preview_images,
                   e.published, e.summary, e.ext_read, f.ext_open_entries_in_browser
            FROM entry e
            JOIN feed f ON f.id = e.feed_id
            WHERE e.feed_id = ?
            ORDER BY e.published DESC;
            """
        ).use { stmt ->
            stmt.bindText(1, feedId)
            return buildList {
                while (stmt.step()) {
                    add(statementToEntriesAdapterRow(stmt))
                }
            }
        }
    }

    fun selectUnread(): List<EntriesAdapterRow> {
        conn.prepare(
            """
            SELECT e.id, e.feed_id, e.ext_bookmarked, e.ext_og_image_url,
                   e.ext_og_image_width, e.ext_og_image_height, e.title,
                   f.title as feed_title, f.ext_show_preview_images,
                   e.published, e.summary, e.ext_read, f.ext_open_entries_in_browser
            FROM entry e
            JOIN feed f ON f.id = e.feed_id
            WHERE e.ext_read = 0 AND e.ext_bookmarked = 0
            ORDER BY e.published DESC;
            """
        ).use { stmt ->
            return buildList {
                while (stmt.step()) {
                    add(statementToEntriesAdapterRow(stmt))
                }
            }
        }
    }

    fun selectUnreadCount(): Int {
        conn.prepare(
            """
            SELECT COUNT(*)
            FROM entry e
            JOIN feed f ON f.id = e.feed_id
            WHERE e.ext_read = 0 AND e.ext_bookmarked = 0;
            """
        ).use { stmt ->
            return if (stmt.step()) stmt.getInt(0) else 0
        }
    }

    fun selectBookmarked(): List<EntriesAdapterRow> {
        conn.prepare(
            """
            SELECT e.id, e.feed_id, e.ext_bookmarked, e.ext_og_image_url,
                   e.ext_og_image_width, e.ext_og_image_height, e.title,
                   f.title as feed_title, f.ext_show_preview_images,
                   e.published, e.summary, e.ext_read, f.ext_open_entries_in_browser
            FROM entry e
            JOIN feed f ON f.id = e.feed_id
            WHERE e.ext_bookmarked = 1
            ORDER BY e.published DESC;
            """
        ).use { stmt ->
            return buildList {
                while (stmt.step()) {
                    add(statementToEntriesAdapterRow(stmt))
                }
            }
        }
    }

    fun selectBookmarkedCount(): Int {
        conn.prepare(
            """
            SELECT COUNT(*)
            FROM entry e
            JOIN feed f ON f.id = e.feed_id
            WHERE e.ext_bookmarked = 1;
            """
        ).use { stmt ->
            return if (stmt.step()) stmt.getInt(0) else 0
        }
    }

    fun selectCount(): Long {
        conn.prepare("SELECT COUNT(*) FROM entry;").use { stmt ->
            return if (stmt.step()) stmt.getLong(0) else 0L
        }
    }

    fun selectMaxId(): String? {
        conn.prepare("SELECT MAX(CAST(id AS INTEGER)) FROM entry;").use { stmt ->
            stmt.step()
            return stmt.getTextOrNull(0)
        }
    }

    fun selectMaxUpdated(): String? {
        conn.prepare("SELECT MAX(updated) FROM entry;").use { stmt ->
            stmt.step()
            return stmt.getTextOrNull(0)
        }
    }

    fun updateReadAndReadSynced(id: String, extRead: Boolean, extReadSynced: Boolean) {
        conn.prepare("UPDATE entry SET ext_read = ?, ext_read_synced = ? WHERE id = ?;")
            .use { stmt ->
                stmt.bindInt(1, if (extRead) 1 else 0)
                stmt.bindInt(2, if (extReadSynced) 1 else 0)
                stmt.bindText(3, id)
                stmt.step()
            }
    }

    fun updateBookmarkedAndBookmarkedSynced(
        id: String,
        extBookmarked: Boolean,
        extBookmarkedSynced: Boolean
    ) {
        conn.prepare("UPDATE entry SET ext_bookmarked = ?, ext_bookmarked_synced = ? WHERE id = ?;")
            .use { stmt ->
                stmt.bindInt(1, if (extBookmarked) 1 else 0)
                stmt.bindInt(2, if (extBookmarkedSynced) 1 else 0)
                stmt.bindText(3, id)
                stmt.step()
            }
    }

    data class EntryWithoutContent(
        val summary: String?,
        val id: String,
        val feedId: String,
        val title: String,
        val published: OffsetDateTime,
        val updated: OffsetDateTime,
        val authorName: String,
        val extRead: Boolean,
        val extReadSynced: Boolean,
        val extBookmarked: Boolean,
        val extBookmarkedSynced: Boolean,
        val extCommentsUrl: String,
        val extOpenGraphImageChecked: Boolean,
        val extOpenGraphImageUrl: String,
        val extOpenGraphImageWidth: Int,
        val extOpenGraphImageHeight: Int,
    )

    fun selectByReadSynced(extReadSynced: Boolean): List<EntryWithoutContent> {
        conn.prepare(
            """
            SELECT 
                summary,
                id,
                feed_id, 
                title, 
                published, 
                updated, 
                author_name,
                ext_read, 
                ext_read_synced,
                ext_bookmarked,
                ext_bookmarked_synced,
                ext_comments_url,
                ext_og_image_checked,
                ext_og_image_url,
                ext_og_image_width,
                ext_og_image_height
            FROM entry
            WHERE ext_read_synced = ?
            ORDER BY published DESC;
            """
        ).use { stmt ->
            stmt.bindInt(1, if (extReadSynced) 1 else 0)
            return buildList {
                while (stmt.step()) {
                    add(statementToEntryWithoutContent(stmt))
                }
            }
        }
    }

    fun selectByBookmarkedSynced(extBookmarkedSynced: Boolean): List<EntryWithoutContent> {
        conn.prepare(
            """
            SELECT
                summary,
                id,
                feed_id,
                title,
                published,
                updated,
                author_name,
                ext_read,
                ext_read_synced,
                ext_bookmarked,
                ext_bookmarked_synced,
                ext_comments_url,
                ext_og_image_checked,
                ext_og_image_url,
                ext_og_image_width,
                ext_og_image_height
            FROM entry
            WHERE ext_bookmarked_synced = ?
            ORDER BY published DESC;
            """
        ).use { stmt ->
            stmt.bindInt(1, if (extBookmarkedSynced) 1 else 0)
            return buildList {
                while (stmt.step()) {
                    add(statementToEntryWithoutContent(stmt))
                }
            }
        }
    }

    fun updateOgImageChecked(extOgImageChecked: Boolean, id: String) {
        conn.prepare("UPDATE entry SET ext_og_image_checked = ? WHERE id = ?;").use { stmt ->
            stmt.bindInt(1, if (extOgImageChecked) 1 else 0)
            stmt.bindText(2, id)
            stmt.step()
        }
    }

    fun updateOgImage(
        extOgImageUrl: String,
        extOgImageWidth: Long,
        extOgImageHeight: Long,
        id: String
    ) {
        conn.prepare("UPDATE entry SET ext_og_image_url = ?, ext_og_image_width = ?, ext_og_image_height = ?, ext_og_image_checked = 1 WHERE id = ?;")
            .use { stmt ->
                stmt.bindText(1, extOgImageUrl)
                stmt.bindLong(2, extOgImageWidth)
                stmt.bindLong(3, extOgImageHeight)
                stmt.bindText(4, id)
                stmt.step()
            }
    }

    fun updateReadSynced(extReadSynced: Boolean, id: String) {
        conn.prepare("UPDATE entry SET ext_read_synced = ? WHERE id = ?;").use { stmt ->
            stmt.bindInt(1, if (extReadSynced) 1 else 0)
            stmt.bindText(2, id)
            stmt.step()
        }
    }

    fun updateBookmarkedSynced(extBookmarkedSynced: Boolean, id: String) {
        conn.prepare("UPDATE entry SET ext_bookmarked_synced = ? WHERE id = ?;").use { stmt ->
            stmt.bindInt(1, if (extBookmarkedSynced) 1 else 0)
            stmt.bindText(2, id)
            stmt.step()
        }
    }

    fun deleteByFeedId(feedId: String) {
        conn.prepare("DELETE FROM entry WHERE feed_id = ?").use { stmt ->
            stmt.bindText(1, feedId)
            stmt.step()
        }
    }

    fun deleteAll() {
        conn.execSQL("DELETE FROM entry")
    }

    fun selectByOgImageChecked(extOgImageChecked: Boolean, limit: Long): List<EntryWithoutContent> {
        val res = mutableListOf<EntryWithoutContent>()
        conn.prepare(
            """
            SELECT summary, id, feed_id, title, published, updated, author_name,
                   ext_read, ext_read_synced, ext_bookmarked, ext_bookmarked_synced,
                   ext_comments_url, ext_og_image_checked, ext_og_image_url,
                   ext_og_image_width, ext_og_image_height
            FROM entry WHERE ext_og_image_checked = ? ORDER BY published DESC LIMIT ?
        """
        ).use { stmt ->
            stmt.bindInt(1, if (extOgImageChecked) 1 else 0)
            stmt.bindLong(2, limit)
            return buildList {
                while (stmt.step()) {
                    add(statementToEntryWithoutContent(stmt))
                }
            }
        }
    }

    private fun getColumnIndex(stmt: SQLiteStatement, name: String): Int {
        return stmt.getColumnNames().indexOf(name)
    }

    private fun SQLiteStatement.getTextOrNull(index: Int): String? {
        return if (isNull(index)) null else getText(index)
    }

    private fun statementToEntriesAdapterRow(stmt: SQLiteStatement): EntriesAdapterRow {
        return EntriesAdapterRow(
            id = stmt.getTextOrNull(getColumnIndex(stmt, "id")) ?: "",
            feedId = stmt.getTextOrNull(getColumnIndex(stmt, "feed_id")) ?: "",
            extBookmarked = stmt.getInt(getColumnIndex(stmt, "ext_bookmarked")) == 1,
            extShowPreviewImages = stmt.getInt(
                getColumnIndex(
                    stmt,
                    "ext_show_preview_images"
                )
            ) == 1,
            extOpenGraphImageUrl = stmt.getTextOrNull(getColumnIndex(stmt, "ext_og_image_url"))
                ?: "",
            extOpenGraphImageWidth = stmt.getInt(getColumnIndex(stmt, "ext_og_image_width")),
            extOpenGraphImageHeight = stmt.getInt(getColumnIndex(stmt, "ext_og_image_height")),
            title = stmt.getTextOrNull(getColumnIndex(stmt, "title")) ?: "",
            feedTitle = stmt.getTextOrNull(getColumnIndex(stmt, "feed_title")) ?: "",
            published = runCatching {
                OffsetDateTime.parse(
                    stmt.getTextOrNull(
                        getColumnIndex(
                            stmt,
                            "published"
                        )
                    )
                )
            }.getOrDefault(OffsetDateTime.now()),
            summary = stmt.getTextOrNull(getColumnIndex(stmt, "summary")) ?: "",
            extRead = stmt.getInt(getColumnIndex(stmt, "ext_read")) == 1,
            extOpenEntriesInBrowser = stmt.getInt(
                getColumnIndex(
                    stmt,
                    "ext_open_entries_in_browser"
                )
            ) == 1,
        )
    }

    private fun statementToSelectByQuery(stmt: SQLiteStatement): SelectByQuery {
        return SelectByQuery(
            id = stmt.getTextOrNull(0) ?: "",
            extShowPreviewImages = stmt.getInt(1) == 1,
            extOpenGraphImageUrl = stmt.getTextOrNull(2) ?: "",
            extOpenGraphImageWidth = stmt.getInt(3),
            extOpenGraphImageHeight = stmt.getInt(4),
            title = stmt.getTextOrNull(5) ?: "",
            feedTitle = stmt.getTextOrNull(6) ?: "",
            published = runCatching { OffsetDateTime.parse(stmt.getTextOrNull(7)) }.getOrDefault(
                OffsetDateTime.now()
            ),
            summary = stmt.getTextOrNull(8) ?: "",
            extRead = stmt.getInt(9) == 1,
            extOpenEntriesInBrowser = stmt.getInt(10) == 1,
        )
    }

    private fun statementToEntryWithoutContent(stmt: SQLiteStatement): EntryWithoutContent {
        return EntryWithoutContent(
            summary = stmt.getTextOrNull(0),
            id = stmt.getTextOrNull(1) ?: "",
            feedId = stmt.getTextOrNull(2) ?: "",
            title = stmt.getTextOrNull(3) ?: "",
            published = runCatching { OffsetDateTime.parse(stmt.getTextOrNull(4)) }.getOrDefault(
                OffsetDateTime.now()
            ),
            updated = runCatching { OffsetDateTime.parse(stmt.getTextOrNull(5)) }.getOrDefault(
                OffsetDateTime.now()
            ),
            authorName = stmt.getTextOrNull(6) ?: "",
            extRead = stmt.getInt(7) == 1,
            extReadSynced = stmt.getInt(8) == 1,
            extBookmarked = stmt.getInt(9) == 1,
            extBookmarkedSynced = stmt.getInt(10) == 1,
            extCommentsUrl = stmt.getTextOrNull(11) ?: "",
            extOpenGraphImageChecked = stmt.getInt(12) == 1,
            extOpenGraphImageUrl = stmt.getTextOrNull(13) ?: "",
            extOpenGraphImageWidth = stmt.getInt(14),
            extOpenGraphImageHeight = stmt.getInt(15)
        )
    }

    data class SelectByQuery(
        val id: String,
        val extShowPreviewImages: Boolean,
        val extOpenGraphImageUrl: String,
        val extOpenGraphImageWidth: Int,
        val extOpenGraphImageHeight: Int,
        val title: String,
        val feedTitle: String,
        val published: OffsetDateTime,
        val summary: String?,
        val extRead: Boolean,
        val extOpenEntriesInBrowser: Boolean,
    )

    fun selectByQuery(query: String): List<SelectByQuery> {
        val searchQuery = "%$query%"
        val sql = """
            SELECT e.id, f.ext_show_preview_images, e.ext_og_image_url, e.ext_og_image_width,
                   e.ext_og_image_height, e.title, f.title as feed_title, e.published,
                   e.summary, e.ext_read, f.ext_open_entries_in_browser
            FROM entry e
            JOIN feed f ON f.id = e.feed_id
            WHERE e.title LIKE ? OR e.summary LIKE ? OR e.content_text LIKE ?
            LIMIT 500
        """.trimIndent()

        return conn.prepare(sql).use { stmt ->
            stmt.bindText(1, searchQuery)
            stmt.bindText(2, searchQuery)
            stmt.bindText(3, searchQuery)
            buildList {
                while (stmt.step()) {
                    add(
                        SelectByQuery(
                            id = stmt.getText(0),
                            extShowPreviewImages = stmt.getInt(1) == 1,
                            extOpenGraphImageUrl = stmt.getText(2),
                            extOpenGraphImageWidth = stmt.getInt(3),
                            extOpenGraphImageHeight = stmt.getInt(4),
                            title = stmt.getText(5),
                            feedTitle = stmt.getText(6),
                            published = OffsetDateTime.parse(stmt.getText(7)),
                            summary = stmt.getText(8),
                            extRead = stmt.getInt(9) == 1,
                            extOpenEntriesInBrowser = stmt.getInt(10) == 1,
                        )
                    )
                }
            }
        }
    }
}