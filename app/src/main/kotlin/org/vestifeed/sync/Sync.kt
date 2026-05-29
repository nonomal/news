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
import org.vestifeed.api.miniflux.MinifluxImpl
import org.vestifeed.api.miniflux.MinifluxSync
import org.vestifeed.api.miniflux.minifluxHttpClient
import org.vestifeed.api.standalone.EmbeddedSync
import org.vestifeed.db.Database
import org.vestifeed.db.table.ConfTable

class Sync(
    private val scope: CoroutineScope,
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
                    if (conf.minifluxUrl == null) {
                        throw Exception("conf.minifluxUrl is missing")
                    }
                    if (conf.minifluxToken == null) {
                        throw Exception("conf.minifluxToken is missing")
                    }
                    val api = MinifluxImpl(
                        client = minifluxHttpClient(token = conf.minifluxToken),
                        baseUrl = "${conf.minifluxUrl}/v1/".toHttpUrl(),
                    )
                    val sync = MinifluxSync(db, api)
                    sync.syncFeeds()
                    sync.syncEntries(initial = conf.minifluxIncrementalSyncTimestamp == null)
                }

                ConfTable.Backend.Embedded -> {
                    val sync = EmbeddedSync(db)
                    sync.syncFeedsAndEntries()
                }

                null -> {
                    throw Exception("conf.backend is missing")
                }
            }
        } catch (_: Throwable) {

        } finally {
            _running.update { false }
        }
    }
}