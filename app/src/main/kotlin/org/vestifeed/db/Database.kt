package org.vestifeed.db

import androidx.sqlite.SQLiteDriver
import androidx.sqlite.execSQL
import org.vestifeed.db.table.ConfTable
import org.vestifeed.db.table.EntryTable
import org.vestifeed.db.table.FeedTable
import org.vestifeed.db.table.LinkTable
import org.vestifeed.db.table.LogTable

class Database(driver: SQLiteDriver, val path: String) {

    private val conn = driver.open(path)

    val feed = FeedTable(conn)
    val entry = EntryTable(conn)
    val conf = ConfTable(conn)
    val link = LinkTable(conn)
    val log = LogTable(conn)

    init {
        migrate()
    }

    private fun migrate() {
        val stmt = conn.prepare("SELECT user_version FROM pragma_user_version;")
        val version = if (stmt.step()) stmt.getInt(0) else 0

        if (version == 0) {
            conn.execSQL(FeedTable.SCHEMA)
            conn.execSQL(EntryTable.SCHEMA)
            conn.execSQL(LinkTable.SCHEMA)
            conn.execSQL(ConfTable.SCHEMA)
            conn.execSQL(LogTable.SCHEMA)
            conn.execSQL("PRAGMA user_version=1;")
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