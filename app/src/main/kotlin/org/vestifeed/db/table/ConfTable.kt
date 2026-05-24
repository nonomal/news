package org.vestifeed.db.table

import androidx.sqlite.SQLiteConnection
import androidx.sqlite.SQLiteStatement
import androidx.sqlite.execSQL
import org.vestifeed.db.getTextOrNull
import kotlin.use

class ConfTable(private val conn: SQLiteConnection) {
    companion object {
        const val SCHEMA = """
            CREATE TABLE conf (
                backend TEXT,
                miniflux_url TEXT,
                miniflux_token TEXT,
                initial_sync_completed INTEGER NOT NULL,
                last_entries_sync_datetime TEXT NOT NULL,
                show_read_entries INTEGER NOT NULL,
                show_preview_images INTEGER NOT NULL,
                crop_preview_images INTEGER NOT NULL,
                mark_scrolled_entries_as_read INTEGER NOT NULL,
                sync_on_startup INTEGER NOT NULL,
                sync_in_background INTEGER NOT NULL,
                background_sync_interval_millis INTEGER NOT NULL,
                use_built_in_browser INTEGER NOT NULL,
                show_preview_text INTEGER NOT NULL,
                synced_on_startup INTEGER NOT NULL
            ) STRICT;
        """

        fun defaultConf(): Conf = Conf(
            backend = null,
            minifluxUrl = null,
            minifluxToken = null,
            initialSyncCompleted = false,
            lastEntriesSyncDatetime = "",
            showReadEntries = false,
            showPreviewImages = true,
            cropPreviewImages = true,
            markScrolledEntriesAsRead = false,
            syncOnStartup = true,
            syncInBackground = true,
            backgroundSyncIntervalMillis = 10800000L,
            useBuiltInBrowser = true,
            showPreviewText = true,
            syncedOnStartup = false,
        )
    }

    enum class Backend {
        Miniflux,
        Embedded,
    }

    data class Conf(
        val backend: Backend?,
        val minifluxUrl: String?,
        val minifluxToken: String?,
        val initialSyncCompleted: Boolean,
        val lastEntriesSyncDatetime: String,
        val showReadEntries: Boolean,
        val showPreviewImages: Boolean,
        val cropPreviewImages: Boolean,
        val markScrolledEntriesAsRead: Boolean,
        val syncOnStartup: Boolean,
        val syncInBackground: Boolean,
        val backgroundSyncIntervalMillis: Long,
        val useBuiltInBrowser: Boolean,
        val showPreviewText: Boolean,
        val syncedOnStartup: Boolean,
    )

    fun SQLiteStatement.toConf(): Conf = Conf(
        backend = getBackendOrNull(0),
        minifluxUrl = getTextOrNull(1),
        minifluxToken = getTextOrNull(2),
        initialSyncCompleted = getInt(3) == 1,
        lastEntriesSyncDatetime = getText(4),
        showReadEntries = getInt(5) == 1,
        showPreviewImages = getInt(6) == 1,
        cropPreviewImages = getInt(7) == 1,
        markScrolledEntriesAsRead = getInt(8) == 1,
        syncOnStartup = getInt(9) == 1,
        syncInBackground = getInt(10) == 1,
        backgroundSyncIntervalMillis = getLong(11),
        useBuiltInBrowser = getInt(12) == 1,
        showPreviewText = getInt(13) == 1,
        syncedOnStartup = getInt(14) == 1,
    )

    fun insert(conf: Conf) {
        conn.prepare(
            """
            INSERT OR REPLACE INTO conf (backend, miniflux_url, miniflux_token, initial_sync_completed, last_entries_sync_datetime, show_read_entries, show_preview_images, crop_preview_images, mark_scrolled_entries_as_read, sync_on_startup, sync_in_background, background_sync_interval_millis, use_built_in_browser, show_preview_text, synced_on_startup)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?);
            """
        ).use { stmt ->
            if (conf.backend == null) {
                stmt.bindNull(1)
            } else {
                stmt.bindText(1, conf.backend.name.lowercase())
            }
            if (conf.minifluxUrl == null) {
                stmt.bindNull(2)
            } else {
                stmt.bindText(2, conf.minifluxUrl)
            }
            if (conf.minifluxToken == null) {
                stmt.bindNull(3)
            } else {
                stmt.bindText(3, conf.minifluxToken)
            }
            stmt.bindInt(4, if (conf.initialSyncCompleted) 1 else 0)
            stmt.bindText(5, conf.lastEntriesSyncDatetime)
            stmt.bindInt(6, if (conf.showReadEntries) 1 else 0)
            stmt.bindInt(7, if (conf.showPreviewImages) 1 else 0)
            stmt.bindInt(8, if (conf.cropPreviewImages) 1 else 0)
            stmt.bindInt(9, if (conf.markScrolledEntriesAsRead) 1 else 0)
            stmt.bindInt(10, if (conf.syncOnStartup) 1 else 0)
            stmt.bindInt(11, if (conf.syncInBackground) 1 else 0)
            stmt.bindLong(12, conf.backgroundSyncIntervalMillis)
            stmt.bindInt(13, if (conf.useBuiltInBrowser) 1 else 0)
            stmt.bindInt(14, if (conf.showPreviewText) 1 else 0)
            stmt.bindInt(15, if (conf.syncedOnStartup) 1 else 0)
            stmt.step()
        }
    }

    fun select(): Conf {
        conn.prepare(
            """
            SELECT backend, miniflux_url, miniflux_token, initial_sync_completed, last_entries_sync_datetime, show_read_entries, show_preview_images, crop_preview_images, mark_scrolled_entries_as_read, sync_on_startup, sync_in_background, background_sync_interval_millis, use_built_in_browser, show_preview_text, synced_on_startup
            FROM conf
            """
        ).use { stmt ->
            return if (stmt.step()) {
                stmt.toConf()
            } else {
                defaultConf()
            }
        }
    }

    fun update(newConf: (Conf) -> Conf) {
        val oldConf = select()
        val updatedConf = newConf(oldConf)
        conn.execSQL("BEGIN TRANSACTION")
        try {
            delete()
            insert(updatedConf)
            conn.execSQL("COMMIT")
        } catch (e: Exception) {
            conn.execSQL("ROLLBACK")
            throw e
        }
    }

    fun delete() {
        conn.execSQL("DELETE FROM conf")
    }

    fun SQLiteStatement.getBackendOrNull(index: Int): Backend? =
        if (isNull(index)) null else Backend.entries.single { it.name.lowercase() == getText(index) }
}