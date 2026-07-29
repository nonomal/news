package org.vestifeed.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.getSystemService
import org.vestifeed.R
import org.vestifeed.db.table.EntryTable.EntriesAdapterRow
import org.vestifeed.navigation.Activity

/**
 * Posts and cancels the "you have N unread entries" system notification. The
 * notification is shown after a periodic background sync and is meant to
 * surface new entries while the app is not in the foreground, so the unread
 * entries screen can dismiss it explicitly when it becomes visible.
 */
object UnreadEntriesNotification {
    const val CHANNEL_ID = "unread_entries"
    const val NOTIFICATION_ID = 1
    private const val MAX_NOTIFICATION_LINES = 10

    fun post(context: Context, unreadEntries: List<EntriesAdapterRow>) {
        if (unreadEntries.isEmpty()) return

        createChannel(context)

        val intent = Intent(context, Activity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }

        val pendingIntent =
            PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_IMMUTABLE)

        val count = unreadEntries.size
        val title = context.resources.getQuantityString(
            R.plurals.you_have_d_unread_news,
            count,
            count,
        )

        val mostRecent = unreadEntries.take(MAX_NOTIFICATION_LINES)

        val inboxStyle = NotificationCompat.InboxStyle()
        inboxStyle.setBigContentTitle(title)
        mostRecent.forEach { inboxStyle.addLine(it.title) }

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_baseline_list_alt_24)
            .setContentTitle(title)
            .setContentText(mostRecent.firstOrNull()?.title.orEmpty())
            .setStyle(inboxStyle)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)

        val notificationManager = NotificationManagerCompat.from(context)

        if (notificationManager.areNotificationsEnabled()) {
            notificationManager.notify(NOTIFICATION_ID, builder.build())
        }
    }

    fun cancel(context: Context) {
        NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID)
    }

    private fun createChannel(context: Context) {
        val name = context.getString(R.string.unread_news)
        val descriptionText = context.getString(R.string.unread_news)
        val importance = NotificationManager.IMPORTANCE_DEFAULT
        val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
            description = descriptionText
        }
        val notificationManager = context.getSystemService<NotificationManager>()!!
        notificationManager.createNotificationChannel(channel)
    }
}
