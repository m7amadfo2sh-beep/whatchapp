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
    fun cardTimeIsOnTheHour() {
        assertTrue(Schedule.isCardTime(at(14, 0), zone))
        assertFalse(Schedule.isCardTime(at(14, 5), zone))
    }

    @Test
    fun cardShowsThreeTimesThreeMinutesApart() {
        assertEquals(0, Schedule.cardRepeatNumber(at(14, 0), zone))
        assertEquals(1, Schedule.cardRepeatNumber(at(14, 3), zone))
        assertEquals(2, Schedule.cardRepeatNumber(at(14, 6), zone))
        assertEquals(-1, Schedule.cardRepeatNumber(at(14, 9), zone))
        assertEquals(-1, Schedule.cardRepeatNumber(at(14, 5), zone))
        assertEquals(-1, Schedule.cardRepeatNumber(at(14, 3, 1), zone))
    }

    @Test
    fun nextEventIncludesBuzzesAndCardRepeats() {
        assertEquals(at(14, 3), Schedule.nextEvent(at(14, 0, 30), zone))
        assertEquals(at(14, 5), Schedule.nextEvent(at(14, 3, 10), zone))
        assertEquals(at(14, 6), Schedule.nextEvent(at(14, 5, 10), zone))
        assertEquals(at(14, 10), Schedule.nextEvent(at(14, 6, 10), zone))
        assertEquals(at(15, 0), Schedule.nextEvent(at(14, 57), zone))
        assertEquals(at(14, 3), Schedule.nextEvent(at(14, 0), zone))
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
