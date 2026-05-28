package org.vestifeed.sync

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.vestifeed.api.Api
import org.vestifeed.api.miniflux.MinifluxImpl
import org.vestifeed.api.miniflux.MinifluxSync
import org.vestifeed.api.miniflux.minifluxHttpClient
import org.vestifeed.api.standalone.EmbeddedSync
import org.vestifeed.db.Database
import org.vestifeed.db.table.ConfTable

class Sync(
    private val scope: CoroutineScope,
    private val legacyApi: Api,
    private val db: Database,
) {

    private val _running = MutableStateFlow(false)
    val running = _running.asStateFlow()

    fun runInBackground() {
        scope.launch { run() }
    }

    suspend fun runInForeground() {
        run()
    }

    private suspend fun run() {
        while (running.value) {
            delay(100)
        }

        _running.update { true }

        try {
            val conf = withContext(Dispatchers.IO) { db.conf.select() }

            when (conf.backend) {
                ConfTable.Backend.Miniflux -> {
                    val minifluxUrl = conf.minifluxUrl
                    val minifluxToken = conf.minifluxToken

                    if (minifluxUrl == null || minifluxToken == null) {
                        throw Exception("Miniflux url or token are not set")
                    }

                    val api = MinifluxImpl(
                        client = minifluxHttpClient(token = minifluxToken),
                        baseUrl = "$minifluxUrl/v1/".toHttpUrl(),
                    )

                    val sync = MinifluxSync(db, api)

                    if (conf.minifluxInitialSyncCompleted) {
                        sync.syncFeeds()

                        val unsyncedEntries =
                            withContext(Dispatchers.IO) { db.entry.selectByReadSynced(false) }
                        val unsyncedReadEntries = unsyncedEntries.filter { it.extRead }
                        val unsyncedUnreadEntries = unsyncedEntries.filter { !it.extRead }

                        if (unsyncedReadEntries.isNotEmpty()) {
                            legacyApi.markEntriesAsRead(
                                entriesIds = unsyncedReadEntries.map { it.id },
                                read = true,
                            )

                            withContext(Dispatchers.IO) {
                                db.transaction {
                                    unsyncedReadEntries.forEach {
                                        db.entry.updateReadSynced(true, it.id)
                                    }
                                }
                            }
                        }

                        if (unsyncedUnreadEntries.isNotEmpty()) {
                            legacyApi.markEntriesAsRead(
                                entriesIds = unsyncedUnreadEntries.map { it.id },
                                read = false,
                            )

                            withContext(Dispatchers.IO) {
                                db.transaction {
                                    unsyncedUnreadEntries.forEach {
                                        db.entry.updateReadSynced(true, it.id)
                                    }
                                }
                            }
                        }

                        val notSyncedEntries =
                            withContext(Dispatchers.IO) {
                                db.entry.selectByBookmarkedSynced(
                                    false
                                )
                            }
                        val notSyncedBookmarkedEntries =
                            notSyncedEntries.filter { it.extBookmarked }
                        val notSyncedNotBookmarkedEntries =
                            notSyncedEntries.filterNot { it.extBookmarked }

                        if (notSyncedBookmarkedEntries.isNotEmpty()) {
                            legacyApi.markEntriesAsBookmarked(
                                notSyncedBookmarkedEntries,
                                true
                            )

                            withContext(Dispatchers.IO) {
                                db.transaction {
                                    notSyncedBookmarkedEntries.forEach {
                                        db.entry.updateBookmarkedSynced(true, it.id)
                                    }
                                }
                            }
                        }

                        if (notSyncedNotBookmarkedEntries.isNotEmpty()) {
                            legacyApi.markEntriesAsBookmarked(
                                notSyncedNotBookmarkedEntries,
                                false
                            )

                            withContext(Dispatchers.IO) {
                                db.transaction {
                                    notSyncedNotBookmarkedEntries.forEach {
                                        db.entry.updateBookmarkedSynced(true, it.id)
                                    }
                                }
                            }
                        }

                        sync.incrementalSync()
                    } else {
                        sync.initialSync()
                    }
                }

                ConfTable.Backend.Embedded -> {
                    val sync = EmbeddedSync(db)
                    sync.syncFeedsAndEntries()
                }

                null -> {
                    throw Exception("Backend is not set")
                }
            }
        } catch (_: Throwable) {

        } finally {
            _running.update { false }
        }
    }
}