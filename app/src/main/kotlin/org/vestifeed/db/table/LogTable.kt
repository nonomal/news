package org.vestifeed.db.table

import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL
import com.google.gson.JsonObject
import org.vestifeed.db.bindJsonObjectOrNull
import org.vestifeed.db.getJsonObjectOrNull
import kotlin.use

class LogTable(private val conn: SQLiteConnection) {
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

    data class Entry(
        val id: Long,
        val timestamp: String,
        val level: String,
        val tag: String,
        val message: String,
        val data: JsonObject?,
    )

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

    private val levelPriority = mapOf(
        "trace" to 0,
        "debug" to 1,
        "info" to 2,
        "warn" to 3,
        "error" to 4,
    )

    fun selectByMinLevel(minLevel: String): List<Entry> {
        val minPriority = levelPriority[minLevel] ?: 1
        val allowedLevels = levelPriority.filter { it.value >= minPriority }.keys
        conn.prepare(
            """
            SELECT id, timestamp, level, tag, message, data
            FROM log
            WHERE level IN (${allowedLevels.joinToString(",") { "'$it'" }})
            ORDER BY id DESC;
            """
        ).use { stmt ->
            return buildList {
                while (stmt.step()) {
                    add(
                        Entry(
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