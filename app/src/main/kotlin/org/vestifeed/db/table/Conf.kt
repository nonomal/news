package org.vestifeed.db.table

import androidx.sqlite.SQLiteConnection
import androidx.sqlite.SQLiteStatement
import androidx.sqlite.execSQL
import kotlin.use

class ConfTable(private val conn: SQLiteConnection) {
    companion object {
        const val SCHEMA = """
            CREATE TABLE conf (
                backend TEXT NOT NULL,
                miniflux_server_url TEXT NOT NULL,
                miniflux_server_trust_self_signed_certs INTEGER NOT NULL,
                miniflux_server_token TEXT NOT NULL,
                initial_sync_completed INTEGER NOT NULL,
                last_entries_sync_datetime TEXT NOT NULL,
                show_read_entries INTEGER NOT NULL,
                sort_order TEXT NOT NULL,
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

        const val BACKEND_STANDALONE = "standalone"
        const val BACKEND_MINIFLUX = "miniflux"

        const val SORT_ORDER_ASCENDING = "ascending"
        const val SORT_ORDER_DESCENDING = "descending"

        fun confDefault(): Conf = Conf(
            backend = ConfDefaults.backend,
            minifluxServerUrl = ConfDefaults.minifluxServerUrl,
            minifluxServerTrustSelfSignedCerts = ConfDefaults.minifluxServerTrustSelfSignedCerts,
            minifluxServerToken = ConfDefaults.minifluxServerToken,
            initialSyncCompleted = ConfDefaults.initialSyncCompleted,
            lastEntriesSyncDatetime = ConfDefaults.lastEntriesSyncDatetime,
            showReadEntries = ConfDefaults.showReadEntries,
            sortOrder = ConfDefaults.sortOrder,
            showPreviewImages = ConfDefaults.showPreviewImages,
            cropPreviewImages = ConfDefaults.cropPreviewImages,
            markScrolledEntriesAsRead = ConfDefaults.markScrolledEntriesAsRead,
            syncOnStartup = ConfDefaults.syncOnStartup,
            syncInBackground = ConfDefaults.syncInBackground,
            backgroundSyncIntervalMillis = ConfDefaults.backgroundSyncIntervalMillis,
            useBuiltInBrowser = ConfDefaults.useBuiltInBrowser,
            showPreviewText = ConfDefaults.showPreviewText,
            syncedOnStartup = ConfDefaults.syncedOnStartup,
        )
    }

    data class Conf(
        val backend: String,
        val minifluxServerUrl: String,
        val minifluxServerTrustSelfSignedCerts: Boolean,
        val minifluxServerToken: String,
        val initialSyncCompleted: Boolean,
        val lastEntriesSyncDatetime: String,
        val showReadEntries: Boolean,
        val sortOrder: String,
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

    object ConfDefaults {
        val backend = ""
        val minifluxServerUrl = ""
        val minifluxServerTrustSelfSignedCerts = false
        val minifluxServerToken = ""
        val initialSyncCompleted = false
        val lastEntriesSyncDatetime = ""
        val showReadEntries = false
        val sortOrder = SORT_ORDER_DESCENDING
        val showPreviewImages = true
        val cropPreviewImages = true
        val markScrolledEntriesAsRead = false
        val syncOnStartup = true
        val syncInBackground = true
        val backgroundSyncIntervalMillis = 10800000L
        val useBuiltInBrowser = true
        val showPreviewText = true
        val syncedOnStartup = false
    }

    fun SQLiteStatement.toConf(): Conf = Conf(
        backend = getText(0),
        minifluxServerUrl = getText(1),
        minifluxServerTrustSelfSignedCerts = getInt(2) == 1,
        minifluxServerToken = getText(3),
        initialSyncCompleted = getInt(4) == 1,
        lastEntriesSyncDatetime = getText(5),
        showReadEntries = getInt(6) == 1,
        sortOrder = getText(7),
        showPreviewImages = getInt(8) == 1,
        cropPreviewImages = getInt(9) == 1,
        markScrolledEntriesAsRead = getInt(10) == 1,
        syncOnStartup = getInt(11) == 1,
        syncInBackground = getInt(12) == 1,
        backgroundSyncIntervalMillis = getLong(13),
        useBuiltInBrowser = getInt(14) == 1,
        showPreviewText = getInt(15) == 1,
        syncedOnStartup = getInt(16) == 1,
    )

    fun insert(conf: Conf) {
        conn.prepare(
            """
            INSERT OR REPLACE INTO conf (backend,miniflux_server_url, miniflux_server_trust_self_signed_certs, miniflux_server_token, initial_sync_completed, last_entries_sync_datetime, show_read_entries, sort_order, show_preview_images, crop_preview_images, mark_scrolled_entries_as_read, sync_on_startup, sync_in_background, background_sync_interval_millis, use_built_in_browser, show_preview_text, synced_on_startup)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?);
            """
        ).use { stmt ->
            stmt.bindText(1, conf.backend)
            stmt.bindText(2, conf.minifluxServerUrl)
            stmt.bindInt(3, if (conf.minifluxServerTrustSelfSignedCerts) 1 else 0)
            stmt.bindText(4, conf.minifluxServerToken)
            stmt.bindInt(5, if (conf.initialSyncCompleted) 1 else 0)
            stmt.bindText(6, conf.lastEntriesSyncDatetime)
            stmt.bindInt(7, if (conf.showReadEntries) 1 else 0)
            stmt.bindText(8, conf.sortOrder)
            stmt.bindInt(9, if (conf.showPreviewImages) 1 else 0)
            stmt.bindInt(10, if (conf.cropPreviewImages) 1 else 0)
            stmt.bindInt(11, if (conf.markScrolledEntriesAsRead) 1 else 0)
            stmt.bindInt(12, if (conf.syncOnStartup) 1 else 0)
            stmt.bindInt(13, if (conf.syncInBackground) 1 else 0)
            stmt.bindLong(14, conf.backgroundSyncIntervalMillis)
            stmt.bindInt(15, if (conf.useBuiltInBrowser) 1 else 0)
            stmt.bindInt(16, if (conf.showPreviewText) 1 else 0)
            stmt.bindInt(17, if (conf.syncedOnStartup) 1 else 0)
            stmt.step()
        }
    }

    fun select(): Conf {
        conn.prepare(
            """
            SELECT backend,miniflux_server_url, miniflux_server_trust_self_signed_certs, miniflux_server_token, initial_sync_completed, last_entries_sync_datetime, show_read_entries, sort_order, show_preview_images, crop_preview_images, mark_scrolled_entries_as_read, sync_on_startup, sync_in_background, background_sync_interval_millis, use_built_in_browser, show_preview_text, synced_on_startup
            FROM conf
            """
        ).use { stmt ->
            return if (stmt.step()) {
                stmt.toConf()
            } else {
                confDefault()
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
}