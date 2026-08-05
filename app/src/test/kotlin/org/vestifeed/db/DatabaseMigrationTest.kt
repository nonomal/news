package org.vestifeed.db

import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.vestifeed.db.table.ConfTable
import org.vestifeed.db.table.EntryTable
import org.vestifeed.db.table.FeedTable
import org.vestifeed.db.table.LinkTable
import java.io.File

class DatabaseMigrationTest {

    private lateinit var dbFile: File

    @Before
    fun before() {
        dbFile = File.createTempFile("vesti-migration-test", ".db")
    }

    @After
    fun after() {
        dbFile.delete()
    }

    @Test
    fun migrate_v1ToV2_addsEntryBodyFontSizeColumn() {
        val driver = BundledSQLiteDriver()
        driver.open(dbFile.absolutePath).use { conn ->
            conn.execSQL(FeedTable.SCHEMA)
            conn.execSQL(EntryTable.SCHEMA)
            conn.execSQL(LinkTable.SCHEMA)

            val v1Schema = """
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
            """.trimIndent()
            conn.execSQL(v1Schema)

            conn.prepare(
                """
                INSERT INTO conf (
                    show_preview_images, crop_preview_images, sync_on_startup,
                    sync_in_background, background_sync_interval_millis,
                    use_built_in_browser, show_preview_text
                ) VALUES (1, 1, 1, 1, 10800000, 1, 1);
                """.trimIndent()
            ).use { stmt ->
                stmt.step()
            }

            conn.execSQL("PRAGMA user_version=1;")
        }

        val db = Database(driver, dbFile.absolutePath)

        val conf = db.conf.select()
        assertEquals(16, conf.entryBodyFontSize)
        assertTrue(conf.showPreviewImages)
        assertTrue(conf.syncInBackground)
        assertEquals(10800000L, conf.backgroundSyncIntervalMillis)
    }

    @Test
    fun migrate_v0ToV2_setsDefaultEntryBodyFontSize() {
        val db = Database(BundledSQLiteDriver(), dbFile.absolutePath)

        val conf = db.conf.select()
        assertEquals(16, conf.entryBodyFontSize)
    }

    @Test
    fun migrate_v2ToV3_addsShowAuthorNameColumn() {
        val driver = BundledSQLiteDriver()
        driver.open(dbFile.absolutePath).use { conn ->
            conn.execSQL(FeedTable.SCHEMA)
            conn.execSQL(EntryTable.SCHEMA)
            conn.execSQL(LinkTable.SCHEMA)

            val v2Schema = """
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
                    show_preview_text INTEGER NOT NULL,
                    entry_body_font_size INTEGER NOT NULL
                ) STRICT;
            """.trimIndent()
            conn.execSQL(v2Schema)

            conn.prepare(
                """
                INSERT INTO conf (
                    show_preview_images, crop_preview_images, sync_on_startup,
                    sync_in_background, background_sync_interval_millis,
                    use_built_in_browser, show_preview_text, entry_body_font_size
                ) VALUES (1, 1, 1, 1, 10800000, 1, 1, 16);
                """.trimIndent()
            ).use { stmt ->
                stmt.step()
            }

            conn.execSQL("PRAGMA user_version=2;")
        }

        val db = Database(driver, dbFile.absolutePath)

        val conf = db.conf.select()
        assertEquals(16, conf.entryBodyFontSize)
        assertEquals(false, conf.showAuthorName)

        db.conf.update { it.copy(showAuthorName = true) }
        assertEquals(true, db.conf.select().showAuthorName)
    }

    @Test
    fun migrate_v3ToV4_addsUseBuiltInAudioPlayerColumn() {
        val driver = BundledSQLiteDriver()
        driver.open(dbFile.absolutePath).use { conn ->
            conn.execSQL(FeedTable.SCHEMA)
            conn.execSQL(EntryTable.SCHEMA)
            conn.execSQL(LinkTable.SCHEMA)

            val v3Schema = """
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
                    show_preview_text INTEGER NOT NULL,
                    entry_body_font_size INTEGER NOT NULL,
                    show_author_name INTEGER NOT NULL DEFAULT 0
                ) STRICT;
            """.trimIndent()
            conn.execSQL(v3Schema)

            conn.prepare(
                """
                INSERT INTO conf (
                    show_preview_images, crop_preview_images, sync_on_startup,
                    sync_in_background, background_sync_interval_millis,
                    use_built_in_browser, show_preview_text, entry_body_font_size,
                    show_author_name
                ) VALUES (1, 1, 1, 1, 10800000, 1, 1, 16, 0);
                """.trimIndent()
            ).use { stmt ->
                stmt.step()
            }

            conn.execSQL("PRAGMA user_version=3;")
        }

        val db = Database(driver, dbFile.absolutePath)

        val conf = db.conf.select()
        assertEquals(false, conf.useBuiltInAudioPlayer)

        db.conf.update { it.copy(useBuiltInAudioPlayer = true) }
        assertEquals(true, db.conf.select().useBuiltInAudioPlayer)
    }
}