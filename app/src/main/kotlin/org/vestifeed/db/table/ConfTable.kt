package org.vestifeed.db.table

import androidx.sqlite.SQLiteConnection
import androidx.sqlite.SQLiteStatement
import androidx.sqlite.execSQL
import org.vestifeed.db.bindTextOrNull
import org.vestifeed.db.getTextOrNull
import kotlin.use

class ConfTable(private val conn: SQLiteConnection) {
    companion object {
        const val SCHEMA = """
            CREATE TABLE conf (
                backend TEXT,
                miniflux_url TEXT,
                miniflux_token TEXT,
                minifluxIncrementalSyncTimestamp TEXT,
                show_preview_images INTEGER NOT NULL,
                crop_preview_images INTEGER NOT NULL,
                sync_on_startup INTEGER NOT NULL,
                sync_in_background INTEGER NOT NULL,
                background_sync_interval_millis INTEGER NOT NULL,
                use_built_in_browser INTEGER NOT NULL,
                show_preview_text INTEGER NOT NULL
            ) STRICT;
        """

        fun defaultConf(): Conf = Conf(
            backend = null,
            minifluxUrl = null,
            minifluxToken = null,
            minifluxIncrementalSyncTimestamp = null,
            showPreviewImages = true,
            cropPreviewImages = true,
            syncOnStartup = true,
            syncInBackground = true,
            backgroundSyncIntervalMillis = 10800000L,
            useBuiltInBrowser = true,
            showPreviewText = true,
        )
    }

    enum class Backend {
        Miniflux,
        Embedded,
    }

    data class Conf(
        // miniflux or embedded
        val backend: Backend?,
        // https://miniflux.app/docs/api.html
        val minifluxUrl: String?,
        // Per-application API keys (since version 2.0.21) -> preferred method
        val minifluxToken: String?,
        // if null, should fetch read + starred and set to
        // current timestamp to fetch future updates
        val minifluxIncrementalSyncTimestamp: String?,
        // based on HTML OpenGraph tags
        val showPreviewImages: Boolean,
        // fixed height
        val cropPreviewImages: Boolean,
        // default on when it's cheap (Miniflux)
        // default off when it's expensive (embedded)
        val syncOnStartup: Boolean,
        // pre-fetched news speed things up when user comes back
        val syncInBackground: Boolean,
        val backgroundSyncIntervalMillis: Long,
        val useBuiltInBrowser: Boolean,
        val showPreviewText: Boolean,
    )

    fun SQLiteStatement.toConf(): Conf = Conf(
        backend = getBackendOrNull(0),
        minifluxUrl = getTextOrNull(1),
        minifluxToken = getTextOrNull(2),
        minifluxIncrementalSyncTimestamp = getTextOrNull(3),
        showPreviewImages = getInt(4) == 1,
        cropPreviewImages = getInt(5) == 1,
        syncOnStartup = getInt(6) == 1,
        syncInBackground = getInt(7) == 1,
        backgroundSyncIntervalMillis = getLong(8),
        useBuiltInBrowser = getInt(9) == 1,
        showPreviewText = getInt(10) == 1,
    )

    fun insert(conf: Conf) {
        conn.prepare(
            """
            INSERT OR REPLACE INTO conf (backend, miniflux_url, miniflux_token, minifluxIncrementalSyncTimestamp, show_preview_images, crop_preview_images, sync_on_startup, sync_in_background, background_sync_interval_millis, use_built_in_browser, show_preview_text)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?);
            """
        ).use { stmt ->
            stmt.bindTextOrNull(1, conf.backend?.name?.lowercase())
            stmt.bindTextOrNull(2, conf.minifluxUrl)
            stmt.bindTextOrNull(3, conf.minifluxToken)
            stmt.bindTextOrNull(4, conf.minifluxIncrementalSyncTimestamp)
            stmt.bindInt(5, if (conf.showPreviewImages) 1 else 0)
            stmt.bindInt(6, if (conf.cropPreviewImages) 1 else 0)
            stmt.bindInt(7, if (conf.syncOnStartup) 1 else 0)
            stmt.bindInt(8, if (conf.syncInBackground) 1 else 0)
            stmt.bindLong(9, conf.backgroundSyncIntervalMillis)
            stmt.bindInt(10, if (conf.useBuiltInBrowser) 1 else 0)
            stmt.bindInt(11, if (conf.showPreviewText) 1 else 0)
            stmt.step()
        }
    }

    fun select(): Conf {
        conn.prepare(
            """
            SELECT backend, miniflux_url, miniflux_token, minifluxIncrementalSyncTimestamp, show_preview_images, crop_preview_images, sync_on_startup, sync_in_background, background_sync_interval_millis, use_built_in_browser, show_preview_text
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