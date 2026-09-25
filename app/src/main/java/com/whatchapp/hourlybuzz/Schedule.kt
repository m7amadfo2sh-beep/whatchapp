package com.whatchapp.hourlybuzz

import java.time.Instant
import java.time.ZoneId

/** Clock maths. Pure functions, unit-tested in ScheduleTest. */
object Schedule {

    val tickMs: Long
        get() = if (Config.TEST_MODE) Config.TEST_VIBRATE_SECONDS * 1000L
        else Config.VIBRATE_EVERY_MINUTES * 60_000L

    val cardMs: Long
        get() = if (Config.TEST_MODE) Config.TEST_CARD_SECONDS * 1000L
        else Config.CARD_EVERY_MINUTES * 60_000L

    /** Start of the local day containing [now]. */
    fun localMidnight(now: Long, zone: ZoneId = ZoneId.systemDefault()): Long =
        Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
            .atStartOfDay(zone).toInstant().toEpochMilli()

    /**
     * Next time strictly after [now] that is a whole multiple of [periodMs]
     * counted from local midnight: :00, :05, ... for 5 minutes; the top of the
     * next hour for 60 minutes.
     */
    fun nextAligned(now: Long, periodMs: Long, zone: ZoneId = ZoneId.systemDefault()): Long {
        val start = localMidnight(now, zone)
        return start + ((now - start) / periodMs + 1) * periodMs
    }

    /** Most recent aligned time at or before [now]. */
    fun prevAligned(now: Long, periodMs: Long, zone: ZoneId = ZoneId.systemDefault()): Long {
        val start = localMidnight(now, zone)
        return start + ((now - start) / periodMs) * periodMs
    }

    /** True when [time] is exactly on a card boundary. */
    fun isCardTime(time: Long, zone: ZoneId = ZoneId.systemDefault()): Boolean =
        prevAligned(time, cardMs, zone) == time

    /** Number of whole card periods since local midnight (the hour, for hourly cards). */
    fun cardSlot(time: Long, zone: ZoneId = ZoneId.systemDefault()): Long =
        (time - localMidnight(time, zone)) / cardMs

    /** Local hour of day (0-23). */
    fun hourOf(time: Long, zone: ZoneId = ZoneId.systemDefault()): Int =
        Instant.ofEpochMilli(time).atZone(zone).hour

    /** Index wrapped into [0, size), also for negative numbers. */
    fun wrapIndex(index: Int, size: Int): Int =
        if (size <= 0) 0 else ((index % size) + size) % size

    /** Milliseconds as "m:ss" (or "h:mm:ss" for an hour or more). */
    fun formatCountdown(ms: Long): String {
        val total = maxOf(0L, (ms + 999) / 1000)
        val h = total / 3600
        val m = (total % 3600) / 60
        val s = total % 60
        return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
    }
}
