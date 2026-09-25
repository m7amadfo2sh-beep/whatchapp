package com.whatchapp.hourlybuzz

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Fires at every buzz time: buzz + dhikr, or on the hour a dua/verse card. */
class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (!State(context).enabled) return

        val now = System.currentTimeMillis()
        val due = intent.getLongExtra(Scheduler.EXTRA_DUE, now)

        // Set the next alarm first so a failure below can't break the chain.
        Scheduler.start(context, from = maxOf(now, due))

        if (Schedule.isCardTime(due)) {
            Alerts.fire(context, AlertKind.CARD)
            Cards.showScheduled(context, due)
        } else {
            Alerts.fire(context, AlertKind.TICK)
            Cards.showDhikr(context)
        }
    }
}
