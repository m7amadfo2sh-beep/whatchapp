package com.whatchapp.hourlybuzz

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build

/**
 * One alarm chain: an exact alarm at every buzz time (:00, :05, ...). Each
 * alarm either buzzes or, on the hour, shows a picture, then sets the next one.
 *
 * setAlarmClock() is used because it is the only alarm type Android lets fire
 * every few minutes while the watch is asleep (it shows as the "next alarm").
 */
object Scheduler {
    const val EXTRA_DUE = "due"

    /** Turns the reminders on or off and remembers the choice. */
    fun setEnabled(context: Context, on: Boolean) {
        State(context).enabled = on
        if (on) start(context) else stop(context)
    }

    /** False if Android hasn't allowed exact alarms (only possible before Android 13). */
    fun canSchedule(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
            context.getSystemService(AlarmManager::class.java).canScheduleExactAlarms()

    /** Sets the alarm for the next buzz time after [from]. */
    fun start(context: Context, from: Long = System.currentTimeMillis()): Boolean {
        if (!canSchedule(context)) return false
        val due = Schedule.nextAligned(from, Schedule.tickMs)
        val alarmManager = context.getSystemService(AlarmManager::class.java)
        val showApp = PendingIntent.getActivity(
            context, 0, Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE,
        )
        alarmManager.setAlarmClock(AlarmManager.AlarmClockInfo(due, showApp), alarmIntent(context, due))
        return true
    }

    fun stop(context: Context) {
        context.getSystemService(AlarmManager::class.java).cancel(alarmIntent(context, 0))
    }

    private fun alarmIntent(context: Context, due: Long): PendingIntent =
        PendingIntent.getBroadcast(
            context, 0,
            Intent(context, AlarmReceiver::class.java).putExtra(EXTRA_DUE, due),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
}
