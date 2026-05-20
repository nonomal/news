package org.vestifeed.db.table

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