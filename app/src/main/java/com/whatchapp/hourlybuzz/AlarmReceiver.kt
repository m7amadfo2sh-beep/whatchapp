package com.whatchapp.hourlybuzz

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Fires at every event: on the hour a new dua/verse card, a few minutes later
 * the same card again (see Config.CARD_SHOW_TIMES), otherwise a 5-minute buzz.
 */
class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (!State(context).enabled) return

        val now = System.currentTimeMillis()
        val due = intent.getLongExtra(Scheduler.EXTRA_DUE, now)

        // Set the next alarm first so a failure below can't break the chain.
        Scheduler.start(context, from = maxOf(now, due))

        val repeat = Schedule.cardRepeatNumber(due)
        when {
            repeat == 0 -> {
                Alerts.fire(context, AlertKind.CARD)
                Cards.showScheduled(context, due)
            }
            repeat > 0 -> {
                Alerts.fire(context, AlertKind.CARD)
                Cards.showAgain(context)
            }
            else -> {
                Alerts.fire(context, AlertKind.TICK)
                Cards.showDhikr(context)
            }
        }
    }
}
