package org.vestifeed.db.table

import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.vestifeed.db.Database

class ConfTest {

    private lateinit var db: Database

    @Before
    fun before() {
        db = Database(BundledSQLiteDriver(), ":memory:")
    }

    @Test
    fun confSchema_constants() {
        assertEquals("embedded", ConfTable.Backend.Embedded.name.lowercase())
        assertEquals("miniflux", ConfTable.Backend.Miniflux.name.lowercase())
        assertEquals("ascending", ConfTable.SORT_ORDER_ASCENDING)
        assertEquals("descending", ConfTable.SORT_ORDER_DESCENDING)
    }

    @Test
    fun confSchema_createTableStatement() {
        val statement = ConfTable.SCHEMA
        assertTrue(statement.contains("CREATE TABLE conf"))
        assertTrue(statement.contains("backend TEXT"))
        assertTrue(statement.contains("miniflux_server_url TEXT NOT NULL"))
        assertTrue(statement.contains("background_sync_interval_millis INTEGER NOT NULL"))
    }

    @Test
    fun confDefaults_values() {
        val defaultConf = ConfTable.confDefault()
        assertEquals(null, defaultConf.backend)
        assertEquals("", defaultConf.minifluxServerUrl)
        assertEquals("", defaultConf.minifluxServerToken)
        assertFalse(defaultConf.initialSyncCompleted)
        assertEquals("", defaultConf.lastEntriesSyncDatetime)
        assertFalse(defaultConf.showReadEntries)
        assertEquals(ConfTable.SORT_ORDER_DESCENDING, defaultConf.sortOrder)
        assertTrue(defaultConf.showPreviewImages)
        assertTrue(defaultConf.cropPreviewImages)
        assertFalse(defaultConf.markScrolledEntriesAsRead)
        assertTrue(defaultConf.syncOnStartup)
        assertTrue(defaultConf.syncInBackground)
        assertEquals(10800000L, defaultConf.backgroundSyncIntervalMillis)
        assertTrue(defaultConf.useBuiltInBrowser)
        assertTrue(defaultConf.showPreviewText)
        assertFalse(defaultConf.syncedOnStartup)
    }

    @Test
    fun confQueries_select_emptyReturnsDefault() {
        val result = db.conf.select()
        val defaultConf = ConfTable.confDefault()
        assertEquals(defaultConf.backend, result.backend)
        assertEquals(defaultConf.sortOrder, result.sortOrder)
        assertEquals(defaultConf.backgroundSyncIntervalMillis, result.backgroundSyncIntervalMillis)
    }

    @Test
    fun confQueries_insertAndSelect() {
        val conf = createConf()
        db.conf.insert(conf)

        val result = db.conf.select()
        assertEquals(conf.backend, result.backend)
        assertEquals(conf.minifluxServerUrl, result.minifluxServerUrl)
        assertEquals(conf.minifluxServerToken, result.minifluxServerToken)
        assertEquals(conf.initialSyncCompleted, result.initialSyncCompleted)
        assertEquals(conf.lastEntriesSyncDatetime, result.lastEntriesSyncDatetime)
        assertEquals(conf.showReadEntries, result.showReadEntries)
        assertEquals(conf.sortOrder, result.sortOrder)
        assertEquals(conf.showPreviewImages, result.showPreviewImages)
        assertEquals(conf.cropPreviewImages, result.cropPreviewImages)
        assertEquals(conf.markScrolledEntriesAsRead, result.markScrolledEntriesAsRead)
        assertEquals(conf.syncOnStartup, result.syncOnStartup)
        assertEquals(conf.syncInBackground, result.syncInBackground)
        assertEquals(conf.backgroundSyncIntervalMillis, result.backgroundSyncIntervalMillis)
        assertEquals(conf.useBuiltInBrowser, result.useBuiltInBrowser)
        assertEquals(conf.showPreviewText, result.showPreviewText)
        assertEquals(conf.syncedOnStartup, result.syncedOnStartup)
    }

    @Test
    fun confQueries_insert_replacesExisting() {
        val conf1 = createConf(backend = ConfTable.Backend.Embedded)
        db.conf.insert(conf1)

        db.conf.delete()

        val conf2 = createConf(backend = ConfTable.Backend.Miniflux)
        db.conf.insert(conf2)

        val result = db.conf.select()
        assertEquals(ConfTable.Backend.Miniflux, result.backend)
    }

    @Test
    fun confQueries_update() {
        val initialConf = createConf(
            backend = ConfTable.Backend.Embedded,
            showPreviewImages = false,
        )
        db.conf.insert(initialConf)

        db.conf.update { it.copy(backend = ConfTable.Backend.Miniflux, showPreviewImages = true) }

        val result = db.conf.select()
        assertEquals(ConfTable.Backend.Miniflux, result.backend)
        assertTrue(result.showPreviewImages)
    }

    @Test
    fun confQueries_delete() {
        val conf = createConf()
        db.conf.insert(conf)
        assertEquals(conf.backend, db.conf.select().backend)

        db.conf.delete()
        val defaultConf = ConfTable.confDefault()
        assertEquals(defaultConf.backend, db.conf.select().backend)
    }

    @Test
    fun confQueries_updatePartialFields() {
        db.conf.insert(createConf(sortOrder = ConfTable.SORT_ORDER_ASCENDING))

        db.conf.update { it.copy(syncOnStartup = false) }

        val result = db.conf.select()
        assertEquals(ConfTable.SORT_ORDER_ASCENDING, result.sortOrder)
        assertFalse(result.syncOnStartup)
    }

    private fun createConf(
        backend: ConfTable.Backend = ConfTable.Backend.Embedded,
        minifluxServerUrl: String = "https://miniflux.example.com",
        minifluxServerToken: String = "miniflux-token",
        initialSyncCompleted: Boolean = true,
        lastEntriesSyncDatetime: String = "2024-01-01T00:00:00Z",
        showReadEntries: Boolean = true,
        sortOrder: String = ConfTable.SORT_ORDER_DESCENDING,
        showPreviewImages: Boolean = true,
        cropPreviewImages: Boolean = false,
        markScrolledEntriesAsRead: Boolean = true,
        syncOnStartup: Boolean = false,
        syncInBackground: Boolean = false,
        backgroundSyncIntervalMillis: Long = 3600000L,
        useBuiltInBrowser: Boolean = false,
        showPreviewText: Boolean = false,
        syncedOnStartup: Boolean = true,
    ) = ConfTable.Conf(
        backend = backend,
        minifluxServerUrl = minifluxServerUrl,
        minifluxServerToken = minifluxServerToken,
        initialSyncCompleted = initialSyncCompleted,
        lastEntriesSyncDatetime = lastEntriesSyncDatetime,
        showReadEntries = showReadEntries,
        sortOrder = sortOrder,
        showPreviewImages = showPreviewImages,
        cropPreviewImages = cropPreviewImages,
        markScrolledEntriesAsRead = markScrolledEntriesAsRead,
        syncOnStartup = syncOnStartup,
        syncInBackground = syncInBackground,
        backgroundSyncIntervalMillis = backgroundSyncIntervalMillis,
        useBuiltInBrowser = useBuiltInBrowser,
        showPreviewText = showPreviewText,
        syncedOnStartup = syncedOnStartup,
    )
}