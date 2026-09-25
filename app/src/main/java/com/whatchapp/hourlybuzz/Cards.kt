package com.whatchapp.hourlybuzz

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent

/**
 * Puts the dhikr and the hourly card on screen.
 *
 * When the app is already visible it opens the screen directly; otherwise a
 * full-screen notification turns the watch screen on and opens it.
 */
object Cards {
    private const val CHANNEL_CARDS = "cards"
    private const val CHANNEL_DHIKR = "dhikr"
    const val NOTIFICATION_CARD = 1
    const val NOTIFICATION_DHIKR = 2

    /** Number of this app's screens currently visible. */
    @Volatile
    var visibleScreens = 0

    /** Every buzz: show the next dhikr. */
    fun showDhikr(context: Context) {
        if (!Config.SHOW_DHIKR) return
        val text = Content.nextDhikr(context) ?: return
        val intent = DhikrActivity.intent(context, text)
        present(context, intent, CHANNEL_DHIKR, NOTIFICATION_DHIKR, text, Schedule.tickMs)
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
        val card = Content.cards(context, pool)[index]
        val intent = CardActivity.intent(context, pool, index, scheduled = true)
        present(context, intent, CHANNEL_CARDS, NOTIFICATION_CARD, card.title, Schedule.cardMs)
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

    private fun present(
        context: Context, intent: Intent, channel: String, id: Int, text: String, timeoutMs: Long,
    ) {
        if (visibleScreens > 0) {
            context.startActivity(intent)
            return
        }
        val pending = PendingIntent.getActivity(
            context, id, intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notification = Notification.Builder(context, channel(context, channel))
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(context.getString(R.string.app_name))
            .setContentText(text)
            .setCategory(Notification.CATEGORY_ALARM)
            .setFullScreenIntent(pending, true)
            .setContentIntent(pending)
            .setAutoCancel(true)
            .setTimeoutAfter(timeoutMs)
            .build()
        context.getSystemService(NotificationManager::class.java).notify(id, notification)
    }

    private fun channel(context: Context, id: String): String {
        val manager = context.getSystemService(NotificationManager::class.java)
        if (manager.getNotificationChannel(id) == null) {
            val name = if (id == CHANNEL_DHIKR) R.string.channel_dhikr else R.string.channel_cards
            manager.createNotificationChannel(
                NotificationChannel(id, context.getString(name), NotificationManager.IMPORTANCE_HIGH).apply {
                    // The app does its own buzz/sound (see Alerts).
                    setSound(null, null)
                    enableVibration(false)
                }
            )
        }
        return id
    }
}
