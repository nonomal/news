package org.vestifeed.sync

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.vestifeed.backend.backend
import org.vestifeed.db.Database

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
            val conf = db.conf.select()
            val backend = backend(db)
            backend.sync(initial = conf.minifluxIncrementalSyncTimestamp == null)
        } catch (_: Throwable) {

        } finally {
            _running.update { false }
        }
    }
}
