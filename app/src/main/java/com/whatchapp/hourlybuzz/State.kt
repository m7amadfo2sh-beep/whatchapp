package com.whatchapp.hourlybuzz

import android.content.Context

/** Saved app state (survives restarts and reboots). */
class State(context: Context) {
    private val prefs = context.applicationContext
        .getSharedPreferences("hourly_buzz", Context.MODE_PRIVATE)

    var enabled: Boolean
        get() = prefs.getBoolean("enabled", true)
        set(value) = prefs.edit().putBoolean("enabled", value).apply()

    /** Position in the dhikr rotation. */
    var dhikrIndex: Int
        get() = prefs.getInt("dhikrIndex", 0)
        set(value) = prefs.edit().putInt("dhikrIndex", value).apply()

    /** Position in each card collection's rotation. */
    fun poolIndex(pool: Pool): Int = prefs.getInt("pool_${pool.name}", 0)

    fun setPoolIndex(pool: Pool, value: Int) =
        prefs.edit().putInt("pool_${pool.name}", value).apply()

    /** The most recently shown card, so it can be opened again. */
    var lastPool: Pool?
        get() = prefs.getString("lastPool", null)?.let { name -> Pool.entries.find { it.name == name } }
        set(value) = prefs.edit().putString("lastPool", value?.name).apply()

    var lastIndex: Int
        get() = prefs.getInt("lastIndex", 0)
        set(value) = prefs.edit().putInt("lastIndex", value).apply()
}
