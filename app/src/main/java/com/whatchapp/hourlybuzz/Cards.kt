package com.whatchapp.hourlybuzz

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context

/**
 * Shows the dhikr and the hourly card.
 *
 * Only the hourly card turns the screen on (a full-screen notification, or
 * directly if the app is already visible). The 5-minute dhikr is a quiet
 * notification that waits until you look at the watch.
 */
object Cards {
    private const val CHANNEL_CARDS = "cards"
    private const val CHANNEL_DHIKR = "dhikr_quiet"

    /** Channels from older versions, removed on upgrade. */
    private val OLD_CHANNELS = listOf("dhikr")
    const val NOTIFICATION_CARD = 1
    const val NOTIFICATION_DHIKR = 2

    /** Number of this app's screens currently visible. */
    @Volatile
    var visibleScreens = 0

    /**
     * Every buzz: post the next dhikr as a quiet notification. It does not turn
     * the screen on; tapping it shows the dhikr full screen.
     */
    fun showDhikr(context: Context) {
        if (!Config.SHOW_DHIKR) return
        val text = Content.nextDhikr(context) ?: return
        val pending = PendingIntent.getActivity(
            context, NOTIFICATION_DHIKR, DhikrActivity.intent(context, text),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notification = Notification.Builder(context, channel(context, CHANNEL_DHIKR))
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(text)
            .setStyle(Notification.BigTextStyle().bigText(text))
            .setCategory(Notification.CATEGORY_REMINDER)
            .setContentIntent(pending)
            .setAutoCancel(true)
            .setOnlyAlertOnce(true)
            .setTimeoutAfter(Schedule.tickMs)
            .build()
        context.getSystemService(NotificationManager::class.java).notify(NOTIFICATION_DHIKR, notification)
    }

    /** On the hour: show the next dua or verse. */
    fun showScheduled(context: Context, due: Long) {
        val pool = Content.poolFor(Schedule.cardSlot(due), Schedule.hourOf(due))
        if (Content.cards(context, pool).isEmpty()) return
        val index = Content.takeNext(context, pool)
        val state = State(context)
        state.lastPool = pool
        state.lastIndex = index

        cancel(context, NOTIFICATION_DHIKR)
        presentCard(context, pool, index)
    }

    /** A few minutes after the hour: show the same card again. */
    fun showAgain(context: Context) {
        val state = State(context)
        val pool = state.lastPool ?: return
        if (Content.cards(context, pool).isEmpty()) return
        presentCard(context, pool, state.lastIndex)
    }

    private fun presentCard(context: Context, pool: Pool, index: Int) {
        val cards = Content.cards(context, pool)
        val card = cards[Schedule.wrapIndex(index, cards.size)]
        val intent = CardActivity.intent(context, pool, index, scheduled = true)
        if (visibleScreens > 0) {
            context.startActivity(intent)
            return
        }
        // A full-screen notification turns the screen on and opens the card.
        val pending = PendingIntent.getActivity(
            context, NOTIFICATION_CARD, intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notification = Notification.Builder(context, channel(context, CHANNEL_CARDS))
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(context.getString(R.string.app_name))
            .setContentText(card.title)
            .setCategory(Notification.CATEGORY_ALARM)
            .setFullScreenIntent(pending, true)
            .setContentIntent(pending)
            .setAutoCancel(true)
            .setTimeoutAfter(Schedule.cardMs)
            .build()
        context.getSystemService(NotificationManager::class.java).notify(NOTIFICATION_CARD, notification)
    }

    /** Opens the most recent card (or the next one if none was shown yet). */
    fun open(context: Context) {
        val state = State(context)
        val pool = state.lastPool
            ?: Content.poolFor(Schedule.cardSlot(System.currentTimeMillis()), Schedule.hourOf(System.currentTimeMillis()))
        val index = if (state.lastPool != null) state.lastIndex else state.poolIndex(pool)
        context.startActivity(CardActivity.intent(context, pool, index, scheduled = false))
    }

    fun cancel(context: Context, id: Int) {
        context.getSystemService(NotificationManager::class.java).cancel(id)
    }

    private fun channel(context: Context, id: String): String {
        val manager = context.getSystemService(NotificationManager::class.java)
        OLD_CHANNELS.forEach { manager.deleteNotificationChannel(it) }
        if (manager.getNotificationChannel(id) == null) {
            val dhikr = id == CHANNEL_DHIKR
            // Low importance: no pop-up and no screen wake for the dhikr.
            val importance = if (dhikr) NotificationManager.IMPORTANCE_LOW else NotificationManager.IMPORTANCE_HIGH
            val name = if (dhikr) R.string.channel_dhikr else R.string.channel_cards
            manager.createNotificationChannel(
                NotificationChannel(id, context.getString(name), importance).apply {
                    // The app does its own buzz/sound (see Alerts).
                    setSound(null, null)
                    enableVibration(false)
                }
            )
        }
        return id
    }
}
