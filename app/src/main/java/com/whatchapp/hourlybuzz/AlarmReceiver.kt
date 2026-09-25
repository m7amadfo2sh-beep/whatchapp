package com.whatchapp.hourlybuzz

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlin.concurrent.thread

/** Fires at every buzz time: buzz, or show the picture on the hour. */
class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val state = State(context)
        if (!state.enabled) return

        val now = System.currentTimeMillis()
        val due = intent.getLongExtra(Scheduler.EXTRA_DUE, now)

        // Set the next alarm first so a failure below can't break the chain.
        Scheduler.start(context, from = maxOf(now, due))

        if (Schedule.isImageTime(due)) {
            Pictures.showScheduled(context)
        } else {
            Alerts.fire(context, AlertKind.TICK)
        }

        // Download the next picture shortly before it is due.
        val nextImage = Schedule.nextAligned(due, Schedule.imageMs)
        if (nextImage - due <= Schedule.preloadMs && Config.IMAGE_URLS.isNotEmpty()) {
            val url = Config.IMAGE_URLS[Schedule.wrapIndex(state.nextIndex, Config.IMAGE_URLS.size)]
            val pending = goAsync()
            thread {
                try {
                    ImageCache.download(context, url)
                } finally {
                    pending.finish()
                }
            }
        }
    }
}
