package com.whatchapp.hourlybuzz.prayer

import com.whatchapp.hourlybuzz.prayer.Posture.LOW
import com.whatchapp.hourlybuzz.prayer.Posture.MOVING
import com.whatchapp.hourlybuzz.prayer.Posture.QIYAM
import com.whatchapp.hourlybuzz.prayer.Posture.RUKU
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RakahCounterTest {
    private var t = 0L

    /** Hold [p] for [ms], sampled at 10 Hz, with a takbir at the start if [voice]. */
    private fun RakahCounter.hold(p: Posture, ms: Long = 3000, voice: Boolean = true) {
        if (voice) onVoice(t)
        repeat((ms / 100).toInt()) {
            onPosture(p, t)
            t += 100
        }
    }

    private fun RakahCounter.move(ms: Long = 1000) = hold(MOVING, ms, voice = false)

    /** One full rak'ah; [sit] = the sitting (tashahhud) that may follow the 2nd sujood. */
    private fun RakahCounter.rakah(sit: Long = 0) {
        hold(QIYAM, 20_000); move()
        hold(RUKU, 4000); move()
        hold(QIYAM, 2000); move()          // i'tidal
        hold(LOW, 4000); move()            // sujood
        hold(LOW, 2000); move()            // jalsa (looks the same as sujood)
        hold(LOW, 4000 + sit); move()      // 2nd sujood (+ tashahhud)
    }

    @Test
    fun countsFourRakahsWithRemainingAfterCurrent() {
        val c = RakahCounter(4)
        val remaining = mutableListOf<Int>()
        repeat(4) { i ->
            c.hold(QIYAM, 5000)
            remaining += c.remainingAfterCurrent()
            assertEquals(i + 1, c.rakah)
            c.move()
            c.rakah(sit = if (i == 1 || i == 3) 30_000 else 0)
        }
        assertEquals(listOf(3, 2, 1, 0), remaining)
        assertEquals(4, c.rakah)
        assertTrue(c.finished(t + 150_000, 150_000))
    }

    @Test
    fun maghribAndFajr() {
        for (total in listOf(2, 3)) {
            val c = RakahCounter(total)
            repeat(total) { c.rakah(sit = 20_000) }
            assertEquals(total, c.rakah)
            assertEquals(0, c.remainingAfterCurrent())
        }
    }

    @Test
    fun shortBlipsAreIgnored() {
        val c = RakahCounter(4)
        c.hold(QIYAM, 10_000)
        c.hold(LOW, 500)          // scratching: too short to count
        c.hold(QIYAM, 5000)
        assertEquals(1, c.rakah)
        assertEquals(RakahCounter.Phase.BEFORE_RUKU, c.phase)
    }

    @Test
    fun withoutTakbirAPostureMustBeHeldLonger() {
        val c = RakahCounter(4)
        c.hold(QIYAM, 5000)
        c.hold(RUKU, 1200, voice = false)
        assertEquals(QIYAM, c.stable)
        c.hold(RUKU, 1000, voice = false)
        assertEquals(RUKU, c.stable)
    }

    @Test
    fun missedStandingStillCountsTheNextRakah() {
        val c = RakahCounter(4)
        c.rakah()
        c.hold(RUKU, 4000)        // standing wasn't detected, but the ruku was
        assertEquals(2, c.rakah)
    }

    @Test
    fun handsAtSidesBeforeTheFirstTakbirDontCount() {
        val c = RakahCounter(2)
        c.hold(RUKU, 5000)        // arms hanging down, looks like ruku
        c.rakah()
        assertEquals(1, c.rakah)
        c.rakah(sit = 30_000)
        assertEquals(2, c.rakah)
    }

    @Test
    fun manualCorrectionStaysInRange() {
        val c = RakahCounter(3)
        c.adjust(-1)
        assertEquals(1, c.rakah)
        c.adjust(5)
        assertEquals(3, c.rakah)
        assertFalse(c.finished(t, 150_000))
    }
}
