package org.vestifeed.db

import androidx.sqlite.driver.bundled.BundledSQLiteDriver

fun db() = Database(BundledSQLiteDriver(), ":memory:")

/**
 * Schema of the `link` table as it looked from v1 through v7 — used by
 * migration tests that boot a DB at an older user_version. Once v8 lands,
 * [org.vestifeed.db.table.LinkTable.SCHEMA] already contains
 * `ext_played` / `ext_played_at`, so any test that wants to exercise the
 * v7→v8 ALTER has to install this older schema explicitly.
 */
const val LINK_SCHEMA_V7 = """
    CREATE TABLE link (
        id INTEGER PRIMARY KEY AUTOINCREMENT,
        href TEXT NOT NULL,
        rel TEXT,
        type TEXT,
        hreflang TEXT,
        title TEXT,
        length TEXT,
        feed_id TEXT REFERENCES feed(id),
        entry_id TEXT REFERENCES entry(id),
        ext_enclosure_download_progress REAL,
        ext_cache_uri TEXT,
        UNIQUE(feed_id, href, rel),
        UNIQUE(entry_id, href, rel),
        CHECK ((feed_id IS NULL) <> (entry_id IS NULL))
    ) STRICT;
"""