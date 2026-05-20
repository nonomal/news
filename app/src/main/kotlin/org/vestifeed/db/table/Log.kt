package org.vestifeed.db.table

import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL
import com.google.gson.JsonObject
import org.vestifeed.db.bindJsonObjectOrNull
import org.vestifeed.db.getJsonObjectOrNull

class Log {
    companion object {
        const val SCHEMA = """
            CREATE TABLE log (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                timestamp TEXT NOT NULL DEFAULT (strftime('%Y-%m-%d %H:%f', 'NOW')),
                level TEXT NOT NULL CHECK(level IN ('trace', 'debug', 'info', 'warn', 'error')),
                tag TEXT NOT NULL,
                message TEXT NOT NULL,
                data TEXT CHECK(json_valid(data))
            ) STRICT;
        """
    }
}

data class LogEntry(
    val id: Long,
    val timestamp: String,
    val level: String,
    val tag: String,
    val message: String,
    val data: JsonObject?,
)

class LogQueries(private val conn: SQLiteConnection) {
    data class InsertArgs(
        val level: String,
        val tag: String,
        val message: String,
        val data: JsonObject? = null,
    )

    fun insert(args: InsertArgs) {
        insert(listOf(args))
    }

    fun insert(args: List<InsertArgs>) {
        if (args.isEmpty()) {
            return
        }
        conn.prepare(
            """
            INSERT INTO log (level, tag, message, data)
            VALUES (?, ?, ?, ?);
            """
        ).use { stmt ->
            args.forEach { entry ->
                stmt.bindText(1, entry.level)
                stmt.bindText(2, entry.tag)
                stmt.bindText(3, entry.message)
                stmt.bindJsonObjectOrNull(4, entry.data)
                stmt.step()
                stmt.reset()
            }
        }
    }

    fun selectAll(): List<LogEntry> {
        conn.prepare(
            """
            SELECT id, timestamp, level, tag, message, data
            FROM log
            ORDER BY id DESC;
            """
        ).use { stmt ->
            return buildList {
                while (stmt.step()) {
                    add(
                        LogEntry(
                            id = stmt.getLong(0),
                            timestamp = stmt.getText(1),
                            level = stmt.getText(2),
                            tag = stmt.getText(3),
                            message = stmt.getText(4),
                            data = stmt.getJsonObjectOrNull(5),
                        )
                    )
                }
            }
        }
    }

    fun deleteAll() {
        conn.execSQL("DELETE FROM log;")
    }
}