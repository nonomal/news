package org.vestifeed.db

import androidx.sqlite.SQLiteDriver
import androidx.sqlite.execSQL
import org.vestifeed.db.table.ConfTable
import org.vestifeed.db.table.EntryTable
import org.vestifeed.db.table.FeedTable
import org.vestifeed.db.table.LinkTable

class Database(driver: SQLiteDriver, val path: String) {

    private val conn = driver.open(path)

    val feed = FeedTable(conn)
    val entry = EntryTable(conn)
    val conf = ConfTable(conn)
    val link = LinkTable(conn)

    init {
        migrate()
    }

    private fun migrate() {
        val stmt = conn.prepare("SELECT user_version FROM pragma_user_version;")
        var version = if (stmt.step()) stmt.getInt(0) else 0

        if (version == 0) {
            conn.execSQL(FeedTable.SCHEMA)
            conn.execSQL(EntryTable.SCHEMA)
            conn.execSQL(LinkTable.SCHEMA)
            conn.execSQL(ConfTable.SCHEMA)
            conn.execSQL("PRAGMA user_version=4;")
            version = 4
        }

        if (version == 1) {
            conn.execSQL("ALTER TABLE conf ADD COLUMN entry_body_font_size INTEGER NOT NULL DEFAULT 16;")
            conn.execSQL("PRAGMA user_version=2;")
            version = 2
        }

        if (version == 2) {
            conn.execSQL("ALTER TABLE conf ADD COLUMN show_author_name INTEGER NOT NULL DEFAULT 0;")
            conn.execSQL("PRAGMA user_version=3;")
            version = 3
        }

        if (version == 3) {
            conn.execSQL("ALTER TABLE conf ADD COLUMN use_built_in_audio_player INTEGER NOT NULL DEFAULT 0;")
            conn.execSQL("PRAGMA user_version=4;")
            version = 4
        }
    }

    fun transaction(block: () -> Unit) {
        conn.execSQL("BEGIN TRANSACTION;")
        try {
            block()
            conn.execSQL("COMMIT;")
        } catch (e: Exception) {
            conn.execSQL("ROLLBACK;")
            throw e
        }
    }
}