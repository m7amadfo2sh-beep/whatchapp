package com.whatchapp.hourlybuzz

import android.content.Context

/** Saved app state (survives restarts and reboots). */
class State(context: Context) {
    private val prefs = context.applicationContext
        .getSharedPreferences("hourly_buzz", Context.MODE_PRIVATE)

    var enabled: Boolean
        get() = prefs.getBoolean("enabled", true)
        set(value) = prefs.edit().putBoolean("enabled", value).apply()

    /** Index of the picture shown at the next picture time. */
    var nextIndex: Int
        get() = prefs.getInt("nextIndex", 0)
        set(value) = prefs.edit().putInt("nextIndex", value).apply()

    /** Index of the most recently shown picture, or -1 if none yet. */
    var lastShownIndex: Int
        get() = prefs.getInt("lastShownIndex", -1)
        set(value) = prefs.edit().putInt("lastShownIndex", value).apply()
}
