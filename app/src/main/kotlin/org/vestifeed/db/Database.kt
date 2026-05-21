package org.vestifeed.db

import androidx.sqlite.SQLiteDriver
import androidx.sqlite.execSQL
import org.vestifeed.db.table.ConfQueries
import org.vestifeed.db.table.ConfSchema
import org.vestifeed.db.table.EntryTable
import org.vestifeed.db.table.FEED_SCHEMA
import org.vestifeed.db.table.FeedQueries
import org.vestifeed.db.table.LINK_SCHEMA
import org.vestifeed.db.table.LinkQueries
import org.vestifeed.db.table.LogTable

class Database(driver: SQLiteDriver, val path: String) {

    private val conn = driver.open(path)

    val feed = FeedQueries(conn)
    val entry = EntryTable(conn)
    val conf = ConfQueries(conn)
    val link = LinkQueries(conn)
    val log = LogTable(conn)

    init {
        migrate()
    }

    private fun migrate() {
        val stmt = conn.prepare("SELECT user_version FROM pragma_user_version;")
        val version = if (stmt.step()) stmt.getInt(0) else 0

        if (version == 0) {
            conn.execSQL(FEED_SCHEMA)
            conn.execSQL(EntryTable.SCHEMA)
            conn.execSQL(LINK_SCHEMA)
            conn.execSQL(ConfSchema.toString())
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