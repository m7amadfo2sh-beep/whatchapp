package com.whatchapp.hourlybuzz

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent

/** Showing the hourly picture. */
object Pictures {
    private const val CHANNEL_ID = "pictures"
    const val NOTIFICATION_ID = 1

    /** Number of this app's screens currently visible. */
    @Volatile
    var visibleScreens = 0

    val count: Int get() = Config.IMAGE_URLS.size

    /** Called on the hour: advance the rotation, alert, and show the picture. */
    fun showScheduled(context: Context) {
        Alerts.fire(context, AlertKind.IMAGE)
        if (count == 0) return

        val state = State(context)
        val index = Schedule.wrapIndex(state.nextIndex, count)
        state.lastShownIndex = index
        state.nextIndex = Schedule.wrapIndex(index + 1, count)

        val intent = ImageActivity.intent(context, index, scheduled = true)
        if (visibleScreens > 0) {
            // The app is on screen, so it may open the picture directly.
            context.startActivity(intent)
            return
        }
        // Otherwise a full-screen notification turns the screen on and opens it.
        val pending = PendingIntent.getActivity(
            context, 0, intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notification = Notification.Builder(context, channel(context))
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(context.getString(R.string.picture_time))
            .setContentText(context.getString(R.string.picture_of, index + 1, count))
            .setCategory(Notification.CATEGORY_ALARM)
            .setFullScreenIntent(pending, true)
            .setContentIntent(pending)
            .setAutoCancel(true)
            .setTimeoutAfter(Schedule.imageMs)
            .build()
        context.getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, notification)
    }

    /** Opens the picture viewer (without changing the rotation). */
    fun open(context: Context) {
        if (count == 0) return
        val state = State(context)
        val index = if (state.lastShownIndex >= 0) state.lastShownIndex else state.nextIndex
        context.startActivity(ImageActivity.intent(context, index, scheduled = false))
    }

    fun cancelNotification(context: Context) {
        context.getSystemService(NotificationManager::class.java).cancel(NOTIFICATION_ID)
    }

    private fun channel(context: Context): String {
        val manager = context.getSystemService(NotificationManager::class.java)
        if (manager.getNotificationChannel(CHANNEL_ID) == null) {
            manager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID, context.getString(R.string.channel_pictures),
                    NotificationManager.IMPORTANCE_HIGH,
                ).apply {
                    // The app does its own buzz/sound (see Alerts).
                    setSound(null, null)
                    enableVibration(false)
                }
            )
        }
        return CHANNEL_ID
    }
}
