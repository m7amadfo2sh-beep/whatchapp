package com.whatchapp.hourlybuzz

import org.junit.Assert.assertEquals
import org.junit.Test

class ContentTest {
    @Test
    fun oddHoursShowQuranVerses() {
        for (hour in listOf(1, 7, 13, 17, 23)) {
            assertEquals(Pool.VERSES, Content.poolFor(hour.toLong(), hour))
        }
    }

    @Test
    fun evenHoursShowDuasForTheTimeOfDay() {
        assertEquals(Pool.MORNING, Content.poolFor(6, 6))
        assertEquals(Pool.MORNING, Content.poolFor(10, 10))
        assertEquals(Pool.GENERAL, Content.poolFor(12, 12))
        assertEquals(Pool.EVENING, Content.poolFor(16, 16))
        assertEquals(Pool.EVENING, Content.poolFor(20, 20))
        assertEquals(Pool.GENERAL, Content.poolFor(22, 22))
        assertEquals(Pool.GENERAL, Content.poolFor(0, 0))
    }

    @Test
    fun arabicDigits() {
        assertEquals("٣", arabicDigits(3))
        assertEquals("١٠٠", arabicDigits(100))
    }
}
