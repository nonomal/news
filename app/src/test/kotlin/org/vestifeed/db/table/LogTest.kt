package org.vestifeed.db.table

import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import com.google.gson.JsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.vestifeed.db.Database
import org.vestifeed.log.LogLevel

class LogTest {

    private lateinit var db: Database

    @Before
    fun before() {
        db = Database(BundledSQLiteDriver(), ":memory:")
    }

    @Test
    fun logSchema_createTableStatement() {
        val statement = LogTable.SCHEMA
        assertTrue(statement.contains("CREATE TABLE log"))
        assertTrue(statement.contains("id INTEGER PRIMARY KEY AUTOINCREMENT"))
        assertTrue(statement.contains("level TEXT NOT NULL"))
        assertTrue(statement.contains("tag TEXT NOT NULL"))
        assertTrue(statement.contains("message TEXT NOT NULL"))
    }

    @Test
    fun logQueries_insert() {
        val entry = createInsertArgs()
        db.log.insert(entry)
        val result = db.log.selectByMinLevel(LogLevel.TRACE).single()
        assertEquals(entry.level, result.level)
        assertEquals(entry.tag, result.tag)
        assertEquals(entry.message, result.message)
    }

    @Test
    fun logQueries_insert_multiple() {
        val entries = listOf(createInsertArgs(), createInsertArgs(), createInsertArgs())
        db.log.insert(entries)
        assertEquals(3, db.log.selectByMinLevel(LogLevel.TRACE).size)
    }

    @Test
    fun logQueries_insert_emptyList() {
        db.log.insert(emptyList())
        assertTrue(db.log.selectByMinLevel(LogLevel.TRACE).isEmpty())
    }

    @Test
    fun logQueries_selectAll_sortsByIdDesc() {
        val entries = listOf(
            createInsertArgs(message = "First"),
            createInsertArgs(message = "Second"),
            createInsertArgs(message = "Third"),
        )
        db.log.insert(entries)

        val result = db.log.selectByMinLevel(LogLevel.TRACE)
        assertEquals("Third", result[0].message)
        assertEquals("Second", result[1].message)
        assertEquals("First", result[2].message)
    }

    @Test
    fun logQueries_selectAll_empty() {
        assertTrue(db.log.selectByMinLevel(LogLevel.TRACE).isEmpty())
    }

    @Test
    fun logQueries_deleteAll() {
        val entries = listOf(createInsertArgs(), createInsertArgs(), createInsertArgs())
        db.log.insert(entries)

        db.log.deleteAll()

        assertTrue(db.log.selectByMinLevel(LogLevel.TRACE).isEmpty())
    }

    @Test
    fun logQueries_withData() {
        val data = JsonObject().apply { addProperty("key", "value") }
        val entry = createInsertArgs(data = data)
        db.log.insert(entry)

        val result = db.log.selectByMinLevel(LogLevel.TRACE).single()
        assertEquals("value", result.data!!.get("key").asString)
    }

    @Test
    fun logQueries_withNullData() {
        val entry = createInsertArgs(data = null)
        db.log.insert(entry)

        val result = db.log.selectByMinLevel(LogLevel.TRACE).single()
        assertNull(result.data)
    }

    private fun createInsertArgs(
        level: LogLevel = LogLevel.INFO,
        tag: String = "TestTag",
        message: String = "Test message",
        data: JsonObject? = null,
    ) = LogTable.InsertArgs(
        level = level,
        tag = tag,
        message = message,
        data = data,
    )
}