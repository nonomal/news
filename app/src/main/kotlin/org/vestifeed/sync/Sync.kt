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

    /**
     * `true` while the global unread entries screen is the top fragment in
     * the foreground activity. [SyncWorker] consults this before posting the
     * "you have N unread entries" notification so the user is not notified
     * about a list they can already see, and the fragment can clear any
     * stale notification that was posted before it became visible.
     *
     * Written from the main thread, read from the worker thread; `@Volatile`
     * is sufficient because each access is an independent snapshot.
     */
    @Volatile
    var unreadScreenVisible: Boolean = false

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
