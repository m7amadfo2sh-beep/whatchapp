package com.whatchapp.hourlybuzz

import java.time.LocalDateTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScheduleTest {
    private val zone = ZoneId.of("Europe/Berlin")
    private val min = 60_000L

    private fun at(h: Int, m: Int, s: Int = 0, day: Int = 15) =
        LocalDateTime.of(2026, 1, day, h, m, s).atZone(zone).toInstant().toEpochMilli()

    @Test
    fun fiveMinuteTicksLandOnTheClock() {
        assertEquals(at(10, 5), Schedule.nextAligned(at(10, 2), 5 * min, zone))
        assertEquals(at(11, 0), Schedule.nextAligned(at(10, 59, 59), 5 * min, zone))
    }

    @Test
    fun exactlyOnABoundaryReturnsTheFollowingOne() {
        assertEquals(at(10, 10), Schedule.nextAligned(at(10, 5), 5 * min, zone))
        assertEquals(at(11, 0), Schedule.nextAligned(at(10, 0), 60 * min, zone))
    }

    @Test
    fun hourlyWrapsPastMidnight() {
        assertEquals(at(0, 0, day = 16), Schedule.nextAligned(at(23, 30), 60 * min, zone))
    }

    @Test
    fun prevAligned() {
        assertEquals(at(10, 5), Schedule.prevAligned(at(10, 7), 5 * min, zone))
        assertEquals(at(10, 0), Schedule.prevAligned(at(10, 0), 60 * min, zone))
    }

    @Test
    fun imageTimeIsOnTheHour() {
        assertTrue(Schedule.isImageTime(at(14, 0), zone))
        assertFalse(Schedule.isImageTime(at(14, 5), zone))
    }

    @Test
    fun wrapIndexHandlesOverflowAndNegatives() {
        assertEquals(0, Schedule.wrapIndex(4, 4))
        assertEquals(3, Schedule.wrapIndex(-1, 4))
        assertEquals(0, Schedule.wrapIndex(5, 0))
    }

    @Test
    fun formatCountdown() {
        assertEquals("4:05", Schedule.formatCountdown(4 * min + 5000))
        assertEquals("1:01:00", Schedule.formatCountdown(61 * min))
        assertEquals("0:00", Schedule.formatCountdown(-5))
    }
}
