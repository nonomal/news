package sync

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.getSystemService
import androidx.work.Worker
import androidx.work.WorkerParameters
import co.appreactor.news.R
import common.App
import common.AppActivity
import common.ConfRepository
import entries.EntriesRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.koin.android.ext.android.get

class SyncWorker(context: Context, workerParams: WorkerParameters) : Worker(context, workerParams) {

    override fun doWork() = runBlocking { doWorkAsync() }

    private suspend fun doWorkAsync(): Result {
        val app = applicationContext as App
        val conf = app.get<ConfRepository>().select().first()
        val sync = app.get<NewsApiSync>()
        val entriesRepository = app.get<EntriesRepository>()

        if (!conf.initialSyncCompleted) {
            return Result.retry()
        }

        when (val syncResult = sync.sync()) {
            is SyncResult.Success -> {
                if (syncResult.newAndUpdatedEntries > 0) {
                    runCatching {
                        val unreadEntries = entriesRepository.selectByReadAndBookmarked(
                            read = false,
                            bookmarked = false,
                        ).first().size

                        if (unreadEntries > 0) {
                            showUnreadEntriesNotification(unreadEntries, app)
                        }
                    }
                }
            }
            is SyncResult.Failure -> {
                return Result.failure()
            }
        }

        return Result.success()
    }

    private fun showUnreadEntriesNotification(unreadEntries: Int, context: Context) {
        createNotificationChannel(context)

        val intent = Intent(context, AppActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }

        val pendingIntent =
            PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_IMMUTABLE)

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_baseline_list_alt_24)
            .setContentTitle(context.getString(R.string.app_name))
            .setContentText(
                context.resources.getQuantityString(
                    R.plurals.you_have_d_unread_news,
                    unreadEntries,
                    unreadEntries,
                )
            )
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)

        with(NotificationManagerCompat.from(context)) {
            notify(NOTIFICATION_ID, builder.build())
        }
    }

    private fun createNotificationChannel(context: Context) {
        val name = context.getString(R.string.unread_news)
        val descriptionText = context.getString(R.string.unread_news)
        val importance = NotificationManager.IMPORTANCE_DEFAULT
        val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
            description = descriptionText
        }
        val notificationManager = context.getSystemService<NotificationManager>()!!
        notificationManager.createNotificationChannel(channel)
    }

    companion object {
        private const val CHANNEL_ID = "unread_entries"
        private const val NOTIFICATION_ID = 1
    }
}