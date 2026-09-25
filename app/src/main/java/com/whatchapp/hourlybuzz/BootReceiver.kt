package com.whatchapp.hourlybuzz

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Restarts the schedule after a reboot, an app update or a clock change. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (State(context).enabled) Scheduler.start(context)
    }
}
