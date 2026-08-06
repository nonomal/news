package org.vestifeed.sync

import android.content.Context
import androidx.work.Worker
import androidx.work.WorkerParameters
import kotlinx.coroutines.runBlocking
import org.vestifeed.app.db
import org.vestifeed.app.sync
import org.vestifeed.notifications.UnreadEntriesNotification

class SyncWorker(context: Context, workerParams: WorkerParameters) : Worker(context, workerParams) {

    override fun doWork() = runBlocking { doWorkAsync() }

    private suspend fun doWorkAsync(): Result {
        val db = applicationContext.db()
        val conf = db.conf.select()

        if (conf.backend == null) {
            return Result.failure()
        }

        val sync = applicationContext.sync()

        try {
            sync.runInForeground()
            val unreadEntries = db.entry.selectUnread()
            if (unreadEntries.isNotEmpty() && !sync.unreadScreenVisible) {
                UnreadEntriesNotification.post(applicationContext, unreadEntries)
            }
            return Result.success()
        } catch (_: Throwable) {
            return Result.failure()
        }
    }
}